# Decision Moment `01M578W9FK24NNSFRYT7C5ZZZ1`

- **Mission:** `pack-registration-distribution-01M56M49`
- **Origin flow:** `plan`
- **Slot key:** `plan.contract.requester-tier`
- **Input key:** `requester_tier`
- **Status:** `resolved`
- **Created:** `2026-10-18T10:28:21.107962+00:00`
- **Resolved:** `2026-10-18T10:28:22.611889+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Is requester tier client-asserted or derived?

## Options

- Derived from authenticated identity; never a client-set field (fail-closed trust)

## Final answer

Requester tier is derived from the authenticated identity, never a client-set field (exposing it client-side would break fail-closed trust). Authn/authz is a separate downstream concern.

## Rationale

_(none)_

## Change log

- `2026-10-18T10:28:21.107962+00:00` — opened
- `2026-10-18T10:28:22.611889+00:00` — resolved (final_answer="Requester tier is derived from the authenticated identity, never a client-set field (exposing it client-side would break fail-closed trust). Authn/authz is a separate downstream concern.")
