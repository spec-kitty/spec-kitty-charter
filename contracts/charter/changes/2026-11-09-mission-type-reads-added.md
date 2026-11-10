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
  `mission_step_contract` is served by the generic item read
  (`getPackArtifact`); `mission_type` is listed, filtered and referenced like the other kinds, and its item is answered
  by `getPackMissionType`. Neither kind answers an `ETag`. New relations `mission-type`, `step`, `step-contract` and
  `skeleton` join the hypermedia guide's table and `link-relations-are-known`.
- `deletePackArtifact` removes a mission type and a step contract (#99, D10). A mission type goes with its step prompt
  and guidelines files and its skeletons as one removal under one impact check; it is refused `409`
  `artifact_referenced` while a step contract whose `mission` names it, or an edge that names one of its action nodes
  (`action:<type>/<step>`) or skeletons, refers to it, and `409` `registration_conflict` while an author-written graph
  document declares the type's, a skeleton's or an action node's node, or holds an edge from the type or a skeleton. A step contract is removed as the five kinds are. An unknown id is
  `mission_type_not_found` or `mission_step_contract_not_found`. Neither kind answers an `ETag`: `If-Match: *` or the
  pack revision. `deletePackArtifact` now also prunes the directories a removal leaves empty, up to the pack's root, for
  every kind (operator ruling): the last directive of a pack takes `directives/` with it.
