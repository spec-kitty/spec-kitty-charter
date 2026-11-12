# Research: Slice B, real packs register as they are

Sources: the operator rulings of 2026-11-11 (#466 comment 6805477785), four read-only brownfield scouts run on `main` at
8c42f52 (2026-11-11), and a direct check of `WholePackStore.loadStaged`. Paths abbreviate
`service/packs/src/main/java/ai/speckitty/charter/packs/` as **M/** and its `src/test/java/...` twin as **T/**;
`S/` is `M/infra/storage/`.

## Decisions

Each is a call the mission makes; the plan-point squad and the operator review them.

| # | Decision | Rationale | Alternatives rejected |
|---|---|---|---|
| D1 | Registration refuses no excluded file (all exclusions, not only Markdown). | #451 lists `missions/qa/templates/README.md` and `plan-field-declaration.yaml`, both already excluded, among the refusals; today `loadStaged` refuses every file outside the modelled set (`S/WholePackStore.java:300-332`, `unmodelled` at :790 ignores classification). The ruling defines "excluded" as "not held, not served, not refused". | Only Markdown exclusions: #451's acceptance would still fail. |
| D2 | Unmodelled Markdown is an `ignored` fallback rule (`**/*.md`), consulted after `payloads` and after companion claims; a new classification `Ignored`. | An `exclusions` glob comes before `retired` and `payloads`, so it would turn retired `.md` files into exclusions (RetiredFilesTest:58-64), drop `.md` assets and templates from the load, overlap every Markdown `files` rule, and make companion resolution refuse skill bodies (`S/Companions.java:178-180`). | A `**/*.md` exclusion; a hard-coded `.md` check in Java (the table is the single authority). |
| D3 | `README.md` replaces `pack.md` in the `files` rule, the digest tail position and the gate model; `pack.md`, `PROVENANCE.md`, `org-charter.yaml` are root-anchored `retired` rules. | Ruling 1 and 2; retired rules are already refused by name in load, validate mode, registration, companions and census. | Excluding the old files (they would vanish silently). |
| D4 | JSON `body` is the canonical Markdown below the H1: preamble and sections, no frontmatter, no title. `title` is omitted when there is no H1. | Ruling: `{title: <H1>, body: <markdown>}`; repeating the H1 in `body` would duplicate it; the raw form keeps frontmatter. | `body` = the whole file. |
| D5 | No `Accept`, `*/*` or `application/*` answer JSON; `text/markdown` answers Markdown; anything else `406` as a code-less Problem. | JSON is the service's default form; the ruling names 406 for unsupported types. | Markdown as default. |
| D6 | Refusal code `pack_document_not_found` (404). | "Pack document" is the model's term (ADR-0042); a dedicated code per ruling. | `doc_not_found`. |
| D7 | `active_charter` is refused by the closed descriptor's unknown-key check, not by a rule of its own. | It is a key, not a file; the closed schema already names unknown keys. | A retired-keys list (a second mechanism). |
| D8 | Readers resolve a pack directory through one resolver that falls back to the single `.pack-old-*` aside, with a bounded re-read when the aside vanishes; the listing does not wait on a held lock. | Ruling 3; the aside is hidden and deleted right after the second rename (`S/PackCommitStore.java:113`), so a resolved aside can vanish; the listing's `READ_WAIT` of 10 s (`S/StorageRootLocations.java:75,222`) is a read-side lock wait. | A read/write lock (ruled out); a test-only retry (ruled out). |
| D9 | The doc read mirrors `getPack`'s headers: `ETag` = pack revision, `Charter-Source-Fingerprint` required; no 304 declared. | ADR-0071 and the `fingerprint-on-verified-reads` lint rule; no pack read declares 304 today. | Declaring 304 (a separate decision for all pack reads). |

## A. Pack document and classification (IC-01)

- **Constants and codec:** `S/PackDocumentMarkdown.java:24` `FILE = "pack.md"`, `:27` `KIND = "pack-document"`, `read`
  :39, `toDomain` :59, `toMarkdown` :75, `toWritableText` :96. Domain `M/domain/PackDocument.java:36`;
  `M/domain/Pack.java:58,333,497`.
- **Rule:** `contracts/packs/file-kinds.yaml:170-174`; header comments :31-56 (text form at :31-37 for #432).
- **Digest:** `S/PackFileSet.java:376-404` (one walk; `markdownHolder` :427-436; at most one document :402); tail
  `packLevelTail` :307-324 = presets, document, `pack-manifest.yaml`, `policy.yaml`; retired files after the tail :303.
- **Load/write:** `S/FilesystemPackStore.java:389-409` (read), :857-859 (write); `S/WholePackStore.java:269-271`
  (modelled), :888-895 (write); `S/DomainRepositoryService.java:338-340,865-867,1028`.
- **Validate mode:** `S/FileSystemPackInspector.java:345-383` (Excluded YAML listed with `exclusionClass`, Unclassified
  and Retired skipped, Markdown read only when Mapped; retired reported from `PackFileSet.listing`); `readDocument`
  :432-437.
- **Classification:** `S/PackFileKind.java:324-339` (files, exclusions, retired, then payloads, else Unclassified);
  `ruleAt` :342-355; `classifyFolded` :320 (companions, case-folded). Skill bodies are TEXT companions
  (`S/SkillBinding.java:49-54`) accepted only when Unclassified (`S/Companions.java:176-197`).
- **Unmodelled:** `S/WholePackStore.java:300-332` (`loadStaged` refuses each unmodelled file), :790-806.
- **Gate:** `T/infra/storage/DomainRoundTrip.java:91,107,194-197`; `WholePackRoundTripTest.java:79,149-150,212-322`;
  `WholePackCensus.java:125-135`; `service/packs/src/test/resources/whole-pack/expected-red.yaml` (only `kind: policy`).
- **Tests that name `pack.md`:** PackDocumentRoundTripTest, PackFileSetTest:155-184, PackDigestFixtureTest:67-71 (pinned
  FINGERPRINT; fixture `src/test/resources/digest-baseline/fixture/pack.md`), PackFileSetExactnessTest:174-182,328,482,
  GeneratedPackTree:45-47,479,592-596,734, PackFileKindTest:59,205,243-253 (`fileRules().hasSize(28)`),
  KindBindingsTest:215-222, CompanionCaseFoldTest:40,63, WholePackStoreTest:320-330 (root README as unmodelled),
  :607-651 (message pins `rule '*/pack.md'`), CompanionFilesTest:170-185, WholePackExpectationTest:49,840,852,
  WholePackExamplesRoundTripTest:218-223, MarkdownFilesRuleTest:25, markdown/MarkdownCorpusTest:28,37,78,
  ReadOnlyPackSaveTest:142-144, CompanionDigestTest:133,241, domain/PackDocumentTest,
  `src/test/resources/markdown-rule-fixtures/pack-document.schema.json` (byte copy of the schema).
- **RetiredFilesTest** pins: rules equal `RetiredFileFixture.RULES` exactly (:46-54), no exclusion matches a retired
  sample (:58-64), the one README overlap in `missions/mission-steps/` (:69-87), namesakes (:91-96), load, validate,
  companion and census behavior (:166-249). `RetiredFileFixture.java:19-50,91-113` must gain the new rules, samples and
  namesakes.
- **Example packs:** `service/packs/src/testFixtures/resources/example-packs/` — `built-in` (pack.md, presets),
  `org-base` (pack.md, presets), `project` (presets, `active_charter: charter.yaml` in pack.yaml:13).
- **Docs:** `contracts/packs/README.md:6,17,26,52-53`; `docs/architecture/logical-model.md:228`;
  `docs/architecture/pack-schemas/README.md:37`, `pack-document.md:8,16`; `descriptor-schemas.md:78`.

## B. org-charter.yaml and active_charter (IC-02)

- **Contracts:** `file-kinds.yaml:127-128`; `schemas/org-charter.schema.json` (delete; `PackSchemasTest:122-133`
  requires schemas on disk = schemas of `files` rules); `pack-descriptor.schema.json:5,125` (closed schema).
- **Main code:** `service/kernel/.../domain/PackPaths.java:9-38` (component), `PackDescriptor.java:19,115` (javadoc);
  `S/PackDescriptorReader.java:69`; `S/PackDescriptorDso.java:14-151`; `S/YamlSerializationStrategy.java:114`;
  `M/domain/PackPolicy.java:98` (DESCRIPTOR_KEYS), :935,963 (javadoc), :1048-1050 (`codeFor` branch);
  `M/domain/PackFindingCode.java:25-26`.
- **Tests and fixtures:** kernel `ChecksTest.java:14-48`; `ValueObjectsTest.java:57-59`; `PackDescriptorReaderTest`
  :70-74,192-213,250-263 (exact property list); `PackRoundTripTest:42,158-159,213,241` with
  `src/test/resources/roundtrip-fixtures/pack-a/pack.yaml:7`; `DomainRepositoryServiceTest:147`;
  `load-fixtures/descriptor-all-declared-keys-are-not-unknown/files/pack.yaml:7`; example `project/pack.yaml:13`;
  `built-in/glossary_packs/spec-kitty-core.glossary-pack.yaml:998`; `ExamplePackCoverageTest:120`;
  `PackPolicyVocabularyTest:57,97-106`; `PackValidationTest:108-137`; `PackSchemasTest:446-451`; `PackFileKindTest:35`;
  `FileSystemPackInspectorTest:264-311,503`; `PackFileSetTest:43`; `GeneratedPackTree:61,166,731` (DECOY_ORG_CHARTER);
  `PackPolicyTest:337-355`.
- **Planted:** `src/test/resources/planted/c10-org-charter-schema-version-quoted/`, `planted/core-verdicts.json:476`,
  `scripts/packs/core_verdicts.py:57` (delete together).
- **Scenarios:** `active_charter: org-charter.yaml` in the Background `pack.yaml` of directives.feature:20,
  toolguides:22, styleguides:21, paradigms:21, relationships:18, journeys 01:17, 02:17, 04:17, 05:17, 06:18, 09:17.
  journey-04 :4-6 description and its `@WIP` scenarios :31-42; `tests/scenarios/charter/PROMISES.md:34`.
- **Docs:** `docs/architecture/logical-model.md:81-84,110,114,329,460-469`; `descriptors.md:85,107,185+`;
  `pack-schemas/org-charter.md` (delete) and `pack-schemas/README.md:34`; `pack-registration-distribution.md:195`,
  `overview.md:57`, `resolution-model.md:41`, `docs/context/domain.md:69`, `system-context.md:53`; diagrams
  `descriptor-governance.puml`, `descriptor-kinds-overview.puml`, `pack-write-model.puml` and their `.svg`;
  `docs/context/user-journeys/04-org-house-rules.md:78`, `05-remove-python.md:89`; ADR-0036:243-249 (table row);
  `docs/conformance/divergences.yaml:185,187,352,364` and `divergence-register.md`; `epic-186-sanitized-shape-follow-ups.md:51,57`;
  `site/.contextive/definitions.yaml:53,173,265,397` (generated glossary pages follow);
  `scripts/usability/README.md:90`, `service-usability-run.sh:419-432`.
- **Enforcement today (for the how-to):** presets `presets/<id>.yaml` (`contracts/packs/schemas/preset.schema.json`),
  items `{id, must_have: true}`; `M/domain/RequiredFloor.java` over the closure (`application/ClosureQueries.java:201`);
  a denial of a floor item is `409 artifact_required` (`activation-register.feature:190-200`); applying a preset is
  `PUT /activations/{activationId}/presets/{presetId}`; `policy.yaml` (ADR-0063, `policy.schema.json`) with
  `kind_defaults` and `artifacts` levels `open|extendable|locked`. Existing guide: `docs/guides/compose-packs.md`.

## C. The doc read (IC-03)

- **Contract:** `contracts/charter/openapi.yaml:85-89` (paths); `paths/packs_packId.yaml:1-11` (parameter),
  :130-179 (`getPack`, its headers and responses); `paths/openapi.yaml:28-54` (a non-JSON 200); 413/415 Problem pattern
  `packs_packId.yaml:100-125`; `schemas/PackRegistration.yaml:17,109-111`; `schemas/Links.yaml`;
  `examples/PackRegistration.stored.yaml:9-14`; `responses/PackNotFound.yaml`; `schemas/CharterRefusalCode.yaml`
  (enum :197-258, `x-refusal` :259-820); `changes/README.md:14-38`; latest fragment
  `changes/2026-11-11-procedures-are-served.md`.
- **Checks:** `contracts/build.gradle.kts` `lintContract` :818, `checkClosedResponseObjects` :901,
  `checkRefusalExamples` :912, `checkContractCompatibility` :849, `checkContractChangelogFragments` :948; lint rules
  `contracts/lint/ruleset.yaml` headers :179, fingerprint :201, links :238, known relations :257 (enum :270-310), link
  examples :313.
- **Service:** `M/infra/api/PackRegistrationController.java:48-50,100-107`; `PackRegistrationMapper.java:98,129-145`;
  `Relation.java:9-41`; `Hypermedia.java`; `M/application/PackSummaryQuery.java:85-126`;
  `M/application/ProcedureReadService.java:58-63,94-99` (body + revision in one read); `ConsistentRead`;
  `service/kernel/.../RefusalCode.java:7,39,256-275`; `service/web-transport/.../RefusalHandler.java:84-100,315-386`;
  `ProblemHandler.java:26,92-97` (406); `InfoController.java:98-107` and `ContractDocumentETag.java:12-20` (non-JSON body,
  Spring's implicit 304).
- **Scenarios:** runner `service/deployable/src/test/java/.../bdd/CharterContractSuite.java`; steps
  `CharterSteps.java:255` (GET accepting), :265 (JSON body only — a raw-text body step is new and goes in
  `scenarios/README.md`), :312 header, :372 problem code, :428/:447 links; `ContractConformance.java:250-292`;
  `OperationCoverage` (every 2xx exercised); `RelationSetParityTest.java:40-45`; `bdd/RelationTable.java`.
- **Docs:** `docs/development/hypermedia-hateoas.md:114-136` (relation table); `docs/api/service-contract.md:283`.
- **ArchUnit:** `service/deployable/src/test/java/.../HexagonalArchitectureTest.java` — ports in domain, implementations
  in `infra.storage`, generated API types only in `infra.api`/web.

## D. Swap window (IC-04)

- `S/PackCommitStore.java:86-114` (`DirectoryRename`, `MOVE_ASIDE`; aside `<name>.pack-old-<unique>`; aside hidden and
  deleted at :113), :162,176 (injectable constructors), :625-718 (`missingLiveDirectory`, `restoreInterruptedSwap`,
  `asidesOf`, `hideStaleAsides`), :740-768 (`resolve`).
- Lock-free readers: `S/CommitStorePackWriteStore.java:163-181,184,285,337-360`; `S/PackFileSet.java:482-492,495-530,580`;
  `S/FileSystemPackSources.java:94-112`; `S/WholePackStore.java:790-806`; `S/FileSystemPackInspector.java:705-715`;
  `S/FilesystemPackStore.java:773-779,1351-1362`.
- Listing: `S/StorageRootLocations.java:72,75,110-149,163-268,374-391,428-431`.
- Tests: `T/infra/storage/SlowSwapCommitStores.java:26-65` (the hook); `T/application/RegistrationConcurrencyTest.java:282-373`
  (`putUntilCommitted` reads `currentRevision` outside the lock at :337); `PackRegistrationServiceTest.Rig` (~972-1087);
  `InterruptedCommitRepairTest.java:154` (asserts the read waits — rewritten), :185,245,266,287,347;
  `PackCommitStoreTest.java:211,475,516,636-642`; `LinkedPackCommitTest.java:143,200`; `StorageRootLocationsTest.java:76-81`.

## E. Record and drafts (IC-05)

ADR-0042 (Status amendments :59-106, decision 8 :350-359, decision 9 :361-377, per-kind table :393-402, follow-ups
:502-520); ADR-0036 :243-249,318,392; ADR-0031 :52-101; `docs/plans/next-slices-2026-11-11.md` (PR #498);
`docs/development/issue-tracker.md` (PR #491). Open-packs drafts go to `research/`.
