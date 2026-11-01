### Breaking

- Added refusals (issues #292, #309, #310): a directive, paradigm, styleguide or toolguide registration with a `null`
  list, or a `null` item in one, is refused 422 `validation_failed` (existing code) on POST and PUT; a list is sent as
  an array, `[]` where it may be empty, and an optional one may be left out. The refusal comes before the body is bound,
  so it wins over a 400 the same body would otherwise get; a `null` field inside an object item stays a 400. A
  directive, paradigm or styleguide registration body is at most 1 MiB; a larger body is refused 422 `validation_failed`
  before it is read, by its `Content-Length` and otherwise one byte past the limit (the toolguide's 7 MiB body limit is
  unchanged). The eight 422 descriptions of `registerPack{Directive,Paradigm,Styleguide,Toolguide}` and
  `putPack{Directive,Paradigm,Styleguide,Toolguide}` and the four `*Registration` schema descriptions say so; the
  service has refused them since #311 and #318. A detail names the list and, for an item, its index counted from 0.
  Scenarios: in `directives.feature`, `paradigms.feature`, `styleguides.feature` and `toolguides.feature`, "A null list,
  or a null item in a list, is refused before the body is bound" (a null-list and a null-item row each, with the exact
  detail), and in the first three "A <kind> registration body over 1 MiB is refused before it is read", with a body
  under the limit registered instead. Breaking on the 0.x line, which ADR-0022 allows; no release carried the accepted
  `null` or the unbounded body. Core's seam: none (no schema shape, enum or status changes; a client that sent `null`
  for a list sends `[]` or leaves it out, and no shipped artifact body is near 1 MiB).
