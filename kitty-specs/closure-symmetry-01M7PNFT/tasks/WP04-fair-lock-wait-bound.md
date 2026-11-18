---
work_package_id: WP04
title: '#593: a per-write wait bound in the fair-lock case'
dependencies: []
requirement_refs:
- FR-008
- NFR-002
planning_base_branch: feat/355-closure-in-pack-ruleset
merge_target_branch: feat/355-closure-in-pack-ruleset
branch_strategy: Planning artifacts for this mission were generated on feat/355-closure-in-pack-ruleset. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/355-closure-in-pack-ruleset unless the human explicitly redirects the landing branch.
subtasks:
- T018
- T019
- T020
- T021
history:
- at: '2026-11-18T07:00:00Z'
  actor: java-jenny
  action: Prompt generated from plan.md and research.md
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/application/MixedReadConcurrencyTest.java
create_intent: []
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/application/MixedReadConcurrencyTest.java
role: implementer
tags: []
tracker_refs:
- '#355'
- '#593'
---
# Work Package Prompt: WP04 - #593, the per-write wait bound

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else. Then read, in
`kitty-specs/closure-symmetry-01M7PNFT/`: `spec.md`, `plan.md` (the Implementation Concern Map), `research.md` (D1 to D6) and `data-model.md`.

## Branch Strategy

Planning base: `feat/355-closure-in-pack-ruleset`. Final merge target: `feat/355-closure-in-pack-ruleset`, then `main`
by the pull request. The execution worktree is allocated per computed lane from `lanes.json`.

```bash
spec-kitty agent action implement WP04 --agent claude --mission closure-symmetry-01M7PNFT
```

## Objective

`sixteenPutsAndOneReplaceAllCompleteUnderTheFairLock` proves no write starves under the fair lock. Today one 30 s
timeout over 100 rounds cannot tell a slow runner from starvation. Bound each write's wait per round instead.

## Subtasks

### T018 Measure each write

Each writer records the nanoseconds from the barrier's release to the end of its call, keyed by its name (`P0`..`P15`,
`R`), per round.

### T019 Fail naming the write and the round

After each round, the slowest write is compared to the bound (10 s). A breach fails with
`write P7 waited 12.3 s in round 63 (bound 10 s)`. A round's futures are awaited with a deadline of the bound plus a
margin, so a stuck write fails the round, naming the writes still running.

### T020 The outer timeout is a hang guard

The method's `@Timeout` becomes 120 s, with a comment that it catches a hang only. The other three cases keep their
shape unless they share the helper.

### T021 Evidence

Run the case 10 times locally (`--rerun`); record the slowest per-write wait seen in the PR body. Prove the message
with a test of the bound helper fed a synthetic slow write (no sleep in production code).

## Definition of Done

- 10 consecutive local runs green; the message test green; nothing else in the class weakened.
