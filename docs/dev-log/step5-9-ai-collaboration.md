# Step 5~9 AI 협업 개발 로그

## 개요
- 이 문서는 Step 5부터 Step 9까지의 구현 과정에서 Codex를 어떻게 활용했는지 기록하기 위한 개발 로그다.
- 포함 범위는 도메인 모델 생성, 규칙 엔진 구현 및 테스트, location resolver 구현 및 테스트다.
- AI는 설계 문서를 기준으로 코드를 생성하고 검토를 반영하는 보조 수단으로만 사용했고, 실제 비즈니스 판단은 문서 기반 규칙으로 고정했다.

---

## Step 5: 도메인 모델 생성

### 사용한 프롬프트
<details>
<summary>Step 5-1 프롬프트</summary>

```text
Re-read AGENTS.md and all documents in /docs before starting.

Important:
- The latest design docs are the single source of truth
- Do not expand the MVP scope
- Do not move decision logic into AI
- Do not change the architecture or package direction already created
- Follow the existing project skeleton, common config, exception setup, and test base already implemented

Step 5-1: Domain model creation (core domain only)

Implement only the core domain models for this project.

Target domain models:
- CalendarEvent
- ResolvedLocation
- WeatherSnapshot
- OutfitDecision

Requirements:
1) Create domain models based strictly on the design docs
2) Include only fields that are clearly required by the docs
3) Keep domain models framework-light as much as reasonably possible
4) Do NOT implement rule engine logic yet
5) Do NOT implement AI integration, external API clients, adapters, repositories, cache logic, or OAuth logic
6) Do NOT add speculative fields beyond the design
7) If needed, add small supporting enums/value types only when they make the domain clearer and are directly justified by the docs
8) Keep naming practical and consistent with the existing package structure
9) Preserve the hybrid design:
   - AI only for location interpretation and final summary
   - actual umbrella/outfit/weather decision logic must NOT be implemented here
10) Prepare the models so that later steps can use them naturally in:
   CalendarEvent -> ResolvedLocation -> WeatherSnapshot -> Rule Engine -> OutfitDecision -> AI Summary

Suggested interpretation from docs:
- CalendarEvent should represent a Google Calendar event needed for recommendation flow
- ResolvedLocation should represent raw location + normalized result + resolution status/source
- WeatherSnapshot should represent weather data for a specific region and target time
- OutfitDecision should represent the final rule-engine decision result, including summary fields produced later

Output rules:
- Answer in Korean
- Be concise
- First show:
  1) files to add or modify
  2) what each file is for
  3) key modeling decisions
  4) anything intentionally left for later steps
- Then implement
- At the end, provide a very short Korean summary
```
</details>

<details>
<summary>Step 5-2 프롬프트</summary>

```text
Re-read AGENTS.md and all documents in /docs before starting.

Important:
- The latest design docs are the single source of truth
- Do not expand the MVP scope
- Do not change previously implemented architecture/package direction unless absolutely necessary
- Do not implement rule engine, resolver, API clients, cache, OAuth, or AI integration in this step

Step 5-2: Refine and harden the domain model layer

Based on the current codebase, refine only the domain model layer created in Step 5-1.

Goals:
1) Review whether the domain models exactly match the docs
2) Remove any over-engineering or speculative fields
3) Improve clarity of enums/value objects/status modeling where necessary
4) Ensure the models are convenient for upcoming steps:
   - rule engine implementation
   - location resolver implementation
   - weather snapshot conversion
5) Add minimal validation/creation patterns only if they clearly improve domain consistency
6) Keep the domain independent from external integrations as much as possible

Check carefully:
- CalendarEvent fields needed for recommendation flow
- ResolvedLocation status/resolvedBy/confidence modeling
- WeatherSnapshot targetTime / precipitationProbability / weatherType expression
- OutfitDecision fields for umbrella result, outfit level/text, reasons, temperature gap, weather change summary, aiSummary
- Whether any enum or value object should be separated for readability
- Whether null handling is too vague and should be improved in a minimal way
- Whether the current package placement fits the project structure

Do NOT:
- add business decision logic
- add repository/entity mapping unless already unavoidable in the current architecture
- add controller/service/application logic
- add test cases yet unless a tiny model-level test is absolutely necessary
- add anything related to phase-2 features

Output rules:
- Answer in Korean
- Be concise
- First show:
  1) review result
  2) what should be kept
  3) what should be simplified or corrected
  4) what remains intentionally deferred
- Then apply the refinement
- End with a short Korean summary of the final domain model shape
```
</details>

### 수행 결과
- `CalendarEvent`, `ResolvedLocation`, `WeatherSnapshot`, `OutfitDecision`를 `domain/model`에 `record` 기반으로 추가했다.
- `LocationResolutionStatus`, `LocationResolvedBy`, `WeatherType`, `RecommendedOutfitLevel`을 보조 enum으로 분리했다.
- `ResolvedLocation`은 `resolved`, `approximated`, `failed` 생성 패턴을 추가해 상태별 필드 일관성을 유지하도록 정리했다.

