# #260 responsibility map: pack.md through the markdown form (researcher-robbie, read-only)

Paths are relative to service/packs/src/{main,test}/java/ai/speckitty/charter/packs/. Storage classes are in infra/storage/.

## The source file
Only `spec-kitty-open-packs/packs/built-in/pack.md` exists. cave-kitty, event-driven and the repo packs `packs/internal` and `packs/qa` have none. It is 14 lines, LF only (0 CR), ends in a single `\n`, has **no frontmatter**, has an H1 `# Built-in Charter Pack`, has **no H2** and has a 3-paragraph preamble (it contains a UTF-8 em dash). Its codec model is title plus preamble with 0 sections. MarkdownCorpusTest (test markdown/MarkdownCorpusTest.java:43,86) already shows that it parses and that write(parse) gives back the same bytes.

## Domain/infra boundary
deployable/.../HexagonalArchitectureTest.java:74-80 says a domain ring must not depend on `..infra..`, and :139 says packs.domain may depend only on java, JSpecify, the kernel and itself. So `Pack` **cannot** hold `MarkdownDocument`, which lives in infra.storage.markdown. Guide.java does the same thing: domain `Guide(Map<GuideBlock,String>, List<GuideSection>)` holds plain strings, and infra `GuideMarkdown` converts between it and `MarkdownDocument` (toDocument :42, fromStored :88, fromDocument :114). `ToolguideDso` holds `Optional<MarkdownDocument>` (:36, :73). For #260 that means a domain value (for example `PackDocument(Optional<String> title, String preamble, List<sections>, frontmatter?)`) plus an infra mapper.

## How presets were wired, and where pack.md goes
| Concern | Preset today (file:line) | pack.md change |
|---|---|---|
| file-kinds rule | contracts/packs/file-kinds.yaml:132-135 (`*/presets/*.yaml`, then `*/pack-manifest.yaml`). Markdown example: :143-145 | Add `*/pack.md`, schema `pack-document`, `content_form: markdown`. PackSchemas.bundled() (:116-124) loads one schema per rule, so `contracts/packs/schemas/pack-document.schema.json` is also needed (model as toolguide-guide.schema.json). PackFileKind :337-350 requires a markdown rule's schema to load strictly. |
| Pack slot | Pack.java: field :54, ctor param :73, sortedBy :89, toBuilder :168, accessor :315, builder list :352, `preset()` :467, build :497. Javadoc :38-43 already names "the pack document … the third to come" | Optional single document slot: field, ctor, toBuilder, accessor, builder setter and build. `with()` :181 keeps it through toBuilder. |
| Classification | PackFileSet.Listing :80-100 (presets list). loaded() :311-332. yamlHolder :335-346 routes `PresetFiles.KIND`. **markdownHolder :352-357 throws `notStored`** for any markdown file that is not a companion. notStored :368-373 holds the "(#260)" text. Javadoc: PackFileKind.java:52, ContentForm.java:18 | Give Listing a pack-document slot and have markdownHolder route the `pack-document` kind to it instead of throwing. Keep notStored for any other markdown and for TEXT (:327). |
| Fingerprint tail | PackFileSet.digested :211-247 calls packLevelTail :254-260 (presets, then MANIFEST). Consumers: PackCommitStore.java:219, CommitStorePackWriteStore.java:74. The javadoc that describes the order is at :29-36, :176-177 and :191-194 | Insert pack.md between `loaded.presets()` and MANIFEST in packLevelTail, and update the three javadocs. |
| Store read | FilesystemPackStore.PackRead record :110-150 (presets component, plus a 3-arg convenience ctor :145). read() :297-331 calls readPresets :338-357 | Add a document component and `readPackDocument`, which runs MarkdownCodec.parse and `PackSchemas.bundled().validateMarkdown("pack-document", …)`. A failure becomes a PackElementViolation, as in readPresets. |
| Assembly | DomainRepositoryService.assembly :292-318 (preset loop :308-315, domain breach becomes a violation) | Map the document into the builder. |
| Per-file write | DomainRepositoryService.writePreset :195-204, which calls FilesystemPackStore.writePreset :589-600 | Add writePackDocument (MarkdownCodec.write). DomainRoundTrip uses it. |
| Whole-pack load/write | WholePackStore.load: modelled set :189-197 (presets :194, manifest :195-197). write: presets loop :315-321, then graphs, then putManifest last :325-326 | Add pack.md to `modelled`, so it is not counted unmodelled. In write(), `put(staging,"pack.md", utf8(MarkdownCodec.write(...)))` before the manifest. |
| Validate mode | FileSystemPackInspector.readsAsYaml :180-186 (notStored). Its markdown walk :226-233 reads companion structures only, through companion() :293-298 (MarkdownCodec.parse + validateMarkdown). ArtifactKinds.PACK_LEVEL :89 | Optional: also read and validate `pack-document` in the markdown walk so that the load and validate agree. PACK_LEVEL needs no change because the schema has no `id`. |

