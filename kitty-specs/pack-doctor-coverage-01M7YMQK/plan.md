# Implementation Plan: pack-doctor coverage

**Branch**: `kitty/mission-pack-doctor-coverage-01M7YMQK` → `feat/pack-doctor-coverage` → PR to `main` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)
**Input**: [spec.md](spec.md); research in [research/rule-coverage.md](research/rule-coverage.md) and
[research/squad-dispositions.md](research/squad-dispositions.md); rulings R-a to R-y in `decisions/`.

## Summary

Give registration and the standalone checker one answer for the same pack, under ADR-0082:

- The rules live in the domain factory (`PackRulesets`). A storage-format rule lives at the storage edge.
- The CLI and the API only transform and interface.

The checker becomes `pack-doctor`, with `validate` and `remediate`.

The work is eleven ordered work packages in one PR (R-f), plus the planning WP09:

1. #614 (`ClosureFault` replaces `CompositionFault`).
2. The rename.
3. The `anti_pattern` file kind and the example packs' anti-patterns as files (WP10, rulings R-p, R-q, R-r), which
   the preset-item rule needs.
4. The file-less `anti_pattern` node refusal (WP11, R-r).
5. The inline `anti_patterns` drop from the procedure and styleguide shapes (WP12, R-r). With WP10 and WP11 this
   delivers and closes #154.
6. The preset-item rule.
7. `manifest_stale`.
8. The `--full` domain phase (narrowed, R-s).
9. The case-only policy lock in pack-doctor's `CLOSURE` (WP13, R-y), after WP05 and before WP08's records are final.
10. The report.
11. `remediate`.
12. The ADR, the contract and the docs.

## Technical Context

**Language/Version**: Java 25 (toolchain pinned by `build-logic`), Gradle Kotlin DSL; YAML/XML via the libraries already
in the build
**Primary Dependencies**: no new dependency. Jackson (already present) serves YAML and XML through its existing
dataformat modules if they are on the verified list; if the XML module is not, the XML writer uses the JDK's
`javax.xml.stream` (no dependency). See D9.
**Storage**: filesystem pack trees (packs `infra.storage`); no database
**Testing**: JUnit 5 + AssertJ, the BDD contract scenarios in `:deployable:test`, the whole-pack round trip
(`:pack-validation:wholePackRoundTrip`), ArchUnit gates; red-first per NFR-001
**Target Platform**: the service (Spring Boot, `deployable`) and the standalone `pack-doctor` jar (plain `main`)
**Project Type**: single multi-module Gradle project (`service/*`, `contracts/*`, `build-logic`)
**Performance Goals**: NFR-002: `validate --round-trip` over the example packs within CI's 20 s step; `--full`
within 30 s
**Constraints**: ADR-0082 (C-001); canonical model, no alias (C-002); no PIT (C-003); strict SHA-256 dependency
verification; `verifyAll` on Java 25
**Scale/Scope**: about 90 files for the rename (occurrence map), about 15 production classes for the rules, six
example packs

## Design decisions

- **D1 — #614 as slice 1 of epic #636 (WP01, re-scoped 2026-11-21).**
  - `Reason` gains an `Origin`: which check produced it.
  - One `ClosureFault.of(...)` factory replaces both `of(Refusal)` and `of(CompositionFault)`.
  - `CompositionFault` is deleted, and `CompositionRules`, `PackRulesets`, `PackSetVerdict` and `Problems` carry
    `ClosureFault`.
  - `ClosureVerdict` becomes a view over `Rationale`, or is deleted. **Deferred by ruling R-x**
    (DM-01M83MFVJZX16G6NBM4YPB9XYG, 2026-11-23): `ClosureVerdict` shipped as a fault list with a derived `Rationale`
    (a reason has no slot for the packs and violations a refusal names), and #636 slice 7 removes it once the ledger
    carries what faults hold. No code change in this mission; FR-001's acceptance records the clause deferred, the PR
    writes `Refs #636` for it, and ADR-0083 decision 15 cites R-x (WP13 T055).
  - Codes and order are unchanged. The call sites change, but no expected code or order changes in
    `CompositionRulesTest`, `PackRulesetsClosureTest`, `ProblemsTest` or `PackCheckReportContractTest`.
  - An ArchUnit or reflection test asserts that `CompositionFault` is gone. Fixtures and contract examples assert
    `Origin`, and no test excludes it.
- **D2 — The rename is mechanical (WP02).** The occurrence map (`occurrence_map.yaml`) covers:
  - the module `service/pack-check` → `service/pack-doctor`;
  - the package `ai.speckitty.charter.packcheck` → `ai.speckitty.charter.packdoctor`;
  - `PackCheckMain` → `PackDoctorMain`;
  - `charter.pack-check-jar` → `charter.pack-doctor-jar`;
  - the jar, the licence bundle, the CI and release workflows, the release scripts and the rehearsal, which change
    together;
  - the contract schema `PackCheckReport` → `PackDoctorReport`;
  - the live docs.

  The CLI gains its two commands. `validate` takes today's options, and `remediate` is stubbed to exit 2 ("not
  available") until WP07. So no behavior beyond the command word changes in WP02.

  `pack-validation`'s `PackCheck` (validate mode over one pack) is a domain term, not the tool's name, and keeps its
  name: the map marks it `do_not_change`. History keeps the old name (C-004).
