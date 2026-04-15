# Domain Model

## Core Entities

### CalendarEvent
- title
- title은 비정형 자연어 필드이며 시간 정보의 source of truth가 아님
- startTime
- startTime은 Google Calendar structured field만으로 결정
- endTime
- endTime은 Google Calendar structured field만으로 결정
- time-unspecified event(date-only)은 추천 계산용 대표 시각으로 정규화
- 대표 시각은 이벤트 날짜의 로컬 12:00
- 이 경우 startTime과 endTime은 동일한 대표 시각을 사용

### ResolvedLocation
- latitude
- longitude
- name (optional)
- resolvedBy (RULE / GOOGLE_PLACES / AI)

### WeatherSnapshot
- temperature
- precipitationProbability
- condition (rain, snow, clear, etc)

### OutfitDecision
- upper
- lower
- outer
- accessories
- note

## Rules

- All decisions must be derived from rule engine
- No AI-based decision making allowed
