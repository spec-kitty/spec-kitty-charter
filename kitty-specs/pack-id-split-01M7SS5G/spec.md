# Mission Specification: Pack id split: the registered pack id and the descriptor ULID

**Mission Branch**: `kitty/mission-pack-id-split-01M7SS5G` (lands on `feat/583-pack-id-split`, then `main`)
**Created**: 2026-11-19
**Status**: Draft
**Input**: Charter #583 (P1, epic #549; ADR-0075 decision 2, #552 Q3 ruling b). It lands after #554, which was merged by #594.
The issue body is the brief. The orchestrator rules the open decisions of the census (`research/census.md`).

## Intent Summary

Brief-intake mode: #583's body states the outcome and its done-when list. The open design choices were ruled by the
operator through the orchestrator on 2026-11-19, all as recommended (D1 to D9, `research/decisions.md`).

- **Primary actor**: a service operator, whose pack domain on disk the service reads and serves. A client
  reads and writes packs over HTTP.
- **Trigger**: a value that is not a pack id reaches a place that holds a pack id. Examples: a directory seeded on
  disk as `a--b`, a `.pack-commit-*` staging name, a descriptor ULID, or a descriptor `name`.
- **Outcome**: a registered pack id and a descriptor identity are two typed values. Each place names the one it holds.
  A stored directory whose name is not a pack id is refused on read with a named code, as every write already refuses it.
- **Invariant**: every `PackIdentifier` is a `slug(63)`. Every descriptor identity is a ULID. No code converts one into
  the other by rewrapping its text.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A stored directory that is not a pack id is not served (Priority: P1)

An operator's pack domain holds a directory named `a--b`, seeded by hand. The service does not list it, configure it or
serve it. It logs the refusal with the code `pack_id_invalid` and the directory's name. Every other pack is served as
before.

**Why this priority**: today the read path serves what every write path refuses. This is the defect the issue records.

**Independent Test**: seed a domain with `a--b` and one valid pack, start the read side, and check the configured
list, the offering and the log.

**Acceptance Scenarios**:

1. **Given** a domain holding `a--b` and `good-pack`, **When** the service lists its configured packs, **Then** it lists
   `good-pack` only and logs `pack_id_invalid` naming `a--b`.
2. **Given** the same domain, **When** a client reads `good-pack`, **Then** the read answers as before.
3. **Given** a domain holding only valid pack ids, **When** the service lists them, **Then** none is refused (positive
   control on the same fixture builder).

### User Story 2 - The pack id type admits only pack ids (Priority: P1)

Any code that builds a `PackIdentifier` from text that is not a kebab slug of at most 63 characters is refused at
construction. The descriptor's `pack_id` travels as its own type, `PackUlid`, which admits only a ULID.

**Why this priority**: the guard is what makes the split hold. Without it, the next site mixes the identities again.

**Independent Test**: unit tests of both kernel types, and a full build with every test passing.

**Acceptance Scenarios**:

1. **Given** `a--b`, `A`, `my_pack`, a 64-character slug or a ULID, **When** a `PackIdentifier` is built, **Then** it is
   refused with the pack id grammar's detail.
2. **Given** `my-pack` or a 63-character slug, **When** a `PackIdentifier` is built, **Then** it is accepted.
3. **Given** `01PACK`, a 25- or 27-character text, or text with `I`, `L`, `O` or `U`, **When** a `PackUlid` is built,
   **Then** it is refused. **Given** a valid ULID, **Then** it is accepted.

### User Story 3 - Each index names the identity it keys by (Priority: P1)

The edge index and the closure views key packs by `PackUlid`. Artifacts carry the registered `PackIdentifier`, as the
contract's `Artifact.packId` states ("as the request named it"). Staging and aside directories are named by a
storage-local directory type, never by a pack id. A dropped enhancement names its pack as a `PackIdentifier`. A new staged pack in
the inbound-reference check is named by the id it is uploaded under.

**Why this priority**: these sites are why the constructor could check only for blank text.

**Independent Test**: the existing closure, edge, inbound-reference and registration suites pass unchanged in their
expectations. A refusal for a newly uploaded pack names its upload id.

**Acceptance Scenarios**:

1. **Given** a closure where two packs hold one URN, **When** the effective read resolves it, **Then** the answer is the
   one the suite expects today.
2. **Given** a new pack upload whose removal breaks a reference, **When** the inbound-reference check refuses it,
   **Then** the refusal's pack is the upload's registered id, not the descriptor `name`.
3. **Given** a profile enhancement that drops a field, **When** it is refused, **Then** the refusal names the
   enhancing pack's registered id, as today.

### Edge Cases

- An aside copy `<id>.pack-old-<n>` is read during a swap. It is read through the directory type and assembled under
  its registered id.
- A descriptor whose `pack_id` is not a ULID is already refused by the schema read. It is not a new path.
- A path parameter that is not a pack id never reaches a controller: the contract's `PackId` pattern answers 400.
- Validate mode reads a directory that has no registration. It assembles under the id the caller passes. Without one,
  it uses the directory name, refused with `pack_id_invalid` when that is not a pack id (D2b).
