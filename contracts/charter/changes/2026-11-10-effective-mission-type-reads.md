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
  (an unknown step) or `skeleton_not_found`. No `ETag`. Scenarios: `effective-mission-types.feature`, which also pins
  that the generic `getEffectiveArtifact` answers a `mission_step_contract` the closure holds, and `artifact_not_found`
  for one it does not.
