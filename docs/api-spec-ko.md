# API 응답 스펙

최신 기준:

- Swagger UI: `/swagger-ui/index.html`
- OpenAPI JSON: `/v3/api-docs`

## 1. 공통 원칙

- 추천 성공 응답의 사용자용 핵심 필드는 `eventId`, `title`, `startAt`, `endAt`, `location`, `needUmbrella`, `recommendedOutfitText`, `summary`다.
- `summary`는 현재 구현의 `OutfitDecision.aiSummary`를 외부 API 필드명으로 노출한 값이다.
- `location`은 현재 구현의 해석 결과 표시 위치(`resolvedLocation.displayLocation`)다.
- `locationFallbackApplied=true`면 일정 위치 해석에 실패해 현재 위치 기준으로 추천한 경우다.
- `weatherFallbackApplied=true`면 날씨 조회 실패로 safe default 추천을 반환한 경우다.
- `weatherSource=CACHE`면 실시간 대신 최신 캐시 날씨를 사용한 경우다.
- `fallbackNotice`는 fallback 의미를 바로 이해할 수 있게 추가한 사용자용 안내 문구다.

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

## 4. 현재 구현 기준 주의사항

- `summary`는 AI summary 실패 시 deterministic fallback 문장으로 대체될 수 있다.
- `weatherFallbackApplied=true`면 `currentWeather`, `startWeather`, `endWeather`는 `null`이다.
- `weatherSource`는 `NORMAL`, `CACHE`, `SAFE_DEFAULT` 중 하나다.
- `POST /api/integrations/google/callback`이 iOS용 기준 엔드포인트다.
- 기존 GET callback도 호환용으로 유지되지만, 신규 클라이언트 계약은 POST 기준으로 본다.
