# Tasks: The walk and closure checks behind ports (#556)

**Mission**: `closure-checks-behind-ports-01M7TS43` · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) ·
**Squad**: [research/squad-plan.md](research/squad-plan.md)
**Branch**: `feat/556-closure-checks-behind-ports` → `main`, as one pull request.

There are seven work packages in one dependency chain. Each leaves `./gradlew verifyAll` green on Java 25.
Implementation profile: `java-jenny`. Record subtask completion with `spec-kitty agent tasks mark-status`.

Most work packages change a fault type that a later one consumes, so they run in order, not in parallel. Where a work
package must keep a later one's file compiling, it makes the smallest bridging edit there (for example, taking the
first element of the new list) and records a one-line rationale in its review notes. The work package that owns the
file then replaces the bridge.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | `ClosureFault(Reason, packs, violations, requiredBy)`, with `of(Refusal)`, `of(CharterRefusal)` and `of(CompositionFault)`; `code()` from the token | WP01 | |
| T002 | `PackCodes.closure(RefusalCode)` over a closed set; a test that maps every code of the set and refuses one outside it | WP01 | |
| T003 | `ClosureVerdict` (`faults`, `rationale()`, `isPassing()`, `and`) | WP01 | |
| T004 | `PackRefusals.of(ClosureVerdict)`: one fault answered exactly as today; several get the D1 mapping (header violations, union of packs and `requiredBy`, the detail) | WP01 | |
| T005 | `PackCompositionResolver`: the delegations, `checkReferences` with `checkQualifiedReference`, and `refuseCaseOnlyMatches` return every fault of their phase | WP02 | |
| T006 | `EnhancementCompose.targetHeld` returns every target fault | WP02 | |
| T007 | `OfferingReadService.composed`: each phase throws its collected faults through `PackRefusals.of`, the resolver phase over `faults()`; `EdgeIndex` and `CompositeComponentResolver` map the same way | WP02 | |
| T008 | Tests: a multi-fault case per phase; single-fault answers unchanged; a `composition.feature` `@contract` scenario with three reference faults (US5) | WP02 | |
| T009 | `ImpactRelations.check` returns `List<ClosureFault>`; all seven rules collect, in edge order; `Violation` is deleted | WP03 | |
| T010 | `cyclesIn`: every cycle once, iterative; implied faults dropped (ungrounded on a reported cycle; a self-override as an override cycle) | WP03 | |
| T011 | `SpecializationCycleRule.check` returns every cycle as `ClosureFault`s | WP03 | |
| T012 | Tests with violation counts: US1.1 (two faults), US1.2 (one fault, as today), the census `CYCLE` fixture at one fault | WP03 | |
| T013 | `ClosureFacts` (domain), which `ClosureView` implements with domain types; `ClosureRoleFacts` and `ProfileEnhancers` read it, with no behaviour change | WP04 | |
| T014 | `FallbackProfileRule`, `RolePinRule` and `ProfileEnhancementRule` return `List<ClosureFault>`; the three `Breach` records are deleted; `unknown` alone ends the fallback | WP04 | |
| T015 | The `ClosureWalk` port and `OwnClosure` (domain); `OwnClosureWalk` (application) runs the fallback, the pins, then the enhancements | WP04 | |
| T016 | Tests: US2.1 to US2.3; a merge refusal is one fault | WP04 | |
| T017 | The `ImpactCheck` port (domain): `PackChange` (`Registration`, `Write`, `Removal`), `Replacement`, `Notices`, `Impact`, and the `Rule` view | WP05 | |
| T018 | `ClosureImpactCheck` (was `RegistrationImpact`): every closure, today's phase order, no snapshot after a fault, read failures propagate, the gate last; the session overload | WP05 | |
| T019 | The removal absorbs the dependents (409 first, then the compose); `PackDependents` and `ClosurePackDependents` are deleted; `PackUninstallService` judges in its own session | WP05 | |
| T020 | The callers name `ImpactCheck` and throw `PackRefusals.of`: `PackRegistrationService`, `ArtifactRemovalService`, `RelationshipRemovalService`, `CommitStorePackWriteStore` (no fallback); the wiring | WP05 | |
| T021 | The census: one finding per pack with every fault, each logged | WP05 | |
| T022 | Tests: US1.3 (two closures), US3.1 and US3.2, the uninstall counting test, the dry run that rethrows an unreadable pack, the #517 suite unchanged | WP05 | |
| T023 | The `MutabilityCheck` port (domain): `Writes` (`Live`, `Staged`), `Operation`, `Write`, the `Rule` view; `WritePolicyGate` is deleted | WP06 | |
| T024 | `PackMutabilityPolicy` implements it and returns every refused write; `PermissiveMutabilityCheck` | WP06 | |
| T025 | The callers judge all their writes at once: the per-kind registration services, the relationship endpoints, the artifact removal, the whole-pack registration | WP06 | |
| T026 | Tests: US4.1 and US4.2; the test files that named `WritePolicyGate` move to `MutabilityCheck`, their assertions kept | WP06 | |
| T027 | `CharterRefusalCode.yaml` descriptions and the `Changed` change fragment | WP07 | |
| T028 | `closure-faults.feature` `@contract` scenarios for US1, US2 and US4, passing the ADR-0059 D1 gate | WP07 | |
| T029 | ADR-0068 amendment; ADR-0075 slice-7 amendment (S1 flagged, the mapper, consumers, A1, `GraphWalk`/`Reachability`, R10, D6, #614); ADR-0081 note | WP07 | |
| T030 | `./gradlew verifyAll` green on Java 25 (round trip, ArchUnit; mutation floors are judged by the weekly or dispatched run, NFR-003) and the docs checks | WP07 | |

## Work Packages

### WP01 — The fault model and the closure edge's mapper

Depends on: nothing. Requirements: FR-008, C-006. Prompt: [tasks/WP01-fault-model.md](tasks/WP01-fault-model.md).

- **Goal.** One fault type and one mapping, which every later package uses.
- **Independent test.** `PackRefusals.of` maps one fault exactly as today and two faults by D1.
- **Subtasks.**
  T001 `ClosureFault` (WP01)
  T002 `PackCodes.closure` (WP01)
  T003 `ClosureVerdict` (WP01)
  T004 `PackRefusals.of(ClosureVerdict)` (WP01)
- **Risk.** SC-002: a single fault's detail must be byte-identical.

### WP02 — Every compose phase collects

Depends on: WP01. Requirements: FR-006. Prompt: [tasks/WP02-compose-phases.md](tasks/WP02-compose-phases.md).

- **Goal.** A compose answers every fault of the phase that fails, on the read and the write path.
- **Subtasks.**
  T005 resolver phases (WP02)
  T006 enhancement targets (WP02)
  T007 `OfferingReadService` and the per-reference checkers (WP02)
  T008 tests and the US5 scenario (WP02)
- **Risk.** The read path's single-fault answers. The effective-reference scenarios are the guard.

### WP03 — The relation rules collect every fault

Depends on: WP02. Requirements: FR-003, NFR-002. Prompt: [tasks/WP03-relation-rules.md](tasks/WP03-relation-rules.md).

- **Subtasks.**
  T009 `ImpactRelations` (WP03)
  T010 `cyclesIn` and implied faults (WP03)
  T011 `SpecializationCycleRule` (WP03)
  T012 tests with counts (WP03)
- **Risk.** Implied faults duplicating the answer (squad R1).

### WP04 — The closure walk

Depends on: WP03. Requirements: FR-001, FR-005. Prompt: [tasks/WP04-closure-walk.md](tasks/WP04-closure-walk.md).

- **Subtasks.**
  T013 `ClosureFacts` (WP04)
  T014 the three rules (WP04)
  T015 the port and `OwnClosureWalk` (WP04)
  T016 tests (WP04)
- **Risk.** The `ClosureFacts` surface (squad A1). Do T013 first, with no behaviour change.

### WP05 — The impact check port

Depends on: WP04. Requirements: FR-001, FR-002, FR-004, FR-009, FR-010, FR-011, NFR-001, C-001, C-002, C-003.
Prompt: [tasks/WP05-impact-check.md](tasks/WP05-impact-check.md).

- **Subtasks.**
  T017 the port (WP05)
  T018 `ClosureImpactCheck` (WP05)
  T019 the removal and the dependents (WP05)
  T020 the callers and the wiring (WP05)
  T021 the census (WP05)
  T022 tests (WP05)
- **Risk.** #517's dry run and NFR-001, plus a new counting test.

### WP06 — The mutability check replaces the write policy gate

Depends on: WP05. Requirements: FR-001, FR-007, C-001. Prompt: [tasks/WP06-mutability-check.md](tasks/WP06-mutability-check.md).

- **Subtasks.**
  T023 the port (WP06)
  T024 `PackMutabilityPolicy` (WP06)
  T025 the callers (WP06)
  T026 tests (WP06)
- **Risk.** A wide mechanical rename. Keep each test's assertions.

### WP07 — Contract, scenarios and decision records

Depends on: WP06. Requirements: FR-012, FR-013, FR-014, NFR-003, C-004, C-005.
Prompt: [tasks/WP07-contract-and-records.md](tasks/WP07-contract-and-records.md).

- **Subtasks.**
  T027 contract (WP07)
  T028 scenarios (WP07)
  T029 ADRs (WP07)
  T030 the gates (WP07)
- **Risk.** The docs checks and the scenario gate.
