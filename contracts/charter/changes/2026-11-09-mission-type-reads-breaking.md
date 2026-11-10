### Breaking

- Mission types and mission step contracts become readable (#99, #148, #96; ADR-0022). `CharterRefusalCode` gains
  `mission_step_contract_not_found` (404, the not-found code of kind `mission_step_contract`) and `skeleton_not_found`
  (404, a skeleton name that does not decode to a skeleton of the mission type); the generic item and references reads,
  `deletePackArtifact` and `getEffectiveMissionStepContract` raise the first, and the skeleton reads raise the second. `Artifact` gains the members `missionType` (`MissionType`) and `missionStepContract`
  (`MissionStepContract`) in its `oneOf`, and `ArtifactKindToken`, the `?kind=` filter, gains `mission_type` and
  `mission_step_contract` after `agent_profile`. The new `MissionSkeleton.encoding` is a closed enum, `text` or
  `base64`. Core must regenerate its DTOs (`CharterRefusalCode`, `Artifact`, `ArtifactKindToken` and the new mission
  schemas) and handle the two codes, the two members and the two kind tokens before it relies on an exhaustive switch
  over any of them. For the five kinds already served the reads answer as before; one behaviour changes: a
  `deletePackArtifact` now prunes the directories the removal leaves empty (operator ruling).
