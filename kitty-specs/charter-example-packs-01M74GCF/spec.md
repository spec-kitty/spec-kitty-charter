# Mission Specification: Charter example packs replace the open-packs corpus

**Mission Branch**: `claude/adr-open-packs-456-458-adf2fo` (PR #474, single branch)
**Created**: 2026-11-11
**Status**: Draft
**Input**: Implement ADR-0070 (accepted): charter's tests read only charter-owned example packs instead of the pinned
spec-kitty-open-packs checkout, so a contract change (PR #474) reaches green without waiting for open-packs.
Plan: `docs/plans/charter-example-packs.md`.

## Intent Summary (confirmed by the operator, 2026-11-11)

- **Primary actor**: a charter contributor who changes the contract or the pack model.
- **Trigger**: a pull request that changes a pack shape (as PR #474 does with ADR-0067 to ADR-0069).
- **Outcome**: the pull request's required checks prove the change on example packs that change in the same commit,
  and pass without any open-packs content.
- **Rule that always holds**: no required check reads open-packs content; round-trip fidelity (ADR-0036) is still a
  required gate, over the example packs.
- **Out of scope here**: the open-packs validation job (ADR-0070 decision 3) is filed as a follow-up issue in
  spec-kitty-open-packs (decision DM-01M74GCTE3RK2PW94VYTF54E96).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A contract change proves itself on example packs (Priority: P1)

A contributor changes a pack shape and updates the example packs in the same commit. The whole-pack round trip and
every pack-reading test run over the example packs and pass; nothing reads open-packs.

**Why this priority**: it is the reason for the mission; without it PR #474 cannot merge green.

**Independent Test**: on a checkout with no open-packs directory, the required checks pass.

**Acceptance Scenarios**:

1. **Given** the example packs in the canonical shape, **When** the whole-pack round trip runs, **Then** every file
   round-trips with its meaning and the expected-red lists are empty.
2. **Given** no open-packs checkout, **When** the required build and CI jobs run, **Then** none needs one and all pass.

### User Story 2 - A gap in the examples is caught (Priority: P2)

A contributor adds a schema property, an artifact kind or a raised refusal code with no example. The coverage check
fails and names what is missing.

**Independent Test**: remove one example field from a pack; the coverage check fails naming it; put it back; it passes.

**Acceptance Scenarios**:

1. **Given** a pack schema property no example sets, **When** the coverage check runs, **Then** it fails naming the
   schema and property.
2. **Given** every property, kind and raised code exampled, **When** it runs, **Then** it passes.

### User Story 3 - Drift with real content is reported, never blocking (Priority: P3)

Each night a job runs the round trip over open-packs `main`; a failure opens or updates an issue and blocks nothing.

**Independent Test**: the workflow is scheduled, not attached to pull requests, and its failure step files an issue.

### Edge Cases

- A test that asserted something about real content only (a digest of a real pack, a real manifest's hashes) gets the
  same assertion over an example pack, with the value derived from the example.
- Invalid shapes needed by error-handling tests stay in the `planted/` packs, never in the example packs.
- The governance sync of `packs/internal` and `packs/qa` keeps reading the pin.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Example packs | As a contributor, I want three charter-owned example packs (built-in, org-base, project) in the canonical shape covering every artifact kind, a three-level closure, policies, shards, an org fragment, step enhancements, prompt slots, mission types with lifecycles and guards, companions, binary payloads, a manifest, presets and a declared `generic` fallback, so that every rule has an example. | High | Open | [build] | no |
| FR-002 | Round trip over examples | As a contributor, I want the whole-pack round trip to run over the example packs with empty expected-red lists, as a required check, so that fidelity is proven on content charter owns. | High | Open | [build] | no |
| FR-003 | Coverage check | As a contributor, I want a test that fails when a pack schema property, an artifact kind or a raised refusal code has no example, so that the examples cannot thin out. | High | Open | [build] | no — paired with the all-covered control |
| FR-004 | Tests move | As a contributor, I want each of the 28 tests that read open-packs to read the example packs and assert the same outcome, so that no coverage is lost. | High | Open | [build] | no |
| FR-005 | No required open-packs input | As a contributor, I want no required task or CI job to read open-packs (the `openPacksRoundTrip` and `openPacksProfiles` tasks, the round-trip checkout and the `openPacksDir` test inputs removed), so that a contract change is not blocked by it. | High | Open | [build] | no |
| FR-006 | Nightly drift watch | As a maintainer, I want a scheduled, non-blocking job that runs the round trip over open-packs `main` and opens an issue on failure, so that drift is seen early. | Medium | Open | [build] | no |
| FR-007 | Governance sync stays | As a maintainer, I want `syncPacks` and `openPacksRef` kept for this repository's own governance only, so that its org layer still works. | Medium | Open | [ratchet] | yes — paired with a check that `syncPacks -PcheckPacks=true` still passes |
| FR-008 | Governance amended | As the operator, I want the charter companion's "Gates before merge" and PROJECT_002 amended as ADR-0070 says, so that the rules match the gates. | Medium | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | PR #474 green | Every required check on PR #474 passes on its final commit (0 failing required checks). | Reliability | High | Open |
| NFR-002 | Example size | The three example packs together hold at most 150 files, each kind exampled at least once. | Maintainability | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Branch | The work lands on `claude/adr-open-packs-456-458-adf2fo` (PR #474). | Process | High | Open |
| C-002 | Canonical only | Example packs carry only canonical shapes; invalid shapes stay in error-handling fixtures. | Technical | High | Open |
| C-003 | No production change | Tests, fixtures, build, CI and governance only; no production behaviour changes. | Technical | High | Open |

### Key Entities

- **Example pack**: a charter-owned pack under the test fixtures, read by every pack-reading test.
- **Coverage check**: the test relating schemas, kinds and refusal codes to the examples.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A pull request that changes a pack shape and its examples passes every required check with no open-packs
  content present — [build] · no-op passable: no
- **SC-002**: 0 required checks read open-packs content — [build] · no-op passable: no
- **SC-003**: Every pack schema property, artifact kind and raised refusal code has at least one example — [build] ·
  no-op passable: no

## Assumptions

- The open-packs validation job is a follow-up issue in spec-kitty-open-packs (operator, decision above).
- `charter.yaml` is regenerated with the installed CLI (`spec-kitty charter generate`).
