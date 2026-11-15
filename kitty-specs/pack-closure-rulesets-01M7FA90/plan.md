# Implementation Plan: ADR-0075 slice 5, the PACK and CLOSURE rulesets and one pipeline

**Branch**: `kitty/mission-pack-closure-rulesets-01M7FA90` → `slice-5-554-pack-closure-rulesets` → `main` (one pull
request) | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/pack-closure-rulesets-01M7FA90/spec.md`

**Base.** Rebased onto `main` at `67ddbe03`, after slice F (PR #567) merged on 2026-11-15. The implementation gate
(C-002) is open.

## Summary

Every pack check becomes a kernel rule over the `packs` domain objects:

- **Per kind.** One ruleset per pack file kind, composed into `PACK`.
- **Across packs.** One `CLOSURE` ruleset over the descriptors of a set of packs with levels.
- **One pipeline.** Validate mode, the registration load and the compose read all ask one port, which `packs.domain`
  declares, `pack-validation` implements and `deployable` wires.
- **One set of facts.** Every surface judges the `PackContents` that `FileSystemPackInspector` reads (P1). The store's
  reader keeps building the aggregate and no longer judges.
- **Deleted.** The converters, the string sub-codes, `PackFinding`, `PackFindingCode` and `PackPolicy`.
- **One wire change.** A file nothing models becomes a `file_not_held` warning and is not held (Q1).

Rulings: specify `DM-01M7FARH7YMGS2X0BRV1HKH47B` (Q1) and plan `DM-01M7FFDY3YP3VA3EA5VGRZF28V` (P1). Both are
recorded in the Decisions section below.

## Decisions

| ID | Decision | Source |
| --- | --- | --- |
| P1 | **Registration judges the inspector's `PackContents` of the staged tree.** The inspector gains the facts it lacks: value-object breaches (`domain`), reference faults, and the manifest and reserved-shard facts. The store's reader only builds the aggregate. A fault the reader meets that `PACK` did not report fails closed as `validation_failed`, and a test pins it. | Operator, `DM-01M7FFDY…` |
| P2 | **The port lives in `packs.domain`.** It is `PackRules`, with `Rationale pack(PackContents)` and `Rationale closure(ClosureFacts)`. `pack-validation`'s `PackRulesets` implements it, and `CharterWiring` wires it. Nothing in `:packs` main names `pack-validation`. | Ruling 1; C-003 |
| P3 | **Codes live in `packs.domain`, as kernel `ReasonCode` constants** (`PackCodes`). Each has its wire token and its severity (ruling 4). They replace `PackFindingCode` and every string kind. The rules in `pack-validation` and the mappers in `packs` name the same constants. | Ruling 4; FR-009 |
| P4 | **Two mappers in `packs.domain`, plus the compose read.** `PackVerdicts.of(Rationale)` sorts by today's `PRECEDENCE`. `PackRefusals.of(Rationale)` keeps the registration sequence. The compose read takes the first failure as `pack_unreadable`, as today. Ordering stays a plain function in each mapper. | Research D3 |
| P5 | **`CLOSURE` collects every fault.** The offering and `EdgeIndex` keep their single refusal: the first `CLOSURE` violation, in today's check order, with the same `RefusalPack` list. The 3 reference checks stay in `PackCompositionResolver` for #556. | Research D5 |
| P6 | **The checks a surface gains keep their validate-mode token.** Registration gains the level, org-fragment, duplicate-id, asset and descriptor rules, as violations with those tokens (`pack_level_mismatch`, `duplicate_artifact_id` and so on). Violation codes are free strings on the wire. Where the aggregate guard reported the same fault as `domain` today, the rule's token replaces it, and a scenario pins that. | Intent summary; research D6 |
| P7 | **The reserved shard keeps a code per surface.** `PACK` emits one `reserved_shard` finding. `PackVerdicts` reports it as `drg_schema_error`, as validate mode does today, and `PackRefusals` as `reserved_shard`. The mapping is one table, with a test. | Research D6 |

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain). The OpenAPI 3.1 contract is in `contracts/charter/`.
**Primary Dependencies**: no new dependency. The work uses the vendored `kernel.validation` (`Rule`, `ModularRuleset`,
`Rationale`, `ReasonCode`, `Location`), the `packs` domain, and `deployable`'s Spring wiring. `deployable` moves
`:pack-validation` from `testImplementation` to `implementation`; it is a project dependency, already verified.
**Storage**: pack files on disk, through `FileSystemPackInspector`, `FilesystemPackStore` and `WholePackStore`. No
change to storage. An unmodelled file joins the excluded files that are removed before the commit.
**Testing**:
- Per-rule and per-mapper unit tests in `:pack-validation:test` and `:packs:test`.
- A parameterised test over the planted packs (`packs/src/test/resources/planted/*/expected.json`), run through
  `PACK` on both surfaces (SC-001).
- The contract scenarios through `:deployable:test`.
- The parity tests (`VocabularyEqualityTest`, `WireTokenParityTest`, `ReservedRefusalCodesTest`, `RefusalHandlerTest`
  and `ExcludedFilesAgreementTest`), unchanged.
- ArchUnit (`HexagonalArchitectureTest`).
- `:packs:wholePackRoundTrip`.
- The whole gate, `./gradlew verifyAll --max-workers=1`, in the foreground, one run at a time.
- Nightly mutation floors (ADR-0062).

**Target Platform**: the charter service (a Linux container) and, after #478, the `pack-check` jar.
**Project Type**: multi-module service (`service/*`).
**Performance Goals**: a registration reads the staged tree twice: once by the inspector and once by the store's
reader (P1). That is accepted. No new budget is set, and the existing registration scenarios keep their timeouts.
**Constraints**: the canonical model, with no legacy reader (C-001). Module direction (C-003). The wire is unchanged
apart from Q1. Signed commits, rebase only.
**Scale/Scope**:
- About 9 per-kind rulesets, `PACK`, `CLOSURE`, 2 mappers and 1 port.
- Deletes `PackPolicy` (1,435 lines), `PackFinding`, `PackFindingCode`, the string kinds and about 15 converters.
- Rewrites about 17 test classes (474 tests, about 10,000 lines) into per-rule tests.
- About 4 contract scenarios rewritten for Q1.

## Charter Check

| Charter rule | Status |
| --- | --- |
| The charter's model is canonical; no legacy reader | Pass. The rules run over `packs.domain` objects. No string-to-code mapper is kept. `PackFinding` and `PackFindingCode` are deleted, not wrapped. |
| Tests and examples carry only the canonical shape | Pass. Invalid packs appear only as error-path fixtures. The planted packs are reused as they are. |
| Contract: 0.x may break on a minor, with `### Breaking` | Pass. Q1 is one dated `### Breaking` fragment. |
| No unverified dependency | Pass. No new external dependency. |
| Whole-pack round trip over the example packs | Pass. A file that is not held is not round-tripped (Q1). The example packs hold none. |
| Adversarial squad at the plan point and before merge | Plan point: one squad over this plan before `/spec-kitty.tasks`, with MAJORs folded. Before merge: one squad over the diff. |
| Issues stay small | Pass. Nothing new is filed (research). `Closes #554`; `Refs #355`. |

No violations, so Complexity Tracking is empty.

## Project Structure

### Documentation (this mission)

```text
kitty-specs/pack-closure-rulesets-01M7FA90/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── research/ (squad-similar-surfaces.md, squad-tracked-issues.md, squad-plan.md, open-packs-drafts/)
├── decisions/
└── tasks/ (from /spec-kitty.tasks)
```

### Source Code (repository root)

```text
service/packs/src/main/java/ai/speckitty/charter/packs/domain/
    PackRules.java            # port: pack(PackContents), closure(ClosureFacts)          (new)
    PackCodes.java            # ReasonCode constants, wire token + severity               (new; replaces PackFindingCode)
    PackVerdicts.java         # Rationale -> PackVerdict, PRECEDENCE order               (new)
    PackRefusals.java         # Rationale -> RefusalViolations, registration order        (new)
    ClosureFacts.java         # descriptors of a set of packs with levels                 (new)
    PackContents.java         # gains: domain breaches, reference faults, manifest and reserved-shard facts
    PackValidation.java       # validate(root) = PackVerdicts.of(rules.pack(inspect(root)))
    PackCompositionResolver.java  # keeps composition plain functions + the 3 reference checks (#556)
    PackPolicy.java, PackFinding.java, PackFindingCode.java, PackElementViolation kinds-as-strings   # deleted
service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
    FileSystemPackInspector.java  # gains the facts of P1
    WholePackStore.java       # loadStaged: inspect staged -> rules.pack -> PackRefusals; reader builds aggregate; fail-closed guard
    DomainRepositoryService.java, FilesystemPackStore.java   # stop judging; breaches become inspector facts
service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/
    PackRulesets.java         # implements PackRules; builds PACK and CLOSURE
    rules/{Descriptor,Level,OrgFragment,Graph,MissionType,Step,Artifact,Asset,Fragment,PromptSlot,Glossary,
           Manifest,ReservedShard,NotHeld}Rules.java
    closure/{Cycle,Dependency,Parent,Level,Conflict}Rules.java
service/deployable/.../CharterWiring.java   # wires PackRulesets as PackRules
contracts/charter/changes/2026-11-1x-unmodelled-files-are-not-held.md   # ### Breaking (Q1)
contracts/charter/scenarios/registration/whole-pack-registration.feature, prompt-fragment-refusals.feature
```

**Structure Decision**: the existing modules. No new module.

## Complexity Tracking

None.

## Implementation Concern Map

### IC-01 — Codes and the port in the packs domain

- **Purpose**: give rules and mappers one typed vocabulary of codes, and a port the packs slice can call without a
  cycle.
- **Relevant requirements**: FR-006, FR-009; P2, P3.
- **Affected surfaces**:
  - `packs.domain`: `PackCodes`, `PackRules` and `ClosureFacts`;
  - `Diagnostic.Code`;
  - `pack-validation/build.gradle.kts` and `deployable/build.gradle.kts`.
- **Sequencing/depends-on**: none.
- **Risks**:
  - Two codes share one token: `PROMPT_SLOT_NOT_EMPTY` and the unversioned warning both send `prompt_slot_not_empty`.
    They become two `ReasonCode`s. The mapper keeps the token, and a test pins it.
  - `ReasonCode` equality is the value and severity pair.

### IC-02 — The inspector reads every fact a rule needs (P1)

- **Purpose**: make `PackContents` the one set of facts, so that no rule parses and registration can judge what the
  inspector read.
- **Relevant requirements**: FR-002, FR-006, NFR-004.
- **Affected surfaces**:
  - `FileSystemPackInspector`;
  - `PackContents`, which gains the domain breaches, the reference faults, the manifest listing and the reserved-shard
    stamp;
  - the shared readers in `packs.infra.storage`.
- **Sequencing/depends-on**: IC-01.
- **Risks**:
  - The inspector and the store's reader must agree on every fault. The fail-closed guard (IC-05) and the
    planted-pack parity test (IC-07) catch any gap.
  - `ExcludedFilesAgreementTest` must stay green.

### IC-03 — The per-kind rulesets and PACK

- **Purpose**: express every per-pack check once.
- **Relevant requirements**: FR-001, FR-002, FR-004, FR-010, FR-011.
- **Affected surfaces**: `pack-validation` `rules/*` and `PackRulesets.pack`.
- **What each ruleset covers**: today's checks in `PackPolicy`, `StepEnhancementRules`, `RelationshipEndpointRule`
  (F; the graph rule calls F's bridge) and the mission-type rules (F). Also:
  - the load-only checks: manifest membership as a `when` that holds when nothing else failed, the reserved shard,
    and orphan fragments;
  - `file_not_held` for files that are unmodelled or excluded (Q1).
- **Plain functions**: classification, `PRECEDENCE` and `ManifestMembership.mismatch` stay plain functions that the
  rules call.
- **Sequencing/depends-on**: IC-01, IC-02.
- **Risks**:
  - A rule that would re-parse a file. Review checks it, and a test runs a rule with no file system reachable.
  - Each mapper reads `Rationale.failures()`, never `details()`, which also holds the passes.

### IC-04 — CLOSURE

- **Purpose**: report every fault in the composition of a set of packs, without the graph.
- **Relevant requirements**: FR-003; P5.
- **Affected surfaces**:
  - `pack-validation` `closure/*` and `PackRulesets.closure`;
  - `PackCompositionResolver`, whose 8 throw sites become rules, while its ordering and precedence stay functions;
  - its callers: `OfferingReadService`, `EdgeIndex` and `CompositeComponentResolver`.
- **Sequencing/depends-on**: IC-01.
- **Risks**:
  - The offering's refusal must stay byte-equal: the first violation in today's check order, and the same pack list.
    A test runs every resolver refusal case through both the old expectations and the new mapping.

### IC-05 — One pipeline, and the deletions

- **Purpose**: every surface asks `PackRules`, and the glue goes.
- **Relevant requirements**: FR-005, FR-006, FR-007, FR-008; P4, P6, P7.
- **Affected surfaces**:
  - `PackValidation`;
  - `WholePackStore.loadStaged`, which inspects the staged tree, judges it, maps it with `PackRefusals`, then loads
    the aggregate, with the fail-closed guard;
  - `DomainRepositoryService.assembly` and `readable` (the compose read);
  - `FilesystemPackStore`;
  - `PackRegistrationService`, whose diagnostics come from `PackVerdicts`;
  - `CharterWiring`.
- **What is deleted**: `PackPolicy`, `PackFinding`, `PackFindingCode`, the string kinds, the converters listed in
  research §2.2, and the `invalid` fallback.
- **Sequencing/depends-on**: IC-03, IC-04.
- **Risks**:
  - The registration order (research D3).
  - Manifest membership stays gated on no other fault.
  - #555 removes `LoadedPack.unmodelled()` on its side. Whichever lands second drops the call.

### IC-06 — An unmodelled file is not held (Q1)

- **Purpose**: warn about an author aid and ignore it.
- **Relevant requirements**: FR-010, FR-013, FR-014.
- **Affected surfaces**:
  - the `NotHeld` rule;
  - `WholePackStore`'s removal of files that are not held (as #451 does);
  - `DiagnosticCode.yaml`'s description;
  - `CharterRefusalCode.yaml`;
  - a `### Breaking` change fragment;
  - `whole-pack-registration.feature`, with the `notes.txt` scenarios rewritten and a second fault that still
    refuses;
  - the open-packs draft `open-packs#TBD-unmodelled-not-held`.
- **Sequencing/depends-on**: IC-03, IC-05.
- **Risks**:
  - A typo'd artifact file is now only a warning (spec edge case).
  - The drift test still fails on such a file, as the #555 relay says.

### IC-07 — Tests, floors and records

- **Purpose**: one test per rule and one per mapper, proof that both surfaces agree, and honest floors.
- **Relevant requirements**: FR-012, FR-013; NFR-002, NFR-003, NFR-005; SC-001, SC-002.
- **Affected surfaces**:
  - the 17 collapsing classes (research §5);
  - a parameterised planted-pack test;
  - the mutation floors in `pack-validation/build.gradle.kts` and `packs/build.gradle.kts`;
  - ADR-0062, ADR-0075 and ADR-0065 annotations;
  - #554's issue text, amended to keep the inspector, at landing.
- **Sequencing/depends-on**: alongside each IC; the floors are measured last.
- **Risks**:
  - Mutation margin 0 today. The per-rule tests land with each ruleset, and the nightly mutation task runs before the
    pull request is marked ready.
