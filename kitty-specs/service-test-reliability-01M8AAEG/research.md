# Research: Service tests wait for states; test hooks out of main

## Pre-spec squad (2026-11-25)

Lenses: architect-alphonso, reviewer-renata, doctrine-daphne, each profile-loaded and read-only. Dispositions follow the
findings-disposition contract of procedure `adversarial-squad-deployment`.

| # | Lens | Severity | Finding | Disposition | Where |
|---|------|----------|---------|-------------|-------|
| 1 | architect | MEDIUM | `WorkMeter` is a test instrument in main (`measure()` has no main caller) | changed: kept on purpose by operator Q5=a, recorded as an ADR-0075 AA5 amendment | spec DM-01M8AAEZ; FR-009 |
| 2 | architect | MEDIUM | the meter sits in `packs.domain`, names infra types; `InForceResolution` "pure" claim false | accepted | FR-005a, IC-01 |
| 3 | architect | MEDIUM | `CompositionFactsCache.loads/hits` still test hooks | accepted (Q6=a) | FR-005c, IC-01 |
| 4 | architect | MEDIUM | `PackMutabilityPolicy` test-only constructors untouched | deferred_with_rationale: excluded by operator; #636 slice 5 owns them | spec C-003 |
| 5 | architect | MEDIUM | `RegistrationJobs.retained`, `ClosureQueries.allConfigured`, `RegistryWriteLock.hasQueuedThread` test-only | changed: `allConfigured` leaves main; `retained` and `hasQueuedThread` stay with reasons (`retained` by operator ruling 2026-11-26) | FR-008, IC-03 |
| 6 | architect | LOW | canonical-constructor wording; seam javadoc; widened `ATOMIC`/`MOVE_ASIDE` | accepted | FR-007a, IC-03 |
| 7 | architect | LOW | no ArchUnit rule stops static counters returning | accepted | FR-005d |
| 8 | architect | LOW | meter undercounts work moved to another thread | accepted | FR-005b |
| 9 | architect | LOW | `CommitStores` in published testFixtures | accepted | FR-007a |
| 10 | architect | NIT | `RegistrationWiring` javadoc says "and test seam" | accepted | IC-03 |
| 11 | reviewer | MEDIUM | `RegistrationWiringStagingTest` cannot fail (stale entry not a job id) | accepted | FR-002a |
| 12 | reviewer | MEDIUM | `GitSourceFetcherTest` barrier met by threads in the DNS lookup | accepted | FR-002b |
| 13 | reviewer | MEDIUM | fillers withdraw queued jobs; `attempt()` guards lost red-first coverage | accepted: test renamed; guards recorded as residual with reason | FR-004 |
| 14 | reviewer | LOW | `workerNumbers()` depends on thread names | accepted: kept, plus `mostInside` asserted before release | IC-02 |
| 15 | reviewer | LOW | ender failure swallowed in `ConsistentReadTest` | accepted | FR-002c |
| 16 | reviewer | LOW | `LOOKUP_PARSE` has no positive control | accepted | FR-005b |
| 17 | doctrine | MEDIUM | ADR-0075 AA5 and the unification note describe the statics | accepted | FR-009 |
| 18 | doctrine | MEDIUM | scenarios README and feature name the removed seam | accepted | FR-009 |
| 19 | doctrine | LOW | fixtures are a second positional construction path | changed: they build only the canonical record; javadoc names what each omits | C-002 |
| 20 | doctrine | LOW | changelog `related:` and wording | accepted | FR-009 |
| 21 | doctrine | LOW | health plan rows 8 and 9 status | accepted | FR-009 |

## Plan-point squad (2026-11-25)

Lenses: architect-alphonso, reviewer-renata, randy-reducer, each profile-loaded and read-only.

