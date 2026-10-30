# #260 gate research (qa-strategist-quinn lens, read-only)

Paths are relative to `service/packs/src/test/java/.../infra/storage/` (T) or `service/packs/src/main/java/.../infra/storage/` (M) unless they are absolute.

## 1. Gates and lists that #260 touches

- **`expected-red.yaml:11-13`**: the `other:pack.md` kind entry (#260). Remove it. If it is left in, `WholePackExpectation.java:361-363` reports `STALE_BASELINE kind other:pack.md: no file has this kind; remove it`. Do not add a `kinds:` entry for `pack-document`. A modelled kind there is `BASELINED_MODELLED_KIND` (:357-360). `other:missions` (:8) stays.
- **`WholePackExpectationTest.java:850-871`** (`TheCensus.filesTakeTheirSchemaKindOrWhereTheyLive`): this test runs the real `WholePackCensus.of`. Change `"pack.md", "other:pack.md"` (:866) to `"pack.md", "pack-document"`. Keep `pack.md` in the sample, so the test now proves the new rule. `other:toolguides` and `other:missions` stay as the honest `other:` examples.
- **`WholePackCensus.java:21-24, 105-115`**: the kind is the `file-kinds.yaml` files-rule schema name, otherwise `other:<first segment>`. Only the Javadoc example `{@code other:pack.md}` (:24) needs updating, to `other:README.md` for example. The code needs no change.
- **`WholePackRoundTripTest.java`**:
  - `REQUIRED_KINDS` (:62-76): add `"pack-document" // #260`.
  - The `bound` set (:109-115): add `DomainRoundTrip.PACK_DOCUMENT`. The assertion at :117-120 is `containsAll(REQUIRED_KINDS).isEqualTo(bound)`, so the adapter can model neither fewer kinds nor any extra kind.
  - `withPayloads` (:366-389) rebuilds the `Pack` and claims "nothing else moves". It must also copy the new pack-document slot.
  - The `MARKDOWN` branch of `observe` (:557-565) already compares by `compareMarkdown`. Nothing to change there.
- **`DomainRoundTrip.java:66-88, 146-169`**: add a `PACK_DOCUMENT` constant, add it to `MODELLED`, and add an `else if` branch that writes in place and pairs by path, like `PRESET`. A modelled file without an outcome goes red ("gave no outcome", `WholePackExpectation.java:274-276`).
- **`DomainRoundTripTest.java:271-292`** (`theAdapterModelsEveryBoundKindAndTheTwoPackDocuments`): add `pack-document` to both hard-coded sets. The test name becomes stale, because there are now three pack documents.
- **`PackFileKindTest.java`**:
  - `MAPPED` (:30-49): add `Map.entry("core/pack.md", "pack-document")`. Without it, `everyFilesRuleMapsItsSamplePathToItsSchema` fails on `containsExactlyInAnyOrder` (:86-88). Its `.md` check (:82-84) expects `MARKDOWN`, which is correct.
  - `noSamplePathIsMatchedByTwoRules` (:195-201) then covers `core/pack.md`.
  - `theVocabularyListsAreExposed`: `fileRules().hasSize(19)` (:219) becomes 20. The `filteredOn(!toolguide-guide)` YAML-only check (:220-224) must also exclude `pack-document`.
  - Optionally, add `"a/b/pack.md"` to `anUnknownFileIsUnclassified` (:181-189), as `"core/deep/er/pack.yaml"` already does for `pack.yaml`.
- **`PackSchemasTest.java:62-63`**: `thereAreNineteenBundledSchemas` becomes 20. `everySchemaFileOfTheRepositoryIsListedInFileKinds` (:100) forces the files rule together with the schema.
- **`VocabularyEqualityTest.java:151-180`**: a schema with `properties.id` is expected as an inspector artifact kind. The `pack-document` schema has no `id` property ({title, preamble, sections}), so it must stay out.
- **`SchemaKeywordCoverageTest`**: the new schema may only use keywords that already have a vocabulary row (copy `toolguide-guide.schema.json`'s keywords).
- **`MarkdownFilesRuleTest.java:25, 92-117`**: `notes` still refuses with "(#260)". Keep these refusal tests: they now prove that any non-companion, non-pack-document markdown file still fails loudly. Update the comment "No store consumer exists yet (#260 adds the first)".
- **Production blockers**:
  - `PackFileSet.java:349-356` `markdownHolder` throws `notStored` for every non-companion markdown file. As soon as `*/pack.md` is a markdown rule, listing the built-in pack throws, unless this is wired first. The message is at :368-373.
  - `FileSystemPackInspector.java:226-232` validate walk reads markdown only when `isCompanionStructure`. `pack.md` must be validated against its structure schema there, not silently skipped. Also see `readsAsYaml` (:180-186).
  - `PackFileKind.java:50-52` and `ContentForm.java:15-18` Javadoc mention #260.
- **`WholePackOpenPacksRoundTripTest.java:189-220`**: `roundTrip()` and `meaning()` (:83-104) compare each pack-level file one by one. Add the pack document, or the seam test silently skips it.
- **`MarkdownCorpusTest.java:38-44, 86`**: `PINNED_COUNT = 72` ("24 guides, pack.md and 47 ...") already includes `glob:packs/*/pack.md`, and its "title and no sections (pack.md)" assertion already holds. No change.
- **`ProcedureAndGuideCorpusTest:94`** pins 39/24/24. It is unaffected.
- **Docs that go stale**:
  - `docs/adr/0042-...md:293-301` (decision 8; "`other:pack.md` stays on the expected-red list") and :313 (the fingerprint tail order is presets, then `pack.md`, then manifest).
  - `docs/architecture/logical-model.md:174` ("Not modelled yet").
  - `docs/adr/0036-...md:352`.
  - `docs/adr/0040-...md:63,116,125`.
  - `docs/adr/0037-...md:91,163`.
  - There is no separate schema ledger. `contracts/packs/file-kinds.yaml` is the single authority: add `- match: "*/pack.md"`, `schema: pack-document`, `content_form: markdown` after :143-145, plus `contracts/packs/schemas/pack-document.schema.json`.
- **Gates that are not affected**: `exclusions.yaml` (`**/README.md` does not match `pack.md`) and `known-failures.yaml`.

## 2. The "lossy writer goes red beside a faithful green" pattern to mirror

- **`WholePackRoundTripTest.aLossyByteWriterTurnsEveryAssetFileAndTemplateRed`** (:188-237). This is the best template. It runs the real corpus twice: a faithful run, `observe(new DomainRoundTrip(yaml), …)`, and a lossy run, `new DomainRoundTrip(yaml, beforeSave)`, where `UnaryOperator<Pack>` changes the loaded pack, so the loss travels the production write path. It asserts the faithful run is empty (green) and the lossy run has an exact red string at each path.
- **`ToolguideGuideTest.theFixtureIsGreenThroughTheGate`** (:170-182) with **`oneChangedCharacterInAGuideSectionIsReported`** (:184-206). This is the markdown analogue: `gate(source, run, beforeSave)` (:211-233), with a red that starts with `sections[3].content: `, while the owner's file stays green.
- **`MarkdownCompanionTest.oneChangedCharacterInASectionIsAGateDifferenceAtThatSection`** (:264-279).
- **`WholePackRoundTripTest.aLossyWriterTurnsTheRealCorpusRed`** (:409-447). This is the `Proxy` `SerializationStrategy` that drops `purpose`, with `faithful.get(path).isEmpty()` beside it.
- Also: `PresetRoundTripTest.aWriterThatDropsMustHaveGoesRedAtTheItem` (:76-94), `PackManifestRoundTripTest.aWriterThatKeepsStaleHashesGoesRed` (:231-249) and `DomainRoundTripTest.aLossyWriterIsVisibleInTheWrittenFile` (:251-259).
- **Suggested method for #260**: `aLossyPackDocumentWriterGoesRedAtPackMdBesideAFaithfulGreenRun`. Use `beforeSave` to replace the preamble (for example, drop the last paragraph), then assert:
  - `faithful.get("pack.md")` is empty;
  - `lossy.get("pack.md")` has the value `"preamble: source …, written …"` (`MeaningComparator.java:151-155` names the `title` and `preamble` paths);
  - every other path is unchanged.

## 3. How a file gets its census kind; REQUIRED_KINDS

- `WholePackCensus.kindOf` (:105-115) checks in order: companion owner, then `PackFileKind.bundled().classify(pack/path)` Mapped kind (the schema name), then `other:<segment>`. `pack.md` moves to `pack-document` as soon as `file-kinds.yaml` gains `*/pack.md` → `schema: pack-document`. The form comes from `contentFormOf` (:210-220), which returns MARKDOWN, so `observe` uses `compareMarkdown`.
- `REQUIRED_KINDS` is not in `DomainRoundTrip`. It is in `WholePackRoundTripTest.java:62-76`, a 14-entry floor, checked at :117-120 together with the exact `bound` set.

## 4. Gradle

- `verifyAll` (`build.gradle.kts:2-8`) runs every subproject's `check` plus build-logic's `check`.
- `:packs:check` depends on `openPacksRoundTrip` (`service/packs/build.gradle.kts:172`, tags `open-packs`, excluding `whole-pack`; :102-127).
- `:packs:wholePackRoundTrip` (:134-155) runs tag `whole-pack` only (`WholePackRoundTripTest`). It is not part of `check`. CI runs it in `.github/workflows/whole-pack.yml`. Its report is `build/reports/whole-pack/report.md`.
- Count pins:
  - `MarkdownCorpusTest` 72 (:38-39);
  - asset-payload 7 GREEN, template 2, asset 6 KNOWN_FAILURE (`WholePackRoundTripTest:139-141`);
  - bytes 7+2 (:223);
  - `PackSchemasTest` 19;
  - `PackFileKindTest` `fileRules` 19.

## 5. pack.md files at the pin

- Open-packs is at HEAD `5df92a5d…`, which equals `openPacksRef` (`gradle.properties:9`). It has exactly one pack.md, `packs/built-in/pack.md` (714 B, 14 lines). The other packs (cave-kitty, event-driven, internal, qa) have none.
- This repository has no `pack.md` (no vendored packs or fixtures), so no fixture fingerprint changes. The built-in pack's fingerprint and ETag change once, through ADR-0042 §9.
- The built-in file:
  - is UTF-8 with an em-dash, has no BOM and no CR, and ends with a single LF;
  - has no frontmatter;
  - is an H1 `# Built-in Charter Pack` plus a preamble, with no H2 sections.
- `MarkdownCorpusTest` already proves it parses and is byte-canonical. `pack-manifest.yaml` does not list `pack.md`.
- **Schema implication**: the `pack-document` schema must allow a non-empty preamble and no sections. `toolguide-guide` requires `preamble: ""`, so do not reuse it.
