# Rules Specification

## Temperature → Outfit

- >= 28°C
    - short sleeve
    - shorts
- 23~27°C
    - short sleeve
    - long pants
- 17~22°C
    - long sleeve
    - light outer
- 10~16°C
    - knit / hoodie
    - jacket
- < 10°C
    - coat
    - heavy outer

## Precipitation

- precipitationProbability >= 50%
  → add umbrella

## Condition Adjustment

- strong wind
  → add outer
- snow
  → prioritize warmth

## Priority Rules

1. Safety (rain, snow, extreme cold)
2. Temperature
3. Comfort

## Location Resolution

1. Rule-based extraction first
2. If unresolved or low-confidence → Google Places
3. If still unresolved → AI fallback
4. If still fails → default location

### Calendar Title As Location Candidate

- event `title`은 `location`이 비어 있을 때만 보조 후보로 사용할 수 있다
- 허용:
  - 지역명, 상호명, 주소성 표현
  - 직접적인 장소 지시어(예: `강남역`, `성수동`, `판교 카페`, `테헤란로`, `회사`, `사무실`, `집`, `학교`)
- 비허용:
  - 일반 활동명이나 추상 표현(예: `회의`, `운동`, `약속`, `공부`, `점심`, `저녁`, `회식`)
- title이 모호하거나 추상적인 표현만 담고 있으면 location을 추론하지 않는다
- 이 단계는 deterministic heuristic만 사용하며, title만으로 새로운 의미를 추론하지 않는다

### Google Places Evaluation

- no result: Places 응답이 비어 있으면 `FAILED`
- insufficient result: 응답이 있어도 좌표/이름 누락 또는 입력-결과 정합성 부족이면 `FAILED`
- `RESOLVED`: 주소 정보가 있고 입력과 장소명/주소가 직접 일치하거나 강한 토큰 정합이 확인된 결과
- `APPROXIMATED`: 의미 있는 부분 일치가 있는 근사 결과
- `FAILED`: 목적지로 채택할 수 없는 결과
- Google Places `RESOLVED` 결과는 confidence `0.8` 이상일 때만 충분한 결과로 채택
- Google Places `APPROXIMATED` 결과는 실패가 아니므로 그대로 채택하며 AI fallback 조건으로 보지 않음
- AI fallback은 Google Places 결과가 `FAILED`이거나, `RESOLVED`여도 confidence `0.8` 미만일 때만 트리거된다

## Failure Handling

### Summary vs Fallback Notice
- `summary`는 우산/옷차림 등 recommendation content만 설명한다
- fallback/degraded-state 설명은 `fallbackNotice`와 구조화 상태 필드(`locationFallbackApplied`, `weatherFallbackApplied`, `weatherSource`)가 담당한다
- `summary`는 fallback/degraded-state를 전달하는 주 채널로 사용하지 않는다
- 클라이언트는 fallback 여부를 `summary`가 아니라 구조화 필드와 `fallbackNotice`로 판단해야 한다

### Location Failure
- FAILED면 current location 기준으로 fallback
- 이 경우 응답에서 `locationFallbackApplied=true`로 명시한다
- 이 경우 추천은 목적지 기반 추천이 아니라 current-location fallback 결과다
- 이 경우 메인 `locationResolution`은 fallback에 실제 사용된 위치를 나타내고, `originalLocationResolution`은 원래의 `FAILED` 해석 결과를 보존한다

### Weather Failure
- fallback to latest cached data
- start 또는 end weather가 없으면 → safe default outfit
- start / end weather가 있으면 recommendation은 계속 진행한다
- current weather만 없으면 current 기반 보정만 생략한다
- 실제 current location이 없으면 destination weather를 currentWeather로 대체하지 않는다

### AI Failure
- ignore AI result
- continue with rule-based flow

## Calendar Event Time Policy

- calendar event의 시간 source of truth는 Google Calendar structured field(start.dateTime / end.dateTime / start.date / end.date)뿐이다
- title은 비정형 자연어 필드이므로 시간 추출에 사용하지 않는다
- title 기반 시간 해석은 오해석 위험이 있어 지원하지 않는다
- explicit time이 없는 calendar event(date-only)은 time-unspecified event로 처리
- recommendation 계산에서는 이벤트 날짜의 로컬 12:00을 대표 시각으로 사용
- recommendation 계산에서는 startAt / endAt 을 같은 대표 시각으로 정규화
- 이 경우 intra-event time-flow adjustment(start → end 변화)는 적용하지 않음
- 이는 계산 정책이며 실제 일정 시각 해석 규칙은 아님

## Cache Strategy (for later stage)

### Location Cache
- key: location_name
- TTL: 24h

### Weather Cache
- key: lat/lon + time
- TTL: 1h

### Recommendation Cache
- key: event + time + location
- TTL: 30min
