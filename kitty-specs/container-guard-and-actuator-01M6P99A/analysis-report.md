---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: container-guard-and-actuator-01M6P99A
mission_id: 01M6P99AFK62B7JKDBYNJQMKCD
generated_at: '2026-11-05T16:48:33.225135+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: kitty-specs/container-guard-and-actuator-01M6P99A/spec.md
    sha256: 9a9ae72601c62bac51bb725fbc521ea1299bced4614f5e0f6a899a13ed973c1c
  plan.md:
    path: kitty-specs/container-guard-and-actuator-01M6P99A/plan.md
    sha256: 3c1247a9bb71b4fc99e2e214bf2cca9d605ffef02449901b7b2e8fd1bec163cd
  tasks.md:
    path: kitty-specs/container-guard-and-actuator-01M6P99A/tasks.md
    sha256: ee4718b2730ba4d4083c772b6b76ca63258c7fda5c52a6a7b762b3e56bace2fc
  charter:
    path: .kittify/charter/charter.yaml
    sha256: 4294d3d4acf870b61bc424ac17d3d34708093d4dbc6126f5b1f72941b23497cc
verdict: ready
issue_counts:
  medium: 3
  critical: 0
  high: 0
  low: 3
  info: 0
findings:
- id: C1
  severity: medium
  category: coverage
  summary: 'FR-016 is split: request-detail logging is refused in WP04 and forwarded headers are ignored in WP05, but FR-016 is mapped only to WP05.'
- id: I1
  severity: medium
  category: inconsistency
  summary: docs/guides/run-in-a-container.md is owned by WP01 (docs PR) but rewritten by WP07 (code PR) as an out-of-map edit; the two PRs touch one file.
- id: I2
  severity: low
  category: inconsistency
  summary: plan.md lists management.endpoint.quartz.show-values among the checked keys; WP04 T014 does not. Quartz is not on the classpath.
- id: U1
  severity: low
  category: underspecification
  summary: The ADR number 0060 is fixed in WP01 owned_files although C-006 picks it at writing time.
- id: U2
  severity: medium
  category: underspecification
  summary: NFR-002 (native job green) has no dedicated subtask; it is only a WP05 Definition-of-Done item checked in CI.
- id: U3
  severity: low
  category: underspecification
  summary: WP04 T015 leaves the bracketed [::1] server.address form as 'check what InetAddress accepts'.
---

## Specification Analysis Report

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| C1 | Coverage | MEDIUM | spec.md FR-016; tasks WP04 T014, WP05 T018 | FR-016 is split across two WPs and mapped to one. | The WP04 implementer covers the logging half (already in T014); reviewers check both. |
| I1 | Inconsistency | MEDIUM | WP01 and WP07 owned_files | Both PRs touch the container guide. | Keep WP01's edit to one bullet in "Limits for now"; rebase the code PR after the docs PR. |
| I2 | Inconsistency | LOW | plan.md "Exposure start-up check"; WP04 T014 | The quartz key appears only in the plan. | Follow WP04 (no quartz). |
| U1 | Underspecification | LOW | WP01 frontmatter | The number is fixed early. | Recheck at writing time; rename if taken. |
| U2 | Underspecification | MEDIUM | NFR-002 | The native proof lives only in CI. | Watch the native job in closeout before ready-for-review. |
| U3 | Underspecification | LOW | WP04 T015 | One test input is undecided. | Decide in the test from observed `InetAddress` behaviour. |

**Coverage Summary Table:**

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 to FR-004, FR-006, FR-009 | Yes | T005–T008 | |
| FR-005, FR-007, FR-010 | Yes | T009–T012 | |
| FR-011 to FR-015 | Yes | T013–T016 | |
| FR-008, FR-016, FR-017 | Yes | T017–T020 | FR-016 also covered in T014 (C1) |
| FR-018 | Yes | T021–T022 | |
| FR-019 | Yes | T001–T004 | |
| FR-020 | Yes | T023–T025 | |
| NFR-001 to NFR-004 | Yes | T017, T019, T004, T025 | NFR-002 is checked in CI (U2) |

**Charter Alignment Issues:** none. The contract change follows ADR-0022 by a fragment (C-003). Dependency
verification is strict (NFR-001). The adversarial squad ran pre-spec and covers the plan's design; a pre-merge
squad is planned in closeout.

**Unmapped Tasks:** none.

**Metrics:**

- Total requirements: 20 FR, 4 NFR, 6 C, 4 SC
- Total tasks: 25
- Coverage: 100% of FR and NFR have at least one task
- Ambiguity count: 1 (U3)
- Duplication count: 0
- Critical issues count: 0

## Next Actions

No critical or high findings, so implementation may proceed. Handle C1, I1 and U2 during implementation and
closeout, as noted.