| # | Lens | Severity | Finding | Disposition | Where |
|---|------|----------|---------|-------------|-------|
| 22 | reviewer | HIGH | FR-003 fix cannot guarantee nothing written: a freed worker may commit a waiting job before `stop` reaches it | accepted: hold the room again before `close()` | FR-003, IC-02 |
| 23 | reviewer | HIGH | FR-002a mutant NPEs; no-root staging is a relative path, not tmpdir | accepted: entry at `<tmpdir>/staging/<job id>`, null-safe mutant, configured-root positive control | FR-002a, IC-02 |
| 24 | reviewer | MEDIUM | counting resolver answers does not place the fetch threads past the lookup | accepted: `acquireSlot` on the stack of the four waiting threads | FR-002b, IC-02 |
| 25 | reviewer | MEDIUM | FR-004 reachable without a seam through the job's monitor | accepted: new test; `ended()` recorded as equivalent mutant | FR-004, IC-02 |
| 26 | reviewer | LOW | minimal positive control per budgeted unit | accepted | FR-005b |
| 27 | reviewer | LOW | spec says "nothing written" is a close guarantee | accepted: reworded | spec Edge Cases |
| 28 | architect | MEDIUM | `OfferingReadServiceCacheWiringTest` reads `loads()`/`hits()` | accepted | FR-005c, IC-01 |
| 29 | architect | MEDIUM | `CompositionFactsCache.clear()`/`size()` test-only | accepted: leave main | FR-005c, IC-01 |
| 30 | architect | MEDIUM | purity rule misses infra.storage, where four counters were | accepted: rule over all main classes with a named allowlist | FR-005d |
| 31 | architect | LOW | `WorkMeter` has no kernel unit test | accepted | IC-01 |
| 32 | architect | LOW | pre-filled `EnumMap` cannot span three unit enums | accepted: lazy map | IC-01 |
| 33 | architect | LOW | new package needs `package-info.java` `@NullMarked` | accepted | IC-01 |
| 34 | reducer | MEDIUM | `CompositionFacts.of`/`ClosureViews.of` ladders: unused arities | accepted: delete arities with no caller | IC-03 |
| 35 | reducer | LOW | `RemovalConcurrencyTest` hand-rolled wait loop | accepted | IC-02 |
| 36 | reducer | LOW | private `await` wrappers | accepted: inline to `Await.until` | IC-02 |
| 37 | reducer | LOW | "ended" predicate written three times | accepted for the two reservation-based ones (one fixture helper); the HTTP one stays | IC-02 |
| 38 | reducer | LOW | registry-hold latch duplicated across modules | deferred_with_rationale: the two tests sit in `activations` and `deployable`; a shared fixture would need a new cross-module dependency for two call sites | - |
| 39 | reducer | LOW | `ReadCounters.Step` duplicates `Runnable` | accepted | IC-01 |

## Full-suite red (2026-11-25, 7389 tests, 1 failed)

`RegistrationJobsTest.closingTheJobsEndsWhatWaitsRefusedBusyAndAuditsEachOnce`: 4 arrivals at the room check, 2
expected. `close()` stops jobs in snapshot order; a stopped filler frees its worker, which takes a waiting job and marks
it running before `close()` ends it busy. The test asserted an order `close()` does not guarantee. Requirement FR-003.

## Decisions

- **Meter location**: Decision: mechanism in `kernel.metering` (`WorkMeter`, `WorkUnit`), units in their owning
  packages. Rationale: `packs.domain` may reach only `kernel`, `java` and `jspecify`; the kernel names no pack type.
  Alternatives: a packs-wide non-domain package (unreachable from the domain); a production metrics consumer (operator
  chose to keep the meter for tests, Q5=a).
- **Holding a job pending**: Decision: keep both workers busy on held fillers. Rationale: no main seam; the queue holds
  the job as in production. Alternative: an injected executor (rejected: a collaborator only tests vary).
- **Kept sleeps** (two, NFR-001): the dripping server (`HttpsGuideFetcherLoopbackTest`), and the pause helper of
  `MixedReadConcurrencyTest`, which staggers its threads and slows one swap past the 600 ms the test reports. Each is a
  stimulus, not a wait. `RegistrationConcurrencyTest` staggers through `Await.within`, a bounded pause, not a sleep.

## Mutation notes (red-first evidence)

Each mutant was applied by hand, the named tests were run, the mutant was reverted from a copy, and `git status` was
checked clean. No PIT was run (C-004).

