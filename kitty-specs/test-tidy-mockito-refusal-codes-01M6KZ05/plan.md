# Implementation Plan: Test-suite tidy: Mockito at Spring's version, refusal codes pinned

**Branch**: `test-tidy-mockito-refusal-codes` (merges to `main` by draft PR) | **Date**: 2026-11-04 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/test-tidy-mockito-refusal-codes-01M6KZ05/spec.md`

## Summary

Two independent test-hygiene changes from the 2026-11-03 assessment, tidy-first:

1. **#373 (R7), as ruled in plan:** keep Mockito and its Java agent, align the catalog version to the
   5.23.0 that Spring Boot 4.1.1 manages and `spring-boot-starter-test` requires (today the catalog's
   5.24.0 wins and two versions are in play), write the reason beside the declaration, and
   regenerate `gradle/verification-metadata.xml` from scratch so it lists no stale checksum.
2. **#369 (R3):** strengthen the 15 type-only `CharterRefusal` checks in place, each asserting
   the `RefusalCode` the production path raises, and prove a sample red by a planted wrong code.

## Technical Context

**Language/Version**: Java 25 (Temurin 25.0.5), Kotlin DSL convention plugins in `build-logic`, Gradle wrapper 9.8.1
**Primary Dependencies**: JUnit Jupiter 6, AssertJ, Spring Boot 4.1.1 test starter, Mockito (kept, 5.23.0), PIT
**Storage**: N/A (test code and build files only)
**Testing**: `./gradlew :packs:test`, `./gradlew verifyAll` (per-PR gate), strict SHA-256 dependency verification
**Target Platform**: Linux CI (GitHub Actions) and the cloud session
**Project Type**: single (multi-module Gradle build)
**Performance Goals**: none new; `:packs:test` execution count unchanged
**Constraints**: no new test methods for #369; no counting guard test (`DIRECTIVE_041`); no hand edit of the verification metadata; no suppressions
**Scale/Scope**: 15 assertion sites in 8 test classes; 1 catalog line plus comments; 1 regenerated metadata file

## Charter Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Charter rule | Status |
|---|---|
| Non-trivial work runs as a mission | PASS: this mission |
| Test first; no fake green; outcome assertions | PASS: #369 adds outcome assertions; FR-003 shows them red on a planted break |
| Tests as scaffold, not friction (`DIRECTIVE_041`) | PASS: no new tests, no guard test about tests |
| Gates before merge: `verifyAll` on Java 25 with strict verification | PASS: planned in quickstart |
| Supply-chain safety (`DIRECTIVE_051`) | PASS, see research.md R-03: the version change is a *downgrade to* the version Spring already resolves and verifies; no new artifact enters the build |
| Adversarial squad for contract, architecture or domain changes | Not triggered at plan (no contract/architecture/domain change); the brief's pre-PR squad runs at closeout |
| Boy-scout vs smallest diff | The metadata regen prunes other stale entries too, by operator ruling (decision `01M6KZNNTG47M33595KY54671S`) |

Post-design re-check: unchanged, PASS.

## Project Structure

### Documentation (this mission)

```
kitty-specs/test-tidy-mockito-refusal-codes-01M6KZ05/
├── spec.md
├── plan.md          # this file
├── research.md      # Phase 0 findings (re-verification on main 4b52bcb)
├── quickstart.md    # the verification commands
└── tasks.md         # /spec-kitty.tasks
```

No `contracts/` and no `data-model.md`: the mission changes no interface and no data
(`meta.json` records `contracts: none`).

### Source Code (repository root)

```
gradle/libs.versions.toml                                   # mockito version 5.24.0 -> 5.23.0, comment
gradle/verification-metadata.xml                            # regenerated from scratch
build-logic/src/main/kotlin/charter.hand-written.gradle.kts # the comment above the Mockito block: why it is kept
service/packs/src/test/java/ai/speckitty/charter/packs/infra/
├── api/RegistrationListsTest.java
└── storage/{DomainRepositoryService,InterruptedCommitRepair,LinkedPackCommit,PackCommitStore,
             PackWriteSafety,TacticSaveContract,WholePackStore}Test.java
```

## Complexity Tracking

No charter violation to justify.

## Implementation Concern Map

### IC-01 — Mockito kept deliberately at Spring's version, metadata regenerated

- **Purpose**: one Mockito version in every test JVM, the keep-reason recorded, no stale checksums.
- **Relevant requirements**: FR-001, FR-001a, SC-001, C-003, C-005
- **Affected surfaces**: `gradle/libs.versions.toml`, `build-logic/src/main/kotlin/charter.hand-written.gradle.kts` (comment only), `gradle/verification-metadata.xml`
- **Sequencing/depends-on**: none (tidy-first, lands first, own commit)
- **Risks**: a from-scratch regen drops an entry a task outside the regen run needs. Mitigation: regenerate over every task family (`verifyAll`, `pitest`, `:packs:wholePackRoundTrip`, `:packs:timingBudgets`, `-p build-logic check`, `help --scan`-free `dependencies` of each project), then run strict `verifyAll` and `pitest` from a cold dependency cache directory. The `<configuration>` block (trusted artifacts, keyrings, ignored keys) is kept verbatim. Draft PR #368 also regenerates nothing but touches the same `.gradle.kts`; keep our change there to the comment.

### IC-02 — Refusal codes pinned on the 15 type-only checks

- **Purpose**: each of the 15 checks fails on a wrong refusal code.
- **Relevant requirements**: FR-002, FR-003, SC-002, SC-003, C-001, C-004
- **Affected surfaces**: the 8 test classes listed above; no main code
- **Sequencing/depends-on**: after IC-01 (commit order only; no technical dependency)
- **Risks**: an expected code read from the code, not observed, could be wrong; each code is confirmed by running the test green. The symlink-dependent sites (`PackWriteSafetyTest:90`, `TacticSaveContractTest:89`, `InterruptedCommitRepairTest:339`) skip without symlink support and run under `-PrequireSymlinks=true` in CI. Shape: `.isInstanceOfSatisfying(CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.X))`, the form `ConsistentReadTest` already uses.
