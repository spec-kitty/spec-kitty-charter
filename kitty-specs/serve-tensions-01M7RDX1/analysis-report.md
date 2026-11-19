---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: serve-tensions-01M7RDX1
mission_id: 01M7RDX1S8Y46WAKCP9M29PF9C
generated_at: '2026-11-19T06:47:10.014802+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: .worktrees/serve-tensions-01M7RDX1-coord/kitty-specs/serve-tensions-01M7RDX1/spec.md
    sha256: c3729258875e1a3829627f887c7990ac222865a099087131e1971013f980217f
  plan.md:
    path: .worktrees/serve-tensions-01M7RDX1-coord/kitty-specs/serve-tensions-01M7RDX1/plan.md
    sha256: 8cc6a0e21ac726e5f5e98e147a7b3b038c4e8728603aa4c86eeb7f599b99b27d
  tasks.md:
    path: .worktrees/serve-tensions-01M7RDX1-coord/kitty-specs/serve-tensions-01M7RDX1/tasks.md
    sha256: 87bb41a877fafb4719bfe6b5b7889ddc8fcc7dc2ff63e93fc5f45822520de567
  charter:
    path: .kittify/charter/charter.yaml
    sha256: 78969118cd3893697d6eae119c7306a91003f27d64a8ddfdb4744d92c50e4503
verdict: ready
issue_counts:
  medium: 3
  low: 2
  high: 0
  critical: 0
  info: 0
findings:
- id: I1
  severity: medium
  category: inconsistency
  summary: plan.md IC-07 still names deactivate-source/deactivate-target and IC-02 the smaller-URN-only orientation; the Revisions section (exclude-*, effective-*, G1) supersedes them.
- id: U1
  severity: medium
  category: underspecification
  summary: WP07 case 5 assumes the required side of `B requires A` has no exclude link; whether the exclusion write's artifact_required predicate fires when both sides are selected is not pinned.
- id: U2
  severity: medium
  category: underspecification
  summary: WP02 leaves open which code a qualified self-tension answers at load (binder drg_schema_error vs tension_self).
- id: C1
  severity: low
  category: coverage
  summary: FR-014 (open-packs draft) is already delivered in research/; WP08 only confirms filing and replaces the placeholder.
- id: T1
  severity: low
  category: terminology
  summary: spec Domain Language lists 'canonical orientation' and 'stored orientation'; ADR-0045 decision 1 uses 'stored orientation' and decision 8 'canonical order'; consistent in meaning, worded differently.
---

## Specification Analysis Report

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| I1 | Inconsistency | MEDIUM | plan.md IC-02, IC-07 | Older link names and orientation text in the concern map | The plan's Revisions section states it wins; WP prompts use the revised names. Implementers follow the WP prompts. |
| U1 | Underspecification | MEDIUM | WP07 T036 case 5; WP06 T029 | "required" side predicate under selection | WP06 reuses the exclusion write's predicate; WP07 asserts whatever that predicate yields and, if neither side is required there, uses another required/locked setup (T037 already allows it). |
| U2 | Underspecification | MEDIUM | WP02 context | Self-tension load code | WP02 records its choice; one code is answered. |
| C1 | Coverage | LOW | FR-014, WP08 T041 | Draft already written | No change. |
| T1 | Terminology | LOW | spec Domain Language, ADR-0045 | Two words for the read-time order | No change. |

**Coverage Summary Table:**

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 tension-is-a-relation | Yes | T001, T002 | WP01 |
| FR-002 pair-class-identity | Yes | T002, T014 | WP01, WP03 |
| FR-003 one-spelling | Yes | T005, T007 | WP01 |
| FR-004 pack-consistency | Yes | T009–T012 | WP02 |
| FR-005 registration | Yes | T013–T018 | WP03 |
| FR-006 resolution | Yes | T019, T023 | WP04 |
| FR-007 graph | Yes | T020, T024, T025 | WP04, WP05 |
| FR-008 context | Yes | T026 | WP05 |
| FR-009 tensions read | Yes | T029–T034 | WP06 |
| FR-010 vocabulary | Yes | T029, T032 | WP06 |
| FR-011 taxonomy/references | Yes | T006, T008 | WP01 |
| FR-012 example pack | Yes | T021, T036 | WP04, WP07 |
| FR-013 records | Yes | T038–T040 | WP08 |
| FR-014 open-packs draft | Yes | T041 | WP08 |
| NFR-001 byte-stable | Yes | T023 | WP04 |
| NFR-002 round trip | Yes | T022 | WP04 |
| NFR-003 gates | Yes | T041 | WP08 |
| NFR-004 budget | Yes | T027 | WP05 |

**Charter Alignment Issues:** none. Canonical model with no legacy reader (WP01 T007), canonical-only fixtures, signed commits, squad at plan point (research/squad-plan.md) and before merge (planned), round trip over example packs (WP04).

**Unmapped Tasks:** none.

**Metrics:** 18 functional and non-functional requirements plus 5 constraints and 4 success criteria; 41 tasks; coverage 100%; ambiguity count 2; duplication count 0; critical issues 0.

## Next Actions

No CRITICAL or HIGH findings: implementation may proceed. The three MEDIUM items are resolved inside their WPs as noted.
