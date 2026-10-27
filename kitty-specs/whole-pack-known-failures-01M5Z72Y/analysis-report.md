---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: whole-pack-known-failures-01M5Z72Y
mission_id: 01M5Z72Y0HB97SQF4P7V832TJF
generated_at: '2026-10-27T18:12:20.980614+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: kitty-specs/whole-pack-known-failures-01M5Z72Y/spec.md
    sha256: 0222ad0ac1e68bfee173e683517876e2b2c3598cd63b2a4ecc97b1d3f55fe109
  plan.md:
    path: kitty-specs/whole-pack-known-failures-01M5Z72Y/plan.md
    sha256: b0e87dbcecce2602a2585befa5a45b254330dd9e4948d03efaa63cfa3f2f1b3a
  tasks.md:
    path: kitty-specs/whole-pack-known-failures-01M5Z72Y/tasks.md
    sha256: fc248d8f4cc544caff16053637a7527310a1e97d2ab46697333b1faf627b63f4
  charter:
    path: .kittify/charter/charter.yaml
    sha256: dd742ab182b61a760929320c028e6174ee7c7f4ffcf5a8d749b94a42847fd9ab
verdict: ready
issue_counts:
  low: 3
  critical: 0
  high: 0
  medium: 1
  info: 0
findings:
- id: C1
  severity: medium
  category: coverage
  summary: Edge case 'the list file is missing fails the load' has no explicit test subtask; fromClasspath throws IllegalStateException today, but nothing pins it for the new resource.
- id: I1
  severity: low
  category: inconsistency
  summary: 'Spec, plan and WP prompts cite bare issue numbers (#247, #190-#193, #235, #236); each will need an issue-matrix row or a context-only marking before approval.'
- id: U1
  severity: low
  category: underspecification
  summary: WP01 T002 makes the parameterized issue-format test conditional on junit-jupiter-params; the version catalog's junit-jupiter aggregate already provides it, so the condition is moot.
- id: A1
  severity: low
  category: ambiguity
  summary: The behaviour of an ambiguous (double-listed) file that round-trips is implied (expected-red's UNEXPECTEDLY_GREEN fires) but not stated in data-model.md.
---

## Specification Analysis Report

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| C1 | Coverage | MEDIUM | spec.md Edge Cases; tasks/WP01 T003 | Missing-resource edge case not pinned by a test | WP01 may add a one-line test, or rely on the existing `resource()` guard; reviewer to confirm |
| I1 | Inconsistency | LOW | spec.md, plan.md, tasks/WP01, tasks/WP02 | Bare issue references | Record issue-matrix rows (#247 fixed; others not-applicable / context) at review |
| U1 | Underspecification | LOW | tasks/WP01 T002 | Conditional on junit params is moot | Use `@ParameterizedTest`; `org.junit.jupiter:junit-jupiter` includes params |
| A1 | Ambiguity | LOW | data-model.md Judgement | Ambiguous + green behaviour implicit | Implement as WP01 T005 states (no known-failure problem; expected-red's ratchet still fires) |

**Coverage Summary Table:**

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 separate list | Yes | T001, T004 | |
| FR-002 listed+failing passes | Yes | T002, T005 | |
| FR-003 listed+passing fails | Yes | T002, T005 | |
| FR-004 issue reference | Yes | T002, T004 | |
| FR-005 unlisted fails | Yes | T002 | ratchet |
| FR-006 moved difference | Yes | T002, T005 | refusal match included |
| FR-007 misplaced entries | Yes | T003, T005 | |
| FR-008 report count | Yes | T006 | |
| FR-009 docs | Yes | T008, T009, T010 | |
| NFR-001 exact assertions | Yes | T002, T003, T007 | mutation check in T007 |
| NFR-002 real list in verifyAll | Yes | T003 | untagged test |
| NFR-003 deterministic report | Yes | T006 | existing test |
| C-001..C-003 | Yes | WP01 constraints | rebase before review |

**Charter Alignment Issues:** none. Test first (T002/T003 before T004–T006), no fake green (NFR-001, mutation check), verifyAll on Java 25 (T007), docs checks (T010), squad at plan point (research.md) and before merge (closeout).

**Unmapped Tasks:** none.

**Metrics:**

- Total Requirements: 18 (9 FR, 3 NFR, 3 C, 3 SC)
- Total Tasks: 10
- Coverage: 100%
- Ambiguity Count: 1
- Duplication Count: 0
- Critical Issues Count: 0

**Next Actions:** no CRITICAL or HIGH findings; proceed to implementation. C1 and A1 are reviewer checkpoints for WP01; I1 is handled at review with the issue matrix.
