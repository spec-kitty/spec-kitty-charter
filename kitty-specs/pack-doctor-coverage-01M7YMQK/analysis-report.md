---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: pack-doctor-coverage-01M7YMQK
mission_id: 01M7YMQKJ7VHPK74YWKCQW8XBT
generated_at: '2026-11-21T19:11:05.482392+00:00'
analyzer_agent: claude
input_artifacts:
  spec.md:
    path: .worktrees/pack-doctor-coverage-01M7YMQK-coord/kitty-specs/pack-doctor-coverage-01M7YMQK/spec.md
    sha256: 0ab11d3cdb84756d4a2bf7e71b63c9ebf10318783b5ee887b29f1429d1ccf378
  plan.md:
    path: .worktrees/pack-doctor-coverage-01M7YMQK-coord/kitty-specs/pack-doctor-coverage-01M7YMQK/plan.md
    sha256: 84a553257d9fa4c10ac8dbc9712780c5eab6b372f5c40e5a29905bb1072f8626
  tasks.md:
    path: .worktrees/pack-doctor-coverage-01M7YMQK-coord/kitty-specs/pack-doctor-coverage-01M7YMQK/tasks.md
    sha256: cde93605b85a9bc2684598fb560b12ec00d6070c798f08d8f9222b2d9a239b1a
  charter:
    path: .kittify/charter/charter.yaml
    sha256: 78969118cd3893697d6eae119c7306a91003f27d64a8ddfdb4744d92c50e4503
verdict: ready
issue_counts:
  low: 6
  critical: 0
  high: 0
  medium: 5
  info: 0
findings:
- id: Q1
  severity: medium
  category: ambiguity
  summary: '#154 asks for the whole anti_pattern kind (file, inline anti_patterns dropped, file-less node refused); R-q lists only the file kind. FR-013 and WP10 build the file kind alone and say Refs #154; the operator confirms the scope (Q1).'
- id: C1
  severity: medium
  category: coverage
  summary: "FR-006 (--full) is mapped to WP05, which is held pending the operator's ruling on plan D5; #632 may close only partially (Refs)."
- id: I1
  severity: medium
  category: inconsistency
  summary: data-model.md's --full section still lists specialization_cycle and CLOSURE_WALK; plan D5 proposal (a) covers delegations, references and targets only.
- id: U1
  severity: medium
  category: underspecification
  summary: WP10 (lane-i) and WP03 (lane-c) add example-pack files whose manifest constituents WP04 (lane-d) restamps; lane-i and lane-d run in parallel and both edit built-in/pack-manifest.yaml.
