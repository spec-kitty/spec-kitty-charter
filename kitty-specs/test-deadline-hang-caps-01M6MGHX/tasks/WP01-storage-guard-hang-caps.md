---
work_package_id: WP01
title: Storage guards get 30 s hang caps
dependencies: []
requirement_refs:
- FR-001
- FR-003
- SC-001
- SC-002
planning_base_branch: test-deadline-hang-caps
merge_target_branch: test-deadline-hang-caps
branch_strategy: Planning artifacts for this mission were generated on test-deadline-hang-caps. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test-deadline-hang-caps unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-test-deadline-hang-caps-01M6MGHX
base_commit: 90d7cd3077a0ffedaf1fa30f2c273ff3e513fe4c
created_at: '2026-11-05T06:46:13.719690+00:00'
subtasks:
- T001
- T002
- T003
- T004
phase: Phase 1 - Hang caps
agent: claude
history:
- at: '2026-11-05T07:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ParsedYamlTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/EmbeddedSchemasTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/InterruptedCommitRepairTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP01 — Storage guards get 30 s hang caps

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Six wall-clock bounds in three `packs` storage test files become **30 s hang caps** (operator ruling, decision
`01M6MGJA27NG72JCTBXB1TWG80`). The tests stay in `:packs:test`, are never tagged `timing` (ADR-0057), and keep
asserting the cause they prove. A planted break per guard proves they still go red (FR-001, FR-003).

## Branch Strategy

Planning base and final merge target: `test-deadline-hang-caps`. The worktree is allocated per computed lane.

```bash
spec-kitty agent action implement WP01 --agent claude --mission test-deadline-hang-caps-01M6MGHX
```

## Context

| Site (main `85c20fa`) | Today | Proves | Cause assertion |
|---|---|---|---|
| `ParsedYamlTest:130` `anAliasBombIsAPackUnreadableForPackYamlWithinTheTimeout` | `assertTimeoutPreemptively(2 s)` | YAML node budget stops an alias bomb (3^16 paths) in `pack.yaml` | `Unreadable`, `TOO_LARGE`, `PACK_UNREADABLE` finding |
| `ParsedYamlTest:146`, `:153` `anAliasBombInAShardOrFragment…` | same, 2 s | same, for a shard and a fragment | `DRG_SCHEMA_ERROR` finding with `TOO_LARGE` |
| `EmbeddedSchemasTest:80` `aCatastrophicPatternIsEvaluatedInLinearTime` | `assertTimeoutPreemptively(5 s)` | the pattern engine runs `^(.*a){12}$` on 40 `a`s + `!` in linear time (ReDoS) | `containsExactly("pattern at /a")` |
| `InterruptedCommitRepairTest:166` | `succeedsWithin(10 s)` | a read waits for the swap, then serves the committed state | the committed tool guide is served |
| `InterruptedCommitRepairTest:200` | `assertTimeoutPreemptively(10 s)` | a listing inside another pack's lock skips a held lock instead of deadlocking | the listing's content |

Do not touch the `@Timeout(60)` caps or the spin-wait deadlines elsewhere; they are already generous. PR #377
edits `InterruptedCommitRepairTest` around lines 339 and 382; stay out of those hunks.

## Subtask T001 — Measure the work's own time

Run each of the five test methods (with `--tests`) on the current code and record each method's duration from
the JUnit XML (`service/packs/build/test-results/test/*.xml`, `testcase time=`). This goes into the PR as the
margin evidence (spec Assumptions).

## Subtask T002 — Widen to 30 s caps

1. In each file, introduce one `private static final Duration HANG_CAP = Duration.ofSeconds(30);` with a
   one-line comment: a cap far above the work's own time, so a loaded runner cannot trip it; the guard is
   proved by the cause assertion; not a budget (#320, ADR-0057).
2. Replace the six bounds with `HANG_CAP`. Keep every cause assertion unchanged. Rename a test whose name
   says "WithinTheTimeout" only if the name now misleads; prefer keeping names.
3. Keep `assertTimeoutPreemptively` where it guards against an engine that would never return (alias bomb,
   ReDoS, deadlock), because a non-preemptive timeout would hang the suite.

## Subtask T003 — Planted-break proof (FR-003)

For each guard, plant a break in **main code**, run only that test, record the failing output and the
elapsed time, revert, and confirm `git diff -- service/packs/src/main` is empty:

- **Alias budget**: raise the YAML loader's alias or code-point budget, or disable its alias limit, so the bomb
  expands. Expect the preemptive cap to fire, or the cause assertion to fail.
- **Linear-time patterns**: make `EmbeddedSchemas` use the backtracking `java.util.regex` engine for that
  pattern. Expect the cap to fire (exponential backtracking).
- **Commit lock**: make the listing wait on (`lock()`) instead of trying (`tryLock()`) a held pack lock. Expect
  the cap to fire (deadlock).
- **Swap wait**: make the reader not wait on the swap. Expect a wrong-state or not-found failure.

If a plant cannot be made in a few lines, say so and record the closest honest alternative. Note whether
a runaway thread kept the JVM busy after the cap fired.

## Subtask T004 — Gates

`./gradlew :packs:spotlessApply`, then `:packs:spotlessCheck :packs:checkstyleTest :packs:test
-PrequireSymlinks=true -PopenPacksDir=/home/user/spec-kitty-open-packs`. The `:packs:test` execution count must be
unchanged (record it), and no test may be tagged `timing`.

## Definition of Done

- [ ] 6 bounds are `HANG_CAP` (30 s); cause assertions unchanged; no `timing` tag; no main change.
- [ ] Work time per site recorded; planted break per guard red and recorded.
- [ ] `:packs:test` green with unchanged count; Spotless, Checkstyle clean.

## Reviewer guidance

Check that no cause assertion was weakened. Re-plant at least one break yourself. Confirm the PR #377 hunks
in `InterruptedCommitRepairTest` are untouched.
