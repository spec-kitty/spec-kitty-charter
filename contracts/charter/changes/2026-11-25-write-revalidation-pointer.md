### Changed

- A per-file write or removal refused `422 validation_failed` because the pack it would leave does not revalidate now
  lists each violation at the place in its file, as `RefusalViolation` and the change of 2026-11-23 promise and a
  registration already does: `pointer` is the pack-relative file, then `#` and the RFC 6901 pointer into it
  (`registered.graph.yaml#/edges/0/relation`), and the bare file only when the place inside it is not known. Before, it
  was always the bare file (#654). Both write paths now state a violation through one mapping. Core's seam: none; the
  schema is unchanged, and a reader that took the pointer as a bare file reads the part before `#`.
