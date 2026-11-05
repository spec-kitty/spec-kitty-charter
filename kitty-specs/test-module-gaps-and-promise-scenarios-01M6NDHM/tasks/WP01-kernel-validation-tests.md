---
work_package_id: WP01
title: kernel.validation tested in kernel
dependencies: []
requirement_refs: [FR-001, FR-004, SC-001, SC-002]
planning_base_branch: test-module-gaps-and-promise-scenarios
merge_target_branch: test-module-gaps-and-promise-scenarios
branch_strategy: Planning artifacts for this mission were generated on test-module-gaps-and-promise-scenarios. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test-module-gaps-and-promise-scenarios unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-test-module-gaps-and-promise-scenarios-01M6NDHM
base_commit: c2b344a0d9fe09e5ddd9e9f541fea6753e7c0ad5
created_at: '2026-11-05T08:55:01.341707+00:00'
subtasks: [T001, T002, T003, T004]
phase: Phase 1 - Module gaps
agent: claude
history:
- at: '2026-11-05T09:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/src/test/java/ai/speckitty/charter/kernel/validation/
create_intent:
- service/kernel/src/test/java/ai/speckitty/charter/kernel/validation/ConstrainedTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/validation/RationaleTest.java
execution_mode: code_change
owned_files:
- service/kernel/src/test/java/ai/speckitty/charter/kernel/validation/**
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP01 — kernel.validation tested in kernel

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

`kernel.validation` is the fail-fast invariant code every module leans on. Its own tests reach little of it
(58.3 % branch coverage). Give the uncovered behaviour direct tests, each proving a real invariant and each
shown red by a planted break (FR-001, FR-004). Tests only (C-002), with no coverage threshold (C-003).

## Branch Strategy

Planning base and merge target: `test-module-gaps-and-promise-scenarios`; worktree per computed lane.

```bash
spec-kitty agent action implement WP01 --agent claude --mission test-module-gaps-and-promise-scenarios-01M6NDHM
```

## Context: PIT inventory on main (`4b52bcb`)

- **No coverage:**
  - `Constrained`: `adheresTo`, `ifValid` ×2, `isValid` ×2, `rationale`
  - `Checks`: `haveNonBlank`, `haveNonNullField`, `haveNullField` and their lambdas (10 mutants)
  - `ModularRuleset`: `emptyRules`, `must`
- **Survived:** `Constraints.isNotBlank`, `Constraints.of`, `Rationale.isFailing` (#239 named this one a real
  one-assertion gap: the passing case).
- **Existing test class:** `ModularRulesetTest`. Read it first and strengthen it before adding classes (C-001,
  `DIRECTIVE_041`).

## Subtask T001 — Read the API and find the realistic entry points

Read every class in `service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/`. Find how other
modules use it (`grep -rn 'Constrained\|Rationale\|ModularRuleset\|Checks\.' service/*/src/main`) and copy that
style of use. Prefer values from the domain (a `PackIdentifier`, a `Urn`) over synthetic strings.

## Subtask T002 — Tests of the invariants

For each behaviour, write a test whose name states the invariant, with a positive control on the same fixture:

- `Constrained.isValid` / `ifValid` / `adheresTo` / `rationale`: a valid value passes and its action runs; an
  invalid one fails, its action does not run, and its rationale names the breached constraint.
- `Rationale.isFailing`: true for a failing rationale, false for a passing one. The survivor is the passing case.
- `Checks.haveNonBlank`, `haveNonNullField`, `haveNullField`: each refuses the breaking value and accepts the
  good one, with the reason text.
- `ModularRuleset.emptyRules` / `must`: an empty ruleset passes everything; `must` adds a rule that is enforced.
- `Constraints.isNotBlank` / `of`: the survived mutants show a missing assertion. Add it to the test that already
  runs that code, or to the new one.

## Subtask T003 — Planted breaks and PIT

1. For each test, plant a break in main code (invert a condition, return the wrong constant), run the test, record
   the failure, revert. Finish with `git diff -- service/kernel/src/main` empty.
2. Run `./gradlew :kernel:pitest` and record the kernel.validation counts (no coverage, survived) before and after.
   Triage any mutant left in kernel.validation, per #239's rule: kill it by strengthening an existing assertion,
   mark it equivalent with a reason, or accept it with a reason. Never add a test only to kill a mutant.

## Subtask T004 — Gates

`./gradlew :kernel:spotlessApply`, then `:kernel:spotlessCheck :kernel:checkstyleTest :kernel:test`. Record the
added test time (NFR-002: ≤ 5 s).

## Definition of Done

- [ ] Direct tests for `Constrained`, `Rationale`, the three `Checks` combinators, `ModularRuleset`, `Constraints`.
- [ ] A planted break per test, recorded; main diff empty.
- [ ] kernel.validation's PIT no-coverage count falls; leftovers triaged.
- [ ] Gates green; no suppressions.

## Reviewer guidance

Check every test against `DIRECTIVE_041`: would it fail on a real regression? Re-plant one break yourself.
