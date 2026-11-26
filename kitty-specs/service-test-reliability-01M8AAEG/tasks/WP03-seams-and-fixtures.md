---
work_package_id: WP03
title: F12 members, seam wording and fixture arities
dependencies: []
requirement_refs:
- FR-007
- FR-007a
- FR-008
planning_base_branch: test/659-state-waits-and-main-hooks
merge_target_branch: test/659-state-waits-and-main-hooks
branch_strategy: Planning artifacts for this mission were generated on test/659-state-waits-and-main-hooks. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test/659-state-waits-and-main-hooks unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-service-test-reliability-01M8AAEG
base_commit: 9f033e1c9c199f4eb44323835a0985186dc5ff2e
created_at: '2026-11-26T05:19:53.151175+00:00'
subtasks:
- T015
- T016
- T017
- T018
- T019
phase: Phase 1 - F12 members, seam wording and fixture arities
history:
- at: '2026-11-25T22:20:00Z'
  actor: claude::java-jenny
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureQueries.java
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CommitStores.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureQueries.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistryWriteLock.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackCommitStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/RegistrationWiring.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureQueriesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureViewGovernanceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureViewTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureViewHeldWinnersTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureViewMergedTest.java
- service/packs/src/testFixtures/java/ai/speckitty/charter/packs/infra/storage/CommitStores.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CommitStores.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StoreSeams.java
- service/packs/src/testFixtures/java/ai/speckitty/charter/packs/fixtures/ClosureViews.java
- service/packs/src/testFixtures/java/ai/speckitty/charter/packs/fixtures/CompositionFacts.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP03 - F12 members, seam wording and fixture arities

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Review Feedback

Check the `review_ref` field in the event log (`spec-kitty agent tasks status`) and address every item before claiming done.

## Objectives & Success Criteria

Deliver FR-007, FR-007a, FR-008 of `kitty-specs/service-test-reliability-01M8AAEG/spec.md` as described in `plan.md` and the dispositions in `research.md`.

## Context & Constraints

- Charter: `.kittify/charter/charter.md` (test first, no fake green, no PIT, canonical shapes, nits folded).
- Retrofit: the first pass is on the branch already (commits a59eef1d1, 95989552f, 91d706a24, 6da6cface). Build on it; do not revert it.
- C-001: no production behaviour or contract change. C-003: `PackMutabilityPolicy` is out; keep edits to `InForceResolution`, `PackCompositionFacts` and `ClosureView` minimal (the #637 runner rebases onto this branch).
- Commits: signed, `Co-Authored-By: Claude <noreply@anthropic.com>`, no model names. Clear only `_JAVA_OPTIONS`; keep `JAVA_TOOL_OPTIONS`.

## Branch Strategy

- **Planning base branch**: `test/659-state-waits-and-main-hooks`
- **Merge target branch**: `test/659-state-waits-and-main-hooks`
- Execution worktrees are allocated per computed lane from `lanes.json`.

## Subtasks

### T015

ClosureQueries.allConfigured() leaves main; its five test callers read the same through the public closure queries

### T016

RegistryWriteLock.hasQueuedThread and PackCommitStore.SwapPhases/moveAside stay; their javadoc records why (no state-observable alternative without them) instead of calling them test hooks; RegistrationWiring class javadoc drops "and test seam"

### T017

FileSystemPackInspector and PackCommitStore constructor javadoc stops saying "the seam a test uses"; PackCommitStore.MOVE_ASIDE no wider than needed

### T018

CommitStores moves from packs testFixtures to packs src/test (only packs tests use it); delete the CompositionFacts.of and ClosureViews.of arities that no test calls

### T019

Red-first: a mutant per moved behaviour where a test changed (allConfigured callers); compile proof for removals

## Definition of Done

- Every subtask done and recorded with `spec-kitty agent tasks mark-status`.
- Every changed test has a mutant that turned it red, reverted, noted for `research.md`.
- The owning module tests pass; every red attributed.
- No file outside `owned_files` changed without a one-line rationale.

## Risks

- A mutant left in place. Diff main against the branch before commit.
- Thread-state barriers that a different parked state could satisfy; name the frame or lock.

## Reviewer Guidance

Run each mutant named in the subtasks and see the test go red; check no `Thread.sleep` was added; check the javadoc no longer calls a kept member a test hook.
