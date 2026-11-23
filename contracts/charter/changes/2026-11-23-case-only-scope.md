### Changed

- `pack-doctor validate` (`CLOSURE`) judges the case-only answer in registration's scope (ruling R-z; ADR-0083
  decision 20): for each pack of the set, over the closure that pack roots, as a registration of it composes it, every
  member's preset items and policy locks against every member's holdings. A mention that names a held artifact only by
  letter case is `pack_reference_artifact_missing`, naming the mentioning pack then the first holder in merge order, with
  the resolver's detail and no violation of its own; a fault two packs' closures find is reported once. So a
  dependency's preset item or lock that a pack depending on it holds only by case is now refused by `validate` when the
  set holds both, as registration refuses it; checked alone, the dependency passes. A preset item's other faults
  (`preset_item_not_held`, `preset_item_undeclared`) keep the declaring pack's own closure. Registration is unchanged:
  its phase 6 and pack-doctor call one body (`CaseOnlyRules`). Registration also judges the closures stored activations
  compose, so a case-only mention between sibling packs that only an activation lists together is refused at
  registration; pack-doctor, which sees no activation, passes that shape. No code, schema or HTTP behavior changes. Core's seam:
  none; the CLI and its report are not part of the API core calls, and core regenerates nothing.
