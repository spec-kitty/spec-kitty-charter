---
work_package_id: WP27
title: 'C · packs: graph projection and the action-context walk'
dependencies:
- WP23
requirement_refs:
- FR-015
- FR-018
- FR-019
- NFR-007
- FR-017
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T134
- T135
- T136
- T137
- T159
- T160
- T161
phase: PR C
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/ActionContextWalk.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ActionContextWalk.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/GraphProjection.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ActionContextWalkTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/GraphProjectionTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepHolding.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/StepHoldingTest.java
- contracts/charter/changes/2026-11-09-c-step-enhancer-binding.md
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ActionContextWalk.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/GraphWalk.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/GraphProjection.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ActionContextWalkTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/GraphWalkTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/GraphProjectionTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepHolding.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/StepHoldingTest.java
- contracts/charter/changes/2026-11-09-c-step-enhancer-binding.md
role: implementer
tags: []
tracker_refs: []
---
# WP27: C · packs: graph projection and the action-context walk

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Walk the merged edges for the last two reads, in `packs` only (C-002): the graph projection answers the merged edges
whose endpoints are both in force (FR-018); `ActionContextWalk.walk(missionType, action, depth, inForce)` runs
ADR-0018's five breadth-first steps from `action:<missionType>/<action>` over the merged index, `depth` (0 or 1)
counting `suggests` hops; a node the predicate refuses is neither listed nor walked through; each listed artifact
brings its active enhancers both ways (FR-015, FR-019). The predicate is "in the effective set" (A7: E16 reads M's
in-force state from the effective set, not a stored mission-type list).

Implementation command:
`spec-kitty agent action implement WP27 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Scope added by operator rulings (2026-11-09)

Binding rulings from the step-enhancements mission's plan point (`adr-0066-step-enhancement-service-alignment-01M6ZRYK`,
plan "Operator decisions (plan point, 2026-11-09): ruled", OD-6, OD-9, D4b) put three more items in this WP. That
mission consumes them after PR C merges (its `PrCSignaturesTest`); keep the names below stable once the lane is
reviewed.

- **OD-9, one predicate for "pack P holds step M/s" (T159).** `StepHolding` in `packs.domain`, over composition facts,
  in two forms (plan-point squad, A4 and the blocker on `EdgeIndexTest`):
  - **per pack:** P defines `mission_type:M` and declares `action:M/s` (the operator's wording; the step-enhancements
    mission's FR-009 calls this form at compose time);
  - **on the closure:** the closure holds step M/s when some closure pack holds it per pack; the step's place is the
    winner of `action:M/s` on the merged view, so a layer override of a held step is the winning version of a held step.
    `EdgeIndex.stepsOf`, the walk's start node and `action_not_found` use this form. `EdgeIndexTest`
    (`theStepsOfAMissionTypeAreThoseOfTheWinnersOnTheMergedView`) stays unchanged and is the guard.
  - A winning `mission_type:M` does not take its losers' unique steps (A7, as built and documented in
    `resolution-model.md`); kept. A test pins it.
  - A pack that declares `action:M/s` and no closure pack defines `mission_type:M`: the step is not held.
  - Signature: the per-pack form takes the facts every caller holds (the pack's defined artifacts: `EdgeIndex` has
    `definedArtifacts`, compose time has `PackCompositionFacts`), so both missions call one function.
  - Consumer tests: one each for `EdgeIndex.stepsOf`, `action_not_found` and the walk's start node, each failing if the
    consumer bypasses the predicate. `StepHolding` reads only the session's verified read (T137's counting test covers
    it, NFR-007).
- **OD-6, the E6 amendment (T160).** A selected step enhancer is grounded exactly when the mission type of its single
  `enhances` target is in force. Scope (plan-point squad A1–A3):
  - only a source of kind `step_enhancement` with exactly one `enhances` edge, to an `action:` URN. Every other shape
    (two targets, a mixed target list, another kind whose targets are actions) keeps E6 as built; each has a test.
    C36 does not guarantee one target (ADR-0066's check lands later), so these shapes are reachable.
  - the target is a held step (T159, closure form): the binding is `mission_type:M`, derived in `packs.domain` from the
    merged edges **before** `ClosureView.resolutionEdges` drops edges to non-winners, and passed in `Edges.bindings`;
    `enhancerOut` does not count that edge.
  - the target is not a held step: the `enhances` edge is kept, so the enhancer is pruned with
    `ENHANCED_TARGET_NOT_IN_FORCE` and its target `action:M/s` is listed (the production-shaped source for #429 item 1;
    an enhancer never stands alone, I11).
  - red test, production-shaped, with **no** facts binding for the step enhancement (so the old `bindingOf` cannot
    pass it): select `step_enhancement:x` and `mission_type:M`, never an `action:` (the selection schema cannot name one, so the test is production-shaped by construction), expect ACTIVE; with `M` deselected
    expect INACTIVE with `MISSION_TYPE_NOT_IN_FORCE` (bucket `missionTypeNotInForce`). The refusal code
    `mission_type_not_in_force` (OD-5) is the step-enhancements mission's and is not added here (decision
    `01M702A8XYB588PSGTXRKQ4NKK`).
  - the wire changes for writes and reads (the `enhancedTargetNotInForce` bucket and `ActivationChange.warnings` no
    longer carry such an enhancer): a `### Changed` fragment `2026-11-09-c-step-enhancer-binding.md` and an
    activations-level test (`EffectiveSetTest`, through `ClosureViewSession`).
  - a step enhancement that also overrides its own mission type would be unstable (`override_unstable`); that shape is
    the step-enhancements mission's ADR-0066 integrity check, not PR C's (deferred, noted to that runner).
