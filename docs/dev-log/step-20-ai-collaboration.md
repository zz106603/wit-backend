# Step 20 AI 협업 개발 로그

## 1. Step 20 개요
- Step 20의 목표는 외부 연동 실패, 토큰 만료, AI 실패, 날씨 조회 실패가 있어도 recommendation을 가능한 한 계속 제공하도록 failure handling과 fallback 정책을 정리하는 것이다.
- 이 단계는 단일 기능 구현보다 `언제 중단하고`, `언제 fallback하며`, `어떤 경우 사용자 액션이 필요한지`를 구분하는 정책 정리가 중심이다.
- 현재 Step 20 범위는 다음 다섯 항목으로 구성된다.
  - Google token expiry / refresh 계약
  - Calendar fetch 경로의 refresh + retry
  - weather latest-cache fallback
  - presentation 레이어의 재인증 필요 / 오류 처리 정책
  - recommendation 응답의 degraded/fallback 표현 정리

---

## 2. 전체 설계 원칙
- recommendation은 가능한 계속 제공한다.
- 외부 연동 실패는 무조건 전체 실패로 올리지 않고, 가능한 경우 degraded 상태나 fallback으로 처리한다.
- 사용자 액션이 필요한 실패와 시스템 외부 장애는 구분한다.
  - user-action-needed: Google 재연동 필요
  - system failure: Google/Weather 외부 통신 장애, 일시적 API 오류
- fallback 우선순위는 다음 순서를 따른다.
  - location 실패: 현재 위치 기준 fallback
  - weather 실패: latest cache 우선, 없으면 safe default outfit
  - AI 실패: 결과를 무시하고 rule-based 흐름 유지

---

## 3. 세부 작업 항목

### 3.1 Google token expiry / refresh + retry
- 현재는 Google access token 상태를 `ACTIVE`, `EXPIRED_REFRESHABLE`, `EXPIRED_REAUTH_REQUIRED`로 구분하는 계약이 추가되어 있다.
- refresh는 `GoogleOAuthClient` 책임으로 분리했고, refresh 결과는 access token과 만료 시각만 가지는 별도 result로 정리했다.
- service 레벨에서는 calendar fetch 직전에 token 상태를 평가하고,
  - `ACTIVE`면 그대로 진행,
  - `EXPIRED_REFRESHABLE`이면 refresh 후 같은 요청을 계속 수행,
  - `EXPIRED_REAUTH_REQUIRED`이면 즉시 재연동 필요 예외를 발생시키도록 연결했다.
- refresh 중 재연동이 필요한 경우와 외부 Google 장애는 서로 다른 예외로 구분한다.
- 상세 계약과 코드 수준 판단 기준은 dev-log 성격상 여기서 과도하게 반복하지 않고, 구현 세부는 해당 변경 로그와 테스트로 위임한다.

### 3.2 Weather fallback 정책
- Step 20의 weather 목표는 `latest cached data -> safe default outfit` 순서의 fallback 정책을 정리하는 것이다.
- 현재 코드에는 safe default outfit fallback은 이미 존재한다.
- 다만 latest cached weather를 외부 실패 시 재사용하는 정책은 아직 Step 20 범위 안에서 남아 있는 작업이다.
- 이후 구현에서는 weather client / cache 구조를 유지한 채, 외부 조회 실패 시 최신 캐시를 우선 사용하는 방향으로 정리한다.

### 3.3 AI failure 정책
- location AI 실패는 location resolution 실패로 수렴시키고, 최종적으로 현재 위치 fallback 또는 기존 recommendation 흐름으로 이어지게 한다.
- summary AI 실패는 recommendation 자체를 중단하지 않고 deterministic fallback summary를 사용한다.
- 공통 원칙은 AI 실패가 rule-based 판단을 대체하거나 깨지 않도록 하는 것이다.
- 즉, AI는 location 자연어 해석과 최종 summary 생성에만 관여하고, 우산/옷차림/기온 판단은 계속 규칙 엔진이 담당한다.

### 3.4 Presentation / Error handling 정책
- 현재 presentation 레이어는 일반적인 validation / 내부 오류 처리 구조는 갖추고 있다.
- 하지만 Step 20 기준으로는 아직 다음 구분이 충분하지 않다.
  - Google 재연동 필요
  - degraded but usable recommendation
  - 외부 장애로 인한 일시 실패
- 이후 단계에서는 error code와 handler를 정리해 재인증 필요 상태를 일관되게 표현하고, recommendation 응답에도 필요한 범위 내에서 degraded/fallback 상태를 드러내는 방향을 검토한다.

---

## 4. 현재까지 구현된 범위 요약
- Google token 상태 모델이 추가됐다.
- Google refresh 계약과 refresh 결과 모델이 추가됐다.
- Google 재연동 필요 / Google 외부 연동 실패를 application 레벨에서 구분할 수 있게 됐다.
- Calendar fetch 경로에서 refresh + continue same request 흐름이 연결됐다.
- location AI 실패와 summary AI 실패는 recommendation을 중단하지 않도록 정리되어 있다.
- weather safe default fallback은 이미 반영되어 있다.

---

## 5. 남은 작업 (TODO)
- weather 실패 시 latest cache fallback 구현
- presentation 레이어의 재인증 필요 error code / handler 정리
- recommendation 응답의 degraded / fallback 표현 범위 정리
