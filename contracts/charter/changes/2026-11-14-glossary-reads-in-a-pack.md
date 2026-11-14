### Added

- `listPackGlossaryContexts` (`GET /packs/{packId}/artifacts/glossary`), `getPackGlossaryContext`
  (`GET …/glossary/{contextId}`) and `getPackGlossaryTerm` (`GET …/glossary/{contextId}/terms/{termId}`), tag
  `Glossaries` (#106; ADR-0049 decision 7, ADR-0074 decisions 3 and 9 to 14). The list answers
  `GlossaryContextList {packId, items: [GlossaryContextSummary {id, name, description, tags?, termCount, _links}],
  _links}` in id order, unpaged. The context answers the `Artifact` envelope with member `glossary`
  (`GlossaryContext`: the pack file in camel case, every term as a `GlossaryContextTerm`, `appliesTo` as a
  `GlossaryAppliesTo`) and links `self`, `collection` (the context list) and `term`, templated by the term's id, a new
  relation. The term answers `GlossaryTerm {id, name, definition, status, examples?, aliases?, avoid?, seeAlso?, urn,
  contextId, _links}` with links `self` and `up` (the context). The path parameters `contextId` and `termId` take a
  letter-first kebab slug of at most 64 characters, anything else is `400`. The three answer the pack revision as
  `ETag` and the source fingerprint. The segments are judged in path order: an unknown pack is
  `404 pack_not_found`, then a context the pack does not hold is `404 artifact_not_found` with the detail
  ``glossary context `<contextId>` is not held``, then a term the context does not hold is `404 artifact_not_found`
  with the detail ``term `<termId>` is not held in glossary context `<contextId>` ``. `listPackArtifacts` lists a
  pack's glossary contexts after its skills, titled by their name, and `?kind=glossary` filters on them; the templated
  item read refuses a glossary context `404 kind_not_found` and names the literal path, and `deletePackArtifact` does
  not remove one. The three operations are `ready` in the switch-gate register.
