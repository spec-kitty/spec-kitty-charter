# Implementation Plan: ADR-0075 slice 5, the PACK and CLOSURE rulesets and one pipeline

**Branch**: `kitty/mission-pack-closure-rulesets-01M7FA90` → `slice-5-554-pack-closure-rulesets` → `main` (one pull
request, R4) | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/pack-closure-rulesets-01M7FA90/spec.md`

**Base.** Rebased onto `main` at `97985727a`, which holds slice F (#567), #555 (#578) and slice G (#573). The branch
rebases again whenever `main` moves (C-002).

## Summary

Every pack check becomes a kernel rule over the `packs` domain objects, in the `packs` domain (R3):

- **Per kind.** One ruleset per pack file kind, in `packs.domain.rules`.
- **`PACK`.** The per-kind rulesets composed into one `PACK`, with a surface profile that chooses the rules each surface
  runs (R1).
- **`CLOSURE`.** Collect-all composition checks over each pack's composition facts.
- **One code table.** `PackCodes` gives one code and one wording per fault on every surface (R2).

Validate mode, registration and the compose read run the same rules. Registration judges the inspector's contents of
the staged tree before the store's reader builds the aggregate, and a reader-only fault fails closed (P1).

**What is deleted:** the converters, `PackFinding`, `PackFindingCode`, `PackPolicy` and the string kinds.

**What changes on the wire:**
- an unmodelled file becomes a `file_not_held` warning (Q1);
- the per-fault code unification (R2), each change under `### Breaking`.

`pack-validation` stays the Spring-free check library that pack-check runs. #554 adds no main code to it. ADR-0075 is
amended.

## Decisions

| ID | Decision | Source |
| --- | --- | --- |
| Q1 | An unmodelled file is a `file_not_held` warning, and it is not held. | `DM-01M7FARH…` |
| P1 | Registration judges the inspector's `PackContents` of the staged tree. The store's reader only builds the aggregate, and a reader fault `PACK` missed fails closed. | `DM-01M7FFDY…` |
| R1 | One `PACK`, with a surface profile. Registration and the compose read keep the faults they report today. Validate mode gains the load-only checks. | `DM-01M7G73S…` |
| R2 | One fault answers one code and one wording. The specific code wins over a catch-all, and each surface that changes gets `### Breaking`. | `DM-01M7G73V…` |
| R3 | The rules live in `packs.domain`, with no judging port. `pack-validation` is pack-check's library. ADR-0075 is amended ([design](research/design-r3.md), with its [challenge](research/design-r3-challenge.md)). R3-b (i): the margins are re-set from the nightly run. | `DM-01M7G73X…` |
| R4 | One pull request, with ordered work packages, each green. | `DM-01M7G740…` |
| P2–P7 (2026-11-15 plan) | **Superseded** by R1 to R3: the `PackRules` port, the `ClosureFacts` type and the per-surface code tables are gone. | — |

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain). OpenAPI 3.1 contract in `contracts/charter/`, and the packs contract
in `contracts/packs/`.
**Primary Dependencies**: no new dependency.
- The vendored `kernel.validation` (`Rule`, `ModularRuleset`, `Rationale`, `Reason`, `ReasonCode`, `Location`).
- The `packs` slice, unchanged in its modules.
- `deployable` keeps `:pack-validation` as `testImplementation`.

**Storage**: pack files on disk, through `FileSystemPackInspector`, `FilesystemPackStore` and `WholePackStore`. The
files that are not held (excluded or unmodelled) are removed before the commit, as #451 does.
**Testing**: the tests live in the module of their subject (ADR-0062).
- **Rule, composition, mapper, code-coverage and subset tests** (SC-001, NFR-005) in `:packs:test`, with `planted/`
  moved to `:packs` testFixtures.
