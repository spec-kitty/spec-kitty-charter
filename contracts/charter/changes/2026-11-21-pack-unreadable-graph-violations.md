### Added

- A `500 pack_unreadable` refusal of a pack whose graph documents cannot be read lists `violations` (#617). That
  covers the composing reads (the offering and every read that composes a closure), the stored edge read, and a
  per-file write or removal that reads the pack's authored graph. There is one violation per fault that makes a graph
  document unreadable and, on a composing read, per edge that joins an artifact to itself. They are listed by path, with
  the `pointer` (the pack-relative graph document), `code` (`drg_schema_error`, also for a graph document that is not
  YAML; `tension_self` for a tension whose two ends are the pack's own) and `detail` that a registration of the same
  pack lists inside its `422 validation_failed`. Other faults of the pack's graph documents are not among them.
  `Problem.violations`, `RefusalViolation.pointer`, `CharterRefusalCode` and the `OfferingUnreadable` response say so.
  The code and the status are unchanged.
