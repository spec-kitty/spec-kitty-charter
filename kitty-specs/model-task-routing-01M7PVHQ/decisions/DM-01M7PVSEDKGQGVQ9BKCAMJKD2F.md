# Decision Moment `01M7PVSEDKGQGVQ9BKCAMJKD2F`

- **Mission:** `model-task-routing-01M7PVHQ`
- **Origin flow:** `specify`
- **Slot key:** `specify.adr.tie`
- **Input key:** `tie_rule`
- **Status:** `resolved`
- **Created:** `2026-11-18T08:18:50.931929+00:00`
- **Resolved:** `2026-11-18T08:33:41.654201+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

ADR-0080 d11: tier_constraints filter before scoring; a remaining tie goes to the lower cost tier, then the lower model id?

## Options

- Lower cost tier, then model id (recommended)
- First listed in models (core behaviour)
- Return all tied models
- Other

## Final answer

Return all tied models

## Rationale

_(none)_

## Change log

- `2026-11-18T08:18:50.931929+00:00` - opened
- `2026-11-18T08:33:41.654201+00:00` - resolved (final_answer="Return all tied models")