### 발생한 문제 / 애매한 부분
- `ResolvedLocation` 실패 상태에서 `lat/lng/confidence`를 어떻게 표현할지 애매했다.
- `rawLocation`, `umbrellaReason`, `outfitReason`, `weatherChangeSummary`의 null 허용 범위가 문서에 명시되어 있지 않았다.
- `WeatherType`과 `RecommendedOutfitLevel`은 문서에서 이름만 제시되지 않아 enum 명칭을 실용적으로 정해야 했다.

### 개선 및 결정
- 최신 문서 `design-overview-ko-v2.md`를 우선 기준으로 삼고, 다른 문서는 보조 참고만 했다.
- 실패 상태를 명확히 표현하기 위해 `ResolvedLocation`의 일부 필드는 nullable로 두고 상태별 검증을 추가했다.
- 도메인 모델에는 JPA나 외부 의존성을 넣지 않고, 규칙 엔진과 resolver가 바로 사용할 수 있는 최소 형태만 유지했다.
- 요약/이유 계열 필드는 이후 규칙 엔진 및 AI summary 단계 소유로 보고 이 단계에서는 과도하게 강제하지 않았다.

---

## Step 6~7: 규칙 엔진 구현 및 테스트

### 사용한 프롬프트
<details>
<summary>Step 6 프롬프트</summary>

```text
Re-read AGENTS.md and all documents in /docs before starting.

Important:
- The latest design docs are the single source of truth
- Do not expand the MVP scope
- Do not move decision logic into AI
- Keep the existing architecture/package direction
- Follow the current domain models already implemented
- This step is about rule behavior, not external integration

Step 6: Implement the rule engine only

Implement only the core rule engine that produces OutfitDecision from already available domain data.

In-scope:
- Rule engine implementation
- Minimal supporting enums/value types if truly needed
- Small policy/helper structure only if it clearly improves readability

Out-of-scope:
- AI integration
- location resolver implementation
- weather API client
- Google OAuth / Calendar integration
- Redis/MySQL integration
- controller/service/api wiring beyond what is minimally necessary
- cache logic

Rule requirements from the docs:
1) Umbrella decision:
   - true if end-time precipitationProbability >= 50
   - OR end-time weatherType == RAIN
2) Outfit recommendation baseline:
   - based on end-time feelsLike
   - >= 23 => 반팔
   - 20~22 => 반팔 + 얇은 겉옷
   - 17~19 => 긴팔
   - 13~16 => 긴팔 + 가벼운 겉옷
   - <= 12 => 두꺼운 겉옷
3) Adjustment rule:
   increase outfit level by +1 if any of:
   - destination/end feelsLike is 4 or more lower than current
   - start -> end feelsLike drops by 3 or more
   - low temperature + rain
4) Keep recommendation functional and deterministic
5) Do not let AI decide umbrella/outfit/weather logic

Implementation guidance:
- Design the rule engine around input domain models already created
- Prefer explicit and readable code over abstraction
- Keep the policy easy to test
- Handle boundary values carefully
- Keep failure/unknown handling minimal and aligned with docs
- Do not over-engineer

Output rules:
- Answer in Korean
- Be concise
- First show:
  1) files to add or modify
  2) what each file is for
  3) rule interpretation decisions
  4) assumptions or deferred points
- Then implement
- End with a very short Korean summary
```
</details>

<details>
<summary>Step 7 프롬프트</summary>

```text
Re-read AGENTS.md and all documents in /docs before starting.

Important:
- The latest design docs are the single source of truth
- Do not expand the MVP scope
- Do not modify architecture/package direction unnecessarily
- Tests must validate the implemented rule engine against the design docs
- Be strict on boundary conditions

Step 7: Write rule engine tests only

Implement tests for the rule engine created in the previous step.

Test scope:
1) Umbrella decision tests
   - precipitationProbability >= 50 at end time
   - weatherType == RAIN at end time
   - no rain / low precipitation
2) Outfit baseline tests by end-time feelsLike
   - >= 23
   - 20~22
   - 17~19
   - 13~16
   - <= 12
3) Adjustment rule tests
   - current 대비 -4도 이상
   - start -> end -3도 이상
   - low temperature + rain
   - no adjustment case
4) Boundary value tests
   - 23, 22, 20, 19, 17, 16, 13, 12
   - precipitation 49 vs 50
   - temperature gap 3 vs 4
5) Result consistency tests
   - recommendedOutfitLevel and recommendedOutfitText stay aligned
   - umbrella reason / outfit reason are sensible if those fields are produced
6) Keep tests deterministic and easy to read

Out-of-scope:
- location resolver tests
- weather client tests
- API/integration tests
- DB/Redis tests
- AI summary tests

Test style guidance:
- Prefer clear scenario-based test names
- Keep fixture construction readable
- Avoid unnecessary mocking if pure rule logic can be tested directly
- Cover the most important happy path + boundary path + adjustment path
- Do not write fragile tests tied to implementation details

Output rules:
- Answer in Korean
- Be concise
- First show:
  1) which scenarios will be tested
  2) any missing ambiguity in current implementation
  3) whether any tiny refactor is needed for testability
- Then implement the tests
- End with a short Korean summary of coverage
```
</details>

