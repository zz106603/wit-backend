# Step 10~11 AI 협업 개발 로그

## 개요
- 이 문서는 Step 10부터 Step 11까지의 구현 과정과 이후 리뷰 반영 과정을 기록하기 위한 개발 로그다.
- 포함 범위는 weather client 구현, 외부 응답의 `WeatherSnapshot` 변환, stub 기반 weather 테스트, 그리고 후속 설계 보정이다.
- AI는 설계 문서를 기준으로 코드를 생성하고 리뷰 의견을 정리하는 보조 수단으로만 사용했고, 실제 날씨 판단과 추천 판단은 규칙 엔진 책임으로 유지했다.

---

## Step 10~11: weather client 구현 및 stub 테스트

### 사용한 프롬프트
<details>
<summary>Step 10 프롬프트</summary>

```text
Re-read AGENTS.md and ALL documents in /docs from scratch before starting.

Important:
- Ignore previous assumptions
- Treat /docs as the single source of truth
- Do not expand MVP scope
- Do not change the architecture
- Do not let AI make weather/outfit/umbrella decisions

Step 10: weather client / snapshot transformation

Implement only the weather integration layer and snapshot conversion needed by the current design.

Scope:
- Add the Weather API client abstraction and implementation shape required by the project design
- Implement logic that converts external weather response data into the internal WeatherSnapshot model
- Support the exact snapshot usage expected by the design:
  - current location weather
  - event start time weather
  - event end time weather
- Keep the design aligned with the rule engine ownership:
  - weather client only fetches and transforms data
  - it must NOT decide umbrella / outfit / summary
- Keep package and layer separation strict

Requirements:
- Follow the existing architecture and package conventions already in the project
- Preserve domain/usecase/infra boundaries
- Prefer clear ports/adapters structure if the current codebase already uses it
- WeatherSnapshot must remain a pure internal model used by downstream rule logic
- Include only minimal configuration/properties/classes required for this step
- Do NOT implement recommendation orchestration yet
- Do NOT implement Redis cache yet
- Do NOT implement real Google Calendar integration changes
- Do NOT implement AI summary or location logic changes
- Do NOT modify unrelated files

Design constraints to preserve:
- AI is not allowed to perform weather judgment
- Rule engine remains the owner of umbrella/outfit decision
- This step is only for weather fetch + transformation

What to produce:
1) Weather client related classes/interfaces
2) External response -> WeatherSnapshot transformation logic
3) Minimal config/properties needed for this step
4) Very short Korean summary of:
   - added/modified files
   - each file’s role
   - how this step fits the architecture

Output rules:
- Answer in Korean
- Be concise
- Do not print full code unless necessary
- If an assumption is unavoidable, explicitly state it briefly
```
</details>

<details>
<summary>Step 11 프롬프트</summary>

```text
Re-read AGENTS.md and ALL documents in /docs from scratch before starting.

Important:
- Ignore previous assumptions
- Treat /docs as the single source of truth
- Do not change architecture
- Do not expand MVP scope
- Do not modify business ownership boundaries

Step 11: weather stub tests

Implement only the tests for the weather client / snapshot transformation added in the previous step.

Scope:
- Add tests that validate weather response stubbing and WeatherSnapshot transformation behavior
- Verify that the system can correctly produce snapshot data for:
  - current weather
  - start time weather
  - end time weather
- Verify mapping stability for the fields required by the domain design
- Focus on testability and deterministic behavior

Requirements:
- Use stub/fake/test fixture style appropriate for the current project
- Tests must not depend on real external API calls
- Tests should validate transformation correctness, not recommendation business logic
- Tests must not test umbrella/outfit decision logic here
- Keep tests aligned with the project’s existing test base/conventions
- Add only the minimal fixtures/helpers needed
- Do NOT add integration with Redis, Google, AI, or recommendation service
- Do NOT change unrelated production code except what is minimally necessary for testability

Must cover:
1) successful current/start/end snapshot conversion
2) external response to internal WeatherSnapshot field mapping
3) malformed or incomplete response handling within the intended boundary
4) deterministic stub-based verification without network dependency

Output:
- Provide a very short Korean summary of:
  1) what tests were added
  2) what scenarios they verify
  3) whether any minimal production-code adjustment was needed for testability

Output rules:
- Answer in Korean
- Be concise
- Do not print full code unless necessary
- Keep the tests strictly within Step 11 scope
```
</details>

