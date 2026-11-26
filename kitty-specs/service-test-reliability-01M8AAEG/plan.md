# Implementation Plan: Service tests wait for states; test hooks out of main

**Branch**: `kitty/mission-service-test-reliability-01M8AAEG` (into `test/659-state-waits-and-main-hooks`) | **Date**: 2026-11-25 | **Spec**: `kitty-specs/service-test-reliability-01M8AAEG/spec.md`
**Input**: Mission specification from `kitty-specs/service-test-reliability-01M8AAEG/spec.md`

## Summary

Retrofit Mission for issue #659. The first pass (commits `a59eef1d1`, `95989552f`, `91d706a24`, `6da6cface` on
`test/659-state-waits-and-main-hooks`) replaced the six static read counters with a ScopedValue-bound `WorkMeter`,
removed `RegistrationJobs.JobGate`, moved the F3 test-only constructors into test fixtures, and replaced 20 of 23 test
sleeps with state waits through one `Await` helper. This plan finishes it: the meter moves from `packs.domain` to the
kernel with units owned by their modules (operator Q5=a), the F12 test-only methods leave main (Q6=a), the pre-spec
squad's findings and the full-suite red are fixed red-first, and the documentation is brought in line.

## Technical Context

**Language/Version**: Java 25 (toolchain fixed by `build-logic/src/main/kotlin/CharterBuild.kt`)
**Primary Dependencies**: none added; JUnit 5, AssertJ, ArchUnit and Spring Boot test as already used
**Storage**: N/A (pack trees on the file system in tests, as today)
**Testing**: `./gradlew :packs:test :activations:test :deployable:test :pack-doctor:test`, then `./gradlew verifyAll`; red-first shown by hand-made mutants, each reverted (no PIT, charter "How work lands")
**Target Platform**: Linux CI runner, Java 25
**Project Type**: single (Gradle multi-module service under `service/`)
**Performance Goals**: `:packs:test` wall time within 10% of `main`'s on the same runner (NFR-003)
**Constraints**: no production behaviour or contract change (C-001); ArchUnit rings unchanged except the new purity rule; edits to `InForceResolution`, `PackCompositionFacts` and `ClosureView` minimal (C-003; the #637 runner rebases onto this branch)
**Scale/Scope**: about 45 files across `kernel`, `packs`, `activations`, `deployable`, docs

## Charter Check

- **Canonical model** (charter "Designing during the rewrite"): no record shape changes; fixtures build through canonical constructors. Pass.
- **Test first, no fake green**: every changed test carries a mutation note (SC-001); the two vacuous tests and the order-dependent close test are fixed. Pass once WP02 lands.
- **No PIT in mission work**: mutants are hand-made and reverted; no `pitest` or `checkMutationFloors`. Pass.
- **Adversarial squad at plan point and before merge**: plan-point squad after this file; pre-merge squad on the aggregate diff, posted as "Independent adversarial review (agent)". Pass once run.
- **Gates before merge**: `verifyAll` on Java 25, `check_docs.py` (all eight), markdownlint-cli2, cspell. Planned in WP05.
- **Issues stay small**: findings below MEDIUM are folded; `Closes #659`. Pass.
- **Supply chain**: no dependency added, upgraded or removed. N/A.

## Project Structure

### Documentation (this mission)

```
kitty-specs/service-test-reliability-01M8AAEG/
|-- spec.md
|-- plan.md
|-- research.md
|-- decisions/
`-- tasks/
```

### Source Code (repository root)

```text
service/kernel/src/main/java/ai/speckitty/charter/kernel/metering/   WorkMeter, WorkUnit (new)
service/packs/src/main/java/.../packs/domain/                         ReadWork units; InForceResolution
service/packs/src/main/java/.../packs/infra/storage/                  StoreWork units; FilesystemPackStore, PackFileSet, StoreRead
service/packs/src/main/java/.../packs/application/                    CacheWork units; CompositionFactsCache, RegistrationJobs, ClosureQueries
service/packs/src/testFixtures/java/...                              Await, HeldRoom, ClosureViews, CompositionFacts
service/packs/src/test/java/...                                       ReadCounters, StoreSeams, CommitStores (moved here)
service/deployable/src/test/java/.../HexagonalArchitectureTest.java   new purity rule and its bad fixture
docs/adr/0075-...md, docs/development/notes/pack-code-unification.md, contracts/charter/scenarios/, docs/changelog/
```

**Structure Decision**: existing multi-module layout; one new kernel package `kernel.metering`, which `packs.domain` may
depend on (ArchUnit lets a domain ring reach `kernel`, `java` and `jspecify` only).

## Complexity Tracking

No charter violations to justify.

## Implementation Concern Map

### IC-01 - Meter in the kernel, units with their owners

- **Purpose**: take the meter out of `packs.domain` (Q5=a) and make every counted unit a meter unit, so no read counter, static or instance, remains a test-only getter in main.
- **Relevant requirements**: FR-005, FR-005a, FR-005b, FR-005c, FR-005d
- **Affected surfaces**: `kernel.metering.WorkMeter`, `WorkUnit` and `package-info.java` (`@NullMarked`), with a kernel unit test `WorkMeterTest` (unbound counts nothing, nested measures, two unit enums apart); the meter's map fills lazily; units `ReadWork` (packs.domain), `StoreWork` (packs.infra.storage), `CacheWork` (packs.application); `CompositionFactsCache` loses `loads()`, `hits()`, `clear()` and `size()`; `ReadCounters` (`Step` becomes `Runnable`), `OneReadTest`, `EffectiveReadOperationBudgetTest`, `EffectiveReadFixture` (a fresh cache for a cold start), `OfferingReadServiceCacheWiringTest`, `WholePackStoreTest` (an `isPositive()` per budgeted unit); `InForceResolution` javadoc; `HexagonalArchitectureTest` gains a rule over all main classes refusing static non-final fields and static `Atomic*`/`LongAdder` fields, allowing `PackCommitStore.COUNTER` and `GlobalAddressResolverProvider` by name, with a bad fixture.
- **Sequencing/depends-on**: none
- **Risks**: the meter counts on the binding thread only; each budget asserts at least one count per unit (FR-005b). A `CacheWork` counter on a shared cache must stay per call.

### IC-02 - Tests that can fail

- **Purpose**: fix the changed tests the squad and the full suite showed can pass vacuously or depend on order.
- **Relevant requirements**: FR-002, FR-002a, FR-002b, FR-002c, FR-003, FR-004
- **Affected surfaces**: `RegistrationWiringStagingTest` (entry at `<tmpdir>/staging/<job id>`, mtime set after writing, a configured-root positive control); `GitSourceFetcherTest` (exactly two inside the remote, the other four TIMED_WAITING with `acquireSlot` on their stack); `ConsistentReadTest` (rethrow the ender's failure); `RegistrationJobsTest.closingTheJobs...` (hold the room again before `close()`; assert ended busy, audited once, nothing written, no staging; drop the arrival count); the deadline test renamed for a queued job withdrawn; a new test reaching a worker that holds an ended job through the job's monitor (BLOCKED barrier); the `ended()` guard recorded as an equivalent mutant; `RemovalConcurrencyTest`'s wait loop through `Await.within`; the private `await` wrappers inlined to `Await.until`.
- **Sequencing/depends-on**: none
- **Risks**: each fix needs its own mutant; a mutant must not be left in place.

### IC-03 - Test-only members and seam wording

- **Purpose**: finish Q6=a and make main's remaining seams honest.
- **Relevant requirements**: FR-007a, FR-008
- **Affected surfaces**: `ClosureQueries.allConfigured()` leaves main (callers in tests rewritten against public behaviour); `RegistrationJobs.retained()` stays with its reason (operator ruling 2026-11-26); `RegistryWriteLock.hasQueuedThread` and `PackCommitStore.SwapPhases` keep their place with a reason in their javadoc; javadoc calling a constructor "the seam a test uses" rewritten; `CommitStores` moved from packs testFixtures to packs `src/test`; `RegistrationWiring` class javadoc.
- **Sequencing/depends-on**: none
- **Risks**: `retained()` has no faithful public stand-in (it counts ended jobs too); kept by operator ruling 2026-11-26.

### IC-04 - Documentation in line with the code

- **Purpose**: the docs describe what the code now does.
- **Relevant requirements**: FR-009
- **Affected surfaces**: ADR-0075 (dated AA5 amendment, Proposed); `docs/development/notes/pack-code-unification.md` AA5 bullet; `contracts/charter/scenarios/README.md` (the registration seam) and `whole-pack-registration.feature` description line; `docs/plans/codebase-health-2026-11-25.md` rows 8 and 9; the changelog fragment (`related:` ADR-0075, wording).
- **Sequencing/depends-on**: IC-01, IC-03 (the docs name the final types)
- **Risks**: the scenarios README is contract documentation; the wording change touches no step text, so the contract version does not move.

### IC-05 - Gates and evidence

- **Purpose**: prove the Mission's claims.
- **Relevant requirements**: NFR-001 to NFR-004, SC-001 to SC-004
- **Affected surfaces**: `kitty-specs/service-test-reliability-01M8AAEG/research.md` (mutation notes per change), the pull request body.
- **Sequencing/depends-on**: IC-01 to IC-04
- **Risks**: a red in `verifyAll` is attributed, never retried to green.
