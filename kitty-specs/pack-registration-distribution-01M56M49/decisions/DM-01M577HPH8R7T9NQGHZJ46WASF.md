# Decision Moment `01M577HPH8R7T9NQGHZJ46WASF`

- **Mission:** `pack-registration-distribution-01M56M49`
- **Origin flow:** `plan`
- **Slot key:** `plan.sourcing.source-kinds`
- **Input key:** `source_kinds`
- **Status:** `resolved`
- **Created:** `2026-10-18T10:05:05.448513+00:00`
- **Resolved:** `2026-10-18T10:11:09.041635+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Which pack source kinds in v1; is a published-artifact source in or out?

## Options

- {git, filesystem, service-stored} only; reserve published-artifact as a future 4th kind via a versionable discriminator
- Include published-artifact now as a 4th source kind
- {git, filesystem, service-stored} only; do not reserve published-artifact

## Final answer

Include published-artifact as a 4th first-class source kind in v1, alongside git, filesystem, and service-stored.

## Rationale

_(none)_

## Change log

- `2026-10-18T10:05:05.448513+00:00` — opened
- `2026-10-18T10:11:09.041635+00:00` — resolved (final_answer="Include published-artifact as a 4th first-class source kind in v1, alongside git, filesystem, and service-stored.")
