# Mission Specification: Pack item ETags and resolver retirement

**Mission Branch**: `claude/feat-487-488-pack-item-etags`
**Created**: 2026-11-11
**Status**: Draft
**Input**: Issues #487 and #488; operator rulings of 2026-11-11 recorded in `research.md`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One rule for a pack read of one thing (Priority: P1)

A client of the charter service reads one artifact, mission step or mission skeleton from a pack and receives the pack
revision it was read at, whatever the kind. Today a tactic, a mission step contract, a mission type, a step and a
skeleton answer without it, so the client needs a per-kind rule to know whether it can name the state it read.

**Why this priority**: it is the operator's ruling on #487 and the visible part of the mission.

**Independent Test**: read a tactic, a mission step contract, a mission type, a step and a skeleton from one pack, and
compare each answer's revision with the revision a directive read of the same pack answers.

**Acceptance Scenarios**:

1. **Given** a pack holding a tactic, **When** the client reads the tactic, **Then** the answer carries the pack
   revision, equal to the revision a directive read of the same pack answers in the same state.
2. **Given** a pack holding a mission type with a step, a skeleton and a step contract, **When** the client reads each,
   **Then** each answer carries that pack's revision.
3. **Given** any of these reads, **When** the client sends a conditional-read header, **Then** the service answers the
   full read as today: no "not modified" answer.
4. **Given** a file of the pack changes, **When** the client reads the tactic again, **Then** the revision differs.
5. **Given** the pack does not change, **When** the client reads the same item twice, **Then** both answers carry the
   same revision.
6. **Given** a mission step whose content resolves from a dependency pack, **When** the client reads it, **Then** its
   revision is the requested pack's revision.

### User Story 2 - The contract says what the revision means (Priority: P2)

A client author learns from the contract that the revision of a mission type, step or skeleton names the pack it was
read from, and is a concurrency token, not a cache validator for content resolved from other packs.

**Why this priority**: without it a client may cache a mission type whose dependency changed.

**Independent Test**: the contract's descriptions of these reads state the meaning; no contract text still says these
reads carry no revision.

**Acceptance Scenarios**:

1. **Given** the published contract, **When** a client author reads the mission type, step and skeleton reads, **Then**
   each states that its revision names the pack and is not a cache validator for content from other packs.
2. **Given** the published contract, **When** it is searched for statements that a tactic, step contract, mission type,
   step or skeleton read carries no revision, **Then** none remain.

### User Story 3 - The service has one walk path (Priority: P3)

A maintainer reads the service's graph-walk code and finds only the path that answers requests. The unused
per-kind resolution path, and the comment that says procedures are not walked, are gone.

**Why this priority**: maintainability; no client-visible change.

**Independent Test**: the reads that check whether a pack of the closure holds a referenced artifact answer exactly
as before.

**Acceptance Scenarios**:

1. **Given** the references read of an artifact whose references are held, unheld and cross-pack, **When** it is read
   after the change, **Then** every reference's holder is what it was before.
2. **Given** a relationship registration whose target is held by a dependency pack, **When** it is checked, **Then** it
   is accepted or refused exactly as before.

### Edge Cases

- A refused read (pack not found, artifact not found, stale input, unreadable pack) carries no revision, as today.
- Inputs that change while the read is answered refuse it as stale; a revision is never answered beside a body read
  from a different state.
- Reads under an activation keep their own tags; the pack revision never appears on an activation answer, and an
  activation read never computes a pack revision (it cannot be refused for one).
- A `HEAD` request answers the same revision as the `GET`.
- `If-Match` or `If-None-Match` sent to one of these pack reads is ignored: the full read is answered.
- The agent profile pack read, which already carries the revision and refuses `If-None-Match`, is unchanged.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Tactic item carries the revision | As a client, I want a tactic read from a pack to carry the pack revision so that I can name the state I read. | High | Open | build | no |
| FR-002 | Step contract item carries the revision | As a client, I want a mission step contract read from a pack to carry the pack revision. | High | Open | build | no |
| FR-003 | Mission type, step and skeleton carry the revision | As a client, I want a mission type, mission step and mission skeleton read from a pack to carry the pack revision. | High | Open | build | no |
| FR-004 | Revision and body agree | As a client, I want the revision and the body of one read to come from one consistent read, so that inputs changing during the read refuse it as stale with no revision, on a tactic read and on a mission-type read. | High | Open | build | no |
| FR-005 | Meaning stated in the contract | As a client author, I want the contract to state that the mission type, step and skeleton revision is a concurrency token naming the pack, not a cache validator. | Medium | Open | build | no |
| FR-006 | No "carries no revision" statement remains | As a client author, I want every contract text, scenario, published service document (`docs/api/service-contract.md`), service javadoc and ADR that says these reads carry no revision to be updated, and the change note to name the withdrawn statements. | Medium | Open | build | no |
| FR-007 | Unused walk path retired | As a maintainer, I want `CompositeComponentResolver.forPack`, `Composed.inPack`, `resolve` and its per-kind read-service dependencies removed, keeping `over`, `holder` and `holderIn`, and the "procedure: served, not walked yet" comment corrected. | Medium | Open | build | no |
| FR-008 | Holder checks unchanged | As a client, I want the references read and the relationship target check to answer exactly as before the retirement. | High | Open | ratchet | yes |

FR-008 is a ratchet: it pins existing answers, so its positive control is the existing references and relationship
scenarios, which must stay green unchanged.

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Additive for clients | No existing response field or status changes; the only wire change is an added header on 5 reads (4 operations). | Compatibility | High | Open |
| NFR-002 | One extra pack digest at most | Each affected read computes the pack revision at most once per request, verified by a unit test that counts revision reads for one tactic item read. | Performance | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | No conditional reads | No pack-scoped read starts honouring a conditional-read header or answering "not modified". | Technical | High | Open |
| C-002 | Activation reads unchanged | Reads under an activation keep their own tags and answers. | Technical | High | Open |
| C-003 | Canonical shape only | Tests and examples use only the canonical shape; no field is excluded from a parity or round-trip check. | Technical | High | Open |
| C-004 | Branch | Work lands on `claude/feat-487-488-pack-item-etags`, cut from PR #486's branch and rebased onto `main` when #486 merges. | Process | High | Open |

### Key Entities

- **Pack revision**: the digest of a pack's files, the token a pack read answers; it changes when any file of the pack
  changes.
- **Pack item read**: a pack-scoped read of one artifact, mission step or mission skeleton.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 5 pack reads named in FR-001 to FR-003 answer the pack revision, and 0 of them answer "not modified". — build · no-op passable: no
- **SC-002**: 0 contract texts, examples or scenarios still state that these reads carry no revision. — build · no-op passable: no
- **SC-003**: The references and relationship-registration scenarios pass unchanged after the retirement. — ratchet · no-op passable: yes
- **SC-004**: 0 references remain to `CompositeComponentResolver.forPack`, `Composed.inPack` or `CompositeComponentResolver.resolve`, and `holder`/`holderIn` remain. — build · no-op passable: no

## Assumptions

- The pack revision's existing meaning (a digest of all of the pack's files) is kept; no per-item revision is introduced.
- The change note is filed under Added, as for procedures, and names the withdrawn "no ETag" statements; nothing is
  breaking under ADR-0022.
