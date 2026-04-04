# Step 18 AI 협업 개발 로그

## 개요
- 이 문서는 Step 18의 구현 과정과 이후 설계 판단을 기록하기 위한 개발 로그다.
- 포함 범위는 Google OAuth 진입점 추가, Google Calendar adapter 구조 추가, OAuth 연동 정보 저장 구조 준비, CalendarEvent 중심 연결 흐름 준비다.
- AI는 설계 문서를 기준으로 코드 생성과 구조 정리를 보조하는 수단으로만 사용했고, 실제 Google 연동 흐름은 application orchestration과 infrastructure adapter 분리 구조로 구현했으며 recommendation 판단 로직과 rule engine 책임은 그대로 유지했다.

---

## Step 18: Google OAuth / Calendar adapter

### 사용한 프롬프트
<details>
<summary>Step 18 프롬프트</summary>

```text
Read AGENTS.md and all documents in /docs before starting.

Step 18: Google OAuth / Calendar adapter 구현

Implement only Step 18 for this project.

Scope:
- Add Google OAuth integration entry points
- Add Google Calendar adapter structure
- Implement application/service flow needed to connect OAuth and Calendar retrieval
- Keep the existing hybrid design intact
- Follow the confirmed API structure for Google integration and recommendation flow
- Prepare the code so that Step 19 can connect real external API details cleanly

Requirements:
- Do NOT change the project architecture
- Do NOT expand AI responsibility
- Do NOT move decision logic into AI
- Do NOT remove or weaken the rule-engine-centered design
- Do NOT implement unrelated business expansion
- Keep MVP scope fixed
- Separate domain / application / infra responsibilities clearly
- Prefer interfaces/ports + adapter structure where appropriate
- Google integration should fit the confirmed API direction:
  - GET /api/integrations/google/login-url
  - POST /api/integrations/google/callback
- Calendar adapter should align with the existing CalendarEvent flow and recommendation pipeline
- User / google integration persistence should fit the existing storage direction
- Make sure token-expiry / exception / fallback polishing is NOT overextended here because that belongs mainly to later steps
- Keep implementation minimal but runnable/testable at code level
```
</details>

### 수행 결과
- `presentation/api/integration`에 `GET /api/integrations/google/login-url`, `POST /api/integrations/google/callback` endpoint와 request/response DTO를 추가했다.
- `application/google`에 `GoogleOAuthClient`, `GoogleCalendarClient`, `GoogleIntegrationRepository` 포트를 추가하고, 이를 조합하는 `GoogleIntegrationService`를 구현했다.
- `application/google`에 Google 연동 정보와 OAuth token 표현용 record를 추가해 callback 이후의 최소 연동 상태를 표현하도록 정리했다.
- `infrastructure/google`에 OAuth properties, stub OAuth client, stub Calendar client, in-memory integration repository를 추가해 외부 API 세부 구현 없이 adapter 구조만 먼저 준비했다.
- `infrastructure/config/GoogleIntegrationConfig`를 추가해 Google 관련 bean 조립을 분리했다.
- Step 18 보완으로 `GoogleIntegrationUserProvider`를 추가해 service 내부의 하드코딩 user 식별을 제거하고 주입형으로 정리했다.
- Step 18 보완으로 controller web test를 추가해 login-url 200, callback 정상 200, callback invalid 400을 검증했다.
- Step 18 보완으로 전역 보안 선행구현은 제거해 범위를 Google integration 진입점 준비에만 제한했다.

### 발생한 문제 / 애매한 부분
- 문서에는 Google OAuth 진입 API 구조와 Google 연동 정보 저장 방향은 있지만, 실제 사용자 인증 체계나 user 식별 방식은 아직 구체화되어 있지 않았다.
- callback 이후 실제 Google token exchange, token refresh, expiry 처리, error mapping을 어디까지 구현할지 선택이 필요했다.
- Calendar adapter가 Step 18에서 실제 일정을 가져와야 하는지, 아니면 CalendarEvent 중심 구조만 먼저 열어야 하는지 범위 판단이 필요했다.
- Step 17까지는 recommendation cache까지 완료된 상태였으므로, Google 연동 추가가 기존 recommendation flow나 cache 책임을 건드리지 않도록 분리할 필요가 있었다.

