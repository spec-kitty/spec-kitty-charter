---
work_package_id: WP02
title: pack-check becomes pack-doctor (rename, no behavior change)
dependencies:
- WP01
requirement_refs:
- FR-007
- C-004
- NFR-002
planning_base_branch: feat/pack-doctor-coverage
merge_target_branch: feat/pack-doctor-coverage
branch_strategy: Planning artifacts for this mission were generated on feat/pack-doctor-coverage. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-doctor-coverage unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-doctor-coverage-01M7YMQK
base_commit: c0f4e3950641bfba3391e7c7d2b64277369ba15d
created_at: '2026-11-21T16:00:10.277855+00:00'
subtasks:
- T006
- T007
- T008
- T009
- T010
phase: Phase 1 - Foundation
history:
- at: '2026-11-21T13:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/pack-doctor/
create_intent:
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/NoPackCheckSurfaceTest.java
- contracts/charter/changes/2026-11-21-pack-doctor.md
- service/pack-doctor/build.gradle.kts
- service/pack-doctor/src/main/java/ai/speckitty/charter/packdoctor/PackDoctorMain.java
- service/pack-doctor/src/main/java/ai/speckitty/charter/packdoctor/Arguments.java
- service/pack-doctor/src/main/java/ai/speckitty/charter/packdoctor/ConfigRoot.java
- service/pack-doctor/src/main/java/ai/speckitty/charter/packdoctor/package-info.java
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/ArgumentsTest.java
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/ConfigRootTest.java
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/MalformedDescriptorTest.java
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/PackDoctorMainTest.java
execution_mode: code_change
owned_files:
- service/pack-check/build.gradle.kts
- service/pack-check/licenses/**
- service/pack-check/src/main/java/ai/speckitty/charter/packcheck/PackCheckMain.java
- service/pack-check/src/main/java/ai/speckitty/charter/packcheck/Arguments.java
- service/pack-check/src/main/java/ai/speckitty/charter/packcheck/ConfigRoot.java
- service/pack-check/src/main/java/ai/speckitty/charter/packcheck/package-info.java
- service/pack-check/src/main/java/ai/speckitty/charter/packcheck/ReportWriter.java
- service/pack-check/src/test/java/ai/speckitty/charter/packcheck/ArgumentsTest.java
- service/pack-check/src/test/java/ai/speckitty/charter/packcheck/ConfigRootTest.java
- service/pack-check/src/test/java/ai/speckitty/charter/packcheck/MalformedDescriptorTest.java
- service/pack-check/src/test/java/ai/speckitty/charter/packcheck/PackCheckMainTest.java
- service/pack-check/src/test/java/ai/speckitty/charter/packcheck/jar/**
- service/pack-check/src/test/java/ai/speckitty/charter/packcheck/parity/**
- service/pack-doctor/build.gradle.kts
- service/pack-doctor/licenses/**
- service/pack-doctor/src/main/java/ai/speckitty/charter/packdoctor/PackDoctorMain.java
- service/pack-doctor/src/main/java/ai/speckitty/charter/packdoctor/Arguments.java
- service/pack-doctor/src/main/java/ai/speckitty/charter/packdoctor/ConfigRoot.java
- service/pack-doctor/src/main/java/ai/speckitty/charter/packdoctor/package-info.java
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/ArgumentsTest.java
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/ConfigRootTest.java
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/MalformedDescriptorTest.java
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/PackDoctorMainTest.java
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/NoPackCheckSurfaceTest.java
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/jar/**
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/parity/**
- settings.gradle.kts
- gradle.properties
- build-logic/**
- .github/workflows/service.yml
- .github/workflows/service-release.yml
- .github/workflows/mutation-testing.yml
- scripts/release/**
- service/charter-reports/build.gradle.kts
- service/deployable/build.gradle.kts
- service/deployable/src/test/java/ai/speckitty/charter/packcheck/**
- service/deployable/src/test/java/ai/speckitty/charter/packdoctor/**
- service/deployable/src/test/java/ai/speckitty/charter/deployable/CharterWiringTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/PackCheckReportContractTest.java
- contracts/src/test/**
- contracts/charter/changes/2026-11-20-pack-doctor-skipped-reasons.md
- contracts/charter/changes/2026-11-21-pack-doctor.md
- docs/guides/check-packs.md
- docs/guides/service-build.md
- docs/conformance/**
- docs/development/notes/pack-code-unification.md
- docs/architecture/hexagonal-service.md
- docs/architecture/diagrams/graph-walk-classes.puml
- docs/architecture/diagrams/graph-walk-classes.svg
- docs/architecture/diagrams/graph-walk-sequence.puml
- docs/architecture/diagrams/graph-walk-sequence.svg
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP02 - pack-check becomes pack-doctor (rename, no behavior change)

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`AGENTS.md`, `.kittify/charter/charter.md`, ADR-0082, the mission `spec.md` and `plan.md` (the decisions this WP
names), and `research/squad-dispositions.md`.

## Objective

Rename the standalone checker from **pack-check** to **pack-doctor** on every live surface, outright, with no alias
(operator rulings R-c, R-e, R-f; spec FR-007; plan D2, D11). The CLI gains its two commands:

- `pack-doctor validate [--config-root <dir> | --pack <dir>[:<level>]...] [--round-trip]`, which does exactly what
  pack-check did;
- `pack-doctor remediate`, which exits 2 with "not available" until WP07.

**No other behavior changes.** History keeps the old name (C-004): see `occurrence_map.yaml`, which is the
authority for what is renamed, and its exceptions.

## Branch Strategy

- Planning branch and merge target: `feat/pack-doctor-coverage` (PR to `main`). Mission topic branch:
  `kitty/mission-pack-doctor-coverage-01M7YMQK`.
- The workspace comes from `lanes.json`. Implement with
  `spec-kitty agent action implement WP02 --agent claude --mission pack-doctor-coverage-01M7YMQK`.
- Commits: no model names and no Co-Authored-By trailer (repository rule). Never push to `main`.

## Subtasks

### T006 — The structural red test (first)
- Add `NoPackCheckSurfaceTest`. Write it **before** the rename; it must be red on the current tree and green after.
- It scans the live surfaces (every tracked file outside the occurrence map's exceptions) for `pack-check`,
  `packcheck` and `PackCheck` (case-sensitive where it matters). An explicit allowlist covers:
  - `service/pack-validation/**/PackCheck.java` and its callers (a domain term);
  - `kitty-specs/**`, `kitty-ops/**`, `docs/changelog/**`, `docs/plans/engineering-notes/**`,
    `contracts/charter/CHANGELOG.md`, `.kittify/evidence/**` and `contracts/packs/conformance/classifier-cases.yaml`;
  - the ADR bodies (`docs/adr/**`), which WP08 amends.

  Use `git ls-files` through a ProcessBuilder, or walk the repo root from the test's working directory. Keep the
  allowlist in one place, with a reason per entry.
- It also asserts the CLI shape:
  - `validate` runs as before;
  - `remediate` exits 2 and says "not available";
  - a call with no command (the old flat form, for example `--config-root x`) exits 2 with usage, so the old form is
    no alias (C-002).
- Docs that WP08 edits (ADRs, the rules doc, `architectural-patterns-applied.md`) stay allowlisted here. WP08 removes
  that allowlist entry once it renames the scope rows.

### T007 — Module, package, main class, jar
- `git mv service/pack-check service/pack-doctor`. Rename the package `ai.speckitty.charter.packcheck` to
  `...packdoctor`, `PackCheckMain` to `PackDoctorMain`, and `PackCheckMainTest`/`PackCheckJarTest` to their
  pack-doctor names.
- `Arguments`: parse a leading command word (`validate` or `remediate`), then today's options for `validate`. Usage
  text names `pack-doctor`. Exit codes are unchanged (0, 1, 2).
- The jar is `pack-doctor-<version>.jar`, its licence bundle is `META-INF/pack-doctor/libraries.txt`, and the Gradle
  task is `:pack-doctor:packDoctorJar`.
- `Problems.java` and `ReportWriter.java` move with the module. WP06 owns their content afterwards, so here change
  only the package line.

### T008 — Build and wiring
- `settings.gradle.kts`, `gradle.properties` (a comment), and in `build-logic`: `charter.pack-check-jar.gradle.kts`
  → `charter.pack-doctor-jar.gradle.kts`, `PackCheckLicences` → `PackDoctorLicences`, and the
  `ModuleDependencyAllowlist(Test)` entries for `:pack-check` and its path.
- `service/charter-reports/build.gradle.kts`: the package-mapped entry `"pack-check" to listOf("...packcheck")`.
- `service/deployable`: `build.gradle.kts`, `CharterWiringTest`, the `packcheck` test package (badfixture), and
  `PackCheckReportContractTest` → `PackDoctorReportContractTest`.
- `HexagonalArchitectureTest` rule names `COMPOSED_BY_THE_PACK_CHECK_MAIN`, `THE_PACK_CHECK_MAIN_NAMES_TWO_ADAPTERS`
  and `ONION_THE_PACK_CHECK_MAIN_COMPOSES_TWO_ADAPTERS` become `..._PACK_DOCTOR_...`. The file is owned by WP07, so
  record this as a justified out-of-map edit.

### T009 — CI and release
- `.github/workflows/service.yml`: the task, the jar path, the call, which becomes
  `java -jar pack-doctor-<v>.jar validate --pack ... --round-trip`, the artifact name `pack-doctor-jar`, and the step
  names.
- The timed step: relabel it from the reused "NFR-002" #478 label to this mission's NFR-002. Add a second timed step
  placeholder for `--full`, inert until WP05 lands, guarded by an `if` on a repository variable or commented with a
  pointer to WP05. Do not add a step that runs an option that does not exist yet.
- `.github/workflows/service-release.yml` (asset names, release notes, attestation text),
  `.github/workflows/mutation-testing.yml` (the module list), and `scripts/release/service-release-assets.sh` and
  `rehearse-service-release.sh` change **together**.
- Run the rehearsal script locally if it can run offline. Otherwise say so in the review notes.

### T010 — Contract schema and live docs
- `contracts/charter/schemas/PackCheckReport.yaml` → `PackDoctorReport.yaml`, with its `_index.yaml` entry and
  `ReachableContractFilesTest`.
- `contracts/charter/changes/2026-11-21-pack-doctor.md` under `### Breaking`: the jar, the command word and the schema
  name change, and core regenerates nothing (the CLI is not part of core's seam), as ADR-0022 requires.
- The unreleased fragment `2026-11-20-pack-doctor-skipped-reasons.md` is renamed in its text and in its file name.
- Live docs in this WP's owned files: the guides, `docs/conformance/**`, the notes, `hexagonal-service.md`, and the
  graph-walk `.puml` diagrams. Regenerate the `.svg` files with the pinned PlantUML (see `check_docs.py`'s
  plantuml check), never by hand.

## Test commands

```bash
./gradlew :pack-doctor:test :pack-doctor:packDoctorJar :deployable:test --tests '*PackDoctor*' --tests '*Hexagonal*' --tests '*Wiring*'
./gradlew -p build-logic test
./gradlew :contracts:test
python3 scripts/docs/check_docs.py   # or the repo's docs check entry point; run all checks
```

Then `./gradlew verifyAll` once before handing over (Java 25).

## Definition of Done
- [ ] T006's scan was red before the rename and is green after it.
- [ ] No `pack-check` jar, task, asset, CI name or option remains on a live surface.
- [ ] `validate` behaves byte-for-byte as pack-check did on the example packs (compare the stdout JSON before and
  after).
- [ ] `remediate` exits 2 with "not available".
- [ ] The release workflow, the asset script and the rehearsal agree.

## Risks and reviewer guidance
- The rename collides with every open branch. Land early in the mission order.
- A reviewer checks the scan's allowlist: each entry has a reason, and none hides a live surface.
