---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: procedure-antipattern-toolguide-round-trip-01M60H9B
mission_id: 01M60H9B96RVV0JBDF4CHB5VS6
generated_at: '2026-10-29T06:32:58.253403+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: kitty-specs/procedure-antipattern-toolguide-round-trip-01M60H9B/spec.md
    sha256: d1a41a891e8d91c8e33558105e980d56419ceac0397a91ec1b49d6571e9c1688
  plan.md:
    path: kitty-specs/procedure-antipattern-toolguide-round-trip-01M60H9B/plan.md
    sha256: 6f920c5fd0972c4f436cc949f8312177ae647d605da1a495d47a8969f9e2ce3e
  tasks.md:
    path: kitty-specs/procedure-antipattern-toolguide-round-trip-01M60H9B/tasks.md
    sha256: 2011e5a1957dc456c04ba3f934e2a6a9684584a9f63c6a466d89a29729e6f890
  charter:
    path: .kittify/charter/charter.yaml
    sha256: dd742ab182b61a760929320c028e6174ee7c7f4ffcf5a8d749b94a42847fd9ab
verdict: blocked
issue_counts:
  critical: 1
  medium: 3
  low: 2
  high: 0
  info: 0
findings:
- id: C1
  severity: critical
  category: charter
  summary: 'Charter PROJECT_002 (charter.yaml) still grants no exception for core-oracle divergence and incompatible contract changes reaching core, conflicting with the #186 ruling the spec and plan apply; the amendment is PR #262 (green, awaiting operator merge).'
- id: R1
  severity: medium
  category: underspecification
  summary: WP10 depends on an external, operator-gated event (open-packs PR merge) with no fallback stated if the merge is delayed or changed in review.
- id: R2
  severity: medium
  category: inconsistency
  summary: From WP03 (sanitized procedure schema; validate-mode corpus test) through WP09, :packs:openPacksRoundTrip (part of check) is red at the old pin by design (operator-accepted red window); charter gate 'verifyAll before merge' holds at PR level, closed by WP10.
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
| C1 | Charter | CRITICAL | .kittify/charter/charter.yaml PROJECT_002; plan.md Charter Check; spec.md C-001, C-004 | PROJECT_002 grants no exception; the mission relies on the #186 ruling. | Merge PR #262 (operator-approved amendment), then re-run analyze. PR-A1 proceeds meanwhile (touches neither invariant). |
| R1 | Underspecification | MEDIUM | tasks/WP10, plan IC-06 | Open-packs merge gates WP10 with no fallback. | Escalate at WP09 completion; options: wait, pin to the PR head temporarily, or known-failure fallback. |
| R2 | Inconsistency | MEDIUM | tasks.md header, WP03/WP04/WP06 | Red window on `check` inside the mission branch (operator accepted 2026-10-29). | Closed by WP10; PR-A2 opens only after it. |
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

- C1: charter amendment PR #262 awaits the operator merge; re-run analyze after it lands.
- Planning edit since the last report: ADR renumbered to 0043 (#195 merged ADR-0042 with rules S1–S8).
- R1, R2: accept or adjust before WP04/WP09 start; U1, G1 during implementation.
