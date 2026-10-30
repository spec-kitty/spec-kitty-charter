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

## F2 (spec-kitty-charter, minor): the glossary term "pack document"

Done in #260 (`site/.contextive/definitions.yaml`), raised by the pre-PR squad.

## F3 (spec-kitty-charter): case-folded ownership of companion paths

**Found by** the #260 pre-PR squad (architecture and adversarial lenses). `Companions.claimedByRule` classifies a
companion path case-sensitively, while the write's claim check folds case. On a case-insensitive file system a skill
whose `body_path` is `PACK.md` loads (no rule matches) and reads the same bytes as the pack document, and the
whole-pack write then refuses with "two parts of the pack are written to pack.md". The load and the write disagree;
the guard holds. The class of gap predates #260 (any rule's file named in another case), but `pack.md` is the first
root-level Markdown owner. **Proposal:** classify the companion path case-folded as well, so the load refuses it as it
refuses the exact name (ADR-0039 decision 3). Parent: epic #186.

## F4 (spec-kitty-charter, minor): one wording for a file that is not UTF-8

The store's load says "the file cannot be read as UTF-8 text" and validate mode says "is not valid UTF-8", for every
file kind. #260's validate-parity test pins both. **Proposal:** one wording, shared by both readers.
