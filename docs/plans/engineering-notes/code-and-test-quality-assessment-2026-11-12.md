---
title: "Code and test suite quality assessment, 2026-11-12"
description: How good the charter's tests and production code are, nine days after the 2026-11-03 assessment, with six test gaps proved by planted breaks and the weaknesses ranked by risk.
doc_status: draft
updated: 2026-11-12T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/engineering-notes/test-suite-assessment-2026-11-03
  - adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr
  - adr/0062-nightly-mutation-floor
  - adr/0066-a-step-enhancement-may-contribute-prompt-fragments
  - adr/0072-sonar-s1845-and-s1314-rulings
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the test gaps proved by planted breaks, the slow and timing-sensitive tests, and the code hotspots, each with its file and line.
  - profile: governance-lead
    guidance_text: Read this for the scorecard against 2026-11-03 and the four questions that need an operator ruling.
---

# Code and test suite quality assessment, 2026-11-12

**Status: assessment, not a decision.** It answers the operator's request for a code and test
suite quality run, judged against the charter's own guidance. It changes no code. The rulings it
asks for are listed under [Questions for the operator](#questions-for-the-operator). It follows the
[test suite assessment of 2026-11-03](/plans/engineering-notes/test-suite-assessment-2026-11-03/),
so the two can be compared. It adds a fourth lens on production code.

## Short answer

**The suite is still strong, and it has more than doubled.** `./gradlew verifyAll` ran **10,623
JUnit tests green**, against 4,488 on 2026-11-03. It had the same 4 skips, all of them platform guards.
Aggregated coverage is **96.6 % of lines and 89.9 % of branches**, against 95.8 % and 88.0 %. A static
triage of 4,652 test methods flags only 3.1 %, and most of those flags do not hold up when read.

The weaknesses have moved from what is missing to what is quietly not checked:

- **Six rules are proved unguarded.** Each was broken on purpose in production code, and the tests
  that should guard it stayed green. The most serious is the rule that keeps an expected artifact's
  template path inside `templates/`.
- **The mutation nightly has had no `:packs` report for three nights** (#461). The run now times
  out, and one test that burns a 30-second production timeout on every run is a large part of the cost.
- **Four places keep an older or retired shape or name beside the canonical one.** The charter's
  2026-11-10 ruling rules this out. Two are legacy readers: the activation store's version 1 reader
  and the unversioned pack-descriptor branch. Each violates the ruling until the operator rules.
- **Complexity has grown in `packs` while its gate waits.** The Checkstyle limits of ADR-0058 are
  still off (#367, #371).

## How this was assessed

- **Run evidence.**
  - `./gradlew verifyAll :charter-reports:testCodeCoverageReport --continue -PrequireSymlinks=true`
    ran on JDK 25 at `ac6af296` (main after #506), with the packs synced at the `openPacksRef` pin. It
    passed in 768 s. The test-result XML and the aggregated JaCoCo XML were read with throwaway scripts.
  - The 2026-11-12 mutation nightly on `main` (run 43143758531) and SonarCloud's analysis of
    `9cd0ad85` (2026-11-12 04:34 UTC) give the mutation and static-analysis figures.
- **The internal pack's `test-suite-quality-assessment` procedure, step by step.**
  - Its static triage asset (`packs/internal/assets/test-quality-scan.py`) ranked every Java test
    file without running a test.
  - Four read-only review squads then read the top of each domain's ledger against the rubric:
    packs domain and application; packs infrastructure; the HTTP and edge slices; and production
    code.
  - Each candidate FIX was then proved with a planted break that ran only the named tests.
- **The rubric** is the charter's and the packs':
  - `DIRECTIVE_041` (tests as scaffold, not friction) and `DIRECTIVE_034` (test first);
  - `QA_003` (no fake green) and `DIRECTIVE_030` (quality gates are never skipped silently);
  - `DIRECTIVE_001` (architectural integrity), `DIRECTIVE_032` (conceptual alignment) and
    `DIRECTIVE_025` (boy scout: debt is recorded, not absorbed);
  - the built-in `test-desiderata-and-boundaries` and `testing-principles` styleguides, and the
    `development-assist-test-cleanup` procedure;
  - the charter's rules that the model is canonical and that tests carry only the canonical shape.

## Scorecard

| Lens | 2026-11-03 | 2026-11-12 | Strongest evidence | Weakest evidence |
|---|---|---|---|---|
| A. Inventory and structure | 4 / 5 | **4 / 5** | 10,623 tests. Every contract operation has a success scenario (`OperationCoverageTest`). Promise-first scenarios not wired to a runner: 27 → 0. | missions-edge is the thinnest module. Two wiring tests assert only that their own configuration exists. |
| B. Effectiveness | 4 / 5 | **4 / 5** | Of 112 ledger files read, 81 are KEEP, and most type-only exception flags are real single-throw-site contracts. | Six rules proved unguarded by planted breaks. Four tests rely on a sleep where they should wait on a condition. |
| C. CI and quality gates | 4 / 5 | **3 / 5** | The PIT scope guard, the module list from the build and the margins (#506). Security rating A. | No `:packs` mutation report for three nights. Reliability E stays until 5 false positives are marked (#463). The complexity gate is still off. |
| D. Production code (new) | — | **3 / 5** | Duplication 0.7 %, 80 min of Sonar debt, 0 vulnerabilities, 0 TODOs, and ArchUnit covers every module. | Four old shapes or retired names are kept beside the canonical ones. The top 9 complexity hotspots are all in `packs`. |

### A. Inventory and structure

**What the run executed.** Counts are executions from the test-result XML; times are summed per suite.

| Module | Executions | Skipped | Time | 2026-11-03 |
|---|---:|---:|---:|---:|
| packs | 4,682 | 4 | 166.6 s | 2,617 + 43 open-packs |
| kernel | 2,888 | 0 | 2.7 s | 845 |
| deployable (contract scenarios included) | 1,453 | 0 | 113.5 s | 635 |
| activations | 876 | 0 | 15.0 s | (new) |
| contracts | 258 | 0 | 6.8 s | 251 |
| web-transport | 127 | 0 | 1.8 s | (new) |
| profiles-edge | 111 | 0 | 3.9 s | (new) |
| build-logic | 82 | 0 | 3.5 s | 17 |
| missions | 67 | 0 | 0.4 s | 71 |
| missions-edge | 63 | 0 | 3.9 s | (new) |
| profiles | 16 | 0 | 0.2 s | 9 |
| **Total** | **10,623** | **4** | | **4,488** |

- The Python tests of `scripts/ci` (24) and of `scripts/docs/tests` run in CI outside Gradle.
- **The 4 skips are platform guards.** Three are root-user guards in `FileSystemPackSourcesTest`
  (`:149`, `:163`, `:236`), skipped here because this sandbox runs as root; GitHub's runner is not
  root, so they run in CI. One is `SymbolicLinksTest`'s GitHub-Actions-only check. The symbolic-link
  skips have a switch that turns them into failures on CI (`-PrequireSymlinks=true`, in every workflow
  that runs tests). The root-user skips have none (weakness 4).
- **Contract scenarios.** There are 653 scenario blocks, of which 611 run in `CharterContractSuite`.
  42 are `@WIP`: 27 journeys (unchanged), 6 distribution, 5 trust, 3 integrity and 1 profiles. The
  27 promise-first scenarios that no runner reached on 2026-11-03 are gone (#374); each promise has a verdict in
  `tests/scenarios/charter/PROMISES.md`. All 46 operations have a success scenario, enforced by
  `OperationCoverageTest`. At least 57 of the 61 refusal codes appear in a scenario that runs. The
  other 4 are reserved and pinned by `ReservedRefusalCodesTest`.
- **The edge modules.** missions-edge has 2,423 main lines (33 classes) against 1,745 test lines (10
  classes). `MissionBodies` (281 lines) and `MissionJson` have no direct test. profiles-edge (1,594
  against 2,978) and web-transport (1,394 against 1,192) are in proportion.
- **Shape.** Still a pyramid. The new edge modules follow it: unit tests first, then two MockMvc
  classes and one context test each.
- **Mockito has spread** from 0 classes on 2026-11-03 to 16. Almost all of it is dummy collaborators
  for `PackRegistrationController`, whose six constructor arguments include another controller.
  `mock(PackRemovalController)` appears 9 times and `mock(PackSummaryQuery)` 10 times. The mocks
  sit at the application seam and the assertions read the HTTP response, so this is a design smell,
  not over-mocking.

### B. Effectiveness

**The static triage** scanned 487 files and 4,652 test methods, and flagged 142 (3.1 %):

| Flag | Count | Read verdict |
|---|---:|---|
| `type-only-assert` | 96 | Mostly not real: the input reaches one throw site, or the type is the contract (`UnsupportedOperationException` for a read-only view, `IllegalStateException` for a 500). About 20 are real (below). |
| `many-asserts` | 15 | Field-by-field mapping tests; legitimate. |
| `sleep`, `wallclock` | 12 | 4 tight or vacuous under load, 2 negative waits, 4 duplicated bounded polls; the rest are fine. |
| `skip-or-xfail` | 8 | All platform guards (above). |
| `interaction-assert` | 5 | At true boundaries; one `verify` is redundant once its stub matches exactly. |
| `over-mocking`, `weak-only-assert`, `long-test`, `no-assertion` | 11 | 2 `no-assertion` flags are false positives: the helpers assert. |

**The squads' verdicts:**

| Squad | Files read | KEEP | FIX | RETIRE |
|---|---:|---:|---:|---:|
| packs domain and application | 38 + 8 sampled | 31 | 7 (+ 2 sampled) | 0 |
| packs infrastructure | 32 + 8 sampled | 18 | 14 (+ 2 sampled) | 0 |
| HTTP and edge slices | 21 + 8 sampled | 15 | 6 (+ 2 sampled) | 0 |

No test is a RETIRE candidate under the development-assist lens. Every mission-era test that looks
like scaffolding is the only guard of its behavior. The edge squad proposed retiring about 40
null-check constructor tests on the grounds that NullAway forbids null at compile time. That is
**not adopted** here. NullAway checks only `@NullMarked` callers, while Spring, reflection and the
YAML and JSON binders can still pass null. PR #481 kept such a check (`Relationship`) for exactly
that reason. It is listed as a question for the operator.

**Six gaps proved by planted breaks.** Each production edit below was applied alone in its own
class, and only the named tests were run. All six stayed green. The control, `GuardWiring`'s
`<` changed to `<=`, turned `GuardWiringTest.aTokenOfExactlyThirtyTwoCharactersIsAccepted` red as
it should, so the method can detect a break.

| # | Planted break | Tests that stayed green | Pattern |
|---|---|---|---|
| 1 | `ExpectedArtifact.java:49`: the template path rule drops `isRelativeInside(path)`, so `templates/../x` is accepted | every `:missions` and `:missions-edge` test, and 13 `:packs` mission-type and template classes | An earlier rule masks the rule under test. In `MissionValuesTest.java:36-43` no input starts with `templates/`, so the prefix check refuses every one before the escape check runs |
| 2 | `Relationship.java:67`: the `from` refusal names the field `to` | `RelationshipTest` (10) | A comment claims a pin the test does not make: `Relationship.java:89-90` says "RelationshipTest pins it", but `RelationshipTest.java:98-112` checks only the type |
| 3 | `ActivationIdMint.java:60`: `time > MAX_TIME` changed to `>=`, refusing a valid clock | `ActivationIdMintTest` (6) | The limit is tested only from outside. Compare `JcsTest`, which pins both sides of ±(2^53−1) |
| 4 | `DrgEdge.java:49`: the null check of `when` deleted | `DrgEdgeTest` (1) | The name says "every field", but fields 5 and 6 are never exercised |
| 5 | `HttpsExchange.java:33`: the cap limit `MAX_VALUE − 8` changed to `MAX_VALUE − 1` | `GuardedHttpsExchangeTest` (76) | The boundary is probed at 0 and `MAX_VALUE`, not at the real edge |
| 6 | `MissionJson.java:25`: `@ImportRuntimeHints` deleted | all `:missions-edge` tests | `MissionJson` is the only `*Json` with runtime hints and no hints test; every other has one, such as `ProfileJsonTest` |

**More FIX verdicts with an exact remedy**, by pattern. None was planted. Each is the same kind
of gap as one proved above.

- **Type-only where a sibling or the contract pins the reason:**
  - `Pep440RangeTest:148,154`: two IllegalArgumentException sources.
  - `MissionStepContractRoundTripTest:189` (compare `:180`) and `LocalSourceReaderTest:233` (compare `:228`).
  - `AgentProfileYamlTest:230,240`, `GraphDocumentTest:67` and `PlacedWriteTest:183`.
  - `ClosureQueriesTest:613` and `ActivationSelectionServiceTest:246` (pin `Kind.MISSING`).
  - `EffectiveAgentProfilesControllerTest:121` and `ActivationsEffectiveProfilesTest:291`.
  - `GitSourceFetcherTest:311,519,650`: `:650` is `isNotNull` on a thrown future.
- **An empty builder masks the rule under test:** `TacticEntityTest:104` and
  `ProcedureEntityTest:154`. A null `packId` throws first, so dropping the id check stays green.
- **The name promises what the body does not check:**
  - `GitHostTest:217` says the host is not fetched from, but asserts nothing about the server.
  - `LimitedInputStreamTest:105`: the close half is vacuous, because its source's `close()` does nothing.
  - `GitSourceRulesBoundsTest:23` claims a possessive match but has no refused neighbor at the cap.
- **Wiring tests that assert the configuration exists, not what it produces:**
  - `MissionsEdgeConfigurationsTest:34-37`: `hasSingleBean` of the configurations it registered itself.
  - `MissionsEdgeWiringTest:35`: `getBeanDefinitionCount().isPositive()`.
  - `PackSourceWiringTest:84`: `isNotNull`.
  - `ProfilesEdgeConfigurationsTest:42-84` shows the right form.
- **Friction (DIRECTIVE_041):** `KindBindingsTest:74-75` pins `hasSize(15)` and `get(14)`, so a correct
  sixteenth kind turns it red. Assert `startsWith(...)` the fifteen in order.
- **Duplicated test knowledge:** `AllowlistUnionTest:109-124` repeats a properties-defaults check
  (`:124` repeats `:115`), which `PackSourcePropertiesTest:47` also holds.

### C. CI and quality gates

- **Delivered since 2026-11-03:**
  - The per-PR gate is unchanged and green.
  - The mutation nightly now:
    - keeps PIT out of `verifyAll` and `check`;
    - takes its module list from the build;
    - shows margins and timed-out mutants;
    - keeps PIT's output (#506).
  - SonarCloud runs nightly (ADR-0058): Security rating **A**, 0 vulnerabilities, 0 hotspots.
- **The mutation nightly is red for a reason that is not a floor.**
  - **No `:packs` report for three nights:**
    - On 2026-11-10 two `ActivationWireTokenParityTest` cases failed under PIT (fixed in 95a078e5).
    - On 2026-11-11 one test failed with no mutation applied, and its log expired before anyone read it.
    - On 2026-11-12 the step ran past its 80-minute limit.
  - **The groups that do report are all above their floors.** The margins show where a slide would bite first:

    | Group | Margin over floor | Mutants it can lose |
    |---|---|---:|
    | `:profiles` | +7.6 points | 6 |
    | `:missions` | +8.6 points | 13 |
    | `:missions-edge` | +7.6 points | 13 |
    | `:profiles-edge` | +14.1 points | 17 |
    | `:kernel` | +8.6 points | 60 |
    | `:activations` | +7.5 points | 107 |

- **Reliability rating E** is held there by exactly five findings. They are the false positives
  ready to mark on #463 (S2095, S6416 ×3, S9428).
- **S1314 is cleared** by the `0_` octal form of ADR-0072. **S1845 shows 6:** the 5 deferred to
  slices B and C, plus `ArtifactReadService.java:110` (`LISTED`). A seventh,
  `AgentProfile.java:30` (`ROLE`), was added on 2026-11-11, after the ruling. Nothing enforces
  ADR-0072 on new code.
- **S8786** remains at `ReservedOperationCoverageTest.java:54`, though #481 made that file's regex
  possessive.
- **The complexity gate is still off.** ADR-0058 turns the Checkstyle limits on once #367 has
  refactored the methods over them (#371). Checkstyle has no complexity, length or parameter-count
  module today, and SonarCloud has never raised S3776, S138, S1541 or S107 on this project. So the
  project's profile appears not to measure method complexity either.

### D. Production code

**Metrics (SonarCloud, `9cd0ad85`):**

| Metric | Value |
|---|---|
| Lines of code | 49,324 in 675 files |
| Cyclomatic / cognitive complexity | 10,199 / 4,681 |
| Duplication | 0.7 % (26 blocks) |
| Technical debt | 80 min (A) |
| Open issues | 12: 7 code smells (S1845 ×6, S8786), 5 false-positive bugs |
| TODO / FIXME in main code | 0 |
| `NOSONAR` / `@SuppressWarnings` | 2 / 5 |

**Complexity hotspots.** The top 9 by cognitive complexity are all in `packs`:

| File | Cognitive | Cyclomatic | Lines of code | Reading |
|---|---:|---:|---:|---|
| `domain/PackPolicy` | 208 | 328 | 889 | Accidental. One class (1,279 lines, 75 static methods) holds level policy, verdicts, step-enhancement faults, prompt slots, the graph build (`:641-886`), schema-finding text (`:954-1165`) and path ordering |
| `domain/PackCompositionResolver` | 161 | 170 | 670 | Accidental. `refuseContests` (`:613`) collects, groups, reconciles and composes in one method, five levels deep |
| `infra/storage/FilesystemPackStore` | 135 | 211 | 799 | Mostly linear guard chains. It mixes I/O, companion planning and containment |
| `infra/storage/FileSystemPackInspector` | 134 | 175 | 591 | Essential, as a reader. The inner `FullRead` (`:311`) is a 240-line stateful class |
| `infra/storage/WholePackStore` | 98 | 166 | 742 | Moderate |
| `kernel/domain/Pep440Range` | 75 | 101 | 320 | Essential: a parser and its version algebra |

**Duplication.** 22 of the 26 blocks are hand-written accessors repeated across the entity classes.
2 are binding boilerplate. 2 are real: `LimitedStream` is copied in `ArchiveBodyAdvice.java:149` and
`PackSourceBodyAdvice.java:73`, differing only in the limit. Two cases of knowledge duplication are
too small for Sonar to see:

- **Code-point and `/`-path ordering is implemented five times:**
  - `PackPolicy.java:1175,1188`
  - `FileSystemPackInspector.java:726`
  - `ActivationDocument.java:310`
  - `ArtifactPosition.java:40`
  - missions-edge `CodePoints.java:15`
- **The not-found refusal codes are listed twice, and the lists already differ.**
  `RefusalCode.notFound()` (`:258`) has 16 codes and no caller in main code. `RefusalHandler.java:326-343`
  hand-lists 17, adding `NO_PROFILE_MATCHED`.

**The canonical model (DIRECTIVE_032; charter ruling of 2026-11-10).** The ruling says a change
replaces the old shape outright, with no deprecation window and no reader for the old shape beside
the new one. Four findings, each read against its source:

- **The retired "Doctrine" vocabulary names kernel types.** `docs/context/domain.md:159-163` retires
  "Doctrine" as a name for any domain object; the graph is the Charter Reference Graph (CRG). The
  code still has `DoctrineGraph` (23 references in main code), `DrgNode` (32) and the storage DSO
  `DrgEdge` (10). No CRG-named type exists.
- **The unversioned pack-descriptor branch is a legacy reader beside the canonical one.** For a
  descriptor without `schema_version`, which `PackDescriptor.java:23` calls "legacy",
  `PackPolicy.java:331,347` turns text in a prompt slot into the warning
  `PROMPT_SLOT_NOT_EMPTY_UNVERSIONED` instead of the blocking `PROMPT_SLOT_NOT_EMPTY`. The ruling
  rules this out. ADR-0066 added the branch on 2026-11-08, two days before the ruling, and nothing
  reconciles the two. It is a violation pending a ruling.
- **The activation store's version 1 reader is a legacy reader beside the canonical one**
  (`ActivationFileCodec.java:32-38,102-126`). The ruling rules this out too. ADR-0020, amendment 2026-11-07,
  item 6 (Storage) added it, and it predates the ruling. The store is unreleased, so no
  field file needs the reader. It is a violation pending a ruling.
- **`kernel/domain/Relation`** is superseded by `RelationType` and has no caller in main code; only
  `VocabularyEqualityTest` uses it. It also shares its name with the hypermedia
  `packs/infra/api/Relation`.

**Architecture.** `HexagonalArchitectureTest` (73 tests, 21 named rules) and four other ArchUnit
classes cover every module. One gap: nothing stops two outbound adapters of the same slice from
naming each other, for example `activations.infra.storage` and `activations.infra.packs`. Only packs has
the "adapter stands alone" rules.

**Test hooks in main code.** `InForceResolution.java:56,65` keeps static counters documented as a
test hook of the NFR-003 gate, which the ArchUnit rule against test seams in main code does not
catch. `PackPolicy.handles` (`:428`), `RegistrationJobs.retained` (`:801`) and
`ClosureQueries.allConfigured` (`:184`) are public and called only from tests.

## Top 5 weaknesses, ranked by risk

1. **Rules that are tested but not guarded.**
   - **What:** six planted breaks stayed green, with about 20 more FIX verdicts of the same kinds. The
     most serious is the escape check of an expected artifact's template path (`ExpectedArtifact.java:49`),
     a path-containment rule whose domain guard no test exercises.
   - **Why it matters:** a refactor can remove any of these rules and every gate stays green.
2. **The `:packs` mutation report is missing, and the suite is slower than it needs to be.**
   - **What:** the cost under PIT is concentrated in a few tests that PIT re-runs once per mutant they cover:
     - `RegistrationJobsTest.closingTheJobsEndsWhatWaitsRefusedBusyAndAuditsEachOnce`
       (`RegistrationJobsTest.java:636-656`) takes **30 s on every run**, in plain `test` as well:
       - its test gate is never released;
       - the workers park on the gate's 30-second cap;
       - `close()` waits them out (`RegistrationJobs.java:834-837`).
       Releasing the gate once the four jobs read REFUSED should bring it to about 0.1 s. Under PIT,
       each mutant this test covers costs about 30–42 s.
     - `PackFileSetExactnessTest` takes 16.7 s, because its fingerprint test re-digests the whole pack
       for every appended file.
   - **Status:** the nightly timeout is owned by another session (#461).
3. **Old shapes and retired names beside the canonical model.** Four places:
   - the unversioned-descriptor branch (a legacy reader; violation pending a ruling);
   - the activation store's version 1 reader (a legacy reader; violation pending a ruling);
   - `Relation` beside `RelationType`;
   - the "Doctrine" type names.

   Each needs a ruling or a bulk rename rather than a quiet fix.
4. **Timing preconditions that are slept on, not waited for.**
   - **Can pass vacuously on a loaded runner:**
     - `RegistryLockOrderTest:110` stands in a 300 ms sleep for "the second writer is parked".
     - `GitSourceFetcherTest:596-618`.
     - `FileSystemPackSourcesTest:197-229`: the race test passes even if the writer never moved a file.
   - **Can fail spuriously under PIT:** `GitCommandTest:222-231`, a 1-second shim inside a 1.5-second
     deadline.
   - **Waits for something not to happen:** `RegistrationWiringStagingTest:116` and
     `RegistrationAuditWiringTest:156`.
   - **Duplicated:** the same "wait until the registration ends" poll is copied four times in
     `deployable`.
   - **No CI switch:** the three root-user skips of `FileSystemPackSourcesTest` have none that would make
     them fail instead of skip, unlike the symbolic-link skips.
5. **Complexity concentrated in `packs`, with its gate still waiting.**
   - **What:** `PackPolicy` and `PackCompositionResolver` carry 369 of the project's 4,681 cognitive
     points. The ADR-0058 limits are off until #367 lands, and nothing enforces ADR-0072 on new constants.

## Recommendations

| # | Action | Weakness | Size |
|---|---|---|---|
| R1 | Fix the six proved gaps first, each with its planted break as the red-first evidence. Then work through the FIX list by pattern. | 1 | One PR per module |
| R2 | Release `RegistrationJobsTest`'s gate. Also cut `PackFileSetExactnessTest`'s re-digest cost without weakening its oracle (owner: the #461 session). | 2 | Small |
| R3 | Put the four model questions below to the operator, then act in one mission per ruling. | 3 | Ruling first |
| R4 | One shared "await until" helper for deployable's registration polls. Replace the parked-thread sleeps with a state check. Add a CI switch for the root-user skips. | 4 | Small |
| R5 | Keep #367 and #371 moving. Split `PackPolicy` behind its facade (schema-finding text, graph build, ordering) as part of #367. Add an ArchUnit or Checkstyle check for the ADR-0072 constant rule. | 5 | Medium |
| R6 | One kernel `CodePointOrder`. Have `RefusalHandler` read `RefusalCode.notFound()`. One `LimitedStream`. A test builder for `PackRegistrationController`'s collaborators. | D | Small each |

## Questions for the operator

1. **The unversioned pack descriptor** (`PackPolicy.java:331,347`, `PackDescriptor.java:23`,
   ADR-0066). The charter default is to require `schema_version` and remove the warning-only branch.
   Or record an exception.
   - Requiring it fails every open-packs descriptor that lacks it: 31 of the 51 `pack.yaml` files in
     `spec-kitty-open-packs` at `ab710df`, and open-packs follows the model.
   - An exception needs a charter amendment.
2. **The activation store's version 1 reader** (`ActivationFileCodec.java:32-38,102-126`; ADR-0020,
   amendment 2026-11-07, item 6 (Storage)). The charter default is to remove the version 1 reader.
   Or record an exception.
   - Removing it means a version 1 store must be rewritten once or recreated. The store is
     unreleased, so no field file is known to need the reader.
3. **"Doctrine" in type names** (`DoctrineGraph`, `DrgNode`, `DrgEdge`). Should they be renamed to the
   CRG vocabulary as one bulk-edit mission, or kept with a recorded exception in `domain.md`?
   - A rename touches about 65 references in main code, plus the tests.
   - The names are not wire-visible: no file in `contracts/` names `DoctrineGraph`, `DrgNode` or
     `DrgEdge`. Only two schema titles carry the retired acronym (`pack-descriptor.schema.json:149`,
     `charter.schema.json:673`).
4. **The null-check constructor tests** (about 40, mostly in the edge slices). Keep them as the guard
   for callers NullAway does not see, or retire them as a class?
   - Retiring them loses the `IllegalArgumentException` contract for YAML, JSON and Spring callers
     that NullAway does not see.
   - Keeping them adds little unless something pins the field name in the refusal.

## Reproduce

```bash
./gradlew syncPacks -PopenPacksDir=<open-packs checkout>
./gradlew verifyAll :charter-reports:testCodeCoverageReport --continue --max-workers=1 -PrequireSymlinks=true
# Test results: service/*/build/test-results/test/*.xml, build/contracts/test-results/, build-logic/build/test-results/
# Coverage: service/charter-reports/build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml
uv run packs/internal/assets/test-quality-scan.py --paths service/*/src/test/java service/packs/src/driftTest/java --out <dir>
```

The planted breaks are the six edits in the table under "Six gaps proved by planted breaks". Apply
one at a time, run only the named tests, then revert.
