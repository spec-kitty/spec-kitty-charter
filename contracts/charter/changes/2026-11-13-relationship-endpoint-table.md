### Breaking

- An edge whose relation does not allow its source and target kinds is refused (ADR-0064 decision 13, #514, #350).
  The endpoint table names, per relation, the kinds each side may hold: a procedure no longer requires a step's
  action, and an `overrides` or `enhances` edge joins two artifacts of one kind. A registration answers
  `422 validation_failed` with one violation per such edge, code `relationship_endpoint_not_allowed`, at its
  pack-relative graph document (example `PackRegistrationStatus.relationship-endpoint-not-allowed`).
  `putPackRelationship` answers `422 validation_failed` with the violation at `/type` and writes nothing (example
  `CharterRefusal.relationship-endpoint-not-allowed`). A composing read of a closure that holds such a pack answers
  `500 pack_unreadable`, naming the graph document. An `overrides` edge from a kind that is not activatable keeps
  `overrides_source_not_activatable`, and an `enhances` or `overrides` edge from a step enhancement the pack holds
  keeps `prompt_fragment_invalid`. Core's seam: a client that reads the violations of `validation_failed` meets one
  more code; no `CharterRefusalCode` is added.
