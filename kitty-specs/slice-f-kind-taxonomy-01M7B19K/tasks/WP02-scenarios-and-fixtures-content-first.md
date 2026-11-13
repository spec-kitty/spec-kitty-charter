---
work_package_id: WP02
title: Scenarios and fixtures, content first
dependencies: []
requirement_refs:
- FR-018
- C-001
planning_base_branch: slice-f-537-kind-taxonomy
merge_target_branch: slice-f-537-kind-taxonomy
branch_strategy: Planning artifacts for this mission were generated on slice-f-537-kind-taxonomy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-f-537-kind-taxonomy unless the human explicitly redirects the landing branch.
subtasks:
- T006
- T007
- T008
- T009
- T010
agent: claude
history:
- at: '2026-11-13T20:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/scenarios/
create_intent: []
execution_mode: code_change
owned_files:
- contracts/charter/scenarios/activation-*.feature
- contracts/charter/scenarios/composition.feature
- contracts/charter/scenarios/effective-reads.feature
- contracts/charter/scenarios/effective-graph-and-context.feature
- contracts/charter/scenarios/effective-context-scoping.feature
- contracts/charter/scenarios/effective-overriders.feature
- contracts/charter/scenarios/effective-references.feature
- contracts/charter/scenarios/effective-step-prompt.feature
- contracts/charter/scenarios/layer-merge.feature
- contracts/charter/scenarios/pack-removal.feature
- contracts/charter/scenarios/step-enhancement*.feature
- contracts/charter/scenarios/prompt-slots.feature
- contracts/charter/scenarios/relationships.feature
- service/packs/src/test/resources/load-fixtures/**
- service/packs/src/test/resources/whole-pack/**
- service/packs/src/test/resources/pack-level/**
- service/packs/src/test/resources/pack-order/**
role: implementer
---

# Work Package Prompt: WP02 - Scenarios and fixtures, content first

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/` and adopt it.

Then read these files in `kitty-specs/slice-f-kind-taxonomy-01M7B19K/`:

- `spec.md`: its Intent Summary and the requirements this work package maps to.
- `plan.md`: concern IC-00.
- `data-model.md`.
- `research.md`: R6, R14 and R15.
- `research/squad-post-plan.md`.

## Objective

Every valid-path pack written inline in a `.feature` file, and every valid-path test fixture, satisfies the endpoint table and the step-edge rule while today's rules still hold. Error fixtures are left for the WP that owns their refusal.

## Context

- `research/squad-post-plan.md` M-B1 and T-B3 list about 34 refused edges:
  - `enhances tactic→directive` ×17;
  - `requires action→directive` ×7;
  - `overrides action→directive` ×4;
  - singletons, such as `requires template→tactic`, `instantiates directive→template`, `requires directive→agent_profile` and `requires mission_type→directive`.
- About 13 `.feature` files author mission types inline without a `requires` edge.
- Leave `override-source-kind.feature` and `prompt-fragment-refusals.feature` to WP05, which owns their refusals. Leave the `mission-types.feature` and `artifacts.feature` step-contract parts to WP07 and WP08.

## Subtasks

### T006: Census over scenarios and fixtures

- Write the census as a script under `scripts/` (for example `scripts/packs/endpoint_census.py`, as an out-of-map new file), reusing the logic of the mission's `research/edge-census.py` to read the inline YAML of `.feature` docstrings, plus the test resource trees under `service/*/src/test/resources/**` and the testFixtures.
- Report:
  - each edge the R6 table refuses, with its file and line;
  - each mission type step with no `requires` edge.
- Mark error fixtures, the ones whose `expected.json` or scenario expects a refusal, separately from valid-path ones.

### T007: Rewrite the disallowed inline edges

For each valid-path edge the table refuses, pick the allowed form that keeps what the scenario tests:

- `enhances X→Y` across kinds becomes a same-kind `enhances`. It is usually about layering, so make both ends the same kind and keep the layer.
- `requires action→directive` becomes `scope action→directive`. The step context reads `SCOPE`, so check that the effective-context assertions still hold.
- `overrides action→directive` becomes an `overrides` edge between two directives at different levels.
- For a singleton, choose the nearest allowed relation and note it in the commit.

Adjust each scenario's expectations only where the rewrite changes them, and say why.

### T008: Requires edges for the inline mission types

Every inline mission type gets `mission_type:<t> -requires-> action:<t>/<s>` for each step and on-demand step, plus the action node where the shard declares nodes.

### T009: Java test fixtures

Apply T007 and T008 to the valid-path fixtures under `service/packs/src/test/resources/{load-fixtures,whole-pack,pack-level,pack-order}` and the other test resource trees the census names. An error fixture keeps its one fault, unless the census shows it gaining a second; then rewrite it so it carries exactly one.

### T010: Evidence

Re-run the census, and record the before and after counts in the commit message. WP11 copies them into `research/closeout-evidence.md`.

## Validation

- The census reports 0 valid-path edges refused and 0 unlinked steps.
- `:deployable:test` and `:packs:test` are green.

## Definition of Done

- T006 to T010 are done, and the census counts are in the commit message.
- No scenario loses its subject.
- Every commit is signed, carries no `Co-Authored-By` trailer and names no model.
- The tests this work package touches are green. Gradle runs in the foreground with `--max-workers=1`, one run at a time.
- Out-of-map edits are recorded with a one-line rationale in the commit message.

## Risks and reviewer guidance

- A scenario's meaning can drift. The reviewer checks each rewritten edge against the scenario's title.
- Do not edit error fixtures that WP04 to WP08 own (impacts, step contracts, prompt fragments).

## Branch Strategy

Planning happened on `slice-f-537-kind-taxonomy`, which is also the final merge target. `finalize-tasks` computes the
execution lanes into `lanes.json`, and each lane gets its own worktree. Do not choose a base branch by hand.

```bash
spec-kitty agent action implement WP02 --agent claude --mission slice-f-kind-taxonomy-01M7B19K
```

## Coordination

- **Slice C** is already on `main` (PR #566), and this branch is based on it.
- **#554 and #556 (epic #549).** Until #554 lands, the read check stays in `PackPolicy`, and whichever lands second rehomes it. Check the open PRs before each WP.
- **#551.** It changes the `kernel.validation` API; the second of the two to land rebases.
- **#106.** It renames `glossary_pack` to `glossary`; the second to land rebases.
- **#552.** It drops `MissionStepContractIdentifier` from its list.
- **Slice D close-out (#367, #371).** It runs in parallel, so rebase or merge `main` often. Never force-push a pushed branch.
