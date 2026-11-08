### Changed

- `CharterRefusalCode`: `override_not_permitted`, `enhances_cycle`, `conflicting_relations`, `self_override` and
  `override_cycle` are `raised: true` (IC-15, FR-025; A6, A8, A11, A12, C28). The registration impact check composes every
  closure a registered pack joins, the closure of each pack that builds on it and the root set of each stored activation
  that holds it, and refuses the registration, `422`, naming the closure's roots and the packs involved, when the closure
  holds an `overrides` edge onto a target that is not `open` (judged with each policy scoped to what its pack and the packs
  it builds on hold, the source's own pack left out), an `enhances` cycle or an enhancer with no route to an artifact that
  enhances nothing (an enhancement of an artifact nobody holds has none), an artifact that both enhances and overrides one
  target, an `overrides` edge whose target resolves to its own source, or a cycle of `overrides` edges. A composition code
  (`pack_dependency_unknown`, `pack_dependency_out_of_range`, `pack_sibling_conflict` and the rest) is refused the same
  way, with the closure's roots named in the detail and among `packs`, as the `problem` of a `refused` registration
  status. The per-artifact writes and the two `DELETE`s answer them synchronously (`2026-11-07-a-per-artifact-impact-check.md`,
  `2026-11-07-a-removal-served.md`).
  The scenario `errors.feature` lists the five among the codes an operation raises.
