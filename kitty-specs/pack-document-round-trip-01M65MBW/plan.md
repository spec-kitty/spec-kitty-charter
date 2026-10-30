# Implementation Plan: Round-trip pack.md through the markdown content form

**Branch**: `issue-260-pack-md-round-trip` | **Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/pack-document-round-trip-01M65MBW/spec.md`; research in
[research.md](research.md).

## Summary

Hold each pack's `pack.md` in the `Pack` aggregate as a domain `PackDocument`, read and written through #194's
`MarkdownCodec`, validated against a new `pack-document` structure schema, digested between the presets and the
manifest, validated in validate mode, and modelled by the whole-pack gate as `pack-document`. No new form, codec or
comparison (C-002).

## Technical Context

**Language/Version**: Java 25 (Gradle, `service/packs`)
**Primary Dependencies**: #194's `infra.storage.markdown` (`MarkdownCodec`, `MarkdownDocument`), `PackSchemas`
**Storage**: pack directories on the file system (`FilesystemPackStore`, `WholePackStore`)
**Testing**: JUnit 5 + AssertJ; the whole-pack gate (`:packs:wholePackRoundTrip`, tag `whole-pack`); the open-packs
seam (`openPacksRoundTrip`, part of `:packs:check`); ArchUnit (`HexagonalArchitectureTest`)
**Constraints**: zero new suppressions, complexity ≤ 15, Error Prone/NullAway/Checkstyle/Spotless clean

## Charter Check

| Charter rule | How this plan meets it |
| --- | --- |
| Round trip is equality of meaning (ADR-0036) | `pack.md` compares by its Markdown model (`compareMarkdown`); the gate turns it green honestly, not by an exemption |
| Divergence from the charter's own model | The document is held by the charter's own markdown model, unchanged |
| Breaking the OpenAPI contract | `contracts/charter/**` untouched (C-001); one additive pack schema |
| Test first, no fake green | Red-first per work package; each refusal beside a clean control (C-005); the lossy writer beside the faithful run |
| Domain, architecture changes get a squad at the plan point | Plan-point squad below; pre-PR squad at closeout |
| Boundaries (DIRECTIVE_001, ADR-0006) | Domain `PackDocument` has no infra dependency; mapping lives in infra (DM `01M65MKX…`) |
| Decision documentation | ADR-0042/0037/0040 amendments; two Decision Moments; open review item for frontmatter equality |

## Design

### D1. Domain value (IC-01)

`ai.speckitty.charter.packs.domain.PackDocument(Optional<Map<String, Object>> frontmatter, Optional<String> title,
String preamble, List<Section> sections)`, `Section(String title, String content)`. Null-checked and copied
(frontmatter top level unmodifiable, as `MarkdownDocument` does); a title is one non-blank line without surrounding
whitespace. Markdown standing (no column-0 H1/H2 in a block, no CR, fences closed) is the codec's: a document read from
a file always holds; one built in code that breaks it is refused when written (`MarkdownDocument`'s constructor),
which `WholePackStore` reports as a refused write. `Pack` gains `Optional<PackDocument> document()`, set by
`Builder.document(PackDocument)`, kept by `toBuilder` (so `with` keeps it).

### D2. Infra mapper (IC-01)

`PackDocumentMarkdown` (infra.storage): `toMarkdown(PackDocument) → MarkdownDocument`,
`fromMarkdown(MarkdownDocument) → PackDocument`, a bijection over the model's fields. Constants: `FILE = "pack.md"`,
`KIND = "pack-document"`.

### D3. Rule and schema (IC-02)

`file-kinds.yaml`: `*/pack.md` → `schema: pack-document`, `content_form: markdown`, appended after the toolguide-guide
rule. `schemas/pack-document.schema.json`: the toolguide-guide schema's keywords, with `frontmatter` an optional
object, `title` optional non-empty, `preamble` any string, `sections` items `{title (non-empty), content}`; no `id`.
`contracts/packs/README.md` counts 19 schemas.

### D4. Listing, read, assembly, fingerprint, validate mode (IC-03)

- `PackFileSet.Listing` gains `Optional<String> document`. `markdownHolder` routes `pack-document` to it; any other
  non-companion Markdown kind still throws `notStored`.
- `packLevelTail`: presets, then `pack.md` when listed, then the manifest. The javadocs of the digest order follow.
- `FilesystemPackStore.PackRead` gains `Optional<Stored<MarkdownDocument>> document`; `readDocument` reads the text
  (a non-UTF-8 file is a violation), `MarkdownCodec.parse` (a refusal is a parse violation naming the line), and
  `PackSchemas.validateMarkdown("pack-document", …)` (a breach is a schema violation), all on `pack.md`.
  `writePackDocument(id, MarkdownDocument)` writes `MarkdownCodec.write(document)` atomically in place.
- `DomainRepositoryService.assembly` maps the stored document into the builder; `writePackDocument(pack,
  PackDocument)` writes in place for the gate.
- `FileSystemPackInspector`: the Markdown walk validates a `pack-document` file through the codec and the schema, the
  same refusals as the load.

### D5. WholePackStore (IC-04)

Load: `pack.md` is modelled when the aggregate holds the document. Write: the document is written before the
manifest in its canonical form; a document the codec cannot write refuses the write (no partial target, as today).

### D6. Gate (IC-05)

`DomainRoundTrip`: `PACK_DOCUMENT = "pack-document"` in `MODELLED`; `writeAll` writes the document in place; an
outcome `packDocument(path, pack, assemblyFailure, reloaded)` mirrors `preset`: refused with the store's exact reasons
when the load recorded violations on `pack.md`, `Written("pack.md")` when the reloaded pack holds a document, else
refused. `WholePackRoundTripTest`: `REQUIRED_KINDS` and `bound` gain `pack-document`; `withPayloads` keeps the
document. `expected-red.yaml` loses `other:pack.md` only. `WholePackExpectationTest`, `DomainRoundTripTest`,
`PackFileKindTest`, `PackSchemasTest` and `WholePackOpenPacksRoundTripTest` follow (research, "Gate and ledger
changes").

### D7. Docs (IC-06)

ADR-0042 (dated amendment; decisions 8 done, 9 tail includes `pack.md`, 10 "three slots", per-kind row, status
qualifiers), ADR-0037 (pending qualifiers lifted), ADR-0040 (the first stored `markdown` files rule; frontmatter
equality ruling), `docs/architecture/logical-model.md`, a changelog entry, the `#260` javadoc mentions in
`ContentForm`, `PackFileKind`, `MarkdownFilesRuleTest`, `WholePackCensus`.

