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
| 5 | architect | MEDIUM | `RegistrationJobs.retained`, `ClosureQueries.allConfigured`, `RegistryWriteLock.hasQueuedThread` test-only | accepted (Q6=a): first two leave main, the third stays with a reason | FR-008, IC-03 |
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
- **Kept sleeps**: the dripping server (`HttpsGuideFetcherLoopbackTest`), the stress-test jitter
  (`MixedReadConcurrencyTest`, `RegistrationConcurrencyTest` via `Await.within`), the slow swap whose duration is
  asserted (`MixedReadConcurrencyTest` 600 ms). Each is a stimulus, not a wait.

## Mutation notes (red-first evidence)

Filled per change in WP05.