- **D4b, the packs half (T161).** `InForceStates.RequiredNotInForce.reason` takes a two-value type
  (`MISSION_TYPE_NOT_IN_FORCE`, `ENHANCED_TARGET_NOT_IN_FORCE`). Red test: a reflection assertion that the record
  component's type has exactly the two constants (fails on the ten-value `Reason`).
- **The node predicate (decision `01M702A71YF7WNDTG2AA89K8ZA`, as amended by the squad).** Built in `packs`
  (`ClosureView`) from the in-force states, never handed in by `activations` or by a test:
  - the walk **starts** at `action:M/a` when `M` is in `Eff` and the closure holds the step; it **lists and walks
    through** `Eff` only, so a step is never an entry (`EffectiveArtifact.kind` is activatable) and never changes a
    hop count;
  - the graph's nodes are `Eff` plus each held step whose `M` is in `Eff`; `layerOverrides` follow the same nodes;
  - kinds that are not activatable (template, asset, guard, lifecycle) are absent from the effective reads even when a
    lock puts them in force inside `packs`; WP29 records the divergence.
  - tests at `ClosureView` level with a real resolution (`ClosureViewTest`), beside the unit tests of the walk.
- **Suggestions and conditional entries (FR-019, #408 ruling 3).** The walk returns, per entry, how it was reached; an
  entry reached only through `suggests` carries the edge's `when` and `reason`. `activations` maps them through the
  existing suggestion carrier in `EffectiveSet` (decisions `01M6Y6VE6VZQ4MXD98N93JB3GJ`, `01M6Y0X5E9ANR25MC8RPFXYM72`);
  no second copy of the rule.
- **Order (standing ruling: never accidental order).** Graph nodes sorted by URN; edges by (source, relation, target);
  `layerOverrides` by (artifact, losing level, losing pack); walk entries in ADR-0018 order, ties by the ADR-0018 parent
  rule then URN. No answer depends on closure, iteration or list order.

## Context

- **Delivery:** PR C (stacked on PR B).
- **Concern:** IC-10. **Requirements:** FR-015, FR-018, FR-019, NFR-007.
- **Unknown names:** an unknown mission type answers `mission_type_not_found`, an unknown action `action_not_found`,
  both judged against the merged closure.
- **Open review item 4:** FR-019 "what the step contract names" is read as what ADR-0018's walk reaches from the
  action node; keep it as the plan reads it.

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


### Files shared with other work packages

These files are owned by another WP (the no-overlap rule) but this WP must make the small edit named. Make only that edit, and record a one-line rationale in the commit message (`out-of-map: <file> — <why>`):

- `service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java` — expose `mergedGraph(Predicate<Urn>)` and `actionContext(...)` (owned by WP21)
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/EdgeIndex.java` — any accessor the walk needs over the merged index (owned by WP21)
- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/InForceResolution.java` — the E6 amendment (OD-6): `enhancerOut` skips `action:` targets, `bindingOf` reads the step enhancement's `enhances` edge (owned by WP23)
- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/InForceStates.java` — `RequiredNotInForce.reason` narrowed to the two pruning reasons (D4b) (owned by WP23)
- `service/packs/src/test/java/ai/speckitty/charter/packs/domain/InForceResolutionTest.java` — the OD-6 red tests and the narrowed reason (owned by WP23)
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java` — the step-enhancer binding before the winner filter (OD-6), the node predicate from the in-force states (owned by WP21)
- `service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureViewTest.java` — the node predicate and OD-6 through a real resolution (owned by WP21)
- `service/packs/src/test/java/ai/speckitty/charter/packs/application/EdgeIndexTest.java` — new cases for the closure form of `StepHolding`; the existing step test unchanged (owned by WP21)
- `service/activations/src/test/java/ai/speckitty/charter/activations/domain/EffectiveSetTest.java` — the OD-6 wire change seen from activations (owned by WP24)

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

### T134: Graph projection

`GraphProjection` over the merged index filtered by the predicate; an edge dropped with either endpoint; tests.

### T135: Action-context walk

The five ADR-0018 steps, depth 0/1, predicate filtering, enhancers both ways; tests per step.

### T136: Unknown names

`mission_type_not_found` / `action_not_found` from the merged closure.

### T137: One read

Both run on the session's verified read (NFR-007); a counting test.

### T159: One step predicate (OD-9)

`StepHolding` in `packs.domain`, per pack and on the closure; `EdgeIndex.stepsOf`, `action_not_found` and the walk's
start node use the closure form; tests for a layer override of a held step, a loser's unique step (A7) and a declared
action with no mission type in the closure.

### T160: E6 amendment (OD-6)

Production-shaped red test first (select the step enhancement and the mission type, never the action), then the
`enhancerOut` and `bindingOf` change; the deselected-`M` twin answers `MISSION_TYPE_NOT_IN_FORCE`.

### T161: Narrow the pruned reason (D4b, packs)

Red: the reflection assertion on the record component; then the two-value type. Existing assertions unchanged.

## Test strategy

`GraphProjectionTest`, `ActionContextWalkTest`, `StepHoldingTest`, the OD-6 cases in `InForceResolutionTest`; `GraphWalkTest` unchanged.

## Risks

- `GraphWalk` is shared with existing reads: keep its behaviour for them (existing tests unchanged).

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first (an assertion failure with every step bound, decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`) and now pass.
- **Exit check:** `:packs:test` and `:activations:test` pass: `GraphProjectionTest`, `ActionContextWalkTest`, `StepHoldingTest`, the OD-6 and D4b cases in `InForceResolutionTest`, the `ClosureViewTest` cases and the `EffectiveSetTest` OD-6 case are new and were red first; `GraphWalkTest` and the existing `EdgeIndexTest` step case unchanged.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