| WP | Mutant | Result | Revert |
|----|--------|--------|--------|
| WP01 | lookup count point dropped (FilesystemPackStore.locate) | WholePackStoreTest.aLookupByIdIsCounted red | reverted |
| WP01 | cache HIT count point dropped (CompositionFactsCache) | EffectiveReadOperationBudgetTest.aWarmRead... red; OfferingReadServiceCacheWiringTest red | reverted |
| WP01 | static non-final field added to PackFileSet (main) | HexagonalArchitectureTest.noMainClassKeepsAStaticCounter red | reverted |
| WP01 | static adder and non-final static in archfixtures | theStaticCounterRuleFails... asserts the rule trips (permanent positive control) | n/a |
| retrofit B | doubled RESOLUTION/RESOLUTION_VISIT/WHOLE_READ/STORE_RUN/TREE_WALK; extra walk in WholePackStore | OneReadTest, OneReadRegistrationTest, PackReadingTest, ReadoutFactsTest, WholePackStoreReadTest, EffectiveReadOperationBudgetTest red | reverted |
| retrofit C | RegistrationJobs pool of three workers | six RegistrationJobsTest cases red | reverted |
| WP02 | no-root wiring sweeps <tmpdir>/staging (null-safe) | RegistrationWiringStagingTest.withoutAConfigRoot... red | reverted |
| WP02 | git fetch slot limit + 1 | GitSourceFetcherTest.atMostTheConfiguredNumberOfFetchesRunAtOnce red | reverted |
| WP02 | awaitIdle answers at once, never waits | ConsistentReadTest.awaitIdle... red (ender failure surfaced) | reverted |
| WP02 | close() stops no job | RegistrationJobsTest.closingTheJobs... red | reverted |
| WP02 | both ended-job guards gone (ended(), markRunning phase) | RegistrationJobsTest.aWorkerThatTakesAJobEndedMeanwhile... red | reverted |
| WP03 | removals only (allConfigured, CommitStores visibility move, unused fixture arities); allConfigured callers switched to the identical snapshot(Optional.empty()) | compile proof; lane-c suites 7388/7389 green, the one red is the first-pass closingTheJobs defect fixed in WP02 (lane-b) | n/a |

## Decisions taken during implementation

- retained(): operator ruling 2026-11-26 (AskUserQuestion in session): keep, javadoc records why (the one observable of "no job was made"); retention test tightened to isEqualTo(MAX_RETAINED). FR-008 amended accordingly.
- FR-004: ended() and markRunning's phase check mask each other; each alone is an equivalent mutant. The new test (aWorkerThatTakesAJobEndedMeanwhileDropsItAndWritesNothing) kills the mutant that lets an ended job reach its work (both guards gone: the stager runs, arrivals rise).
- Not done (outside the operator-ruled F3/F12 scope; tidy-ups, not defects): main javadoc that names a test as a member's caller remains on WholePackCensus (census with bindings, retired companion), the WholePackStore 4-arg constructor, PackRemediation's move and delete seams, PolicyYaml.write, PackFileKind's table from text and FileActivationRepository; PackMutabilityPolicy is excluded (#636 slice 5). PackFileSet and FilesystemPackStore, which this Mission changes, were reworded (squad #47).
- CLI unsigned commits: operator ruling 2026-11-26: push as made; land one signed squash with the Co-Authored-By trailer.
- WP01 review, MEDIUM: `OfferingReadServiceCacheWiringTest` no longer starts cold, because the Spring cache is a shared
  singleton and a fresh one needs a new context. It proves the wiring (the second read is answered by the cache alone);
  the cold-start check lives in `EffectiveReadOperationBudgetTest`, whose fixture rebuilds the service over an empty
  cache. Recorded, not filed.
- FR-005d gained a fourth named exemption, `PackSchemas.bundled` (a lazily built, immutable schema value retried after
  a failed build), and the test fixtures; `GlobalAddressResolverProvider` is exempt by field, not whole.
- WP05 writes its planning artifacts in the coordination worktree (the Mission branch, where they live), not the root
  checkout the CLI names for the planning lane: finalize moved the root checkout to the merge target, which has no
  `kitty-specs/`, and a second copy there would conflict at consolidation.
- Work-package reviews ran in this session as independent reviewer subagents (operator ruling 2026-11-25): WP01, WP02
  and WP03 APPROVE with findings folded; WP04 REJECT, folded, APPROVE on re-review.

## Pre-merge squad (2026-11-26)

Lenses: reviewer-renata and architect-alphonso on the aggregate diff (`local/659-integration` against `origin/main`),
read-only. Both APPROVE. The integration snapshot they read predates some per-WP folds; each finding's disposition:

