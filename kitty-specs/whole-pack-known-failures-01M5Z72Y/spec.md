# Mission Specification: Whole-pack gate known-failure list

**Mission Branch**: `feat/whole-pack-known-failures`
**Created**: 2026-10-27
**Status**: Draft
**Input**: Issue #247: a self-expiring known-failure list for the whole-pack round-trip gate (ADR-0036 open question 1 and decision 5).

## Intent Summary (confirmed 2026-10-27)

A contributor runs the whole-pack round-trip gate and finds a pack file that fails because the pack's own data is
inconsistent. Under ADR-0036 decision 5 they file an issue in `spec-kitty-open-packs` and must not work around the
shape here. Until the pinned open-packs commit carries the fix, the contributor records the file on a **known-failure
list**: the gate then reports it as a known failure, counted on its own, and stays green. When the pin moves to a
commit where the file passes, the gate fails and says to remove the entry, so the list cannot outlive the fix.

Rule that always holds: every red file has exactly one owner (an expected-red entry, a known-failure entry, or none,
in which case the gate fails), and the known-failure list never records a service defect.

Decision `01M5Z73CH2336MGVKF64EJ444G` (resolved): an entry's issue is written exactly as
`spec-kitty/spec-kitty-open-packs#<number>`; there is one entry per file, and it pins where the file first differs.

## Domain Language

| Term | Meaning | Avoid |
|------|---------|-------|
| Known failure | A file of a modelled kind that fails the round trip because of a pack inconsistency tracked by an open-packs issue | "xfail", "skip", "allowlist" |
| Expected red | The existing baseline: a kind with no adapter yet, or a file of a modelled kind with a service-side loss tracked here | Using it for pack inconsistencies |
| Exclusion | A file the gate does not compare at all ("not modelled yet" or not pack content) | Using it for files that fail |
| Pin | `openPacksRef`, the open-packs commit the gate reads | "version" |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Record a pack inconsistency without turning the gate red (Priority: P1)

A contributor has filed an open-packs issue for a file that fails the round trip. They add one entry naming the file,
where it first differs, a one-line reason and the issue. The gate passes and reports the file as a known failure.

**Why this priority**: Without it the gate stays red until the pin moves, which blocks every sibling mission.

**Independent Test**: Run the gate's judgement over a pack where one listed file fails at its pinned location.

**Acceptance Scenarios**:

1. **Given** a file listed as a known failure, **When** it fails at the pinned location, **Then** the gate passes and the file's status is "known failure".
2. **Given** a failing file on no list, **When** the gate runs, **Then** the gate fails naming that file as unexpected red.
3. **Given** a listed file, **When** it now fails at a different location, **Then** the gate fails reporting the changed difference.

### User Story 2 - The list expires itself after the pin moves (Priority: P1)

The open-packs fix merges and the pin moves. The listed file now round-trips. The gate fails and tells the
contributor to remove the entry, naming the known-failure list.

**Why this priority**: This is what keeps the list from going stale (ADR-0036 open question 1).

**Independent Test**: Run the gate's judgement with a listed file that round-trips.

**Acceptance Scenarios**:

1. **Given** a listed file, **When** it round-trips, **Then** the gate fails with "unexpectedly green", the path and an instruction to remove it from the known-failure list.

### User Story 3 - The list cannot carry malformed or misplaced entries (Priority: P2)

**Acceptance Scenarios**:

1. **Given** an entry with no issue, or an issue not written as `spec-kitty/spec-kitty-open-packs#<number>` (for example `#12` or `TODO`), **When** the gate loads the list, **Then** the gate fails naming the entry.
2. **Given** an entry whose file is not in the pack, or is excluded, **When** the gate runs, **Then** the gate fails reporting a stale entry.
3. **Given** an entry for a file of a kind no adapter models, **When** the gate runs, **Then** the gate fails: such an entry could never expire.
4. **Given** a file listed both as expected red and as a known failure, **When** the gate runs, **Then** the gate fails reporting the double entry.
5. **Given** the same file listed twice, or an entry with an unknown field or a missing path, location or reason, **When** the gate loads the list, **Then** the gate fails naming the entry.

### User Story 4 - The report shows known failures on their own (Priority: P2)

**Acceptance Scenarios**:

1. **Given** a run with known failures, **When** the report is written, **Then** the totals line and the per-kind table each show a known-failure count beside the green, expected-red, excluded and red counts, and each known-failure row names its reason and issue.

### Edge Cases

