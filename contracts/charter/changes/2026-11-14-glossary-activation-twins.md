### Added

- The activation twins of the glossary reads (#106; FR-011 to FR-013, ADR-0074 decision 12):
  `listEffectiveGlossaryContexts` (`GET /activations/{activationId}/artifacts/glossary`),
  `getEffectiveGlossaryContext` (`GET …/glossary/{contextId}`) and `getEffectiveGlossaryTerm` (`GET
  …/glossary/{contextId}/terms/{termId}`), tag `EffectiveGlossaries`. Each answers the copy of a context the layer
  merge keeps across the levels of the closure, whole: a term only a copy the winner replaced holds is not in force.
  The list answers `GlossaryContextList` of the closure's context winners, whatever their state, in id order and
  unpaged, with no `packId` (each item's `artifact` link leads to its pack's copy): `GlossaryContextList` serves both trees, and its
  `packId` is present in a pack and absent under an activation. A list item links `effective-artifact` and `artifact`.
  The context answers `EffectiveGlossaryContextRead`, the members of `EffectiveArtifactRead` (`state`, `reason`,
  `policy`, `required`, `satisfiedBy?`, `suggestion?`) plus `glossary` (`GlossaryContext`), whatever its state, and
  links `self`, `artifact`, `collection`, `term` (templated by the term's id) and `up`. The term answers
  `EffectiveGlossaryTermRead`, the same members plus `term` (`GlossaryContextTerm`), with its context's facts, because
  a term has no state of its own, and links `self`, `artifact` (`getPackGlossaryTerm` in the winner's pack) and `up`
  (the context under the activation). Each answers a strong `ETag` of its own read and `304` to a matching
  `If-None-Match`. The segments are judged in path order, before any `If-None-Match`: an unknown activation is `404
  activation_not_found`, then a context no pack of the closure holds is `404 artifact_not_found` with the detail
  ``glossary context `<contextId>` is not held``, then a term the winning copy does not hold is `404
  artifact_not_found` with the detail ``term `<termId>` is not held in glossary context `<contextId>` ``; two packs of
  one level holding one context are `500 pack_sibling_conflict`. An `Activation` links `glossaries`
  (`listEffectiveGlossaryContexts`), a new relation, after `skills`. The three operations are `ready` in the
  switch-gate register.
