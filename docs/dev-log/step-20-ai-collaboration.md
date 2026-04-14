# Step 20 AI 협업 개발 로그

## 1. Step 20 개요
- Step 20의 목표는 외부 연동 실패, 토큰 만료, AI 실패, 날씨 조회 실패가 있어도 recommendation을 가능한 한 계속 제공하도록 failure handling과 fallback 정책을 완성하는 것이다.
- 이 단계는 단순 예외 추가보다 `언제 계속 제공할지`, `언제 degraded로 내릴지`, `언제 사용자 액션이 필요한지`를 구분하는 데 초점을 둔다.
- 구현 범위는 다음 다섯 항목으로 마무리됐다.
  - Google token expiry / refresh 계약
  - Calendar fetch 경로의 refresh + retry
  - weather latest-cache fallback
  - presentation 레이어의 재인증 필요 / 오류 처리
  - recommendation 응답의 degraded / fallback 표현

---

## 2. 전체 설계 원칙
- recommendation은 가능한 계속 제공한다.
- external failure는 가능한 경우 degraded 상태나 fallback으로 흡수한다.
- 사용자 액션이 필요한 실패와 시스템 외부 장애는 구분한다.
  - user-action-needed: Google 재연동 필요
  - system failure: Google/Weather 외부 통신 장애, 일시적 API 오류
- fallback 우선순위는 다음 순서를 따른다.
  - location 실패: 현재 위치 기준 fallback
  - weather 실패: latest cache 우선, 없으면 safe default outfit
  - AI 실패: 결과를 무시하고 rule-based 흐름 유지

---

## 3. 세부 구현 내용

### 3.1 Google token expiry / refresh + retry
- Google access token 상태는 `ACTIVE`, `EXPIRED_REFRESHABLE`, `EXPIRED_REAUTH_REQUIRED`로 구분한다.
- calendar fetch 직전에 token 상태를 평가한다.
  - `ACTIVE`: 그대로 일정 조회 진행
  - `EXPIRED_REFRESHABLE`: refresh 후 같은 요청을 이어서 수행
  - `EXPIRED_REAUTH_REQUIRED`: 즉시 재연동 필요 예외로 종료
- refresh 책임은 `GoogleOAuthClient`에 두고, service는 refresh 결과를 저장한 뒤 동일 요청을 계속 수행한다.
- refresh 불가 또는 refresh 거절은 `Google 재연동 필요`로 수렴시키고, 외부 Google 장애는 `연동 불가`로 구분한다.

### 3.2 Weather fallback 정책
- weather 경로는 `정상 조회 -> latest cache fallback -> safe default` 순서로 동작한다.
- weather API 호출이 성공하면 정상 weather snapshot으로 rule engine을 실행한다.
- weather API 호출이 실패하면 `CachingWeatherClient`가 latest cached weather를 먼저 조회한다.
- latest cache가 있으면 cached weather로 recommendation을 계속 생성한다.
- latest cache도 없으면 `RecommendationService`가 safe default recommendation 경로로 내려간다.
- 이 흐름으로 weather 장애 시에도 recommendation이 가능한 한 끊기지 않도록 유지한다.

### 3.3 AI failure 정책
- location AI 실패는 location resolution 실패로 수렴시키고, 최종적으로 현재 위치 fallback 경로로 이어진다.
- summary AI 실패는 recommendation 자체를 중단하지 않고 deterministic fallback summary를 사용한다.
- AI 실패가 우산/옷차림/기온 판단을 대신하지 않도록 rule-based 흐름은 그대로 유지한다.
- 즉, AI는 location 자연어 해석과 summary 생성에만 관여하고, recommendation 결정은 계속 코드와 규칙 엔진이 담당한다.

### 3.4 Presentation error handling
- presentation 레이어는 `Google 재연동 필요`와 `Google 외부 연동 불가`를 별도 응답으로 구분한다.
- `ErrorCode` 기준으로 다음처럼 표현한다.
  - `GOOGLE_REAUTH_REQUIRED`: 401
  - `GOOGLE_INTEGRATION_UNAVAILABLE`: 503
- `GlobalExceptionHandler`는 두 예외를 일관된 API error response로 매핑한다.
- 이로써 presentation에서는 사용자 액션 필요 상태와 일시적 외부 장애를 명확히 구분할 수 있다.

### 3.5 Recommendation degraded response
- recommendation 응답은 degraded/fallback 상태를 응답 필드로 직접 드러낸다.
- 최소 구분 기준은 다음과 같다.
  - `locationFallbackApplied`: location fallback 적용 여부
  - `weatherFallbackApplied`: safe default fallback 적용 여부
  - `weatherSource`: `NORMAL`, `CACHE`, `SAFE_DEFAULT`
- 이를 통해 클라이언트는
  - location fallback이 있었는지
  - weather가 정상 조회인지, cache 기반인지, safe default인지
  를 명확히 구분할 수 있다.

---

## 4. 전체 failure handling 흐름 요약
- 정상 경로
  - Google token 유효
  - calendar 조회 성공
  - location resolve 성공
  - weather 조회 성공
  - rule engine + summary로 일반 recommendation 반환
- degraded 경로
  - location 실패 시 현재 위치 기준으로 계속 진행
  - weather 실패 시 latest cache가 있으면 cache 기반 recommendation 반환
  - latest cache도 없으면 safe default recommendation 반환
  - summary AI 실패 시 fallback summary로 계속 반환
- user action required 경로
  - Google token이 재연동 필요 상태이거나 refresh가 재인증 필요로 끝나면 recommendation 조회를 중단하고 재연동 필요 응답을 반환

---

## 5. 결과
- Step 20에서는 Google token 만료 처리, refresh + retry, weather latest-cache fallback, presentation error handling, recommendation degraded response 표현이 모두 정리됐다.
- 구현 결과 recommendation은 가능한 한 계속 제공되고, 중단이 필요한 경우는 `사용자 액션이 필요한 경우`로 한정된다.
- 즉, Step 20은 `추천이 끊기지 않는 구조`를 목표로 한 failure handling과 fallback 정책을 실제 동작 기준으로 마무리한 단계다.
