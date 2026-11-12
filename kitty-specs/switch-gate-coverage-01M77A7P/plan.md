# Implementation Plan: Switch-gate scenario coverage (ADR-0059 D1)

**Branch**: `mission/switch-gate-coverage-9-72` | **Spec**: [spec.md](spec.md)

## Summary

Turn the suite-wide R-20 success check into a per-operation switch gate: a register shipped with the scenarios,
checked after the last scenario against every exchange the suite made, plus scenarios for the declared answers no
scenario exercises today.

## Technical Context

- Java 25, Cucumber on the JUnit platform (`CharterContractSuite`), in `service/deployable` tests.
- `ContractConformance` reads the contract from the test classpath; `CharterSteps.everySuccessAnswerIsLinked`
  records each exchange into `OperationCoverage`, whose `@AfterAll` fails on a gap.
- `contracts/charter/scenarios/**` is copied onto the test classpath as `scenarios/**` and released as
  `charter-scenarios.tar.gz` (`scripts/release/contract-release-assets.sh`), so a file there ships to core.

## Charter Check

- Contract and test-architecture work: adversarial squad at this plan point and before merge (charter "How work
  lands").
- No contract shape changes, so no `CHANGELOG` `### Breaking` entry. The register is a new file of the scenario
  set, listed in `contracts/charter/CHANGELOG.md` under the unreleased version as an addition.
- Canonical shape only; no exemption register for gaps (charter: no field excluded from a check to make it pass).
- Core work is recorded, not filed (issue-tracker Rule 5).

## Design

### The register (`contracts/charter/scenarios/switch-gate.yaml`)

```yaml
shared:            # statuses one mechanism answers in front of every operation, proven once
  "401": {mechanism: "...", provenBy: "security.feature"}
  "403": {mechanism: "...", provenBy: "security.feature"}
  "500": {mechanism: "...", provenBy: "errors.feature"}
operations:
  getPack: {switch: ready}
  someOp: {switch: held, reason: "..."}
```

`provenBy` names a feature file whose scenarios must have exercised that status on some operation in this run.

### The check (`SwitchGate`, replacing `OperationCoverage`)

- `record(method, path, status, scenario uri:line)`: every exchange, not only 2xx, by the operation
  `ContractConformance.operationTemplate` names.
- Declared statuses per operation: each numeric response key (`default` excluded), with the `operationId` and
  `x-reserved`, read from the classpath contract.
- `@AfterAll`: compute `verdicts = SwitchGate.judge(declared, exercised, register)` and fail on any violation:
  1. the success gaps (R-20 wording kept), for every built operation;
  2. register entries missing or extra against the contract's operations;
  3. a `ready` operation with an unexercised declared status not in `shared`;
  4. a `held` operation that would pass 3;
  5. a reserved operation marked `ready`;
  6. a `shared` status whose `provenBy` file exercised it nowhere.
- Writes `build/reports/switch-gate/switch-gate.md` (operation, status, scenarios) whatever the outcome.
- Pure judging logic in a package-private class with planted inputs for unit tests (`SwitchGateTest`), as
  `OperationCoverageTest` does today.

### Scenarios

New feature files, by area, each `@contract`, Scenario Outlines where the cases share steps:

- `read-refusals.feature`: the 400 (parameter grammar), 404 and 304 answers of the pack, mission-type and effective
  reads that no scenario covers.
- `pack-write-refusals.feature`: the 404, 409, 412, 422 and 503 answers of the per-artifact writes and removals.
- `activation-write-refusals.feature`: the 400, 412, 415, 422, 428 and 503 answers of the activation writes.
- The registration gaps (`registerPackFromSource` 412, 413, 415): new file `registration-from-source-refusals.feature`
  in `registration/`, not the files slice B edits.

A step is added only where none exists (for example a JSON body sent with another media type). A declared status
that cannot be raised is reported in the PR as a contract defect, and the operation stays `held`.

## Implementation Concern Map

### IC-01 — Gate machinery

`SwitchGate`, the register, `SwitchGateTest`, and the hook change in `CharterSteps`. Initially every operation with a
gap is `held`, so the build stays green.

### IC-02 — Scenarios, area by area

Fill the gaps; flip each operation to `ready` as it completes (the check fails a complete `held` one).

### IC-03 — Docs and core's half

Scenarios README ("Switch gate"), `docs/guides/service-build.md`, ADR-0059 D1 note, the contract CHANGELOG, the
roadmap's slice D row, and the core consequence in `docs/plans/pack-ecosystem-work-breakdown.md`.

## Risks

- Scenario volume (about 50 answers). Mitigation: Scenario Outlines per area.
- Collisions with slices B and C. Mitigation: new files only; the register's exactness check makes B add one line
  for `GET /packs/{packId}/doc`, whichever lands second.
- A shared status hides a per-operation defect. Mitigation: only 401, 403 and 500 are shared, each answered by a
  filter or handler in front of every controller; 503 and 415 are not shared.

## Plan-point squad (2026-11-12) and what changed

Two reviewers, test-architecture and governance lenses. Folded:

- **500 is not one mechanism** (HIGH): no shared statuses at all. 401 and 403 are asserted on every operation by
  `every-operation.feature`; 500 is asserted per operation by `unexpected-failure.feature`.
- **Incidental refusals** (MEDIUM): a non-2xx answer counts only where `Then the response status is <n>` asserted it.
- **Reserved and held** (MEDIUM): a held reserved operation is never reported stale; a planted control proves it.
- **503 seam** (MEDIUM): the busy-registry scenarios hold the real `RegistryWriteLock`.
- **CHANGELOG** (HIGH): a fragment under `contracts/charter/changes/`, not the CHANGELOG itself.
- **Breaking surface** (HIGH): the register's header, the README and the ADR amendment say a key change or a
  ready-to-held move is `### Breaking`. A release-gate comparison of the register against the baseline is not built
  (PR "Not done").
- **ADR** (MEDIUM): a dated amendment to ADR-0059 D1, not a note.
- **Issues** (MEDIUM): #72 closes and core's half is C13 in the work breakdown; #14 and #69 are edited; #9's
  provenance bullet maps to the fingerprint rule.
- **Collisions** (MEDIUM): the spec's constraints name the shared files.
