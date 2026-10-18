# Decision Moment `01M5781BVDQ4NTHSKA95Z60XPY`

- **Mission:** `pack-registration-distribution-01M56M49`
- **Origin flow:** `plan`
- **Slot key:** `plan.sourcing.engine-compat`
- **Input key:** `engine_compat`
- **Status:** `resolved`
- **Created:** `2026-10-18T10:13:38.797562+00:00`
- **Resolved:** `2026-10-18T10:13:40.270136+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Engine/compatibility gating mechanism?

## Options

- requires_core-style range on registration/source descriptor + lockfile; enforce at resolve with a PACK_ENGINE_INCOMPATIBLE-style code

## Final answer

requires_core-style compat range on the registration/source descriptor + lockfile entry (NOT pack.yaml, which forbids extra fields); enforced at resolve time with a canonical refusal code sibling to PACK_LEVEL_MISMATCH (exact string a contract-naming detail).

## Rationale

_(none)_

## Change log

- `2026-10-18T10:13:38.797562+00:00` — opened
- `2026-10-18T10:13:40.270136+00:00` — resolved (final_answer="requires_core-style compat range on the registration/source descriptor + lockfile entry (NOT pack.yaml, which forbids extra fields); enforced at resolve time with a canonical refusal code sibling to PACK_LEVEL_MISMATCH (exact string a contract-naming detail).")
