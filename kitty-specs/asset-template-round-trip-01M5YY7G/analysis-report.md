---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: asset-template-round-trip-01M5YY7G
mission_id: 01M5YY7GCDBMADFQB5A2PSR5GX
generated_at: '2026-10-27T18:43:51.945233+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: kitty-specs/asset-template-round-trip-01M5YY7G/spec.md
    sha256: 7f32fe03f8d6af749248b083573931b1b6c537273f86805998f1bb703250d421
  plan.md:
    path: kitty-specs/asset-template-round-trip-01M5YY7G/plan.md
    sha256: 687e3645523781cdfeb261bc04945f147cb4f701be798ac16a134bcc6f364373
  tasks.md:
    path: kitty-specs/asset-template-round-trip-01M5YY7G/tasks.md
    sha256: a1f5afa2204de3c2c1f775a5711fad0c0f0007313701eb9087b234f93ae26c0f
  charter:
    path: .kittify/charter/charter.yaml
    sha256: f09a533e68e7b103f251e94dd9272001a683a26ffbbb539b602a09333ce90aa9
verdict: ready
issue_counts:
  medium: 1
  low: 5
  high: 0
  critical: 0
  info: 0
findings:
- id: U1
  severity: medium
  category: underspecification
  summary: Spec edge case 'a payload a sidecar names that another rule also classifies' has no owning subtask; payloads are a fallback layer, so a sidecar path naming a file of a files-rule kind is not refused.
- id: U2
  severity: low
  category: underspecification
  summary: Spec edge case 'sidecar path escaping assets/' is covered only implicitly (it resolves to no held asset file and is refused as a missing payload); no subtask asserts it.
- id: T1
  severity: low
  category: terminology
  summary: spec.md says 'asset payload'; data-model and tasks name the type AssetFile; the glossary entry (WP08 T034) must tie the two.
- id: N1
  severity: low
  category: coverage
  summary: NFR-004 (load cost under 1 s) is recorded as a measurement in WP05 T024, not asserted; acceptable under NO_FULL_HEAVY_SUITES_IN_MISSION but not a gate.
- id: I1
  severity: low
  category: inconsistency
  summary: plan.md Implementation Concern Map (IC-01..IC-06) predates the eight-WP split; WP ownership in tasks.md is authoritative.
- id: R1
  severity: low
  category: inconsistency
  summary: 'WP09 (reconcile with main after #190/#191/#192 and ADR-0039) edits files owned by WP01, WP03-WP07 as merge resolution; recorded as out-of-map in its prompt and handoff.'
---

## Specification Analysis Report

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| U1 | Underspecification | MEDIUM | spec.md Edge Cases; WP05 T020 | A sidecar whose `path` names a file another rule classifies (e.g. a `*.directive.yaml` under `assets/`) is not covered: the payload rule is a fallback, so that file is never an asset file and the sidecar is refused as "missing payload" — a defensible outcome but with a misleading reason. | In WP05 T020, add a test that pins the outcome (refused, reason names the path) or refine the reason; no new logic needed. |
| U2 | Underspecification | LOW | spec.md Edge Cases; WP02 T008 | `..`/absolute sidecar paths are refused only as "missing payload". | Add a one-line case to WP05 T020's refusal tests. |
| T1 | Terminology | LOW | spec.md Domain Language; data-model.md | "asset payload" (spec, census kind `asset-payload`) vs `AssetFile` (type). | WP08 T034 glossary entry names both. |
| N1 | Coverage | LOW | spec.md NFR-004; WP05 T024 | Measured, not asserted. | Keep; report the number in the PR. |
| R1 | Inconsistency | LOW | tasks/WP09 | Merge resolution touches other WPs' files. | Accepted: recorded as out-of-map; reviewer diffs the merge against both parents. |
| I1 | Inconsistency | LOW | plan.md IC-01..IC-06 | Concern map predates the WP split. | No action; tasks.md is the execution authority. |

**Coverage Summary Table:**

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 content form on rules | yes | T001–T005 | WP01 |
| FR-002 asset descriptor | yes | T007, T017, T024, T031 | |
| FR-003 asset payload bytes | yes | T006, T020, T024, T031 | |
| FR-004 sidecar-less payload | yes | T003, T020, T022 | |
| FR-005 missing payload refused | yes | T020, T024 | |
| FR-006 unknown sidecar fields | yes | T020 | |
| FR-007 template | yes | T009, T019, T022 | |
| FR-008 census kind | yes | T003, T029 | |
| FR-009 gate flips our kinds | yes | T016, T031 | |
| FR-010 pinned violations | yes | T014–T016, T024 | |
| FR-011 fingerprint | yes | T011–T013, T018, T025 | |
| FR-012 relationship endpoints | yes | T027 | |
| FR-013 mission templates untouched | yes | T005, T029 | |
| FR-014 decision recorded | yes | T032–T035 | ADR-0040 also records the relation to ADR-0039 |
| NFR-001 byte fidelity | yes | T024, T031 | |
| NFR-002 fingerprint stability | yes | T018, T025 | |
| NFR-003 build gates | yes | WP DoDs, T038; CI `verifyAll` | |
| NFR-004 load cost | yes (measured) | T024 | N1 |
| C-001..C-005 | yes | DoDs, T002, T020 | |

**Charter Alignment Issues:** none. Contract frozen (C-001); no dependency change; squads ran at pre-spec, plan
and post-tasks; BDD/test-first and non-vacuity controls are in every WP; `verifyAll` owned by CI per directive.

**Unmapped Tasks:** none.

**Metrics:**

- Total Requirements: 14 FR, 4 NFR, 5 C, 4 SC
- Total Tasks: 38 (9 WPs)
- Coverage: 100% of FR/NFR/C have ≥1 task
- Ambiguity Count: 0
- Duplication Count: 0
- Critical Issues Count: 0

## Next Actions

- No CRITICAL or HIGH findings: implementation may proceed. U1 and U2 are folded into WP05 T020's tests during
  implementation (test-only additions inside WP05's owned files; no planning edit needed).
