# API 응답 스펙

최신 기준:

- Swagger UI: `/swagger-ui/index.html`
- OpenAPI JSON: `/v3/api-docs`

## 1. 공통 원칙

- 추천 성공 응답의 사용자용 핵심 필드는 `eventId`, `title`, `startAt`, `endAt`, `location`, `needUmbrella`, `recommendedOutfitText`, `summary`다.
- `summary`는 현재 구현의 `OutfitDecision.aiSummary`를 외부 API 필드명으로 노출한 값이다.
- `summary`는 우산/옷차림 등 추천 내용만 전달하는 사용자용 문구다.
- `location`은 현재 구현의 해석 결과 표시 위치(`resolvedLocation.displayLocation`)다.
- `locationFallbackApplied=true`면 일정 위치 해석에 실패해 현재 위치 기준으로 추천한 경우다.
- 이 경우 해당 응답은 목적지 기반 추천이 아니며, `location`도 현재 위치 표시값이다.
- 이 경우 `originalLocationResolution`에 fallback 이전의 원래 `FAILED` 위치 해석 결과를 함께 담는다.
- `weatherFallbackApplied=true`면 날씨 조회 실패 후 latest cache도 없어 safe default 추천을 반환한 경우다.
- `weatherSource=NORMAL`이면 응답 생성에 사용된 날씨 입력이 정상 조회 기준이다.
- `weatherSource=CACHE`면 응답 생성에 사용된 날씨 입력 중 하나 이상이 캐시 데이터다. exact cache hit와 API 실패 후 latest cache fallback을 모두 포함한다.
- `weatherSource=SAFE_DEFAULT`면 start/end 기준 날씨를 확보하지 못해 safe default 추천을 사용한 경우다.
- `fallbackNotice`는 fallback 의미를 바로 이해할 수 있게 추가한 사용자용 안내 문구다.
- fallback/degraded-state 설명의 1차 채널은 `fallbackNotice`, `locationFallbackApplied`, `weatherFallbackApplied`, `weatherSource` 같은 구조화 필드다.
- `summary`는 fallback/degraded-state를 주된 방식으로 설명하지 않으며, fallback 여부 판단은 반드시 구조화 필드와 `fallbackNotice`로 한다.
- API 응답으로 구분 가능한 cache usage는 weather cache에 한정한다. 이 구분은 `weatherSource`와 `weatherFallbackApplied`로 표현한다.
- 현재 MVP는 location/recommendation cache hit 여부를 별도 응답 필드로 노출하지 않는다. 두 캐시는 내부 최적화이며 검증은 테스트로 수행한다.

## 2. 엔드포인트

### GET `/api/integrations/google/login-url`

- 응답: `loginUrl`

### POST `/api/integrations/google/callback`

- 요청 본문:
  - `code`
  - `state`
- 응답:
  - `connected`
  - `email`
  - `calendarEventCount`

### GET `/api/recommendations/home`

- 응답: `recommendations[]`
- 홈 API는 각 일정 추천을 독립적으로 생성한다.
- 일부 일정에서 추천 생성에 실패하면 전체 요청을 실패시키지 않고 해당 항목만 응답 목록에서 제외할 수 있다.
- 따라서 응답 목록은 조회된 전체 후보 일정 수와 항상 같다고 가정하지 않는다.
- 각 항목 공통 핵심 필드:
  - `eventId`
  - `title`
  - `startAt`
  - `endAt`
  - `location`
  - `needUmbrella`
  - `recommendedOutfitText`
  - `summary`
- 상세 보조 필드:
  - `locationFallbackApplied`
  - `weatherFallbackApplied`
  - `weatherSource`
  - `fallbackNotice`
  - `rawLocation`
  - `locationResolution`
  - `originalLocationResolution`
  - `umbrellaReason`
  - `outfitReason`
  - `temperatureGap`
  - `weatherChangeSummary`
  - `currentWeather`
  - `startWeather`
  - `endWeather`

### GET `/api/recommendations/events/{eventId}`

- 응답: 홈 항목과 동일한 단건 상세 구조
- `eventId`가 향후 7일/최대 3건 조회 결과에 없으면 `404 RECOMMENDATION_404`

## 3. 오류 응답

공통 오류 구조:

- `code`
- `message`
- `errors`

대표 코드:

- `COMMON_400`: 잘못된 요청
- `RECOMMENDATION_404`: 추천 대상 일정 없음
- `GOOGLE_401`: Google 재연동 필요
- `GOOGLE_503`: Google 연동 장애
- `COMMON_500`: 서버 내부 오류

추천 API별 오류 노출:

- `GET /api/recommendations/home`
  - `401 GOOGLE_401`
  - `503 GOOGLE_503`
  - `500 COMMON_500`
- `GET /api/recommendations/events/{eventId}`
  - `404 RECOMMENDATION_404`
  - `401 GOOGLE_401`
  - `503 GOOGLE_503`
  - `500 COMMON_500`

## 4. 현재 구현 기준 주의사항

- `summary`는 AI summary 실패 시 deterministic fallback 문장으로 대체될 수 있다.
- `summary`는 recommendation content 전용이며, fallback/degraded-state를 대표하는 필드로 해석하지 않는다.
- `weatherFallbackApplied=true`면 `weatherSource=SAFE_DEFAULT`이며 `currentWeather`, `startWeather`, `endWeather`는 `null`이다.
- `weatherFallbackApplied=false`여도 `currentWeather`는 `null`일 수 있다. 이 경우 `startWeather`와 `endWeather`가 있으면 추천은 계속 진행되고 current 기반 보정만 생략된다.
- 실제 현재 위치가 없으면 `currentWeather`는 목적지 날씨로 대체하지 않고 `null`로 유지한다.
- `weatherSource=CACHE`일 때 `fallbackNotice`는 "실시간 호출 대신 캐시된 날씨 데이터를 사용했습니다."다.
- `weatherSource`는 `NORMAL`, `CACHE`, `SAFE_DEFAULT` 중 하나다.
- `POST /api/integrations/google/callback`이 iOS용 기준 엔드포인트다.
- 기존 GET callback도 호환용으로 유지되지만, 신규 클라이언트 계약은 POST 기준으로 본다.
