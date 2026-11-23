### Changed

- `pack-doctor validate --round-trip` and `pack-doctor remediate` read every file of the whole-pack round trip, the
  source and what was written, through the parser's guards (aliases, nesting depth, size; ADR-0083 decision 23). A file
  past a guard, such as an alias bomb or a document nested 20 000 deep, is a problem (exit 1) at the file, `unreadable`
  with the parser's reason, where it was an internal error (exit 2) or a crash with nothing on standard output. No code
  or schema changes. Core's seam: none; the CLI and its report are not part of the API core calls, and core
  regenerates nothing.
