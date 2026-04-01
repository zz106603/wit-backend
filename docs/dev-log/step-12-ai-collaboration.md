# Step 12 AI 협업 개발 로그

## 개요
- 이 문서는 Step 12의 구현 과정과 이후 리뷰 반영 과정을 기록하기 위한 개발 로그다.
- 포함 범위는 recommendation service orchestration 구현, location/weather/rule engine 연결, fallback 처리 보정, orchestration 테스트다.
- AI는 설계 문서를 기준으로 코드를 생성하고 리뷰 의견을 정리하는 보조 수단으로만 사용했고, 실제 추천 판단은 규칙 엔진과 domain-side fallback component 책임으로 유지했다.

---

## Step 12: recommendation service assembly

### 사용한 프롬프트
<details>
<summary>Step 12 프롬프트</summary>

```text
Re-read AGENTS.md and ALL documents in /docs from scratch.

Also review existing implementation:
- CalendarEvent
- ResolvedLocation
- WeatherSnapshot
- weather client
- rule engine (if already implemented)
- current test base

Important:
- Treat /docs as the single source of truth
- Do NOT expand MVP scope
- Do NOT modify existing domain logic
- Do NOT move business logic into infra layer
- Do NOT let AI perform decision logic

Step 12: recommendation service assembly

Implement the application-level orchestration that connects all components into a single recommendation flow.

Scope:
- Implement RecommendationService (or equivalent application service)
- Orchestrate the following flow:

  1) Input: CalendarEvent
  2) Resolve location (use existing location resolver)
  3) Fetch weather snapshots:
     - current
     - start time
     - end time
  4) Execute rule engine
  5) Produce OutfitDecision

- Return a structured result that matches the current domain design

Requirements:
1) Responsibility separation
- RecommendationService:
  - orchestration ONLY
  - no business decision logic
- Rule engine:
  - owns umbrella / outfit decision
- Weather client:
  - only fetch + transform
- Location resolver:
  - rule + AI fallback 유지

2) Data flow integrity
Follow strictly:
CalendarEvent
 → ResolvedLocation
 → WeatherSnapshot (3)
 → Rule Engine
 → OutfitDecision
Do NOT skip or merge steps

3) Error / fallback behavior (minimal)
- If location fails:
  - fallback to current location 기준
- If weather fails:
  - do NOT crash entire flow
  - return partial or safe fallback structure

4) Output structure
- Use OutfitDecision as the main result
- Do NOT attach AI summary yet (Step 14)
- Keep response clean and aligned with domain

5) Layer rules
- Application layer orchestrates
- Domain layer owns decision
- Infra layer only provides data

6) What NOT to do
- ❌ Redis cache
- ❌ Google Calendar API integration 변경
- ❌ AI summary 생성
- ❌ 새로운 복잡한 abstraction 추가
- ❌ rule engine 수정
```
</details>

### 수행 결과
- `application/recommendation/RecommendationService`를 추가해 일정 입력에서 위치 해석, 날씨 조회 3건, 규칙 엔진 실행까지의 흐름을 application 레이어에서 조립했다.
- `application/recommendation/RecommendationResult`를 추가해 `OutfitDecision`과 orchestration 중간 결과(`CalendarEvent`, `ResolvedLocation`, `WeatherSnapshot` 3건)를 함께 담도록 구성했다.
- `application/location/CurrentLocationProvider`를 추가해 location 실패 시 현재 위치 기준 fallback 경로를 application 포트로 분리했다.
- `infrastructure/config/RecommendationAssemblyConfig`와 `infrastructure/location/CurrentLocationProperties`를 추가해 resolver, current location provider, rule engine, recommendation service bean을 조립했다.
- current location 기본값은 `application.yaml`에 설정 기반으로 추가했다.

### 발생한 문제 / 애매한 부분
- 초기 구현에서는 weather 실패 시 `RecommendationService`가 synthetic `WeatherSnapshot`을 만들어 rule engine을 계속 실행하는 방식으로 처리했다.
- 이 방식은 날씨가 없는 상태를 숨기고 정상 날씨 흐름처럼 보이게 만들어 설계의 failure 의미와 맞지 않았다.
- 초기 fallback 구현에서는 `RecommendationService`가 직접 `OutfitDecision`의 fallback 값을 생성해 application 레이어가 추천 판단 값을 소유하게 되었다.
- 문서에는 `Weather Failure -> cached data -> if none -> safe default outfit`이 명시되어 있었고, 현재 단계에서는 cache를 구현하지 않으므로 최소한 “safe default outfit” 의미를 명시적으로 표현해야 했다.

