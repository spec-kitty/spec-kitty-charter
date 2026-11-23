### Changed

- A violation at a place inside a pack file now names that place (#617 follow-up). On a registration's
  `validation_failed`, a per-file write or removal whose pack would not revalidate, and a `pack_unreadable` refusal of
  a pack whose graph documents cannot be read, `RefusalViolation.pointer` is a URI reference: the pack-relative file,
  then `#` and an RFC 6901 JSON Pointer into it when the place is known, for example
  `drg/directive.graph.yaml#/edges/3` for an edge or `tactics/a.tactic.yaml#/schema_version` for a field the schema
  refuses. A fault at an edge (a malformed qualifier, an edge that joins an artifact to itself, an endpoint the
  relation's table or the source rule refuses) points at `/edges/<index>` in its document's `edges` list; a fragment
  node whose `body_path` leaves the pack points at `/nodes/<index>`; a schema fault points at the field it names. With
  no place inside the file, the pointer stays the bare path, or `pack`. The registration's problem detail names the
  first violation at its pointer. The code and the status are unchanged.
