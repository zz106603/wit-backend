# 운영 런북

## 1. 운영 대상

- Spring Boot 백엔드 1개
- MySQL 1개
- Redis 1개
- 외부 연동
  - Google OAuth / Calendar
  - Google Places
  - Open-Meteo
  - Gemini

---

## 2. 필수 구성요소

애플리케이션 기동에 필요한 최소 구성:

- Java 21
- MySQL
- Redis

외부 연동까지 실제 검증하려면 추가 필요:

- Google OAuth 설정
- Google Places API Key
- Gemini API Key / Model

---

## 3. 필수 환경 변수

### 최소 기동

- `SPRING_PROFILES_ACTIVE`
- `MYSQL_PORT`
- `MYSQL_DATABASE`
- `MYSQL_USER`
- `MYSQL_PASSWORD`
- `REDIS_PORT`

### 외부 연동 운영

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `GOOGLE_REDIRECT_URI`
- `GOOGLE_PLACES_API_KEY`
- `GEMINI_API_KEY`
- `GEMINI_MODEL`

### 운영 시 같이 확인할 값

- `CURRENT_LOCATION_DISPLAY_LOCATION`
- `CURRENT_LOCATION_LAT`
- `CURRENT_LOCATION_LNG`
- `LOCATION_CACHE_TTL`
- `WEATHER_CACHE_TTL`
- `RECOMMENDATION_CACHE_TTL`
- `OPEN_METEO_BASE_URL`
- `LOG_LEVEL`
- `LOG_PATH`

---

## 4. 로컬 실행

### 실행 순서

```bash
docker-compose up -d mysql redis
./gradlew bootRun
```

### 기본 확인

- `http://localhost:8080/swagger-ui/index.html`
- `http://localhost:8080/v3/api-docs`

### 로컬 수동 확인

1. `SPRING_PROFILES_ACTIVE=local` 확인
2. MySQL 연결 가능 여부 확인
3. Redis 연결 가능 여부 확인
4. Swagger UI 접근 확인
5. 임의 API 호출 후 `X-Trace-Id` 헤더 확인
6. 같은 요청 로그에서 같은 `traceId=` 값 확인

---

## 5. 배포 절차

### 배포 전 준비

- 운영 환경 변수 주입 확인
- MySQL / Redis 연결 정보 확인
- `GOOGLE_REDIRECT_URI`가 실제 서비스 URL과 일치하는지 확인
- 외부 API Key 누락 여부 확인

### 배포

```bash
./gradlew bootJar
java -jar build/libs/*.jar
```

### 배포 직후

- 프로세스 기동 확인
- Swagger/OpenAPI 접근 확인
- 로그 파일 생성 확인
- 홈 추천 API 1회 호출
- Google 로그인 URL API 1회 호출

---

## 6. 배포 후 스모크 체크

### 공통

- `GET /swagger-ui/index.html`
- `GET /v3/api-docs`

### 추천 API

- `GET /api/recommendations/home`
- `GET /api/recommendations/events/{eventId}`

확인 포인트:

- 200 또는 문서화된 오류 코드 반환
- `X-Trace-Id` 헤더 존재
- fallback 응답이면 구조화 필드가 문서와 일치

### Google 연동

- `GET /api/integrations/google/login-url`
- callback 이후 `connected=true` 확인

---

## 7. 외부 API 장애 대응

### Google

- `GOOGLE_401`
  - 의미: 재연동 필요
  - 확인: refresh token/redirect URI/사용자 재인증 필요 여부
- `GOOGLE_503`
  - 의미: Google 외부 연동 장애
  - 확인: 외부 API 상태, 네트워크, access token 갱신 흐름

### Google Places

- 위치 해석 실패 시 추천이 중단되지 않고 current location fallback으로 가는지 확인
- 응답에서 `locationFallbackApplied=true`와 `originalLocationResolution` 확인

### Weather

- weather API 실패 시 순서:
  - latest cache 사용 가능 → `weatherSource=CACHE`
  - latest cache도 없음 → `weatherSource=SAFE_DEFAULT`
- `weatherFallbackApplied=true`면 weather snapshot 3종이 `null`인지 확인

### Gemini

- summary 생성 실패 시 추천 자체는 계속 반환되어야 함
- summary가 deterministic fallback 문장으로 내려오는지 확인

---

## 8. 캐시 해석

### location cache

- 목적: location 해석 비용 절감
- API 응답에서 cache hit/miss를 직접 노출하지 않음
- 운영 확인은 로그와 테스트 기준으로 본다

### weather cache

- API 응답에서만 cache usage를 직접 해석 가능
- `weatherSource=NORMAL`
  - 정상 조회 기준
- `weatherSource=CACHE`
  - exact cache hit 또는 latest cache fallback 포함
- `weatherSource=SAFE_DEFAULT`
  - weather 입력 확보 실패

### recommendation cache

- 목적: 최종 추천 결과 재사용
- API 응답에서 cache hit/miss를 직접 노출하지 않음

### Redis 장애 시 기대 동작

- recommendation 핵심 흐름은 cache 없이도 계속 동작해야 한다
- cache read/write 실패만으로 추천 API 전체가 실패하면 안 된다
- 이 경우는 degraded 상태로 보고, cache 최적화만 사라진 것으로 해석한다
- 단, weather API도 함께 실패하면 `weatherSource=SAFE_DEFAULT` 비율이 올라갈 수 있다

### 기본 TTL

- location: `24h`
- weather: `1h`
- recommendation: `30m`

---

## 9. 로그 확인

### local

- 콘솔 로그만 사용

### local 외 프로필

- `${LOG_PATH}/application.log`
- `${LOG_PATH}/application-error.log`

### 기본 확인 포인트

- `traceId=`
- `GOOGLE_401`, `GOOGLE_503` 대응 로그
- location fallback 관련 로그
- weather fallback 관련 로그
- cache read/write 실패 로그
- Redis 연결/읽기/쓰기 실패 로그

---

## 10. 자주 보는 수동 점검 시나리오

### 앱은 뜨지만 추천이 비정상

- Google 연동 상태 확인
- 홈 추천 API 응답 코드 확인
- `fallbackNotice`, `weatherSource`, `locationFallbackApplied` 확인

### 추천이 모두 safe default로 내려감

- weather API 접근 실패 여부 확인
- Redis 연결 상태 확인
- `WEATHER_CACHE_TTL` 및 latest cache 존재 가능성 확인

### Redis가 내려감

- Redis 관련 error/warn 로그 확인
- 추천 API가 cache 없이 계속 동작하는지 확인
- `weatherSource=CACHE` 감소, `SAFE_DEFAULT` 증가 여부 확인
- 이 상태는 우선 degraded로 보되, 추천 API 자체가 실패하면 system failure로 본다

### 위치 fallback이 급증함

- Google Places API Key 확인
- Places 외부 장애 여부 확인
- 입력 location 품질 문제 여부 확인

### traceId로 요청 추적이 안 됨

- 응답 헤더 `X-Trace-Id` 확인
- 로그 패턴에 `traceId=` 포함 여부 확인

---

## 11. 운영 체크리스트

- MySQL 접속 가능
- Redis 접속 가능
- 앱 기동 성공
- Swagger/OpenAPI 접근 가능
- `X-Trace-Id` 헤더 확인 가능
- Google 로그인 URL API 정상
- 홈 추천 API 정상 또는 문서화된 fallback 응답
- `weatherSource` 해석 가능
- 로그 파일 또는 콘솔 로그 확인 가능
- 오류 로그 별도 확인 가능
