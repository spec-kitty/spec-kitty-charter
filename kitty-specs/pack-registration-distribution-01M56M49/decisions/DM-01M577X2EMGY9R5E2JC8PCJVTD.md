# Decision Moment `01M577X2EMGY9R5E2JC8PCJVTD`

- **Mission:** `pack-registration-distribution-01M56M49`
- **Origin flow:** `plan`
- **Slot key:** `plan.contract.rest-graphql-parity`
- **Input key:** `rest_graphql_parity`
- **Status:** `resolved`
- **Created:** `2026-10-18T10:11:18.100995+00:00`
- **Resolved:** `2026-10-18T10:13:37.257194+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

REST vs GraphQL surface parity (domain-op parity holds by construction at the use-case layer; this is only which ops each surface exposes)?

## Options

- OpenAPI/REST authoritative + source of record; GraphQL full read/traversal parity + operator-scoped write parity
- Full parity: reads and writes through both
- REST only in v1; GraphQL deferred

## Final answer

REST (OpenAPI HATEOAS) ships in v1 as the source of record; GraphQL is a planned later version - designed-for (use-case layer stays adapter-ready) but NOT built in v1.

## Rationale

_(none)_

## Change log

- `2026-10-18T10:11:18.100995+00:00` — opened
- `2026-10-18T10:13:37.257194+00:00` — resolved (final_answer="REST (OpenAPI HATEOAS) ships in v1 as the source of record; GraphQL is a planned later version - designed-for (use-case layer stays adapter-ready) but NOT built in v1.")
