### Added

- The mission type reads under an activation, tag `EffectiveMissionTypes` (#99, FR-007): `getEffectiveMissionType`
  (`GET /activations/{activationId}/artifacts/mission_type/{missionTypeId}`), `getEffectiveMissionStep`
  (`.../steps/{stepId}`) and `getEffectiveMissionSkeleton` (`.../skeletons/{skeletonName}`), the literal paths the
  generic `getEffectiveArtifact` leaves for the kind, as `getEffectiveAgentProfile` is for `agent_profile`. They answer
  the bodies of the pack-scoped reads (`Artifact` with `missionType`, `MissionStep`, `MissionSkeleton`) for the
  layer-merge winner of the type in the activation's merged closure, whatever its state: `packId` is the pack that holds
  the winner, the skeletons and the step prompt and guidelines are that pack's, and a step's `step-contract` link names
  the merged closure's step contract bound to the step. Every link stays under the activation, but the type's
  `artifact`, the pack-scoped read of the winner (`getPackMissionType` of the holding pack). Refusals in order:
  `activation_not_found`, then `mission_type_not_found` for a type no pack of the closure holds, then `action_not_found`
  (an unknown step) or `skeleton_not_found`. No `ETag`. Scenarios: `effective-mission-types.feature`.
- `getEffectiveMissionStepContract` (`GET /activations/{activationId}/artifacts/mission_step_contract/{contractId}`, tag
  `EffectiveMissionTypes`, D11): the layer-merge winner of a step contract under an activation, in the `Artifact`
  envelope with `missionStepContract` as the pack read answers it, `packId` the pack that holds the winner. The literal
  path the generic `getEffectiveArtifact` answered before (winner, no body). Its `mission-type` and `step` links and a
  held delegation candidate's `effective-artifact` stay under the activation; `artifact` is the pack read of the winner.
  A step's `step-contract` link under an activation now lands on it. Refusals: `activation_not_found`, then
  `mission_step_contract_not_found`. No `ETag`. The 404 of each mission read under an activation names only its own
  codes (`ActivationMissionTypeNotFound`, `ActivationMissionStepNotFound`, `ActivationMissionSkeletonNotFound`,
  `ActivationMissionStepContractNotFound`).
