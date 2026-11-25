# Decision Moment `01M8A9W2C4ZEYT5Q946PBW8JTB`

- **Mission:** `adr-0082-graph-walk-slices-01M8A867`
- **Origin flow:** `plan`
- **Slot key:** `plan.wire.unified-status`
- **Input key:** `unified_status`
- **Status:** `deferred`
- **Created:** `2026-11-25T21:30:31.172743+00:00`
- **Resolved:** `2026-11-25T21:30:42.273373+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Q-A: The unified edge-index codes (self_override, override_not_permitted) are 422 on every operation today. Follow ADR-0056 (a read keeps 500 --- the configured packs are at fault --- and the 13 impact-checked operations keep 422 through x-refusal operations overrides; moves both codes to the 500 default), or keep 422 everywhere (a read would answer 422 for a stored-pack fault)?

## Options

- ADR-0056 model: 500 on reads, 422 on impact-checked writes (recommended)
- 422 everywhere
- Other

## Final answer

_(none)_

## Rationale

Sent to the operator through the dispatcher on 2026-11-25; only WP04 depends on it, so WP01 to WP03 proceed.

## Change log

- `2026-11-25T21:30:31.172743+00:00` - opened
- `2026-11-25T21:30:42.273373+00:00` - deferred
