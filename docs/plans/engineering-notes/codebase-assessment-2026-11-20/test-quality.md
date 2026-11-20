---
title: "Test quality, 2026-11-20"
description: How good the charter's tests are at 25e24c37, with the open planted-break gaps and timing tests from 2026-11-12, contract scenarios and mutation margins.
doc_status: draft
updated: 2026-11-20T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/engineering-notes/codebase-assessment-2026-11-20
  - plans/engineering-notes/code-and-test-quality-assessment-2026-11-12
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the test gaps and timing tests still open since 2026-11-12, the thin mutation margins, and the fix for each.
---

# Test quality, 2026-11-20

Lens L3 of the [codebase assessment of 2026-11-20](/plans/engineering-notes/codebase-assessment-2026-11-20/), pinned
at `25e24c37`. It judges the test suite: inventory, timing tests, the planted-break gaps of 2026-11-12, contract
scenarios and mutation testing. It changes no code.

Baseline: `25e24c372bae838d95a9328be57ee2063f6c05d1`. Read-only. No PIT, no build, no test run, no planted break
(all six gaps were settled by reading; see T3). The scan script is `scripts/analysis/tests/jscan.py`; its output
(`tests.json`, `summary.json`) goes to `build/analysis/tests/` and is not committed. Run logs are not committed either.

**No correctness, data or security defect was found in production code.** The findings below are test-quality
gaps: rules whose guard a refactor could delete with every gate green, and timing tests that can pass vacuously.

## T1. Inventory per module (static, `.java` lines incl. comments)

| Module | Main files | Main LOC | Test files | Test LOC | Test/main | Test methods* | Flagged* | @DisplayName | Sentence names* |
|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|
| packs (+testFixtures) | 498 | 76,346 | 410 | 106,522 | 1.40 | 3,705 | 225 | 1,270 | 94 % |
| activations | 86 | 15,167 | 89 | 22,048 | 1.45 | 840 | 53 | 4 | 98 % |
| deployable | 18 | 1,950 | 245 | 22,928 | 11.8 | 484 | 17 | 30 | 97 % |
| kernel | 99 | 6,958 | 32 | 3,676 | **0.53** | 218 (parameterized) | 16 | 89 | 80 % |
| artifacts-edge | 63 | 6,636 | 31 | 6,627 | 1.00 | 165 | 10 | 0 | 100 % |
| pack-validation (+driftTest) | 14 | 1,989 | 15 | 4,327 | 2.18 | 148 | 7 | 9 | 90 % |
| profiles-edge | 30 | 2,387 | 17 | 3,896 | 1.63 | 140 | 22 | 0 | 98 % |
| profiles | 26 | 2,231 | 7 | 1,653 | 0.74 | 77 | 6 | 70 | 39 % (DisplayName-led) |
| missions-edge | 37 | 2,140 | 10 | 1,713 | 0.80 | 52 | 1 | 0 | 100 % |
| missions | 23 | 1,849 | 5 | 1,127 | 0.61 | 52 | **16** | 49 | 69 % |
| web-transport | 12 | 1,667 | 10 | 1,423 | 0.85 | 57 | 1 | 21 | 98 % |
| routing | 20 | 997 | 5 | 762 | 0.76 | 34 | 5 | 39 | 47 % |
| routing-edge | 21 | 972 | 6 | 498 | **0.51** | 21 | 1 | 0 | 95 % |
| pack-check | 1 | 13 | 0 | 0 | — | — | — | — | — |
| **Total** | **948** | **121,302** | **882** | **177,200** | **1.46** | **5,993** | **380** | | |

\* From a Java port of the scan flags (`jscan.py`, ≥5 words = sentence name). Command, from the repository root:
`python3 -I scripts/analysis/tests/jscan.py . build/analysis/tests`.

**Style.** Uniform and good: method names are behavior sentences (`aWriterResolvesItsPackOnlyAfterAnotherWritersSwapEnded`);
kernel/missions/profiles/routing lead with `@DisplayName` instead. Given/when/then comments: 2 in the whole suite (style is
the sentence name plus Gherkin for contract scenarios). Assertions are AssertJ only (16,7xx `assertThat`, 0 JUnit
`assertEquals/True`). Mockito: 32 test files (16 on 11-12, 0 on 11-03); `mock(PackSummaryQuery)` in 10 files and
`mock(PackRemovalController)` in 9, still no shared builder for `PackRegistrationController`'s collaborators (11-12 R6 open).

