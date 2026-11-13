---
work_package_id: WP08
title: Retire mission_step_contract
dependencies: [WP07]
requirement_refs:
- FR-006
- FR-007
- NFR-003
- C-005
planning_base_branch: slice-f-537-kind-taxonomy
merge_target_branch: slice-f-537-kind-taxonomy
branch_strategy: Planning artifacts for this mission were generated on slice-f-537-kind-taxonomy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-f-537-kind-taxonomy unless the human explicitly redirects the landing branch.
subtasks:
- T036
- T037
- T038
- T039
- T040
- T041
agent: claude
history:
- at: '2026-11-13T20:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/
create_intent:
- service/deployable/src/test/java/ai/speckitty/charter/deployable/RetiredNamesScanTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MissionStepContract*.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ContractStep.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GateBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepDelegation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepInput.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/MissionStepContract*.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBindings.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/ArtifactKind.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/NodeKind.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalCode.java
- contracts/packs/file-kinds.yaml
- contracts/packs/schemas/mission-step-contract.schema.json
- service/missions-edge/src/main/java/ai/speckitty/charter/missions/**/*StepContract*.java
- service/activations/src/main/java/ai/speckitty/charter/activations/**/EffectiveStepContractWinner.java
- contracts/charter/paths/activations_activationId_artifacts_mission_step_contract_contractId.yaml
- contracts/charter/schemas/MissionStepContract*.yaml
- contracts/charter/scenarios/removed-routes.feature
- contracts/charter/scenarios/effective-mission-types.feature
- contracts/charter/scenarios/artifacts.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/RetiredNamesScanTest.java
role: implementer
---

# Work Package Prompt: WP08 - Retire mission_step_contract

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/` and adopt it.

Then read these files in `kitty-specs/slice-f-kind-taxonomy-01M7B19K/`:

- `spec.md`: its Intent Summary and the requirements this work package maps to.
- `plan.md`: concern IC-03.
- `data-model.md`.
- `research.md`: R6, R14 and R15.
- `research/squad-post-plan.md`.

## Objective

`mission_step_contract` is gone, outright, from the kernel, the packs, missions-edge, activations, the contract, the pack schemas, the example packs and the fixtures. A `*.step-contract.yaml` file or a `mission_step_contract.graph.yaml` fragment is refused as a retired file kind. #533 is moot.

## Context

- `research/footprint-mission-step-contract.md`, plus the squad's additions in T-M8 and M-M4. Slice C added more step-contract code on `main`: `ActivationMissionTypes#stepContract`, the `EffectiveReadService` winner and placement code, and the D11 scenarios.
- `file-kinds.yaml`'s `retired:` section, with `RetiredFilesTest`, is the precedent.
- The removal is a Breaking change; record each item for WP10's fragment.

## Subtasks

### T036: The packs side

- Delete these packs types:
  - `MissionStepContract`, `MissionStepContractIdentifier`, `ContractStep`, `GateBinding`, `StepDelegation`, `StepInput`;
  - `MissionStepContractBinding`, `MissionStepContractDso`, `MissionStepContractYaml`.
- Remove the kind from `KindBindings`, `PackContents`, `PackPolicy`, `ArtifactReadService`, `ArtifactRemovalService`, `MissionTypeHoldingRead`, `ArtifactDtos`, `ArtifactJson`, `PresetMapper`, `DomainRepositoryService`, `FileSystemPackInspector` and `YamlFormats`. These are out-of-map edits, each a mechanical removal.
- Delete the kind-only tests: `MissionStepContractTest`, `StepContractDtoTest`, `MissionStepContractCorpusTest`, `MissionStepContractRoundTripTest`.

### T037: Kernel, file kinds and pack schemas

- Remove `MISSION_STEP_CONTRACT` from `ArtifactKind` and `NodeKind`, and the `MissionStepContract` marker (WP03). Remove `RefusalCode.MISSION_STEP_CONTRACT_NOT_FOUND`, and the `RefusalHandler:341` mapping (an out-of-map edit in web-transport).
- `file-kinds.yaml`:
  - drop the kind from `node_kinds`, `artifact_kinds` and `fragment_kinds`;
  - add `retired:` entries for `**/*.step-contract.yaml` and `**/mission_step_contract.graph.yaml`, with the reason "folded into the mission-type steps' substeps (slice F)".
- Delete `mission-step-contract.schema.json`. Drop the kind from the `graph-shard`, `org-fragment`, `pack-manifest`, `policy`, `preset` and `charter` schemas, and from `ActivationConfig.schema.json` with its corpus files 06 and 07.

### T038: missions-edge and activations

