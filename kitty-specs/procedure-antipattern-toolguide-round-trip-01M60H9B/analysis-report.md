---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: procedure-antipattern-toolguide-round-trip-01M60H9B
mission_id: 01M60H9B96RVV0JBDF4CHB5VS6
generated_at: '2026-10-29T05:27:15.682727+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: kitty-specs/procedure-antipattern-toolguide-round-trip-01M60H9B/spec.md
    sha256: 0f7747fbbfa52ffd90b6096956f50ebdc2c4c7b932f8ab1f6d208e52fb79a7b4
  plan.md:
    path: kitty-specs/procedure-antipattern-toolguide-round-trip-01M60H9B/plan.md
    sha256: 47f699bae23cd8f77da9c7ae0a141b2256ac328a4ea43432fd92e08dd3ca2434
  tasks.md:
    path: kitty-specs/procedure-antipattern-toolguide-round-trip-01M60H9B/tasks.md
    sha256: 492dce83f57f3c4f684699142cb00e996c3a1acdfd454a6f3284af50ec91499c
  charter:
    path: .kittify/charter/charter.yaml
    sha256: dd742ab182b61a760929320c028e6174ee7c7f4ffcf5a8d749b94a42847fd9ab
verdict: blocked
issue_counts:
  critical: 1
  low: 2
  medium: 3
  high: 0
  info: 0
findings:
- id: C1
  severity: critical
  category: charter
  summary: "Charter 'What must never go wrong' 1–2 (no divergence from the oracle, no incompatible contract change reaching core; 'no exception is granted') conflict with the #186 operate-as-if-core-does-not-exist ruling the spec and plan apply; charter.md has not been amended."
- id: R1
  severity: medium
  category: underspecification
  summary: WP10 depends on an external, operator-gated event (open-packs PR merge) with no fallback stated if the merge is delayed or changed in review.
- id: R2
  severity: medium
  category: inconsistency
  summary: WP04–WP09 leave :packs:openPacksRoundTrip (part of check, so verifyAll) red at the old pin by design; charter gate 'verifyAll before merge' holds only at PR level — lane merges into the mission branch run red.
- id: U1
  severity: medium
  category: ambiguity
  summary: WP05 T021 leaves the companion-form type open (reuse ContentForm.TEXT or add CompanionForm); WP03 T014 contains a self-cancelling instruction about refusing profile ids.
- id: O1
  severity: low
  category: ownership
  summary: 'Recorded out-of-map edits: WP05→WholePackCensus, WP06→file-kinds.yaml and PackFileKindTest, WP10→migration package and build.gradle.kts, WP11→changelog README; all dependency-ordered.'
- id: G1
  severity: low
  category: coverage
  summary: Edge case 'two toolguides naming the same guide' has no explicit test named in WP06 T031 (case-only collision is named).
---

## Specification Analysis Report

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| C1 | Charter | CRITICAL | .kittify/charter/charter.md:24-31; plan.md Charter Check, Complexity Tracking; spec.md C-001, C-004 | Charter invariants 1–2 grant no exception; the mission relies on the #186 ruling, which the charter does not yet carry. | Explicit charter update recording the rewrite-period ruling (operator decision), outside or alongside this mission, before implementation. |
| R1 | Underspecification | MEDIUM | tasks/WP10, plan IC-06 | Open-packs merge gates WP10 with no fallback. | Escalate at WP09 completion; options: wait, pin to the PR head temporarily, or known-failure fallback. |
| R2 | Inconsistency | MEDIUM | tasks.md header, WP04/WP06 | Red window on `check` inside the mission branch. | Accept explicitly (PR opens only after WP10) or reorder so the migration lands before registration. |
| U1 | Ambiguity | MEDIUM | tasks/WP05 T021, tasks/WP03 T014 | Implementer choices left open / contradictory text. | Implementer decides and records; reviewer checks. |
| O1 | Ownership | LOW | tasks.md "Shared files" | Out-of-map edits declared. | None; reviewers verify they stay small. |
| G1 | Coverage | LOW | spec.md Edge Cases; tasks/WP06 T031 | Duplicate-guide naming not named in tests. | Add to WP06 T031 during implementation. |

**Coverage Summary Table:**

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 sanitized-procedure-shape | yes | T012–T015 | WP03 |
| FR-002 procedure-round-trip | yes | T016–T019, T051 | WP04, WP10 |
| FR-003 / FR-003a anti-patterns | yes | T014, T019, T051 | WP03, WP04, WP10 |
| FR-004 structured-guide | yes | T026–T031, T051 | WP06, WP10 |
| FR-005 markdown-form | yes | T001–T011 | WP01, WP02 |
| FR-005a markdown-companions | yes | T021–T025 | WP05 |
| FR-006 migrate-upstream | yes | T044–T053 | WP09, WP10 |
| FR-007 graph-unchanged | yes | T050 | WP10 |
| FR-008 references-not-minted | yes | T018 | WP04 |
| FR-009 baseline-and-file-kinds | yes | T030, T050 | WP06, WP10 |
| FR-010 guide-over-http | yes | T032–T043 | WP07, WP08 |
| FR-011 guide-h1 | yes | T027, T039 | WP06, WP07 |
| NFR-001..005 | yes | T005, T036, T051, T053, T039 | |
| C-001..C-007 | yes | T038, T049, T054, T052 | C-004 depends on C1 |
| SC-001..SC-006 | yes | T005, T019, T031, T051, T053 | |

**Charter Alignment Issues:** C1.

**Unmapped Tasks:** none.

**Metrics:**

- Total Requirements: 13 FR + 5 NFR + 7 C + 6 SC = 31
- Total Tasks: 58
- Coverage: 100%
- Ambiguity Count: 1
- Duplication Count: 0
- Critical Issues Count: 1

### Next Actions

- Resolve C1 before `/spec-kitty.implement` (operator: explicit charter amendment).
- R1, R2: accept or adjust before WP04/WP09 start; U1, G1 during implementation.
