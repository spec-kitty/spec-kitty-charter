# Decision Moment `01M578WFJ84BNS2ZSE6SN35CFG`

- **Mission:** `pack-registration-distribution-01M56M49`
- **Origin flow:** `plan`
- **Slot key:** `plan.contract.resolution-resource`
- **Input key:** `resolution_resource`
- **Status:** `resolved`
- **Created:** `2026-10-18T10:28:27.336165+00:00`
- **Resolved:** `2026-10-18T11:04:34.904979+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Is 'resolve' a public REST resource or internal-only?

## Options

- Public resource: POST /packs/{id}/resolutions returning a verified ResolvedPack (integrity/engine/cache/reachability surface there)
- Internal domain service only; observable solely via distribution
- Public but read-only projection of resolution state

## Final answer

Resolution is a READ-ONLY projection of resolution state (GET), not a public action resource. Resolve happens internally during register/distribute; integrity/engine/cache/reachability outcomes surface as the distribute action's result and are reflected in the read-only resolution-state projection.

## Rationale

_(none)_

## Change log

- `2026-10-18T10:28:27.336165+00:00` — opened
- `2026-10-18T11:04:34.904979+00:00` — resolved (final_answer="Resolution is a READ-ONLY projection of resolution state (GET), not a public action resource. Resolve happens internally during register/distribute; integrity/engine/cache/reachability outcomes surface as the distribute action's result and are reflected in the read-only resolution-state projection.")
