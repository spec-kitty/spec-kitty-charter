# Mission Specification: Switch-gate scenario coverage (ADR-0059 D1)

**Mission Branch**: `mission/switch-gate-coverage-9-72`
**Created**: 2026-11-12
**Status**: Draft
**Input**: Slice D (#468) Part 1: issues #9 and #72, re-scoped to ADR-0059 D1 by PR #507.

## Intent Summary

[ADR-0059](../../docs/adr/0059-switch-gate-cascade-edges-and-activation-rulings.md) D1 rules that an operation is
ready for core to switch to it when its design-derived BDD `@contract` scenarios and its contract tests pass against
the service. A `@reserved` 501 pin does not count. Nothing runs against core's Python answers.

What exists on `main` (measured 2026-11-12, `fd20faa`, 46 operations, 2,496 exchanges in the contract suite):

- `OperationCoverage` fails the suite when a declared 2xx answer is exercised by no scenario (R-20). Every one of the
  46 operations passes it.
- Every exchange is checked against the contract's schema, its links and the fingerprint rule. These checks are the
  operation's contract tests in D1's sense.
- Nothing says, per operation, whether it is switchable, which scenarios cover it, or whether its declared refusals
  are exercised. About 50 declared refusal answers (status and operation) are exercised by no scenario of that
  operation: 400, 404, 409, 412, 415, 422, 428, 503, 304 and 413 answers of 30 operations.

This mission makes "this operation is switchable" an explicit, checked fact per operation, ships it with the
contract release, and fills the scenario gaps so every operation is switchable.

## User Scenarios & Testing

### User Story 1 — The register says which operations core may switch to (P1)

A core maintainer vendoring a contract release reads one file, shipped in `charter-scenarios.tar.gz`, that lists
every operation and whether it is switchable, and gets a per-operation report of the scenarios that cover each
answer.

**Acceptance**

1. Given the contract, when the suite ends, then the register lists exactly the contract's operations: an operation
   the register lacks, or an entry naming no operation, fails the run (fail closed).
2. Given an operation marked switchable, when one of its declared answers is exercised by no selected scenario of that
   operation, then the run fails, naming the operation and the status.
3. Given an operation marked held, when its coverage is complete, then the run fails and asks for it to be marked
   switchable: the register cannot go stale in either direction.
4. Given a reserved operation (`x-reserved`), then it must be held; marking it switchable fails the run.
5. After the suite, a report names, per operation and status, the scenarios (`file:line`) that exercised it.

### User Story 2 — Every operation is switchable (P1)

Each declared refusal of each operation is exercised by a scenario that calls that operation, except the answers one
mechanism gives in front of every operation, which are proven once and named in the register.

**Acceptance**

1. Every operation's declared 2xx, 3xx and 4xx answers, and its 503, are exercised by a scenario of that operation,
   or are a shared answer the register names with the scenario that proves it.
2. Every operation is marked switchable, and `./gradlew verifyAll` is green.

### User Story 3 — Core's half is recorded, not built (P2)

The part of #72 that makes the register block core's switch is core work at incorporation (issue-tracker Rule 5).
It is recorded in the pack-ecosystem work breakdown, and #72 is narrowed to it.

### Edge Cases

- An exchange made during setup (a `GET` before a `PUT`) counts toward the operation it calls. Coverage is about
  the answer being exercised and checked, not about which step made it.
- A scenario selected out by a tag filter is not run, so a filtered run reports gaps, as `OperationCoverage` does.
- A `default` response is not a status and is not counted.
- A new operation (for example slice B's `GET /packs/{packId}/doc`) fails the run until it has a register entry.

## Requirements

### Functional Requirements

- **FR-001** A register `contracts/charter/scenarios/switch-gate.yaml` holds one entry per operation, by
  `operationId`, with `switch: ready` or `switch: held` and a reason for held.
- **FR-002** The register's `shared` section names each status answered by one mechanism in front of every
  operation, with the scenario that proves the mechanism. The check fails when that scenario did not run.
- **FR-003** After the last scenario, the suite records every exchange by operation and status and fails when an
  entry is missing or extra, a ready operation has an unexercised declared status that is not shared, a held
  operation is complete, or a reserved operation is ready.
- **FR-004** It writes the per-operation report to `build/reports/switch-gate/`.
- **FR-005** It subsumes `OperationCoverage`'s 2xx check (R-20) with no loss: a missing 2xx answer still fails, with
  the R-20 wording.
- **FR-006** New scenarios fill the gaps of User Story 2. They go in new feature files, so they do not collide with
  slices B (#466) and C (#467).
- **FR-007** The scenarios README, the service-build guide and ADR-0059 D1's "what follows" describe the register.

### Non-Functional Requirements

- **NFR-001** The check's logic is unit-tested with planted contracts and registers, a control for each failure.
- **NFR-002** No exchange, scenario or field is excluded to make the gate pass (charter: no fake green).

### Constraints

- Contract operations, schemas and responses are not changed. A declared status that cannot be raised is a contract
  defect: it is reported, not dropped.
- Do not edit files slices B and C change (registration, `PackCommitStore`, `file-kinds.yaml`, in-force resolution,
  action context).

## Success Criteria

- **SC-001** All 46 operations are `ready`, with no unexercised declared status beyond the shared ones.
- **SC-002** Deleting a covering scenario, adding an operation, or marking a reserved operation ready each fails the
  suite (proven by the unit tests).
- **SC-003** #9 closes with evidence; #72 is narrowed to core's half.