- **The golden snapshot** of every surface (NFR-002), in `:packs:test` and `:deployable:test`.
- **The contract scenarios**, through `:deployable:test`.
- **ArchUnit** (`HexagonalArchitectureTest`).
- **The whole-pack gate**, `:pack-validation:wholePackRoundTrip`, as #578 set it.
- **A narrowed PIT run per work package**, and the nightly floors.
- **`./gradlew verifyAll --max-workers=1`**, in the foreground, one run at a time.

**Target Platform**: the charter service (a Linux container, and the native image), and later the `pack-check` jar
(#478).
**Project Type**: multi-module service (`service/*`).
**Performance Goals**:
- Registration reads the staged tree twice: once by the inspector and once by the reader (P1). That is accepted.
- The compose read does not read a pack again: it runs the rules on its own read.
- No new budget is set, and the registration scenarios keep their timeouts.

**Constraints**: the canonical model, with no legacy reader (C-001); the module direction (C-003); the wire changes
only as Q1 and R2 list; signed commits, rebase only.
**Scale/Scope**:
- About 12 per-kind rule classes, `PackRulesets`, `CompositionRules`, `PackCodes` and 2 mappers in `packs.domain`.
- A Spring-free assembly class and a manifest derivation in `packs.infra.storage`.
- Deletes `PackPolicy`, `PackFinding`, `PackFindingCode`, the string kinds and about 15 converters.
- About 82 test files are touched.
- About 4 scenarios are rewritten for Q1, plus the rows R2 changes.

## Charter Check

| Charter rule | Status |
| --- | --- |
| The charter's model is canonical; no legacy reader | Pass. The rules run over `packs.domain` objects in the `packs` domain. No old-string mapper or per-surface table is kept. |
| Tests and examples carry only the canonical shape | Pass. Invalid packs appear only as error-path fixtures. `planted/*/expected.json` moves to the new tokens and severities, and `core_verdicts.py` follows. |
| Contract: 0.x may break on a minor, with `### Breaking` | Pass. There are dated fragments for Q1, for registration's R2 rows and for validate mode's R2 rows. |
| No unverified dependency | Pass. No new external dependency. |
| The whole-pack round trip over the example packs | Pass. A file that is not held is not round-tripped. |
| Adversarial squad at the plan point and before merge | Plan point: done ([squad-plan.md](research/squad-plan.md), and the R3 design squad with its challenge). Before merge: one squad over the diff. |
| Issues stay small | Pass. Nothing new is filed. `Closes #554`, `Refs #355`. |

No violations.

## Project Structure

### Documentation (this mission)

```text
kitty-specs/pack-closure-rulesets-01M7FA90/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── research/ (squad-*.md, design-r3.md, design-r3-challenge.md, golden/, open-packs-drafts/)
├── decisions/
└── tasks/
```

### Source Code (repository root)

```text
service/packs/src/main/java/ai/speckitty/charter/packs/domain/
    PackCodes.java                    # ReasonCode: token, severity, wording (replaces PackFindingCode + string kinds)
    PackVerdicts.java, PackRefusals.java   # Rationale -> verdict / problem + diagnostics; order functions
    PackValidation.java               # inspect -> PackRulesets.pack(VALIDATE) -> PackVerdicts
    PackCompositionResolver.java      # collect-all composition checks; the offering's first refusal in today's order
    rules/
        PackRulesets.java             # PACK(profile), CLOSURE; Surface {VALIDATE, REGISTER, COMPOSE_READ}
        {Descriptor,Level,OrgFragment,Graph,MissionType,Step,Artifact,Asset,Fragment,PromptSlot,Glossary,
         Manifest,ReservedShard,NotHeld}Rules.java
        CompositionRules.java         # CLOSURE over PackUnderResolution
        GraphBinding.java, Wording.java, Precedence.java   # PackPolicy's residue as plain functions
    PackPolicy.java, PackFinding.java, PackFindingCode.java   # deleted
service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
    PackAssembly.java, PackManifestDerivation.java   # Spring-free, not beans (F2)
    PackCompositionFacts (from PackContents)          # Spring-free builder (F5)
    FileSystemPackInspector.java      # gains the A-M1 facts; no symlinks on the registration path (F4)
    WholePackStore.java               # loadStaged reads and builds only; returns the reader's faults
    DomainRepositoryService.java      # compose read runs PACK(COMPOSE_READ) on its own read; withholding kept
service/packs/src/main/java/ai/speckitty/charter/packs/application/
    PackRegistrationService.java      # inspects staged -> PACK(REGISTER) -> PackRefusals; guard; then loadStaged
    OfferingReadService.java          # first composition refusal, today's order
service/deployable/src/test/.../HexagonalArchitectureTest.java   # A–C re-pointed; PACK_CODES_HAVE_ONE_OWNER; PACK_RULES_READ_NO_FILE
docs/adr/0075-*.md                    # amendment 2026-11-16 (R3)
contracts/charter/changes/2026-11-16-*.md   # ### Breaking: Q1; registration R2; validate R2
```

**Structure Decision**: the existing modules. No module is added, and no main dependency changes.

## Complexity Tracking

None.

## Implementation Concern Map

### IC-01 — Golden snapshot and the R2 code table

- **Purpose**: freeze what every surface answers today, and decide each fault's one code and wording, before anything
  changes.
- **Relevant requirements**: NFR-002, FR-009, SC-002.
- **Affected surfaces**:
  - a characterization test over every planted, example and scenario pack: validate verdict, registration problem
    and diagnostics, compose-read 500 detail, offering refusal;
  - `planted/` moved to `:packs` testFixtures;
  - the R2 table in `data-model.md`, checked against the snapshot.
- **Sequencing/depends-on**: none. It comes first.
- **Risks**: the snapshot must be taken on today's code. Any later diff must cite Q1 or an R2 row.

### IC-02 — Q1 on today's pipeline

- **Purpose**: an unmodelled file becomes a `file_not_held` warning and is not held, before the pipeline moves.
- **Relevant requirements**: FR-010, FR-013, FR-014; SC-003.
- **Affected surfaces**:
  - `WholePackStore` (`notHeld()` replaces `unmodelled()`), and its callers in #578's harness and the drift test;
  - `ExcludedFilesAgreementTest`;
  - the scenarios (C-10's list);
  - the unreleased 2026-11-11 fragment;
  - `DiagnosticCode.yaml`, `packs_packId.yaml` and the packs contract wording;
  - one `### Breaking` fragment;
  - the open-packs draft.
- **Sequencing/depends-on**: IC-01.
- **Risks**: after Q1, a manifest mismatch beside `notes.txt` is now reported. The fragment says so.

### IC-03 — PackPolicy's residue and the descriptor function

- **Purpose**: move the members other classes use into named `packs.domain` functions, so that `PackPolicy` can go.
- **Relevant requirements**: FR-004; A-M5.
- **Affected surfaces**: `TENSION`, `joinsItself`, `bodyPathEscape`, `assetPathEscape`, `registeredGraph` and
  `descriptorFindings`, and their roughly 20 callers.
- **Sequencing/depends-on**: none.
- **Risks**: none beyond mechanics.

### IC-04 — PackCodes and the unified codes

- **Purpose**: give one typed code table, and make every surface speak it (R2).
- **Relevant requirements**: FR-009, FR-013; ruling 4.
- **Affected surfaces**:
  - `PackCodes`;
  - `PackElementViolation.kind` and `PackFinding.code` become `ReasonCode`;
  - the `invalid` fallback goes;
  - the R2 table's changes on each surface, with two `### Breaking` fragments;
  - `planted/*/expected.json` and `core_verdicts.py`.
- **Sequencing/depends-on**: IC-01, IC-02.
- **Risks**: the two `prompt_slot_not_empty` codes. The mappers switch on the constant, and a token-grammar test is
  added.

### IC-05 — Inspector facts, the Spring-free assembly and the composition facts

- **Purpose**: make `PackContents` hold every fact a rule needs (P1, A-M1), and build composition facts without
  Spring (F5).
- **Relevant requirements**: FR-006, FR-015, NFR-004.
- **Affected surfaces**:
  - `FileSystemPackInspector` and `PackContents`;
  - `PackAssembly` and `PackManifestDerivation` in `infra.storage` (F2), shared with `DomainRepositoryService` and
    the manifest stamp;
  - the builder of composition facts, from pack contents.
- **Sequencing/depends-on**: IC-03, IC-04.
- **Acceptance**:
  - the A-M1 checklist, with one fixture per row;
  - `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` stays green;
  - the composition-facts parity test passes over the example packs;
  - no symlinks are followed on the registration path (F4).

### IC-06 — Rules, PACK with surface profiles, and the mappers

- **Purpose**: express each per-pack check once, in the kernel vocabulary, in `packs.domain.rules`.
- **Relevant requirements**: FR-001, FR-002, FR-008, FR-011, FR-012.
- **Affected surfaces**:
  - `packs.domain.rules`, carved from `PackPolicy`, `StepEnhancementRules` and `RelationshipEndpointRule`;
  - `PackVerdicts` and `PackRefusals`, with the order functions and the diagnostics order (C-9);
  - the manifest gate, as a plain sequence.
- **Sequencing/depends-on**: IC-04, IC-05.
- **Risks**: the composition interactions (M-7). Each gets a composition test, and they are listed in the tasks.

### IC-07 — CLOSURE

- **Purpose**: collect every composition fault, and keep the offering's first refusal.
- **Relevant requirements**: FR-003.
- **Affected surfaces**:
  - `PackCompositionResolver`, whose throws become collect-all functions;
  - `CompositionRules`;
  - `OfferingReadService`, which takes the first refusal in today's interleaved order, with its `RefusalPack` list.
- **Out of scope**: the reference and case-only checks, which go to #556.
- **Sequencing/depends-on**: IC-03, IC-04.
- **Risks**: the first refusal must stay byte-equal. Multi-fault fixtures check it against the snapshot.

### IC-08 — The switch, the deletions and ArchUnit

- **Purpose**: every surface runs `PACK`, and the glue goes.
- **Relevant requirements**: FR-005, FR-006, FR-007; SC-001, SC-004.
- **Affected surfaces**:
  - validate mode;
  - `PackRegistrationService` judging, with the guard ahead of the gate and the impact check;
  - `PackSourceStagers` calling through registration;
  - `loadStaged` as a reader that returns the reader's faults;
  - the compose read under `PACK(COMPOSE_READ)`.
- **What is deleted**: `PackPolicy`, `PackFinding`, `PackFindingCode` and the converters.
- **ArchUnit**: rules A to C re-pointed, plus `PACK_CODES_HAVE_ONE_OWNER` and `PACK_RULES_READ_NO_FILE`.
- **Sequencing/depends-on**: IC-06, IC-07. It is atomic.
- **Risks**:
  - The store tests that assert refusals move to registration and rule tests.
  - Both subset tests must hold.

### IC-09 — Tests, floors and records

- **Purpose**: collapse the old tests, cover every code, re-set the margins honestly and amend the records.
- **Relevant requirements**: FR-012, FR-013; NFR-003, NFR-005; SC-005.
- **Affected surfaces**:
  - the collapsing test classes;
  - the code-coverage test;
  - the narrowed and nightly PIT runs, with the margins in `pack-validation/build.gradle.kts`, `packs/build.gradle.kts`
    and ADR-0062 re-set from measurement (R3-b (i));
  - the ADR-0075 amendment (R3) and the ADR-0065 annotation;
  - #554's issue text, and the closeout.
- **Sequencing/depends-on**: IC-08.
- **Risks**: the `packs` group gains the rules' mutants. A narrowed PIT run per work package catches a dip early.
