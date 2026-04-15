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
- fallback to default location

### Weather Failure
- fallback to latest cached data
- if none → safe default outfit

### AI Failure
- ignore AI result
- continue with rule-based flow

## Calendar Event Time Policy

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
