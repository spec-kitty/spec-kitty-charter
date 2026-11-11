### Added

- Every pack read of one thing answers the pack revision as its `ETag` (#487): `getPackArtifact` for a tactic and a
  mission_step_contract, `getPackMissionType`, `getPackMissionStep` and `getPackMissionSkeleton` join the kinds that
  already did. The published "no ETag" statements of these reads are withdrawn. For the mission-type family the tag
  is a concurrency token naming the pack, not a cache validator: the answer can carry content from dependency packs
  that the revision does not cover. No pack read takes `If-None-Match` or answers 304; activation reads are unchanged.
  Core's seam: nothing to change. Additive. Closes the contract half of #487.
