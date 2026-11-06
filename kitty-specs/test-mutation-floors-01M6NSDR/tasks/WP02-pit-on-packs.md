---
work_package_id: WP02
title: PIT on packs, measured and floored
dependencies: [WP01]
requirement_refs: [FR-002, FR-003, SC-001]
planning_base_branch: test-mutation-floors
merge_target_branch: test-mutation-floors
branch_strategy: Planning artifacts for this mission were generated on test-mutation-floors. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test-mutation-floors unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-test-mutation-floors-01M6NSDR
base_commit: 67e1573d6776c6f52b218e8c6f6bc7e040da8419
created_at: '2026-11-05T12:47:30.405823+00:00'
subtasks: [T006, T007, T008, T009]
phase: Phase 2 - Packs
agent: claude
history:
- at: '2026-11-05T12:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/
execution_mode: code_change
owned_files:
- service/packs/build.gradle.kts
- service/packs/pit-targets.init.gradle
- gradle/verification-metadata.xml
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP02 — PIT on packs, measured and floored

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Mutate `packs.domain.*`, `packs.application.*` (excluding `application.config.*`) and `packs.infra.storage.*`
with their unit tests, in one `:packs:pitest` run with two floor groups, `domain+application` and
`infra.storage` (FR-002, FR-003; operator ruling of 2026-11-05). **The packs floors are written only after the
operator rules them (C-001):** measure, report, stop.

## Branch Strategy

Planning base and merge target `test-mutation-floors`; depends on WP01, so the lane starts from WP01's work.

```bash
spec-kitty agent action implement WP02 --agent claude --mission test-mutation-floors-01M6NSDR
```

## Environment

`export JAVA_HOME=/usr/lib/jvm/temurin-25 SPEC_KITTY_NO_NAG=1 SPEC_KITTY_NO_UPGRADE_CHECK=1`; `./gradlew` only; run
`./gradlew syncPacks -PopenPacksDir=/home/user/spec-kitty-open-packs` once per worktree and pass
`-PopenPacksDir=/home/user/spec-kitty-open-packs` to every run. Never `./gradlew --stop`. Never a model-bearing identity.

## Subtasks

- **T006 Apply and target.**
  - `service/packs/build.gradle.kts` applies `charter.mutation-conventions`.
  - Set `targetClasses` to the three package groups and `excludedClasses` to `application.config.*` and
    `infra.api.*`.
  - `targetTests` are the unit tests of those packages: exclude Spring-wired tests (`*WiredTest`, any
    `@SpringBootTest`) and the tagged suites `open-packs`, `whole-pack` and `timing` (PIT's
    `excludedGroups` or JUnit tag config).
  - Declare the two `mutationFloors` groups with a placeholder floor of 0 until T009.
  - Reconcile `pit-targets.init.gradle` so its `-Dpit.classes`/`-Dpit.tests` narrowing still works, and
    update its header comment.
- **T007 Dependency verification.**
  - Run `./gradlew --write-verification-metadata sha256 :packs:pitest` once (additive), plus `help`, so every
    artifact the packs PIT classpath resolves is recorded.
  - Review the diff: list every added component. Expect the pitest plugin's poms and the Spring-BOM-resolved
    junit-platform modules (#239). Never hand-edit.
  - Then a strict `./gradlew :packs:pitest` must verify.
  - Note: #377 regenerates this file from scratch. Whichever lands second regenerates additively; record which
    base you used.
- **T008 Measure.**
  - Run `./gradlew :packs:pitest` (from a cold run, `--rerun-tasks`, with packs synced) and record the
    wall time, plus per group: mutants, killed, survived, no coverage, score, test strength, and the top surviving
    classes.
  - If the run exceeds about 40 min on this machine, stop and report.
  - Compute `floor5(score − 3)` per group.
  - **STOP here.** Commit T006 and T007, mark T006–T008 done, and report the numbers back to the orchestrator.
    Do not move to for_review. The orchestrator gets the operator's ruling and sends it back to you.
- **T009 Write the ruled floors** (after the ruling).
  - Set the two packs floors as ruled.
  - Prove: raise one temporarily above its score and `:packs:pitest` fails, naming `infra.storage` or
    `domain+application`; revert.
  - Commit, then move WP02 to for_review as `claude::java-jenny`.

## Definition of Done

- [ ] `:packs:pitest` mutates only the three groups with unit tests, verified strictly.
- [ ] The numbers are reported, the floors are written as ruled, and the forced-floor proof is recorded.
- [ ] Commits end with the two required lines.