| # | Lens | Severity | Finding | Disposition | Where |
|---|------|----------|---------|-------------|-------|
| 40 | architect | LOW | AA5 note says Resolved while the amendment is Proposed | accepted | ADR-0075, unification note (lane-d) |
| 41 | architect | LOW | "three named fields" in the rule javadoc and ADR | accepted (ADR fixed in the WP04 fold; javadoc now says four) | lane-a, lane-d |
| 42 | architect | LOW | ADR-0006 module map omits kernel.metering | accepted | ADR-0006 (lane-d) |
| 43 | architect | LOW | nothing enforces "only tests call measure" | accepted: ArchUnit rule `onlyTestsMeasure` with a fixture | lane-a |
| 44 | architect | NIT | ADR omits structured-scope forks | accepted (WP04 fold) | ADR-0075 |
| 45 | architect | NIT | `MOVE_ASIDE` widened to package-private | changed: kept; the test-side `CommitStores` needs it, no wider | - |
| 46 | reviewer | LOW | spec, plan and tasks still say `retained()` leaves main | accepted: SC-002, plan, tasks and row 5 amended | spec, plan, tasks |
| 47 | reviewer | LOW | "the seam a test uses" javadoc remains; `PackFileSet.listing` is called from main | accepted for the files this Mission changes (PackFileSet, FilesystemPackStore); deferred_with_rationale for WholePackCensus, WholePackStore, PackRemediation, PolicyYaml, FileActivationRepository and PackMutabilityPolicy: outside the operator-ruled F3/F12 scope, tidy-ups not defects (charter Rule 1: recorded, not filed) | lane-a; SC-002 |
| 48 | reviewer | LOW | four exempt fields vs FR-005d's two | accepted (FR-005d amended) | spec |
| 49 | reviewer | LOW | the wiring test no longer starts cold | changed: recorded; the cold start is in the budget test; FR-005c amended | spec, research |
| 50 | reviewer | LOW | two OneReadTest zero assertions lack a same-test positive control | accepted | lane-a OneReadTest |

## Gates on the integration tree (2026-11-26)

`local/659-integration` merges lanes a to d over `f6f21e4c0` (runs 1 to 4), then the Mission branch and `main` (runs 5 and 6); every run is `env -u _JAVA_OPTIONS ./gradlew verifyAll
--continue --max-workers=2`. No red was retried; each is attributed.

| Run | Result | Attribution | Fix |
|-----|--------|-------------|-----|
| 1 | killed | the container's memory limit killed the Gradle daemon (dmesg); not a test result | rerun with two workers |
| 2 | red | `:profiles-edge:compileTestJava`: `PacksProfileSourceTest` called the short `PackCompositionFacts` constructor WP03 moved out of main; the first pass rewrote callers in packs, activations and deployable only | lane-c: `CompositionFacts.of`; a sweep of every other module found no other caller |
| 3 | red | `:packs` and `:deployable` `spotlessJavaCheck`: the squad folds in `OneReadTest` and `HexagonalArchitectureTest` were not formatted; every test passed | lane-a: `spotlessApply` |
| 4 | green | 8 min 55 s; packs 7391, deployable 2438 and activations 1102 tests, none failing | - |
| 5 | green | the final tree: the lanes, the Mission artifacts and `main` at `56b7641d7` merged; 15 min 45 s | - |
| 6 | green | the landed tree (`002bf92b1`: the same, with `main` at `53d3ba8d6`, which brought #648 and #658 into packs); 15 min 6 s | - |

Docs: `check_docs.py all`, markdownlint-cli2 0.18.1 (394 files, 0 errors) and cspell 9.2.0 on the changed pages are
green. The MD038 hits a direct lint of `contracts/charter/scenarios/README.md` shows predate this Mission (seven on
`main`) and lie outside the gate's `docs/**` globs.

NFR-003: `:packs:test --rerun --max-workers=2`, wall time on this runner, alternating trees: `main` (`f6f21e4c0`) 377 s
and 359 s (mean 368 s); this Mission 362 s and 379 s (mean 370.5 s). The difference, +0.7 %, is inside the 10 % bound
and within the spread of two runs of the same tree.
