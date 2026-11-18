# Decision Moment `01M7PVS8Z0R6P76SFZA12BN4PG`

- **Mission:** `model-task-routing-01M7PVHQ`
- **Origin flow:** `specify`
- **Slot key:** `specify.adr.refusals`
- **Input key:** `recommendation_refusals`
- **Status:** `open`
- **Created:** `2026-11-18T08:18:45.344270+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

ADR-0080 d9: refusals in order 400, activation_not_found, artifact_not_found, mission_type_not_found/action_not_found, 409 routing_catalog_not_in_force, 409 routing_catalog_conflict (two in force), 404 task_type_not_found; no fit or no profile model is 200 with fewer candidates?

## Options

- As proposed (recommended)
- Two catalogs: request names the catalog instead of 409
- Unknown task type: 200 with no catalog candidate (core behaviour)
- Other

## Final answer

_(none)_

## Rationale

_(none)_

## Change log

- `2026-11-18T08:18:45.344270+00:00` - opened