- **D3 — One preset-item rule in an own-closure phase (WP03; R-a, R-k, R-l, R-m; post-plan squad A1-A3, A7, A8).**
  - **The facts are split.** `PackCompositionFacts.defined` mixes files with graph-only nodes, so it is replaced
    outright by two fields:
    - `held`: artifact files;
    - `declared`: every graph-node URN, plus `held`.

    Every reader of `defined` moves to the one it means. Both `CompositionFactsBuilder` and `WriteFacts` (the write
    path's rebuild) produce them, each with a test.
  - **The rule.** `PresetItemRules` in `packs.domain.rules`, exposed by `PackRulesets` as one own-closure phase.
    - Per pack, it takes the presets and the own closure. The own closure is
      `PackCompositionResolver.compositionClosure` rooted at the pack, with no new walk.
    - The phase first computes `caseOnlyMatches` (the existing domain function) over the own closure. It then judges
      the items that check did not name, all in one phase function.
    - A `must_have` item that is not held answers `preset_item_not_held`. Any other item that is not declared answers
      `preset_item_undeclared`.
    - It reports one fault per item, in pack-id order, then preset path order, then item order. The detail lists the
      own closure's packs in `compositionClosure` order.
  - **Where it runs.**
    - In pack-doctor, in CLOSURE after the compose and the preset contests. `PackRulesets.closure(...)` takes each
      pack's presets, replacing `presetIdsOf` outright.
    - In registration and every write, from `ClosureImpactCheck.walkFaults` with `ComposedClosure.facts`, beside
      `OwnClosureWalk`.
    - Never in `OfferingReadService.judged`, whose phase 6 case-only check and the read path are unchanged.

    `FallbackProfileRule`, `RolePinRule` and `ProfileEnhancementRule` join this phase in #636 slice 3, recorded in
    ADR-0083.
  - **Order.** Registration places preset-item faults after compose and the earlier phases. pack-doctor's default
    order (compose, contests, case-only, items) is that same order restricted to what it runs.
  - **The read path stays reachable.** Scenarios that need a stored pack which now breaks the rule (A10 at
    `activation-ruling-changes.feature:513-540`, and the other `must_have` scenarios) use a test-only store seed that
    bypasses the factory: an invalid-content path, which the charter allows. A test keeps a count of scenarios still
    asserting `requiredNotHeld`, so the count cannot drop to zero.
  - **Contract.** New `RefusalCode` constants, `PackCodes.CLOSURE` membership, and a 422 entry in `Problems.statusOf`.
  - **Positive controls.** A test asserts that every preset item of `built-in` and `org-base` is held or declared, and
    a planted variant of each asserts the exact code. `org-base` gains its two tactics rather than dropping the ids,
    to keep the preset's intent.
- **D4 — `manifest_stale` at the storage edge (WP04; R-b; squad A7, A10).**
  - **Detection.** `FileSystemPackInspector.manifest` already holds the parsed manifest. The reader fills
    `TreeCensus.Manifest` with `PackManifests.stale` over the incoming bytes, minus its `missing:` entries, which
    membership owns. `ManifestRules` derives nothing.
  - **Reporting.** `ManifestRules` states one `manifest_stale` per stale field, after membership. `StorageCodes` owns
    the token. `ManifestRules.mismatch` returns the paths it names, so the stale check can skip them without parsing
    prose.
  - **Writes.** The post-write `stamp` stays.
  - **The round trip.** `PackRoundTrip`/`RoundTripObservation` drop the source-stale reason and keep written-stale
    (A8). #636's round-trip-codes slice (retiring `EXCLUDED_WRITTEN`, merging the `STALE_*` wordings) is folded
    into WP04 (ruling R-t).
  - **Tests to rewrite, not delete.**
    - `WholePackFlowTest:127-166` and `RoundTripObservationTest:176`: the source-stale case moves to a `ManifestRules`
      test that asserts `manifest_stale` with its field pointer.
    - `MalformedDescriptorTest:148`, `ProblemsTest:193`, `PackCheckJarTest:227/310`, `PackSetCheckTest:756-781` and
      `PackCheckReportContractTest:233/577` each gain a positive assertion.
    - A source-stale pack under `--round-trip` yields exactly `["manifest_stale"]`, asserted by equality.
  - **Scenarios.** In `whole-pack-registration.feature:528-584`:
    - "partial" gets correct hashes, so it still isolates membership;
    - the scenario at 560 becomes a `manifest_stale` refusal;
    - a control scenario with a correctly hashed manifest registers.
  - **Example manifests** are restamped in WP04 with a test-scope helper over `ManifestMembership.stamp`.
- **D5 — `--full` (WP05; R-d, R-h; ruled R-s, DM-01M801NHF0BFAX05EA3F19KHV7, 2026-11-21: "1. as recommended",
  option (a)).**
  - **The finding.** The post-plan squad found that "everything registration judges" is not reachable without
    ADR-0082 slices 2-4 and 7. Registration also runs `ImpactRelations` and the `EdgeIndex` refusals and the own-closure
    walk, and the `specializes_from` cycle needs the set's composed closure (siblings are not in each other's own
    closure) and `ImpactRelations.cyclesIn`, application code that slice 2 moves into `PackGraph`.
  - **Ruled (a).** `--full` adds only what existing domain functions give, with no new walk, each run per own closure,
    in registration's phase order:
    1. delegations: `PackCompositionResolver.unresolvedDelegations` (`PackCompositionResolver.java:634`), answering
       `validation_failed` with the violation `substep_delegation_unresolved`, as registration refuses it
       (`ClosureFault.asRegistrationRefuses()`; the composing read answers `pack_unreadable`);
    2. references: `PackCompositionResolver.referenceFaults` (`:582`), answering `pack_reference_unqualified`,
       `pack_reference_pack_missing` or `pack_reference_artifact_missing`;
    3. enhancement targets: `EnhancementCompose.targetFaults` (`EnhancementCompose.java:218`), answering
       `pack_reference_pack_missing` or `pack_reference_artifact_missing`.

    All three already return every fault (a `List`). The first-fault callers (`OfferingReadService.judged` phases 2-4,
    `ClosureImpactCheck`) take the first element of those same forms, so one body remains per check.
  - **Narrowed.** The spec's "full pass" is these three checks. The `specializes_from` cycle, relations, edge-index and
    own-closure-walk checks are recorded in ADR-0083 as landing in the factory with #636 slices 2-4 and 7. The PR writes
    `Refs #632` and names that remainder.
  - **The check value.** `PackSetVerdict.Check` gains one value for the phase: `REFERENCES` (ruling R-u,
    DM-01M8035FAMHXG0A04SCBD7QTKD), since the phase is not the own-closure walk.
  - **Folded after all.** The #636 round-trip-codes slice (D4) was ruled into WP04 by R-t
    (DM-01M801QGCV2GW4DCF0KTRFBNHH), which governs over R-s's "not folded".
