# Decision Moment `01M577HSFD75NHZHVMP9RGNEK7`

- **Mission:** `pack-registration-distribution-01M56M49`
- **Origin flow:** `plan`
- **Slot key:** `plan.sourcing.builtin-migration`
- **Input key:** `builtin_migration`
- **Status:** `resolved`
- **Created:** `2026-10-18T10:05:08.461689+00:00`
- **Resolved:** `2026-10-18T10:11:12.062492+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Built-in migration sequencing + cold-offline bootstrap target?

## Options

- Staged (resolver-behind-loader -> integrity+lockfile -> fetch-at-release -> de-vendor), bundle built-in into the installed distribution
- Swap directly to service-resolved once the service ships
- Staged, but bundle built-in as a separate downloaded release artifact

## Final answer

Staged migration (resolver-behind-loader -> integrity+lockfile -> fetch-at-release -> de-vendor); built-in bundles into the installed distribution for cold/offline installs.

## Rationale

_(none)_

## Change log

- `2026-10-18T10:05:08.461689+00:00` — opened
- `2026-10-18T10:11:12.062492+00:00` — resolved (final_answer="Staged migration (resolver-behind-loader -> integrity+lockfile -> fetch-at-release -> de-vendor); built-in bundles into the installed distribution for cold/offline installs.")
