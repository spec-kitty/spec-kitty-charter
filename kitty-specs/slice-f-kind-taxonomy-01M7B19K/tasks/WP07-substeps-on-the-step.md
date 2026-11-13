---
work_package_id: WP07
title: Substeps on the mission step
dependencies: [WP06]
requirement_refs:
- FR-004
- FR-005
planning_base_branch: slice-f-537-kind-taxonomy
merge_target_branch: slice-f-537-kind-taxonomy
branch_strategy: Planning artifacts for this mission were generated on slice-f-537-kind-taxonomy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-f-537-kind-taxonomy unless the human explicitly redirects the landing branch.
subtasks:
- T030
- T031
- T032
- T033
- T034
- T035
agent: claude
history:
- at: '2026-11-13T20:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/missions/src/main/java/ai/speckitty/charter/missions/domain/
create_intent:
- service/missions/src/main/java/ai/speckitty/charter/missions/domain/Substep.java
- service/missions/src/main/java/ai/speckitty/charter/missions/domain/SubstepInput.java
- service/missions/src/main/java/ai/speckitty/charter/missions/domain/SubstepDelegation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/SubstepDelegationRule.java
- contracts/charter/schemas/MissionSubstep.yaml
- contracts/charter/schemas/MissionSubstepDelegation.yaml
- contracts/charter/schemas/MissionSubstepDelegationCandidate.yaml
execution_mode: code_change
owned_files:
- contracts/packs/schemas/mission-type.schema.json
- service/missions/src/main/java/ai/speckitty/charter/missions/domain/MissionStep.java
- service/missions/src/main/java/ai/speckitty/charter/missions/domain/Substep*.java
- service/missions/src/test/java/ai/speckitty/charter/missions/domain/**
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/SubstepDelegationRule.java
- contracts/charter/schemas/MissionStep.yaml
- contracts/charter/schemas/MissionSubstep*.yaml
- contracts/charter/examples/MissionStep*.yaml
- service/packs/src/testFixtures/resources/example-packs/built-in/missions/software-dev/**
- service/missions-edge/src/main/java/ai/speckitty/charter/missions/infra/api/MissionBodies.java
role: implementer
---

# Work Package Prompt: WP07 - Substeps on the mission step

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/` and adopt it.

Then read these files in `kitty-specs/slice-f-kind-taxonomy-01M7B19K/`:

- `spec.md`: its Intent Summary and the requirements this work package maps to.
- `plan.md`: concern IC-03.
- `data-model.md`.
- `research.md`: R6, R14 and R15.
- `research/squad-post-plan.md`.

## Objective

A mission-type step authors `substeps`: a flat, ordered list holding everything a step contract's step held (operator ruling `DM-01M7B1YH1GF15RD0G4ZPSA8KW9`). Each delegation candidate must resolve and must have a `scope` edge from the step's action. The reads in both trees serve the substeps. The example pack's 5 step contracts become substeps. The step-contract kind still exists here; WP08 retires it.

## Context

- The data model has `Substep`, `SubstepInput` and `SubstepDelegation`.
- Candidates follow the grammar `[<pack>::]<id>`, with the kind given once (D-M8). The delegated kind is a `scope` target from `action`: G or agent_profile (D-M4).
- In-pack checks run over `PackContents.MissionTypeFacts` (WP06). Qualified candidates are checked at compose and registration (D-M3).
- The step contract schema (`mission-step-contract.schema.json`) is the source of the substep, input and delegation field shapes.

## Subtasks

### T030: Schema

- `mission-type.schema.json`'s `Step` gets `substeps` (optional; `minItems: 1` when present). Each item is a closed object `{id, description, command?, inputs?, delegates_to?, guidance?}`.
- Copy the `inputs[]` item (`flag`, `source`, `optional`) and the `delegates_to` (`kind`, `candidates`) definitions from `mission-step-contract.schema.json`.
- `delegates_to.kind` is an enum of the scope-target kinds: directive, tactic, styleguide, toolguide, paradigm, procedure, agent_profile.
- `candidates` items follow the pattern `^([a-z0-9][a-z0-9-]*::)?<id grammar>$`.
- Substep `id` uses the kernel identifier grammar.

### T031: Domain and codec

- `Substep`, `SubstepInput` and `SubstepDelegation` are records with kernel guards.
- `MissionStep` gains `List<Substep> substeps()`: empty when not authored, and ids unique within the step.
- The mission-type YAML codec reads and writes them in authored order.
- Round trip (ADR-0036): add a `DomainRoundTripTest` case.

### T032: In-pack load rules

- `MissionTypeFacts` gains, per step, its substep delegations.
- `SubstepDelegationRule`:
  - A bare candidate must be an artifact of `kind` in this pack; otherwise `SUBSTEP_DELEGATION_UNRESOLVED`.
  - `action:<t>/<s>` must have a `scope` edge to it; otherwise `SUBSTEP_DELEGATION_UNSCOPED`.
- Register both as `PackFindingCode`s with `violationKind` mapping (an out-of-map edit, `PackPolicy`), and in `CharterRefusalCode.yaml` (out-of-map), with examples.
- Retarget `PackFindingCode.DUPLICATE_STEP_ID` ("a step contract declares a step id twice", `PackPolicy.java:494`) to duplicate substep ids within a step.

### T033: Qualified candidates at compose and registration

- A qualified candidate `<pack>::<id>` resolves in the closure, through the existing qualified-reference check (`RelationshipTargetCheck`, and `pack_reference_*` codes).
- Refuse with `substep_delegation_unresolved` when the qualified artifact is missing, and order this check before the reference check so the fixture has one fault.
- The `scope` edge for a qualified candidate is the qualified edge.

### T034: Contract and reads

- `MissionStep.yaml` gains `substeps` (`MissionSubstep`).
- Rename `MissionStepDelegation` to `MissionSubstepDelegation`, and `MissionStepDelegationCandidate` to `MissionSubstepDelegationCandidate`. Keep `id`, `$ref Links.yaml`, `artifact`, `effective-artifact` under an activation, and empty links for an unserved kind. Repoint the `x-source`.
- `MissionBodies` builds the substeps for `getPackMissionStep` and `getEffectiveMissionStep`.
- Examples and scenarios go in `mission-types.feature` (out-of-map rows; WP06 owns it) and `effective-mission-types.feature`.

### T035: Example pack

- Fold each of the 5 `built_in_step_contracts/*.step-contract.yaml` into its step's `substeps` in `software-dev.mission-type.yaml`, keeping every field. WP01 already made the candidates resolve and added their `scope` edges.
- `ExamplePackCoverageTest.everySchemaPropertyHasAnExample` must pass: every new `substeps/*` property has an example.
- Regenerate the manifest hash.

## Validation

- `:missions:test :packs:test :missions-edge:test :deployable:test` are green.
- A step read shows the substeps in authored order, with linked candidates.

## Definition of Done

- T030 to T035 are done, and both delegation codes are pinned by invalid packs and scenarios.
- Every commit is signed, carries no `Co-Authored-By` trailer and names no model.
- The tests this work package touches are green. Gradle runs in the foreground with `--max-workers=1`, one run at a time.
- Out-of-map edits are recorded with a one-line rationale in the commit message.

## Risks and reviewer guidance

- **Two homes for one step's content between WP07 and WP08.** That is acceptable inside the mission, since the PR lands whole. Do not add a reader that merges the two.

## Branch Strategy

Planning happened on `slice-f-537-kind-taxonomy`, which is also the final merge target. `finalize-tasks` computes the
execution lanes into `lanes.json`, and each lane gets its own worktree. Do not choose a base branch by hand.

```bash
spec-kitty agent action implement WP07 --agent claude --mission slice-f-kind-taxonomy-01M7B19K
```

## Coordination

- **Slice C** is already on `main` (PR #566), and this branch is based on it.
- **#554 and #556 (epic #549).** Until #554 lands, the read check stays in `PackPolicy`, and whichever lands second rehomes it. Check the open PRs before each WP.
- **#551.** It changes the `kernel.validation` API; the second of the two to land rebases.
- **#106.** It renames `glossary_pack` to `glossary`; the second to land rebases.
- **#552.** It drops `MissionStepContractIdentifier` from its list.
- **Slice D close-out (#367, #371).** It runs in parallel, so rebase or merge `main` often. Never force-push a pushed branch.
