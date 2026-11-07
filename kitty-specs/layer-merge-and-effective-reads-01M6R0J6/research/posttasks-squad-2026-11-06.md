# Post-tasks squad: dispositions (2026-11-06)

Three read-only lenses on WP01–WP29: planner-priti (plan fidelity), qa-test-designer-tessa (test design),
architect-alphonso (sequencing, ownership, per-PR green). Each finding is accepted (folded as proposed), changed
(folded differently, with the reason), or rejected. Decisions recorded for this fold:

- `DM-01M6RN5MTWC98PXEVJNFQSRRJX`: `skippedByScope` stays as the write report of the `cascade` scope.
- `DM-01M6RN5SG19KXAHH4VGT9QBYA1`: the failing timing gate is an operation-count budget; wall time is report-only.
- `DM-01M6RN5Y406SB4KA13QYMQ2P8J`: create keeps `422 version_not_held`; other writes drop the pin.
- `DM-01M6RN62PPCMSH6M54D2HXYZEQ`: where the FR-042 round trip runs.
- `DM-01M6RN677S4XN4WR8ZVMAW777G`: the `git-host` tests, the shim, and the CI job.
- `DM-01M6RN6BWE6SVZZE8ERQ8Z9MV6`: a mutual override is an override cycle (`pack_unreadable`).
- `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`: red evidence is an assertion failure with bound glue; there is a suite guard; scenarios are `@WIP` until served.

## Architecture lens

| Finding | Disposition | Where |
|---|---|---|
| H: `skippedByScope` is a live write report, not a read bucket | Accepted (kept on the write response, empty at read) | DM above; WP01, WP22; data-model; contract-delta; plan |
| H: WP06 breaks `OfferingReadService`, `PresetTest`, `PresetRoundTripTest` | Accepted | WP06 `owned_files` |
| H: WP03 breaks three `activatedKinds` tests | Accepted | WP03 `owned_files` (`ActivationRestartTest` included) |
| H: `ClosureQueriesTest` and `PacksAvailabilityTest` (owned in A and B) break in A0 | Changed: the owners stay; the A0 WPs fix them as listed shared edits | WP03 (both), WP06 (`PacksAvailabilityTest`), WP07 (`ClosureQueriesTest`) |
| H: WP28 breaks `ReservedRefusalCodesTest` | Accepted | WP28 shared edit |
| M: the DTO regeneration breaks the compile of the A0 lanes | Changed: WP01 makes the minimal `ActivationMapper` compile edit and WP03 follows the domain; WP08 does the full mapping | WP01 and WP03 shared edits; WP01's exit check compiles `:activations` |
| M: WP03 and WP07 both edit `ClosureView` in parallel | Accepted | WP07 depends on WP03 |
| M: WP08 edits unowned features and examples | Accepted | WP08 owns the selections and presets features; WP01 owns `ActivationChange.selected.yaml`; WP22 owns the other two examples |
| L: WP26 and WP29 both edit ADR-0059 | Accepted | WP29 depends on WP26 |
| L: `lane-planning` bundles WP09 (A0) and WP10 (A) | Accepted as a closeout note: the lane is cut by commit | this table |

## Planner lens

