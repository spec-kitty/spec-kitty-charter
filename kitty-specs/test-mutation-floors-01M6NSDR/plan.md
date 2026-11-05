# Implementation Plan: Mutation testing floors, and PIT on packs

**Branch**: `test-mutation-floors` (merges to `main` by draft PR) | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/test-mutation-floors-01M6NSDR/spec.md`

## Summary

Implement #239 as designed, with the operator's rulings. Concretely:

- a `charter.mutation-conventions` convention plugin;
- a floor-check task that reads PIT's `mutations.xml` leniently and fails per module or per package group;
- PIT on `packs` (domain, application, infra.storage), with the floors measured and ruled;
- a nightly that reports every module, uploads every report, and opens one issue on red;
- an ADR, the doc updates, and triage of the surviving kernel mutants.

**Amendment (2026-11-05)**: the pre-PR squad ruling added `packs.infra.fetch` (without `infra.fetch.config`) as a third
packs group with floor 70, shared the packs `test` and `pitest` configuration, and ran the summary tests on pull
requests (see the spec's amendment).

## Technical Context

**Language/Version**: Java 25 (Temurin 25.0.5); Kotlin DSL in `build-logic`; Gradle 9.8.1; GitHub Actions YAML; a small Python 3 summary script, if the workflow needs one
**Primary Dependencies**: `info.solidsoft.pitest` Gradle plugin and pitest-junit5 (versions already in the catalog); no new library
**Storage**: N/A
**Testing**: `./gradlew pitest --continue`; `./gradlew -p build-logic check` (Kotlin tests for the floor check); `verifyAll --dry-run`; a `workflow_dispatch` dry run after merge (operator)
**Target Platform**: GitHub Actions `ubuntu-24.04` nightly
**Project Type**: single (multi-module Gradle build)
**Performance Goals**: the nightly finishes within `timeout-minutes` with ≥ 25 % headroom (NFR-002)
**Constraints**: never on a PR (NFR-001); floors as ruled (C-001); no test only for a mutant (C-002); additive verification metadata (C-003); minimal hunks near #368 and #377 (C-004)
**Scale/Scope**: about 4 build-logic files, 1 packs build file, 1 workflow, 1 ADR, the guide, the verification metadata

## Charter Check

| Charter rule | Status |
|---|---|
| Gates before merge unchanged; mutation testing stays out of `verifyAll` | PASS: NFR-001 |
| Tests as scaffold (`DIRECTIVE_041`) | PASS: C-002; FR-006 triage order from #239 item 4 |
| Supply chain (`DIRECTIVE_051`) | PASS: the PIT plugin is already in the build for kernel; applying it to packs adds its transitive artifacts' checksums through `--write-verification-metadata`, reviewed in the diff |
| Decisions documented | PASS: FR-005 ADR |
| Adversarial squad for architecture or CI-gate changes | Planned pre-PR |

## Design

- **`charter.mutation-conventions`**:
  - applies `info.solidsoft.pitest` and sets `pitestVersion`, `junit5PluginVersion`, `outputFormats = HTML, XML`,
    `timestampedReports = false`, `threads`, and mutators (the defaults, unless measured otherwise);
  - declares a `mutationFloors` extension: a map of group name to (package prefixes, floor);
  - registers `checkMutationFloors`, which `pitest` is finalized by.
  - `charter.domain-conventions` applies it; packs applies it alongside its Spring-aware conventions. Packs cannot
    use `charter.domain-conventions`, which refuses external dependencies.
- **Floor check**:
  - reads `build/reports/pitest/mutations.xml` with a lenient line scan (status, mutated class), never a strict XML
    parser (#239: raw `<` in descriptions);
  - computes score = (killed + timed-out + memory-error) ÷ all per group, and test strength;
  - fails naming module, group, score and floor; writes a small machine-readable summary
    (`build/reports/pitest/floors.json`) for the workflow;
  - is unit-tested in `build-logic`: under, at and over the floor, plus a malformed `<`.
- **Module floors**: kernel 65, missions 70, profiles 50, each in the module's build file (#239 design item 1).
  Packs: two groups. `domain+application` covers `packs.domain.*` and `packs.application.*` (excluding
  `application.config.*`). `infra.storage` covers `packs.infra.storage.*`. Floors are `floor5(score − 3)` from the
  first measured run, reported to the operator before they are written (C-001).
- **Packs targets**: the target tests are the unit tests of those packages, excluding Spring-wired tests
  (`*WiredTest`, `@SpringBootTest`) and `open-packs`/`whole-pack`/`timing`-tagged tests. Note:
  `PackFileSetExactnessTest` and other storage tests may need `syncPacks`, so the nightly checks out open-packs at
  the pin. `pit-targets.init.gradle` keeps working: it narrows `targetClasses`/`targetTests` when its system
  properties are set.
- **Nightly**:
  - runs `./gradlew pitest --continue` and syncs packs first;
  - an `if: always()` summary step reads each `floors.json` and appends a Markdown table (module/group, mutants,
    score, strength, no coverage, floor, verdict, top surviving classes) to `$GITHUB_STEP_SUMMARY`;
  - uploads `service/*/build/reports/pitest`;
  - on `failure() && github.event_name == 'schedule'`, or on `workflow_dispatch` with an input that forces it, a job
    with `issues: write` opens one `mutation-nightly` issue or comments on the open one;
  - keeps `timeout-minutes` with ≥ 25 % headroom over the measured run.
- **ADR**: number chosen at writing time, against `main` and the open PRs (#376 holds 0059). "Nightly mutation
  floor" ends the experiment status (DM-01M57RMMBAS7275PXGNSYJZBQY) and records the floors, the raise rule, the
  triage order, the packs groups and the nightly behaviour.

## Implementation Concern Map

### IC-01 — Convention plugin and floor check
- **Purpose**: one PIT configuration and a lenient, tested floor check.
- **Relevant requirements**: FR-001, FR-003 (kernel, missions, profiles), NFR-001
- **Affected surfaces**: `build-logic/src/main/kotlin/charter.mutation-conventions.gradle.kts` (new), the floor-check code in `build-logic/src/main/kotlin/` (new), its tests in `build-logic/src/test/kotlin/`, `charter.domain-conventions.gradle.kts`, `service/{kernel,missions,profiles}/build.gradle.kts`
- **Sequencing/depends-on**: none
- **Risks**: #368 edits `charter.hand-written.gradle.kts`, which is not touched here; the Mockito agent's PIT `jvmArgs` hook there keeps working through `withPlugin`.

### IC-02 — PIT on packs
- **Purpose**: mutate the three packs groups, measure them, have the operator rule the floors, set them.
- **Relevant requirements**: FR-002, FR-003 (packs), C-001, C-003
- **Affected surfaces**: `service/packs/build.gradle.kts`, `service/packs/pit-targets.init.gradle`, `gradle/verification-metadata.xml`
- **Sequencing/depends-on**: IC-01
- **Risks**: run time (infra.storage has 84 test classes on real file systems); the verification metadata conflicts with #377's from-scratch regen, so whichever lands second regenerates additively.

### IC-03 — Nightly workflow
- **Purpose**: `--continue`, open-packs checkout, job summary, all reports, one issue on red.
- **Relevant requirements**: FR-004, NFR-001, NFR-002
- **Affected surfaces**: `.github/workflows/mutation-nightly.yml`, a summary script if needed (`scripts/ci/`)
- **Sequencing/depends-on**: IC-01 (the `floors.json` shape)

### IC-04 — ADR and docs
- **Purpose**: record the decision and make the docs match what runs.
- **Relevant requirements**: FR-005
- **Affected surfaces**: `docs/adr/00NN-nightly-mutation-floor.md`, `docs/guides/service-build.md` (the "Not in verifyAll: mutation testing" paragraph), the FR-009 trace in `kitty-specs/charter-contract-and-build-retro-01M57QHJ/` (a dated note only), `docs/changelog/`
- **Sequencing/depends-on**: IC-02 and IC-03 (final numbers and behaviour)

### IC-05 — Kernel survivor triage
- **Purpose**: triage the surviving kernel mutants per #239 item 4.
- **Relevant requirements**: FR-006, C-002
- **Affected surfaces**: `service/kernel/src/test/java/**` (strengthen existing tests only), a triage table in the WP note and PR
- **Sequencing/depends-on**: none. #384 already kills kernel.validation's survivors; this covers kernel.domain (`Pep440Range` and others). #368 edits `Pep440Range.candidates` in main code; only tests change here.
