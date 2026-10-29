# Decision Moment `01M60PKX1KSVDJ3BB6RTBTHMAT`

- **Mission:** `procedure-antipattern-toolguide-round-trip-01M60H9B`
- **Origin flow:** `plan`
- **Slot key:** `plan.api.toolguide-guide-transport`
- **Input key:** `toolguide_guide_transport`
- **Status:** `resolved`
- **Created:** `2026-10-28T07:29:27.091908+00:00`
- **Resolved:** `2026-10-28T07:29:29.747243+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

What does the HTTP toolguide registration send for the guide?

## Options

_(none)_

## Final answer

Either a URI or the entire guide contents, never just a path: the API must not assume shared filesystem access (authorized HTTP contract change).

## Rationale

_(none)_

## Change log

- `2026-10-28T07:29:27.091908+00:00` — opened
- `2026-10-28T07:29:29.747243+00:00` — resolved (final_answer="Either a URI or the entire guide contents, never just a path: the API must not assume shared filesystem access (authorized HTTP contract change).")
