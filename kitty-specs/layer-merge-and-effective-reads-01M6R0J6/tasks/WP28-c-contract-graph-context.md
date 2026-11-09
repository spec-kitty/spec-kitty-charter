---
work_package_id: WP28
title: C · Contract C and the last two reads served
dependencies:
- WP27
- WP25
requirement_refs:
- FR-018
- FR-019
- FR-020
- FR-021
- NFR-002
- SC-001
- SC-002
- FR-017
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T138
- T139
- T140
- T141
- T142
- T162
- T163
- T164
phase: PR C
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
- contracts/charter/schemas/NodeReference.yaml
- contracts/charter/changes/2026-11-09-c-node-reference-step-targets.md
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSessionStepRefsTest.java
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
- contracts/charter/schemas/NodeReference.yaml
- contracts/charter/changes/2026-11-09-c-node-reference-step-targets.md
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSessionStepRefsTest.java
role: implementer
tags: []
tracker_refs: []
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

## Scope added by operator rulings (2026-11-09)

#429 is part of PR C (ruling D3 of the step-enhancements mission, relayed on #429), with its scope rulings D4(a),
D15 and D16, plus the activations half of D4b. Keep `NodeReference` stable once reviewed: the step-enhancements
mission reuses it and never defines a second copy.

- **`NodeReference` (T162; D15).** A new closed schema `{kind: NodeKind, id}` whose `id` admits `/`
  (`^[A-Za-z0-9][A-Za-z0-9_./-]*$`), introduced once. `EnhancerWithoutTarget.targets` items become `NodeReference`
  with `minItems: 1` (C36 guarantees a target). `### Breaking`: a documented response of `getActivationSkippedReport`
  (and of every read carrying `SkippedReport`) changes. `ArtifactReference` stays activatable-only.
- **Step references carried through (T163; #429 item 1, D4(a)).** `ClosureViewSession` maps an enhancer's `action:`
  targets instead of filtering them through `activatable()`. Every part of the skipped report that drops a step
  reference gets a unit test that states its answer (`ClosureViewSessionStepRefsTest`); only the step-target field
  changes on the wire.
- **`requiredNotInForce` covers activatable kinds (T163; #429 item 2, D16).** No policy or preset can name a step
  (the policy and preset schemas exclude `action`), so the floor holds activatable kinds only by construction. A
  test pins that `requiredNotInForce` never carries a step; WP29 writes the sentence into E14 and C31 and the
  divergence entry.
- **D4b, the activations half (T164).** `EffectiveSkippedReport.RequiredNotInForce.reason` is narrowed to the
  two-value type WP27 introduces (mapped, not by name); `ActivationWireTokenParityTest`'s `RequiredNotInForce.reason`
  row pins the contract against that type instead of the `PRUNING_REASONS` copy of `InForceResolution`'s condition.
- **`LayerOverride` (T138).** `artifact` is a `Urn`, so any kind can be named; the records follow the node predicate
  (WP27): a held step's override under an in-force `M` is listed, while template, asset, guard and lifecycle
  overrides are not (they are never in force). `ClosureViewSession.overridesOf` keeps step overrides for the graph;
  WP29 records the drop of the other kinds as a divergence.

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
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedRefusalCodesTest.java` — `ACTION_NOT_FOUND` moves from the unraised to the raised set (owned by WP11)
- `contracts/charter/examples/EffectiveActionContext.implement.yaml` — the `skipped` member of the context (owned by WP22)

- `contracts/charter/schemas/EnhancerWithoutTarget.yaml` — `targets` items become `NodeReference`, `minItems: 1` (#429, D15) (owned by WP22)
- `service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveSkippedReport.java` — enhancer targets as node references; `RequiredNotInForce.reason` narrowed (D4b) (owned by WP24)
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectiveReadMapper.java` — map `NodeReference` (owned by WP25)
- `service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationWireTokenParityTest.java` — the `RequiredNotInForce.reason` row against the narrowed type (D4b; from #436, not owned by a WP)
- `client-java` fixtures and the generated-model tests that pin `EnhancerWithoutTarget` — follow the schema change

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

`effective-graph-and-context.feature` US5 AS1–AS5 with negative controls; each absence (an edge absent, a step
absent) paired with a positive control in the same answer. Bind the glue, run against the `501`, record the assertion
failures as red (decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`), then serve (T140, T141).

### T140: Serve the graph

Service + controller + mapper for the graph, ETag/304.

### T141: Serve the action context

Service + controller + mapper, unknown-name refusals, mission type not in force.

### T142: Reservation coverage

`ReservedOperationCoverageTest` at 0 with its bad-fixture control; scenarios green.

### T162: `NodeReference` (D15)

Schema, `EnhancerWithoutTarget.targets` retyped with `minItems: 1`, a `### Breaking` fragment
(`2026-11-09-c-node-reference-step-targets.md`), example and lint updates.

### T163: Step references in the skipped report (#429)

Red unit tests per skipped-report part first (D4(a)); then carry step targets through `ClosureViewSession`; pin that
`requiredNotInForce` holds no step (D16).

### T164: Narrow the pruned reason (D4b, activations)

The activations record and mapper use WP27's type; the parity row pins the contract to it.

## Test strategy

Contract lint, the new feature file, `ReservedOperationCoverageTest`, controller tests, `ClosureViewSessionStepRefsTest`, `ActivationWireTokenParityTest`.

## Risks

- With no reserved operation left, the coverage test must still keep the machinery honest (the control).

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first (an assertion failure with every step bound, decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`) and now pass.
- **Exit check:** the contract suite passes; `ReservedOperationCoverageTest` counts 0 with its bad-fixture control; `ReservedRefusalCodesTest` passes with `action_not_found` raised.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
