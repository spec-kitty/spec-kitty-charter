### Changed

- `parent_pack` is an ordering and a cycle edge, as `depends_on` is (operator ruling of 2026-11-06). The pack a child
  extends merges before the child, whatever the pack ids sort as, so `GET /offering` and the closure read serve the
  parent first and the child's preset takes the place of its parent's. A `parent_pack` cycle, a pack that names itself
  included, is refused `500 pack_dependency_cycle`; it was served before. Core's seam: none, `parent_pack` was
  already in the descriptor. `getOffering`, `listActivationPacks` and `ActivationPackList` say the order puts dependencies
  and `parent_pack` parents before the packs that need them.
- Two packs of one level that hold one preset id and are not related by `parent_pack` are refused
  `500 pack_sibling_conflict`; the last holder in load order used to win. A child and a pack on its `parent_pack` line
  may hold one id, the child winning, and a preset id held at different levels is still a layer override (ADR-0053
  decision 6, ADR-0059 decision 3). `getOffering` says so; `composition.feature` and `activation-register.feature` pin it.