**Fixture weight.** Heaviest test classes: `HexagonalArchitectureTest` 2,411 lines, `FileSystemPackInspectorTest` 1,958,
`CompositionRulesTest` 1,454, `ArtifactRemovalServiceTest` 1,396. Resources: packs test 2.6 MB + testFixtures 3.1 MB
(example packs, 167 files); biggest golden files `golden/surfaces.json` 71 KB, `pack-validation golden/validate.json`
50 KB, rewritten by gated `rewriteTheFile` tests (skip unless asked; fine).

**Duplicated helpers.** The "wait until the registration ends" poll exists as 6 copies in 5 `deployable` test files (`awaitEnd`/`did not end`
variants; T2 lists them), `HttpResponse<String> send(` in 4, `deleteTree(Path` in 5. No shared `await until` helper exists in any test
source set or `testFixtures`.

**Scan comparison with 11-12 (142 / 4,652 = 3.1 %).** Not reproducible as written. The scan asset available here
(`/opt/spec-kitty/packs/internal/assets/test-quality-scan.py`, = open-packs cache `ecda7b00`) is pytest-only: it globs
`test_*.py`/`*_test.py` (line 478) and on the Java roots reports `scanned 0 files, 0 tests` (run output not
committed). `packs/internal` is not synced in this checkout and the pinned open-packs repo (`1692a3b1`) is not
reachable from this session, so whether the pinned asset has a Java mode could not be checked. The approximate port
flags 380 / 5,993 (6.3 %): type-only 195, weak-only 74, many-asserts 42, no-assertion 22 (mostly helper-asserting, e.g.
`ActivationBodyAdviceTest`), skip 22 (platform and golden-rewrite guards), `wallclock` 18, sleep 15, long 12, verify 4,
over-mocking 3. The port is looser than the original (its type-only rule fires on any `isInstanceOf` without a message
check in the method), so the rate is not comparable; the method count (+29 %) is.

## T2. Timing and vacuous-pass items from 11-12

| 11-12 item | Status | Evidence at 25e24c37 |
|---|---|---|
| `RegistryLockOrderTest:110` 300 ms sleep for "second writer parked" | **OPEN** | `RegistryLockOrderTest.java:110` `Thread.sleep(300)`. The same class already has the right helper, `awaitQueued` (`:143-150`, polls `lock.hasQueuedThread`), used at `:57`; `RegistryWriteLock.hasQueuedThread` exists (`RegistryWriteLock.java:106`) |
| `GitSourceFetcherTest:596-618` concurrency cap | **OPEN** | `:597-623`; cap proved by `highest == 2` after a 2-latch + `Thread.sleep(150)` (`:607`). A broken cap of 3+ passes if the third fetch is not scheduled within 150 ms |
| `FileSystemPackSourcesTest:197-229` race test | **OPEN** | `:192-229`; asserts `refused == 0` but never that the writer completed a move (no counter), so a writer that never ran passes |
| Root-user skips without CI switch | **OPEN** | `assumeFalse("root"...)` at `:149`, `:163`, `:237`; only `charter.requireSymlinks` exists as a fail-instead-of-skip switch (`service/packs/build.gradle.kts:176-211`) |
| `GitCommandTest:222-231` 1 s shim in 1.5 s deadline | **OPEN** | `:221-231` unchanged (`shim.sleep("fetch", 1)`, `Duration.ofMillis(1500)`); also `:234-237` sleeps 200 ms past a 50 ms deadline (safe direction) |
| `RegistrationWiringStagingTest:116` negative wait | **OPEN** | `:116` `Thread.sleep(1500)` then `assertThat(sweep).isNotNull()` (weak) and "file still exists" — waits for something not to happen |
| `RegistrationAuditWiringTest:156` negative wait | **OPEN** | `:152-156` poll then `Thread.sleep(300)` "a second entry would arrive in this time" |
| 4x duplicated registration poll in deployable | **OPEN (grew)** | `ActivationListingFaultTest:214-225` (returns silently at the deadline), `ExampleCoverageTest:206-219`, `RegistrationAuditWiringTest:152-155`, `bdd/PackRegistrationSteps:230-245` and `:405-420`, `bdd/HoldableGate:75` |
| `RegistrationJobsTest closingTheJobs…` 30 s gate | **FIXED** | `cad3f433` (2026-11-12): releaser thread waits on a condition (all four REFUSED) then releases the gate, `RegistrationJobsTest.java:680-714` |
| `PackFileSetExactnessTest` 16.7 s | **MITIGATED** | `ecd257d9`: trees written on first use (`PackFileSetExactnessTest.java:70-76, 250-257`). Under PIT it is still the slowest `:packs` test: 8.0 s / 8.1 s (run 43794141037). The per-file re-digest of #531 item 1 is not done |

