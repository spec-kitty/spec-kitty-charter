---
work_package_id: WP02
title: Missions identifier patterns cannot recurse and are capped
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- NFR-001
- NFR-002
- C-003
planning_base_branch: fix/regex-bounds-and-sonar-cleanup
merge_target_branch: fix/regex-bounds-and-sonar-cleanup
branch_strategy: Planning artifacts for this mission were generated on fix/regex-bounds-and-sonar-cleanup. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into fix/regex-bounds-and-sonar-cleanup unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-regex-bounds-and-sonar-cleanup-01M706DF
base_commit: 62d1051450f637918fa10af4ccdeee64d96314a5
created_at: '2026-11-09T13:14:46.062289+00:00'
subtasks:
- T005
- T006
- T007
- T008
history:
- timestamp: '2026-11-09T13:20:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/missions/src/main/java/ai/speckitty/charter/missions/domain/
create_intent:
- service/missions/src/test/java/ai/speckitty/charter/missions/domain/IdentifierBoundsTest.java
execution_mode: code_change
model: ''
owned_files:
- service/missions/src/main/java/ai/speckitty/charter/missions/domain/GuardReference.java
- service/missions/src/main/java/ai/speckitty/charter/missions/domain/LifecycleIdentifier.java
- service/missions/src/main/java/ai/speckitty/charter/missions/domain/MissionTypeIdentifier.java
- service/missions/src/main/java/ai/speckitty/charter/missions/domain/ExpectedArtifact.java
- service/missions/src/test/java/ai/speckitty/charter/missions/domain/IdentifierBoundsTest.java
role: implementer
tags: []
tracker_refs: []
---

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else, then load
`spec-kitty charter context --action implement`.

## Objective

The four missions identifier types refuse an id of any length with their ordinary refusal, never a
`StackOverflowError`. Kebab ids are capped at 64 (per part for GuardReference), the ExpectedArtifact key at 255,
checked before matching. Language and captured parts are unchanged. FR-001 to FR-004, issue #440.

## Context

| File | Today | Target |
|---|---|---|
| `domain/GuardReference.java:17-18` | `KEBAB=[a-z0-9]+(?:-[a-z0-9]+)*`, used twice; `m.group(1)`, `m.group(2)` at :43 | `KEBAB=[a-z0-9]++(?:-[a-z0-9]++)*+`; outer capturing groups kept; each part ≤ 64 |
| `domain/LifecycleIdentifier.java:16` | `[a-z0-9]+(-[a-z0-9]+)*` | possessive, cap 64 |
| `domain/MissionTypeIdentifier.java:16` | `[a-z][a-z0-9]*(-[a-z0-9]+)*`; `ID` reused by `MissionStep.java:48` | `[a-z][a-z0-9]*+(?:-[a-z0-9]++)*+`, cap 64 (MissionStep keeps working: it calls `matches()`) |
| `domain/ExpectedArtifact.java:28` (used :80) | `[a-z][a-z0-9-]*(\.[a-z0-9_-]+)+` | `[a-z][a-z0-9-]*+(?:\.[a-z0-9_-]++)++`, cap 255 |

ExpectedArtifact's `find()` calls (`NON_CANONICAL`, `PARENT`) use other patterns; leave them. See plan.md and
research.md R3 and R8. MissionStep's step id reuses `MissionTypeIdentifier.ID`; if it also needs a cap, apply the
same 64 where MissionStep validates (it is in the same module; record the out-of-map edit in the commit body).

## Subtasks

### T005: Red tests first

Create `service/missions/src/test/java/ai/speckitty/charter/missions/domain/IdentifierBoundsTest.java`:

- `"a" + "-a".repeat(10_000)` refused with the type's ordinary exception (today `StackOverflowError`).
- For ExpectedArtifact: `"a" + ".a".repeat(10_000)`.
- For GuardReference: a long pack part and a long guard part, separately.
- Exactly-at-cap accepted; cap + 1 refused.

Run `./gradlew :missions:test --tests '*IdentifierBoundsTest'`; record the red run in the commit body.

### T006: Possessive rewrite

As in the table above.

### T007: Caps before matching

Add a `MAX_LENGTH` constant per type and check it before matching. GuardReference caps each part at 64.

### T008: Equivalence and captured parts

Table of edge strings as in WP01, run against the old code first. For GuardReference assert `group(1)`/`group(2)`
results through the type's public accessors on `pack::guard:name`-style and bare inputs (whatever the type accepts
today; read the Javadoc and existing tests).

## Definition of Done

- Red run recorded; `:missions:test --tests '*IdentifierBoundsTest'` green after; existing missions tests touching
  these types pass (`./gradlew :missions:test`); spotless and checkstyle clean for `:missions`.
- Missions mutation floor (70) not put at risk: cap boundaries tested both sides.

Implement command: `spec-kitty agent action implement WP02 --agent claude --mission regex-bounds-and-sonar-cleanup-01M706DF`