- An activation pin naming `my_pack` is refused at the contract (D7). Today it fails later, as an unconfigured pack.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Pack id guarded | As a maintainer, I want `PackIdentifier`'s constructor to refuse any text that is not `slug(63)` so that no non-pack-id travels as one. | High | Open | [build] | no — `a--b` is accepted today |
| FR-002 | Descriptor identity type | As a maintainer, I want a kernel `PackUlid` that admits only a ULID so that the descriptor's `pack_id` has its own type. | High | Open | [build] | no — the type does not exist |
| FR-003 | Indexes key by `PackUlid` | As a maintainer, I want the edge index, closure view and closure session to key packs by `PackUlid` so that the indexes never hold a ULID as a pack id. | High | Open | [build] | no — the ULID sites fail FR-001's guard |
| FR-004 | Artifacts carry the registered id | As a client, I want every artifact's pack to be the registered id on every read path so that `Artifact.packId` matches the request. | High | Open | [build] | no — StoreRead.of and ArtifactFiles assemble under the ULID today |
| FR-005 | One locator per identity | As a maintainer, I want the pack locator to take the registered id only, with a separate lookup by `PackUlid`, so that there is no dual-identity reader. | High | Open | [build] | no — the fallback exists today |
| FR-006 | Storage directories named | As a maintainer, I want staging, aside and raw directory names to travel as a storage-local directory type so that none poses as a pack id. | High | Open | [build] | no — staging names fail FR-001's guard |
| FR-007 | Dropped pack typed | As a maintainer, I want a dropped or enhancing profile pack to be a `PackIdentifier` so that refusals do not rewrap text. | Medium | Open | [build] | no — the fields are String today |
| FR-008 | New staged pack named by its upload id | As a client, I want an inbound-reference refusal for a new pack to name its upload id so that the pointer addresses the pack. | Medium | Open | [build] | no — it names the descriptor `name` today |
| FR-009 | Refuse on read | As an operator, I want a stored directory whose name is not a pack id to be refused on read with `pack_id_invalid` (logged, not listed, configured or served) so that read and write agree. | High | Open | [build] | no — `a--b` is served today; paired with a valid pack on the same fixture |
| FR-010 | Fixtures use the type that fits | As a maintainer, I want every test fixture to build a `PackIdentifier` from a pack id and a `PackUlid` from a real ULID so that tests carry the canonical shape. | High | Open | [build] | no — FR-001 and FR-002 fail ~40 fixtures |
| FR-011 | Activation pin grammar | As a client, I want an activation pin's `packId` to follow the pack id grammar so that a pin can only name a registrable pack (D7). | Medium | Open | [build] | no — `my_pack` is accepted today |
| FR-012 | PackId wording | As a client reader, I want the `PackId` parameter to describe the registry name, not "as the pack declares it" (D8). | Low | Open | [build] | yes — docs only; checked by review |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Gate green | `./gradlew verifyAll` on Java 25 passes with 0 failures, the whole-pack round trip included. | Reliability | High | Open |
| NFR-002 | No wire drift | The contract scenarios and golden snapshots pass unchanged, apart from the changes FR-008 and FR-011 list in a `### Breaking` or `### Changed` CHANGELOG fragment. | Compatibility | High | Open |
| NFR-003 | No mixed construction | Zero production sites build a `PackIdentifier` from a descriptor `pack_id`, descriptor `name` or staging name. A grep in the review checklist shows 0 hits. | Maintainability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Canonical model | No deprecation window and no legacy reader beside the new one (charter, 2026-11-10). | Technical | High | Open |
| C-002 | No #556 work | #556 follows sequentially; its scope is not taken here. | Business | High | Open |
| C-003 | Rebase on siblings | #517 (PR #609), #602 (PR #606) and #478 (PR #608) are in flight. Their sessions are told about renamed APIs. | Technical | High | Open |
| C-004 | ArchUnit unchanged | No ArchUnit rule is widened. `PackUlid` lives in `kernel.domain`, and the directory type is storage-local. | Technical | High | Open |

### Key Entities

- **PackIdentifier**: the registered pack id: the name a pack is registered, stored and addressed under. A `slug(63)`.
- **PackUlid**: the descriptor's `pack_id`, the pack's immutable identity across registrations. A ULID.
- **Pack directory**: a name of one directory under the pack domain. It may be a pack id, a staging name or an aside
  name, and it is storage-local.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A seeded `a--b` directory is absent from the configured list, and one `pack_id_invalid` log line names it.
  — [build] · no-op passable: no
- **SC-002**: 0 production constructions of `PackIdentifier` from a descriptor or staging value (NFR-003). — [build] ·
  no-op passable: no
- **SC-003**: The full gate passes with 0 failed checks. — [ratchet] · no-op passable: yes (paired with SC-001 and
  SC-002)

## Assumptions

- The descriptor schema read already refuses a `pack_id` that is not a ULID, so `PackUlid`'s guard adds no new refusal
  on a valid pack.
- The operator ruled D1 to D9 as recommended on 2026-11-19 (`research/decisions.md`).
