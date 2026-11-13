# Footprint of `mission_step_contract`

This is an inventory, made on 2026-11-13, of everything that retiring the kind removes or changes (#514, #533).
Paths are relative to the repository root.

## Service: 88 Java files
- **kernel**: `ArtifactKind:28`, `NodeKind:23`, `RefusalCode:207, 260, 279` (`MISSION_STEP_CONTRACT_NOT_FOUND`), `ArtifactKindTest`.
- **web-transport**: `RefusalHandler:341`.
- **packs** (21 main files, 30 test files). These exist only for the kind:
  - `domain/{MissionStepContract, MissionStepContractIdentifier, ContractStep, GateBinding, StepDelegation, StepInput}`
  - `infra/storage/{MissionStepContractBinding, MissionStepContractDso, MissionStepContractYaml}`

  These mention it: `ArtifactReadService:144-158`, `ArtifactRemovalService`, `MissionTypeHoldingRead`,
  `PackContents`, `PackPolicy`, `ArtifactDtos`, `ArtifactJson`, `PresetMapper:107`, `DomainRepositoryService:567`,
  `FileSystemPackInspector`, `KindBindings:26`, `YamlFormats:18`.

  These tests exist only for the kind: `MissionStepContractTest`, `StepContractDtoTest`,
  `MissionStepContractCorpusTest`, `MissionStepContractRoundTripTest`.
- **missions-edge** (17 main files, 7 test files). These exist only for the kind: `EffectiveStepContract`,
  `EffectiveStepContractView`, `StepContractView`, `StepContracts`, `infra/packs/PacksStepContracts`.

  These mention it:
  - `EffectiveMissionTypesController:65`, the handler for #533;
  - `MissionBodies:168-216`, the delegation candidates;
  - `MissionLinks:34, 139-145`;
  - `MissionRelation:18` (`STEP_CONTRACT`);
  - `StepView`, `MissionTypeHolding`, `MissionTypeReadService:194`, `EffectiveMissionType*` and `*MissionTypes`.
- **activations**: `EffectiveStepContractWinner` exists only for the kind. `EffectiveReadService:702, 728-749` and
  `EffectiveMissionTypeWinner` mention it.
- **Tests** in artifacts-edge (1) and deployable (3): `ActivationRoutingTest`, `PackArtifactRoutingTest` and
  `ReservedRefusalCodesTest`.

## Contract
- **Operation**: `getEffectiveMissionStepContract`, at `openapi.yaml:201-202` and
  `paths/activations_activationId_artifacts_mission_step_contract_contractId.yaml`.
- **Parameters, responses and examples**: `parameters/StepContractId`, `responses/ActivationMissionStepContractNotFound`
  and `examples/Artifact.activation-mission-step-contract`.
- **Schemas**: `MissionStepContract`, `MissionStepContractGate`, `MissionStepContractInput`, `MissionStepContractStep`,
  `MissionStepDelegation`, `MissionStepDelegationCandidate`.
- **Enum values and members**: `ArtifactKindToken`, `ActivatableKind`, `CascadeKind`, `NodeKind`,
  `CharterRefusalCode` (`mission_step_contract_not_found`), `Artifact.missionStepContract`, `SelectedArtifacts`,
  `DeactivatedArtifacts`, `Preset.activatedMissionStepContracts`, and the `step-contract` link on `MissionStep`.
- **Scenarios**: `mission-types.feature`, `effective-mission-types.feature` and `effective-reads.feature` carry the
  heavy use. Six other files have one hit each.
- **Other contract files**: `contracts/lint/ruleset.yaml:312`, and the offering schema `ActivationConfig.schema.json:121, 275`
  with its corpus files 06 and 07.
- **Pack schemas**: `mission-step-contract.schema.json` (the whole file); the kind entries in `graph-shard`,
  `org-fragment`, `pack-manifest`, `policy`, `preset` and `charter`; and `file-kinds.yaml:81, 101, 120, 151-152`.

## Fixtures and packs
- **Charter example `built-in` pack**: 5 `*.step-contract.yaml` files, holding 26 delegations and 1 gate, and a
  `mission_step_contract.graph.yaml` with 5 nodes and 0 edges. The kind also appears in the manifest, the policy and
  the default preset.
- **`packs/qa`**: 7 files, a synced copy of open-packs.
- **Planted and digest fixtures**: 4 more files.
- **Open-packs**: 28 files (14 in foundation, 7 in writing, 7 in qa), with 144 delegation candidates and 1 gate. It
  also has 2 graph fragments with 21 nodes and 0 edges. The validator is `scripts/ci/validate_pack_schemas.py:81, 880`,
  and the kind is described in its docs pages.

## Docs needing an amendment note
- **ADRs**: 0024, 0036, 0037, 0042, 0044, 0048, 0049, 0050, 0053, 0054, 0064, 0071, 0073, 0074, 0075. ADR-0075
  decision 2 names `MissionStepContractIdentifier`.
- **Architecture pages**: `docs/architecture/pack-schemas/mission-step-contract.md` and the other architecture pages
  that name the kind.
- **Diagrams**: the six descriptor and write-model diagrams.
- **Other docs**: `docs/api/service-contract.md`, `docs/context/*`, the glossary and the conformance register.

## Step contract against the mission step
| Field of a step contract | In `MissionStep` |
|---|---|
| `steps[]`: substeps of one action | none (nested steps are #513, Post-cutover) |
| substep `command`, `inputs[]` (CLI flags) | none |
| substep `delegates_to {kind, candidates[]}` | none. 72 of the 144 candidates are already `scope` edges from the action (`delegation-edge-coverage.md`) |
| substep `guidance` | part of it, as `guidelines_path` |
| `gates[]` | none on the step. The lifecycle `transitions[].guards` carry them (ADR-0054 decision 12, #339) |
