# Decision Moment `01M5FXKRX31S2QK7NZ5Q28RNRJ`

- **Mission:** `application-edge-read-path-01M5FQXC`
- **Origin flow:** `plan`
- **Slot key:** `plan.api.tactic-endpoint-shape`
- **Input key:** `tactic_endpoint_shape`
- **Status:** `resolved`
- **Created:** `2026-10-21T19:04:37.539398+00:00`
- **Resolved:** `2026-10-21T19:08:35.985120+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

What is the inbound REST shape for tactic retrieval?

## Options

- GET pack tactics collection + GET one tactic by id
- GET collection only
- GET single tactic by id only
- Other

## Final answer

Collection + single-by-id: GET /api/v1/packs/{packId}/tactics (all tactics of a pack) and GET /api/v1/packs/{packId}/tactics/{tacticId} (one). 404-class refusal when pack/tactic absent.

## Rationale

_(none)_

## Change log

- `2026-10-21T19:04:37.539398+00:00` — opened
- `2026-10-21T19:08:35.985120+00:00` — resolved (final_answer="Collection + single-by-id: GET /api/v1/packs/{packId}/tactics (all tactics of a pack) and GET /api/v1/packs/{packId}/tactics/{tacticId} (one). 404-class refusal when pack/tactic absent.")