### 개선 및 결정
- Step 18은 “실제 외부 API 완성”이 아니라 “integration structure setup”으로 제한했다. 따라서 Google OAuth / Calendar는 포트와 adapter 구조를 먼저 만들고 실제 외부 통신 세부는 Step 19로 넘겼다.
- `GoogleIntegrationService`는 OAuth 진입 URL 생성, callback 처리, integration 저장, CalendarEvent 조회 연결만 orchestration 하도록 두고 business decision은 추가하지 않았다.
- user 식별은 service 내부 상수로 고정하지 않고 `GoogleIntegrationUserProvider`로 분리해 Step 19에서 실제 사용자 연결 시 큰 리팩터링 없이 교체할 수 있게 했다.
- Calendar adapter는 `CalendarEvent`를 반환하는 포트 형태로 두어 기존 `CalendarEvent -> ResolvedLocation -> WeatherSnapshot -> Rule Engine -> OutfitDecision -> AI Summary` 흐름에 자연스럽게 연결되도록 했다.
- Google 연동 저장은 문서의 MySQL 방향을 해치지 않도록 repository 포트로만 열어 두고, 이번 단계에서는 임시 in-memory adapter만 사용했다.
- Step 17의 recommendation cache 구조와 충돌하지 않도록 Google integration은 recommendation service 바깥의 upstream 입력 준비 단계로만 추가했다.
- AI 역할은 전혀 확장하지 않았다. Google OAuth / Calendar 흐름에는 AI를 사용하지 않았고, 기존 허용 영역인 location 해석과 summary 생성만 유지했다.
- rule engine, weather retrieval, recommendation cache, fallback 정책은 이번 단계에서 변경하지 않았다.

---

## 전체 회고

### 잘 된 점
- Google 연동을 `presentation -> application -> infrastructure` 구조로 추가해 기존 layered architecture와 package 방향을 유지했다.
- Calendar adapter를 `CalendarEvent` 중심으로 설계해 recommendation pipeline 앞단 입력 공급 구조를 분명하게 만들었다.
- Step 17까지 완성된 recommendation cache와 recommendation orchestration을 수정하지 않고, Google integration을 그 이전 단계의 입력 연결 책임으로만 제한했다.
- 실제 외부 API 완성 없이도 endpoint, service, adapter, 저장 포트, 테스트까지 최소 runnable 구조를 갖춰 Step 19 준비 상태를 만들었다.

### 어려웠던 점
- Google OAuth는 보통 실제 redirect, token exchange, refresh, user 식별까지 함께 묶이기 쉬워 Step 18 범위를 넘지 않도록 의도적으로 절제해야 했다.
- user 식별이 문서에 아직 구체화되지 않아 service 내부 하드코딩과 별도 추상화 사이에서 최소 설계를 선택해야 했다.
- Calendar adapter를 너무 얕게 만들면 다음 단계 연결이 불명확해지고, 너무 깊게 만들면 Step 19 범위를 선구현하게 되어 균형이 필요했다.

### 개선 방향
- Step 19에서는 현재 포트 구조를 유지한 채 stub OAuth client와 stub Calendar client를 실제 Google API adapter로 교체하는 것이 적절하다.
- user 식별 provider는 이후 실제 인증/사용자 컨텍스트가 정해지면 그 구현만 바꾸고 application service 시그니처는 유지하는 방향이 적절하다.
- token refresh, expiry handling, Google API 예외 매핑, 실제 integration persistence는 Step 18 구조를 그대로 활용해 다음 단계에서 채우는 것이 맞다.
