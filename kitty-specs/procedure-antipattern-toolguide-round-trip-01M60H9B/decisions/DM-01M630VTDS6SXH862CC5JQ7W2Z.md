# Decision Moment `01M630VTDS6SXH862CC5JQ7W2Z`

- **Mission:** `procedure-antipattern-toolguide-round-trip-01M60H9B`
- **Origin flow:** `plan`
- **Slot key:** `plan.api.toolguide-read-side`
- **Input key:** `toolguide_read_side`
- **Status:** `resolved`
- **Created:** `2026-10-29T05:07:01.177811+00:00`
- **Resolved:** `2026-10-29T05:07:03.637962+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

What does the toolguide response expose after the change?

## Options

_(none)_

## Final answer

The response carries the guide's Markdown as guide and drops guidePath, mirroring the registration body (breaking response change on the 0.x line).

## Rationale

_(none)_

## Change log

- `2026-10-29T05:07:01.177811+00:00` — opened
- `2026-10-29T05:07:03.637962+00:00` — resolved (final_answer="The response carries the guide's Markdown as guide and drops guidePath, mirroring the registration body (breaking response change on the 0.x line).")
