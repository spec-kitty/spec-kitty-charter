# Implementation Plan: pack-doctor coverage

**Branch**: `kitty/mission-pack-doctor-coverage-01M7YMQK` → `feat/pack-doctor-coverage` → PR to `main` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)
**Input**: [spec.md](spec.md); research in [research/rule-coverage.md](research/rule-coverage.md) and
[research/squad-dispositions.md](research/squad-dispositions.md); rulings R-a to R-n in `decisions/`.

## Summary

Give registration and the standalone checker one answer for the same pack, under ADR-0082:

- The rules live in the domain factory (`PackRulesets`). A storage-format rule lives at the storage edge.
- The CLI and the API only transform and interface.

The checker becomes `pack-doctor`, with `validate` and `remediate`.

The work is eight ordered work packages in one PR (R-f):

1. #614 (`ClosureFault` replaces `CompositionFault`).
2. The rename.
3. The preset-item rule.
4. `manifest_stale`.
5. The `--full` domain phase.
6. The report.
7. `remediate`.
8. The ADR, the contract and the docs.

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

- **D1 — #614 first (WP01).**
  - `CompositionRules.faults` and `presetFaults` return `List<ClosureFault>`.
  - `PackRulesets.closureFaults` and `PackSetVerdict` carry `ClosureFault`.
  - `Problems` maps `ClosureFault` through the one closure-refusal mapping that #618 built.
  - `CompositionFault` and `ClosureFault.of(CompositionFault)` are deleted.
  - `CompositionRulesTest`, `PackRulesetsClosureTest`, `ProblemsTest` and `PackCheckReportContractTest` pin the codes
    and the order unchanged.
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
- **D3 — One preset-item rule (WP03; R-a, R-k, R-l, R-m).**
  - **The rule:** `PresetItemRules` in `packs.domain.rules`, exposed through `PackRulesets`.
  - **Input:** per pack, its presets, its own closure and the closure's declared and held URNs. The own closure is
    `PackCompositionResolver.compositionClosure` rooted at the pack (depends_on + parent_pack, R-k). No new walk
    (NFR-003).
  - **Held:** `PackCompositionFacts.defined` (artifact files).
  - **Declared:** held plus the graph node URNs. If the facts do not yet carry graph nodes, `PackCompositionFacts`
    gains `declared` (graph nodes) from the same assembly. The facts are built once, from the one read.
  - **Faults:** one per item, in pack, preset and item order. Case-only takes precedence: an item the case-only phase
    names gets no preset-item fault.
  - **Codes:** a `must_have` item that is not held answers `preset_item_not_held`. Any other item that is not
    declared answers `preset_item_undeclared`.
  - **Where it runs:**
    - in `CLOSURE`'s preset phase (`PackRulesets.closure(...)` takes `List<Preset>` per pack, replacing
      `presetIdsOf` outright);
    - in `ClosureImpactCheck`, on every write;
    - never in `OfferingReadService.judged`, so a composing read is unchanged (A3). `RequiredFloor` and
      `requiredNotHeld` stay (A2).
  - **Contract:** new `RefusalCode` constants, `PackCodes.CLOSURE` membership and `Problems.statusOf` (422).
- **D4 — `manifest_stale` at the storage edge (WP04; R-b).**
  - The reader fills `TreeCensus.Manifest` with the stale fields from `PackManifests.stale` over the incoming bytes,
    so `ManifestRules` derives nothing.
  - `ManifestRules` states one `manifest_stale` per stale field, after membership, skipping the paths membership
    names. `StorageCodes` owns the token.
  - The post-write `stamp` stays.
  - `PackRoundTrip` / `RoundTripObservation` drop the source-stale reason and keep written-stale (A8). The
    `PackSetCheck.notReported` file dedupe is unchanged.
  - Example manifests are restamped with the service's own stamp. A one-off test-scope helper or WP07's `remediate`
    is acceptable; the result is committed in WP04.
  - `whole-pack-registration.feature:560` is rewritten to a refusal.
- **D5 — One collect-all domain phase for `--full` (WP05; R-d, R-h).**
  - **The forms:**
    - `PackCompositionResolver.referenceFaults` (the collect-all form of `checkReferences`);
    - `EnhancementCompose.targetFaults` (collect-all `targetHeld`);
    - `SpecializationCycleRule`, moved to `packs.domain.rules` over `List<PackUnderResolution>` plus facts.
  - **The phase:** each check is exposed by `PackRulesets` as one phase, in today's order (references, targets,
    cycle).
  - **Callers:** `OfferingReadService.judged` (phases 3 and 4) and `ClosureImpactCheck` call the same forms and keep
    their first-phase-refuses behavior. `PackSetCheck` runs the phase per pack over its own closure when `--full` is
    given, and adds `Check.CLOSURE_WALK`.
  - **No scope flag:** the CLI composes the phase or does not.
  - **Tests:** a cross-sibling cycle is reported once (A6).
