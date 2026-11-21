---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: pack-doctor-coverage-01M7YMQK
mission_id: 01M7YMQKJ7VHPK74YWKCQW8XBT
generated_at: '2026-11-21T13:04:57.825564+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: .worktrees/pack-doctor-coverage-01M7YMQK-coord/kitty-specs/pack-doctor-coverage-01M7YMQK/spec.md
    sha256: f6deded67d9a1ae974a63688cb7db991f51fc4e6d2b68aeeda084964c7b91025
  plan.md:
    path: .worktrees/pack-doctor-coverage-01M7YMQK-coord/kitty-specs/pack-doctor-coverage-01M7YMQK/plan.md
    sha256: 15572efd7637ad06082242561ea26d3d64a45ef8d8cfdbf7ce1954fab0b5ba50
  tasks.md:
    path: .worktrees/pack-doctor-coverage-01M7YMQK-coord/kitty-specs/pack-doctor-coverage-01M7YMQK/tasks.md
    sha256: 8c809f62b64bb617676607204e187d3021e360f0ae475f6ba2d472b46610e21a
  charter:
    path: .kittify/charter/charter.yaml
    sha256: 78969118cd3893697d6eae119c7306a91003f27d64a8ddfdb4744d92c50e4503
verdict: ready
issue_counts:
  critical: 0
  low: 3
  medium: 4
  high: 0
  info: 0
findings:
- id: C1
  severity: medium
  category: coverage
  summary: "FR-006 (--full) is mapped to WP05, which is held pending the operator's ruling on plan D5; #632 may close only partially (Refs)."
- id: I1
  severity: medium
  category: inconsistency
  summary: data-model.md's --full section still lists specialization_cycle and CLOSURE_WALK; plan D5 proposal (a) covers delegations, references and targets only.
- id: I2
  severity: low
  category: inconsistency
  summary: spec Domain Language defines 'full pass' as everything registration judges; plan D5 narrows it pending the ruling.
- id: I3
  severity: low
  category: inconsistency
  summary: spec US5.2 says remediate refuses on a 'domain fault'; plan D7 and data-model define the precondition as the storage verdict without the manifest gate and form findings.
- id: U1
  severity: medium
  category: underspecification
  summary: WP03 adds tactic and anti-pattern files to the example packs, changing manifest membership, but WP04 (the stamp helper) does not depend on WP03; the two lanes can collide on pack-manifest.yaml.
- id: U2
  severity: medium
  category: underspecification
  summary: Several WPs declare justified out-of-map edits to files owned by others (PackRulesets, PackSetCheck, Problems.statusOf, StorageRulesets, CharterRefusalCode.yaml, Arguments, HexagonalArchitectureTest); dependency order mitigates but lanes run in parallel.
- id: N1
  severity: low
  category: coverage
  summary: NFR-002's 30 s --full budget is only exercised once WP05 lands; WP02 leaves the CI step inert.
---

## Specification Analysis Report

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| C1 | Coverage | MEDIUM | spec.md FR-006; tasks WP05 | `--full` held for the operator's ruling (plan D5) | Proceed with the other WPs. On the ruling, update spec FR-006, data-model and WP05 together, then re-run analyze |
| I1 | Inconsistency | MEDIUM | data-model.md "`--full` phase"; plan D5 | The data model lists the cycle and `CLOSURE_WALK`; plan proposal (a) does not | Align after the ruling (same edit as C1) |
| I2 | Inconsistency | LOW | spec.md Domain Language | The "full pass" definition is broader than D5 (a) | Align after the ruling |
| I3 | Inconsistency | LOW | spec.md US5.2; plan D7 | Wording of remediate's refusal | Use D7's wording in the spec at the next spec edit |
| U1 | Underspecification | MEDIUM | WP03 T015; WP04 T021 | Example-pack file additions vs the manifest restamp in parallel lanes | WP03 restamps its own additions through `ManifestMembership.stamp`, or WP04 rebases on WP03. The implementer of the later-merging lane re-runs the stamp helper |
| U2 | Underspecification | MEDIUM | WP01, WP03, WP04, WP06, WP07 | Out-of-map edits to shared files | Merge lanes in dependency order (WP01 → WP02 → WP03 → WP04 → WP06 → WP07). Reviewers check that each out-of-map edit is minimal and listed |
| N1 | Coverage | LOW | NFR-002; WP02 T009 | The second timed CI step stays inert until WP05 | Acceptable; WP05 activates it |

**Coverage Summary Table:**

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 one-closure-fault-type | yes | T001-T005 | WP01 |
| FR-002 preset-item-rule | yes | T011-T013 | WP03 |
| FR-003 preset-rule-surfaces | yes | T014, T016 | WP03 |
| FR-004 manifest-stale | yes | T017, T020 | WP04 |
| FR-005 round-trip-one-problem | yes | T018, T019 | WP04 |
| FR-006 validate-full | held | T022-T024 | WP05, ruling pending |
| FR-007 rename | yes | T006-T010 | WP02 |
| FR-008 report | yes | T025-T027 | WP06 |
| FR-009 remediate | yes | T028-T031 | WP07 |
| FR-010 fixtures | yes | T015, T021 | WP03, WP04 |
| FR-011 contract | yes | T012, T017, T010, T027 | fragments per WP |
| FR-012 adr | yes | T032-T034 | WP08 |
| NFR-001 red-first | yes | every WP | stated per subtask |
| NFR-002 timing | partial | T009, T023 | see N1 |
| NFR-003 no-new-walk | yes | T013, T022 | reviewer check |

**Charter Alignment Issues:** none. Canonical model (no alias, no legacy reader), no PIT, ADR-0082 placement,
fixtures fixed and not exempted, a squad at the plan point (done twice), Tier R review: all present.

**Unmapped Tasks:** none (T035/T036 map to C-006 and SC-002).

**Metrics:**

- Total requirements: 12 FR + 3 NFR + 6 C + 3 SC
- Total tasks: 36
- Coverage: 100% of FRs mapped (FR-006 held)
- Ambiguity count: 0
- Duplication count: 0
- Critical issues: 0

## Next Actions

- No CRITICAL or HIGH findings: implementation may proceed with WP01.
- C1/I1/I2 resolve together when the operator rules on `--full`. Re-run analyze after that edit.
- U1/U2: lanes merge in dependency order, and reviewers check out-of-map edits.