- **D6 — The report (WP06; R-i, R-n; squad reducer 1, 2, 9).**
  - **One model.** The report file is the same tree as the stdout problem JSON (`PackDoctorReport`, today's
    `PackCheckReport` renamed). `Problems.Problem` is reused verbatim, with `tool`, `inputs` and `summary` added as
    top-level keys. There is no second problem shape.
  - **Formats.** Three serializers of that tree: JSON (stdout), YAML (`--report`, the default) and XML
    (`--format xml`).
  - **One normative schema.** `PackDoctorReport.yaml` (JSON Schema) is the only schema. The XML is a mechanical,
    tested element-per-key mapping (R-n: our own shape, not JUnit). The contract test parses the XML back into the
    tree and validates it against the same JSON Schema, so no XSD is maintained by hand. An XUnit-compatible form is
    a later option, recorded in ADR-0083.
  - **Serialization.** `pack-doctor` already uses Jackson 3 (`tools.jackson`). YAML and XML go through the Jackson 3
    dataformat modules only if they are already verified. Otherwise XML uses `javax.xml.stream` and YAML uses the
    service's existing emitter (SnakeYAML-engine via packs), with no new verification metadata (DIRECTIVE_051).
    WP06 checks `gradle/verification-metadata.xml` first and records the choice.
- **D7 — `remediate` (WP07; R-j; squad architect 9, reducer 3 and 4).**
  - **The facade.** `PackRemediation`, a public facade in `packs.infra.storage` (not in `.rules`). It composes the
    existing pieces:
    - the canonical rewrite through `DomainRoundTrip`'s writer;
    - then the stamp through `ManifestMembership.stamp`, run after the rewrite.

    It adds no new serialization.
  - **Staging.** It writes a staged sibling directory, then swaps the directory in one rename. It reuses
    `PackCommitStore`'s staging helper if that is framework-free and free of locks. Otherwise it uses a minimal
    atomic swap, without `PackCommitStore`'s locks or generation, so pack-doctor stays framework-free.
  - **Precondition.** One `StorageRulesets` function: the storage verdict without the manifest gate and the form
    findings. A violation means exit 1, nothing written, and the violations listed. The spec and the data model use
    this wording.
  - **Architecture tests.** `HexagonalArchitectureTest`'s pack-check main rules (`COMPOSED_BY_…`, `…NAMES_TWO_ADAPTERS`,
    `ONION_…COMPOSES_TWO_ADAPTERS`) name a third adapter.
  - **Tests.**
    - Start from a pack red for both `manifest_stale` and a form finding, and assert that red first.
    - After remediating, assert green, and assert the meaning is unchanged by a model comparison.
    - Assert a stray file is untouched, by its bytes.
    - Assert an unrepairable pack writes nothing, by a file-tree hash taken before and after.
