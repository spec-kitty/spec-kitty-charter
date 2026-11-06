---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: test-tidy-mockito-refusal-codes-01M6KZ05
mission_id: 01M6KZ0553DQBYH16HZDEWFH87
generated_at: '2026-11-04T19:24:51.695765+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: kitty-specs/test-tidy-mockito-refusal-codes-01M6KZ05/spec.md
    sha256: 020ceccb36a0aeffef0a514d424d33a589adcb0447163bf92b4b00c7a0423279
  plan.md:
    path: kitty-specs/test-tidy-mockito-refusal-codes-01M6KZ05/plan.md
    sha256: 64bf78e0f7ebc5bb81ecbc895e742048df350fa51f5fe8a8ee8ea65d7fa88b4a
  tasks.md:
    path: kitty-specs/test-tidy-mockito-refusal-codes-01M6KZ05/tasks.md
    sha256: 97a72cea500b9c48be0cd7f16fac511a90e370b627c24c0bf5778a6e04c54be7
  charter:
    path: .kittify/charter/charter.yaml
    sha256: af2620decc50721d9aa4e6f169d3e1e8c718412cc7d347a2891ca6d8aacbd351
verdict: ready
issue_counts:
  medium: 1
  critical: 0
  low: 4
  high: 0
  info: 0
findings:
- id: C1
  severity: low
  category: coverage
  summary: NFR-001 and NFR-002 (gate green, no suppressions) have no explicit WP requirement_refs; they are carried only in both WPs' Definition of Done.
- id: I1
  severity: low
  category: inconsistency
  summary: spec.md Edge Cases says PackWriteSafetyTest 106 and 108 are two checks in one statement chain; they are two separate statements in one test method.
- id: I2
  severity: low
  category: inconsistency
  summary: FR-003 samples one check per distinct expected code; WP02 T007 samples per code and per throwing method. The WP is stricter, which is consistent but should be read as the operative rule.
- id: I3
  severity: low
  category: inconsistency
  summary: The immutable mission title says 'drop Mockito' while the ruled scope keeps it; spec.md records the amendment, the PR title must follow the ruling.
- id: U1
  severity: medium
  category: underspecification
  summary: WP01 T003 regen-family list relies on 'help' resolving every resolvable configuration; configurations created only during task execution (e.g. PIT's, native image) are covered by explicit task runs, but the release workflow's checkServiceReleaseTag is not run. The cold-cache T004 run would not catch a release-only artifact.
---

## Specification Analysis Report

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| C1 | Coverage | LOW | spec.md NFR table; tasks/WP01, WP02 DoD | NFRs carried in DoD only | Acceptable; reviewer checks both DoDs |
| I1 | Inconsistency | LOW | spec.md Edge Cases | 106/108 are separate statements | Read as "two checks in one method" |
| I2 | Inconsistency | LOW | spec.md FR-003; WP02 T007 | WP samples per throwing method too | WP rule is operative (stricter) |
| I3 | Inconsistency | LOW | meta.json friendly_name | Title predates the ruling | PR title/body state "keep Mockito at Spring's version" |
| U1 | Underspecification | MEDIUM | WP01 T003/T004 | release-only task not in the regen run | Implementer also runs `checkServiceReleaseTag -PreleaseTag=<a valid tag>` (or confirms it resolves nothing) with `--write-verification-metadata` |

**Coverage Summary Table:**

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 mockito-at-spring-version | yes | T001, T002 | |
| FR-001a metadata-regenerated | yes | T003, T004 | see U1 |
| FR-002 refusal-codes-pinned | yes | T005, T006, T008 | |
| FR-003 planted-break-proof | yes | T007 | |
| NFR-001 gate-green | yes (DoD) | T004, T006 | |
| NFR-002 no-suppressions | yes (DoD) | WP02 DoD | |

**Charter Alignment Issues:** none. Test-first/no-fake-green is served by FR-003; DIRECTIVE_041 by C-001; supply-chain safety recorded in research R-03.

**Unmapped Tasks:** none.

**Metrics:**

- Total Requirements: 4 FR, 2 NFR, 5 C, 3 SC
- Total Tasks: 8
- Coverage: 100 % (FR with >= 1 task)
- Ambiguity Count: 0
- Duplication Count: 0
- Critical Issues Count: 0

### Next Actions

No CRITICAL or HIGH findings: proceed to implementation. Fold U1 into WP01's execution (run the release task under the regen as well).
