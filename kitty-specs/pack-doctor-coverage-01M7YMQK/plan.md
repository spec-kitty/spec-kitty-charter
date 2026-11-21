# Implementation Plan: pack-doctor coverage

**Branch**: `kitty/mission-pack-doctor-coverage-01M7YMQK` → `feat/pack-doctor-coverage` → PR to `main` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)
**Input**: [spec.md](spec.md); research in [research/rule-coverage.md](research/rule-coverage.md) and
[research/squad-dispositions.md](research/squad-dispositions.md); rulings R-a to R-q in `decisions/`.

## Summary

Give registration and the standalone checker one answer for the same pack, under ADR-0082:

- The rules live in the domain factory (`PackRulesets`). A storage-format rule lives at the storage edge.
- The CLI and the API only transform and interface.

The checker becomes `pack-doctor`, with `validate` and `remediate`.

The work is nine ordered work packages in one PR (R-f):

1. #614 (`ClosureFault` replaces `CompositionFault`).
2. The rename.
3. The `anti_pattern` file kind (WP10, rulings R-p and R-q), which the preset-item rule needs.
4. The preset-item rule.
5. `manifest_stale`.
6. The `--full` domain phase.
7. The report.
8. `remediate`.
9. The ADR, the contract and the docs.

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
  - `ClosureVerdict` becomes a view over `Rationale`, or is deleted.
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
    (A8). Folding #636's round-trip-codes slice (retiring `EXCLUDED_WRITTEN`, merging the `STALE_*` wordings) needs
    the operator's yes, which the dispatcher is asking for.
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
- **D5 — `--full` (WP05; R-d, R-h). HELD pending the operator (scope adjustment via the dispatcher,
  2026-11-21).**
  - **The finding.** The post-plan squad found that "everything registration judges" is not reachable without
    ADR-0082 slices 2-4 and 7. Registration also runs:
    - delegations;
    - `ImpactRelations` and the `EdgeIndex` refusals;
    - the own-closure walk.
  - **The cycle check.** The `specializes_from` cycle needs the set's composed closure, because siblings are not in
    each other's own closure. It also needs `ImpactRelations.cyclesIn`, which is application code that slice 2 moves
    into `PackGraph`.
  - **Proposed (a).** `--full` adds only what existing domain functions give with no new walk:
    - references (`PackCompositionResolver.referenceFaults`);
    - enhancement targets (`EnhancementCompose.targetFaults`);
    - delegations (`unresolvedDelegations`).

    Each runs per own closure, in registration's phase order: delegations, references, targets. `OfferingReadService`
    and `ClosureImpactCheck` take the first fault of the same collect-all forms, so one body remains. The spec's
    "full pass" is narrowed to these, and the cycle, relations, edge-index and own-closure-walk checks are recorded in
    ADR-0083 as landing in the factory with #636 slices 2-4 and 7. The PR says `Refs #632`.
  - **Alternative (b).** `--full` stays design-only until slice 7.
  - WP05 starts only on the ruling.
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
      judged, and an invalid one is refused. The packs contract gains an `### Added` line.
  - `### Changed` fragment: the report formats and the `CLOSURE_WALK` check value.
  - Core's regeneration consequence is stated in each fragment (ADR-0022).
- **D9 — Dependencies.** Folded into D6: no new library, and no new verification metadata. If Jackson's YAML or XML dataformat is not already a verified dependency of
  a module `pack-doctor` may use, the YAML writer reuses the service's existing YAML emitter, and the XML writer uses
  `javax.xml.stream`. DIRECTIVE_051: no supply-chain change, so no new verification metadata.
- **D10 — The ADR.** ADR-0083 records rulings R-a to R-n, and amends:
  - ADR-0075 (decision 6: the CLI composes the domain phase under `--full`; decision 7: the preset phase; decision 9:
    pack-doctor, its commands, `--report`);
  - ADR-0042 (the 2026-11-12 amendment: hashes are verified on upload);
  - rules A10/C31 (`docs/architecture/composition-and-activation-rules.md`);
  - the scope rows on `architectural-patterns-applied.md`;
  - ADR-0052 (a status note: decision 8 is applied by the file kind, D12; decisions 14 to 16 wait for the migration).

  It also records rulings R-p and R-q (D12). It records #535 and #52.3 as not the charter's to judge. ADR-0082 itself stays as it is.

