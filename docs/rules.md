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

- precipitationProbability >= 60%
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

## Failure Handling

### Location Failure
- FAILED면 current location 기준으로 fallback
- 이 경우 응답에서 `locationFallbackApplied=true`로 명시한다
- 이 경우 추천은 목적지 기반 추천이 아니라 current-location fallback 결과다

### Weather Failure
- fallback to latest cached data
- if none → safe default outfit

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
