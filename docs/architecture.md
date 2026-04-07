# Architecture

## Layered Structure

- domain
    - core models
    - rule engine
- application
    - use cases
    - orchestration
- infrastructure
    - external APIs
    - persistence
    - cache
- presentation
    - controllers / API

## Rules

- Domain layer must NOT depend on infrastructure
- Application layer orchestrates flow only
- Infrastructure handles external systems
- Presentation handles request/response only

## External Integrations (later stage)

- Google Calendar
- Google Places
- Open-Meteo
- AI API

## Cache (later stage)

- Redis
    - location cache
    - weather cache
    - recommendation cache
