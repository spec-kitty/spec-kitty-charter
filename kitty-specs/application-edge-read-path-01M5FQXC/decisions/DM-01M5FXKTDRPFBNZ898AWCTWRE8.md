# Decision Moment `01M5FXKTDRPFBNZ898AWCTWRE8`

- **Mission:** `application-edge-read-path-01M5FQXC`
- **Origin flow:** `plan`
- **Slot key:** `plan.gate.application-edge-rule`
- **Input key:** `application_edge_rule`
- **Status:** `resolved`
- **Created:** `2026-10-21T19:04:39.096581+00:00`
- **Resolved:** `2026-10-21T19:08:37.496686+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How to revise the ArchUnit gate for application-as-edge?

## Options

- Replace APPLICATION_IS_PLAIN_JAVA with APPLICATION_IS_EDGE (framework allowed; still no cross-slice/deployable deps)
- Delete the application purity rule outright (treat like infra)
- Other

## Final answer

Replace APPLICATION_IS_PLAIN_JAVA with a positive APPLICATION_IS_EDGE rule: application may depend on domain, kernel and framework, but must NOT depend on another slice's infra, on storage internals of its own slice beyond the ports, on cross-slice domain, or on deployable. Keep a biting known-bad control (ReachesStorage-style).

## Rationale

_(none)_

## Change log

- `2026-10-21T19:04:39.096581+00:00` — opened
- `2026-10-21T19:08:37.496686+00:00` — resolved (final_answer="Replace APPLICATION_IS_PLAIN_JAVA with a positive APPLICATION_IS_EDGE rule: application may depend on domain, kernel and framework, but must NOT depend on another slice's infra, on storage internals of its own slice beyond the ports, on cross-slice domain, or on deployable. Keep a biting known-bad control (ReachesStorage-style).")
