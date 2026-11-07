---
title: "Test suite assessment, 2026-11-03"
description: How good the charter's test suite is, measured against the pack testing rubric, and whether to add SonarCloud, with ranked weaknesses and follow-up issues.
doc_status: draft
updated: 2026-11-05T00:00:00Z
type: Plan
kind: Planning
related:
  - adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr
  - adr/0047-generated-indexes-and-merge-queue
  - adr/0023-ci-gate-required-check
  - adr/0006-onion-vertical-slices-archunit
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for where the suite is strong, the five weaknesses ranked by risk, and the filed issues that act on them.
  - profile: governance-lead
    guidance_text: Read this for the scorecard, the SonarCloud recommendation and its cost, and the operator decisions recorded on 2026-11-03.
---

# Test suite assessment, 2026-11-03

**Status: assessment, not a decision.** It answers two operator questions: how good is the test
suite, and should the charter add SonarCloud? It changes no code. The SonarCloud decision it feeds
is recorded as [ADR-0058](/adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr/)
(Accepted, 2026-11-03). The follow-ups it recommends were filed on 2026-11-04 and are listed at the end.

**Clarified 2026-11-04 (PR #366 landing).** The measurements below are as of 2026-11-03 and are not
updated. Four readings are corrected, each marked inline:

- the eight tight wall-clock checks in W3 are deadline checks, not budgets, so R2 keeps them in
  `:packs:test` under [#320](https://github.com/spec-kitty/spec-kitty-charter/issues/320) and does
  not tag them `timing`;
- the `timingBudgets` step is report-only since
  [ADR-0057](/adr/0057-performance-budgets-never-block-a-pull-request/);
- R3 pins the 15 `CharterRefusal` codes and adds no counting guard test.
- R5 has no grandfathering (operator ruling of 2026-11-04): the existing over-limit methods are
  refactored first under [#367](https://github.com/spec-kitty/spec-kitty-charter/issues/367), and
  there is no shrink-only register.

## Short answer

The suite is **strong**, and its assertions hold up when probed. `./gradlew verifyAll` ran
**4,488 JUnit tests green**, with 4 skips that are all platform guards. Aggregated coverage is
**95.8 % of lines and 88.0 % of branches**. No test uses a mock. No test is disabled. Where PIT
mutates code, **95–96 % of the mutants that the tests reach are killed**.

The risk sits in what the suite does not measure:

- mutation testing gates nothing and never reaches `packs`, which holds 90 % of the code;
- module-local reach is lower than the aggregate coverage suggests;
- tight wall-clock deadline checks (not budgets) remain in the parallel test run, which a loaded
  runner can trip in the same way that #316's CPU-time budget (`ThreadMXBean`, not wall-clock) was
  tripped *(clarified 2026-11-04)*;
- about a third of the exception assertions check only the exception's type;
- nothing measures complexity, duplication or security patterns.

**SonarCloud: yes, but nightly and report-only**, mirroring core's `sonar.yml`. Per pull request,
complexity is gated by Checkstyle, which is already in the build. The operator ruled on this on
2026-11-03.

## How this was assessed

- **Run evidence.**
  - `./gradlew verifyAll --continue` ran on JDK 25 at `d2e1670` with open-packs at the
    `openPacksRef` pin, and passed in 462 s including `syncPacks`.
  - The test-result XML files and the aggregated JaCoCo XML
    (`service/charter-reports/build/reports/jacoco/testCodeCoverageReport/`) were read with
    throwaway scripts.
  - `./gradlew pitest` ran on the same tree in 71 s.
- **Three read-only research lenses:**
  - inventory and structure;
  - effectiveness, which sampled 15 test classes and ran a Java port of the R1–R9 static flags over
    all 2,286 test methods;
  - CI and SonarCloud fit, which included a read-only reading of core's Sonar setup.
- **The rubric is the packs' own.** The built-in `test-desiderata-and-boundaries` styleguide supplies
  Kent Beck's twelve desiderata and the boundary-by-responsibility rule, adopted from his Test
  Desiderata site and the *Define Test Boundaries* practice. The other sources are:
  - the `testing-principles` styleguide and the `testing-select-appropriate-level` tactic (the
    test pyramid);
  - `DIRECTIVE_041` (tests as scaffold, not friction);
  - the `USE_MUTATION_TESTING_TO_VALIDATE_TEST_QUALITY` directive;
  - the internal pack's `test-suite-quality-assessment` procedure and its R1–R9 flag codes;
  - the QA pack's `QA_003` (no fake green), `qa-flaky-test-quarantine` and
    `qa-coverage-gap-analysis`.

  The three web pages behind them (Test Desiderata, *Define Test Boundaries* and the test pyramid
  concept page) could not be fetched from the session, so their content was read through the pack
  artifacts that adopt them.

## Scorecard

| Lens | Score | Strongest evidence | Weakest evidence |
|---|---|---|---|
| A. Inventory and structure | **4 / 5** | Each test kind has its own tag and task. 159 contract scenarios run through Cucumber. ArchUnit guards the onion. | `profiles` has 1 test class for 10 domain classes. 27 promise-first scenarios are not wired to any runner. |
| B. Effectiveness | **4 / 5** | AssertJ only, no vacuous tests, 0 mocks. Every refusal code is asserted. Mutation test strength is 95–96 %. | Mutation coverage reach is 53–77 %. 154 of 494 exception assertions check only the type. |
| C. CI and quality gates | **4 / 5** | One required gate, `CI gate / gate`. Error Prone, NullAway, Checkstyle with 0 suppressions, `-Werror`, dependency verification, OpenAPI lint and breaking-change check. | No complexity, duplication or SAST rule. No coverage or mutation threshold. The nightly uploads only kernel's PIT report. |

### A. Inventory and structure

**What the run executed.** Counts come from the test-result XML files. Parameterized and Cucumber cases
are counted as they run, and the times are summed per suite, not wall clock.

| Module | Executions | Skipped | Time | Main / test LOC |
|---|---:|---:|---:|---|
| packs (`test`) | 2,617 | 4 | 61.0 s | 35,051 / 44,460 |
| packs (`openPacksRoundTrip`) | 43 | 0 | 36.8 s | (same module) |
| kernel | 845 | 0 | 5.2 s | 2,074 / 870 |
| deployable (220 Cucumber executions of 159 scenarios) | 635 | 0 | 64.9 s | 838 / 9,306 |
| contracts | 251 | 0 | 15.3 s | 3,272 / 2,858 |
| missions | 71 | 0 | 2.1 s | 1,615 / 778 |
| build-logic | 17 | 0 | 2.2 s | — |
| profiles | 9 | 0 | 0.8 s | 1,037 / 160 |
| **Total** | **4,488** | **4** | | |

- The 37 Python tests of the docs scripts (`scripts/docs/tests/`) run in CI at
  `docs-cleanliness.yml:61`, outside Gradle.
- The 4 skips are guards for a root user (`FileSystemPackSourcesTest`, 3) and for running on GitHub
  Actions only (`SymbolicLinksTest`, 1). On CI that guard runs, and `-PrequireSymlinks=true` makes
  the other symbolic-link tests fail instead of skip.

**Kinds of test, and how the build runs each:**

- **Unit and domain tests** run in every module's `test` task. jqwik is excluded on purpose
  (`gradle/libs.versions.toml:17`).
- **Round-trip tests** run in two places:
  - in `test`, through `PackRoundTripTest`, `DomainRoundTripTest` and others;
  - in `openPacksRoundTrip` (part of `check`), through the 16 classes tagged `open-packs`, against
    the pinned open-packs corpus.
- **The whole-pack gate** is `wholePackRoundTrip`, tagged `whole-pack`. It runs in
  `whole-pack.yml`, outside `check` ~~and outside the required gate, by design~~. *Clarified 2026-11-05:*
  the operator ruled on epic #186 that it is required; `CI gate / gate` now calls `whole-pack.yml`
  for its `wholepack` group, still outside `check`. Its expected-red lists
  ratchet in both directions:
  - `WholePackExpectation.java:291-381` fails on an unlisted red;
  - it fails `UNEXPECTEDLY_GREEN` on a listed entry that passes;
  - it fails `STALE_BASELINE` on stale entries.

  `expected-red.yaml` holds only 3 `empty_kinds` (skill #192; guard and lifecycle #156).
- **Contract tests (BDD).** `CharterContractSuite` runs the 25 Gherkin files copied from
  `contracts/charter/scenarios/`: `@IncludeTags("contract")`, `@ExcludeTags("WIP")`. That is 186
  scenarios, of which 159 run and 27 journey scenarios are `@WIP` (a different set from the 27
  promise-first scenarios below). An `@After` hook in
  `CharterSteps.java:76-91` checks every HTTP exchange against the OpenAPI schema.
- **ArchUnit.** `HexagonalArchitectureTest` has 45 tests. `ReservedRefusalCodesTest`,
  `GeneratedConstraintTypesTest` and `NoBuildTimeProfilesTest` are in `deployable`, and
  `NetworkCodeBoundaryTest` is in `packs`.
- **Conformance and parity tests** include `ContractConformanceTest`,
  `DivergenceRegisterTest`, `ActivationConfigConformanceTest` and the parity tests (`WireTokenParityTest`,
  `RelationSetParityTest` and others).
- **Timing.** `timingBudgets` runs only `@Tag("timing")` (report-only since 2026-11-04, ADR-0057). That is `PackFileSetBudgetTest`, run
  serially in its own CI step since #316 was fixed by #321.

**Shape against the pyramid.** The base is wide: the domain modules plus `packs.domain` and
`packs.application`. The middle is thick: `packs.infra.storage` has 84 test classes against real
temporary file systems, which is the right layer for a file-system adapter. The top is narrow and
deliberate: 159 contract scenarios and 12 `@SpringBootTest` classes. This is a pyramid, not an
ice-cream cone.

**Thin areas.** Five main classes are named by no test anywhere, so their behavior is tested only
indirectly, if at all: `kernel` `validation/Constrained` and `validation/Rationale`, `profiles`
`SelfReviewStep` and `ModeDefault`, and `packs` `infra/fetch/CappedBodySubscriber`. More classes
are named by no test in their own module and are covered from another module or through HTTP: 12
in `kernel`, 19 helpers under `packs` `infra/storage`, 11 under `infra/api`, and 6 under
`contracts` `closure`.

### B. Effectiveness

**Coverage** comes from the aggregated JaCoCo report over the service modules (`contracts` is not
in it), which credits each line to any module's tests:

| Module | Line | Branch | Weakest package |
|---|---:|---:|---|
| packs | 95.6 % | 88.2 % | `infra.fetch`: 77.2 % lines, 79.5 % branches |
| kernel | 95.4 % | **74.8 %** | `validation`: 82.8 % lines, **58.3 % branches** |
| missions | 98.2 % | 88.1 % | — |
| profiles | 99.1 % | 83.3 % | — |
| deployable | 95.9 % | 96.9 % | — |
| **Total** | **95.8 %** | **88.0 %** | |

**Mutation testing.** These are fresh results from `./gradlew pitest`, which mutates each module
with that module's own tests only:

| Module | Mutants | Killed | Survived | No coverage | Score | Test strength | Own-test line coverage |
|---|---:|---:|---:|---:|---:|---:|---:|
| kernel | 196 | 136 | 6 | 54 | 69 % | 96 % | 73 % |
| missions | 141 | 108 | 5 | 28 | 77 % | 96 % | 92 % |
| profiles | 66 | 35 | 2 | 29 | **53 %** | 95 % | **75 %** |

Score is killed ÷ all mutants. Test strength is killed ÷ the mutants the module's tests reach. No
coverage counts the mutants that no test of the module executes.

The two tables together are the main finding. Where a module's own tests reach code, they pin it:
very few mutants survive. But each module's tests reach less than the aggregate suggests.
`profiles` reports 99.1 % line coverage, yet its own tests cover 75 % of its lines and never touch 29
of its 66 mutants. Tests in other modules execute that code without asserting on it. As the
mutation directive puts it, coverage "prove[s] a test executed a piece of code", not that a test
would notice if the code were wrong.

**Assertion quality.** These figures come from the R1–R9 flags of the internal pack, ported to
Java:

- AssertJ is the only assertion style: 4,872 `assertThat(` and 494 `assertThatThrownBy(`, and no
  `assertEquals` or `assertTrue`.
- No test lacks an assertion. The six candidates the flag raised all assert through AssertJ chains
  or MockMvc `andExpect`.
- **Mocks: zero.** No `mock(`, `@Mock` or `verify(` appears anywhere. Tests stub infrastructure at
  real seams with lambdas and real temporary file systems, which is the
  boundary-by-responsibility rule in practice. Mockito is still declared and attached as a Java agent
  to every test JVM (`charter.hand-written.gradle.kts:105-127`), although nothing uses it.
- **Exception assertions that check only the type: 154 of 494 (31 %).** This was measured on whole
  `assertThatThrownBy` statements that carry no message, code, cause or identity check. Of these, 111
  are in `packs`, 23 in `missions`, 7 in `profiles` and 13 elsewhere. **15 assert a `CharterRefusal`
  without its code.** Compare a strong and a weak check:

  ```java
  // ConsistentReadTest.java:68 (packs) — pins the refusal and its code
  assertThatThrownBy(() -> reading(D1, D2).read(STALE, loading(() -> "value")))
          .isInstanceOfSatisfying(
                  CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT))
          .hasMessage(STALE);

  // PackWriteSafetyTest.java:71 (packs) — any refusal, any code, passes
  assertThatThrownBy(() -> failing.write(PACK, TacticBinding.INSTANCE, tactic("alpha-tactic", "Never written")))
          .isInstanceOf(CharterRefusal.class);
  ```

  The weak test still earns its keep, because it goes on to assert that the target bytes and the
  directory listing are unchanged. It does not notice a wrong refusal code.

**Refusal and fail-closed paths are well covered.** Each of the 24 codes in `RefusalCode` is
referenced by at least two test files and one `.feature` file. The contract scenarios assert 87
error statuses and check `"code"` 23 times. `ReservedRefusalCodesTest` proves that no main class
raises a reserved code. Refusal tests are paired with positive controls, for example
`PackWriteSafetyTest.java:90-97` and `TacticSaveContractTest.java:95-102`.

**Hygiene.** There is no `@Disabled`, no `flaky` or `quarantine` tag, and no masked defect.
Assumptions appear only as platform guards. The verdicts from the sample, following the procedure:

- **KEEP:** 15 classes, among them `UrnTest`, `LifecycleTest`, `ConsistentReadTest`,
  `HexagonalArchitectureTest`, `ReservedRefusalCodesTest` and `CharterSteps`.
- **FIX:** 4, each by adding the missing message or code assertion: `MissionValuesTest`,
  `AgentProfileEntityTest:101-109`, `TacticSaveContractTest:78-89` and `ReleaseGateTest:45`.
- **RETIRE:** none.

### C. CI and quality gates

`ci.yml` is the orchestrator. Its terminal `gate` job, `CI gate / gate`, is the one required check.
It fails closed, it honors `pr:pause-ci`, and per
[ADR-0047](/adr/0047-generated-indexes-and-merge-queue/) it alone runs on `merge_group`.
`service.yml` runs `verifyAll`, then `:packs:timingBudgets`, and uploads the aggregated JaCoCo
report as the `coverage` artifact. A `verify` run on `main` takes about 5 minutes.

*Clarified 2026-11-04:* since 2026-11-04 ([ADR-0057](/adr/0057-performance-budgets-never-block-a-pull-request/))
the `timingBudgets` step is report-only and runs each budget in its own JVM. *Clarified 2026-11-07:* the budgets moved
to the nightly `timing-nightly.yml` workflow; `service.yml` now proceeds from `verifyAll` to its artifact uploads.

| Gate family | Present today | Blocking |
| --- | --- | --- |
| Compiler | `-Xlint:all -Werror`, Error Prone 2.50.0 on main and test | yes |
| Nullness | NullAway at ERROR, `OnlyNullMarked`, JSpecify mode, main code only; 20 of 20 main packages `@NullMarked` | yes |
| Style | Checkstyle 14.3.0, `maxWarnings = 0`, 0 suppressions; Spotless | yes |
| Architecture | ArchUnit onion rules ([ADR-0006](/adr/0006-onion-vertical-slices-archunit/)) | yes |
| Contract | vacuum lint with planted fixtures, `oasdiff` breaking-change check | yes |
| Supply chain | `gradle/verification-metadata.xml` (SHA-256), SHA-pinned actions | yes |
| Performance budgets | `:packs:timingBudgets` | no (report-only, ADR-0057) |
| Coverage | JaCoCo per module and aggregated, uploaded; **no threshold** | no |
| Mutation | PIT nightly on kernel, missions, profiles; **no threshold**; uploads kernel's report only | no |
| Complexity, length, duplication | **none**: Checkstyle has no such module; no PMD or CPD | — |
| SAST, security hotspots | **none**: no SpotBugs, CodeQL or Sonar | — |

## Top 5 weaknesses, ranked by risk

The recommendations below refer to these as W1 to W5.

1. **Mutation testing gates nothing and does not reach where the logic is.** `packs` holds 10,433 of
   the 11,621 measured lines and 73 % of the test methods (59 % of executions), and PIT never mutates it. On the modules PIT does
   reach, no floor stops a falling score. A hollow assertion in the pack store or the read path would
   go unnoticed. [#239](https://github.com/spec-kitty/spec-kitty-charter/issues/239) already designs
   the fix.
2. **Module-local reach is lower than the aggregate coverage says.** Three areas stand out:
   - `kernel.validation` has 58.3 % branch coverage. It is the fail-fast invariant code every module
     leans on, and `Constrained` and `Rationale` have no test of their own.
   - `profiles` has a mutation score of 53 %, with 29 of 66 mutants never touched.
   - `packs.infra.fetch` has 77.2 % line coverage. It is the outbound HTTPS guide fetcher, a
     fail-closed network boundary, and `CappedBodySubscriber` has no test of its own.
3. **Tight wall-clock deadline checks (not budgets) remain in the parallel `test` task.** A loaded
   runner can trip them in the same way that #316's CPU-time budget (`ThreadMXBean`, not a
   wall-clock one) was tripped, which dropped unrelated pull requests from the merge queue. The
   eight checks, in five files, are:
   - `HttpsGuideFetcherTest.java:540`: less than 2 s;
   - `HttpsGuideFetcherLoopbackTest.java:93`: less than 5 s;
   - `ParsedYamlTest.java:130,146,153`: `assertTimeoutPreemptively`, 2 s;
   - `EmbeddedSchemasTest.java:80`: 5 s;
   - `InterruptedCommitRepairTest.java:166,200`: 10 s.

   `PackFileSetExactnessTest` also keeps 60 s `@Timeout` hang caps, which are generous enough to
   leave alone.

   They have not flaked yet, but they share #316's failure mode under load.

   *Clarified 2026-11-04:* [ADR-0057](/adr/0057-performance-budgets-never-block-a-pull-request/)
   rules that a time or CPU-time budget is tagged `timing`, stays out of `verifyAll` and runs
   report-only in CI. These eight are deadline checks, not budgets: several are correctness and
   security guards, among them the alias-bomb refusal in `ParsedYamlTest` and the fetcher's
   `TIMEOUT`. Tagging them `timing` would make those guards report-only. The
   [service build guide](/guides/service-build/) says a deadline check needs no tag, and
   [#320](https://github.com/spec-kitty/spec-kitty-charter/issues/320) tracks the tight ones.
4. **About a third of the exception assertions check only the type.** That is 154 of 494, of which
   15 are `CharterRefusal`. A change that raises the wrong refusal code, or fails for the wrong reason,
   still passes them. The contract scenarios catch some of these at the HTTP layer, but not the
   storage-layer refusals.
5. **Nothing measures complexity, duplication or security patterns.** The build's static gates are
   strict on correctness and nullness but blind to size. The `testing-principles` styleguide says
   such metrics "belong to static analysis", not to tests. `packs` main code has grown to 35 k
   source lines (10,433 executable lines) with no complexity signal.

Other findings, lower risk:

- **The promise-first scenarios are not wired.** The 18 scenarios in
  `src/test/resources/features/` and the 9 in `tests/scenarios/charter/` predate the Cucumber
  harness and are bound to no runner. Their README still reads "Layer B — the harness (not yet
  wired)". They can drift from the service unseen.
- **Mockito is unused**, yet it is a test dependency and a Java agent on every test JVM.
- **The nightly PIT run keeps only kernel's report** (`mutation-nightly.yml:50`), so the reports of
  missions and profiles are lost.
- **The internal pack's static scanner reads Python only.** `test-quality-scan.py` cannot run on
  this Java suite, so this assessment used a throwaway Java port.

## Recommendations

| # | Recommendation | Size | Addresses |
|---|---|:-:|---|
| R1 | Adopt [#239](https://github.com/spec-kitty/spec-kitty-charter/issues/239) as designed. Add a per-module floor of the current score minus 3, rounded down to a multiple of 5: kernel 65, missions 70, profiles 50, from today's run. Extend PIT to `packs.domain` and `packs.application`. Upload every module's report with a job summary. | M | W1 |
| R2 | *(Clarified 2026-11-04.)* Under [#320](https://github.com/spec-kitty/spec-kitty-charter/issues/320), keep the eight deadline checks in `:packs:test`. Widen each deadline into a hang cap far above the work's own time, or assert the work instead of the time. Do not tag them `timing`. | S | W3 |
| R3 | Pin the code on the 15 `CharterRefusal` checks that test only the type. Leave the other 139 type-only `assertThatThrownBy` to review and to the test-cleanup FIX verdicts, with no counting guard test, which would be a test about tests (`DIRECTIVE_041`). *(Clarified 2026-11-04.)* | S | W4 |
| R4 | Close the module-local gaps that PIT's no-coverage list shows: `kernel.validation` (`Constrained`, `Rationale`), `profiles` (`SelfReviewStep`, `ModeDefault`) and `packs.infra.fetch` (`CappedBodySubscriber`). Prove each new test with a planted break. | M | W1, W2 |
| R5 | Add Checkstyle complexity modules to the per-PR gate: `CyclomaticComplexity`, `NPathComplexity`, `MethodLength`, `NestedIfDepth`. Set the limits from a measurement run, and keep the current 0-suppression register or a shrink-only one. No new tool is needed. *(Clarified 2026-11-04: the operator ruled no grandfathering; see ADR-0058. The limits are as ruled, #367 refactors the existing over-limit methods first, and there is no shrink-only register.)* | M | W5 |
| R6 | Add a nightly, report-only SonarCloud scan with new-code coverage, as set out below and in ADR-0058. | M | W5, coverage |
| R7 | Remove the unused Mockito dependency and Java agent, or record why they are kept. | S | hygiene |
| R8 | Run the QA pack's `qa-coverage-gap-analysis` over the 27 promise-first scenarios. Map each one to a contract scenario that covers it, move it under the Cucumber runner, or retire it with the covering scenario named. | M | traceability |
| R9 | Give the internal pack's `test-quality-scan` a Java front end, as an open-packs change. The `test-suite-quality-assessment` procedure could then run per domain on this repository with ledgers. | L | repeatability |

## SonarCloud recommendation

**Yes: nightly and report-only.** The operator ruled on 2026-11-03.

**Why yes.**

- SonarCloud covers the one gate family the build lacks: complexity, duplication and security
  hotspots.
- It reads the JaCoCo XML the build already produces, so coverage needs no new Gradle work.
- It shows new-code coverage over time, which the operator chose in place of a coverage threshold.
- It matches core, so both repositories report to the same SonarCloud organization in the same way.
- The repository is public, so the SonarCloud project and GitHub-hosted minutes are free.

**Why not per pull request.**

- Java analysis needs compiled classes (`sonar.java.binaries`).
- Core deliberately never compiles a pull request's build next to `SONAR_TOKEN`. Core's
  `sonar-pr` job can analyze Python without compiling; a Java job cannot.
- A safe per-PR shape would need a new compiled-classes artifact and a `workflow_run` consumer. That
  is more surface for a signal that Checkstyle (R5) can give in the gate itself, blocking and with no
  new tool.

**Proposed shape** (ADR-0058 has the full record):

- **A new `sonar.yml` workflow.**
  - It runs nightly and on manual dispatch, on `main` only.
  - It runs `verifyAll` on trusted code, then the SHA-pinned generic `SonarSource/sonarqube-scan-action`.
    That is the action core uses, so no Gradle plugin is added.
  - It sets `sonar.java.binaries` to the modules' `build/classes/java/main` and
    `sonar.coverage.jacoco.xmlReportPaths` to the aggregated report.
  - It excludes the generated `charter-api`.
- **Reported, never required.**
  - Every step is `continue-on-error`.
  - The run skips green, with a notice, when `SONAR_TOKEN` is absent.
  - The quality-gate result goes to the job summary.
  - It never becomes part of `CI gate / gate`, and it never runs on `merge_group`.
- **New-code coverage is read from SonarCloud's new-code period** on `main`, not per pull request.

**Cost.**

- One SonarCloud project and one `SONAR_TOKEN` secret, both created by the operator.
- About 8 minutes of runner time a night: 5 for `verifyAll` and roughly 3 for the scan.
- No pull-request minutes and no change to the required check.
- Triage time while the default quality profile is tuned against rules that overlap Error Prone and
  Checkstyle. A review after four weeks of signal decides whether a per-PR shape is worth its cost.

## Operator decisions (2026-11-03)

| Question | Ruling |
|---|---|
| SonarCloud adoption and shape | **Nightly SonarCloud, report-only; per-PR complexity through Checkstyle** (R5, R6). Not implemented in this change: recorded as [ADR-0058](/adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr/) (Accepted, 2026-11-03); the implementation is a follow-up mission. |
| Mutation testing | **Adopt #239 as designed** (R1). |
| Coverage gate | **No JaCoCo threshold. New-code coverage is reported by SonarCloud** (R6). |

The operator creates the SonarCloud project and the `SONAR_TOKEN` secret. The workflow does not
wait for them: it skips green, with a notice, until the token exists. So the CI work for R5 and R6
proceeds as its own mission.

## Follow-up issues (filed 2026-11-04)

| Recommendation | Issue | Size | Notes |
| --- | --- | :-: | --- |
| R1 | [#239](https://github.com/spec-kitty/spec-kitty-charter/issues/239) (comment) | M | Today's baseline and the report-upload gap |
| R2 | [#320](https://github.com/spec-kitty/spec-kitty-charter/issues/320) (comment) | S | The eight call sites: widen each deadline into a hang cap, or assert the work; do not tag them `timing` |
| R3 | [#369](https://github.com/spec-kitty/spec-kitty-charter/issues/369) | S | Lists the 15 `CharterRefusal` sites; no counting guard test (`DIRECTIVE_041`) |
| R4 | [#370](https://github.com/spec-kitty/spec-kitty-charter/issues/370) | M | PIT no-coverage counts fall; each new test is proved by a planted break |
| R5 | [#371](https://github.com/spec-kitty/spec-kitty-charter/issues/371) | M | Limits as ruled; [#367](https://github.com/spec-kitty/spec-kitty-charter/issues/367) first; no suppressions and no shrink-only register |
| R6 | [#372](https://github.com/spec-kitty/spec-kitty-charter/issues/372) | M | Needs `SONAR_TOKEN` and the SonarCloud project from the operator |
| R7 | [#373](https://github.com/spec-kitty/spec-kitty-charter/issues/373) | S | |
| R8 | [#374](https://github.com/spec-kitty/spec-kitty-charter/issues/374) | M | `qa-coverage-gap-analysis`; map, wire or retire each one |
| R9 | [open-packs#115](https://github.com/spec-kitty/spec-kitty-open-packs/issues/115) | L | An open-packs change; this repository consumes it |

## Reproduce

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew syncPacks && ./gradlew verifyAll --continue
# Coverage: service/charter-reports/build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml
# Test results: service/*/build/test-results/*/*.xml, build/contracts/test-results/, build-logic/build/test-results/
./gradlew pitest   # reports in service/{kernel,missions,profiles}/build/reports/pitest/
```
