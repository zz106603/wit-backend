# 배포 체크리스트

## 1. 배포 전

- Java 21 환경 확인
- MySQL 실행 확인
- Redis 실행 확인
- 운영 환경 변수 주입 확인
- `GOOGLE_REDIRECT_URI` 운영 URL 일치 확인
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` 확인
- `GOOGLE_PLACES_API_KEY` 확인
- `GEMINI_API_KEY`, `GEMINI_MODEL` 확인
- `LOCATION_CACHE_TTL`, `WEATHER_CACHE_TTL`, `RECOMMENDATION_CACHE_TTL` 확인
- `LOG_PATH` 확인
- `./gradlew test` 또는 최소 추천 API 테스트 통과 확인

---

## 2. 배포 시

- `./gradlew bootJar`
- 새 jar 배포
- 프로세스 재기동
- 기동 직후 에러 로그 확인

---

## 3. 배포 직후 스모크 체크

- `GET /swagger-ui/index.html`
- `GET /v3/api-docs`
- `GET /api/integrations/google/login-url`
- `GET /api/recommendations/home`
- 응답 헤더 `X-Trace-Id` 확인

추천 API 확인 포인트:

- 200 응답 또는 문서화된 오류 응답
- fallback이면 `weatherSource`, `weatherFallbackApplied`, `fallbackNotice` 확인
- location fallback이면 `locationFallbackApplied`, `originalLocationResolution` 확인

---

## 4. 배포 후 1차 관찰

- `application.log` 확인
- `application-error.log` 확인
- Google 관련 오류 급증 여부 확인
- weather fallback 급증 여부 확인
- Redis 연결 오류 여부 확인

---

## 5. 롤백 판단 기준

### 배포 유지 가능

- Swagger/OpenAPI 접근 가능
- `GET /api/integrations/google/login-url` 정상
- `GET /api/recommendations/home`가 200 또는 문서화된 오류 응답 반환
- fallback이 있더라도 문서화된 범위(`weatherSource`, `fallbackNotice`) 안에서 동작

### 롤백 필요

- 스모크 체크 핵심 API가 실패
- 추천 API가 문서화되지 않은 5xx로 반복 실패
- fallback이 아닌 시스템 오류가 연속 발생
- 배포 직후 `weatherSource=SAFE_DEFAULT` 또는 location fallback이 비정상적으로 급증

---

## 6. 수동 대응 기준

- `GOOGLE_401` 다수 발생
  - 사용자 재연동 경로 확인
- `GOOGLE_503` 다수 발생
  - 외부 Google 장애 여부 확인
- `weatherSource=SAFE_DEFAULT` 급증
  - weather API 또는 Redis 상태 확인
- 위치 fallback 급증
  - Google Places 상태와 API Key 확인

---

## 7. 이번 단계에서 포함하지 않는 것

- 무중단 배포
- Kubernetes / Terraform
- HA 구성
- 모니터링 플랫폼 구축
- 자동 롤백 시스템