- **D11 — Test guards (post-plan reviewer).**
  - **WP02 has its own red test.** The rename changes no behavior, so its first test is structural, written before
    the rename. It scans the live surfaces outside the occurrence map's exceptions for `pack-check`/`packcheck`, with
    an explicit allowlist: `PackCheck` in pack-validation, and history. It also asserts:
    - `validate` works;
    - `remediate` exits 2 with "not available" until WP07;
    - a call with no command fails, so the old flat form is no alias.
  - **CI timing.** CI runs `validate --round-trip` under its own timed step (the #478 step, relabelled), and
    `validate --round-trip --full` under a second step with its own threshold (NFR-002).
  - **SC-001.** One parameterized table of planted faults drives both registration and `validate`, and asserts that
    the codes are equal.

- **D8 — Contract versioning.**
  - `### Breaking` fragments:
    - the preset-item refusals (a valid input is now refused);
    - `manifest_stale` (an upload with stale hashes is now refused);
    - the report schema rename;
    - the `anti_pattern` file kind (D12): an `*.anti-pattern.yaml` that registration warned `file_not_held` is now
      judged, and an invalid one is refused. The packs contract gains an `### Added` line;
    - the file-less `anti_pattern` node (D13): a pack that registered is now refused `drg_schema_error`;
    - the inline drop (D14): `antiPatterns` leaves `Styleguide`, `StyleguideRegistration` and `Procedure`, two schemas
      are deleted, and a pack file with `anti_patterns` is refused. The packs contract gains a `### Removed` (or
      `### Breaking`, per its CHANGELOG's headings) line.
  - `### Changed` fragment: the report formats and the `--full` check value (D5, analysis Q2).
  - Core's regeneration consequence is stated in each fragment (ADR-0022).
- **D9 — Dependencies.** Folded into D6: no new library, and no new verification metadata. If Jackson's YAML or XML dataformat is not already a verified dependency of
  a module `pack-doctor` may use, the YAML writer reuses the service's existing YAML emitter, and the XML writer uses
  `javax.xml.stream`. DIRECTIVE_051: no supply-chain change, so no new verification metadata.
- **D10 — The ADR.** ADR-0083 records rulings R-a to R-y (R-x and R-y added by WP13, D15), and amends:
  - ADR-0075 (decision 6: the CLI composes the domain phase under `--full`; decision 7: the preset phase; decision 9:
    pack-doctor, its commands, `--report`);
  - ADR-0042 (the 2026-11-12 amendment: hashes are verified on upload);
  - rules A10/C31 (`docs/architecture/composition-and-activation-rules.md`);
  - the scope rows on `architectural-patterns-applied.md`;
  - ADR-0052 (a dated status note: decisions 8, 10, 13, 14 and 16 are applied by WP10 to WP12 under R-r, decision 9
    as amended by ADR-0064 d12, decision 11 by the existing reference check; decision 15, the curation of open-packs'
    entries, is open-packs' work, and until it is done open-packs' inline entries and file-less nodes are refused);
  - ADR-0017 (a dated note: R14 is superseded in part for `anti_pattern` by ADR-0052 d14, now applied, D13).

  It also records rulings R-p, R-q and R-r (D12-D14; R-r governs where R-p's "#629 does not close" and R-q's "file kind
  only" differ) and R-s (D5: the narrowed `--full`, and the checks that land with #636 slices 2-4 and 7). It records #535 and #52.3 as not the charter's to judge. ADR-0082 itself stays as it is.

- **D12 — The `anti_pattern` file kind (WP10; rulings R-p, R-q and R-r, DM-01M7ZPVTTGHYCSC2Y39BZV0FXT,
  DM-01M7ZQGHVNQXAQ0S3DMTK7EZCB and DM-01M801H54DXR0447M3F2ZQWFPZ; ADR-0052 decision 8 as amended by ADR-0053
  decision 30; FR-013).**
  - **Why.** R-l counts a `must_have` only when a closure pack holds it as a file. `anti_pattern` has no file form
    today: `ArtifactKind.ANTI_PATTERN` says "never a standalone file" (`ArtifactKind.java:36`),
    `CompositionFactsBuilder.declaredWithoutFile` takes it from graph nodes only (`CompositionFactsBuilder.java:445-462`),
    `file-kinds.yaml` has no `files` rule for it (`file-kinds.yaml:141-227`), `contracts/packs/schemas/` has no schema
    (`contracts/packs/README.md:5`: "a template and an anti-pattern have none"), and `KindBindings` binds no
    anti-pattern (`KindBindings.java:19-34`). `built-in/presets/default.yaml` requires
    `anti_pattern:indirection-for-repetition`, which is only a node of `built-in/styleguide.graph.yaml:16-18`. R-p: build
    the file kind first. R-q: as a new WP of this mission, before WP03. R-r (later, governs): this PR delivers all of
    #154 and closes it, so the file kind is the first of three WPs (D12 here, D13 and D14).
  - **The shape (ADR-0052 d8, ADR-0053 d30).** `anti_patterns/<id>.anti-pattern.yaml`, closed:
    `schema_version` (`"1.0"`), `id` (kebab-case, at most 64, the file stem), `title`, `description`, `severity`
    (`smell`, `severe` or `breaking`, ADR-0052 d10; no default, ADR-0053 d32) and `bad_example` required;
    `good_example` optional. The kind token stays `anti_pattern`; the file and schema token is `anti-pattern`.
  - **Its refusals are the existing answers.** A file missing `severity` or `bad_example`, with an unknown severity or
    with an unknown key, breaks the closed schema, and the one validated read (`YamlSerializationStrategy.readValidated`,
    `YamlSerializationStrategy.java:69-91`) answers `artifact_schema_error` at the file and pointer
    (`Wording.codeFor`, `Wording.java:183-194`; contract: `CharterRefusalCode.yaml:419-421`). A misplaced file or a stem
    mismatch is the existing stem and placement rule (`FileStems`). No code is minted.
  - **The closest precedent is the routing catalog** (ADR-0080): a kind token with an underscore and a kebab file
    token, placed at `<plural>/<id>.<token>.yaml` (`RoutingCatalogBinding.java:35-70`: `fileToken`, `schemaName`,
    `fileName`, `idIsFileStem` and `placedAtItsFileName`), appended last to `KindBindings` and `YamlFormats`, with a
    `files` rule (`file-kinds.yaml:172-175`), a classifier case, a packs `CHANGELOG` line and a digest-fixture file.
    Its flat YAML form follows `GuardYaml` (`GuardYaml.java`: schema key order, `YamlDumpEmitter`). The kind facts
    of the kernel (`NodeKind.ANTI_PATTERN`: an artifact kind, activatable, declarable in graphs, an edge target;
    `NodeKind.java:48`) do not change, and no enum of kinds in either contract changes (the manifest constituent
    enum, the preset's `activated_anti_patterns`, `policy`'s URN pattern and the kind taxonomy already list it).
    Only `ArtifactKind.ANTI_PATTERN`'s documentation changes.
  - **A node and a file at once is the norm.** Every file-held kind of the example packs is also a node of a graph
    shard: `paradigm:atomic-design` is a node of `built-in/paradigm.graph.yaml` and the file
    `paradigms/atomic-design.paradigm.yaml`; `styleguide:boring-code-review` is a node of `styleguide.graph.yaml:11-15`
    and a file. A shard's name carries no kind (`file-kinds.yaml:144-147`), and the store reads node and file under one
    URN. So the anti-pattern's node **stays** in `styleguide.graph.yaml`, as the target of the `rejects` edge from
    `styleguide:boring-code-review` (`styleguide.graph.yaml:19-24`; ADR-0064 d12). The node declares; the file holds.
  - **What already exists, and is only pinned** (#154's other bullets, with evidence):
    - `rejects` as a graph edge from guidance to an anti-pattern: `RelationType.REJECTS` (`RelationType.java:45`), the
      endpoint table `REJECTS -> product(GUIDANCE, {ANTI_PATTERN})` (`EndpointTable.java:69`; `GUIDANCE` =
      directive, tactic, styleguide, toolguide, paradigm, procedure, `:19-25`), refused otherwise as
      `relationship_endpoint_not_allowed` (`GraphRules.endpoints`, `GraphRules.java:80-110`).
    - A cross-pack `rejects` needs the target's pack in `depends_on`: an edge's endpoints are references
      (`CompositionFactsBuilder.edgeFacts`, `CompositionFactsBuilder.java:369-379`), and a qualified reference outside the
      `depends_on` closure answers `pack_reference_pack_missing` (`PackCompositionResolver.java:189-190, 582-600`);
      pinned for an anti-pattern target by `PackCompositionResolverTest.aQualifiedReferenceToAnArtifactThePackHoldsIsAcceptedWhateverItsKind`.
    - Activation: `ArtifactKind.ANTI_PATTERN` is activatable (`ArtifactKind.java:37`), and `rejects` is not a cascade
      relation (`CascadeRules.CASCADE_RELATIONS`, `CascadeRules.java:64-70`), so activating the guidance that rejects an
      anti-pattern never activates it. No test pins that today; WP11 adds one (`RejectsIsNotCascadedTest`, beside
      `TensionIsNotCascadedTest`).
  - **No HTTP read.** The charter API serves no `anti_pattern` body (`ArtifactReadService.SERVED`;
    `ArtifactKindToken.yaml`: "other kernel kinds are not served yet"), as for `step_enhancement`. "The service reads the
    kind" (#154) is met by the store: registration, validation, the whole-pack read, composition and activation read
    it. An HTTP read is additive (ADR-0052 follow-ups), not in #154's acceptance, and stays out (analysis C2).
  - **Fixtures (ADR-0070: the charter owns its example packs, and curates them).** All 18 inline anti-patterns of the
    example packs move to `built-in/anti_patterns/` in WP10, each with a node and a `rejects` edge from its owner, and
    the five inline blocks are removed (`styleguides/boring-code-review` 3, `aggregate-design-rules` 2,
    `java-conventions` 1; `procedures/adversarial-squad-deployment` 8, `example-mapping-workshop` 4). The
    styleguide's `name` becomes `title` and a procedure entry's `content` becomes `description`. Eleven procedure
    entries have no `bad_example`; WP10 writes one for each from its `content` (curation, ADR-0053 d30), and assigns each
    severity explicitly (WP10 carries the table; no default, d32). Moving them in WP10 means WP11 and WP12 start from
    example packs that already have the final shape. The built-in manifest lists every new file (ADR-0042): WP10
    stamps it with `ManifestMembership.stamp` as a justified out-of-map edit of WP04's manifest, and edits the three
    styleguides as a justified out-of-map edit of WP03's `built-in/styleguides/**` glob (WP03 edits none of them).
- **D13 — A file-less `anti_pattern` node is refused (WP11; R-r; ADR-0052 d14, ADR-0053 d31; FR-014).**
  - **Rule.** One `Rule<PackContents>` in `GraphRules` (`ANTI_PATTERN_WITHOUT_FILE`), appended to `GraphRules.RULES`
    after `TENSION_CONSISTENCY`, so `PackRulesets.content()` composes it (ADR-0082: one static factory) and every
    surface that runs content (registration, every write, `pack-doctor validate`) refuses alike. For each graph document
    the read read (shards, then the fragment, `GraphRules.read`), each `anti_pattern` node (a shard node of kind
    `anti_pattern`, a fragment node of plural `anti_patterns`) whose URN the same pack does not hold as an artifact
    (`contents.artifacts()`, as `GraphRules.tensions` builds its `held` set, `GraphRules.java:116-118`) is one finding.
    It reads facts of the one read and walks nothing (NFR-003).
  - **Code: `drg_schema_error`, no new code.** A graph node the pack may not declare is already refused so: a guard,
    lifecycle or routing-catalog node answers `drg_schema_error` at `/nodes/<n>` of its document
    (`GraphBinding.notAuthored`, `GraphBinding.java:33-51`, `:154-155`, `:178-191`). The new finding uses the same code
    and place, with the detail `<origin>: anti_pattern:<id> is declared with no file; an anti-pattern is held by
    anti_patterns/<id>.anti-pattern.yaml in the same pack`. `drg_schema_error` is an enum value of the contract
    (`CharterRefusalCode.yaml:263, 348`) and a listed violation code of `validation_failed`. Only a node of the
    pack's own graph is judged: a node URN carries no pack (`graph-shard.schema.json:84`), so a cross-pack rejection
    is a qualified edge endpoint, judged by the reference check (D12), never by this rule.
  - **Facts.** After WP11 every `anti_pattern` node has a file, so WP03's `declared` adds nothing for the kind; WP03
    removes `declaredWithoutFile` as planned (T011). `action` nodes stay graph-only (mission type steps).
  - **Tests that relied on a file-less node** become canonical (a file beside the node), or become refusal tests:
    `CompositionFactsTest:85-102`, `DomainRepositoryServiceTest:593-630`. `GraphDocumentReaderTest:88-106` reads a
    document only (no content rule) and stays.
- **D14 — The procedure and styleguide shapes drop `anti_patterns` (WP12; R-r; ADR-0052 d16; FR-015).**
  - **Packs contract.** `procedure.schema.json` drops `anti_patterns` (`:111-117`) and `$defs.AntiPatternEntry`
    (`:5-32`); `styleguide.schema.json` drops `anti_patterns` (`:159-165`) and `$defs.AntiPattern` (`:5-34`). Both roots
    stay closed (`procedure.schema.json:69`, `styleguide.schema.json:121`), so a file that still carries the key is
    refused `artifact_schema_error` with the detail "unknown key 'anti_patterns'" (`Wording.additionalPropertiesDetail`,
    `Wording.java:263-265`), the unknown key ranked before a missing one (`:126-127`). No code is minted: the refusal is
    the closed schema's, as #154 asks ("refused as an unknown field").
  - **Service.** `Procedure.antiPatterns` and `Styleguide.antiPatterns` go, with `AntiPatternEntry` and
    `StyleguideAntiPattern`; `ProcedureDso`/`ProcedureYaml`/`ProcedureBinding` and `StyleguideDso`/`StyleguideYaml`/
    `StyleguideBinding` lose the field; `ArtifactDtos` and `StyleguideController` lose the mapping.
  - **Charter contract (ADR-0052 follow-ups; ADR-0022).** `Styleguide.yaml` (`required` at `:17`, property `:64-71`),
    `StyleguideRegistration.yaml` (`:67-74`) and `Procedure.yaml` (`required` at `:8`, property `:61-68`) drop
    `antiPatterns`; `StyleguideAntiPattern.yaml` and `ProcedureAntiPattern.yaml` are deleted; the examples
    `Artifact.styleguide.yaml:15` and `Artifact.procedure.yaml:24` and the scenarios `procedures.feature:61, 103, 120`,
    `styleguides.feature:52` and `artifacts.feature:129` follow. `StyleguideRegistration` is closed (`:18`), so a request
    that still sends `antiPatterns` is refused by its schema. A `### Breaking` fragment: core regenerates its client.
  - **Tests and fixtures** carry the new shape only; `refused-anti-pattern-name` and `refused-anti-pattern-description`
    (the inline entry's own refusals) become one `refused-inline-anti-patterns.procedure.yaml` (the unknown-key
    refusal), an error-handling fixture the charter allows.
  - **Not here.** `.kittify/charter-packs/` and `.kittify/charter/generated/` hold spec-kitty's own governance
    doctrine for this repository, written by the spec-kitty tool; no charter test reads them, and core realigns them
    through its generated client (charter: core aligns later). They are not example packs and are not edited.

- **D15 — The case-only policy lock in pack-doctor's `CLOSURE` (WP13; ruling R-y, DM-01M83MFXWH2NZQ8CJWFW1JTPY6;
  FR-016).**
  - **The gap.** Registration's composing judge runs phase 6, `PackCompositionResolver.caseOnlyMatches(composed,
    mentionsOf(facts))` (`OfferingReadService.java:450-475`), where `mentionsOf` is every preset item URN **and** every
    URN key of the pack's policy file (`facts.policy().urnEntries()`). Its fault is the resolver refusal lifted as it is
    (`ResolverFaults::of`): `pack_reference_artifact_missing`, naming the mentioning pack then the holder, no violation
    of its own; `ClosureImpactCheck.place` names the closure in its detail. pack-doctor's `CLOSURE`
    (`PackRulesets.closureFaults`, `PackRulesets.java:95-110`: compose, preset contests, preset items) runs
    `caseOnlyMatches` only per preset item, inside `PresetItemRules.itemFault` (`PresetItemRules.java:152-197`). So a
    case-only policy lock passes `validate` and is refused at registration (ADR-0075 decision 9 broken).
  - **Placement (ADR-0082: the rule in the factory, the domain owns the walk).** One new rule class,
    `packs.domain.rules.PolicyLockRules`, rule name `closure.policy-locks`, a public static `phase(factsOf)` returning
    `Function<List<PackUnderResolution>, List<ClosureFault>>`, as `PresetItemRules.phase` does. For each pack of the
    set, in pack-id order (the preset-item phase's order), whose policy has URN entries, it runs the existing
    `PackCompositionResolver.caseOnlyMatches` over the pack's **own closure** (R-k; computed by
    `PresetItemRules.ownClosure`, made package-private and shared, not copied; a pack whose own closure reaches a pack
    outside the set is skipped, as there), with the mentions function answering that pack's policy URN keys
    (`facts.policy().urnEntries().keySet()`, as written) and nothing for the others. Each `Refusal` becomes
    `ClosureFault.of(PackOrigins.of(pack, lockUrn), RULE, refusal.failure().code(), refusal.detail(),
    refusal.packs())`, no violation: the code, detail and packs registration's phase 6 gives, the closure prefix aside,
    which registration adds in `place`. No new walk (NFR-003), no new code, no new `PackSetVerdict.Check`.
  - **Order.** `PackRulesets.closureFaults` becomes compose, preset contests, preset items, **policy locks**:
    `PolicyLockRules.phase(factsOf)` is the fourth stream, and `PackRulesets.closure(factsOf)` takes it through
    `closureFaults` (one derivation). This is registration's order restricted to what pack-doctor runs, as D3 states
    for the preset items: registration's phase 6 follows the contests (phase 5) and lists, per pack, its preset
    mentions before its policy mentions, so a pack's case-only preset item comes before its case-only lock on both
    surfaces. Registration's phase 6 halts the closure, so there a case-only lock never stands beside a
    `preset_item_not_held` or `preset_item_undeclared`; pack-doctor's `CLOSURE` collects every phase (D3, unchanged),
    so it reports both, each with registration's code. Across packs, pack-doctor groups by phase then pack id, and
    registration lists by pack in composed order; the codes per pack are equal (SC-001 compares codes).
  - **What does not change.** Registration and every write (`ClosureImpactCheck`), the composing read
    (`OfferingReadService.judged` and its private `mentionsOf`), `PresetItemRules`' judgement, the `--full` phase,
    `PackSetCheck` (it takes the phase through `closureFaults`), the contract and its codes. Moving the read's phase 6
    into the factory, so `mentionsOf` has one body, lands with #636 (slice 7), recorded in ADR-0083.
  - **Tests (red first, NFR-001).** `PolicyLockRulesTest` (domain): a lock that differs from a held id by case alone is
    one `pack_reference_artifact_missing` with the packs and no violation; a lock held exactly, a lock no closure pack
    holds in any case, and a case match held only by a dependent (outside the own closure) are accepted; a pack with a
    case-only preset item and a case-only lock gets the preset item's fault, then the lock's, through
    `PackRulesets.closureFaults`. `FullPassParityTest.closureRows()` gains the row "a policy lock that names a held
    artifact only by case" (`pack_reference_artifact_missing`, no violations, `fullReports` true), red before the rule:
    registration refuses it, `validate` passes it.
  - **Records.** ADR-0083: R-x and R-y in the rulings table and the status line ("R-a to R-y"), decision 15 cites R-x,
    one new decision for R-y, and Batch 5 of the alternatives ("Do it in this mission" declined for R-x; "Follow-up via
    #636" declined for R-y). `docs/guides/check-packs.md`: the `CLOSURE` text gains the policy lock. A new change
    fragment `contracts/charter/changes/2026-11-23-pack-doctor-policy-lock-case-only.md` under `### Changed`, as the
    `--full` fragment is: no code or wire change, registration already refused it, and core regenerates nothing. The
    preset-item fragment (WP03's) is not edited.

## Charter Check

- **Canonical model (C-002):** the shapes are replaced outright: `presetIdsOf` → presets, `CompositionFault` deleted,
  pack-check → pack-doctor, no alias. Fixtures are fixed (FR-010). The `anti_pattern` file kind (D12) adds the shape
  ADR-0052 d8 rules, with no legacy reader; R-r drops the inline entries (D14) and refuses file-less nodes (D13) in
  the same PR, with no deprecation window. The example packs move to the canonical shape (D12); open-packs follows
  the model through its curation (C-006). Pass.
- **Contract versioning (ADR-0022):** the `### Breaking` fragments are listed in D8. Pass.
- **Round trip (ADR-0036/0070):** the whole-pack gate runs over the restamped example packs, and remediate keeps
  meaning. Pass.
- **No PIT (ADR-0062):** none is run. Pass.
- **Adversarial squad:** run at pre-spec (dispositions recorded). A pre-merge squad runs over the aggregate diff. Tier
  R: a separate reviewer session reviews before ready. Pass.
- **Issue rules:**
  - `Closes #614 #629 #630 #154`, `Refs #632` with the remainder named (R-s); `Refs #636` for FR-001's
    `ClosureVerdict` clause (R-x);
  - #52 item 3 and #535 are closed with evidence;
  - #549 and #142 are ticked;
  - open-packs consequences are drafted (C-006).

  Pass.
- **Supply chain (DIRECTIVE_051):** no dependency change (D9). Pass.

## Project Structure

### Documentation (this mission)

```
kitty-specs/pack-doctor-coverage-01M7YMQK/
├── spec.md
├── plan.md
├── data-model.md
├── quickstart.md
├── occurrence_map.yaml
├── research/ (rule-coverage.md, squad-dispositions.md, open-packs issue drafts in WP08)
├── decisions/
└── tasks/
```

### Source Code (repository root)

```
service/packs/src/main/java/ai/speckitty/charter/packs/
├── domain/rules/        PackRulesets, CompositionRules, PresetItemRules (new), PolicyLockRules (new, D15),
│                        GraphRules (D13)
├── domain/              PackCompositionResolver, EnhancementCompose, PackCompositionFacts, ClosureFault, PackCodes,
│                        AntiPattern, AntiPatternIdentifier, AntiPatternSeverity (new, D12),
│                        Procedure, Styleguide (lose antiPatterns; AntiPatternEntry, StyleguideAntiPattern deleted, D14)
├── application/         ClosureImpactCheck, OfferingReadService (call the domain forms)
└── infra/storage/       ManifestRules, StorageCodes, TreeCensus, PackRoundTrip, PackRemediation (new),
                         AntiPatternBinding, AntiPatternDso, AntiPatternYaml (new, D12), KindBindings, YamlFormats,
                         Procedure/Styleguide Dso, Yaml and Binding (D14)
└── infra/api/           ArtifactDtos, StyleguideController (D14)
service/pack-validation/  PackSetCheck, PackSetVerdict (the --full check value, D5), roundtrip
service/pack-doctor/      (renamed from pack-check) PackDoctorMain, Arguments, Problems, ReportWriter, report/
contracts/charter/        schemas (RefusalCode, PackDoctorReport.yaml; Styleguide, StyleguideRegistration, Procedure lose
                          antiPatterns, D14), scenarios, examples, changes/
contracts/packs/          anti-pattern.schema.json (new, D12), procedure/styleguide schemas (D14), file-kinds.yaml,
                          conformance/, CHANGELOG.md
build-logic/, .github/workflows/, scripts/release/, docs/
service/packs/src/testFixtures/resources/example-packs/ (built-in anti_patterns/ with 18 files, org-base fixes;
                          manifests restamped)
```

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|---|---|---|
| A one-PR rename together with behavior changes | Operator ruling R-f | Split PRs were offered and declined |
| Two report formats | Operator ruling R-i | YAML-only was offered and declined |

## Implementation Concern Map

### IC-01 — One closure fault type (#614)
- **Purpose**: delete `CompositionFault`; CLOSURE states `ClosureFault`.
- **Relevant requirements**: FR-001.
- **Affected surfaces**: `CompositionRules`, `PackRulesets`, `PackSetCheck`, `PackSetVerdict`, `Problems`, and their tests.
- **Sequencing/depends-on**: none.
- **Risks**: the order of findings and the wire codes must not shift. Mitigation: the pinning tests stay green with
  no expected-value edits.

### IC-02 — pack-check becomes pack-doctor
- **Purpose**: the rename with the two commands, and no behavior change.
- **Relevant requirements**: FR-007, C-004.
- **Affected surfaces**: see `occurrence_map.yaml`.
- **Sequencing/depends-on**: IC-01.
- **Risks**:
  - The release workflow, the asset script and the rehearsal must change together.
  - Build-logic allowlists hard-code `:pack-check`.

  Mitigation: run the release rehearsal and the build-logic tests in the WP.

### IC-09 — The `anti_pattern` file kind
- **Purpose**: D12.
- **Relevant requirements**: FR-013, FR-010.
- **Affected surfaces**: `anti-pattern.schema.json` (new), `file-kinds.yaml`, `classifier-cases.yaml`, the packs
  `CHANGELOG` and `README`; `ArtifactKind` (documentation); `AntiPattern`, `AntiPatternIdentifier`,
  `AntiPatternSeverity` and `Pack`; `AntiPatternBinding`, `AntiPatternDso`, `AntiPatternYaml`, `KindBindings`,
  `YamlFormats`; the digest fixture; `built-in/anti_patterns/` (18 files), `built-in/styleguide.graph.yaml` and
  `procedure.graph.yaml` (nodes and `rejects` edges), the five example files that lose their inline block; a
  registration scenario and a change fragment; the logical model, the domain context page and the glossary.
- **Sequencing/depends-on**: IC-02. IC-03, IC-10 and IC-11 depend on it.
- **Risks**:
  - The built-in manifest is WP04's file, and the three styleguides are inside WP03's glob. Mitigation: justified
    out-of-map edits, listed; the later lane re-stamps.
  - The facts snapshot (`facts/example-packs.json`) gains 18 held URNs. Mitigation: re-record it in WP10; WP03
    re-records it again for `held`/`declared`.
  - Eleven procedure entries need a `bad_example` written. Mitigation: WP10 writes each from the entry's `content`,
    and a reviewer reads them.

### IC-10 — The file-less `anti_pattern` node refusal
- **Purpose**: D13.
- **Relevant requirements**: FR-014, SC-004.
- **Affected surfaces**: `GraphRules` (one rule), `GraphRulesTest`, `CompositionFactsTest`, `DomainRepositoryServiceTest`,
  a new `RejectsIsNotCascadedTest`, a registration scenario, a `pack-doctor validate` parity test, a change fragment.
- **Sequencing/depends-on**: IC-09 (every example node has its file).
- **Risks**: any fixture with a graph-only `anti_pattern` node turns red. Mitigation: the census in D13 lists them;
  each becomes canonical or an explicit refusal test.

### IC-11 — The inline `anti_patterns` drop
- **Purpose**: D14.
- **Relevant requirements**: FR-015, SC-004.
- **Affected surfaces**: the procedure and styleguide pack schemas; `Procedure`, `Styleguide`, `AntiPatternEntry`,
  `StyleguideAntiPattern`; their DSOs, YAML forms and bindings; `ArtifactDtos`, `StyleguideController`; the charter
  schemas `Styleguide`, `StyleguideRegistration`, `Procedure`, `StyleguideAntiPattern`, `ProcedureAntiPattern`; two
  contract examples and three scenarios; the schema and round-trip fixtures and the tests that build inline entries;
  `docs/architecture/descriptors.md`, `descriptor-schemas.md`, `docs/guides/author-a-procedure.md`, the conformance
  divergence entries.
- **Sequencing/depends-on**: IC-09 (the example packs carry no inline entry).
- **Risks**: core's generated client loses two types (a `### Breaking` fragment, ADR-0022). The conformance register
  is WP02's (`docs/conformance/**`): a justified out-of-map edit.

### IC-03 — The preset-item rule
- **Purpose**: D3.
- **Relevant requirements**: FR-002, FR-003, FR-010, FR-011.
- **Affected surfaces**:
  - `PresetItemRules` (new), `PackRulesets`, `PackCompositionFacts` (declared), `PackCodes`, `RefusalCode`;
  - `ClosureImpactCheck`, `PackSetCheck`, `Problems`;
  - `CharterRefusalCode.yaml`, the registration scenarios;
  - the built-in and org-base example packs.
- **Sequencing/depends-on**: IC-01, IC-02, IC-09 (the built-in anti-pattern must be held as a file, R-l). It does not
  wait for IC-10 or IC-11.
- **Risks**: open-packs content may break, which is expected and drafted (C-006). The case-only precedence must hold.

### IC-04 — `manifest_stale`
- **Purpose**: D4.
- **Relevant requirements**: FR-004, FR-005, FR-010, FR-011.
- **Affected surfaces**: `TreeCensus`, the reader, `ManifestRules`, `StorageCodes`, `StorageRulesets`, `PackRoundTrip`,
  `RoundTripObservation`, `whole-pack-registration.feature`, the example manifests.
- **Sequencing/depends-on**: IC-02.
- **Risks**: the whole-pack baseline shifts. Mitigation: restamp in the same WP.

### IC-05 — The `--full` domain phase (ruled R-s)
- **Purpose**: D5.
- **Relevant requirements**: FR-006, NFR-002, NFR-003, SC-001.
- **Affected surfaces**: `PackCompositionResolver` (`unresolvedDelegations`, `referenceFaults`), `EnhancementCompose`
  (`targetFaults`), `OfferingReadService`, `ClosureImpactCheck`, `PackSetCheck`, `PackSetVerdict`, `Arguments`, the
  CI timing step.
- **Sequencing/depends-on**: IC-03.
- **Risks**: the first-fault refusal codes on registration and reads must not change. Mitigation: the existing
  scenarios stay green unchanged.

### IC-06 — The report
- **Purpose**: D6.
- **Relevant requirements**: FR-008.
- **Affected surfaces**: `pack-doctor` report model and writers, `Arguments`, the schemas, the contract test.
- **Sequencing/depends-on**: IC-02 (the schema values from IC-05).
- **Risks**: an XML dependency (D9).

### IC-07 — `remediate`
- **Purpose**: D7.
- **Relevant requirements**: FR-009.
- **Affected surfaces**: `PackRemediation` (new, `infra.storage`), `pack-doctor` command wiring, the ArchUnit
  storage-edge rule.
- **Sequencing/depends-on**: IC-04.
- **Risks**: an in-place write. Mitigation: a staged copy, then the swap, then validation again.

### IC-12 — The case-only policy lock in `CLOSURE`
- **Purpose**: D15.
- **Relevant requirements**: FR-016, NFR-001, NFR-003, SC-001, C-001.
- **Affected surfaces**: `PolicyLockRules` (new), `PackRulesets.closureFaults`, `PresetItemRules.ownClosure` (shared),
  `PolicyLockRulesTest` (new), `FullPassParityTest.closureRows()`, ADR-0083, `docs/guides/check-packs.md`, a change
  fragment.
- **Sequencing/depends-on**: IC-03, IC-05 (the parity table), IC-08 (ADR-0083 and the guide exist).
- **Risks**: the order of `CLOSURE`'s findings for sets that already pass is unchanged (a new phase appended last).
  Mitigation: `PackRulesetsClosureTest` and the goldens stay green with no expected-value edit.

### IC-08 — Decision record, contract and docs
- **Purpose**: D8, D10, C-006.
- **Relevant requirements**: FR-011, FR-012 (R-x and R-y added by IC-12).
- **Affected surfaces**: ADR-0083, the ADR-0075/0042 amendments, the rules doc, the patterns page, `docs/guides/check-packs.md`
  (text only), `contracts/charter/changes/`, the open-packs drafts.
- **Sequencing/depends-on**: IC-01 to IC-07, IC-09 to IC-11.
- **Risks**: the ADR number. Mitigation: check `main` and the open PRs when writing it.