- The checked-in list is empty: the gate behaves exactly as before, and the report shows a known-failure count of 0.
- The list file is missing from the test resources: the gate fails to load rather than passing vacuously.
- A refused file (the reader refuses it outright): the pinned location covers the whole refusal message, so a different refusal of the same file is a changed difference, not the known one.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Separate known-failure list | As a contributor, I want a known-failure list apart from the expected-red baseline and the exclusions, whose entries carry the file path, the location where it first differs, a one-line reason and the open-packs issue, so that pack inconsistencies are never mixed with service losses. | High | Open | [build] | no |
| FR-002 | Listed and failing passes | As a contributor, I want a listed file that fails at its pinned location reported as a known failure that does not fail the gate, so that a pending upstream fix does not block the build. | High | Open | [build] | no — a no-op leaves the file as unexpected red |
| FR-003 | Listed and passing fails | As a maintainer, I want a listed file that round-trips to fail the gate with an instruction to remove its entry from the known-failure list, so that the list expires itself after the pin moves. | High | Open | [build] | no — paired with FR-002 on the same fixture |
| FR-004 | Issue reference required | As a maintainer, I want an entry whose issue is missing or not written as `spec-kitty/spec-kitty-open-packs#<number>` to fail the gate, so that only tracked pack inconsistencies are listed. | High | Open | [build] | no — paired with a well-formed entry on the same fixture |
| FR-005 | Unlisted failure still fails | As a maintainer, I want a failing file on no list to keep failing the gate, so that the new list adds no blind spot. | High | Open | [ratchet] | yes — paired with FR-002 on the same fixture |
| FR-006 | Moved difference fails | As a maintainer, I want a listed file whose first difference moved (or whose refusal message changed) to fail the gate as a changed difference, so that an entry cannot hide a new loss. | High | Open | [build] | no |
| FR-007 | Misplaced entries fail | As a maintainer, I want an entry for a file not in the pack, an excluded file, a file of an unmodelled kind, or a file also listed as expected red to fail the gate, and a repeated or malformed entry to fail loading. | Medium | Open | [build] | no |
| FR-008 | Known-failure count in the report | As a reader of the gate report, I want known failures counted on their own in the totals line and the per-kind table, beside green, expected red, excluded and red, with each row naming its reason and issue. | Medium | Open | [build] | no |
| FR-009 | Workflow documented | As a contributor, I want the gate's docs to describe the workflow (file the open-packs issue per ADR-0036 decision 5, add the entry, remove it in the change that moves the pin) and a changelog entry announcing the list. | Medium | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Exact assertions | Each of the four acceptance cases of #247 has at least one test that asserts the exact problem code and path (or the exact status and an empty problem list), never only a count. | Test quality | High | Open |
| NFR-002 | Real list exercised in the default build | A test that runs in `./gradlew verifyAll` loads the checked-in known-failure list, so a malformed list fails the build even though the whole-pack gate runs outside it. | Reliability | High | Open |
| NFR-003 | Deterministic report | Two runs over the same pinned pack still produce byte-identical reports (0 differing bytes). | Reliability | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Additive only | Change only the gate's harness, report, test resources and docs; add to shared code, never rework it. No edits to `expected-red.yaml`, kind registrations or the HTTP contract. | Technical | High | Open |
| C-002 | Test bookkeeping, not product logic | No reader rule, normalization or exemption in production code (ADR-0036 ruling 3). | Architectural | High | Open |
| C-003 | Sibling missions | Wave 1 missions (#190–#193) edit the gate concurrently; rebase on `main` after any sibling merge and before review. | Process | High | Open |

### Key Entities

- **Known-failure entry**: path, first-difference location, reason, open-packs issue. One per file.
- **Known failure (status)**: a file's verdict in a gate run, beside green, expected red, excluded and red.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All four acceptance cases of #247 are covered by tests that fail when the behaviour is removed. — [build] · no-op passable: no
- **SC-002**: `./gradlew verifyAll` on Java 25 and `./gradlew :packs:wholePackRoundTrip` both pass with the checked-in (empty) list, and the report shows a known-failure count of 0. — [ratchet] · no-op passable: yes — paired with SC-001
- **SC-003**: 0 lines of `expected-red.yaml` and 0 production-code files change. — [ratchet] · no-op passable: yes — paired with SC-001

## Assumptions

- The list ships empty: no file at the current pin is a known pack inconsistency (the two current file-level reds, #235 and #236, are service defects and stay expected red).
- ADR-0036 needs no change; open question 1 is already answered.
