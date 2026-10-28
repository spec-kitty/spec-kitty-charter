---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: pack-level-files-round-trip-01M60H1A
mission_id: 01M60H1AK29G4N229BXHYJBH4H
generated_at: '2026-10-28T07:41:59.114532+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: kitty-specs/pack-level-files-round-trip-01M60H1A/spec.md
    sha256: 1211bd16a6069b15a3e116525bfcc9f20f56a9d2603109147f37a85bfbec8af2
  plan.md:
    path: kitty-specs/pack-level-files-round-trip-01M60H1A/plan.md
    sha256: de67e7cc6e7fb716a957a5020535b64817ac76f84d28b4955629c19e249aed3d
  tasks.md:
    path: kitty-specs/pack-level-files-round-trip-01M60H1A/tasks.md
    sha256: 1f672c10414c071a7a5ecafe39671f2f48347a0ef231efd73a663769846a335a
  charter:
    path: .kittify/charter/charter.yaml
    sha256: dd742ab182b61a760929320c028e6174ee7c7f4ffcf5a8d749b94a42847fd9ab
verdict: unknown
issue_counts:
  medium:
  high:
  info:
  low:
  critical:
findings: []
---

## Specification Analysis Report

Mission: `pack-level-files-round-trip-01M60H1A`. Inputs: spec.md, plan.md, tasks.md, WP01–WP08, research.md,
data-model.md and contracts/pack-level-shapes.md. Analyzer: claude, a read-only pass. This second recording covers the spec and plan amendments of 2026-10-28: the operator answers on #195, the ruling on #186, and WP01's widened scope.

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| A1 | Inconsistency | LOW | plan.md:51 | The scale line counts "3 presets", which leaves out the open-packs fixture `example.yaml` (reviewer R15, accepted). | Read it as 4 preset files, 2 of them in the gate. Fix at the next plan touch. |
| A2 | Inconsistency | LOW | plan.md:108 | The project tree still says `PackFileKind (BYTES_KINDS += pack-document)`, from before `pack.md` moved to #194's markdown form (DM-01M60HM8). | WP08 follows #194's design. Read the tree line as superseded. |
| A3 | Inconsistency | LOW | research/grounding-squad.md:97 | F2's disposition says the gate seam switch stays deferred. Post-plan P4 replaced this with the pairing restamp in `DomainRoundTrip`. | research.md P4 governs, and WP06 T023 implements it. |
| A4 | Coverage | LOW | spec FR-013 | The fingerprint requirement covers presets (WP04), the manifest (WP06) and `pack.md` (WP08). The `pack.md` part is blocked on #194's PR-A. | Covered by the WP08 dependency. The hand-off states any open remainder. |
| A5 | Underspecification | MEDIUM | WP03 T008, WP04, WP06 | Out-of-map edits are recorded in the WP prompts (`WholePackStore` and `WholePackStoreTest` in WP03; files owned by WP04 in WP06), but they cross lanes. | Implement WP03, WP04 and WP06 in dependency order and rebase each lane on the previous merge. |
| A6 | Charter alignment | MEDIUM | plan Charter Check, FR-018 | Validate mode now reports the pinned built-in presets and manifest (a new divergence from core's `validate_pack`). Not served over HTTP. | Record it in ADR-0043 and the conformance register (WP07 T029). |
| A7 | Ambiguity | resolved | spec C-001, FR-012 | The operator approved the `pack_unknown_key` description edit and its contract CHANGELOG line on #195 (2026-10-28). C-001 and FR-012 now say so. | None. |
| A8 | Inconsistency | resolved | spec C-006, Assumptions; research drafts | The operator ruled on #186 that this rewrite operates as if core does not exist. The core-first sequencing and draft core issue C-1 are removed. | None. |
| A9 | Coverage | LOW | WP01, WP04, WP06 | A repository-wide test requires every schema to have a file-kind rule, so WP01 also lands the two `files` rules and their knock-on edits, which WP04 and WP06 had held. This is recorded in each WP prompt. | Keep the dependency order. |

**Coverage summary**

| Requirement | Has task? | Task IDs | Notes |
|---|---|---|---|
| FR-001, FR-006 | yes | T001–T003 | |
| FR-002, FR-003 | yes | T004–T006, T012–T013, T017 | |
| FR-004 | yes | T013, T017 | |
| FR-005 | yes | T030–T032 | Blocked on #194 PR-A |
| FR-007, FR-008 | yes | T018–T020 | |
| FR-009, FR-010 | yes | T021, T024 | |
| FR-011, FR-012 | yes | T007–T010 | |
| FR-013 | yes | T014, T022, T030 | |
| FR-014, FR-015 | yes | T016, T023 | |
| FR-016 | yes | research/draft-upstream-issues.md | Already delivered |
| FR-017 | yes | T026–T027 | |
| FR-018 | yes | T015, T022 | |
| NFR-001 | yes | T020 | |
| NFR-002 | yes | T014 | |
| NFR-003 | yes | T026–T029 and every WP's DoD | |
| NFR-004 | yes | T025 | |
| SC-001–SC-005 | yes | mapped in WP frontmatter | |

**Charter alignment issues:** none blocking. A6 is recorded and dispositioned.

**Unmapped tasks:** none.

**Metrics:**

- Total requirements: 18 FR, 4 NFR, 6 C, 7 SC.
- Coverage: 100% of FR and NFR have at least one task.
- Ambiguity count: 0 (A7 resolved).
- Duplication count: 0.
- Critical issues count: 0.

## Next Actions

- No CRITICAL or HIGH issues. Proceed to implementation in dependency order: WP01, WP02 and WP03, then WP04, WP05,
  WP06 and WP07. WP08 waits for #194's PR-A.
- Fold A1–A3 into the plan at the next touch. They do not change any task.
