# Mission Specification: Closure symmetry: one closure rule on every path

**Mission Branch**: `kitty/mission-closure-symmetry-01M7PNFT` (lands on `feat/355-closure-in-pack-ruleset`, then `main`)
**Created**: 2026-11-18
**Status**: Draft
**Input**: Charter #355 (P2, epic #142; under the pack-check epic #549), with #593 folded in. #355's issue body,
re-scoped 2026-11-17 with the operator's rulings, is the brief.

## Intent Summary

Brief-intake mode: #355's body records the operator's rulings of 2026-11-17, so no discovery interview was run.

- **Primary actor**: a pack author. Their CI runs validate mode, and a client sends per-file writes.
- **Trigger**: a pack holds a guard, a lifecycle, an executable asset or a mission type that does not close over its
  references. Or a per-file write would leave the pack in that state.
- **Outcome**: every path that judges a pack gives the same answer, from one closure rule. The load leaves the artifact
  out. The whole-pack write refuses the pack. Validate mode reports an error on the file the load drops. A per-file
  write is refused with `409 registration_conflict`.
- **Invariant**: validate mode never reads greener than the load. No path drops silently what another path refuses.
- **Rulings** (operator, 2026-11-17): a per-file delete of an asset that a guard names is **refused**, not cascaded. A
  closure finding in validate mode is an **error**. The per-file status is **`409 registration_conflict`**.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Validate mode agrees with the load on the closure (Priority: P1)

A pack author runs validate mode in CI over a pack whose guard names an asset the pack does not hold. Validate mode
reports an error at the guard's file, with the reason the load records. It also reports the lifecycle that names that
guard.

