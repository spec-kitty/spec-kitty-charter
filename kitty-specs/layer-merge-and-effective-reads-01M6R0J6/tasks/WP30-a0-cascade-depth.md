---
work_package_id: WP30
title: 'A0 · Cascade depth: suggests hops on a cascading select and deactivate (#408)'
dependencies:
- WP08
requirement_refs:
- FR-043
- NFR-001
- NFR-002
- C-001
- C-006
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T153
- T154
- T155
- T156
- T157
phase: PR A0
history:
- timestamp: '2026-11-07T00:00:00Z'
  agent: claude-runner
  action: Prompt added for the operator's #408 ruling 1 (2026-11-07)
agent_profile: java-jenny
authoritative_surface: contracts/charter/
create_intent:
- contracts/charter/changes/2026-11-07-a0-cascade-depth.md
- contracts/charter/scenarios/activation-cascade-depth.feature
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesDepthTest.java
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/schemas/SelectionRequest.yaml
- contracts/charter/schemas/ExclusionRequest.yaml
- contracts/charter/changes/2026-11-07-a0-cascade-depth.md
- contracts/charter/scenarios/activation-cascade-depth.feature
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesDepthTest.java
role: implementer
tags: []
tracker_refs:
- '#408'
---
# WP30: A0 · Cascade depth: suggests hops on a cascading select and deactivate (#408)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Give a cascading select and a cascading deactivate an optional `depth`: the number of `suggests` hops the walk may
cross (FR-043, IC-21). Without `depth` the walk is unlimited, exactly as today (ADR-0059 D2, core parity).

Implementation command:
`spec-kitty agent action implement WP30 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Ruling:** issue #408, ruling 1 (2026-11-07), recorded in `research/rulings-2026-11-07.md`.
- **Meaning (decision `DM-01M6TDH0SY5Q224GX709DXWG8K`):** `depth` counts `suggests` hops only, as ADR-0018 D4's
  `depth` does for the action-context walk. `requires`, the other hard relations, `enhances` and the lineage
  pass-through are walked in full. `depth: 0` follows no `suggests` edge; `depth: 1` follows one, and so on.
- **Walk state:** the pair (artifact, `suggests` hops used). An artifact first reached over more `suggests` hops is
  re-expanded when a later path reaches it with fewer, so the answer does not depend on visit order.
- **Deactivate:** the target's own walk honours `depth`, so a cascading deactivate at depth N undoes a cascading select
  at depth N. The shared walk (the other active artifacts, which decides what is kept) stays unlimited: the bound never
  drops a candidate another active artifact still reaches.
- **Read first:** `plan.md` IC-21 and IC-19, `spec.md` FR-043, ADR-0059 D2 and its amendments, ADR-0018 D4, and the
  current `CascadeRules`, `ReachPlan`, `ClosureViewSession` and the packs walk behind `ClosureView.reach`.

### Files shared with other work packages

These files are owned by WP05 (approved), WP08 (merged before this WP) or WP24 (PR B, later). Make only the edit named, and record a one-line rationale in the
commit message (`out-of-map: <file> — <why>`):

- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationJson.java` and
  `ActivationBodyAdvice.java`: read and validate `depth` (integer ≥ 0) and pass it to the application.
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ActivationSteps.java`: only if a new step is
  needed; prefer existing steps.
- The packs walk implementation behind `ClosureView.reach` (in `service/packs/.../application/`): honour the bound.
- `service/activations/src/main/java/ai/speckitty/charter/activations/domain/ReachPlan.java` and `CascadeRules.java`
  (WP05): carry the `suggests` bound and pass it on the select and the deactivate's target walk.
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSession.java` (WP24):
  map the bound into the `ReachRequest`.
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/ReachRequest.java` (WP05): carry the bound.

## Project constraints (apply to every subtask)

- **TDD, contract first (C-001).** The schema change and its `contracts/charter/changes/` fragment land first; the
  scenario is red (an assertion failure with every step bound) before the code serves it. Record the red run.
- **No disabled tests, no new suppressions (C-006, NFR-001).** Methods stay within CC ≤ 15, NPath ≤ 1000, ≤ 60 lines
  and nested-if depth ≤ 1.
- **Hexagonal slices (C-002).** The walk lives in `packs`; `activations` only plans it (`ReachPlan`) and reads its
  answer. `HexagonalArchitectureTest` stays green.
- **Gradle.** Module tasks only: `:packs:check :activations:check :deployable:check :client-java:check
  :contracts:check`. On a Maven Central `429`, wait and re-run; never touch verification metadata.

## Subtasks

### T153: Contract: `depth` on the cascade requests

`depth` (integer, minimum 0, optional) on `SelectionRequest` and `ExclusionRequest`, its description stating the
meaning above and that absent means unlimited. A `### Added` fragment
`contracts/charter/changes/2026-11-07-a0-cascade-depth.md` citing #408 and the decision id. `depth` without `cascade`
is accepted and has no effect (there is no walk); say so in the description.

### T154: Red scenarios

`contracts/charter/scenarios/activation-cascade-depth.feature`, over a fixture pack where a profile `requires` one
artifact and `suggests` a chain of two (each with its own `requires`): depth 0 selects the target and its hard closure
only; depth 1 adds the first `suggests` target and its hard closure; absent depth selects the whole chain (the control
in the same feature); a cascading deactivate at depth 1 undoes the depth-1 select; `depth: -1` and `depth: "1"` are
`422 validation_failed` naming `/depth`. Run it red before T155–T156.

### T155: Domain and plan

`ReachPlan` carries an optional `suggests` bound; `CascadeRules.planSelect` and the deactivate's target walk pass it;
the shared walk does not. Unit tests in `CascadeRulesDepthTest`.

### T156: The packs walk honours the bound

`ReachRequest` carries the bound; the walk keys its visited set on (artifact, hops used) and re-expands on fewer hops.
Unit tests in the packs slice for diamond paths (one `suggests` path and one `requires` path to the same artifact).

### T157: API glue and the client

Map `depth` from the request body; regenerate the client and check the round trip of a request with and without
`depth`. Run the gate.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The new feature is green, with its red run recorded in the hand-off; the module gate is green.
- The hand-off note tells WP09 what to write: the ADR-0059 D2 amendment for `depth`, and the divergence entry (core
  has no write-cascade depth).

## Reviewer guidance

- Check the meaning against IC-21 and the decision, not against core.
- Check that an absent `depth` changes nothing: every existing cascade scenario passes unchanged.
- Check the diamond case: an artifact reachable over `requires` is selected at depth 0 even when a `suggests` path
  also reaches it.
