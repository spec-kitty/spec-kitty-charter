# Decision Moment `01M589DHDJG0GQAFTWCJKJ60F1`

- **Mission:** `charter-contract-and-build-retro-01M57QHJ`
- **Origin flow:** `plan`
- **Slot key:** `plan.gates.jspecify-in-core`
- **Input key:** `jspecify_in_core`
- **Status:** `resolved`
- **Created:** `2026-10-18T19:57:00.722507+00:00`
- **Resolved:** `2026-10-18T19:57:04.412014+00:00`
- **Resolved by:** `stijn`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

May the plain-Java core (domain, read-app) carry JSpecify's @NullMarked so NullAway checks it, which the ArchUnit rule theCoreIsPlainJava forbids today?

## Options

- Allow JSpecify
- Keep core unmarked

## Final answer

Allow JSpecify

## Rationale

Operator ruling 2026-10-18. JSpecify is an annotations-only artifact with no dependencies or behavior; theCoreIsPlainJava allows org.jspecify.annotations.. so NullAway covers the domain and read-app.

## Change log

- `2026-10-18T19:57:00.722507+00:00` — opened
- `2026-10-18T19:57:04.412014+00:00` — resolved (final_answer="Allow JSpecify")
