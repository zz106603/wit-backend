# 통합 테스트 가이드

## 1. 이 프로젝트에서 통합 테스트의 의미

이 프로젝트에서 통합 테스트는 Spring 컨텍스트 안에서 여러 레이어를 함께 붙여
추천 API 흐름이 실제 조립 기준으로 동작하는지 검증하는 테스트다.

대상 흐름:

- controller
- exception handler
- application service orchestration
- domain rule engine
- cache/fallback wiring

즉, 단일 클래스 로직보다
`API 요청 → 서비스 조립 → fallback/오류 처리 → 응답 변환`
전체 연결을 확인하는 목적이다.

---

## 2. 테스트 범위

### 포함

- 추천 API 정상 흐름
- 추천 API fallback 흐름
- 추천 API 오류 매핑
- Spring bean 조립 결과
- 응답 필드와 fallback 의미 반영 여부

### 제외

- 실제 Google API 호출
- 실제 Weather API 호출
- 실제 Gemini API 호출
- 실제 DB 연동
- 성능/부하 테스트
- iOS 클라이언트 테스트

---

## 3. 다른 테스트와의 차이

### unit test

- 클래스 하나의 규칙/메서드만 검증
- 예: rule engine 경계값 검증

### stub-based test

- 특정 adapter 또는 변환 로직만 고립해서 검증
- 예: weather mapper, HTTP stub 기반 client 테스트

### 이전 E2E test

- 주로 application/service 내부 흐름 중심
- 컨트롤러, 예외 응답, 보안, MockMvc까지는 포함하지 않음

### 통합 테스트

- Spring 기반 실제 bean 조립 상태에서
- controller + service + exception handling + fallback 의미를 함께 검증

---

## 4. 무엇을 mock/stub 하는가

실제 외부 연동과 비결정적 요소는 mock/stub 한다.

대상:

- `GoogleCalendarClient`
- `GoogleOAuthClient`
- `GooglePlacesLocationResolver`
- `GeminiApiClient`
- `HttpWeatherClient`
- `WeatherCache`
- `CurrentLocationProvider`
- `LocationResolutionCache`
- `RecommendationCache`
- `SummaryGenerator`
- `GoogleIntegrationUserProvider`

이유:

- 외부 API 호출 차단
- 테스트 반복 가능성 확보
- fallback 시나리오를 결정적으로 재현

참고:

- Google integration 저장은 현재 구현된 in-memory repository를 사용한다.

---

## 5. 핵심 시나리오

현재 구현 기준 핵심 통합 시나리오는 다음과 같다.

- 홈 추천 API 정상 흐름
- 이벤트 상세 추천 API 정상 흐름
- 위치 해석 실패 후 현재 위치 fallback
- 날씨 실패 후 latest cache fallback
- cache miss 후 safe default fallback
- summary 생성 실패 후 fallback summary
- Google 재연동 필요 `401`
- Google 외부 장애 `503`
- 존재하지 않는 `eventId`의 `404`

또한 AI fallback wiring은 별도 Spring 기반 테스트에서 확인한다.

cache 검증 기준:

- API 응답 검증
  - `weatherSource=NORMAL|CACHE|SAFE_DEFAULT`
  - `weatherFallbackApplied`
  - `fallbackNotice`
  - safe default 시 weather snapshot 3종이 `null`
- 단위 테스트 검증
  - location cache: key 정규화, TTL, hit/miss, `FAILED` 결과 저장
  - weather cache: current/forecast/latest key, TTL, latest fallback 경로
  - recommendation cache: key, TTL, hit 시 전체 흐름 미재실행, miss 후 저장
- 현재 MVP는 location/recommendation cache hit 여부를 API 필드로 직접 노출하지 않는다

---

## 6. 테스트 구조

현재 관련 구조:

- `src/test/java/com/yunhwan/wit/support/IntegrationTestSupport.java`
- `src/test/java/com/yunhwan/wit/presentation/api/recommendation/RecommendationApiIntegrationTest.java`
- `src/test/java/com/yunhwan/wit/application/recommendation/RecommendationServiceAiFallbackWiringTest.java`

역할:

- `IntegrationTestSupport`
  - `@SpringBootTest`
  - `test` 프로필 활성화
- `RecommendationApiIntegrationTest`
  - MockMvc 기반 추천 API 통합 테스트
- `RecommendationServiceAiFallbackWiringTest`
  - recommendation 조립 경로에서 AI fallback wiring 확인

---

## 7. 로컬 실행 방법

전체 테스트:

```bash
./gradlew test
```

추천 API 통합 테스트만 실행:

```bash
./gradlew test --tests 'com.yunhwan.wit.presentation.api.recommendation.RecommendationApiIntegrationTest'
```

AI fallback wiring 테스트만 실행:

```bash
./gradlew test --tests 'com.yunhwan.wit.application.recommendation.RecommendationServiceAiFallbackWiringTest'
```

---

## 8. 이후 확장 원칙

- 실제 외부 API는 계속 호출하지 않는다.
- 새 통합 테스트도 Spring bean 조립과 API 의미 검증에 집중한다.
- business rule 자체 검증은 unit test에 둔다.
- adapter 변환/HTTP 계약 검증은 stub-based test에 둔다.
- 통합 테스트에서는 정상/폴백/오류 중 하나의 연결 의미가 분명한 시나리오만 추가한다.
- 응답 필드 의미가 바뀌면 API 문서와 통합 테스트를 함께 갱신한다.
