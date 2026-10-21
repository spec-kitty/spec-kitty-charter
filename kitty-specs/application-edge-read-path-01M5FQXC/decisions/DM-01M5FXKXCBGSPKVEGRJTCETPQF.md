# Decision Moment `01M5FXKXCBGSPKVEGRJTCETPQF`

- **Mission:** `application-edge-read-path-01M5FQXC`
- **Origin flow:** `plan`
- **Slot key:** `plan.contract.save-semantics`
- **Input key:** `save_semantics`
- **Status:** `resolved`
- **Created:** `2026-10-21T19:04:42.123331+00:00`
- **Resolved:** `2026-10-21T19:08:40.516181+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

What create-vs-update contract should TacticRepository.save be pinned to?

## Options

- Replace-existing-only (refuse unknown id)
- Create-or-update (upsert)
- Other

## Final answer

Create-or-update (upsert): save creates the tactic when its id is unknown and replaces it when known. The filesystem adapter gains an atomic create arm (new file in the pack dir) alongside the existing atomic replace. Pinned by a port-level contract test covering both the create and the update arm. Write surface stays HTTP-unexposed this mission (C-006).

## Rationale

_(none)_

## Change log

- `2026-10-21T19:04:42.123331+00:00` — opened
- `2026-10-21T19:08:40.516181+00:00` — resolved (final_answer="Create-or-update (upsert): save creates the tactic when its id is unknown and replaces it when known. The filesystem adapter gains an atomic create arm (new file in the pack dir) alongside the existing atomic replace. Pinned by a port-level contract test covering both the create and the update arm. Write surface stays HTTP-unexposed this mission (C-006).")
