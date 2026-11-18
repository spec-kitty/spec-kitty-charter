---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: model-task-routing-01M7PVHQ
mission_id: 01M7PVHQF72SKVY48WB787M7GM
generated_at: '2026-11-18T11:08:03.561544+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: .worktrees/model-task-routing-01M7PVHQ-coord/kitty-specs/model-task-routing-01M7PVHQ/spec.md
    sha256: 23afd0d2c0cdc23bfb6841d4b41e26c57927c29d4a2af2c15263182cbc3029b6
  plan.md:
    path: .worktrees/model-task-routing-01M7PVHQ-coord/kitty-specs/model-task-routing-01M7PVHQ/plan.md
    sha256: c06ab6bcd313c0254d0827f29c87a0f1d61cd32f38af0c2fc3b6ce9830348d56
  tasks.md:
    path: .worktrees/model-task-routing-01M7PVHQ-coord/kitty-specs/model-task-routing-01M7PVHQ/tasks.md
    sha256: 4f88b90d0c8731c8a0a541ca74b3550c36d3731c4571f8e8a35a5959992f1ecc
  charter:
    path: .kittify/charter/charter.yaml
    sha256: 78969118cd3893697d6eae119c7306a91003f27d64a8ddfdb4744d92c50e4503
verdict: blocked
issue_counts:
  medium: 4
  low: 2
  high: 2
  critical: 0
  info: 0
findings:
- id: C1
  severity: high
  category: charter
  summary: The plan's Charter Check replaces the charter's pre-merge adversarial squad with one independent reviewer; no gate lists the squad before merge.
- id: I1
  severity: high
  category: inconsistency
  summary: WP02 adds the kernel kind but WP03 adds the contract enums; WireTokenParityTest compares them, so WP02's DoD (verifyAll green) cannot hold on its own.
- id: I2
  severity: medium
  category: inconsistency
  summary: Adding the kind changes scenarios that enumerate kinds (taxonomy, ?kind= 400 details) at WP02/WP03, but those ripple edits are assigned to WP05 and WP03 only.
- id: G1
  severity: medium
  category: coverage
  summary: NFR-001 asks for identical bodies and ETags over 100 calls and file-order independence; WP09 asserts two calls only.
- id: G2
  severity: medium
  category: coverage
  summary: FR-005 (deny and require a catalog) has no named scenario; WP08 covers select, inactive and locked only.
- id: G3
  severity: medium
  category: coverage
  summary: The spec edge case of a held but not-in-force profile still giving its candidate is in no WP's scenario list.
- id: A1
  severity: low
  category: ambiguity
  summary: Spec US2-3 and US3-3 say 'both are catalog candidates' (containment) while research R7 and WP09 require exact ordered lists.
- id: T1
  severity: low
  category: terminology
  summary: data-model.md writes top(all); WP07 writes top(fitting) for the same set.
---

## Specification Analysis Report

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| C1 | Charter | HIGH | plan.md Charter Check; tasks.md | The charter requires an adversarial review squad at the plan point **and before merge** for contract, architecture and domain changes. The plan names only an independent reviewer before merge, and no task or gate schedules the pre-merge squad. | State both pre-merge gates in the plan: the independent WP reviewer and the pre-merge squad. Name the squad in tasks.md as a Mission gate after WP10. |
| I1 | Inconsistency | HIGH | WP02, WP03; `WireTokenParityTest` | `WireTokenParityTest` pins `ArtifactKind.values()` against `ActivatableKind` and its inlined copies. WP02 changes the kernel and WP03 the contract, so WP02 alone is red. | Move the contract enum additions (T008) into WP02, or merge WP02 and WP03 into one lane with a shared DoD. |
| I2 | Inconsistency | MEDIUM | WP02, WP03, WP05 | The scenarios that list every kind (`offering.feature` taxonomy, the `?kind=` 400 details in `artifacts.feature` and `effective-reads.feature`) change as soon as the kind exists. They are assigned to WP03 and WP05, after WP02. | Assign every kind-enumerating scenario fix to the WP that first adds the kind (after I1, the merged WP02). |
| G1 | Coverage | MEDIUM | spec NFR-001; WP09 T035 | The 100-call determinism threshold and the file-order check are not asserted at the read. | WP09: a test of 100 calls with equal body and tag; WP07 already reverses the file order. |
| G2 | Coverage | MEDIUM | spec FR-005; WP08 T031 | Deny and require have no scenario. | WP08: scenarios that deny `default` (then the read is not in force) and require it through a preset `must_have`. |
| G3 | Coverage | MEDIUM | spec Edge Cases; WP09 T035 | No scenario for a held profile that is not in force. | WP09: add the scenario. |
| A1 | Ambiguity | LOW | spec.md US2-3, US3-3 | "Both are catalog candidates" reads as containment. | Reword to "exactly these candidates, in this order". |
| T1 | Terminology | LOW | data-model.md rule 4; WP07 T024 | `top(all)` against `top(fitting)`. | Use `top(fitting)` in both. |

**Coverage Summary Table:**

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 kind | Yes | T004, T008–T010, T016–T017 | |
| FR-002 content rules | Yes | T018 | |
| FR-003 pack reads | Yes | T027–T028, T031 | |
| FR-004 effective reads | Yes | T029, T031 | |
| FR-005 activation and policy | Partial | T021, T031 | G2 |
| FR-006 recommendation read | Yes | T032–T035 | |
| FR-007 objective | Yes | T023, T026, T035 | |
| FR-008 ties | Yes | T024, T026, T035 | |
| FR-009 tiers | Yes | T024–T026, T035 | |
| FR-010 step tier | Yes | T013–T015 | |
| FR-011 profile candidate | Yes | T033–T035 | G3 |
| FR-012 refusals | Yes | T032–T033, T035 | |
| FR-013 caching | Yes | T033–T035 | |
| FR-014 switch gate | Yes | T031, T036 | |
| FR-015 records | Yes | T037–T040 | |
| NFR-001 determinism | Partial | T026, T035 | G1 |
| NFR-002 exact decimal | Yes | T023, T026 | |
| NFR-003 gates | Yes | T001–T002, all | |
| NFR-004 docs | Yes | T040 | |

**Charter Alignment Issues:** C1.

**Unmapped Tasks:** none. Every T001–T040 maps to a WP with requirement refs.

**Metrics:**

- Total requirements: 15 FR, 4 NFR, 5 C, 4 SC
- Total tasks: 40 subtasks in 10 WPs
- Coverage: 100% of FRs have at least one task; FR-005 and NFR-001 are partial
- Ambiguity count: 1
- Duplication count: 0
- Critical issues: 0; high: 2

## Next Actions

The verdict is blocked by C1 and I1. Remediate the planning artifacts (plan Charter Check, tasks.md, WP02/WP03,
WP08, WP09, spec wording), then re-run `/spec-kitty.analyze`.
