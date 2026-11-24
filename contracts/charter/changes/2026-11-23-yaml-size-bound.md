### Breaking

- A pack's YAML file, or a Markdown file's front matter, of more than 1 048 576 code points is now refused before it is
  parsed, with the detail `the document is too large` (ADR-0083 decision 22), as a document past the parser's alias or
  nesting budget already was. So is a document whose strings, keys and values, a value shared through an alias counted
  at each use, hold more than 1 048 576 code points in all (Tier R review B3): one long scalar aliased thousands of
  times passed the alias and node budgets and exhausted the memory of every serialization of it. A document without
  aliases is never refused for this, since a string is never longer than the text that writes it. A number's digits
  count in the same budget at each reach, and a number scalar whose text holds more than 1 000 code points is refused
  before it is converted (Tier R review B3′): a 500 000-digit integer reached 30 000 times ran validation for hours.
  Quoted, the same digits are a string, bounded by the cap. So is a float whose plain form, the text
  `BigDecimal.toPlainString()` renders (`1e-999` is `0.`, 998 zeros and `1`), is longer than 1 000 code points, judged
  from its exponent's text (Tier R review B3″, F1): `score: 1e-999999999` passed the schema and ran the round trip out
  of memory. A registration answers `validation_failed` with the violation `unreadable` at an artifact file, or
  `pack_unreadable` at `pack.yaml`, and `pack-doctor validate` answers the same and exits 1. Before, such a file was
  parsed, slowly, and accepted. A pack with such a file that registered before is refused now. Core's seam: none; no
  code or schema changes, and core regenerates nothing.
