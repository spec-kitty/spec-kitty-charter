### Added

- The mission type reads, tag `MissionTypes` (#99, #148, #96): `getPackMissionType`
  (`GET /packs/{packId}/artifacts/mission_type/{missionTypeId}`, the `Artifact` envelope with member `missionType`: the
  stored fields, the steps then the on-demand steps, each linked, and the skeletons, each linked), `getPackMissionStep`
  (`.../steps/{stepId}`, the stored prompt and guidelines byte for byte, expected input and output artifacts, and the
  `mission-type`, `step-contract` and `skeleton` links) and `getPackMissionSkeleton` (`.../skeletons/{skeletonName}`,
  the stored bytes as `text` or `base64` with `sha256` and `size`). `{skeletonName}` is one path segment: the path
  under the type's `templates/` escaped as in RFC 6901 (`~` as `~0`, then `/` as `~1`), so
  `templates/divio/howto-template.md` is `divio~1howto-template.md` (D9). Refusals in order: `pack_not_found` or
  `pack_unreadable`, `mission_type_not_found`, then `action_not_found` (an unknown step) or `skeleton_not_found`.
  `getActivationMissionType`, `getActivationMissionStep` and `getActivationMissionSkeleton`, the same reads under
  `/activations/{activationId}/artifacts/mission_type/...`, are declared `x-reserved` (contract set `activation`, #99)
  and answer `501` until they are built. `mission_step_contract` is served by the generic item read
  (`getPackArtifact`); `mission_type` is listed, filtered and referenced like the other kinds, and its item is answered
  by `getPackMissionType`. Neither kind answers an `ETag`. New relations `mission-type`, `step`, `step-contract`,
  `skeleton` and `effective-artifact` join the hypermedia guide's table and `link-relations-are-known`.
