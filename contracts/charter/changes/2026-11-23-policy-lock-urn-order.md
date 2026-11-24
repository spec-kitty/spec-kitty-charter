### Changed

- Registration and every write judged over the closures a pack joins list a pack's case-only policy locks in URN
  order, where the order followed how the policy file was read, which was not stable (ruling R-z; ADR-0083 decision
  20). Only a pack with several case-only locks sees a change: the order of its `pack_reference_artifact_missing`
  faults, and so the first fault a registration answers with. `pack-doctor validate` answers the same order. No code,
  schema or status changes. Core's seam: none; core regenerates nothing.