### 수행 결과
- `application/weather/WeatherClient`를 추가해 application 포트로 현재 날씨와 특정 시각 날씨 조회 계약을 분리했다.
- `infrastructure/weather` 패키지에 `HttpWeatherClient`, `WeatherApiResponse`, `WeatherApiProperties`, `WeatherClientConfig`, `WeatherSnapshotMapper`를 추가했다.
- `HttpWeatherClient`는 좌표와 시각으로 외부 weather API를 호출하고, `WeatherSnapshotMapper`를 통해 내부 `WeatherSnapshot`으로 변환하도록 구현했다.
- Step 11에서 `WeatherSnapshotMapperTest`, `HttpWeatherClientTest`를 추가해 mapper 단위 변환과 `MockRestServiceServer` 기반 HTTP stub 테스트를 작성했다.

### 발생한 문제 / 애매한 부분
- 초기 구현에서는 `WeatherSnapshot.targetTime`이 요청 시각이 아니라 provider 응답의 `targetTime`을 사용할 수 있게 되어 있었다.
- 초기 condition 매핑은 `SUNNY`, `PARTLY_CLOUDY`, `DRIZZLE` 같은 문자열을 추론적으로 넓게 허용하고 있었다.
- `null response`, 역직렬화 실패, `4xx/5xx`에 대한 일관된 weather infra 예외 경계가 없었다.
- Step 11 테스트는 current/start/end snapshot 생성과 기본 매핑은 검증했지만, `targetTime` 의미와 unknown condition 처리, infra 실패 정규화는 처음에는 포함하지 못했다.

### 개선 및 결정
- weather layer는 fetch + transform만 담당하고, 우산/옷차림/summary 결정은 만들지 않았다.
- `WeatherSnapshot`은 domain 내부 모델로 유지하고, provider 응답 구조는 `WeatherApiResponse`에만 남겼다.
- 테스트는 실제 네트워크 호출 없이 `MockRestServiceServer`와 고정 `Clock`을 사용해 결정적으로 검증하도록 유지했다.
- Step 10~11 구현 후 리뷰에서 드러난 문제는 다음 보정 단계에서 weather infra 내부 수정으로만 정리하기로 했다.

---

## Step 10~11 후속 수정: 리뷰 반영 및 설계 보정

### 사용한 프롬프트
<details>
<summary>후속 수정 프롬프트</summary>

```text
Re-read AGENTS.md and ALL documents in /docs from scratch.

Also review the current implementation related to:
- WeatherSnapshot
- WeatherSnapshotMapper
- HttpWeatherClient
- weather stub tests

Important:
- Treat /docs as the single source of truth
- Do NOT expand MVP scope
- Do NOT modify unrelated code
- Do NOT implement recommendation / cache / orchestration
- Do NOT introduce new layers or over-engineering

Now apply the following corrections.

[1] targetTime ownership fix (MANDATORY)
- WeatherSnapshot.targetTime must represent the internally requested target time
- NOT the provider response timestamp

[2] provider condition → WeatherType contract (MANDATORY)
- Stop using inferred or guessed mapping
- Define an explicit contract for provider condition values
- Map ONLY from those defined values → internal WeatherType
- If unknown value appears:
  - handle safely (e.g. UNKNOWN or fallback)
  - do NOT break the system

[3] weather infrastructure failure normalization (MINIMAL)
- Normalize only minimal failure cases in HttpWeatherClient
- Handle:
  - null response
  - deserialization failure
  - HTTP 4xx / 5xx
- Convert them into a consistent weather-infra-level failure

[4] tests update (MANDATORY)
- targetTime is always requestedTargetTime
- condition mapping follows explicit contract
- unknown condition is safely handled
- failure cases (null / deserialize / 4xx/5xx) are handled deterministically
```
</details>

