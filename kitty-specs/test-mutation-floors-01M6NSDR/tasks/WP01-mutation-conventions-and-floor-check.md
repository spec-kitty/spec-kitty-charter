---
work_package_id: WP01
title: Mutation convention plugin and floor check
dependencies: []
requirement_refs: [FR-001, FR-003, NFR-001, SC-001, SC-003]
planning_base_branch: test-mutation-floors
merge_target_branch: test-mutation-floors
branch_strategy: Planning artifacts for this mission were generated on test-mutation-floors. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test-mutation-floors unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-test-mutation-floors-01M6NSDR
base_commit: 3ea18b6a03b2e1270ec006273213bfcdbec1975b
created_at: '2026-11-05T12:10:30.421534+00:00'
subtasks: [T001, T002, T003, T004, T005]
phase: Phase 1 - Foundation
agent: claude
history:
- at: '2026-11-05T12:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: build-logic/
create_intent:
- build-logic/src/main/kotlin/charter.mutation-conventions.gradle.kts
- build-logic/src/main/kotlin/MutationFloors.kt
- build-logic/src/test/kotlin/MutationFloorsTest.kt
execution_mode: code_change
owned_files:
- build-logic/src/main/kotlin/charter.mutation-conventions.gradle.kts
- build-logic/src/main/kotlin/MutationFloors.kt
- build-logic/src/test/kotlin/MutationFloorsTest.kt
- build-logic/src/main/kotlin/charter.domain-conventions.gradle.kts
- service/kernel/build.gradle.kts
- service/missions/build.gradle.kts
- service/profiles/build.gradle.kts
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP01 — Mutation convention plugin and floor check

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Implement #239 design items 1 and 3 for the existing PIT modules (FR-001, FR-003):
- one `charter.mutation-conventions` plugin;
- a lenient, tested floor check that fails a module's mutation run below its floor, naming the module (and the
  group), the score and the floor;
- the ruled floors: kernel 65, missions 70, profiles 50.

Read `plan.md` "Design", issue #239 and `research.md`.

## Branch Strategy

Planning base and merge target `test-mutation-floors`; worktree per computed lane.

```bash
spec-kitty agent action implement WP01 --agent claude --mission test-mutation-floors-01M6NSDR
```

## Environment

`export JAVA_HOME=/usr/lib/jvm/temurin-25 SPEC_KITTY_NO_NAG=1 SPEC_KITTY_NO_UPGRADE_CHECK=1`; `./gradlew` only; run
`./gradlew syncPacks -PopenPacksDir=/home/user/spec-kitty-open-packs` once per worktree and pass
`-PopenPacksDir=/home/user/spec-kitty-open-packs` to every run. Never `./gradlew --stop`. Never a model-bearing identity.

## Subtasks

- **T001 Convention plugin.** Create `charter.mutation-conventions.gradle.kts`:
  - apply `info.solidsoft.pitest` and move PIT's shared settings into it from `charter.domain-conventions`
    (`pitestVersion`, `junit5PluginVersion` from the catalog, `outputFormats = HTML, XML`,
    `timestampedReports = false`, and `threads` sized for CI);
  - keep `targetClasses` defaulting to `ai.speckitty.charter.*` as today;
  - `charter.domain-conventions` then applies the new plugin instead of configuring PIT itself;
  - keep the Mockito agent hook in `charter.hand-written` working (it uses `withPlugin`): verify a kernel PIT
    run still gets the agent.
- **T002 Floor model and check (`MutationFloors.kt`).**
  - Add a `mutationFloors` extension: named groups, each with package prefixes (and optional excludes) and an
    integer floor (percent).
  - Register `checkMutationFloors`: it reads `build/reports/pitest/mutations.xml` with a lenient line or regex
    scan (never a strict XML parser: raw `<` appears in mutator descriptions).
  - Per group, compute mutants, killed (KILLED + TIMED_OUT + MEMORY_ERROR + RUN_ERROR if PIT counts it so; follow
    PIT's own score definition and document it), survived, no coverage, score and test strength.
  - Write `build/reports/pitest/floors.json` with one entry per group, plus the top 5 classes by survived and
    uncovered mutants.
  - Fail with `"<project path> <group>: mutation score S % is below its floor F % (#239, ADR)"` when any group is
    under its floor.
  - `pitest` is `finalizedBy` the check, so `./gradlew pitest --continue` reports every module.
  - The task must be configuration-cache compatible: declared inputs and outputs, no project access at execution
    time.
- **T003 Tests (`MutationFloorsTest.kt`, plain JUnit in build-logic).** Cover:
  - a group under, at and over its floor, using small `mutations.xml` fixtures with a raw `<` in a description;
  - package-prefix grouping with an exclude;
  - an empty report;
  - the exact failure message.

  Check how build-logic tests run today (`build-logic/src/test/kotlin`, the `-p build-logic check` and
  `verifyAll` wiring).
- **T004 Module floors.** In `service/{kernel,missions,profiles}/build.gradle.kts`, declare one group per module,
  covering the module's packages: kernel 65, missions 70, profiles 50.
- **T005 Proof.**
  - `./gradlew pitest --continue` on main's tests: every module passes and writes `floors.json`.
  - Raise kernel's floor to 99 temporarily: `:kernel:pitest` (via its finalizer) fails with the message, while
    missions and profiles still run. Revert.
  - `./gradlew verifyAll --dry-run | grep -i -E 'pitest|MutationFloors'` is empty (NFR-001).
  - `./gradlew -p build-logic check` and `:kernel:check` are green.

## Definition of Done

- [ ] The plugin is applied by domain-conventions; the floor check is tested; the floors are set.
- [ ] Forced-floor proof red then green; `--continue` keeps going; no pitest in `verifyAll`.
- [ ] One commit, its body ending with exactly these two lines:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
  `Claude-Session: https://claude.ai/code/session_019C2Tk5hqubpy7Fyur7YD8o`
