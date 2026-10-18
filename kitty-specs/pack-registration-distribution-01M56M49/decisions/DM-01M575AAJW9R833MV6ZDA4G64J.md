# Decision Moment `01M575AAJW9R833MV6ZDA4G64J`

- **Mission:** `pack-registration-distribution-01M56M49`
- **Origin flow:** `plan`
- **Slot key:** `plan.contract.graphql-source`
- **Input key:** `graphql_contract_source`
- **Status:** `resolved`
- **Created:** `2026-10-18T09:26:06.684924+00:00`
- **Resolved:** `2026-10-18T09:45:36.258089+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

GraphQL adds a second schema language vs ADR-0002's single-OpenAPI-contract source of record. How to reconcile?

## Options

- OpenAPI sole source; GraphQL derived/generated from it
- Hand-author GraphQL SDL as co-equal contract (amend ADR-0002)
- GraphQL internal/experimental, not a frozen contract yet

## Final answer

OpenAPI remains the sole source of record (ADR-0002 preserved); the GraphQL schema is derived/generated from it.

## Rationale

_(none)_

## Change log

- `2026-10-18T09:26:06.684924+00:00` — opened
- `2026-10-18T09:45:36.258089+00:00` — resolved (final_answer="OpenAPI remains the sole source of record (ADR-0002 preserved); the GraphQL schema is derived/generated from it.")
