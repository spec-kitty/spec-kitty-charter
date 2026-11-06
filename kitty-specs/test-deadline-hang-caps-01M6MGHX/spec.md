# Mission Specification: Deadline checks become hang caps

**Mission Branch**: `test-deadline-hang-caps`
**Created**: 2026-11-05
**Status**: Draft
**Input**: Operator brief (2026-11-04): pick up #320 and the assessment's R2 (W3). Intent and the 30 s cap
confirmed in decision `01M6MGJA27NG72JCTBXB1TWG80`.

## Intent Summary

- **Actor:** a contributor or CI runner executing `:packs:test` inside `verifyAll`, where every module's tests
  run at once on a possibly starved runner.
- **Trigger:** eight wall-clock checks in five `packs` test files bound the time of work that proves a guard
  (the YAML alias-bomb budget, the linear-time pattern engine, the commit lock, the fetcher's deadline). Their
  bounds (2 s to 10 s) can be missed by a loaded runner, the failure mode of #316.
- **Outcome:** each check keeps gating the guard it proves, and a loaded runner can no longer fail it by
  slowness alone. A real regression of the guard still fails the test, at the latest when the cap expires.
- **Invariant:** the checks stay in `:packs:test` and are never tagged `timing`. ADR-0057 makes `timing`
  report-only, which would stop correctness and security guards from gating. No test is added or removed.
- **Boundary:** the eight sites listed in #320's comment of 2026-11-04. The existing `@Timeout(60)` hang caps
  and spin-wait deadlines are already generous and stay as they are.
- **Amendment (2026-11-05)**: Op `01M6NB6N` widened the boundary. The 10 s latch and spin-wait helpers of
  `InterruptedCommitRepairTest` and the two 2 s bounds in the fetcher's `Deadline` tests fired before the cap,
  so they now wait up to it. The `@Timeout(60)` hang caps stay as they are. The landing pass on PR #379 added a
  cause check to the lock-skip test: the listing never waits on the held lock, since under the cap alone a
  listing that waited its 10 s still passed.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A slow runner does not fail a deadline check (Priority: P1)

A pull request touches nothing in `packs`, and CI runs it on a heavily loaded runner. Today a deadline check
with a 2 s bound can fail because the runner is slow and drop the PR from the merge queue. After this change,
the same run passes, because every bound is a hang cap of 30 s, at least 15 times larger than today's bounds
and orders of magnitude above the work's own time.

**Why this priority**: it is the flake the assessment ranks W3 and #316 showed in practice.

**Independent Test**: the eight checks pass under `verifyAll`; each bound in the diff is 30 s, and no
test is tagged `timing`.

**Acceptance Scenarios**:

1. **Given** the eight checks, **When** `verifyAll -PrequireSymlinks=true` runs, **Then** they pass, still in
   `:packs:test`, and none is tagged `timing`.
2. **Given** a check whose bound guarded against a hang, **When** its guard works, **Then** the test still
   asserts the cause it proves (the refusal reason, the finding code, the matched problem), not only the time.

### User Story 2 - A broken guard still fails its test (Priority: P1)

A contributor removes or breaks the guard a check proves (the alias budget, the linear-time engine, the
fetcher's deadline, the commit lock's try-lock). The test fails, either because the work outlives the cap or
because the cause assertion sees the wrong outcome.

**Why this priority**: widening a bound must not turn a guard test into fake green (`QA_003`).

**Independent Test**: one planted break per guard turns its test red; reverting turns it green.

**Acceptance Scenarios**:

1. **Given** a planted break of a guard, **When** its test runs, **Then** it fails within the cap plus
   tear-down, naming the cap or the wrong cause.

### Edge Cases

- The fetcher checks need the slow work to outlast the cap. Otherwise a broken deadline would let the
  work finish before the cap and pass: the lookup's sleep and the drip's duration rise to 60 s.
- A preemptive timeout runs the work in another thread. Where the cap expires on a broken guard, the
  abandoned thread must not keep the JVM busy for the rest of the suite. The planted-break proof records
  what happens.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Storage deadline checks become 30 s hang caps | As a contributor, I want the bounds at `ParsedYamlTest` 130, 146 and 153, `EmbeddedSchemasTest` 80 and `InterruptedCommitRepairTest` 166 and 200 set to 30 s, with their cause assertions kept, so that a slow runner cannot fail them. | High | Open | [build] | no — the diff shows each bound; a planted break per guard shows the test still fails |
| FR-002 | Fetcher deadline checks become 30 s hang caps over 60 s slow work | As a contributor, I want the elapsed-time bounds at `HttpsGuideFetcherTest` 540 and `HttpsGuideFetcherLoopbackTest` 93 set to 30 s, the slow lookup and the drip lengthened to 60 s, and the `TIMEOUT` refusal still asserted, so that the check proves the deadline fired rather than the work ending. | High | Open | [build] | no — a planted break of the deadline makes the test fail at the cap |
| FR-003 | Planted-break proof per guard | As a reviewer, I want one planted break per guarded behaviour (alias budget, linear-time patterns, commit lock, fetcher deadline for lookup and body) to turn its test red, recorded in the PR, so that the widened checks are shown not to be fake green. | High | Open | [build] | no — the record shows a red run per guard |

**Amendment (2026-11-05) to FR-002**: the elapsed-time bounds were read only after the fetch returned, so a
broken deadline waited out the 60 s slow work. Op `01M6NB6N` runs both fetches under `assertTimeoutPreemptively`
with the 30 s cap instead, and the `TIMEOUT` refusal is still asserted. The landing pass on PR #379 dropped the
slow-lookup test's interrupt check, which repeated `aLookupThatOutrunsTheDeadlineIsInterruptedAndDoesNotOutliveIt`.

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Suite time on a green run | `:packs:test` wall time does not grow by more than 5 s on a green run (the lengthened slow work is cut off by the deadline, not waited for). | Performance | High | Open |
| NFR-002 | Gate stays green, nothing hidden | `verifyAll -PrequireSymlinks=true` green; `:packs:test` execution count unchanged; 0 tests tagged `timing` added; 0 `@Disabled`, 0 suppressions. | Reliability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Never tag them `timing` | The eight checks stay in `:packs:test` (ADR-0057; #320 comment of 2026-11-04; service build guide). | Technical | High | Open |
| C-002 | No new tests, no main code | Only the eight checks and their fixtures change; no production code changes. | Technical | High | Open |
| C-003 | Do not widen until it passes | Each new bound is the confirmed 30 s cap, justified by the work's own time, not tuned to make a flaky run pass (#320). | Technical | High | Open |
| C-004 | Stay clear of #367's refactor | `HttpsGuideFetcherTest.send` (nested class) is on #367's list, owned by #368's mission; do not touch it. | Technical | Medium | Open |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 8 of 8 listed bounds are 30 s; 0 of them tagged `timing`. — [build] · no-op passable: no
- **SC-002**: each guarded behaviour (5) has a recorded planted break that turned its test red. — [build] · no-op passable: no
- **SC-003**: `:packs:test` wall time on a green run grows by at most 5 s. — [ratchet] · no-op passable: yes (paired with SC-002)

## Assumptions

- The work each check bounds takes milliseconds on a quiet machine, so 30 s leaves a margin of more than
  1,000x. The implementation records the measured work time per site.
