---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: slice-b-real-packs-register-01M7649C
mission_id: 01M7649C0GKZVR0PWAWJSAV6K9
generated_at: '2026-11-11T20:44:06.112053+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: .worktrees/slice-b-real-packs-register-01M7649C-coord/kitty-specs/slice-b-real-packs-register-01M7649C/spec.md
    sha256: 4b1e149ea2947eb38ef3970207fdca738cf5ac2a264b1cd10d8d84e4e6b5f614
  plan.md:
    path: .worktrees/slice-b-real-packs-register-01M7649C-coord/kitty-specs/slice-b-real-packs-register-01M7649C/plan.md
    sha256: 7d248c36f03f58a4fd2744556633d372149930e468e74de8abeca15d0d801533
  tasks.md:
    path: .worktrees/slice-b-real-packs-register-01M7649C-coord/kitty-specs/slice-b-real-packs-register-01M7649C/tasks.md
    sha256: 633dce0c46abb7017a66d7db1b82cd1f0bf445f62c701dff604e927a060aed40
  charter:
    path: .kittify/charter/charter.yaml
    sha256: 5d4cdfea00b2bef238ca366dfaa85d221f449e62930f61f1106ca9894d74716a
verdict: ready
issue_counts:
  critical: 0
  low: 2
  medium: 4
  high: 0
  info: 0
findings:
- id: I1
  severity: medium
  category: inconsistency
  summary: data-model.md's classification table still has an 'Ignored (new)' row with gate census other:<segment> and 'not refused'; the squad folds make it an excluded file with a checked EXCLUDED census status and a file_not_held diagnostic.
- id: I2
  severity: medium
  category: inconsistency
  summary: plan.md's Design > Swap window text says 'a bounded re-read (three attempts)'; the squad folds and WP06 use an identity-checked re-read with a progress-only cap of 8.
- id: T1
  severity: medium
  category: terminology
  summary: "'ignored' (file-kinds section name) and 'excluded' (the classification and the spec's term) name one concept; WP prompts use both."
- id: O1
  severity: medium
  category: underspecification
  summary: Several WPs make recorded out-of-map edits to files another WP owns (file-kinds.yaml, PackFileKind, PackFileKindTest, RetiredFileFixture, whole-pack-registration.feature, packs_packId.yaml); safe only because the WPs run in strict sequence.
- id: C1
  severity: low
  category: coverage
  summary: NFR-003 (all gates) is mapped to WP08 only; every WP lists its own gate runs, so coverage holds in practice.
- id: U1
  severity: low
  category: underspecification
  summary: FR-024's 304 depends on WP01's spike; if the spike finds 304 cannot be declared cleanly, FR-024 and WP05 need a spec update before WP05 starts.
---

## Specification Analysis Report

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| I1 | Inconsistency | MEDIUM | data-model.md (classification table) vs plan.md "Plan-point squad folds", WP03 | The table predates the folds: fallback files are reported (`file_not_held`) and judged EXCLUDED by the gate. | WP03 is authoritative; update the table in WP08's docs pass or before WP03 starts. |
| I2 | Inconsistency | MEDIUM | plan.md Design > Swap window vs plan.md folds, WP06 | Original design text is superseded by the folds. | WP06 is authoritative; strike the superseded line when the plan is next edited. |
| T1 | Terminology | MEDIUM | file-kinds `ignored:` section, WP03, data-model | Two terms for one concept. | Keep `ignored` only as the section name; call the result "excluded" everywhere (squad A12). |
| O1 | Ownership | MEDIUM | WP02 to WP05 frontmatter | Shared files edited out of map. | Keep the WPs strictly sequential (dependencies already chain them); reviewers check each out-of-map edit is recorded. |
| C1 | Coverage | LOW | tasks.md WP08 | NFR-003 mapped once. | None needed. |
| U1 | Underspecification | LOW | spec FR-024, WP01, WP05 | 304 rests on the spike. | WP01 records the outcome; adjust FR-024 if needed before WP05. |

**Coverage Summary Table:**

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 to FR-003, FR-016, FR-019, FR-022 | yes | T004 to T009 | WP02 |
| FR-004, FR-005, FR-021 | yes | T010 to T016 | WP03 |
| FR-009 to FR-011 | yes | T017 to T021 | WP04 |
| FR-006, FR-024 | yes | T001 to T003, T022 to T027 | WP01 spike, WP05 build |
| FR-007, FR-008, FR-018 | yes | T022 to T027 | WP05 |
| FR-013 to FR-015 | yes | T028 to T033 | WP06 |
| FR-023 | yes | T034, T035 | WP07 |
| FR-012, FR-017 | yes | T036 to T040 | WP08 |
| FR-020 | yes | T041, T042 | WP09 |
| NFR-001, NFR-002 | yes | T032, T033 | WP06 |
| NFR-003 | yes | every WP's validation; T036 to T040 | WP08 runs verifyAll |
| NFR-004 | yes | T009, T013 | WP02, WP03 |
| SC-001 to SC-005 | yes | WP03, WP04, WP05, WP06, WP09 | |

**Charter Alignment Issues:** none. Canonical model only (C-001, C-002) is built into WP02 and WP04; squads at the plan
point (done) and before merge (planned); gates in every WP; issue rules in C-005.

**Unmapped Tasks:** none.

**Metrics:**

- Total requirements: 24 FR, 4 NFR, 6 C, 5 SC
- Total tasks: 42 (T001 to T042), 9 work packages
- Coverage: 100% of FR and NFR have at least one task
- Ambiguity count: 1 (U1)
- Duplication count: 0
- Critical issues: 0

## Next Actions

No CRITICAL or HIGH findings: implementation may proceed. I1, I2 and T1 are wording drift in planning prose; the WP
prompts carry the folded design and are authoritative. Fix the prose in WP08's docs pass.
