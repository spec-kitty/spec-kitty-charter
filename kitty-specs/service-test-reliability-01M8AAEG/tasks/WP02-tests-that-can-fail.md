---
work_package_id: WP02
title: Registration and wait tests that can fail
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-002a
- FR-002b
- FR-002c
- FR-003
- FR-004
- FR-006
- FR-008
- NFR-001
- NFR-002
planning_base_branch: test/659-state-waits-and-main-hooks
merge_target_branch: test/659-state-waits-and-main-hooks
branch_strategy: Planning artifacts for this mission were generated on test/659-state-waits-and-main-hooks. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test/659-state-waits-and-main-hooks unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-service-test-reliability-01M8AAEG
base_commit: 3bbdf14eb5e82804611095146160c5518c7329b2
created_at: '2026-11-26T05:09:19.106661+00:00'
subtasks:
- T008
- T009
- T010
- T011
- T012
- T013
- T014
phase: Phase 1 - Registration and wait tests that can fail
history:
- at: '2026-11-25T22:20:00Z'
  actor: claude::java-jenny
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/application/
create_intent: []
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationJobs.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationJobsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationJobsUploadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/StreamedUploadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/SlowUploadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PackSourceRegistrationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/StorageRoomAdmissionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ChunkedArchiveBodyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/git/GitSourceFetcherTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ConsistentReadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RemovalConcurrencyTest.java
- service/packs/src/testFixtures/java/ai/speckitty/charter/testfixtures/Await.java
- service/packs/src/testFixtures/java/ai/speckitty/charter/packs/fixtures/HeldRoom.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/RegistrationWiringStagingTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/PackRegistrationSteps.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02 - Registration and wait tests that can fail

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Review Feedback

Check the `review_ref` field in the event log (`spec-kitty agent tasks status`) and address every item before claiming done.

## Objectives & Success Criteria

Deliver FR-001, FR-002, FR-002a, FR-002b, FR-002c, FR-003, FR-004, FR-006, FR-008, NFR-001, NFR-002 of `kitty-specs/service-test-reliability-01M8AAEG/spec.md` as described in `plan.md` and the dispositions in `research.md`.

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

### T008

RegistrationWiringStagingTest: stale entry at <tmpdir>/staging/<job id>, a directory whose mtime is set after writing to 2 h ago; a same-fixture positive control where a configured root sweeping that directory removes it; mutant: the no-root branch sweeps <tmpdir>/staging with a null-safe pack root

### T009

GitSourceFetcherTest: release only when inFlight == 2 and the other four fetch threads are TIMED_WAITING with GitSourceFetcher.acquireSlot on their stack; mutant: a slot limit of three

### T010

ConsistentReadTest: capture the ender thread's failure in an AtomicReference and rethrow after join; mutant: awaitIdle that never sees the end

### T011

RegistrationJobsTest.closingTheJobs...: hold the room again before close(); assert each waiting job ends refused busy, one audit entry each, nothing written, no staging; drop the arrival count; mutant: close() that lets a waiting job commit

### T012

FR-004: rename the deadline test for a queued job withdrawn; a new test holds the job monitor (synchronized on the reservation), frees one worker, waits until it is BLOCKED on that monitor (ThreadMXBean lock identity), ends the job (clock + expireOverdue), releases; the worker writes nothing, one audit entry; mutant: drop the markRunning phase check; record the ended() guard as an equivalent mutant

### T013

RegistrationJobs.retained() leaves main: the seven tests read what they check from statuses or owned(); RemovalConcurrencyTest wait loop through Await.within; inline the private await wrappers in RegistrationJobsTest and PackRegistrationSteps to Await.until; one Await-based helper for the reservation-ended predicate shared by RegistrationJobsTest and RegistrationJobsUploadTest

### T014

Run :packs:test, :activations:test, :deployable:test; every red attributed, no retry-to-green

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
