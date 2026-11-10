# Contract delta

The authoritative change is in `contracts/charter/`, recorded in four change fragments in `contracts/charter/changes/`:
`2026-11-09-mission-type-reads-breaking.md` (`### Breaking`), `2026-11-09-mission-type-reads-added.md`,
`2026-11-10-effective-mission-type-reads.md` and `2026-11-10-mission-kind-and-step-links.md` (`### Added`). All are on
the 0.x line (ADR-0022). This page is the one view of the change for reviewers.

## Breaking (core regenerates its DTOs)

- `CharterRefusalCode` gains `mission_step_contract_not_found` and `skeleton_not_found`, both raised.
- `Artifact` gains the members `missionType` and `missionStepContract`.
- `ArtifactKindToken` gains `mission_type` and `mission_step_contract`, after the profiles mission's `agent_profile`.
- `MissionSkeleton.encoding` is a new closed enum: `text`, `base64`.

## Added

| Operation | Path | Tag |
|---|---|---|
| `getPackMissionType` | `GET /packs/{packId}/artifacts/mission_type/{missionTypeId}` | `MissionTypes` |
| `getPackMissionStep` | `GET /packs/{packId}/artifacts/mission_type/{missionTypeId}/steps/{stepId}` | `MissionTypes` |
| `getPackMissionSkeleton` | `GET /packs/{packId}/artifacts/mission_type/{missionTypeId}/skeletons/{skeletonName}` | `MissionTypes` |
| `getEffectiveMissionType` | `GET /activations/{activationId}/artifacts/mission_type/{missionTypeId}` | `EffectiveMissionTypes` |
| `getEffectiveMissionStep` | `GET /activations/{activationId}/artifacts/mission_type/{missionTypeId}/steps/{stepId}` | `EffectiveMissionTypes` |
| `getEffectiveMissionSkeleton` | `GET /activations/{activationId}/artifacts/mission_type/{missionTypeId}/skeletons/{skeletonName}` | `EffectiveMissionTypes` |
| `getEffectiveMissionStepContract` | `GET /activations/{activationId}/artifacts/mission_step_contract/{contractId}` | `EffectiveMissionTypes` |

- In a pack, `mission_step_contract` is served by the generic item read; under an activation, by the literal
  `getEffectiveMissionStepContract` (D11), so the templated `getEffectiveArtifact` serves neither mission kind. Both kinds are listed, filtered, referenced and removable
  (`deletePackArtifact`; a mission type's removal cascades its skeletons, D10).
- Behavior change for every kind, the five included: `deletePackArtifact` prunes the directories a removal leaves
  empty, up to the pack's root (operator ruling).
- The four effective mission reads answer the layer-merge winner whatever its state, carry
  `Charter-Source-Fingerprint` and no `ETag`, and link no `up`.
- `{skeletonName}` is one segment: the path under the type's `templates/`, escaped as in RFC 6901 (D9).
- Schemas: `MissionType`, `MissionStep`, `MissionStepContract`, `MissionSkeleton` and their parts.
- Relations: `mission-type`, `step`, `step-contract`, `skeleton`. `artifact` and `effective-artifact` (the profiles
  mission's) extend to the mission kinds, `action:` graph nodes and the action context.