### 개선 및 결정
- recommendation flow는 `CalendarEvent -> ResolvedLocation -> WeatherSnapshot(3) -> Rule Engine -> OutfitDecision` 순서를 유지하도록 구현했다.
- location 실패는 `CurrentLocationProvider`가 제공하는 현재 위치를 기준으로 계속 진행하도록 유지했다.
- weather 실패는 전체 흐름을 중단하지 않되, 날씨가 존재하는 것처럼 fake snapshot을 만들지 않는 방향으로 정리하기로 했다.
- `RecommendationResult`를 사용해 `OutfitDecision`을 중심으로 두되, fallback 여부와 중간 결과를 함께 확인할 수 있도록 구성했다.

---

## Step 12 후속 수정: fallback 설계 보정

### 사용한 프롬프트
<details>
<summary>후속 수정 프롬프트</summary>

```text
Re-read AGENTS.md and ALL documents in /docs from scratch.

Also review the current implementation related to:
- RecommendationService
- RecommendationResult
- OutfitDecision
- weather client usage
- Step 12 tests

Important:
- Treat /docs as the single source of truth
- Do NOT expand MVP scope
- Do NOT modify unrelated files
- Do NOT add cache / AI summary / Google integration / retry system
- Keep the fix minimal and aligned with Step 12 only

Apply the following corrections.

[1] Remove fake weather snapshot fallback
- RecommendationService currently uses safeFallbackSnapshot(...) or equivalent logic
- This creates synthetic weather snapshots to bypass weather failure
- This is NOT aligned with the design

[2] Align weather failure fallback with design intent
- On weather failure, return a safe default recommendation/result structure
- Do NOT fabricate weather data to continue normal rule-engine flow

[3] Remove application-owned fallback decision logic
- RecommendationService must NOT construct business decision values by itself

[4] Move fallback recommendation ownership to a proper domain-side place
- RecommendationService chooses the path
- Domain-side decision component owns the actual recommendation decision value

[5] Preserve explicit weather failure meaning
- weather fetch failed
- recommendation is limited / safe fallback based
- Do NOT hide failure behind fake normal flow
```
</details>

### 수행 결과
- `RecommendationService`에서 fake weather snapshot fallback을 제거했다.
- weather 실패 시 `currentWeather`, `startWeather`, `endWeather`를 `null`로 두고 `weatherFallbackApplied = true`인 명시적 fallback 결과를 반환하도록 수정했다.
- 초기에는 `RecommendationService` 내부 `safeDefaultDecision()`으로 fallback 추천값을 만들었지만, 이후 이를 제거했다.
- `domain/rule/WeatherFailureFallbackDecisionProvider`를 추가해 weather failure 시 사용할 fallback `OutfitDecision` 생성 책임을 domain-side component로 이동했다.
- `RecommendationService`는 weather 실패를 감지하고 fallback 경로를 선택만 하며, 실제 fallback recommendation 값은 `WeatherFailureFallbackDecisionProvider`에서 받도록 바꿨다.
- `RecommendationAssemblyConfig`에 `WeatherFailureFallbackDecisionProvider` bean 등록을 추가해 전체 조립이 Spring bean 기준으로 동작하도록 정리했다.

### 발생한 문제
- fake snapshot 제거 이후에도 `RecommendationService`가 직접 `needUmbrella`, `recommendedOutfitLevel`, `recommendedOutfitText`를 정하는 fallback decision 로직을 갖고 있으면 여전히 orchestration-only 원칙을 위반했다.
- fallback recommendation이 application에 남아 있으면 rule engine ownership과 동일한 성격의 recommendation ownership이 application으로 새게 된다.
- weather failure 의미를 유지하려면 fallback recommendation을 쓰더라도 “정상 weather path”를 다시 실행하지 않아야 했다.
- `RecommendationResult`는 유지하되, fallback 여부가 wrapper 안에서 가려지지 않도록 최소한의 표시가 필요했다.

### 개선 및 결정
- `RecommendationService`에서는 위치 해석, 날씨 조회, 실패 여부 판단, 정상 경로/실패 경로 선택만 남겼다.
- 정상 경로에서는 기존 `OutfitRuleEngine`을 그대로 호출하고, weather 실패 경로에서는 rule engine 대신 `WeatherFailureFallbackDecisionProvider`를 호출하도록 분기했다.
- `RecommendationResult`에는 `weatherFallbackApplied`를 유지하고 weather snapshot 3건은 `null`로 두어 날씨 실패가 명시되도록 했다.
- 새 domain-side component는 fallback recommendation 생성 책임만 가지는 최소 구현으로 유지했고, rule engine 전체를 재설계하지 않았다.

