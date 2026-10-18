# Decision Moment `01M578WJG7ZQ9AAX4WWP1H555M`

- **Mission:** `pack-registration-distribution-01M56M49`
- **Origin flow:** `plan`
- **Slot key:** `plan.sourcing.allowlist-granularity`
- **Input key:** `allowlist_granularity`
- **Status:** `resolved`
- **Created:** `2026-10-18T10:28:30.343482+00:00`
- **Resolved:** `2026-10-18T11:04:37.921446+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Trust allowlist granularity?

## Options

- Host-level (e.g. github.com/acme)
- Org/namespace-level
- Exact-repo-level (most restrictive)
- Operator picks per entry (mixed granularity)

## Final answer

Trust allowlist is mixed-granularity: each entry may be host-, org-, or exact-repo-scoped (operator picks per entry; exact-repo for least privilege).

## Rationale

_(none)_

## Change log

- `2026-10-18T10:28:30.343482+00:00` — opened
- `2026-10-18T11:04:37.921446+00:00` — resolved (final_answer="Trust allowlist is mixed-granularity: each entry may be host-, org-, or exact-repo-scoped (operator picks per entry; exact-repo for least privilege).")