- **D6 — The report (WP06; R-i, R-n).**
  - One report model in `pack-doctor`: inputs, checks run and skipped, packs with problems, and a summary. Two
    serializers: YAML (the default) and XML.
  - Schemas: `contracts/charter/schemas/PackDoctorReport.yaml` (JSON Schema, which also validates the YAML form) and
    `PackDoctorReport.xsd`. A contract test validates both against a run over the example packs.
  - An XUnit-compatible form is out of scope (R-n), and is recorded as a later option in the ADR.
- **D7 — `remediate` (WP07; R-j).**
  - A public facade, `PackRemediation`, in `packs.infra.storage` over `PackManifestDerivation`,
    `ManifestMembership.stamp`, `SerializationStrategy` and the round trip's canonical writer
    (`DomainRoundTrip`/`PackRoundTrip`). Only `pack-doctor`'s composition root names it.
  - **Precondition:** the pack's storage verdict, without the manifest gate and form findings, has no violation.
    Otherwise remediate exits 1 and writes nothing.
  - **Writes:** to a staged copy, then each file is replaced, so a failure leaves the pack as it was.
  - **After writing:** remediate re-runs `validate` and reports the result.
  - Stray files are untouched.
- **D8 — Contract versioning.**
  - `### Breaking` fragments:
    - the preset-item refusals (a valid input is now refused);
    - `manifest_stale` (an upload with stale hashes is now refused);
    - the report schema rename.
  - `### Changed` fragment: the report formats and the `CLOSURE_WALK` check value.
  - Core's regeneration consequence is stated in each fragment (ADR-0022).
- **D9 — Dependencies.** No new library. If Jackson's YAML or XML dataformat is not already a verified dependency of
  a module `pack-doctor` may use, the YAML writer reuses the service's existing YAML emitter, and the XML writer uses
  `javax.xml.stream`. DIRECTIVE_051: no supply-chain change, so no new verification metadata.
- **D10 — The ADR.** ADR-0083 records rulings R-a to R-n, and amends:
  - ADR-0075 (decision 6: the CLI composes the domain phase under `--full`; decision 7: the preset phase; decision 9:
    pack-doctor, its commands, `--report`);
  - ADR-0042 (the 2026-11-12 amendment: hashes are verified on upload);
  - rules A10/C31 (`docs/architecture/composition-and-activation-rules.md`);
  - the scope rows on `architectural-patterns-applied.md`.

  It records #535 and #52.3 as not the charter's to judge. ADR-0082 itself stays as it is.

## Charter Check

- **Canonical model (C-002):** the shapes are replaced outright: `presetIdsOf` → presets, `CompositionFault` deleted,
  pack-check → pack-doctor, no alias. Fixtures are fixed (FR-010). Pass.
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
├── domain/              PackCompositionResolver, EnhancementCompose, PackCompositionFacts, ClosureFault, PackCodes
├── application/         ClosureImpactCheck, OfferingReadService (call the domain forms)
└── infra/storage/       ManifestRules, StorageCodes, TreeCensus, PackRoundTrip, PackRemediation (new)
service/pack-validation/  PackSetCheck, PackSetVerdict (CLOSURE_WALK), roundtrip
service/pack-doctor/      (renamed from pack-check) PackDoctorMain, Arguments, Problems, ReportWriter, report/
contracts/charter/        schemas (RefusalCode, PackDoctorReport.yaml/.xsd), scenarios, changes/
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

### IC-03 — The preset-item rule
- **Purpose**: D3.
- **Relevant requirements**: FR-002, FR-003, FR-010, FR-011.
- **Affected surfaces**:
  - `PresetItemRules` (new), `PackRulesets`, `PackCompositionFacts` (declared), `PackCodes`, `RefusalCode`;
  - `ClosureImpactCheck`, `PackSetCheck`, `Problems`;
  - `CharterRefusalCode.yaml`, the registration scenarios;
  - the built-in and org-base example packs.
- **Sequencing/depends-on**: IC-01, IC-02.
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
  (renamed), `contracts/charter/changes/`, the open-packs drafts.
- **Sequencing/depends-on**: IC-01 to IC-07.
- **Risks**: the ADR number. Mitigation: check `main` and the open PRs when writing it.