**Why this priority**: validate mode is the contract pack-check (#478) inherits. A green validation of a pack the
service then partly drops is the defect #355 records.

**Independent Test**: validate a broken fixture, load the same fixture, and compare the files and reasons.

**Acceptance Scenarios**:

1. **Given** a guard `implemented_by` an asset the pack does not hold, **When** validate mode runs, **Then** it reports
   an error at the guard's file and at the file of the lifecycle that names it, as the load records them.
2. **Given** an executable asset whose `args` repeat its `entry`, or a lifecycle with an unknown state, no terminal
   state, an unreachable state, a transition out of a terminal state or two targets for one state and event, **When**
   validate mode runs, **Then** it reports an error at that file, as the load records it.
3. **Given** the repaired pack, **When** validate mode runs, **Then** it reports no closure finding.

### User Story 2 - A per-file write cannot leave a pack the load would partly drop (Priority: P1)

A per-file write (an added or replaced artifact, or a delete) is judged on the pack as it will be after the write,
under the pack's write lock. A write that would leave a guard, lifecycle, asset or mission type that does not close is
refused with `409 registration_conflict`, with the reason the load would record, and nothing is written.

**Why this priority**: item 13 adds registrations for guards, lifecycles, assets, mission types and templates. The check
must be in place first, and it must cover deletes.

**Independent Test**: drive the per-file write store with a change that adds a guard naming a missing asset, and with
a delete of the asset a guard names, and check the refusal and the unchanged tree.

**Acceptance Scenarios**:

1. **Given** a pack that closes, **When** a per-file write adds a guard whose asset the pack does not hold, **Then**
   it is refused with `registration_conflict`, the reason names the guard as the load would, and no file changes.
2. **Given** a pack whose guard names asset `carrier-lint`, **When** a per-file delete removes that asset, **Then** it
   is refused with `registration_conflict`, not cascaded, and no file changes.
3. **Given** the same writes on a repaired pack, **Then** the closure check lets them through.

### User Story 3 - One standing symmetry table (Priority: P1)

One table of broken fixtures, each paired with a repaired control, asserts for every closure case and every
`Executable` and `Lifecycle` invariant: validate mode reports it, the load drops it, the whole-pack write refuses it,
and the per-file write that would produce it is refused.

**Why this priority**: it is the standing guard that stops the paths drifting again.

**Independent Test**: the table test itself; every row fails if any path stops judging its case, and every control
passes on all paths.

### User Story 4 - The fair-lock starvation test says what starved (Priority: P3, #593)

`MixedReadConcurrencyTest`'s fair-lock case bounds each write's wait per round. When a write waits too long, it fails
naming the write and the round. A loose outer timeout catches only a hang.

**Why this priority**: the per-file closure check runs under the same `PackWriteLocks`, so a starvation regression must
be visible and believable. It is P3 as filed.

**Independent Test**: the test passes on a quiet runner and on CI. A deliberately starved write fails with its name and
its round.

### Edge Cases

- A cascade: an asset that does not resolve drops the guard it backs, and then the lifecycle naming that guard. Every
  path reports all three, as the load does today.
- A pack-qualified reference (`<pack>::asset:<id>`, `<pack>::guard:<id>`) is refused on every path, as at load today.
- A mission type whose output template is a top-level template, not one of the type's own, is refused on every path.
- A stored pack that already fails to close: the load drops the broken artifacts, so the loaded aggregate closes, and
  a per-file write is judged only on what the write changes.
- A relationship-only per-file write does not change the closure, and is not refused by it.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status | Delivery | No-op passable? |
|----|-------------|--------|----------|-----------------|
| FR-001 | One closure rule in the PACK ruleset decides asset entry, guard asset, guard `config_schema`, lifecycle guard entries and `config`, and mission-type output templates, with the cascade. `GuardClosure` and `MissionTemplates` no longer hold rule logic. | Proposed | build | no |
| FR-002 | The load and the whole-pack write behave as today: the same artifacts are left out or refused, with the same files, codes and reasons. | Proposed | ratchet | yes, paired with FR-006's controls |
| FR-003 | Validate mode reports an error finding, at the file and with the reason the load records, for every closure case and every `Executable` and `Lifecycle` invariant. It gets them by reusing the load's assembly and the PACK ruleset, not by restating rules. | Proposed | build | no |
| FR-004 | The per-file write path judges the pack as it will be after the write, with the same rule, under `PackWriteLocks`, and refuses with `409 registration_conflict` and the load's reason. A delete of an asset a guard names is refused. | Proposed | build | no |
| FR-005 | Validate mode over charter's own example packs reports no new finding, or each new finding is listed in the pull request. | Proposed | ratchet | yes, paired with FR-003 |
| FR-006 | One table-driven symmetry test covers every case on all four paths, each row with a repaired control. The per-case tests it replaces are folded in, not duplicated. | Proposed | build | no |
| FR-007 | ADR-0042 decision 7's known-gap paragraph is amended, and the work breakdown's item 8 note is cleared. | Proposed | build | no |
| FR-008 | `MixedReadConcurrencyTest`'s fair-lock case bounds each write's wait per round and fails naming the write and the round that exceeded it. A loose outer timeout catches only a hang. The bound is not raised on its own. | Proposed | build | no |

### Non-Functional Requirements

| ID | Requirement | Status |
|----|-------------|--------|
| NFR-001 | `./gradlew verifyAll` is green on Java 25, with strict dependency verification, the style gates, the ArchUnit gate and the whole-pack round trip. | Proposed |
| NFR-002 | The rewritten #593 test passes 10 consecutive local runs and every CI run of the pull request, with each write's per-round bound at most 10 s and the outer timeout at most 120 s. | Proposed |
| NFR-003 | The open-packs drift test (`:pack-validation:driftTest`) runs against this branch, over spec-kitty-open-packs `main`, and its result is reported in the pull request. | Proposed |

### Constraints

| ID | Constraint | Status |
|----|------------|--------|
| C-001 | The charter's model is canonical. No legacy reader, no deprecation window, no "core first" ordering. | Accepted |
| C-002 | Tests and examples use only the canonical shape, except tests of error handling. No field is excluded from a parity or round-trip check. | Accepted |
| C-003 | The closure rule is pure domain. Schema compilation stays an infra port that the rule is given. The application ring never reaches `infra` (ADR-0006). | Accepted |
| C-004 | Out of scope: cross-pack reference resolution (items 2 and 3), a served validate operation or CLI (#149, #38, #478), the guard runner (#340), the trust policy (#157), the CI 429 fix. | Accepted |

## Key Entities

- **Closure finding**: an artifact's file, its URN and the reason it does not close, worded as the load records it.
- **Closure subject**: what the rule judges. The pack's candidate assets with their `executable`, its asset payload
  paths, its guards, its lifecycles, and its mission types with their own templates.

## Domain Language

| Term | Meaning | Avoid |
|------|---------|-------|
| closure | an artifact's references resolve within the pack | "link check" |
| PACK ruleset | `PackRulesets.content()`, #554's named composition | "PackClosure beside it" |
| per-file write | a registration or a delete through the commit store | "partial write" |

## Success Criteria *(mandatory)*

- **SC-001**: For every row of the symmetry table, all four paths judge the broken fixture, and all four accept the
  repaired control. — [build] · no-op passable: no
- **SC-002**: The existing load and round-trip tests pass unchanged. — [ratchet] · no-op passable: yes, paired with
  SC-001
- **SC-003**: The rewritten starvation test, made to starve one write on purpose, fails with that write's name and
  round. — [build] · no-op passable: no
- **SC-004**: #355 and #593 are closed by the pull request with evidence, and epic #142 is ticked. — [build] · no-op
  passable: no

## Assumptions

- #554 already routes the assembly's store-only faults into validate mode, so the `Executable` and `Lifecycle`
  invariant breaches reach validate mode through the shared assembly. This mission keeps that path and moves the
  closure into the PACK ruleset.
- The closure finding keeps the code the load records today (`artifact_schema_error`), so the load is unchanged and
  validate mode names the same code. No new code is minted.
- No registration service for guard, lifecycle, asset, mission type or template exists yet (item 13). The per-file
  check is therefore proven at the commit store's choke point, which every per-file write goes through.