| Finding | Disposition | Where |
|---|---|---|
| H: no subtask for the NFR-009 sweep and free-space check | Accepted | WP16 T151 (`pack_storage_full`, declared in WP11) |
| H: the NFR-008 timeout end state is missing | Accepted | WP11 T049 (`registration_timed_out`), WP16 T077 (fake clock) |
| M: the bound and ungrounded floor are missing from the data model | Accepted | data-model (the exception, the fixed point, `requiredNotInForce`); WP22 T109/T110 pin the tokens |
| M: `unheldRequired` vs `requiredNotHeld` | Accepted (`requiredNotHeld`) | WP22 T110 |
| M: `version_not_held` on create vs the B1 drop | Accepted; ruled | DM above; WP01 T002 declares `droppedPins`; WP04 T017; data-model; contract-delta |
| M: the timing NFRs are weakened to report-only | Changed: the failing gate is a deterministic operation-count budget, and wall time stays report-only (ADR-0057) | DM above; spec NFR-003 and NFR-008; WP25 T130; WP16 T081 |
| M: packId grammar, case-fold uniqueness and the real-path check appear only in context | Accepted | WP14 T065 (`PackIdRulesTest`), WP16 T079 |
| M: the branch-ref-without-commit `If-Match` rule has no test | Accepted (`428`) | WP18 T091/T093; contract-delta |
| M: SC-005 scenarios for A4, B5, A10, A14, A15, B8, plus the trace | Accepted | WP08 T147; trace file WP09 T149, filled by WP20, WP26 and WP29 |
| M: no scenarios for B6/B7 warnings and B4 `denyNotApplied` | Accepted | WP22 T113 |
| L: `428`? on PUT/POST; `registration_not_found` | Accepted (no `428`; code declared) | WP11 T047/T049 |
| L: audit fields and the `412` job case | Accepted | WP16 T077, T080 |
| L: `notices.activationsAffected` is in PR A vs PR B | Accepted (PR B, per the delta) | WP11 T048, WP22 T110 |
| L: spec and plan errata (FR-017, IC-13 is PR C, "pending") | Accepted | spec FR-017; plan IC-13 risk, IC-20 placement, the ruling table |
| L: boilerplate DoD | Accepted | every WP has an **Exit check** line |

## Test-design lens

| Finding | Disposition | Where |
|---|---|---|
| H: an undefined step counts as red | Accepted | DM above; WP08 T148 (`UndefinedStepGuard`); every contract WP commits `@WIP`, and the serving WP records red |
| H: absence and `304` checks pass vacuously | Accepted | positive controls in WP01, WP11, WP22, WP28; the ETag cases in WP22 T113 and WP24 T123 |
| H: FR-042 cannot be proven in client-java alone, and "or a no-op" | Changed: a client wire test (JDK `HttpServer`), the generator option fix, and the server canonical form plus an `@contract` scenario. `languages` is excluded from the canonical form, because absent and `[]` differ | DM above; WP03 T012, WP08 T037, WP01 T006 |
| H: the hostile corpus asserts only "refused" | Accepted | WP13 T063 (violation and path per row, benign controls, byte bound, `0x7075`, hardlink outside the pack); WP16 T076 (the `413` reads no byte) |
| H: stratum 4 can undo stratum 3 (FR-029) | Accepted | WP23 T116 (cross-stratum case), T118 (FR-029 invariant); data-model fixed point |
| H: the US1 AS6 async race is untested | Accepted | WP11 T051 scenario; WP16 T077 `JobGate` seam |
| H: the NFR-003 3× gate never runs | Changed: see the timing DM; the cache key varies per run in the reported test | WP25 T130 |
| H: failing loudly on git ≥ 2.50.1 is unsafe | Accepted | DM above; WP18 T093, T152 (shim, `git-host` tag, the `git-source` job) |
| H: PIT does not mutate `infra.archive` or `infra.api` | Accepted | WP13 T150 |
| M: timed-out mutants inflate the score | Accepted (the `stress` and `git-host` tags are excluded from PIT) | WP13 T150; WP12, WP14 and WP19 tag their concurrency tests |
| M: three of WP29's scenarios are green before | Accepted (labelled as regression controls) | WP29 T144 |
| M: the WP12 oracle passes on all-`stale_input` | Accepted | WP12 T057 (overlap count > 0, stale rate ≤ 10%, the retry ends in a version) |
| M: the A2 oracle is disjunctive; the mutual override conflicts with C28 | Accepted; ruled | WP23 T116; DM above |
| M: status lifecycle, the forgotten URI, the timeout | Accepted | WP11 T051, WP16 T077 |
| M: deterministic `503` on `tryLock`; nothing slow under the lock; sweep; free space | Accepted | WP12 T053, WP16 T077, T151 |
| M: synchronous refusals not audited | Accepted | WP16 T080; WP19 T098 (each `DELETE` outcome) |
| M: the v1 upgrade's reads must never write; the ETag after the upgrade | Accepted | WP01 T006 scenario; WP22 T113; WP24 T123 |
| M: `quickstart.md` uses `activatedKinds` and "report-only" | Accepted | quickstart |
| L: jqwik is excluded | Accepted (seeded generators) | WP23 T118 |

## Rejected

None. Every finding is accepted or folded in changed form.
