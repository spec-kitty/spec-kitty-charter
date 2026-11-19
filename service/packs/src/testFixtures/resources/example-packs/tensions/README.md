---
audience: org
maintainers:
- '@spec-kitty/core'
---
# Tensions example pack

An org pack that holds one case of each way a tension between two guidance artifacts can stand (ADR-0045, #602). It
depends on the built-in pack, and no pack depends on it. Its directives and tactics are open, so a scenario can register
a relationship into it. Every tension is written once, from the smaller bare URN (ADR-0045 ruling G1).

| Case | Artifacts | Edges in `drg/fragment.yaml` |
| --- | --- | --- |
| 1 reconciled | `TENSION_REC_A`, `TENSION_REC_B`, tactic `reconcile-rec` | `A tension B`, with `when` and `reason`; `reconcile-rec reconciles_tension` both sides |
| 2 unreconciled | `TENSION_UNREC_A`, `TENSION_UNREC_B`, tactic `reconcile-half` | `A tension B`; `reconcile-half reconciles_tension A` only, so it reconciles nothing |
| 3 one side out | `TENSION_ONE_A`, `TENSION_ONE_B` | `A tension B`; an activation that excludes one side has no tension in force |
| 4 registered | `TENSION_REG_A`, `TENSION_REG_B` | none; a scenario registers the tension through `PUT /packs/{packId}/relationships` |
| 5 requires and tension | `TENSION_REQ_A`, `TENSION_REQ_B` | `A tension B`; `B requires A`, a separate relationship on the same pair |

## Provenance

Authored in this repository as the charter's own example pack (ADR-0070). It is a fixture of tensions, not a copy of any
organization's pack.
