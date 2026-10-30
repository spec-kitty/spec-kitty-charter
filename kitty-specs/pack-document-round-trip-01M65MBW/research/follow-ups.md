# Follow-ups drafted by #260

No `spec-kitty-open-packs` issue is needed: the built-in `pack.md` at the pin reads and round-trips clean.

## F1 (spec-kitty-charter): one home for the Markdown model's content rules

**Found by** the #260 plan-point squad (architecture lens).

`MarkdownDocument` and `MarkdownScanner` (`infra/storage/markdown/`) import only `java.*` and JSpecify; only
`MarkdownCodec` needs infra (`FrontmatterYaml`). Today the domain holds two shapes of the same model without its
rules:

- `Guide` (#194) re-implements the block rules by hand (`Guide.standsAsSectionContent`), and drifts from the scanner:
  it does not refuse setext underlines, empty headings or a tab after `#`, which `MarkdownScanner` does.
- `PackDocument` (#260) holds the model's shape and leaves the rules to the write, where `PackDocumentMarkdown`
  turns a breach into `VALIDATION_FAILED` naming `pack.md`.

**Proposal.** Move `MarkdownDocument` and `MarkdownScanner` to a domain package (for example
`packs.domain.markdown`, allowed by `HexagonalArchitectureTest` DOMAIN_IS_PURE), keep `MarkdownCodec` in infra, and
let `Guide` and `PackDocument` check their content through the one scanner at build time (a
`ConstraintBreachException`). #260 did not do it: it rewrites #194's merged code (#260's C-002) and files #194 PR-B
is editing.

**Done when** `Guide` and `PackDocument` refuse the same texts the codec refuses, at build time, with no hand copy.
Parent: epic #186.

## F2 (spec-kitty-charter, minor): glossary term "pack document"

`docs/context` has no entry for the pack document (`pack.md`, `Pack.document()`, gate kind `pack-document`).
