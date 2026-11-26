---
work_package_id: WP05
title: Gates and red-first evidence
dependencies:
- WP01
- WP02
- WP03
- WP04
requirement_refs:
- NFR-003
- NFR-004
- SC-001
- SC-003
planning_base_branch: test/659-state-waits-and-main-hooks
merge_target_branch: test/659-state-waits-and-main-hooks
branch_strategy: Planning artifacts for this mission were generated on test/659-state-waits-and-main-hooks. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test/659-state-waits-and-main-hooks unless the human explicitly redirects the landing branch.
subtasks:
- T024
- T025
- T026
phase: Phase 1 - Gates and red-first evidence
history:
- at: '2026-11-25T22:20:00Z'
  actor: claude::java-jenny
  action: Prompt generated via /spec-kitty.tasks
agent_profile: reviewer-renata
authoritative_surface: kitty-specs/service-test-reliability-01M8AAEG/
create_intent:
- kitty-specs/service-test-reliability-01M8AAEG/research.md
- kitty-specs/service-test-reliability-01M8AAEG/quickstart.md
execution_mode: planning_artifact
model: ''
owned_files:
- kitty-specs/service-test-reliability-01M8AAEG/research.md
- kitty-specs/service-test-reliability-01M8AAEG/quickstart.md
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP05 - Gates and red-first evidence

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `reviewer-renata`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Review Feedback

Check the `review_ref` field in the event log (`spec-kitty agent tasks status`) and address every item before claiming done.

## Objectives & Success Criteria

Deliver NFR-003, NFR-004, SC-001, SC-003 of `kitty-specs/service-test-reliability-01M8AAEG/spec.md` as described in `plan.md` and the dispositions in `research.md`.

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

### T024

Mutation notes in research.md: one row per changed test (mutant, red seen, reverted), including the first-pass retrofit commits

### T025

git fetch --unshallow --tags origin; unset _JAVA_OPTIONS only; ./gradlew verifyAll on Java 25; attribute every red; record :packs:test wall time against main (NFR-003)

### T026

quickstart.md: how to run the changed suites and reproduce one mutant; pre-merge squad dispositions appended to research.md

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
