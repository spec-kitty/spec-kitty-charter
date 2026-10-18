# Decision Moment `01M576E6NQGGSE8ESV765NGH7X`

- **Mission:** `pack-registration-distribution-01M56M49`
- **Origin flow:** `plan`
- **Slot key:** `plan.structure.module-granularity`
- **Input key:** `module_granularity`
- **Status:** `resolved`
- **Created:** `2026-10-18T09:45:42.327366+00:00`
- **Resolved:** `2026-10-18T10:05:03.974744+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Gradle module granularity for the slice x layer matrix?

## Options

- One module per (slice x layer) - maximal ArchUnit isolation, many modules
- One module per layer, slices as packages (matches current charter-domain/read-app/adapter-*)
- One module per slice, layers as packages

## Final answer

One Gradle module per concern slice; layers as packages within; ArchUnit enforces layering and the no-infra-in-domain rule.

## Rationale

_(none)_

## Change log

- `2026-10-18T09:45:42.327366+00:00` — opened
- `2026-10-18T10:05:03.974744+00:00` — resolved (final_answer="One Gradle module per concern slice; layers as packages within; ArchUnit enforces layering and the no-infra-in-domain rule.")
