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

- **OD-9, one predicate for "pack P holds step M/s" (T159).** `StepHolding` in `packs.domain`, over composition facts:
  P defines `mission_type:M` and declares `action:M/s`. `EdgeIndex.stepsOf`, `ActionContextWalk`'s `action_not_found`
  and the walk's start node all use it; the step-enhancements mission's FR-009 calls it. A pack that declares
  `action:M/s` without defining `mission_type:M` does not hold the step.
- **OD-6, the E6 amendment (T160).** A selected step enhancer is in force exactly when the mission type of its single
  `enhances` target is in force. In `InForceResolution` (`packs.domain`): `enhancerOut` skips `action:` targets, and
  `bindingOf` derives `M` from the step enhancement's `enhances` edge to `action:M/s` in the merged edges. The pruned
  reason is the existing `MISSION_TYPE_NOT_IN_FORCE` (bucket `missionTypeNotInForce`); the refusal code
  `mission_type_not_in_force` (OD-5) belongs to the step-enhancements mission's prompt read and is not added here.
  Red test, production-shaped: select `step_enhancement:x` and `mission_type:M`, never the `action:`, and expect
  ACTIVE; with `M` deselected expect INACTIVE with `MISSION_TYPE_NOT_IN_FORCE`. A step enhancement with an `enhances`
  edge to anything but one `action:` keeps E6 as built (C36 and ADR-0066 guarantee one target).
- **D4b, the packs half (T161).** `InForceStates.RequiredNotInForce.reason` is narrowed to the two pruning reasons
  (`MISSION_TYPE_NOT_IN_FORCE`, `ENHANCED_TARGET_NOT_IN_FORCE`) by type, so a third reason cannot reach the bucket
  (WP28 narrows the activations half and the parity row).
- **The node predicate (runner decision, recorded with the plan-point re-analysis).** E15 and E16 restrict the graph
  and the walk to `Eff`, which never holds a step. The predicate is `Eff` plus every step `action:M/s` that a closure
  pack holds (T159) and whose `M` is in `Eff`, as E6 already binds a step to its mission type. Kinds with no
  activation state (template, asset, guard, lifecycle) are not in force and are neither listed nor walked through;
  WP29 records the divergence.

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

`StepHolding` in `packs.domain`; `EdgeIndex.stepsOf`, `action_not_found` and the walk's start node use it; tests
including a pack that declares the action without defining the mission type.

### T160: E6 amendment (OD-6)

Production-shaped red test first (select the step enhancement and the mission type, never the action), then the
`enhancerOut` and `bindingOf` change; the deselected-`M` twin answers `MISSION_TYPE_NOT_IN_FORCE`.

### T161: Narrow the pruned reason (D4b, packs)

A two-value type behind `InForceStates.RequiredNotInForce.reason`; the existing tests keep their assertions.

## Test strategy

`GraphProjectionTest`, `ActionContextWalkTest`, `StepHoldingTest`, the OD-6 cases in `InForceResolutionTest`; `GraphWalkTest` unchanged.

## Risks

- `GraphWalk` is shared with existing reads: keep its behaviour for them (existing tests unchanged).

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first (an assertion failure with every step bound, decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`) and now pass.
- **Exit check:** `:packs:test` passes with `GraphProjectionTest`, `ActionContextWalkTest`, and `GraphWalkTest` unchanged.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
