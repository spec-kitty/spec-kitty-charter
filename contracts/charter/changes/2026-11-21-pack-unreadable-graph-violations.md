### Added

- A `500 pack_unreadable` refusal of a pack whose graph documents cannot be read lists `violations` (#617). That
  covers the composing reads (the offering and every read that composes a closure), the stored edge read, and a
  per-file write or removal that reads the pack's authored graph. There is one violation per fault of the pack's graph
  documents, in registration's order (by path), with the same `pointer` (the pack-relative graph document), `code`
  (`drg_schema_error`, also for a graph document that is not YAML) and `detail` that a registration of the same pack
  lists inside its `422 validation_failed`. The composing reads' `detail` now names the first graph document and its
  reason, as the edge read and a removal already did (#628): `a graph document of <pack> cannot be read:
  <pack>/<file>: <reason>`, where it was `a graph document of <pack> cannot be read`; pack-check's `skipped` reason
  quotes the longer detail. The code and the status are unchanged; `Problem.violations` and `CharterRefusalCode` say
  so.