## Implementation Concern Map

| IC | Concern | Requirements |
| --- | --- | --- |
| IC-01 | Domain `PackDocument`, `Pack` slot, infra mapper | FR-002 |
| IC-02 | `*/pack.md` rule and `pack-document` structure schema | FR-001 |
| IC-03 | Store listing, read, write, assembly, fingerprint, validate mode | FR-003, FR-005, FR-006, NFR-002 |
| IC-04 | `WholePackStore` load and write | FR-004 |
| IC-05 | Gate: `DomainRoundTrip`, `REQUIRED_KINDS`, `withPayloads`, expected-red, lossy writer, edge-case tests | FR-007-FR-010 |
| IC-06 | ADRs, changelog, logical model | FR-011 |

IC-02 and IC-03 must land together (research R3): the rule without the store path breaks every listing of the
built-in pack.

## Risks

- **Fingerprint change.** The built-in pack's digest list gains `pack.md`; tests that pin the built-in pack's
  fingerprint or ETag value (if any) move once, by design (ADR-0042 decision 9). A fixture without `pack.md` must not
  move (NFR-002).
- **Sibling overlap (#194 PR-B).** Edits to shared files are append-only (C-004); the `markdownHolder` routing is by
  kind, so PR-B adds a branch beside it.
- **Frontmatter equality** is a ruling without an operator review yet: logged as an open review item in the PR.

## Project Structure

```
contracts/packs/file-kinds.yaml, contracts/packs/schemas/pack-document.schema.json, contracts/packs/README.md
service/packs/src/main/java/ai/speckitty/charter/packs/domain/{PackDocument,Pack}.java
service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/{PackDocumentMarkdown,PackFileSet,
  FilesystemPackStore,DomainRepositoryService,WholePackStore,FileSystemPackInspector}.java
service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/{PackDocumentRoundTripTest,DomainRoundTrip,
  WholePackRoundTripTest,...}.java, service/packs/src/test/resources/whole-pack/expected-red.yaml
docs/adr/0037-*.md, 0040-*.md, 0042-*.md, docs/architecture/logical-model.md, docs/changelog/2026-10-30-*.md
```
