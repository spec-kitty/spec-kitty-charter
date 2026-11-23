### Breaking

- A pack's YAML file, or a Markdown file's front matter, of more than 1 048 576 code points is now refused before it
  is parsed, with the detail `the document is too large` (ADR-0083 decision 22), as a document past the parser's alias
  or nesting budget already was: a registration answers `validation_failed` with the violation `unreadable` at an
  artifact file, or `pack_unreadable` at `pack.yaml`, and `pack-doctor validate` answers the same and exits 1. Before,
  such a file was parsed, slowly, and accepted. A pack with such a file that registered before is refused now. Core's
  seam: none; no code or schema changes, and core regenerates nothing.
