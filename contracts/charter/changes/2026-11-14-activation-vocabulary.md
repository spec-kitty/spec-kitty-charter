### Added

- The vocabulary reads of an activation (#106; FR-014 to FR-017, ADR-0076), tag `Vocabulary`, each `ready` in the
  switch-gate register. `listActivationVocabularyTerms` (`GET /activations/{activationId}/vocabulary/terms`) answers
  `VocabularyTermList`: `activationId`, `revision`, `governanceFingerprint`, `terms` and `_links` (`self`, `activation`).
  The terms are those of the winning copy of each glossary context in force, whole (a term only a replaced copy holds is
  not listed), whatever their status, ordered by context id and then term id in Unicode code point order. A
  `VocabularyTerm` carries `urn`, `contextId`, `id`, `name`, `definition`, `status`, `examples?`, `aliases?`, `avoid?`,
  `seeAlso?`, the `packId` and `level` of its context's winning copy, and `_links` (`effective-term`,
  `effective-glossary`). `getActivationVocabularyContextive` (`GET …/vocabulary/contextive`) answers a Contextive v1
  definitions file as `application/yaml`, a string with an example in the contract: the contexts in force by id
  (`name`, `domainVisionStatement`, `paths?`, `terms`), their terms by id (`name`, `definition`, `examples?`,
  `aliases?`, `meta: {urn, status}`); an `Accept` that excludes `application/yaml` is `406`, a Problem with no code.
  `listActivationVocabularyTaboos` (`GET …/vocabulary/taboos`) answers `TabooList`: `entries` of `TabooEntry` (`word`,
  `urn`, the URN of the term to use instead, and `termName`, its name), one per avoided word per term, ordered by word and then URN in Unicode code point order, and
  `_links` (`self`, `activation`). Each read answers its own strong `ETag` and `Charter-Source-Fingerprint`, and `304`
  to a matching `If-None-Match`, judged after its refusals: `404 activation_not_found`, and the closure's `500` codes
  (`pack_sibling_conflict` among them). An activation with no glossary context in force answers empty `terms`,
  `entries` and `contexts`. An `Activation` links `vocabulary-terms`, `vocabulary-contextive` and `vocabulary-taboos`,
  after `glossaries`; the relations `activation`, `effective-term` and `effective-glossary` are new too. The REST
  conventions' success media type gains its `application/yaml` exceptions, `/openapi.yaml` and the Contextive read.
- One term, three schemas, one shape (#106; operator ruling 2026-11-14): `GlossaryTerm`, `GlossaryContextTerm` and
  `VocabularyTerm` hold the term members (`id`, `name`, `definition`, `status`, `examples?`, `aliases?`, `avoid?`,
  `seeAlso?`) with identical schemas, and a contract test pins them. A `seeAlso` entry's two ids are letter-first kebab
  slugs of at most 64 characters, in the contract schemas as in `glossary.schema.json`.