- Delete `StepContracts`, `PacksStepContracts`, `EffectiveStepContract`, `EffectiveStepContractView`, `StepContractView` and `EffectiveStepContractWinner`.
- Edit `EffectiveMissionTypesController` (remove the handler for `getEffectiveMissionStepContract`), `MissionLinks` (the `step-contract` link), `MissionRelation.STEP_CONTRACT`, `StepView.stepContractId`, `MissionTypeHolding`, `MissionTypeReadService`, `EffectiveMissionType*`, `ActivationMissionTypes#stepContract`, `ActivationsMissionTypes`, `PacksMissionTypes`, and `EffectiveReadService:702,728-749` plus C's winner and placement code.
- Delete or update the tests the footprint lists.

### T039: Contract

- **Removals:** the path file and its `openapi.yaml` entry; `parameters/StepContractId` and its index entry; `responses/ActivationMissionStepContractNotFound`; `examples/Artifact.activation-mission-step-contract`, `Artifact.mission-step-contract` and `CharterRefusal.mission-step-contract-not-found`; the six `MissionStepContract*` schemas.
- **Enum values and members:** `ArtifactKindToken`, `ActivatableKind`, `CascadeKind`, `NodeKind`, `CharterRefusalCode` (the value and its `x-refusal`), `Artifact.missionStepContract`, `SelectedArtifacts`, `DeactivatedArtifacts`, `Preset.activatedMissionStepContracts`, and the `step-contract` link on `MissionStep`.
- **Text:** `Problem.yaml:29`, `ArtifactSummary`, `ArtifactNotFound`, `parameters/ArtifactKind`, both `*_kind_artifactId` and both `*_references` paths, `ActivationSkippedReport.each-bucket`, `MissionStep.specify`, and the `kind_not_found` prose.
- `ruleset.yaml`'s `link-relations-are-known` (an out-of-map edit; WP09 owns it) and the hypermedia table rows (WP10).
- Remove the `getEffectiveMissionStepContract` key from `switch-gate.yaml` (out-of-map).

### T040: Fixtures, scenarios and the removed routes

- Example pack: delete `missions/built_in_step_contracts/**` and `mission_step_contract.graph.yaml`, and drop the manifest, policy, preset and glossary entries. Regenerate the hashes.
- Planted and digest fixtures: `digest-baseline/fixture/mission_step_contracts/m-first.step-contract.yaml`, `planted/duplicate-step-id` (repurposed to substep ids), `planted/core-verdicts.json:1491` and `pack-level/presets-pack/presets/governing.yaml:22`.
- Add a retired-file refusal fixture: a pack shipping a `*.step-contract.yaml` is refused by name.
- Scenarios: remove the step-contract parts of `mission-types.feature` (out-of-map), `effective-mission-types.feature`, `effective-reads.feature` (out-of-map), `artifacts.feature`, `errors.feature:252`, `every-operation.feature`, `request-id-length.feature:40` and `unexpected-failure.feature:107`.
- `removed-routes.feature` pins `404 kind_not_found` on each retired item route, and `400` on `?kind=mission_step_contract`.

### T041: Scans

`RetiredNamesScanTest` (deployable):

- 0 occurrences of `mission_step_contract`, `MissionStepContract`, `MISSION_STEP_CONTRACT`, `step-contract` (as a link relation) and `IMPACTS|REPLACES|DELEGATES_TO` in `service/*/src/main`;
- the same tokens in `contracts/`, excluding `changes/`, `CHANGELOG.md` and `file-kinds.yaml`'s `retired:` block.

Add a positive control: a planted string in a temporary file is found.

## Validation

- `./gradlew verifyAll --max-workers=1` is green.
- The scans are clean.

## Definition of Done

- T036 to T041 are done.
- #533's route is gone.
- Each Breaking item is listed for WP10.
- Every commit is signed, carries no `Co-Authored-By` trailer and names no model.
- The tests this work package touches are green. Gradle runs in the foreground with `--max-workers=1`, one run at a time.
- Out-of-map edits are recorded with a one-line rationale in the commit message.

## Risks and reviewer guidance

- **A large deletion across seven modules.** Compile module by module.
- **ArchUnit rules that name deleted classes.** Update them.

## Branch Strategy

Planning happened on `slice-f-537-kind-taxonomy`, which is also the final merge target. `finalize-tasks` computes the
execution lanes into `lanes.json`, and each lane gets its own worktree. Do not choose a base branch by hand.

```bash
spec-kitty agent action implement WP08 --agent claude --mission slice-f-kind-taxonomy-01M7B19K
```

## Coordination

- **Slice C** is already on `main` (PR #566), and this branch is based on it.
- **#554 and #556 (epic #549).** Until #554 lands, the read check stays in `PackPolicy`, and whichever lands second rehomes it. Check the open PRs before each WP.
- **#551.** It changes the `kernel.validation` API; the second of the two to land rebases.
- **#106.** It renames `glossary_pack` to `glossary`; the second to land rebases.
- **#552.** It drops `MissionStepContractIdentifier` from its list.
- **Slice D close-out (#367, #371).** It runs in parallel, so rebase or merge `main` often. Never force-push a pushed branch.