- **D12 — The `anti_pattern` file kind (WP10; rulings R-p and R-q, DM-01M7ZPVTTGHYCSC2Y39BZV0FXT and
  DM-01M7ZQGHVNQXAQ0S3DMTK7EZCB; ADR-0052 decision 8 as amended by ADR-0053 decision 30; FR-013).**
  - **Why.** R-l counts a `must_have` only when a closure pack holds it as a file. `anti_pattern` has no file form
    today: `ArtifactKind.ANTI_PATTERN` says "never a standalone file" (`ArtifactKind.java:36`),
    `CompositionFactsBuilder.declaredWithoutFile` takes it from graph nodes only (`CompositionFactsBuilder.java:445-462`),
    `file-kinds.yaml` has no `files` rule for it (`file-kinds.yaml:141-227`), `contracts/packs/schemas/` has no schema
    (`contracts/packs/README.md:5`: "a template and an anti-pattern have none"), and `KindBindings` binds no
    anti-pattern (`KindBindings.java:19-34`). `built-in/presets/default.yaml` requires
    `anti_pattern:indirection-for-repetition`, which is only a node of `built-in/styleguide.graph.yaml:16-18`. R-p: build
    the file kind first. R-q: as a new WP of this mission, before WP03, so #629 and #632 still close here.
  - **The shape (ADR-0052 d8, ADR-0053 d30).** `anti_patterns/<id>.anti-pattern.yaml`, closed:
    `schema_version` (`"1.0"`), `id` (kebab-case, at most 64, the file stem), `title`, `description`, `severity`
    (`smell`, `severe` or `breaking`, ADR-0052 d10; no default, ADR-0053 d32) and `bad_example` required;
    `good_example` optional. The kind token stays `anti_pattern`; the file and schema token is `anti-pattern`.
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
    and a file. A shard's name carries no kind (`file-kinds.yaml:144-147`, any `*/*.graph.yaml`), and the store
    reads node and file under one URN. So the anti-pattern's node **stays** where it is, in `styleguide.graph.yaml`,
    as the target of the `rejects` edge from `styleguide:boring-code-review` (`styleguide.graph.yaml:19-24`; ADR-0064
    d12: an edge is authored only in graph documents, and its endpoints are declared there). The node declares; the
    file holds. Moving the node to an `anti_pattern.graph.yaml` would change nothing the model sees.
  - **What does not change here.** A graph-only `anti_pattern` node stays declared, and is still counted in
    `CompositionFactsBuilder`'s graph-only set until WP03 replaces `defined` with `held` and `declared` (D3): after
    WP03, a node alone is declared but not held, so R-l refuses a `must_have` that has no file. Refusing a file-less
    node (ADR-0052 d14, ADR-0053 d31) is ruled "after the migration", and open-packs has 13 file-less nodes and no
    anti-pattern file (`packs/architecture/drg/fragment.yaml`, `packs/refactoring/drg/fragment.yaml`). The inline
    `anti_patterns` of styleguides and procedures are a different shape (a value of the owner, with no id), and
    ADR-0052 d16 orders "the kind and its schema land first; the migration runs ... then a re-pin". Both stay on #154
    (**Refs #154**, not Closes), pending the operator question recorded in `analysis-report.md` (Q1).
  - **No HTTP read.** The charter API serves no `anti_pattern` body today (`ArtifactReadService.SERVED`;
    `ArtifactKindToken.yaml`: "other kernel kinds are not served yet"), as for `step_enhancement`, which is also
    activatable, graph-declared, file-held and unserved. A read path is additive (ADR-0052 follow-ups) and is not in
    R-q's list. The charter contract changes only in what whole-pack registration holds: a `### Breaking` fragment,
    since an `*.anti-pattern.yaml` that was warned `file_not_held` is now judged, and an invalid one is refused.
  - **Fixture.** `built-in/anti_patterns/indirection-for-repetition.anti-pattern.yaml`, written from the inline entry
    of `boring-code-review` (`styleguides/boring-code-review.styleguide.yaml`, "Indirection to remove a few repeated
    lines"), with `severity: smell`, and the title the node's label carries. The built-in manifest lists it
    (ADR-0042): WP10 stamps it with `ManifestMembership.stamp` as a justified out-of-map edit of WP04's manifest, and
    the lane that merges second re-runs the stamp (as analysis U1 set out for WP03).

## Charter Check

- **Canonical model (C-002):** the shapes are replaced outright: `presetIdsOf` → presets, `CompositionFault` deleted,
  pack-check → pack-doctor, no alias. Fixtures are fixed (FR-010). The `anti_pattern` file kind (D12) adds the shape
  ADR-0052 d8 rules, with no legacy reader; the inline entries and file-less nodes it leaves are the pre-migration
  state ADR-0052 d16 orders, asked of the operator as Q1. Pass.
- **Contract versioning (ADR-0022):** the `### Breaking` fragments are listed in D8. Pass.
- **Round trip (ADR-0036/0070):** the whole-pack gate runs over the restamped example packs, and remediate keeps
  meaning. Pass.
- **No PIT (ADR-0062):** none is run. Pass.
- **Adversarial squad:** run at pre-spec (dispositions recorded). A pre-merge squad runs over the aggregate diff. Tier
  R: a separate reviewer session reviews before ready. Pass.
- **Issue rules:**
  - `Closes #614 #629 #630 #632`;
  - #52 item 3 and #535 are closed with evidence;
  - #549 is ticked;
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
├── domain/rules/        PackRulesets, CompositionRules, PresetItemRules (new), SpecializationCycleRule (moved)
├── domain/              PackCompositionResolver, EnhancementCompose, PackCompositionFacts, ClosureFault, PackCodes,
│                        AntiPattern, AntiPatternIdentifier, AntiPatternSeverity (new, D12)
├── application/         ClosureImpactCheck, OfferingReadService (call the domain forms)
└── infra/storage/       ManifestRules, StorageCodes, TreeCensus, PackRoundTrip, PackRemediation (new),
                         AntiPatternBinding, AntiPatternDso, AntiPatternYaml (new, D12), KindBindings, YamlFormats
service/pack-validation/  PackSetCheck, PackSetVerdict (CLOSURE_WALK), roundtrip
service/pack-doctor/      (renamed from pack-check) PackDoctorMain, Arguments, Problems, ReportWriter, report/
contracts/charter/        schemas (RefusalCode, PackDoctorReport.yaml/.xsd), scenarios, changes/
contracts/packs/          anti-pattern.schema.json (new, D12), file-kinds.yaml, conformance/, CHANGELOG.md
build-logic/, .github/workflows/, scripts/release/, docs/
service/packs/src/testFixtures/resources/example-packs/ (built-in, org-base fixes; manifests restamped)
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
  `YamlFormats`; the digest fixture; `built-in/anti_patterns/`; a registration scenario and a change fragment.
- **Sequencing/depends-on**: IC-02. IC-03 depends on it.
- **Risks**:
  - The built-in manifest is WP04's file. Mitigation: stamp the one new constituent; the later lane re-stamps.
  - The facts snapshot (`facts/example-packs.json`) moves the anti-pattern URN to the file kinds' order.
    Mitigation: re-record it in WP10; WP03 re-records it again for `held`/`declared`.
  - Open-packs' Python checker treats `anti_pattern` as graph-only (`validate_pack_schemas.py:84-88`, 447, 1181).
    Mitigation: drafted as `open-packs#TBD-anti-pattern-files` (C-006).

### IC-03 — The preset-item rule
- **Purpose**: D3.
- **Relevant requirements**: FR-002, FR-003, FR-010, FR-011.
- **Affected surfaces**:
  - `PresetItemRules` (new), `PackRulesets`, `PackCompositionFacts` (declared), `PackCodes`, `RefusalCode`;
  - `ClosureImpactCheck`, `PackSetCheck`, `Problems`;
  - `CharterRefusalCode.yaml`, the registration scenarios;
  - the built-in and org-base example packs.
- **Sequencing/depends-on**: IC-01, IC-02, IC-09 (the built-in anti-pattern must be held as a file, R-l).
- **Risks**: open-packs content may break, which is expected and drafted (C-006). The case-only precedence must hold.

### IC-04 — `manifest_stale`
- **Purpose**: D4.
- **Relevant requirements**: FR-004, FR-005, FR-010, FR-011.
- **Affected surfaces**: `TreeCensus`, the reader, `ManifestRules`, `StorageCodes`, `StorageRulesets`, `PackRoundTrip`,
  `RoundTripObservation`, `whole-pack-registration.feature`, the example manifests.
- **Sequencing/depends-on**: IC-02.
- **Risks**: the whole-pack baseline shifts. Mitigation: restamp in the same WP.

### IC-05 — The `--full` domain phase
- **Purpose**: D5.
- **Relevant requirements**: FR-006, NFR-003.
- **Affected surfaces**: `PackCompositionResolver`, `EnhancementCompose`, `SpecializationCycleRule` (moved),
  `PackRulesets`, `OfferingReadService`, `ClosureImpactCheck`, `PackSetCheck`, `PackSetVerdict`, `Arguments`.
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

### IC-08 — Decision record, contract and docs
- **Purpose**: D8, D10, C-006.
- **Relevant requirements**: FR-011, FR-012.
- **Affected surfaces**: ADR-0083, the ADR-0075/0042 amendments, the rules doc, the patterns page, `docs/guides/check-packs.md`
  (text only), `contracts/charter/changes/`, the open-packs drafts.
- **Sequencing/depends-on**: IC-01 to IC-07, IC-09.
- **Risks**: the ADR number. Mitigation: check `main` and the open PRs when writing it.
