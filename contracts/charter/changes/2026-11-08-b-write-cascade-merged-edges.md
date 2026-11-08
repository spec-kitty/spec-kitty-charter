### Changed

- The activation write cascade walks the merged edges of the closure (FR-038, rule E20): a cascading select or
  deactivate never follows an edge that a version replaced by a later level declared, and where no layer override
  exists the answer is unchanged. A write over a closure whose layer merge refuses answers `500`, with `pack_unreadable`
  (an override the mutability level forbids, an `overrides` cycle, an unstable answer or an `overrides` edge that ends at
  its own source) or `pack_sibling_conflict` (a step contest no `overrides` entry settles), as the effective reads
  do. No operation, schema or response changes: the sub-resource writes already declare the `500` of
  `OfferingUnreadable`, which names the composition codes (`pack_sibling_conflict` among them), `pack_unreadable`,
  `stale_input` and `internal_error`. The preconditions of a write are still judged before the packs are read.