### 수행 결과
- `domain/rule/OutfitRuleEngine`를 추가해 현재/시작/종료 날씨 3개로 `OutfitDecision`을 생성하도록 구현했다.
- 우산 판단, 종료 시점 체감온도 기준 baseline, 보정 규칙 `+1단계`를 문서 기준으로 명시적으로 구현했다.
- 경계값, 우산 판단, 보정 조건, 추천 단계/문구 일관성을 검증하는 단위 테스트를 추가했다.

### 발생한 문제
- `docs/rules.md`와 최신 `design-overview-ko-v2.md` 사이에 일부 수치가 달라 최신 문서를 기준으로 선택해야 했다.
- baseline 경계값 테스트 작성 초기에 입력값이 보정 조건까지 만족해 테스트가 실패했다.
- `저온 + 비`의 “저온” 기준 수치가 문서에 명시되지 않아 해석이 필요했다.

### 개선 및 결정
- 최신 설계 문서를 단일 기준으로 사용하고, 오래된 규칙 문서는 따르지 않았다.
- baseline 테스트는 순수 baseline만 검증하도록 현재/시작/종료 입력을 동일하게 맞췄다.
- `저온 + 비`는 최소 해석으로 `end feelsLike <= 12 && RAIN`으로 구현하고, 더 구체적 정책은 이후 문서 확정 시점으로 미뤘다.
- 이유 문구와 `weatherChangeSummary`는 도메인 필드를 채우기 위한 최소 수준만 생성하고, 별도 자연어 고도화는 보류했다.

---

## Step 8~9: location resolver 구현 및 테스트

### 사용한 프롬프트
<details>
<summary>Step 8 프롬프트</summary>

```text
Re-read AGENTS.md and all documents in /docs before starting.

Important:
- The latest design docs are the single source of truth
- Do not expand the MVP scope
- Keep the hybrid design exactly as documented
- AI must only be used as a fallback for location interpretation
- Do not change the existing architecture/package direction
- Do not implement real external API integration in this step

Step 8: Implement location resolver (rule + AI fallback)

Implement only the location resolver layer for this project.

Goal:
- Resolve a calendar event raw location string into ResolvedLocation
- Use rule-based resolution first
- Use AI fallback only when rule-based resolution cannot confidently resolve it
- Preserve the documented status / resolvedBy semantics

Required behavior from the docs:
1) Input is a raw natural-language location string from CalendarEvent
2) Rule-based resolution is attempted first
3) If rules cannot resolve sufficiently, use AI fallback
4) Result should map naturally into the existing ResolvedLocation model
5) Possible result states should align with docs:
   - RESOLVED
   - APPROXIMATED
   - FAILED
6) resolvedBy should align with docs:
   - RULE
   - AI
7) Keep rawLocation for traceability
8) Keep AI responsibility limited to location interpretation only
9) Do not let AI participate in weather, umbrella, or outfit decision logic

Implementation guidance:
- Prefer a small, explicit, testable structure
- Keep the rule-based resolver simple and deterministic
- Use a port/interface or similarly minimal boundary for AI fallback
- Do not implement real AI API calls
- Do not implement cache, weather client, Google integration, or service/api layers beyond what is minimally necessary
- Avoid over-engineering
- Handle blank/meaningless inputs carefully
- Failed resolution should be representable as a domain result, not necessarily as an exception

Suggested scope:
- resolver orchestration component
- rule-based resolver component
- AI fallback boundary/interface and minimal stub-friendly shape if needed
- any tiny helper/value type only if truly necessary

Out-of-scope:
- real AI integration
- weather API
- Google OAuth / Calendar adapter
- Redis/MySQL integration
- controller endpoints
- recommendation flow wiring
- final summary generation

Output rules:
- Answer in Korean
- Be concise
- First show:
  1) files to add or modify
  2) what each file is for
  3) how the resolver flow works
  4) what is intentionally deferred
- Then implement
- End with a very short Korean summary
```
</details>

<details>
<summary>Step 9 프롬프트</summary>

