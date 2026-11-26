---
work_package_id: WP04
title: Documentation in line with the code
dependencies:
- WP01
- WP03
requirement_refs:
- FR-009
- SC-004
planning_base_branch: test/659-state-waits-and-main-hooks
merge_target_branch: test/659-state-waits-and-main-hooks
branch_strategy: Planning artifacts for this mission were generated on test/659-state-waits-and-main-hooks. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test/659-state-waits-and-main-hooks unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-service-test-reliability-01M8AAEG
base_commit: 8ccf6e697d1708c2269492546c716d34f0e23d23
created_at: '2026-11-26T06:20:08.506531+00:00'
subtasks:
- T020
- T021
- T022
- T023
phase: Phase 1 - Documentation in line with the code
history:
- at: '2026-11-25T22:20:00Z'
  actor: claude::java-jenny
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/adr/
create_intent: []
execution_mode: code_change
model: ''
owned_files:
- docs/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check.md
- docs/development/notes/pack-code-unification.md
- docs/plans/codebase-health-2026-11-25.md
- docs/changelog/2026-11-25-service-tests-wait-for-states.md
- contracts/charter/scenarios/README.md
- contracts/charter/scenarios/registration/whole-pack-registration.feature
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP04 - Documentation in line with the code

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Review Feedback

Check the `review_ref` field in the event log (`spec-kitty agent tasks status`) and address every item before claiming done.

## Objectives & Success Criteria

Deliver FR-009, SC-004 of `kitty-specs/service-test-reliability-01M8AAEG/spec.md` as described in `plan.md` and the dispositions in `research.md`.

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

### T020

ADR-0075: dated amendment (Proposed, operator 2026-11-25, #659): AA5 resolved by kernel.metering.WorkMeter, kept on purpose for the read budgets; history not rewritten

### T021

pack-code-unification.md AA5 bullet marked resolved with a pointer; health plan rows 8 and 9 marked delivered by #659

### T022

Scenarios README "The RegistrationJobs seam" and whole-pack-registration.feature description name the held storage room; step texts unchanged

### T023

Changelog fragment: related adds ADR-0075; wording names the kept seams instead of claiming "no test hooks"; check_docs.py all, markdownlint-cli2, cspell

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
