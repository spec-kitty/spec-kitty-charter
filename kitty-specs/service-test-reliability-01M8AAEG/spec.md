# Mission Specification: Service tests wait for states; test hooks out of main

**Mission Branch**: `kitty/mission-service-test-reliability-01M8AAEG` (merges into `test/659-state-waits-and-main-hooks`, which lands on `main` by pull request)
**Created**: 2026-11-25
**Status**: Draft
**Input**: Operator dispatch of 2026-11-25 (issue #659): make the service tests reliable; replace sleeps with state waits; take the static counters, `JobGate` and the test-only constructors out of main code. Evidence: actions 8 and 9 of `docs/plans/codebase-health-2026-11-25.md` (ADR-0075 AA5).

## Intent Summary (confirmed by the operator, 2026-11-25)

- **Actor**: a contributor or the CI runner running the service test suites (`:packs`, `:activations`, `:deployable`, `:pack-doctor`), including on a loaded runner and with tests in parallel.
- **Trigger**: health summary 2026-11-25 actions 8 and 9: tests that pass vacuously when the runner is slow, and global state (JVM-wide static counters, a production-wired test gate) shared across tests.
- **Outcome**: every changed test waits for the state it checks, never for a span of time, and still fails when the behaviour it checks breaks. Main code holds no static read counters, no `JobGate`, and no constructor or method only tests call, for the F3 list and the F12 items the operator brought in.
- **Invariants**: production behaviour is unchanged; no contract change; tests build only the canonical record shapes; no PIT; a sleep that is itself the stimulus under test stays, with its reason recorded.
- **Retrofit**: the code was first written as an Op on `test/659-state-waits-and-main-hooks` (commits `a59eef1d1`, `95989552f`, `91d706a24`, `6da6cface`) before this Mission existed (the operator's direction to run a Mission reached the session after those commits). The Mission keeps that code, specifies it, and brings the pre-spec squad's findings and the full-suite red under it as requirements.

## Operator decisions

- **DM-01M8AAEZFK3YV64F8WMK3AR12C (Q5, the work meter)**: keep `WorkMeter`, record an ADR-0075 AA5 amendment (Proposed), move it out of `packs.domain`, and correct its javadoc and `InForceResolution`'s "pure" claim.
- **DM-01M8AAF1ZZB8KXQKE4DDTNWTP5 (Q6, F12 methods)**: in. `CompositionFactsCache.loads/hits` become meter units; `RegistrationJobs.retained` and `ClosureQueries.allConfigured` leave main; `RegistryWriteLock.hasQueuedThread` and `PackCommitStore.SwapPhases` stay, each with a recorded reason.
- **Scope rulings**: one pull request (A+B+C+D); D takes the full F3 list; `PackMutabilityPolicy`'s test-only constructors are excluded (#636 slice 5 owns them); `InForceResolution`'s counters are included, kept minimal so they rebase onto the #637 `PackGraph` walk move.
- **Full-suite red, accepted as a requirement**: `RegistrationJobsTest.closingTheJobsEndsWhatWaitsRefusedBusyAndAuditsEachOnce` asserts what `close()` does not guarantee once workers are held by fillers; the test asserts only what `close()` guarantees, with no main-code seam.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A test waits for a state, not a span of time (Priority: P1)

A contributor runs the service tests on a loaded CI runner. A test that waits does so until the state it checks holds, and a test that proves something does not happen settles that with a state barrier, so a slow runner neither hides a broken behaviour nor fails a correct one.

**Why this priority**: vacuous passes are the risk action 8 names; a test that cannot fail gives false confidence.

**Independent Test**: break each behaviour a changed test checks (the change's mutation note) and see the test go red; restore it and see it green.

**Acceptance Scenarios**:

1. **Given** a test that waited by sleeping, **When** it runs, **Then** it waits through the shared `Await` helper for the state it checks, and fails after `Await.LIMIT` naming the state that never came.
2. **Given** a test that proved an absence by watching a window, **When** it runs, **Then** it first waits for the state that settles the question (the job's end, the synchronous startup sweep, the writer parked on the lock, every fetch past its lookup), and a mutant that produces the absent thing turns it red.
3. **Given** a sleep that is the stimulus under test (a dripping server, stress-test jitter, a swap whose slowness is asserted), **When** the change is reviewed, **Then** the sleep stays and its reason is recorded.

---

### User Story 2 - Tests do not share state across the JVM (Priority: P1)

Two tests that count a read's work run at once in one JVM. Each sees only the work of its own call.

**Why this priority**: global counters are the risk action 9 names (ADR-0075 AA5).

**Independent Test**: the read-budget tests measure through a meter bound around one call; doubling a count point or adding an extra walk turns them red.

**Acceptance Scenarios**:

1. **Given** the six former static counters, **When** main code is searched, **Then** no static mutable counter remains and each count point counts on the meter bound around the call.
2. **Given** a budgeted read, **When** it runs measured, **Then** each budgeted unit is counted at least once, so a count point that stopped counting fails the budget.

---

### User Story 3 - Main code holds no test-only seams (Priority: P2)

A maintainer reads a main-code class and finds only what production uses: no job gate wired as a bean, no constructor only tests call, no method only tests call, except the seams kept on purpose with a recorded reason.

**Why this priority**: test seams in main grow the surface, mislead readers and invite global state.

**Independent Test**: a search of main for the removed names finds nothing; every main constructor has a main caller or is the one canonical constructor taking every collaborator.

**Acceptance Scenarios**:

1. **Given** `RegistrationJobs`, **When** a test needs a job held running or pending, **Then** it holds it at the jobs' storage-room check (`HeldRoom`) or keeps both workers busy, as production's queue does.
2. **Given** the F3 classes, **When** a test needs a short form, **Then** it builds through a test fixture over the canonical constructor.

### Edge Cases

- A job ended while it waits in the executor queue is withdrawn from the queue, not woken: tests assert what `close()` and the deadline guarantee (each job ends once, a job not yet committing ends refused busy, a commit already begun finishes), not the order in which `close()` stops jobs. "Nothing written" holds only while the room holds the jobs that get a worker, which the close test arranges.
- Read work moved onto another thread would escape the meter: each budgeted unit is asserted to be counted at least once.
- A failure raised on a helper thread (the ender of `ConsistentReadTest`) is rethrown on the test thread.
- A thread parked for any reason other than the barrier the test means (the git fetcher's DNS lookup) must not satisfy the barrier.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | One state wait | As a contributor, I want every wait in the service tests to go through one helper that waits for a state and fails naming it after a fixed limit, so that no test waits for a span of time. | High | Done (retrofit) | [build] | no - the search for sleeps in the changed tests is paired with each test's mutation note |
| FR-002 | Absence checks settle on a state | As a contributor, I want each test that proves something does not happen to wait first for the state that settles it, so that a slow runner cannot hide the thing. | High | Open | [build] | no - each carries a mutant that produces the absent thing and turns it red |
| FR-002a | Staging sweep test can fail | As a contributor, I want the sweep test's stale entry to be one the sweeper would remove (`<tmpdir>/staging/<job id>`, a directory older than the cutoff), with a same-fixture positive control where a configured root sweeping that directory removes it. | High | Open | [build] | no - mutant: the no-root branch sweeps `<tmpdir>/staging` with a null-safe pack root (not an NPE) |
| FR-002b | Fetch-limit barrier past the lookup | As a contributor, I want the git fetch-limit test to release only once exactly two fetches are inside the remote and the other four are parked in the fetcher's slot wait (`acquireSlot` on their stack), so that a broken limit raises the highest count. | High | Open | [build] | no - mutant: a limit of three |
| FR-002c | Helper-thread failures surface | As a contributor, I want a failure on `ConsistentReadTest`'s ender thread rethrown on the test thread. | Medium | Open | [build] | no - mutant: `awaitIdle` that never sees the end |
| FR-003 | Close asserts its guarantee | As a contributor, I want `closingTheJobs...` to hold the room again before `close()`, so a waiting job that gets a freed worker is held running before it stages; the test asserts each waiting job ends refused busy, is audited once, writes nothing and leaves no staging, and nothing about the order `close()` stops jobs in. | High | Open | [build] | no - mutant: `close()` that lets a waiting job commit |
| FR-004 | Queued-end guard coverage | As a contributor, I want the deadline test renamed for what it shows (a queued job withdrawn), and a test that reaches a worker holding an ended job with no main seam: the test holds the job's monitor, frees a worker, waits until it is BLOCKED on that monitor, ends the job, then releases it; the worker writes nothing and the job is audited once. The `ended()` guard, masked by `markRunning`, is recorded as an equivalent mutant. | Medium | Open | [build] | no - mutant: drop the `markRunning` phase check |
| FR-005 | Meter instead of static counters | As a contributor, I want the six static counters replaced by a meter bound around one call, so that two measured calls never see each other's work. | High | Done (retrofit) | [build] | no - mutation notes: doubled counts and an extra walk turn the budget tests red |
| FR-005a | Meter out of the domain | As a maintainer, I want the meter moved out of `packs.domain`, its javadoc free of infrastructure names, and `InForceResolution`'s "pure" claim corrected. | Medium | Open | [build] | no - ArchUnit and a search for the old package |
| FR-005b | Every budgeted unit is counted | As a contributor, I want each budget test to assert its units are counted at least once (lookups included), so that a count point that stopped counting fails. | Medium | Open | [build] | no - positive control on the same fixture |
| FR-005c | Cache counters on the meter | As a maintainer, I want `CompositionFactsCache.loads/hits` to become meter units and their getters, `clear()` and `size()` (only tests call them) to leave main; `OfferingReadServiceCacheWiringTest` and the budget test read the meter and build a fresh cache for a cold start. | Medium | Open | [build] | no - the budget tests read them from the meter |
| FR-005d | No static counters return to main | As a maintainer, I want an ArchUnit rule over all main classes that refuses static non-final fields and static `Atomic*`/`LongAdder` fields, allowing by name `PackCommitStore.COUNTER` (temporary names) and `GlobalAddressResolverProvider` (JDK SPI), with a bad fixture that trips it. | Low | Open | [build] | no - the bad fixture turns the rule red |
| FR-006 | No job gate in main | As a maintainer, I want `RegistrationJobs.JobGate` and its bean gone, tests holding a job at the storage-room check or by busy workers. | High | Done (retrofit) | [build] | no - mutation note: a third worker turns six tests red |
| FR-007 | No test-only constructors | As a maintainer, I want the F3 classes (`FilesystemPackStore`, `FileSystemPackInspector`, `PackCommitStore`, `ClosureView`, `PackCompositionFacts`) to keep the constructors main calls plus one canonical constructor, the short forms in test fixtures. | High | Done (retrofit) | [build] | no - main compiles without the removed overloads |
| FR-007a | Constructor and seam javadoc tells the truth | As a maintainer, I want javadoc that still calls a constructor or type "the seam a test uses" corrected, `CommitStores` moved out of the published test fixtures into `:packs` tests, and the widened `ATOMIC`/`MOVE_ASIDE` kept no wider than the fixtures need. | Low | Open | [build] | no - search for the phrases |
| FR-008 | F12 methods out of main | As a maintainer, I want `RegistrationJobs.retained` and `ClosureQueries.allConfigured` out of main, and `RegistryWriteLock.hasQueuedThread` and `PackCommitStore.SwapPhases` kept with a recorded reason. | Medium | Open | [build] | no - search plus the recorded reasons |
| FR-009 | Documentation matches the code | As a reader, I want ADR-0075 AA5 amended (Proposed), the pack-code-unification AA5 note marked resolved, the scenarios README and `whole-pack-registration.feature` naming the held storage room, the health plan rows 8 and 9 marked, and the changelog fragment linking ADR-0075 and not over-claiming. | Medium | Open | [build] | no - docs checks and a search for `JobGate` and the counters outside history |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | No wait for a span | 0 `Thread.sleep` calls in the changed service tests apart from the recorded stimuli (3 kept: the dripping server, two stress-test jitters). | Reliability | High | Open |
| NFR-002 | Waits are bounded | Every state wait fails within 30 s (`Await.LIMIT`) when its state never comes. | Reliability | High | Done (retrofit) |
| NFR-003 | Suite time does not grow | `:packs:test` wall time stays within 10% of `main`'s on the same runner. | Performance | Medium | Open |
| NFR-004 | Suite is green | `./gradlew verifyAll` on Java 25 passes with 0 failed tests, every red attributed, no retry-to-green. | Reliability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Production unchanged | No production behaviour and no contract change; the meter counts nothing when unbound. | Technical | High | Open |
| C-002 | Canonical shapes only | Tests build only the canonical records; fixtures build through the canonical constructor. | Technical | High | Open |
| C-003 | Exclusions | `PackMutabilityPolicy`'s test-only constructors are out (#636 slice 5). Edits to `InForceResolution`, `PackCompositionFacts` and `ClosureView` stay minimal and coordinated with the #637 runner through the dispatcher. | Business | High | Open |
| C-004 | No PIT | No `pitest` or `checkMutationFloors` run; red-first is shown by hand-made mutants, each reverted. | Technical | High | Open |
| C-005 | Commits | Signed commits, no Co-Authored-By trailers, no model names. | Regulatory | High | Open |
| C-006 | Gates | `verifyAll` on Java 25, `check_docs.py all`, markdownlint-cli2 and cspell pass; a changelog fragment is added. | Technical | High | Open |

### Key Entities

- **WorkMeter**: a meter bound around one measured call; counts units of read work done on that thread inside the call; counts nothing when unbound.
- **HeldRoom**: a test fixture over the jobs' storage room that holds a job at its run-time check.
- **Await**: the one test wait: until a state holds, or a bounded window for stress tests.

## Success Criteria *(mandatory)*

- **SC-001**: Every changed test has a mutation note naming the mutant that turned it red and confirming the revert. [build] (no-op passable: no)
- **SC-002**: A search of main code finds no static mutable counter, no `JobGate`, no `retained()`, no `allConfigured()`, and no javadoc calling a member a test seam except the two seams kept with reasons. [build] (no-op passable: no)
- **SC-003**: `verifyAll` passes on Java 25 with the full suite green, and the docs checks pass. [ratchet] (no-op passable: no, paired with SC-001)
- **SC-004**: Issue #659 is closed by the pull request, and the health plan rows 8 and 9 are marked. [build] (no-op passable: no)

## Assumptions

- The kept sleeps are stimuli, not waits, as the reviewer lens conceded.
- Thread-state barriers (TIMED_WAITING on a lock) are acceptable where the parked state is the only waiting point the thread reaches.
