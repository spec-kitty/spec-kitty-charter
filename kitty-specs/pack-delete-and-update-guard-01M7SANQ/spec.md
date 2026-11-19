# Mission Specification: Pack uninstall and update guard (#517)

**Mission Branch**: `feat/517-pack-delete-and-update-guard`
**Created**: 2026-11-19
**Status**: Draft
**Input**: Issue [#517](https://github.com/spec-kitty/spec-kitty-charter/issues/517) (P1, epic #143, milestone
Post-cutover), re-scoped 2026-11-17 to the service's part of the external org-pack lifecycle.

## Intent Summary (operator rulings, 2026-11-19)

The brief is the issue body; the open decisions were put to the operator through the orchestrating session and ruled on
2026-11-19. Each ruling is a resolved Decision Moment under `decisions/`.

- **Actor.** A client that installs, updates and uninstalls external org packs through the service (core's
  lifecycle, at incorporation; today, any client of the contract).
- **Trigger.** It wants to remove a registered pack, to know what a removal or a replacement would do before doing
  it, or to be sure a replacement moves forward.
- **Outcome.** `DELETE /packs/{packId}` removes a pack whole or changes nothing; `?dryRun=true` on the delete and on
  both registrations answers what the write would do without writing; a replacement must carry a newer `pack_version`
  unless it is byte-identical.
- **Invariants.**
  - A pack another registered pack depends on, or that an activation names, is never removed; the refusal names
    every dependent.
  - A dry run and the real write share one code path and answer the same outcome and refusals.
  - A `pack_version` names one content forever (ADR-0065 D9 label reuse stays), and a replacement may only move it
    forward by PEP 440 precedence (reverses ADR-0065 D2 / ruling B1, acknowledged by the operator).

| # | Ruling | DM |
|---|--------|----|
| D1 | Build now behind a `PackDependents` port over the existing closure finders; #556's `ImpactCheck` absorbs it later | `01M7SBC5YZMB2Y33SVZZGBNY56` |
| D2 | "Newer" is PEP 440 public-version precedence (kernel `Pep440`); older-or-equal with other bytes reuses `409 pack_version_conflict` with new detail; same bytes is `unchanged` | `01M7SBCA63DZ78JHTFWR0ESFYR` |
| D4 | The answer reports `previousVersion` and the new version; `If-Match` stays the from-guard; no new request header | `01M7SBCE4KP5BYNBJ1FXV5PXTX` |
| D5 | `?dryRun=true` on `DELETE /packs/{packId}`, `PUT /packs/{packId}` and `POST /packs`, one code path | `01M7SBCJF9M5R61EAXPS36Z80R` |
| D6 | Only an activation whose `packs` list names the pack is a dependent; every other closure holding it is composed strictly (422) | `01M7SBCPR3DSGJ80S3H8F8PPPR` |
| D7 | New `409 pack_has_dependents`, its violations naming each dependent | `01M7SBCTZ9198XCJAYBT57H10M` |
| D8 | Delete: synchronous; `If-Match` required (428, 412, `*` allowed); 404 `pack_not_found`; 409 `registration_conflict` for built-in or read-only; 503 while registration is off; one swap under both locks | `01M7SBCZ8D3EFNH0S44RBJ3MBP` |
| D9 | The version history survives a pack delete, for the label-reuse check only | `01M7SBD3AH3M4XJM6NK2G5WK3Q` |
| D10 | New ADR-0081 amending ADR-0065 D2, D4, D9; DIV-602 updated; a `### Breaking` change fragment | `01M7SBD7K44SZS8897GHWN3DA3` |

D3 (a non-PEP 440 version) was withdrawn: the read already refuses it with `pack_version_invalid`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Uninstall a pack nothing uses (Priority: P1)

An operator removes an org pack that no other pack depends on and no activation names.

**Why this priority**: There is no way to remove a pack today; this is the core of the issue.

**Independent Test**: Register a leaf example pack, `DELETE` it with its `ETag`, then `GET /packs/{packId}` is 404.

**Acceptance Scenarios**:

1. **Given** a registered org pack nothing depends on, **When** the client sends `DELETE /packs/{packId}` with
   `If-Match` its revision (or `*`), **Then** the answer is `204`, the pack directory is gone, and a read is
   `404 pack_not_found`.
2. **Given** that pack was deleted, **When** the client sends the same `DELETE` again, **Then** the answer is
   `404 pack_not_found` and the store is unchanged.
3. **Given** a registered pack, **When** the `DELETE` carries no `If-Match`, **Then** `428`; with a stale one, `412`;
   nothing is removed.

### User Story 2 - A delete that would break something is refused, naming why (Priority: P1)

**Why this priority**: Removing a depended-on pack silently breaks every closure above it.

**Independent Test**: Register two example packs where one `depends_on` the other; delete the depended-on one.

**Acceptance Scenarios**:

