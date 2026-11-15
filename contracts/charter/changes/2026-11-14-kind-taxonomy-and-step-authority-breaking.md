### Breaking

- `mission_step_contract` is retired outright (ADR-0077 decision 3, operator ruling DM-01M7B1YH1GF15RD0G4ZPSA8KW9;
  #514, #533). A step's substeps are authored in its mission type (see the substeps entry of this version), and the
  `review` gate is the guard `spec-kitty-pre-review` on the work-package lifecycle's `in_progress -> for_review`
  transition (ADR-0054 decision 12). This retires what earlier entries of this version added for the kind. Gone from
  the contract: the operation `getEffectiveMissionStepContract` and its path
  `/activations/{activationId}/artifacts/mission_step_contract/{contractId}`, with its switch-gate key; the parameter
  `StepContractId`; the response `ActivationMissionStepContractNotFound`; the example
  `Artifact.activation-mission-step-contract`; the schemas `MissionStepContract`, `MissionStepContractGate`,
  `MissionStepContractInput` and `MissionStepContractStep`; the value `mission_step_contract` of `ArtifactKindToken`,
  `ActivatableKind`, `CascadeKind` and `NodeKind`; the refusal code `mission_step_contract_not_found`; the members
  `Artifact.missionStepContract`, `SelectedArtifacts.mission_step_contract`,
  `DeactivatedArtifacts.mission_step_contract` and `Preset.activatedMissionStepContracts`; and a step's
  `step-contract` link, with the `step` link relation a step contract's links carried (`contracts/lint/ruleset.yaml`
  and the hypermedia relation table). The offering schema `ActivationConfig.schema.json` drops the kind from
  `selected` and `deactivated`. Every retired route answers as an unknown kind: `404 kind_not_found` on the item and
  `/references` routes in both trees, and a `400` Problem without a code on `?kind=mission_step_contract`
  (`removed-routes.feature`). Core's seam: regenerate the DTOs, drop the step-contract read, the kind token and the
  refusal code, and read a step's `substeps` from `getPackMissionStep` and `getEffectiveMissionStep`.
- The pack schemas change with the kind (ADR-0077): `mission-step-contract.schema.json` is removed, and the kind
  leaves `graph-shard`, `org-fragment` (the fragment plural `mission_step_contracts`), `pack-manifest`, `policy`,
  `preset` (`activated_mission_step_contracts`) and `charter` (`selected_mission_step_contracts`). A
  `*.step-contract.yaml` file or a `mission_step_contract.graph.yaml` fragment is refused by name as a retired file
  kind (`file-kinds.yaml`, `retired:`): `422 validation_failed` at registration and a blocking finding in validate
  mode. A consumer of `contracts/packs` (#559) meets the same break; spec-kitty-open-packs folds its 28 step
  contracts into their steps.
- The relations `impacts`, `replaces` and `delegates_to` are retired (ADR-0077 decision 6, operator ruling
  DM-01M7B1YXJHQCBSYJ145NJC77V5). They leave `Relation` (its `x-source` is now `RelationType`) and the relation enum
  of `graph-shard.schema.json` and `org-fragment.schema.json`, and an edge's `impacts` strength leaves both schemas,
  whose edge is closed. A graph document whose edge uses one is refused as an unknown relation, and an edge that
  writes `impacts` as an unknown member, as any unknown relation or member is (`drg_schema_error`), so a pack that
  registered with one before is refused now. `putPackRelationship` with one as its `type`, or with any type that
  names no relation, answers `422 validation_failed` with one violation `relationship_type_unknown` at `/type`
  (example `CharterRefusal.relationship-type-unknown`). `replaces` was
  sometimes written to mean `overrides`: rewrite such an edge as `overrides`. Core's seam: map no relation to the
  three, drop the edge strength, and read one more violation code of `validation_failed`.
- The other refusals of this change are entries of their own in this version: the endpoint table
  (`relationship_endpoint_not_allowed`, and the same-kind rule of `overrides`), the step links and `template_set`
  (`mission_step_unlinked`), and the substeps (`substep_delegation_unresolved`, `substep_delegation_unscoped`, and the
  renamed `MissionSubstepDelegation` and `MissionSubstepDelegationCandidate`).
