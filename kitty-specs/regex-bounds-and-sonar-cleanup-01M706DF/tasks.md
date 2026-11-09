# Tasks: Bounded identifier patterns and Sonar cleanup

**Mission**: `regex-bounds-and-sonar-cleanup-01M706DF` · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Red tests: 10 000-segment and over-cap inputs for the packs identifier types | WP01 | |
| T002 | Possessive rewrite of the packs identifier patterns, groups kept | WP01 | |
| T003 | Length caps checked before matching in the packs identifier types | WP01 | |
| T004 | Edge-string equivalence table for the packs identifier types | WP01 | |
| T005 | Red tests: 10 000-segment and over-cap inputs for the missions identifier types | WP02 | [P] |
| T006 | Possessive rewrite of the missions identifier patterns, groups kept | WP02 | [P] |
| T007 | Length caps checked before matching in the missions identifier types | WP02 | [P] |
| T008 | Edge-string equivalence and captured-part tests for the missions identifier types | WP02 | [P] |
| T009 | maxLength 64 and 255 on the pack schema id fields listed in plan.md | WP03 | [P] |
| T010 | Breaking contract fragment under contracts/charter/changes/ | WP03 | [P] |
| T011 | Contract scenario: a 65-character pack id refuses the pack as 500 pack_unreadable | WP03 | [P] |
| T012 | Check every id in the pinned open-packs and packs/ against the caps | WP03 | [P] |
| T013 | Red test: long sexagesimal scalar overflows the YAML resolver | WP04 | [P] |
| T014 | Possessive rewrite of the YAML INT and FLOAT resolver patterns | WP04 | [P] |
| T015 | Possessive rewrite of GitSourceRules.PATH with a path-at-cap control | WP04 | [P] |
| T016 | PackPolicy fragment message as one map/orElseGet | WP04 | [P] |
| T017 | Regression guard scanning main code for non-possessive repeated groups | WP05 | |
| T018 | Guard fixture tests: a planted recursive pattern is flagged, possessive passes | WP05 | |
| T019 | Allow-list for patterns the heuristic cannot judge, each with its reason | WP05 | |

## Phase 1: Identifier types (User Story 1)

### WP01: Packs identifier patterns cannot recurse and are capped

- **Goal**: the seven packs identifier types refuse a long id with their ordinary refusal, never an `Error`. Kebab
  ids are capped at 64, `Executable` at 255. FR-001, FR-002, FR-003, FR-004.
- **Priority**: P1 (MVP).
- **Independent test**: a parameterized test feeds `"a" + "-a".repeat(10_000)` to each constructor and expects the
  refusal; ids at exactly the cap are accepted.
- **Prompt**: [tasks/WP01-packs-identifier-bounds.md](tasks/WP01-packs-identifier-bounds.md)

T001 Red tests: 10 000-segment and over-cap inputs for the packs identifier types (WP01)
T002 Possessive rewrite of the packs identifier patterns, groups kept (WP01)
T003 Length caps checked before matching in the packs identifier types (WP01)
T004 Edge-string equivalence table for the packs identifier types (WP01)

- **Dependencies**: none.
- **Risks**: GuardImplementation reads groups 1-3, so its group structure must be kept.

### WP02: Missions identifier patterns cannot recurse and are capped

- **Goal**: the four missions identifier types do the same. Kebab ids are capped at 64 (per part for
  GuardReference), the ExpectedArtifact key at 255. FR-001, FR-002, FR-003, FR-004.
- **Priority**: P1.
- **Independent test**: as WP01, in the missions module.
- **Prompt**: [tasks/WP02-missions-identifier-bounds.md](tasks/WP02-missions-identifier-bounds.md)

T005 Red tests: 10 000-segment and over-cap inputs for the missions identifier types (WP02)
T006 Possessive rewrite of the missions identifier patterns, groups kept (WP02)
T007 Length caps checked before matching in the missions identifier types (WP02)
T008 Edge-string equivalence and captured-part tests for the missions identifier types (WP02)

- **Dependencies**: none (parallel with WP01).
- **Risks**: GuardReference reads groups 1 and 2; `MissionTypeIdentifier.ID` is reused by `MissionStep`.

## Phase 2: Contract (User Story 1)

### WP03: Pack schemas cap id length (Breaking)

- **Goal**: the pack schemas refuse an over-cap id as `pack_unreadable` before the domain sees it, announced as
  Breaking. FR-005, FR-006, FR-007.
- **Priority**: P1.
- **Independent test**: the new contract scenario; the whole-pack round trip still passes.
- **Prompt**: [tasks/WP03-pack-schema-caps.md](tasks/WP03-pack-schema-caps.md)

T009 maxLength 64 and 255 on the pack schema id fields listed in plan.md (WP03)
T010 Breaking contract fragment under contracts/charter/changes/ (WP03)
T011 Contract scenario: a 65-character pack id refuses the pack as 500 pack_unreadable (WP03)
T012 Check every id in the pinned open-packs and packs/ against the caps (WP03)

- **Dependencies**: none (parallel with WP01 and WP02).
- **Risks**: a path field wrongly capped at 64 would refuse real manifests (paths reach 89 characters).

## Phase 3: Resolver and tidy-ups (User Stories 2 and 3)

### WP04: YAML resolver, git source path and pack policy message

- **Goal**: the YAML int and float resolver and the git source path cannot recurse, with resolution unchanged, and
  the pack policy message resolves the kind once. FR-008, FR-009, FR-010, FR-012.
- **Priority**: P2.
- **Independent test**: a 10 000-segment sexagesimal scalar reads without an `Error`; short scalars resolve as
  before; a path at the URL cap still matches; the message text is unchanged.
- **Prompt**: [tasks/WP04-yaml-git-policy.md](tasks/WP04-yaml-git-policy.md)

T013 Red test: long sexagesimal scalar overflows the YAML resolver (WP04)
T014 Possessive rewrite of the YAML INT and FLOAT resolver patterns (WP04)
T015 Possessive rewrite of GitSourceRules.PATH with a path-at-cap control (WP04)
T016 PackPolicy fragment message as one map/orElseGet (WP04)

- **Dependencies**: none.

## Phase 4: Guard (User Story 3)

### WP05: Regression guard against recursive patterns

- **Goal**: the build flags main code that compiles a pattern with a repeated group that is not possessive. The
  guard is heuristic and says so. FR-011.
- **Priority**: P3.
- **Independent test**: fixture tests; the real main code passes once WP01, WP02 and WP04 are in.
- **Prompt**: [tasks/WP05-regex-recursion-guard.md](tasks/WP05-regex-recursion-guard.md)

T017 Regression guard scanning main code for non-possessive repeated groups (WP05)
T018 Guard fixture tests: a planted recursive pattern is flagged, possessive passes (WP05)
T019 Allow-list for patterns the heuristic cannot judge, each with its reason (WP05)

- **Dependencies**: WP01, WP02, WP04 (the scan of real main code passes only after their rewrites).

## MVP

WP01 alone removes the overflow from the packs identifier types reachable through pack registration.