**#593, the reference shape** (`aa6babfc`, `55c6086e`, PR #599, `MixedReadConcurrencyTest`). At the baseline, one case of the same
class, `aSnapshotStartedInsideASwapRetriesIntoAVersionOnceTheSwapEnds`, still released a swap after a fixed 15 ms and
failed 1 run in 100 on CI; `d04a8573` (#621) makes it wait for the read to start. Copy the shape in its fixed form:

1. The property is bounded **per operation**, not per test: each write's wait, from the barrier to the end of its
   write, is held to `WAIT_BOUND = 4 s`, chosen below the production registry-lock limit (5 s), so a starving write
   fails before the lock gives up.
2. A failure is **named** with the operation and round (`write P7 waited 4.2 s in round 63 (bound 4.0 s)`); a lock
   refusal or a write still running at the per-round deadline (`ROUND_DEADLINE = 15 s`) is named too.
3. `@Timeout(120)` is only a **hang cap**; it is not the oracle.
4. A **control** proves the bound bites: `aWriteHeldBehindASlowSwapIsNamedWithItsRound` holds one swap 600 ms behind a
   300 ms bound and requires named breaches.
5. The **wording** is a pure function (`waitBreach`) with its own unit test at both sides of the bound.
None of the eight open items uses this shape; each would map onto it (condition wait instead of sleep, named breach,
hang cap, a control).

Other sleeps (not in 11-12): `ConsistentReadTest:410` (30 ms), `StagingPermissionsTest:88`, `HttpsGuideFetcherLoopbackTest:141`
(100 ms), `StreamedUploadTest:180`; all inside condition polls with deadlines, acceptable. Main code:
`GitCommand.java:289` watch tick (production poll, fine).

## T3. The six planted-break gaps of 11-12 (static re-check, no break planted)

| # | Rule | Status | Evidence |
|---|---|---|---|
| 1 | `ExpectedArtifact.java:49` template path stays inside `templates/` | **OPEN at the domain; guarded upstream by the schema** | Domain tests still never pass a path that starts with `templates/` and escapes: `MissionValuesTest.java:37-44` uses `/spec.md`, `../spec.md`, … which the prefix check refuses first; `MissionTypeTest.java:196` is a `.`-segment case. The YAML path is guarded by the schema `not` (`MissionTypeSchemaTest.java:139`, `MissionTypeBindingTest.java:286-291` asserts the *schema* refuses `templates/../../../x.md`), so dropping `isRelativeInside` in `ExpectedArtifact` stays green |
| 2 | `Relationship` names `from`/`to` | **OPEN** | Moved to kernel: `kernel/domain/Relationship.java:113-115`; the comment at `:134-136` still says "RelationshipTest pins it"; `RelationshipTest.java:156-171` checks the type only |
| 3 | `ActivationIdMint` `MAX_TIME` | **OPEN** | `ActivationIdMint.java:60`; `ActivationIdMintTest.java:76-77` probes only `1L << 48` (refused); no test mints at `(1L << 48) - 1` |
| 4 | `DrgEdge` `when` null check | **FIXED** | `fcfe0854` (2026-11-15, retiring `impacts`) made `DrgEdgeTest.java:18-21` pass `null` for `when` and `reason`; type-only (no field name), which is enough for this record |
| 5 | `HttpsExchange` cap `MAX_VALUE − 8` | **OPEN** | `HttpsExchange.java:33`; `GuardedHttpsExchangeTest.java:291` probes only `Integer.MAX_VALUE`; file unchanged since `4417e180` |
| 6 | `MissionJson` runtime hints | **OPEN** | `MissionJson.java:26,55-59`; no missions-edge test references `MissionJson` or `RuntimeHints` (compare `ProfileJsonTest.java:103`) |

Same-pattern FIX items of 11-12 rechecked: `MissionsEdgeConfigurationsTest:34-37` (hasSingleBean of its own
configurations), `MissionsEdgeWiringTest:35` (`isPositive` bean count), `PackSourceWiringTest:83,126` (`isNotNull`),
`KindBindingsTest:75-76` (`hasSize(15)`, `get(14)` friction) — **all OPEN**. The type-only refusal pattern has spread to
new code: `SubstepTest.java:58-160` (WP07, `b267818e`, after 11-12) has six type-only refusal tests on a class with
several rules per input, the same shape that masked gap 1.

## T4. Contract scenarios

- 88 `.feature` files, **1,001 scenarios** (653 on 11-12), 987 under a `@contract` tag, **42 `@WIP`** (unchanged: journeys
  28, distribution 6, trust 5, integrity 3; the 11-12 "profiles 1" moved to journeys). `CharterContractSuite` leaves `@WIP`
  out; `UndefinedStepGuard` fails a selected scenario with an unbound step.
- Size: median 9 steps per scenario, p90 20, max 84. Readability sample is good: `registration/pack-uninstall.feature:58-70`
  reads as a REST story (`When I DELETE … with the remembered ETag / Then the response status is 204`), each file starts
  with its ADR/FR provenance and its step vocabulary. `tensions.feature:28-31` expects `… when when a change is …` (the
  authored condition starts with "when"), which reads as a typo; cosmetic.
- **[ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) D1 gate.** D1: every core switch is gated by this repository's BDD scenarios and contract tests, never by
  core's Python answers. The 2026-11-12 amendment made it checkable: `contracts/charter/scenarios/switch-gate.yaml` lists
  every operation as `ready` or `held` with the exact unasserted statuses. **Enforced** by the Cucumber `@AfterAll` hook
  `deployable/bdd/SwitchGate.java:342-353` (fails the contract suite in `verifyAll`) fed by
  `CharterSteps.everyExchangeIsRecordedForTheSwitchGate` (`CharterSteps.java:162-180`); its logic is unit-tested with
  controls in `SwitchGateTest` (planted contract, `:40-320`, incl. `theShippedRegisterNamesExactlyTheContractsOperations`).
  Register today: 79 operations, 77 ready, 2 held (`deleteActivation`, `getPackRegistration`, both `[500]`). Not
  enforced (stated by the ADR, `0059:486-487`): the ready→held diff against the last tag (release-review duty only).
- **[ADR-0070](/adr/0070-charter-tests-own-example-packs-not-open-packs-content/) example-pack coverage.** Packs in `service/packs/src/testFixtures/resources/example-packs/` (built-in,
  org-base, org-alt, org-mandate, project, tensions; 167 files). `ExamplePackCoverageTest` enforces every schema property
  (`:36`), every file kind (`:58`), with controls (`:42`, `:95`, `:105`); `ExampleCoverageTest` (deployable) enforces every
  raised refusal code produced by a scenario or an example pack.
  - **routing_catalog: covered.** `built-in/routing_catalogs/default…`, `org-base/routing_catalogs/{default,cost-led}…`,
    `org-mandate/routing_catalogs/mandated…`, pinned by `ExamplePackCoverageTest.theRoutingContentIsHeldAtTwoLevels…`
    (`:70-85`); 140 scenario lines mention routing catalogs.
  - **tensions: covered.** `example-packs/tensions/` (6 directives, `drg/fragment.yaml:55-75` `relation: tension`),
    read by `tensions.feature` (8 scenarios, `:20-21` `Given the example pack "tensions" of the charter`).
  - **uninstall: covered by scenarios, not by example packs.** `registration/pack-uninstall.feature` (12 scenarios,
    ADR-0081) and `pack-removal.feature` author their packs inline (`Given the stored pack "qa" …`); no test removes an
    example pack (e.g. built-in with org-base depending on it). ADR-0070 decision 2 does not list uninstall, so this is
    not a breach; `pack_has_dependents` is produced by a scenario, so `ExampleCoverageTest` passes.

## T5. Mutation testing

Workflow is now **weekly** (`mutation-testing.yml:31`, `cron: '43 2 * * 1'`), three shards, 110 min step budget.
Only one run exists under this workflow file: **run 43794141037** (schedule, 2026-11-16, head `97985727`), **green**.
Shards: others 16 m 35 s, packs-core 44 m 1 s, packs-storage 40 m 29 s (37-40 % of budget). Margin = detected −
⌈floor × mutants / 100⌉, computed from the `checkMutationFloors` lines of the job logs (jobs 133058994356,
133058994609, 133058994610).

| Module / group | Mutants | Score | Floor | Points over | Margin (mutants) | 11-12 margin |
|---|--:|--:|--:|--:|--:|--:|
| **profiles** | 261 | 91.1 % | 90 | **+1.1** | **3** | 6 |
| **packs infra.archive** | 429 | 82.9 % | 80 | **+2.9** | 12 | — |
| **pack-validation** | 350 | 88.0 % | 85 | **+3.0** | 10 | 0 (#561) |
| packs domain+application | 4,141 | 90.3 % | 85 | +5.3 | 219 | — |
| packs infra.storage | 3,241 | 90.4 % | 85 | +5.4 | 175 | — |
| missions-edge | 171 | 91.2 % | 85 | +6.2 | 10 | 13 |
| artifacts-edge | 354 | 91.5 % | 85 | +6.5 | 23 | new |
| activations | 1,586 | 91.8 % | 85 | +6.8 | 107 | 107 |
| packs infra.fetch | 693 | 78.2 % | 70 | +8.2 | 56 | — |
| kernel | 846 | 83.6 % | 75 | +8.6 | 72 | 60 |
| missions | 174 | 78.7 % | 70 | +8.7 | 15 | 13 |
| profiles-edge | 167 | 98.8 % | 85 | +13.8 | 23 | 17 |
| routing | — | never run | 90 | — | — | — |
| routing-edge | — | never run | 85 | — | — | — |

- **Surviving-mutant themes** (summed per mutator from the PIT logs): `ConditionalsBoundaryMutator` is the top or
  second survivor in every shard (others 48, packs-core 67, packs-storage 71), then `VoidMethodCallMutator` (34/72/49) and
  `NegateConditionalsMutator` (21/55/35). Boundary survivors are the PIT signature of planted gaps 3 and 5 (limits
  probed only from outside). `NullReturnVals` dominates *no coverage* (others 99, storage 68). Per-class tops are only in
  the job summary/artifacts, not readable here (artifact download blocked by the proxy, 403).
- **Slowest tests under PIT** (run 43794141037): `artifacts-edge ExampleVocabularyReadTest.everyTermOfTheExampleVocabularyIsReadByItsAddress`
  **45.7 s** (138 uncached full reads through the whole packs stack, `ExampleVocabularyReadTest.java:70-92`; not in #531);
  `activations EffectiveReadOperationBudgetTest.aWarmReadComposesFromTheCacheOnceAndResolvesOnce` 12.3 s; `packs
  PackFileSetExactnessTest.theFingerprintChanges…` 8.0 / 8.1 s; `profiles-edge ProfilesEdgeConfigurationsTest` 5.8 s;
  `pack-validation PackLoadTest` 5.0 s; `missions-edge MissionsEdgeConfigurationsTest` 4.9 s. 79 mutants TIMED_OUT
  across shards (12 others, 54 packs-core, 13 packs-storage: the sum of PIT's per-mutator `TIMED_OUT` counts in the
  shard logs; counted as detected).
- **Issues.** #461 (open; the bot issue of the 11-10 run, still not closed after the green 11-16 run). #530 (open, JVM-flag
  A/B, no measurement yet). #531 (open: exactness re-digest, `HostileArchiveCorpusTest` fixtures, `ProcedureAndGuideCorpusTest`
  — none done). #593 closed by PR #599.

## Findings

1. **MEDIUM — Five of the six proved gaps of 11-12 are still open, eight days on.** Evidence: T3 (gaps 1, 2, 3, 5, 6; only
   4 fixed, as a side effect of `fcfe0854`). Gap 1 is a path-containment rule (`ExpectedArtifact.java:49`) whose domain
   guard no test exercises; the schema guards the YAML route (`MissionTypeBindingTest.java:286-291`), so the risk is a
   non-YAML caller or a schema relaxation. Remedy: one test per gap with its planted break as red-first (R1 of 11-12):
   `templates/../x` at `MissionValuesTest`; `hasMessageContaining("from")`/`("to")` in `RelationshipTest`; mint at
   `MAX_TIME` and refuse `MAX_TIME + 1`; `BodyPolicy(MAX_VALUE - 8)` accepted and `- 7` refused; a `MissionJsonTest`
   copying `ProfileJsonTest:103`. Size S (one PR, ~5 small tests).
2. **MEDIUM — All eight timing items are open, though the repository now has the reference shape (#593).**
   Evidence: T2 table. The vacuous ones (`RegistryLockOrderTest:110`, `GitSourceFetcherTest:607`,
   `FileSystemPackSourcesTest:192-229`) can pass with the guarded rule broken on a loaded runner. Remedy: use
   `awaitQueued`/`RegistryWriteLock.hasQueuedThread` at `:110`; in the fetcher test hold the two admitted fetches on a
   latch until a third caller is observed parked on the semaphore (or assert `inFlight <= 2` at every entry); count the
   writer's moves and require > 0; add a `charter.requireNonRoot`-style switch. Size S–M.
3. **MEDIUM — `profiles` is 3 mutants above its floor (+1.1 points); `infra.archive` and `pack-validation` are at +3.**
   Evidence: run 43794141037 (`:profiles profiles: 261 mutants, score 91.1 %, floor 90 %`). Profiles tripled its
   mutants (85 → 261) since the floor was raised. Any small refactor there can turn the weekly run red with no test
   weakened. Remedy: triage the 23 undetected `:profiles` mutants (kill or mark equivalent) before more profiles code
   lands; do not lower the floor without an [ADR-0062](/adr/0062-nightly-mutation-floor/) amendment. Size S.
4. **MEDIUM — Main has not been mutation-tested for 262 commits, and two modules have floors that no run has ever
   measured.** Evidence: the only run of `mutation-testing.yml` is 43794141037 at `97985727`; `git log 97985727..HEAD`
   = 262 commits, among them `aa1ae67b`, which adds `:routing` (floor 90, `service/routing/build.gradle.kts:13-18`) and
   `:routing-edge` (floor 85, `:31-43`). ADR-0062 records every other module's entry by amendment (`0062:248-353`); routing
   has none, and 90 was not derived by `floor5(score − 3)` from a measured run. Remedy: a `workflow_dispatch` now (before
   the 11-23 schedule) as the 11-13 research did for #461; an ADR-0062 amendment for the two routing modules with
   their measured first run. Size S.
5. **MEDIUM — The type-only refusal pattern that masked gap 1 is spreading in new code.** Evidence:
   `SubstepTest.java:58-160` (6 methods, added in WP07 after 11-12), `MissionValuesTest.java:32-44`,
   `MissionTypeTest.java:254-306`; missions has 15 of its 52 methods type-only while `ExpectedArtifact`/`Substep` apply
   several rulesets per input. Remedy: assert the rule text (`hasMessageContaining(...)`) where a value class has more
   than one rule, as `MissionValuesTest:47-51` already does. Size S.
6. **LOW — The new slowest PIT test is not tracked.** `ExampleVocabularyReadTest` takes 45.7 s under PIT (log of job
   133058994356) by reading 138 terms one full read each (`:79-86`). Remedy: add it to #531 (one store, cached read per
   term, same assertions). Size S.
7. **LOW — Wiring tests still assert configuration existence** (`MissionsEdgeConfigurationsTest:34-37`,
   `MissionsEdgeWiringTest:35`, `PackSourceWiringTest:83,126`) and `KindBindingsTest:75-76` still pins `hasSize(15)`
   (friction for a sixteenth kind). Remedy as 11-12. Size S.
8. **LOW — The 11-12 scan cannot be reproduced from this checkout.** The available asset is pytest-only (`scanned 0
   files`), so the 142/4,652 baseline has no repeatable successor. Remedy: keep a Java-capable scan (open-packs content,
   not filed here per the issue rules) or record the exact asset commit used. Size S.
9. **LOW — The registration poll is now 6 copies in 5 files, against 4, with no shared helper**, one of which returns silently at its
   deadline (`ActivationListingFaultTest:214-225`). Remedy: one `Await.until(condition, timeout, failureText)` in a
   deployable test-support class (shape of #593: named failure). Size S.
10. **LOW — #461 is still open after the green run it waits for** (run 43794141037, 11-16). Per the issue rules it
    should be closed with that run as evidence and #494 ticked. Size S.

Commands used: `git log --grep '#593'`, `git show aa6babfc`, `grep -rn Thread.sleep service --include=*.java`,
`python3 -I scripts/analysis/tests/jscan.py . build/analysis/tests`, the jobs and logs of CI run 43794141037,
`issue_read` #461 #530 #531 #593.