1. **Given** pack B declares `depends_on` A (or `parent_pack` A), **When** A is deleted, **Then**
   `409 pack_has_dependents` with a violation naming B, and A is unchanged.
2. **Given** a stored activation whose `packs` list names A, **When** A is deleted, **Then**
   `409 pack_has_dependents` with a violation naming that activation.
3. **Given** an artifact of another pack refers to an artifact of A, **When** A is deleted, **Then** the same `409`,
   with a violation naming the referring pack and artifact.
4. **Given** several dependents of different kinds, **When** A is deleted, **Then** every dependent is named, in a
   stable order, in one refusal.
5. **Given** no named dependent, but a closure that holds A (an activation with no `packs` list) no longer composes
   without it, **When** A is deleted, **Then** `422` with the composition code, naming the closure; nothing written.

### User Story 3 - An update must move forward (Priority: P1)

**Independent Test**: Register an example pack at 1.1.0, then `PUT` other bytes at 1.0.0 and at 1.1.0.

**Acceptance Scenarios**:

1. **Given** a pack stored at 1.1.0, **When** a replacement at 1.2.0 is registered, **Then** it succeeds `replaced`,
   and the result reports `version` 1.2.0 and `previousVersion` 1.1.0.
2. **Given** a pack stored at 1.1.0, **When** a replacement with other bytes at 1.0.0 is registered, **Then** the job
   is refused `409 pack_version_conflict`, whose detail says the version is not newer than the stored one.
3. **Given** a pack stored at 1.1.0, **When** other bytes at 1.1.0 are registered, **Then** `409
   pack_version_conflict` (as today).
4. **Given** a pack stored at 1.1.0, **When** the identical archive is registered again, **Then** `succeeded`,
   outcome `unchanged`.
5. **Given** a pack stored at `1.1.0+a`, **When** other bytes at `1.1.0+b` are registered, **Then** `409
   pack_version_conflict` (a local segment does not order).
6. The same holds for `POST /packs` (registration from a source).

### User Story 4 - Know before you write (Priority: P2)

**Independent Test**: For each case of stories 1 to 3, send it with `?dryRun=true` and compare with the real write.

**Acceptance Scenarios**:

1. **Given** any state, **When** `DELETE /packs/{packId}?dryRun=true` is sent, **Then** the answer is `200` with a
   `PackImpact` that reports the outcome the real delete would have (`deleted`) or the same refusal it would answer
   (same status and code, same violations), and the store and the version history are unchanged.
2. **Given** any state, **When** `PUT /packs/{packId}?dryRun=true` (or `POST /packs?dryRun=true`) is sent, **Then**
   the job ends as the real registration would (`succeeded` with the outcome it would have, or `refused` with the same
   problem), its result says it is a dry run, and nothing is written: no pack, no history entry, no audit entry of a
   write.
3. **Given** the preconditions the real write checks synchronously (428, 412, 404, 503), **Then** the dry run answers
   them the same way.

### Edge Cases

- A delete of a built-in pack, or of a pack configured in a read-only location: `409 registration_conflict`.
- A delete addressed by the declared `pack_id` instead of the configured name: resolved as every pack path resolves it.
- A delete while the registry lock is busy: `503` with `Retry-After`, as the other writes.
- A stored pack whose own `pack.yaml` cannot be read: its `pack_id` is unknown, so what builds on it cannot be judged;
  the delete is refused `500 pack_unreadable`, and the pack is first replaced with a readable one (fail closed,
  implementation call I3). A pack whose descriptor reads but whose artifacts do not is judged from what the salvage read
  finds, as for a replace.
- Another configured pack whose descriptor cannot be read: it cannot be judged, so the delete is refused
  `500 pack_unreadable`, naming it (fail closed).
- A delete of a pack whose only dependent is itself (a self-reference): not a dependent.
- Re-install after a delete: any version the history does not hold with other bytes (D9); there is no stored
  version to be newer than.
- A replace that changes nothing but the version label backwards (same bytes, older label): the bytes differ (the
  descriptor is in the revision), so it is refused.
- A dry run under `If-None-Match: *` against a stored pack: `412`, as the real write.

## Domain Language

- **Dependent** (of a pack A): a registered pack whose `depends_on` or `parent_pack` names A; a stored activation
  whose `packs` list names A; an artifact of another pack that refers to an artifact of A (inbound reference).
