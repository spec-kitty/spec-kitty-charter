# Tasks: Service tests wait for states; test hooks out of main

Mission: `service-test-reliability-01M8AAEG`. Spec: `spec.md`. Plan: `plan.md`. Research and dispositions: `research.md`.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Create kernel.metering | WP01 | [P] |
| T002 | Kernel WorkMeterTest | WP01 | [P] |
| T003 | Units with their owners | WP01 | [P] |
| T004 | CompositionFactsCache | WP01 | [P] |
| T005 | Budget positive controls | WP01 | [P] |
| T006 | ArchUnit rule over all main classes (ServiceClasses.main()) | WP01 | [P] |
| T007 | Red-first | WP01 | [P] |
| T008 | RegistrationWiringStagingTest | WP02 | [P] |
| T009 | GitSourceFetcherTest | WP02 | [P] |
| T010 | ConsistentReadTest | WP02 | [P] |
| T011 | RegistrationJobsTest.closingTheJobs... | WP02 | [P] |
| T012 | FR-004 | WP02 | [P] |
| T013 | RegistrationJobs.retained() leaves main | WP02 | [P] |
| T014 | Run  | WP02 | [P] |
| T015 | ClosureQueries.allConfigured() leaves main; its five test callers read | WP03 | [P] |
| T016 | RegistryWriteLock.hasQueuedThread and PackCommitStore.SwapPhases/moveA | WP03 | [P] |
| T017 | FileSystemPackInspector and PackCommitStore constructor javadoc stops  | WP03 | [P] |
| T018 | CommitStores moves from packs testFixtures to packs src/test (only pac | WP03 | [P] |
| T019 | Red-first | WP03 | [P] |
| T020 | ADR-0075 | WP04 |  |
| T021 | pack-code-unification.md AA5 bullet marked resolved with a pointer; he | WP04 |  |
| T022 | Scenarios README "The RegistrationJobs seam" and whole-pack-registrati | WP04 |  |
| T023 | Changelog fragment | WP04 |  |
| T024 | Mutation notes in research.md | WP05 |  |
| T025 | git fetch --unshallow --tags origin; unset _JAVA_OPTIONS only; ./gradl | WP05 |  |
| T026 | quickstart.md | WP05 |  |

## WP01 - Meter in the kernel; units with their owners; no static counters in main

**Prompt**: `tasks/WP01-meter-in-the-kernel.md`  
**Requirements**: FR-005, FR-005a, FR-005b, FR-005c, FR-005d, FR-007a, SC-002  
**Depends on**: none

Subtasks:

T001 Create kernel.metering: WorkMeter (ScopedValue binding, lazily filled map, measure, Counts.of returns 0 for an absent unit), marker WorkUnit, package-info @NullMarked; javadoc names no pack type and says counting is on the binding thread only (WP01)
T002 Kernel WorkMeterTest: unbound count is a no-op; nested measure counts apart; two unit enums counted apart; measure rethrows what the call throws (WP01)
T003 Units with their owners: ReadWork (packs.domain: RESOLUTION, RESOLUTION_VISIT), StoreWork (packs.infra.storage: WHOLE_READ, LOOKUP_PARSE, TREE_WALK, STORE_RUN), CacheWork (packs.application: LOAD, HIT); switch every count point; delete packs.domain.WorkMeter; correct InForceResolution's "pure" javadoc and FilesystemPackStore/FileSystemPackInspector constructor javadoc that says "the seam a test uses" (WP01)
T004 CompositionFactsCache: loads()/hits() become CacheWork counts; remove loads(), hits(), clear(), size(); EffectiveReadFixture builds a fresh cache for a cold start; rewrite EffectiveReadOperationBudgetTest, OfferingReadServiceCacheWiringTest, CompositionFactsCacheTest on the meter (WP01)
T005 Budget positive controls: one isPositive() per budgeted unit on the same Measured (LOOKUP_PARSE on its own in WholePackStoreTest; RESOLUTION_VISIT in the budget test); ReadCounters.Step becomes Runnable (WP01)
T006 ArchUnit rule over all main classes (ServiceClasses.main()): no static non-final field, no static Atomic*/LongAdder field; allow PackCommitStore.COUNTER and GlobalAddressResolverProvider by name; a bad fixture class under the test tree trips it (WP01)
T007 Red-first: for each changed test, a hand-made mutant (doubled count, dropped count point, extra walk, a static counter in a fixture) turns it red; revert; note each in research.md via WP05 (WP01)

## WP02 - Registration and wait tests that can fail

**Prompt**: `tasks/WP02-tests-that-can-fail.md`  
**Requirements**: FR-001, FR-002, FR-002a, FR-002b, FR-002c, FR-003, FR-004, FR-006, FR-008, NFR-001, NFR-002  
**Depends on**: none

Subtasks:

T008 RegistrationWiringStagingTest: stale entry at <tmpdir>/staging/<job id>, a directory whose mtime is set after writing to 2 h ago; a same-fixture positive control where a configured root sweeping that directory removes it; mutant: the no-root branch sweeps <tmpdir>/staging with a null-safe pack root (WP02)
T009 GitSourceFetcherTest: release only when inFlight == 2 and the other four fetch threads are TIMED_WAITING with GitSourceFetcher.acquireSlot on their stack; mutant: a slot limit of three (WP02)
T010 ConsistentReadTest: capture the ender thread's failure in an AtomicReference and rethrow after join; mutant: awaitIdle that never sees the end (WP02)
T011 RegistrationJobsTest.closingTheJobs...: hold the room again before close(); assert each waiting job ends refused busy, one audit entry each, nothing written, no staging; drop the arrival count; mutant: close() that lets a waiting job commit (WP02)
T012 FR-004: rename the deadline test for a queued job withdrawn; a new test holds the job monitor (synchronized on the reservation), frees one worker, waits until it is BLOCKED on that monitor (ThreadMXBean lock identity), ends the job (clock + expireOverdue), releases; the worker writes nothing, one audit entry; mutant: drop the markRunning phase check; record the ended() guard as an equivalent mutant (WP02)
T013 RegistrationJobs.retained() leaves main: the seven tests read what they check from statuses or owned(); RemovalConcurrencyTest wait loop through Await.within; inline the private await wrappers in RegistrationJobsTest and PackRegistrationSteps to Await.until; one Await-based helper for the reservation-ended predicate shared by RegistrationJobsTest and RegistrationJobsUploadTest (WP02)
T014 Run :packs:test, :activations:test, :deployable:test; every red attributed, no retry-to-green (WP02)

## WP03 - F12 members, seam wording and fixture arities

**Prompt**: `tasks/WP03-seams-and-fixtures.md`  
**Requirements**: FR-007, FR-007a, FR-008  
**Depends on**: none

Subtasks:

T015 ClosureQueries.allConfigured() leaves main; its five test callers read the same through the public closure queries (WP03)
T016 RegistryWriteLock.hasQueuedThread and PackCommitStore.SwapPhases/moveAside stay; their javadoc records why (no state-observable alternative without them) instead of calling them test hooks; RegistrationWiring class javadoc drops "and test seam" (WP03)
T017 FileSystemPackInspector and PackCommitStore constructor javadoc stops saying "the seam a test uses"; PackCommitStore.MOVE_ASIDE no wider than needed (WP03)
T018 CommitStores moves from packs testFixtures to packs src/test (only packs tests use it); delete the CompositionFacts.of and ClosureViews.of arities that no test calls (WP03)
T019 Red-first: a mutant per moved behaviour where a test changed (allConfigured callers); compile proof for removals (WP03)

## WP04 - Documentation in line with the code

**Prompt**: `tasks/WP04-docs-in-line.md`  
**Requirements**: FR-009, SC-004  
**Depends on**: WP01, WP03

Subtasks:

T020 ADR-0075: dated amendment (Proposed, operator 2026-11-25, #659): AA5 resolved by kernel.metering.WorkMeter, kept on purpose for the read budgets; history not rewritten (WP04)
T021 pack-code-unification.md AA5 bullet marked resolved with a pointer; health plan rows 8 and 9 marked delivered by #659 (WP04)
T022 Scenarios README "The RegistrationJobs seam" and whole-pack-registration.feature description name the held storage room; step texts unchanged (WP04)
T023 Changelog fragment: related adds ADR-0075; wording names the kept seams instead of claiming "no test hooks"; check_docs.py all, markdownlint-cli2, cspell (WP04)

## WP05 - Gates and red-first evidence

**Prompt**: `tasks/WP05-gates-and-evidence.md`  
**Requirements**: NFR-003, NFR-004, SC-001, SC-003  
**Depends on**: WP01, WP02, WP03, WP04

Subtasks:

T024 Mutation notes in research.md: one row per changed test (mutant, red seen, reverted), including the first-pass retrofit commits (WP05)
T025 git fetch --unshallow --tags origin; unset _JAVA_OPTIONS only; ./gradlew verifyAll on Java 25; attribute every red; record :packs:test wall time against main (NFR-003) (WP05)
T026 quickstart.md: how to run the changed suites and reproduce one mutant; pre-merge squad dispositions appended to research.md (WP05)

## Parallelization

WP01, WP02 and WP03 own disjoint files and run in parallel. WP04 follows WP01 and WP03 (it names their final types). WP05 follows all.

## MVP

WP02 (tests that can fail) carries the HIGH findings; WP01 carries the operator decision Q5.
