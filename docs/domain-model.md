# Domain Model

## Core Entities

### CalendarEvent
- title
- startTime
- endTime

### ResolvedLocation
- latitude
- longitude
- name (optional)

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