```text
Re-read AGENTS.md and all documents in /docs before starting.

Important:
- The latest design docs are the single source of truth
- Do not expand the MVP scope
- Tests must validate the rule-first, AI-fallback structure
- Do not implement real external integrations
- Be strict about status/resolvedBy semantics

Step 9: Write tests for the location resolver

Implement tests only for the location resolver created in the previous step.

Test goals:
1) Rule-based resolution succeeds without AI fallback when input matches known rules
2) AI fallback is used only when rule-based resolution cannot resolve
3) Failed resolution is represented correctly when both rule and AI fallback fail
4) ResolvedLocation fields stay consistent:
   - rawLocation
   - normalizedQuery
   - displayLocation
   - lat / lng
   - confidence
   - status
   - resolvedBy
5) Blank or unusable input is handled safely
6) Resolver behavior stays deterministic and easy to understand

Suggested scenarios:
- exact or well-known rule match
- simple natural language input that rules can normalize
- ambiguous input that falls back to AI and succeeds
- ambiguous input that falls back to AI and returns APPROXIMATED
- input that fails in both rule and AI paths
- blank/null-like/meaningless input
- verify AI is not called when rule resolution already succeeded
- verify resolvedBy/status are correct for each path

Test style guidance:
- Prefer scenario-based test names
- Keep fixture setup readable
- Use fake/stub implementations rather than heavy mocking where possible
- Avoid fragile tests tied to internal implementation details
- Test behavior, not incidental structure

Out-of-scope:
- weather client tests
- recommendation service tests
- API/integration tests
- cache tests
- AI summary tests

Output rules:
- Answer in Korean
- Be concise
- First show:
  1) test scenario list
  2) any ambiguity in the current resolver behavior
  3) whether a tiny refactor is needed for testability
- Then implement tests
- End with a short Korean coverage summary
```
</details>

### 수행 결과
- `application/location` 패키지에 `LocationResolver`, `AiLocationFallbackResolver`, `RuleBasedLocationResolver`, `DefaultLocationResolver`를 추가했다.
- `DefaultLocationResolver`는 rule-first, AI-fallback 오케스트레이션만 담당하고, 실제 AI 호출은 인터페이스 경계로만 남겼다.
- 규칙 기반 exact match는 `RESOLVED/RULE`, keyword 포함 해석은 `APPROXIMATED/RULE`, 둘 다 실패하면 `FAILED`로 반환하도록 구현했다.
- resolver 테스트는 rule 성공, AI fallback 성공, AI `APPROXIMATED`, 전체 실패, blank/useless 입력, AI 미호출 여부를 검증하도록 작성했다.

### 발생한 문제
- 초기 구현에서는 blank/useless 입력도 AI fallback으로 전달되는 문제가 있었다.
- `isMeaninglessInput`과 rule resolver의 normalize 로직이 비슷해 중복처럼 보였지만, 현재 범위에서는 동작 우선으로 유지했다.
- confidence 값은 문서에 수치가 없어서 exact `1.0`, approximated `0.6`, AI 예시 `0.82`를 실용적으로 사용했다.

### 개선 및 결정
- blank/useless 입력은 AI에 보내지 않고 즉시 `FAILED`로 반환하도록 수정했다.
- meaningful input에 대해서만 rule-first 후 AI fallback이 동작하도록 제한했다.
- AI 결과는 `resolvedBy == AI`, `status != FAILED`, `rawLocation` 일치 조건을 만족할 때만 채택했다.
- 실제 AI API, 캐시, default current-location fallback은 다음 단계로 미뤘다.

---

## 전체 회고

### 잘 된 점
- 설계 문서를 매 단계 다시 확인하면서 AI 생성 결과를 제한해 아키텍처 이탈을 막았다.
- 규칙 엔진과 resolver 모두 테스트를 먼저 보강하면서 경계값과 상태 semantics를 검증했다.
- AI 역할을 location fallback과 summary 예정 필드 수준으로만 묶어 하이브리드 설계를 유지했다.

### 어려웠던 점
- 최신 문서와 오래된 문서 사이에 규칙 수치 차이가 있어 기준 문서를 명확히 잡아야 했다.
- `저온 + 비`, null 허용 범위, meaningless input 처리처럼 문서에 수치나 정책이 없는 부분은 최소 해석이 필요했다.
- AI는 종종 성능 최적화나 추상화를 과하게 제안했기 때문에 MVP 범위와 단계 목표로 계속 걸러야 했다.

### 개선 방향
- 다음 단계에서는 현재 만든 domain/application 경계를 유지한 채 recommendation flow orchestration으로 확장하는 것이 적절하다.
- 문서가 더 구체화되면 `confidence`, failure fallback, summary 필드 생성 기준을 명확히 맞출 필요가 있다.
- AI 활용은 계속 구현 보조와 검토 보조로 제한하고, 실제 비즈니스 결정은 규칙과 테스트로 고정하는 방식이 적합하다.
