# Decision Moment `01M5SJ83B7HHS8F7GP7W8C49EM`

- **Mission:** `regular-artefact-read-registration-01M5SC0T`
- **Origin flow:** `plan`
- **Slot key:** `plan.architecture.kind-registry`
- **Input key:** `kind_registry_strategy`
- **Status:** `resolved`
- **Created:** `2026-10-25T12:58:24.999895+00:00`
- **Resolved:** `2026-10-25T13:04:11.785045+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Build the per-kind registry now (#169) or copy the Tactic slice per kind for this mission?

## Options

- Build the kind registry now (this mission is ADR-0024 trigger)
- Copy the Tactic slice per kind, defer the registry to #169
- Hybrid: registry for the read/registration dispatch only

## Final answer

Copy the Tactic slice per kind; defer the registry to #169. Operator's rationale: keep each kind detached and clearly modelled so they evolve independently; a shared abstraction risks over-coupling their behaviour and cascade issues; accept the near-duplication to avoid premature generalization.

## Rationale

_(none)_

## Change log

- `2026-10-25T12:58:24.999895+00:00` — opened
- `2026-10-25T13:04:11.785045+00:00` — resolved (final_answer="Copy the Tactic slice per kind; defer the registry to #169. Operator's rationale: keep each kind detached and clearly modelled so they evolve independently; a shared abstraction risks over-coupling their behaviour and cascade issues; accept the near-duplication to avoid premature generalization.")