---

## Step 12 테스트: orchestration 및 fallback 검증

### 사용한 프롬프트
<details>
<summary>Step 12 테스트 보강 프롬프트</summary>

```text
Re-read AGENTS.md and ALL documents in /docs from scratch.

Also review the current implementation related to:
- RecommendationService
- RecommendationResult
- OutfitDecision
- rule engine
- Step 12 tests

Important:
- Treat /docs as the single source of truth
- Do NOT expand MVP scope
- Do NOT modify unrelated files
- Keep the fix minimal and aligned with Step 12 only

Update/add tests to verify at least:

1) normal recommendation flow
2) location failure fallback path
3) weather failure path uses domain-owned fallback decision path
4) no synthetic weather snapshot is used
5) resolver exception / weather null or failure handling
6) RecommendationService does orchestration only
```
</details>

### 수행 결과
- `RecommendationServiceTest`를 추가하고 정상 흐름, location 실패 fallback, weather 실패 fallback, resolver exception, weather `null` 반환을 검증하도록 작성했다.
- weather 실패 테스트에서는 `TrackingRuleEngine`으로 rule engine이 호출되지 않는지 검증하고, `TrackingFallbackDecisionProvider`로 fallback recommendation 생성이 domain-side component를 통해 수행되는지 검증했다.
- 정상 흐름 테스트에서는 `CalendarEvent`, `ResolvedLocation`, `WeatherSnapshot` 3건, `OutfitDecision`이 기대한 순서대로 조합되는지 확인했다.
- location 실패 테스트에서는 해석 실패 시 현재 위치 기준으로 start/end weather 조회가 이어지는지 검증했다.

### 발생한 문제
- recommendation orchestration 테스트는 정상 흐름뿐 아니라 fallback 의미와 ownership까지 함께 검증해야 해서 단순 happy-path 테스트만으로는 충분하지 않았다.
- weather failure 테스트에서 단순히 결과값만 검증하면 `RecommendationService`가 직접 fallback decision을 만들었는지, domain-side provider를 사용했는지 구분하기 어려웠다.
- synthetic snapshot 제거 이후에는 snapshot이 `null`이어야 한다는 점을 명시적으로 확인해야 했다.

### 개선 및 결정
- 테스트는 외부 API 호출 없이 stub/fake 기반으로만 구성해 Step 12 범위에 맞게 유지했다.
- weather failure 시 `weatherFallbackApplied == true`, `currentWeather/startWeather/endWeather == null`, `ruleEngine.called == false`, `fallbackDecisionProvider.called == true`를 함께 검증하도록 정리했다.
- resolver exception과 `null` weather 반환도 fallback 경로에 포함해 application orchestration이 예외를 안전하게 흡수하는지 확인했다.
- recommendation service 테스트는 Step 13 확장 없이 Step 12 범위의 orchestration과 fallback semantics만 검증하도록 제한했다.

---

## 전체 회고

### 잘 된 점
- location resolver, weather client, rule engine을 application service 하나로 조립하되 각 레이어 책임을 크게 흔들지 않고 연결했다.
- fake snapshot fallback과 application-owned fallback decision을 제거하면서 failure 의미를 더 명확하게 드러냈다.
- weather failure 시점의 recommendation ownership을 domain-side component로 이동해 orchestration-only 원칙과 recommendation ownership을 맞췄다.

### 어려웠던 점
- 초기에 “흐름을 끊지 않기 위해” fake snapshot으로 rule engine을 계속 실행하는 방식이 자연스럽게 보였지만, 이는 failure 의미를 숨기는 방식이라 설계와 맞지 않았다.
- weather failure에서 safe default outfit이 필요하다는 문서 요구와 application이 decision 값을 만들면 안 된다는 원칙을 동시에 만족시키기 위해 fallback ownership 위치를 다시 정리해야 했다.
- `RecommendationResult`를 유지하면서도 `OutfitDecision` 중심성과 fallback 명시성을 함께 드러내는 선을 맞추는 작업이 필요했다.

### 개선 방향
- 다음 단계에서는 현재 recommendation orchestration 구조를 유지한 채 stub event 기반 end-to-end service test로 확장하는 것이 적절하다.
- cache fallback은 문서에 있지만 현재 단계 범위를 넘으므로, 이후 단계에서 weather failure 처리와 연결해 별도로 구현하는 것이 맞다.
- AI summary, Google integration, presentation API 연결은 지금 구조를 유지한 상태에서 이후 단계로 확장하는 것이 적절하다.
