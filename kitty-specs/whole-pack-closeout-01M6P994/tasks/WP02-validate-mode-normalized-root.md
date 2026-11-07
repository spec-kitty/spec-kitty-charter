---
work_package_id: WP02
title: '#319: validate mode normalizes the pack root once'
dependencies: []
requirement_refs:
- FR-004
- FR-005
- C-002
- C-003
- SC-002
planning_base_branch: epic-186-closeout
merge_target_branch: epic-186-closeout
branch_strategy: Planning artifacts for this mission were generated on epic-186-closeout. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into epic-186-closeout unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-whole-pack-closeout-01M6P994
base_commit: 3ba7eba157911cb7765a8b5cee3a12249c3d3847
created_at: '2026-11-05T16:49:23.260683+00:00'
subtasks:
- T006
- T007
- T008
phase: Phase 1 - Fixes
history:
- at: '2026-11-05T17:10:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackRootSpellingTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackRootSpellingTest.java
role: implementer
tags: []
task_type: implement
tracker_refs:
- '#319'
---

# Work Package Prompt: WP02 – #319: validate mode normalizes the pack root once

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave
according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Issue #319: validate mode over a pack root spelled with `..` reports every mission-type file unreadable. Cause
(research R2): `FileSystemPackInspector.inspect` (~L144) uses `Path.of(root.locator())` raw;
`FilesystemPackStore.missingNamedFile` normalizes the target (~L710), so `target.startsWith(directory)` (~L719) is
false: "leaves the pack". Fix once where the root is resolved: `toAbsolutePath().normalize()`. Not `toRealPath`
(C-003, #284). Do not touch `Spellings` (C-002).

## Branch Strategy

Planning branch and merge target: `epic-186-closeout`. Implement with
`spec-kitty agent action implement WP02 --agent claude --mission whole-pack-closeout-01M6P994`.

## Subtasks

### T006 — Red first

New `PackRootSpellingTest` in `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/`:

- Fixture per case: `PackFixtures.copyOf("mission-type", host)` (last segment stays `mission-type`); add an output
  `template_path: templates/spec-template.md` to `missions/research/research.mission-type.yaml` the way
  `MissionTypeBindingTest.outputTemplatesNameAFile` does, and write
  `missions/research/templates/spec-template.md`. `Files.createDirectories(host.resolve("x"))`.
- Parameterized spellings: `host/mission-type` (positive control), `host/x/../mission-type`,
  `host/mission-type/.`, `host/mission-type/missions/..`. Locator = the spelling's `toString()` (built by string
  concatenation so `Path` does not normalize it).
- (i) Template present: `inspect(..., FULL)` gives no unreadable file, and the same files/artifacts as the plain run.
- (ii) Template deleted: exactly one unreadable file, the mission-type file, whose detail ends with `: missing`
  (not `leaves the pack`).
- (iii) Load parity: `new FilesystemPackStore(...)` load at the same spelling gives the same verdict (pattern of
  `MissionTypeBindingTest` ~L561-568).
- Run it before the fix and record the failing detail (`... (named by step '...'): leaves the pack`) for the commit
  message and the PR.

### T007 — The fix

`FileSystemPackInspector.inspect`: `Path directory = Path.of(root.locator()).toAbsolutePath().normalize();` with a
one-line comment citing #319 and why not real path. Keep the "is not a directory" refusal and the reported
`PackRoot` unchanged.

### T008 — Linked root and precondition

- Linked-root variant in the same test, guarded by `SymbolicLinks.requireSupport`/`create`: `host/packs/mission-type`
  → `host/checkout/other-name`; template present and missing give the plain run's verdicts (classification reads the
  link's name).
- `FilesystemPackStore.missingNamedFile` javadoc: one line stating `directory` must be absolute and normalized (the
  callers normalize it). No code change there.

## Definition of Done

- The new test is red before T007 for the recorded reason and green after it.
- `./gradlew :packs:test -PrequireSymlinks=true` and `:packs:openPacksRoundTrip --tests '*FileSystemPackInspectorTest*'
  -PopenPacksDir=$PWD/../spec-kitty-open-packs` (a `..` spelling) green.

## Reviewer guidance

Check the trailing `.`/`..` cases exist (they catch a fix that patches only `startsWith`), the negative control
asserts the exact reason, and no `toRealPath` crept in.
