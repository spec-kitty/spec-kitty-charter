# Research: round-trip `pack.md` through the markdown content form (#260)

Pre-spec research squad, read-only, run on 2026-10-30 against `origin/main` at `feeff0a` (#194's first PR, the
`markdown` content form, merged). Four lenses, each profile-loaded with `spec-kitty agent profile show <id>`:

| Lens | Profile | Report |
| --- | --- | --- |
| Files, contracts, responsibility map, test seams | researcher-robbie | [squad/code.md](research/squad/code.md) |
| Invariants and governing ADRs | architect-alphonso | [squad/adr.md](research/squad/adr.md) |
| Gates, expected-red and known-failure ledgers | qa-strategist-quinn | [squad/gates.md](research/squad/gates.md) |
| Sibling runs, 90-day churn, CLI sequence | planner-priti | [squad/siblings.md](research/squad/siblings.md) |

Baseline: `./gradlew verifyAll` on `feeff0a` passed (Java 25, 3 m 53 s). No baseline red to classify.

## Findings that shape the spec

- **R1. The ADR numbers in the issue are off by one.** The decisions the issue calls "ADR-0043 decisions 8, 9, 10"
  are ADR-0042's (`docs/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest.md`, decisions 8 at
  :293-301, 9 at :303-318, 10 at :320-332). ADR-0043 is #194's record and holds no `pack.md` pending qualifier. The
  mix-up comes from `DM-01M60HM8BV7CE17HYE1QS829K4`, which reserved 0043 for #195 before the two records swapped
  numbers. This mission amends ADR-0042 and ADR-0037 (plus ADR-0040 and ADR-0036, below); the PR says so.
- **R2. The domain cannot hold `MarkdownDocument`.** `HexagonalArchitectureTest` forbids `packs.domain` from
  depending on `..infra..`, and `MarkdownDocument` is infra. #194 met the same constraint for the toolguide guide: a
  domain value (`Guide`) and an infra mapper (`GuideMarkdown`). "A `Pack` document slot typed as #194's markdown value"
  is therefore read as a domain value with the markdown model's shape (frontmatter, title, preamble, sections) and an
  infra mapper to and from `MarkdownDocument`.
- **R3. The rule cannot land before the store path.** `PackFileSet.markdownHolder` (`PackFileSet.java:349-356`) throws
  `notStored` for any `markdown` file that is not a companion, and `FileSystemPackInspector` (validate mode) reads
  Markdown only as a companion. A `*/pack.md` rule without the store path breaks every listing of the built-in pack.
  The rule, the schema, the listing slot, the store read, validate mode and the fingerprint land in one change.
- **R4. `pack.md` needs its own structure schema.** A `markdown` `files` rule names a structure schema (ADR-0040
  decision 1, as amended). `toolguide-guide` pins the preamble to `""` and forbids frontmatter, so it cannot serve. A
  new `contracts/packs/schemas/pack-document.schema.json` is needed; it must declare no `id` property
  (`VocabularyEqualityTest` reads an `id` as an artifact kind) and use only covered keywords
  (`SchemaKeywordCoverageTest`).
- **R5. #194's rule for the edge cases** (ADR-0040 decision 3 as amended; `MarkdownCodec`): CRLF and a byte order mark
  are refused at their line, never normalized (ADR-0036 decision 5: a bad shape is a pack inconsistency, fixed in
  open-packs); a missing final newline is the same model and is written back canonically; an empty file is the empty
  document; frontmatter compares as loaded YAML (`Map.equals`).
- **R6. Frontmatter equality is #260's to settle.** ADR-0040 leaves "full ADR-0036 numeric and empty-versus-absent
  equality of the frontmatter" to #260. The gate compares the source and the written file through the same reader,
  and the writer emits the loaded values, so `Map.equals` on the loaded values decides a round trip; the built-in
  `pack.md` has no frontmatter. Decided in the spec, logged as an open review item.
- **R7. The source at the pin.** Only `spec-kitty-open-packs/packs/built-in/pack.md` exists (714 B, UTF-8, LF, one
  final newline, no frontmatter, an H1, a three-paragraph preamble, no H2). `MarkdownCorpusTest` already reads and
  writes it back byte for byte. `pack-manifest.yaml` does not list it. The built-in pack's fingerprint (and ETag)
  changes once when it joins the digest (ADR-0042 decision 9); no repo fixture holds a `pack.md`.

## Gate and ledger changes (kept honest, never loosened)

- `expected-red.yaml`: delete only the `other:pack.md` block; leave `other:missions` (#194 PR-B's). A stale entry would
  be `STALE_BASELINE`; a `pack-document` entry would be `BASELINED_MODELLED_KIND`.
- `WholePackRoundTripTest`: `REQUIRED_KINDS` and the exact `bound` set gain `pack-document`; `withPayloads` keeps the
  document.
- `DomainRoundTrip`: a `PACK_DOCUMENT` kind in `MODELLED` and a dispatch branch that writes in place and pairs by path,
  as presets do. `DomainRoundTripTest` pins the modelled set twice.
- `WholePackExpectationTest:866`: `pack.md` classifies as `pack-document`.
- `PackFileKindTest`: a `MAPPED` sample, the files-rule count (19 → 20) and the YAML-only filter.
  `PackSchemasTest`: 19 → 20 schemas.
- `WholePackOpenPacksRoundTripTest` lists pack-level files explicitly; it gains the document.
- `MarkdownFilesRuleTest`'s `notStored` refusals stay: they now prove that any other non-companion Markdown file still
  fails loudly.
- Lossy-writer pattern to mirror: `WholePackRoundTripTest.aLossyByteWriterTurnsEveryAssetFileAndTemplateRed` (a
  `beforeSave` that travels the production write path, beside a faithful run).

## Siblings

- **#194 PR-B** (mission types) edits `file-kinds.yaml` (mission exclusions, new rules), `expected-red.yaml`
  (`other:missions`, directly above `other:pack.md`) and `REQUIRED_KINDS`. This mission appends its rule after the
  toolguide-guide rule, deletes only its own expected-red block and appends `pack-document` last in `REQUIRED_KINDS`.
  PR-B may also need a store path for non-companion Markdown (mission `prompt.md`, `guidelines.md`); this mission's
  path is routed by kind (`pack-document`) and keeps `notStored` for every other kind, so PR-B adds its own branch
  beside it. Flagged to the orchestrator.
- **#229** (hosted config) does not touch the packs surface.
- The #195 mission (`pack-level-files-round-trip-01M60H1A`) is a terminal record (WP08 canceled, "split off to
  #260"); it is cited, not edited.

## Churn

All recent churn on the surface is #189-#195, 2026-10-20 to 2026-10-29. Hottest: `PackFileSet.java` (17 commits),
`Pack.java` (13), `WholePackStore.java` (11), `DomainRoundTrip.java` and `expected-red.yaml` (9 each).
