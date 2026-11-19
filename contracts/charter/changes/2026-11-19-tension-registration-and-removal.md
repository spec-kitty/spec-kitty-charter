### Changed

- A registered relationship may be a tension (#602, ADR-0045 decisions 1 and 5). `putPackRelationship` accepts
  `type: tension`, whose `from` may name another pack's artifact; it stores the tension in its stored orientation and
  answers it as written. A tension and the structural relationship of the same pair are two slots: each answers `201`
  when new and `200` when it replaces the edge of its own slot. A tension the pack's own graph documents already join
  is `422 validation_failed` with `tension_duplicated` at `/to`. `deletePackRelationship` takes an optional `relation`:
  given, it removes only the registered edge of that relation on the pair (`404 relationship_not_found` when there is
  none); absent, every registered edge of the pair, and a pair with an author-written edge of either class is `409
  relationship_not_registered`, whose detail tells the client to pass `relation`. A whole-pack registration keeps a
  pack's tensions, judged on each side by the write policy.
