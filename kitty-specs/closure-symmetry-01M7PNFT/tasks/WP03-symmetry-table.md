---
work_package_id: WP03
title: The symmetry table
dependencies:
- WP01
- WP02
requirement_refs:
- FR-006
- SC-001
- SC-002
planning_base_branch: feat/355-closure-in-pack-ruleset
merge_target_branch: feat/355-closure-in-pack-ruleset
branch_strategy: Planning artifacts for this mission were generated on feat/355-closure-in-pack-ruleset. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/355-closure-in-pack-ruleset unless the human explicitly redirects the landing branch.
subtasks:
- T013
- T014
- T015
- T016
- T017
history:
- at: '2026-11-18T07:00:00Z'
  actor: java-jenny
  action: Prompt generated from plan.md and research.md
agent_profile: java-jenny
authoritative_surface: service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/ClosureSymmetryTest.java
create_intent:
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/ClosureSymmetryTest.java
execution_mode: code_change
owned_files:
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/ClosureSymmetryTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GuardAndLifecycleLoadTest.java
role: implementer
tags: []
tracker_refs:
- '#355'
---
# Work Package Prompt: WP03 - The symmetry table

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else. Then read, in
`kitty-specs/closure-symmetry-01M7PNFT/`: `spec.md`, `plan.md` (the Implementation Concern Map), `research.md` (D1 to D6) and `data-model.md`.

## Branch Strategy

Planning base: `feat/355-closure-in-pack-ruleset`. Final merge target: `feat/355-closure-in-pack-ruleset`, then `main`
by the pull request. The execution worktree is allocated per computed lane from `lanes.json`.

```bash
spec-kitty agent action implement WP03 --agent claude --mission closure-symmetry-01M7PNFT
```

## Objective

One table-driven test, `ClosureSymmetryTest` (in `:pack-validation`, which reaches `PackCheck`, the packs store and the
`:packs` test fixtures), asserts for every row: validate mode reports an error at the file with the load's reason; the
load drops the artifact with the same file and reason; the whole-pack write refuses; the per-file write that would
produce it is refused with `registration_conflict`. Every row pairs with a repaired control that passes all four.

## Subtasks

### T013 The table's harness

A row: name, a tree edit of the `guard-lifecycle` fixture (or a mission-type fixture), the expected `(file, reason)`
list, and the domain-level mutation the two write paths apply. The four path checks are helpers; the control is the
fixture unedited (or the edit reverted).

### T014 Closure rows

Asset entry not a payload; guard asset missing; guard asset with no executable; guard `config_schema` invalid; a
lifecycle naming a missing guard; `config` not fitting; `config` for a guard with no schema; a cross-pack asset; a
cross-pack guard entry; the cascade; a mission type's output template not its own.

### T015 Invariant rows

Args repeating entry; an unknown state; no terminal state; an unreachable state; a transition out of a terminal state;
two targets for one state and event. Validate mode and the load report them; on the two write paths the domain value
cannot be built (`ConstraintBreachException`).

### T016 The delete row

Removing `asset:carrier-lint`: the load and validate mode over the tree without the sidecar and payload report the
guard; the whole-pack write of `pack.without(asset)` refuses; the per-file `remove` refuses with
`registration_conflict`.

### T017 Fold the per-case tests

Remove from `GuardAndLifecycleLoadTest` the refusal cases the table now covers (keep the load-shape and round-trip
cases). `FileSystemPackInspectorTest`'s symmetry cases cover other rules and stay.

## Definition of Done

- The table runs every row on all four paths, and every control is green on all four.
- A row whose path stops judging fails (check by commenting out one judge locally; do not commit that).