- **Uninstall**: `DELETE /packs/{packId}`, the removal of a registered pack whole.
- **Dry run**: a write request with `dryRun=true`, judged exactly as the write and never committed.
- **Newer**: greater by PEP 440 public-version precedence.
- **Previous version**: the `pack_version` of the stored pack a registration replaced.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status |
|----|-------------|--------|
| FR-001 | `DELETE /packs/{packId}` removes a registered org or project pack whole and answers `204` with no body. | Draft |
| FR-002 | The delete requires `If-Match` (absent `428`, stale `412`, `*` accepted), checked again under the locks. | Draft |
| FR-003 | An identifier that names no configured pack is `404 pack_not_found`; a built-in pack or a pack in a read-only location is `409 registration_conflict`; while `charter.registration.enabled` is off the answer is `503 pack_source_unavailable` before anything else is judged. | Draft |
| FR-004 | A delete with any dependent is refused `409 pack_has_dependents`. Every dependent is found: each pack whose `depends_on` or `parent_pack` names it, each activation whose `packs` lists it, and each inbound artifact referrer. The `packs` member names the dependent packs, and the violations name the dependents in a stable order, at most 100, with the detail counting the rest. Nothing is written. | Draft |
| FR-005 | With no dependent, the delete composes every other closure that holds the pack without it, strictly; one that does not compose or breaks a relation rule is refused `422` with its code, naming the closure; the activations gate (`requireComposable`) runs as on every write. | Draft |
| FR-006 | The removal is one swap under the registry write lock then the pack's lock: a concurrent read sees the pack or no pack, and a failure leaves the pack whole. | Draft |
| FR-007 | The version history of the name survives a delete and is used only for the label-reuse check (ADR-0065 D9). | Draft |
| FR-008 | A whole-pack registration (`PUT /packs/{packId}`, `POST /packs`) that replaces a stored pack with other bytes is refused `409 pack_version_conflict` unless its `pack_version` is newer than the stored one by PEP 440 precedence; the detail names both versions. | Draft |
| FR-009 | A registration of the stored bytes is `unchanged`, whatever the version rule says (as today). | Draft |
| FR-010 | A registration that replaces a pack reports `previousVersion` beside `version` in its `PackRegistration` result. | Draft |
| FR-011 | `?dryRun=true` on `DELETE /packs/{packId}` answers `200` with a `PackImpact` body: the outcome the delete would have, or the problem it would answer, and the dependents and notices it found; nothing is written. | Draft |
| FR-012 | `?dryRun=true` on `PUT /packs/{packId}` and `POST /packs` runs the registration job through every check and ends `succeeded` with the outcome it would have (result flagged as a dry run) or `refused` with the same problem; nothing is written: no pack, no history entry. | Draft |
| FR-013 | The dry run and the write run the same code; only the commit is switched. | Draft |
| FR-014 | The dependents check is a `PackDependents` port in `packs.application`, implemented over `ClosureRoots`, `ActivationImpact` and `InboundReferences`, so #556's `ImpactCheck` can implement or absorb it. | Draft |
| FR-015 | `@contract` scenarios cover each case of User Stories 1 to 4 on charter's own example packs (ADR-0070) and pass the ADR-0059 D1 gate. | Draft |

### Non-Functional Requirements

| ID | Requirement | Status |
|----|-------------|--------|
| NFR-001 | A delete reads each stored pack's descriptor at most once (one `StagedPackViews` session), as the registration does (NFR-009 of the registration mission). | Draft |
| NFR-002 | A dry run leaves the file system byte-identical, including the history file and the audit log's write entries. | Draft |

### Constraints

| ID | Constraint | Status |
|----|------------|--------|
| C-001 | The charter's model is canonical: the old "label, not an order" rule is replaced outright; no compatibility switch. | Draft |
| C-002 | `packs` declares the ports and `activations.infra` implements them; the ArchUnit gate holds. | Draft |
| C-003 | No rule, fault collection or parity test of #556 is added here. | Draft |
| C-004 | The contract change is recorded as a `### Breaking` change fragment (ADR-0022, 0.x). | Draft |
| C-005 | Tests and examples carry only the canonical shape, except error-handling tests. | Draft |
| C-006 | Core's lifecycle (config and snapshot ownership, CLI rollback, CLI names) is out of scope (issue-tracker Rule 5). | Draft |

### Key Entities

- **PackImpact** (new schema): `packId`, `operation` (`delete`), `outcome` (`deleted` or `refused`), `version`,
  `revision`, `notices`, `problem` (when refused: the delete's own refusal, whose `packs` and `violations` name the
  dependents), `_links`.
- **PackRegistration** (changed): `previousVersion` (optional), `dryRun` (optional, true only on a dry run).
- **CharterRefusalCode** (changed): `pack_has_dependents` (409).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every acceptance criterion of #517 maps to at least one passing `@contract` scenario.
- **SC-002**: For every scenario of stories 1 to 3, its `dryRun=true` twin reports the same outcome or refusal and the
  store's bytes are unchanged afterwards.
- **SC-003**: `./gradlew verifyAll` is green on Java 25, the whole-pack round trip included.

## Assumptions

- Activations name packs by configured name; an activation document that cannot be read has no root set and so names
  no dependent (as `ActivationImpact.activationRoots` already treats it).
- A registered pack lives in the writable root; deleting it removes its directory, which also removes it from the
  configured set (discovery by directory).
