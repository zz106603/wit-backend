# Domain Model

## Core Entities

### CalendarEvent
- eventId
- title
- title은 비정형 자연어 필드이며 시간 정보의 source of truth가 아님
- startAt
- startAt은 Google Calendar structured field만으로 결정
- endAt
- endAt은 Google Calendar structured field만으로 결정
- rawLocation
- Google Calendar location 우선
- location이 비어 있으면 장소성 있는 title만 보조 후보로 사용
- time-unspecified event(date-only)은 추천 계산용 대표 시각으로 정규화
- 대표 시각은 이벤트 날짜의 로컬 12:00
- 이 경우 startAt과 endAt은 동일한 대표 시각을 사용

### ResolvedLocation
- rawLocation
- normalizedQuery (optional)
- displayLocation (optional)
- lat (optional)
- lng (optional)
- confidence (optional)
- status (RESOLVED / APPROXIMATED / FAILED)
- resolvedBy (RULE / GOOGLE_PLACES / AI)

### WeatherSnapshot
- regionName
- targetTime
- temperature
- feelsLike
- precipitationProbability
- weatherType

### Recommendation Result / Response Context
- locationResolution
- originalLocationResolution (location fallback 시 원래 FAILED 해석 결과 보존)
- currentWeather (optional)
- startWeather
- endWeather
- locationFallbackApplied
- weatherFallbackApplied
- weatherSource
- fallbackNotice (optional)

### OutfitDecision
- needUmbrella
- recommendedOutfitLevel
- recommendedOutfitText
- umbrellaReason
- outfitReason
- temperatureGap
- weatherChangeSummary
- aiSummary

## Rules

- All decisions must be derived from rule engine
- No AI-based decision making allowed