- id: U2
  severity: medium
  category: underspecification
  summary: Several WPs declare justified out-of-map edits to files owned by others (PackRulesets, PackSetCheck, Problems.statusOf, StorageRulesets, CharterRefusalCode.yaml, Arguments, HexagonalArchitectureTest; now WP10's built-in manifest stamp and WP03's re-record of WP10's facts snapshot).
- id: I2
  severity: low
  category: inconsistency
  summary: spec Domain Language defines 'full pass' as everything registration judges; plan D5 narrows it pending the ruling.
- id: I3
  severity: low
  category: inconsistency
  summary: spec US5.2 says remediate refuses on a 'domain fault'; plan D7 and data-model define the precondition as the storage verdict without the manifest gate and form findings.
- id: I4
  severity: low
  category: inconsistency
  summary: "Ruling R-p's rationale says #629 does not close in this PR; R-q (later) keeps #629 and #632 closing here. spec, plan D12 and issue-matrix follow R-q."
- id: N1
  severity: low
  category: coverage
  summary: NFR-002's 30 s --full budget is only exercised once WP05 lands; WP02 leaves the CI step inert.
- id: C2
  severity: low
  category: coverage
  summary: FR-013 adds no HTTP read of the anti_pattern kind (ADR-0052 follow-ups list one as additive); R-q does not ask for it, and the kind stays unserved like step_enhancement.
- id: N2
  severity: low
  category: coverage
  summary: "WP10's change fragment is ### Breaking on the judgment that an *.anti-pattern.yaml warned file_not_held and now refused when invalid is a valid input newly refused (D8 rule); a reviewer may read it as ### Changed."
---

## Specification Analysis Report

Re-run 2026-11-21 after rulings R-p and R-q added WP10 (the `anti_pattern` file kind, FR-013, plan D12) before WP03.
The earlier findings C1, I1, I2, I3, N1 carry over unchanged; U1 and U2 widen to WP10; Q1, I4, C2 and N2 are new.

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| Q1 | Ambiguity | MEDIUM | spec FR-013; plan D12; WP10; issue #154 | #154's scope is the whole kind; R-q names only the file kind | Ask the operator (below). Until answered, WP10 is the file kind only; its stop condition covers another answer |
| C1 | Coverage | MEDIUM | spec FR-006; tasks WP05 | `--full` held for the operator's ruling (plan D5) | Unchanged: on the ruling, update FR-006, data-model and WP05 together, then re-run analyze |
| I1 | Inconsistency | MEDIUM | data-model "`--full` phase"; plan D5 | The data model lists the cycle and `CLOSURE_WALK`; proposal (a) does not | Align after the ruling (same edit as C1) |
| U1 | Underspecification | MEDIUM | WP10 T040; WP03 T015; WP04 T021 | New example-pack files vs the manifest restamp, across lanes i, c and d | WP10 and WP03 stamp their own constituents through `ManifestMembership.stamp`; the lane that merges last re-runs WP04's stamp helper |
| U2 | Underspecification | MEDIUM | WP01, WP03, WP04, WP06, WP07, WP10 | Out-of-map edits to shared files | Merge lanes in dependency order (a → b → i → c, d → f → g → h). Reviewers check each out-of-map edit is minimal and listed |
| I2 | Inconsistency | LOW | spec Domain Language | "full pass" broader than D5 (a) | Align after the ruling |
| I3 | Inconsistency | LOW | spec US5.2; plan D7 | Wording of remediate's refusal | Use D7's wording at the next spec edit |
| I4 | Inconsistency | LOW | decisions R-p, R-q | R-p's "#629 does not close" is superseded by R-q | ADR-0083 (WP08) quotes both and states R-q governs |
| N1 | Coverage | LOW | NFR-002; WP02 T009 | Second timed CI step inert until WP05 | Acceptable; WP05 activates it |
| C2 | Coverage | LOW | FR-013; ADR-0052 follow-ups | No HTTP read of the kind | Leave on #154 or a later contract change; no issue filed (below MEDIUM) |
| N2 | Coverage | LOW | WP10 T041; plan D8 | `### Breaking` vs `### Changed` for the fragment | Keep `### Breaking` per D8's rule; the reviewer may downgrade with a reason |

**Coverage Summary Table:**

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 one-closure-fault-type | yes | T001-T005 | WP01 (approved) |
| FR-002 preset-item-rule | yes | T011-T013 | WP03 (blocked until WP10) |
| FR-003 preset-rule-surfaces | yes | T014, T016 | WP03 |
| FR-004 manifest-stale | yes | T017, T020 | WP04 |
| FR-005 round-trip-one-problem | yes | T018, T019 | WP04 |
| FR-006 validate-full | held | T022-T024 | WP05, ruling pending |
| FR-007 rename | yes | T006-T010 | WP02 (approved) |
| FR-008 report | yes | T025-T027 | WP06 |
| FR-009 remediate | yes | T028-T031 | WP07 |
| FR-010 fixtures | yes | T015, T021, T040 | WP03, WP04, WP10 |
| FR-011 contract | yes | T012, T017, T010, T027 | fragments per WP |
| FR-012 adr | yes | T032-T034 | WP08 (now also R-p, R-q and the ADR-0052 note) |
| FR-013 anti-pattern-file-kind | yes | T037-T041 | WP10 |
| NFR-001 red-first | yes | every WP | stated per subtask |
| NFR-002 timing | partial | T009, T023 | see N1 |
| NFR-003 no-new-walk | yes | T013, T022 | reviewer check; WP10 adds no walk |

**Charter Alignment Issues:** none blocking. Canonical model: WP10 adds the ruled shape with no legacy reader or
alias. The inline `anti_patterns` entries and graph-only nodes it leaves in place are the pre-migration state that
ADR-0052 d16 and ADR-0053 d31 order ("the kind and its schema land first"); whether to retire them in this PR is Q1.
Fixtures follow the model (the built-in anti-pattern becomes a file; no fixture exempted). ADR-0082: WP10 adds no
rule outside the storage edge and no walk. No PIT. Tier R review applies to WP10 as to the others.

**Lanes after re-finalize:** lane-a WP01, lane-b WP02, lane-c WP03 (now on lane-b and lane-i), lane-d WP04, lane-i
WP10 (on lane-b), lane-e WP05, lane-f WP06, lane-g WP07, lane-h WP08, lane-planning WP09. No started lane changed
membership.

**Unmapped Tasks:** none (T035/T036 map to C-006 and SC-002).

**Metrics:**

- Total requirements: 13 FR + 3 NFR + 6 C + 3 SC
- Total tasks: 41
- Coverage: 100% of FRs mapped (FR-006 held)
- Ambiguity count: 1 (Q1)
- Duplication count: 0
- Critical issues: 0

## Operator question

**Q1 — How much of #154 does this PR deliver?** R-q lists the file kind (schema, file rule, binding, kernel facts,
contract, the built-in fixture). #154 also asks to drop the inline `anti_patterns` of styleguides and procedures
(ADR-0052 d16) and to refuse a graph-only `anti_pattern` node (ADR-0052 d14, ADR-0053 d31, "after the migration").
Open-packs holds 13 graph-only nodes and no anti-pattern file, and the inline entries need curation (a severity and a
bad example each, ADR-0053 d30/d32) before they can move.

- (a) **Recommended.** The file kind only, now (WP10 as planned). The inline drop and the graph-only refusal stay
  on #154 for after the open-packs curation (ADR-0052 d16 order). The PR says `Refs #154`.
- (b) The file kind and the graph-only node refusal in this PR; the inline drop later. The 13 open-packs nodes
  are refused at the repin.
- (c) All of #154 in this PR: the file kind, the refusal, and the procedure and styleguide schemas drop
  `anti_patterns` (a `### Breaking` packs-contract change; every open-packs styleguide and procedure with inline
  entries is refused until curated). The PR closes #154.

## Next Actions

- No CRITICAL or HIGH findings: WP10 may start in lane-i now (WP02 is approved). WP03 stays blocked until WP10 is
  approved, then resumes in lane-c.
- Q1 to the operator before WP10 starts; option (a) needs no re-plan.
- C1/I1/I2 resolve together on the `--full` ruling. Re-run analyze after that edit.
- U1/U2: lanes merge in dependency order, and reviewers check out-of-map edits.