## Toolguide guide markdown today
- Read: Companions.read, Companions.java:~276-295 (MarkdownCodec.parse, then the structure check), called from FilesystemPackStore.java:556.
- Validate: Companions.breach (:~311-320, `validateMarkdown`), called at FilesystemPackStore.java:677 and WholePackStore.java:445. Validate mode uses FileSystemPackInspector.java:298. PackSchemas.validateMarkdown is at :227, and markdownTree is at :239.
- Write: Companions.text, which calls `MarkdownCodec.write` (Companions.java:~338). It is called at FilesystemPackStore.java:701 and WholePackStore.java:449. GuideMarkdown.java:71 also calls `MarkdownCodec.write`.
- MarkdownCodec has a public `parse(String)` (:66) and `write(MarkdownDocument)` (:91). write re-parses its own output and throws if the model differs. `read` is private (:133).

## Gate (test) changes
- DomainRoundTrip.java: constants :66-80, MODELLED :86-89, dispatch in roundTrip() :142-167 (PRESET branch :164-166), writeAll :216-235 (presets :228-229), preset outcome helper :476-516. Add `PACK_DOCUMENT="pack-document"`, a branch and a writer call.
- **REQUIRED_KINDS is in WholePackRoundTripTest.java:62-76**, not in DomainRoundTrip. Its `bound` set is at :106-113. `withPayloads` is at :368-386 and copies presets at :384, so add the document there.
- DomainRoundTripTest.java:270-291 checks the modelled-kinds set in two places, and both need `pack-document`.
- WholePackCensus.kindOf (:105-115) gives `other:pack.md` today and will give `pack-document` once the rule exists. Update WholePackExpectationTest.java:850-868, which expects `"pack.md","other:pack.md"`. **Remove expected-red.yaml:11-13** (`other:pack.md`, #260). The gate already compares markdown-form files through compareMarkdown, using WholePackCensus.contentFormOf :210 and WholePackRoundTripTest.java:556-563.
- MarkdownFilesRuleTest.java:95-117 uses its own TABLE (`notes/*.md`), so the notStored message stays valid. Its javadoc :25 says "#260 adds the first" and will be stale.

## Existing test seams and fixtures
- PresetRoundTripTest uses fixture `presets-pack` (PackFixtures).
- PackManifestRoundTripTest runs the manifest restamp over the written bytes. The manifest does not list pack.md.
- WholePackStoreTest uses PackFixtures `pack-a`.
- ToolguideGuideTest uses a PackFixtures pack that holds a guide and goes through the gate.
- WholePackRoundTripTest and WholePackOpenPacksRoundTripTest use OpenPacksCorpus with built-in plus the repo packs. Pinned counts are at :267 and :304.
- PackFileSetTest :77-160 checks digest order and the tail.
- PackFileSetExactnessTest uses GeneratedPackTree (which has no pack.md shape today, see the table at :42-47 and plant() :408). The fingerprint invariant at :100 needs a pack.md shape to cover the new digest input.
- No test resource contains a pack.md.
