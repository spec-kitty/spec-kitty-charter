---
work_package_id: WP05
title: Kernel survivors triaged
dependencies: []
requirement_refs: [FR-006]
planning_base_branch: test-mutation-floors
merge_target_branch: test-mutation-floors
branch_strategy: Planning artifacts for this mission were generated on test-mutation-floors. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test-mutation-floors unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-test-mutation-floors-01M6NSDR
base_commit: 588f3e07135f54fef7abd635c847550de88a9b05
created_at: '2026-11-05T12:10:50.878001+00:00'
subtasks: [T017, T018, T019]
phase: Phase 1 - Triage
agent: claude
history:
- at: '2026-11-05T12:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/
execution_mode: code_change
owned_files:
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/**
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP05 — Kernel survivors triaged

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Every surviving kernel mutant gets one triage verdict, per #239 item 4 (FR-006, C-002):
- (a) killed by strengthening an **existing** test's assertion;
- (b) equivalent, with a one-line reason;
- (c) accepted, naming an owner.

A new test whose only purpose is killing a mutant is not allowed. kernel.validation is handled by #384, which is
not on this branch; this WP covers `kernel.domain`.

## Branch Strategy

Planning base and merge target `test-mutation-floors`; independent lane.

```bash
spec-kitty agent action implement WP05 --agent claude --mission test-mutation-floors-01M6NSDR
```

## Environment

`export JAVA_HOME=/usr/lib/jvm/temurin-25 SPEC_KITTY_NO_NAG=1 SPEC_KITTY_NO_UPGRADE_CHECK=1`; `./gradlew` only; run
`./gradlew syncPacks -PopenPacksDir=/home/user/spec-kitty-open-packs` once per worktree and pass
`-PopenPacksDir=/home/user/spec-kitty-open-packs` to every run. Never `./gradlew --stop`. Never a model-bearing identity.

## Subtasks

- **T017 Inventory.** `./gradlew :kernel:pitest --rerun`; list every SURVIVED mutant in `kernel.domain` (class,
  method, line, mutator, description). Expect about 25, mostly `Pep440Range`, plus `Pep440`, `Urn`,
  `PackQualifiedReference` and `DoctrineGraph.hashCode`. #239 names about 7 real one-assertion gaps:
  `Relationship.equals` reversed pair, `Urn.parse` leading `:`, `Pep440` empty trailing clause,
  `Relation.fromToken` unknown token, and the `Constraints` factories.
- **T018 Triage.** For each survivor, pick (a), (b) or (c).
  - For (a), find the existing test that already exercises the code and add the missing assertion there.
  - #368 refactors `Pep440Range.candidates` in main code. Change only tests; prefer assertions on public
    behaviour, so they survive that refactor.
  - Record a table: mutant → verdict → test or reason or owner.
  - If a mutant reveals a real defect in main code, stop and report it.
- **T019 Proof.**
  - Re-run `:kernel:pitest` and record survivors and score before and after.
  - Plant a break matching two of the killed mutants and confirm the strengthened tests go red; revert.
  - `:kernel:spotlessCheck :kernel:checkstyleTest :kernel:test` green.
  - One commit with the two required lines.

## Definition of Done

- [ ] Every kernel.domain survivor has a verdict; no new test exists only for a mutant; the before and after
  numbers are recorded.