### 수행 결과
- `WeatherSnapshotMapper`에서 `WeatherSnapshot.targetTime`을 항상 `requestedTargetTime`으로 설정하도록 수정했다.
- `WeatherType`에 `UNKNOWN`을 추가하고, provider condition 계약을 `CLEAR`, `CLOUDS`, `RAIN`, `SNOW`, `WIND`로 명시했다.
- `WeatherSnapshotMapper`는 provider 문자열을 위 계약에 따라 내부 `WeatherType`으로 변환하고, 알 수 없는 값이나 blank 값은 `UNKNOWN`으로 처리하도록 바꿨다.
- `HttpWeatherClient`에 `WeatherInfrastructureException`을 추가하고 `null response`, 역직렬화 실패, `4xx/5xx`를 이 예외로 정규화했다.
- 관련 테스트를 갱신해 요청 시각 우선 규칙, explicit condition contract, unknown condition 처리, infra failure 정규화를 검증했다.

### 발생한 문제
- 리뷰 과정에서 `targetTime`을 provider 응답 시각으로 둘 경우 rule engine이 비교하는 `현재 / 시작 / 종료` 의미가 흔들릴 수 있다는 지적이 나왔다.
- 추론 기반 condition 매핑은 provider 문자열 계약이 바뀌면 mapper가 오작동하거나 예외를 던질 수 있었다.
- 외부 HTTP/역직렬화 실패가 framework 예외로 그대로 노출되면 weather infra 경계가 흐려졌다.
- 수정 범위를 넓히지 않기 위해 cache fallback, recommendation orchestration, global exception redesign은 이번 단계에서 제외해야 했다.

### 개선 및 결정
- `WeatherSnapshot.targetTime`은 내부가 요청한 시각을 나타내도록 고정해 downstream rule engine 사용성과 domain 의미를 안정화했다.
- provider condition은 DTO의 원문 문자열로 받고, mapper에서만 명시 계약으로 `WeatherType`으로 변환하도록 경계를 분리했다.
- 알 수 없는 condition은 시스템 중단 대신 `UNKNOWN`으로 처리해 weather layer가 외부 문자열 변화에 과도하게 깨지지 않도록 했다.
- failure normalization은 weather infra 내부의 최소 예외로만 정리하고, retry/fallback/cache 정책은 이후 단계로 미뤘다.

---

## 전체 회고

### 잘 된 점
- weather 관련 구현을 application 포트와 infrastructure 어댑터로 분리해 기존 아키텍처 방향을 유지했다.
- `WeatherSnapshot`을 외부 API 구조와 분리된 domain 모델로 유지하면서 현재/시작/종료 3개 시점 사용 구조를 준비했다.
- stub 기반 테스트를 통해 외부 의존성 없이 mapper와 client 동작을 반복 가능하게 검증했다.

### 어려웠던 점
- 문서에는 `WeatherSnapshot.targetTime`의 소유 주체가 직접 적혀 있지 않아, 초기 구현에서 provider 응답 시각과 내부 요청 시각 사이의 선택이 필요했다.
- provider condition 문자열 계약이 문서에 없어서 초기에 추론 기반 매핑이 들어갔고, 이후 리뷰에서 명시 계약으로 좁혀야 했다.
- weather infra 실패를 어디까지 지금 정규화할지, 어디부터 이후 단계로 미룰지 범위를 제한해야 했다.

### 개선 방향
- 다음 단계에서는 현재 정리된 weather infra 경계를 그대로 사용하고, recommendation service는 이 결과만 소비하도록 연결하는 것이 적절하다.
- 실제 weather provider 스펙이 확정되면 `WeatherApiResponse` 필드와 condition 계약을 그 스펙 기준으로 다시 맞출 필요가 있다.
- cache fallback, recommendation orchestration, global exception handling 확장은 이후 단계에서 별도로 다루는 것이 범위상 적절하다.
