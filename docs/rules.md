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
2. If fails → AI fallback
3. If still fails → default location

## Failure Handling

### Location Failure
- fallback to default location

### Weather Failure
- fallback to latest cached data
- if none → safe default outfit

### AI Failure
- ignore AI result
- continue with rule-based flow

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