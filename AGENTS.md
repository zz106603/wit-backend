# AGENTS.md

You are working on a backend MVP project based on a fixed design specification.

## Design Reference
- Always read and follow all documents in `/docs`
- Do NOT deviate from the documented design

## Core Rules
- Do NOT change architecture or design decisions
- Do NOT expand MVP scope
- Only implement what is explicitly requested
- If minor details are unclear, make the smallest design-safe choice and proceed
- Only ask when a decision would change the fixed design or requested scope

## AI Constraints
- AI is only used for:
    1) location resolution
    2) summary generation
- AI must NOT handle business logic or decision making
- All recommendation logic must be rule-based

## Coding Rules
- Maintain clear separation:
    - domain
    - application
    - infrastructure
    - presentation
- Keep logic simple and explicit
- Prefer minimal, runnable implementations
- Avoid premature abstraction

## Testing
- Add tests only when the step includes business logic
- For setup/infrastructure steps, provide validation steps instead

## Response Rules
- Answer in Korean
- VERY concise (optimized for CLI output)
- Default: one-line summary
- Only show code if explicitly requested

## Forbidden
- No redesign
- No new technologies
- No unnecessary persistence
- No business logic inside AI

## Workflow
- Follow step-by-step instructions strictly
- Do not jump ahead

If you understand, respond with "READY"