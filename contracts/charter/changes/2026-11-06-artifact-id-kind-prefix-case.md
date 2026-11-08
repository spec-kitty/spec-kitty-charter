### Changed

- The case-insensitive match of an artifact id spelling covers the URN's kind prefix too: `DIRECTIVE:directive_001`
  normalizes to the declared id. `ArtifactId` (and `ActivationConfig.schema.json`) widen the prefix of their `pattern`
  to `[A-Za-z][A-Za-z_]*`; the id after the colon keeps its grammar. `maxLength: 128` covers the whole string, so the
  description now says a declared id longer than 128 less the kind and the colon (118 for `directive:`) cannot be
  written as a URN, and is written as itself.
