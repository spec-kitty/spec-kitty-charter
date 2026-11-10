### Added

- The effective reads link the mission type reads (#99, #450, FR-008): an `EffectiveArtifact` item of kind
  `mission_type` or `mission_step_contract` (effective list, action context) carries `effective-artifact`
  (`getEffectiveMissionType`, or `getEffectiveMissionStepContract` for a step contract) and `artifact` (`getPackMissionType` or
  `getPackArtifact` of the pack that holds the version in force), and an `EffectiveGraphNode` of those kinds carries
  `effective-artifact`. A step node `action:<type>/<step>` carries `effective-artifact`, the step's read under the
  activation (`getEffectiveMissionStep`), and the `EffectiveActionContext` carries `effective-artifact` (the same read)
  and `artifact`, the step's read in the pack that holds the mission type's version in force (`getPackMissionStep`).
  A graph node names no pack, so it has no `artifact`. The profiles mission's WP08 scenarios that pinned these entries
  without `_links` now pin the links.
