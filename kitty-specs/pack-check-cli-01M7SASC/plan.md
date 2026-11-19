# Implementation Plan: pack-check CLI and its service release

**Branch**: `kitty/mission-pack-check-cli-01M7SASC` (merge target `feat/478-pack-check`; draft PR #608 to `main`) |
**Date**: 2026-11-19 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/pack-check-cli-01M7SASC/spec.md`, rulings D1 to D4 (2026-11-19).

## Summary

`service/pack-check` becomes a runnable, Spring-free jar: a thin `main` over `pack-validation`.

- **Per pack** it runs validate mode (`PackCheck`) through the one reader (`FileSystemPackInspector`).
- **Over the set** it runs `CLOSURE` (`PackRulesets.closure(presetIdsOf)`) on the facts the one read already
  carries.
- **With `--round-trip`** it runs `PackRoundTrip` and `WholePackRoundTripRule` with no baseline.
- **Output.** It writes a `PackCheckReport` (an additive contract schema) whose problems are the API's own, and exits
  `0`, `1` or `2`.
- **D1.** A storage rule refuses every link inside a pack root (`pack_entry_linked`), so registration refuses it too
  (#597).
- **Release.** `service-release.yml` assembles `pack-check-<ver>.jar` with its checksum and attestation (ADR-0046
  amended). A follow-up release PR cuts `service-v0.1.0-rc.2` (D4).

Prototypes on 2026-11-19 ran validate mode, the facts builder plus `CLOSURE`, and the round trip over charter's example
packs on a classpath of 20 jars with no Spring, Tomcat, web transport or generated contract: every path answered, and
no Spring class loaded (research.md R1).

## Technical Context

**Language/Version**: Java 25 (Temurin), Gradle with the repository's build-logic conventions
**Primary Dependencies**: `:pack-validation`, `:packs` (domain, one reader, round trip), kernel validation; Jackson 3
(`tools.jackson.core:jackson-databind`, already allowlisted for pack-check) to write JSON; no new external dependency
**Storage**: local files only (pack directories; a temporary directory for the round trip's written side)
**Testing**: JUnit 5 and AssertJ unit tests in `pack-check` and `pack-validation`; a jar test that runs the built jar
in a child JVM; ArchUnit in `deployable` (`HexagonalArchitectureTest`); the contract test validating the report against
the schema with networknt; `ValidateGoldenTest` parity; a planted-link test in `packs.application` (#597); the release
rehearsal script
**Target Platform**: any host with Java 25 (the jar is not native-built; ADR-0046's natives are the service's)
**Project Type**: single multi-module Gradle project
**Performance Goals**: NFR-002, all example packs as a set with `--round-trip` under 20 s on a CI runner
**Constraints**: no Spring, Tomcat, Jakarta Servlet or generated-contract class in the jar (NFR-001); jar under 40 MB
(NFR-004; the lean classpath is 8.8 MB); strict SHA-256 dependency verification (C-004)
**Scale/Scope**: one new module main (about 6 classes), the readout's facts, one storage rule and code, one
contract schema plus one code, workflow and script edits, three docs pages

## Charter Check

| Charter rule | How this plan meets it |
|---|---|
| Divergence from the charter's own model never goes wrong | The CLI composes the service's own rules; no check is reimplemented. `ValidateGoldenTest` parity (NFR-003) and the `CLOSURE` set tests pin it |
| The OpenAPI contract is honored; changes follow ADR-0022 | `PackCheckReport` is additive; `pack_entry_linked` is a new refusal violation, recorded under `### Breaking` on the 0.x line with a `changes/` fragment |
| Round trip is lossless, proven on example packs | Unchanged gate; `--round-trip` reuses the production flow and rule |
| No unverified dependency | No new dependency: the SLF4J no-op provider is in `slf4j-api` already, selected by a service file |
| The model is canonical; tests use the canonical shape | New fixtures are canonical example packs; links and broken packs appear only in error-handling tests |
| Adversarial squad at plan and before merge (contract and architecture change) | A plan squad runs before tasks (research.md §Squad); the independent adversarial review is requested from the orchestrator at for_review |
| `verifyAll` on Java 25, style gates, whole-pack, docs checks | NFR-005; every work package ends green on `verifyAll` |
| Issues stay small | #597 folds in (D1) and closes with evidence; no follow-up filed below MEDIUM |

No violation to justify.

## Project Structure

### Documentation (this mission)

```
kitty-specs/pack-check-cli-01M7SASC/
├── spec.md
├── plan.md              # this file
├── research.md          # prototypes, decisions, squad dispositions
├── data-model.md        # the report, the pack-set check and the link rule
├── quickstart.md        # running pack-check
├── contracts/           # the PackCheckReport schema draft and the CLI's command-line contract
└── tasks.md             # /spec-kitty.tasks
```

### Source Code (repository root)

```
service/pack-check/
├── build.gradle.kts                              # application jar task, Spring-free runtime classpath, jar test
└── src/
    ├── main/java/ai/speckitty/charter/packcheck/
    │   ├── PackCheckMain.java                    # composition root: names the adapters, maps exit codes
    │   ├── Arguments.java                        # --config-root | --pack dir[:level] ... | --round-trip; usage faults
    │   ├── ConfigRoot.java                       # <root>/packs/<name>/ discovery, levels from pack.yaml
    │   ├── ReportWriter.java                     # PackCheckReport → JSON (Jackson), warnings → stderr
    │   └── Problems.java                         # Rationale/CompositionFault → contract Problem, 100-violation cap
    ├── main/resources/META-INF/services/org.slf4j.spi.SLF4JServiceProvider
    └── test/java/...                             # unit tests, jar test, contract test
service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/
    ├── PackCheck.java                            # gains check(root) → readout + verdict; validate delegates
    └── set/PackSetCheck.java, PackSetVerdict.java # per-pack PackCheck, then CLOSURE + presets, D3 skip
service/packs/src/main/java/ai/speckitty/charter/packs/
    ├── domain/PackReadout.java                   # gains Facts (Built | Unreadable), filled by the one read
    ├── domain/PackRefusalMapping (moved from application.PackRefusals)
    └── infra/storage/rules/LinkRules.java (+ StorageCodes.PACK_ENTRY_LINKED)   # D1
service/deployable/src/test/.../HexagonalArchitectureTest.java   # PackCheckMain may name the adapters; controls
contracts/charter/schemas/PackCheckReport.yaml (+ CharterRefusalCode docs, changes/, CHANGELOG)
scripts/release/service-release-assets.sh, rehearse-service-release.sh
.github/workflows/service.yml (pack-check jar job), service-release.yml (assemble)
docs/adr/0046-*.md, docs/adr/0075-*.md, docs/guides/service-build.md, docs page for pack authors
```

**Structure Decision**: the existing multi-module layout. The pack-set check is a validation, so it goes in
`pack-validation` beside `PackCheck`. Only what the CLI alone needs (arguments, config-root discovery, problem mapping,
JSON, exit codes) goes in `pack-check` (FR-009).

## Complexity Tracking

None.

## Implementation Concern Map

### IC-01 — The link rule in the one reader (D1, #597)

- **Purpose**: refuse every symbolic link inside a pack root with `pack_entry_linked`, so `pack-check` and registration
  refuse it alike.
- **Relevant requirements**: FR-008, SC-004
- **Affected surfaces**:
  - `packs.infra.storage.rules`: a `LinkRules` over the census's `LINK` entries in `StorageRulesets.holding()`;
  - `StorageCodes`, the R2 table in `docs/development/notes/pack-code-unification.md`, and the goldens
    (`surfaces.json`, `validate.json`);
  - a `planted/` subject, plus a `packs.application` registration test that registers a staged tree with a file link,
    a directory link and an unmodelled link (#597's acceptance);
  - contract: `CharterRefusalCode.yaml` docs, a `changes/` fragment, CHANGELOG `### Breaking`, and the
    `whole-pack-registration.feature` scenario;
  - an open-packs draft issue in `research/` (its Python mirror).
  - `PackFileSet.walk` drops links from `regular` (D6), so no read on any surface follows a link; the store half
    states `pack_entry_linked` (D6 A), so the configured load refuses the pack too.
- **Sequencing/depends-on**: none
- **Risks**:
  - A pack root that is itself a link must still read (#174); a test pins it, and a link to a directory inside the
    pack too.
  - Service behaviour changes for a configured pack holding a link (D6): check open-packs and `packs/` for committed
    symlinks before landing.
  - Once the walk drops links, no other finding can arise at a link's path, so no precedence filter is needed (S2).

### IC-02 — The facts ride on the one read (squad S1)

- **Purpose**: let the validation edge get each pack's composition facts from the read it already made, with no new
  port and no second read.
- **Relevant requirements**: FR-003, FR-007
- **Affected surfaces**:
  - `packs.domain.PackReadout` gains `facts`, a sealed `PackReadout.Facts`: `Built(PackCompositionFacts)` or
    `Unreadable(RefusalCode, detail)`;
  - `FileSystemPackInspector.read` fills it with `CompositionFactsBuilder.composeFacts(id, one.store())`, catching the
    builder's `CharterRefusal`, and mapping `Assembly.None` to `pack_unreadable`;
  - every `PackReadout` construction site (tests, registration);
  - `CompositionFactsBuilderTest` keeps pinning the facts.
- **Sequencing/depends-on**: none
- **Risks**:
  - Registration now computes facts it ignores: a cost on every upload, measured against the registration budget
    tests.
  - Alternatively, compute them lazily (a memoized supplier) if the budget moves.

### IC-03 — The pack-set check in pack-validation

- **Purpose**: per pack, validate mode; over the set, `CLOSURE` with the preset phase, skipped when any pack has no
  facts (D3); the verdict names the checks that ran.
- **Relevant requirements**: FR-002, FR-003, FR-004, FR-007, US2
- **Affected surfaces**:
  - `PackCheck` gains a `check(root)` that returns readout and verdict, so the preset ids come from the same read;
  - `packvalidation.set` holds `PackSetCheck` and `PackSetVerdict`;
  - the round-trip step (temporary target, deleted after);
  - mutation floor group `pack-validation`.
  - `PackRulesets.closure(presetIdsOf)` (= `closure().must(CompositionRules.presets(...))`, squad S4), the one
    definition of `CLOSURE` the CLI runs.
- **Sequencing/depends-on**: IC-02
- **Risks**:
  - D5: `CLOSURE` runs over the set as given, so a dependent pack is checked with its dependencies; an acceptance test
    uses a dependent pack.
  - #556 later extends `CLOSURE` in `PackRulesets`, with no change here.

### IC-04 — The CLI module: main, arguments, report, problems, exit codes

- **Purpose**: the composition root and everything only the CLI needs.
- **Relevant requirements**: FR-001, FR-005, FR-006, FR-009, edge cases
- **Affected surfaces**:
  - `service/pack-check` main and tests, and its `build.gradle.kts` (the javadoc skip block goes);
  - `HexagonalArchitectureTest`: `PackCheckMain` alone (by FQCN) may name `FileSystemPackInspector` and
    `PackRoundTrip`, with a known-good control that another `packcheck` class still may not (S5);
  - the Rationale→`CharterRefusal` mapping moves from `packs.application.PackRefusals` to `packs.domain`, and both
    surfaces call it (S3);
  - `pack-check`'s `ModuleDependencyAllowlist` gains `:packs`; duplicate `--pack` is detected by `toRealPath`;
  - `charter.mutation-conventions` with a measured floor (J9).
- **Sequencing/depends-on**: IC-03
- **Risks**:
  - The problem must equal what `RefusalHandler` renders for `getPackRegistration`. A deployable test compares the
    whole problem for one fixture per code family, and title and status for every code the CLI can emit (the decision
    9 contract test).
  - Status per code is a small table in the CLI, pinned by that test.

### IC-05 — The report schema and its contract test

- **Purpose**: `PackCheckReport` as an additive contract schema; every problem is the shared `Problem`.
- **Relevant requirements**: FR-005, D2
- **Affected surfaces**:
  - `contracts/charter/schemas/PackCheckReport.yaml` (draft in `contracts/`), listed in `_index.yaml`;
  - a named `STANDALONE` exemption in `contracts/.../lint/ReachableContractFilesTest`, which asserts the file exists
    and stays unreached;
  - a `changes/` fragment (`### Added`); the CHANGELOG is assembled at release;
  - a `service/deployable` test that validates real CLI output with `ContractConformance.schemaViolations`, for a
    valid set, a pack fault and a composition fault, and pins title and status to `RefusalHandler` (R6).
- **Sequencing/depends-on**: IC-04
- **Risks**:
  - `ReachableContractFilesTest` refuses a schema no operation references. The exemption is named and self-checking.
  - `ContractConformance` refuses `const`, `uniqueItems`, `anyOf` and `$defs`. The draft uses none.

### IC-06 — The Spring-free jar and its proof

- **Purpose**: build `pack-check-<version>.jar` as one runnable file without Spring, and prove it runs on every path.
- **Relevant requirements**: NFR-001, NFR-002, NFR-004, US4
- **Affected surfaces**:
  - `service/pack-check/build.gradle.kts`: a jar configuration that excludes by module (`spring-boot-starter-webmvc`,
    `:web-transport`, `:charter-api`) and resolves consistently with `runtimeClasspath`, judged by the allowlist (J5,
    J6);
  - a `Jar` task writing to `build/pack-check/` (J7), with:
    - `Main-Class` and `Multi-Release: true`;
    - no `module-info.class`, no `:packs` resolver service, no source `MANIFEST.MF`;
    - the generated SLF4J NOP service file, added only here (J1);
    - licences relocated per artifact by a configuration-cache-safe task, with checked-in texts for re2j,
      json-schema-validator, itu and snakeyaml-engine, failing when one is missing (J8);
    - `DuplicatesStrategy.FAIL`;
  - a tagged `jarTest` (`Test` over the `test` source set, wired into `check`) that runs the jar with the toolchain
    launcher in a clean environment (J3, J10):
    - for a valid set, a pack fault, a composition fault, a planted link, a schema-invalid file and a usage error, with
      `--round-trip` where it applies;
    - asserting the exit code, the schema-valid stdout and an empty stderr (or the expected warnings);
    - with a marker-file test for links (S2);
    - with the jar-entry deny list (J4) and a byte-identical double build (J12).
- **Sequencing/depends-on**: IC-04
- **Risks**:
  - A class reached only reflectively from an excluded module fails at run time only. The failure-path runs are the
    guard.

### IC-07 — Release wiring and the ADRs

- **Purpose**: put the jar on the `service-v*` release and record it.
- **Relevant requirements**: FR-010, FR-011, FR-012, SC-003
- **Affected surfaces**:
  - `.github/workflows/service.yml`: the jar job also builds `:pack-check:packCheckJar`, runs it over a fixture and
    uploads `pack-check-jar`;
  - `service-release.yml` (assemble downloads it);
  - `scripts/release/service-release-assets.sh` and `rehearse-service-release.sh`: 13 files, and 6 lines in
    `SHA256SUMS`;
  - ADR-0046 (decision 4 amendment), ADR-0075 (landing note), `docs/guides/service-build.md`, a pack-author page;
  - the release PR for `service-v0.1.0-rc.2` after this merges (D4).
- **Sequencing/depends-on**: IC-06
- **Risks**:
  - `SHA256SUMS` gains a sixth line. Core's vendor ignores names it does not map (J2, evidence in research.md).
  - FR-012 is a post-merge follow-through (S13): the version PR, then the native dispatch, then the operator's tag,
    then the `-SNAPSHOT` PR.
