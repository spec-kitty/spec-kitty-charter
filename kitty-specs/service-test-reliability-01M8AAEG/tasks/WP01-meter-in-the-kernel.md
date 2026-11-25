---
work_package_id: WP01
title: Meter in the kernel; units with their owners; no static counters in main
dependencies: []
requirement_refs:
- FR-005
- FR-005a
- FR-005b
- FR-005c
- FR-005d
- FR-007a
- SC-002
planning_base_branch: test/659-state-waits-and-main-hooks
merge_target_branch: test/659-state-waits-and-main-hooks
branch_strategy: Planning artifacts for this mission were generated on test/659-state-waits-and-main-hooks. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test/659-state-waits-and-main-hooks unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
- T007
phase: Phase 1 - Meter in the kernel; units with their owners; no static counters in main
history:
- at: '2026-11-25T22:20:00Z'
  actor: claude::java-jenny
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/metering/
create_intent:
- service/deployable/src/test/java/ai/speckitty/charter/deployable/archfixtures/StaticCounterFixture.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/metering/WorkMeter.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/metering/WorkUnit.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/metering/package-info.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/metering/WorkMeterTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ReadWork.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StoreWork.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/CacheWork.java
execution_mode: code_change
model: ''
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/metering/WorkMeter.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/metering/WorkUnit.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/metering/package-info.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/metering/WorkMeterTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/WorkMeter.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/InForceResolution.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ReadWork.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StoreRead.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StoreWork.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/CompositionFactsCache.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/CacheWork.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ReadCounters.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/OneReadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/OneReadRegistrationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackReadingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ReadoutFactsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackStoreReadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/CompositionFactsCacheTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadOperationBudgetTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadFixture.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/OfferingReadServiceCacheWiringTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/archfixtures/StaticCounterFixture.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 - Meter in the kernel; units with their owners; no static counters in main

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Review Feedback

Check the `review_ref` field in the event log (`spec-kitty agent tasks status`) and address every item before claiming done.

## Objectives & Success Criteria

Deliver FR-005, FR-005a, FR-005b, FR-005c, FR-005d, FR-007a, SC-002 of `kitty-specs/service-test-reliability-01M8AAEG/spec.md` as described in `plan.md` and the dispositions in `research.md`.

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

### T001

Create kernel.metering: WorkMeter (ScopedValue binding, lazily filled map, measure, Counts.of returns 0 for an absent unit), marker WorkUnit, package-info @NullMarked; javadoc names no pack type and says counting is on the binding thread only

### T002

Kernel WorkMeterTest: unbound count is a no-op; nested measure counts apart; two unit enums counted apart; measure rethrows what the call throws

### T003

Units with their owners: ReadWork (packs.domain: RESOLUTION, RESOLUTION_VISIT), StoreWork (packs.infra.storage: WHOLE_READ, LOOKUP_PARSE, TREE_WALK, STORE_RUN), CacheWork (packs.application: LOAD, HIT); switch every count point; delete packs.domain.WorkMeter; correct InForceResolution's "pure" javadoc and FilesystemPackStore/FileSystemPackInspector constructor javadoc that says "the seam a test uses"

### T004

CompositionFactsCache: loads()/hits() become CacheWork counts; remove loads(), hits(), clear(), size(); EffectiveReadFixture builds a fresh cache for a cold start; rewrite EffectiveReadOperationBudgetTest, OfferingReadServiceCacheWiringTest, CompositionFactsCacheTest on the meter

### T005

Budget positive controls: one isPositive() per budgeted unit on the same Measured (LOOKUP_PARSE on its own in WholePackStoreTest; RESOLUTION_VISIT in the budget test); ReadCounters.Step becomes Runnable

### T006

ArchUnit rule over all main classes (ServiceClasses.main()): no static non-final field, no static Atomic*/LongAdder field; allow PackCommitStore.COUNTER and GlobalAddressResolverProvider by name; a bad fixture class under the test tree trips it

### T007

Red-first: for each changed test, a hand-made mutant (doubled count, dropped count point, extra walk, a static counter in a fixture) turns it red; revert; note each in research.md via WP05

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
