---
work_package_id: WP01
title: Example packs, content first
dependencies: []
requirement_refs:
- FR-008
- FR-018
- C-001
planning_base_branch: slice-f-537-kind-taxonomy
merge_target_branch: slice-f-537-kind-taxonomy
branch_strategy: Planning artifacts for this mission were generated on slice-f-537-kind-taxonomy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-f-537-kind-taxonomy unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
- T005
agent: claude
history:
- at: '2026-11-13T20:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/testFixtures/resources/example-packs/
create_intent:
- service/packs/src/testFixtures/resources/example-packs/built-in/guards/spec-kitty-pre-review.guard.yaml
execution_mode: code_change
owned_files:
- service/packs/src/testFixtures/resources/example-packs/built-in/*.graph.yaml
- service/packs/src/testFixtures/resources/example-packs/built-in/guards/**
- service/packs/src/testFixtures/resources/example-packs/built-in/lifecycles/**
- service/packs/src/testFixtures/resources/example-packs/built-in/missions/built_in_step_contracts/**
- service/packs/src/testFixtures/resources/example-packs/built-in/pack-manifest.yaml
- service/packs/src/testFixtures/resources/example-packs/org-base/**
- service/packs/src/testFixtures/resources/example-packs/project/**
role: implementer
---

# Work Package Prompt: WP01 - Example packs, content first

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/` and adopt it.

Then read these files in `kitty-specs/slice-f-kind-taxonomy-01M7B19K/`:

- `spec.md`: its Intent Summary and the requirements this work package maps to.
- `plan.md`: concern IC-00.
- `data-model.md`.
- `research.md`: R6, R14 and R15.
- `research/squad-post-plan.md`.

## Objective

Charter's three example packs already satisfy every rule slice F adds: the endpoint table, every step linked from its type by a `requires` edge, every delegation candidate held in the pack with a `scope` edge from its action, and the gate held as a lifecycle guard. Today's rules still accept all of it, so this lands green before any new rule.

## Context

- Research R6 is the endpoint table, and `research/squad-post-plan.md` (M-B2, M-7, M-8) gives the counts.
- The example packs live under `service/packs/src/testFixtures/resources/example-packs/` (`built-in`, `org-base`, `project`).
- The census script is `research/edge-census.py`. Run it against the example packs to confirm, before and after.
- No rule changes in this work package. It changes content only.

## Subtasks

### T001: Drop the impacts edges, and turn suggests styleguide→anti_pattern into rejects

- Remove the `impacts` edges in `org-base/drg/fragment.yaml` and `project/project.graph.yaml` (the census found 2).
- Retire also any per-artifact `impacts` value list? **No.** ADR-0028's per-value `impacts` list on an artifact is not the relation. Leave it alone.
- Change the `suggests` edge from a styleguide to an `anti_pattern` into `rejects` (G → anti_pattern is the `rejects` row).
- Check that no example test asserts on the removed edges: grep `ExamplePacksComposeTest`, `DomainRoundTripTest` and the scenarios for them. Update any assertion so that it pins the new edge.

### T002: Add the 8 action nodes and their requires edges

- In `built-in/missions/software-dev/software-dev.mission-type.yaml`, list the steps and the on-demand steps (13 in total).
- `built-in/mission_type.graph.yaml` and `action.graph.yaml` carry 5 `mission_type:software-dev -requires-> action:software-dev/<step>` edges.
- Add the missing action nodes and edges, in the existing shard order and format: discovery, analyze, accept, charter, tasks-finalize, tasks-outline, tasks-packages, retrospect.
- Check the org-base pack's `missions/` as well. Every mission type there needs the same: one edge per step.

### T003: The pre-review guard and its lifecycle entry (ADR-0054 decision 12)

- The example `review.step-contract.yaml` has a `gates[0]` binding (`handler_kind: mission_step_contract`). Read it.
- Add `built-in/guards/spec-kitty-pre-review.guard.yaml`, following `guard.schema.json`:
  - `implemented_by: builtin:spec-kitty-pre-review` (already in the guard schema's closed list);
  - `on_indeterminate: pass`, which replaces `fail_open`;
  - no `provenance`.
- Add the guard entry `{ref: spec-kitty-pre-review}` to `lifecycles/work-package.lifecycle.yaml`, on the `in_progress → for_review` transition. Keep the guards already there (subtasks-ready, traceability-ready).
- Add a guard node to the right shard if guards are graph nodes. They are not authored in graphs (`NodeKind.authoredInGraphs`), so they are probably not, but check how the other guards are held.
- Leave the step contract's `gates` untouched. WP08 deletes the file.

### T004: Delegation candidates and their scope edges

- For each of the 5 `built_in_step_contracts/*.step-contract.yaml`, list every `delegates_to.candidates` entry. The squad found 37 references, 31 of them unresolved (20 distinct ids such as DIRECTIVE_024/029/030/033/037, tdd-red-green-refactor, execution-lanes, git-flow and trunk-based).
- Repoint each unresolved candidate at an artifact of the same kind that the pack holds (for example DIRECTIVE_001/003/010, autonomous-operation-protocol, adversarial-squad-deployment, efficient-local-tooling, atomic-design), keeping the intent where you can.
- Add a small artifact only where no held artifact fits. Prefer repointing, so the example pack stays small.
- A delegated kind must be a `scope` target from `action`: a guidance kind or agent_profile. Change any other kind.
- Add `action:software-dev/<step> -scope-> <kind>:<id>` for every candidate, in `action.graph.yaml`, skipping the edges that already exist.

### T005: Manifest hashes and green tests

- Regenerate the content hashes in each edited pack's `pack-manifest.yaml`, and add the new guard file to `built-in/pack-manifest.yaml`. Use the repository's own tooling: grep `scripts/` and the Gradle tasks for the manifest restamp (#110). Never hand-compute a hash.
- Run `./gradlew :packs:test --max-workers=1` and `./gradlew :packs:wholePackRoundTrip --max-workers=1`, then `:deployable:test` for the example-pack scenarios.

## Validation

- `python3 kitty-specs/slice-f-kind-taxonomy-01M7B19K/research/edge-census.py` reports, for the example packs: 0 edges outside the R6 table, 0 steps without a `requires` edge, and 0 delegation candidates that are unresolved or have no `scope` edge.
- `:packs:test`, `:packs:wholePackRoundTrip` and `:deployable:test` are green.

## Definition of Done

- T001 to T005 are done, and the census is clean for the example packs.
- No rule or Java main source changed.
- Every commit is signed, carries no `Co-Authored-By` trailer and names no model.
- The tests this work package touches are green. Gradle runs in the foreground with `--max-workers=1`, one run at a time.
- Out-of-map edits are recorded with a one-line rationale in the commit message.

## Risks and reviewer guidance

- **Changing a scenario's meaning.** Some scenarios read example-pack edges, for example the effective context. Adding `scope` edges widens what is in force for a step's context (`ActionContextWalk`). Update those assertions on purpose, and say so in the commit message.
- **Hand-edited hashes.** Regenerate them.

## Branch Strategy

Planning happened on `slice-f-537-kind-taxonomy`, which is also the final merge target. `finalize-tasks` computes the
execution lanes into `lanes.json`, and each lane gets its own worktree. Do not choose a base branch by hand.

```bash
spec-kitty agent action implement WP01 --agent claude --mission slice-f-kind-taxonomy-01M7B19K
```

## Coordination

- **Slice C** is already on `main` (PR #566), and this branch is based on it.
- **#554 and #556 (epic #549).** Until #554 lands, the read check stays in `PackPolicy`, and whichever lands second rehomes it. Check the open PRs before each WP.
- **#551.** It changes the `kernel.validation` API; the second of the two to land rebases.
- **#106.** It renames `glossary_pack` to `glossary`; the second to land rebases.
- **#552.** It drops `MissionStepContractIdentifier` from its list.
- **Slice D close-out (#367, #371).** It runs in parallel, so rebase or merge `main` often. Never force-push a pushed branch.
