---
work_package_id: WP28
title: C · Contract C and the last two reads served
dependencies: [WP27, WP25]
requirement_refs:
- FR-018
- FR-019
- FR-020
- FR-021
- NFR-002
- SC-001
- SC-002
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts were generated on issue-10-layer-merge-effective-reads; execution worktrees come from lanes.json and completed changes merge back into issue-10-layer-merge-effective-reads. The four PRs (A0, A, B, C) are cut from it at closeout.
subtasks:
- T138
- T139
- T140
- T141
- T142
phase: PR C
assignee: ''
agent: claude
shell_pid: ''
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/schemas/
create_intent:
- contracts/charter/schemas/LayerOverride.yaml
- contracts/charter/changes/2026-11-07-c-graph-and-context.md
- contracts/charter/scenarios/effective-graph-and-context.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/EffectiveGraphSteps.java
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/schemas/EffectiveGraph.yaml
- contracts/charter/schemas/EffectiveGraphEdge.yaml
- contracts/charter/schemas/EffectiveGraphNode.yaml
- contracts/charter/schemas/LayerOverride.yaml
- contracts/charter/schemas/EffectiveActionContext.yaml
- contracts/charter/paths/activations_activationId_graph.yaml
- contracts/charter/paths/activations_activationId_missionTypes_missionType_actions_action_context.yaml
- contracts/charter/changes/2026-11-07-c-graph-and-context.md
- contracts/charter/scenarios/effective-graph-and-context.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/EffectiveGraphSteps.java
role: implementer
---
# WP28: C · Contract C and the last two reads served

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Serve `getEffectiveGraph` (with its `layerOverrides`) and `getEffectiveActionContext`, and retire the last reserved
operations (`ReservedOperationCoverageTest` 2 → 0; the interceptor stays for future reservations, with one
bad-fixture control).

Implementation command:
`spec-kitty agent action implement WP28 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR C (stacked on PR B).
- **Concern:** IC-11. **Requirements:** FR-018, FR-019, FR-020, FR-021, NFR-002, SC-001, SC-002.
- `EffectiveGraph` gains required `layerOverrides: [LayerOverride]` (overrides whose artifact is in force);
  `EffectiveActionContext.skipped` uses the extended `SkippedReport`; a mission type not in force answers an empty
  `artifacts` list and lists it in `missionTypeNotInForce`; `action_not_found` becomes raised.
- Scenarios go in a new `effective-graph-and-context.feature` (`effective-reads.feature` is WP22's): US5 AS1–AS5.

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


### Files shared with other work packages

These files are owned by another WP (the no-overlap rule) but this WP must make the small edit named. Make only that edit, and record a one-line rationale in the commit message (`out-of-map: <file> — <why>`):

- `service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java` — add the graph and action-context reads (owned by WP25)
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationController.java` — remove the last two `@ReservedOperation` marks (owned by WP25)
- `service/activations/src/main/java/ai/speckitty/charter/activations/application/PacksSession.java` — port methods for the projection and the walk (owned by WP24)
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSession.java` — map them (owned by WP24)
- `contracts/charter/schemas/CharterRefusalCode.yaml` — `action_not_found` raised (owned by WP11)
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ReservedOperationCoverageTest.java` — 2 → 0 with one bad-fixture control (owned by WP22)


## Project constraints (apply to every subtask)

- **TDD, contract first (C-001).** Write the failing test or `@contract` scenario first, watch it fail for the right
  reason, then write the code. A contract change lands with its `contracts/charter/changes/` fragment before the code
  that serves it.
- **No disabled tests, no new suppressions (C-006, NFR-001).** No `@Disabled`, no `@WIP` left on a scenario this WP
  makes green, no `@SuppressWarnings`/Checkstyle/PMD/SpotBugs suppression added, and the suppression register does not
  grow. Methods stay within CC ≤ 15, NPath ≤ 1000, ≤ 60 lines and nested-if depth ≤ 1.
- **Hexagonal slices, no shadow logic (C-002).** Merges and walks live in `packs`; `activations` reaches `packs` only
  through the `AvailablePacks` port and `activations.infra.packs`. `HexagonalArchitectureTest` must stay green.
- **No core changes (C-003).** Nothing in spec-kitty core is edited, and no PR or issue is opened in core.
- **git is a host binary (C-008).** Only the system `git` ≥ 2.50.1, never JGit, never a work-tree checkout.
- **Gradle.** Run module tasks, not the full suite (`NO_FULL_HEAVY_SUITES_IN_MISSION`):
  `./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none <module>:check`.
  On a Maven Central `429`, wait and re-run the same command; never turn dependency verification off.
- **Divergences (C-007).** Every behaviour that differs from core gets a divergence-register entry in the PR that
  ships it (the docs WP of that PR writes it; tell it what you changed in your hand-off note).
- **Commits** end with the session's attribution lines; no model id in commits or PR text.

## Branch Strategy

- Planning branch: `issue-10-layer-merge-effective-reads`; final merge target: `issue-10-layer-merge-effective-reads`.
- Execution worktrees are allocated per computed lane from `lanes.json`; do not pick a base branch by hand. Dependencies are a claim gate: this WP can be claimed only when its dependencies are approved or done.
- At closeout the mission branch is cut into the stacked PRs #399 → A0 → A → B → C; keep this WP's commits inside its PR letter (C).

## Subtasks

### T138: Schemas and paths

`LayerOverride`, `EffectiveGraph.layerOverrides`, `EffectiveActionContext.skipped`; `x-reserved` removed; changelog fragment.

### T139: Red scenarios

`effective-graph-and-context.feature` US5 AS1–AS5 with negative controls; confirm red.

### T140: Serve the graph

Service + controller + mapper for the graph, ETag/304.

### T141: Serve the action context

Service + controller + mapper, unknown-name refusals, mission type not in force.

### T142: Reservation coverage

`ReservedOperationCoverageTest` at 0 with its bad-fixture control; scenarios green.

## Test strategy

Contract lint, the new feature file, `ReservedOperationCoverageTest`, controller tests.

## Risks

- With no reserved operation left, the coverage test must still keep the machinery honest (the control).

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
