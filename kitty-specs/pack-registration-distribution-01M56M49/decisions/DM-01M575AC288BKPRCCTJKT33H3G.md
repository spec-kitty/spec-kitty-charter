# Decision Moment `01M575AC288BKPRCCTJKT33H3G`

- **Mission:** `pack-registration-distribution-01M56M49`
- **Origin flow:** `plan`
- **Slot key:** `plan.contract.hateoas-representation`
- **Input key:** `hateoas_representation`
- **Status:** `resolved`
- **Created:** `2026-10-18T09:26:08.200754+00:00`
- **Resolved:** `2026-10-18T09:45:37.738507+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

HATEOAS hypermedia links vs ADR-0004's closed generated DTOs (additionalProperties:false). How to represent links?

## Options

- Model HAL _links/_embedded explicitly in OpenAPI schemas
- Reusable Links schema component referenced per response
- Links via Link: headers / thin envelope, resource schemas untouched

## Final answer

Represent HATEOAS via a reusable Links schema component referenced per response; DTOs stay closed per ADR-0004.

## Rationale

_(none)_

## Change log

- `2026-10-18T09:26:08.200754+00:00` — opened
- `2026-10-18T09:45:37.738507+00:00` — resolved (final_answer="Represent HATEOAS via a reusable Links schema component referenced per response; DTOs stay closed per ADR-0004.")
