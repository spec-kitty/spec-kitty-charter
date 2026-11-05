# Mission Specification: Activation register and activated-pack closure

**Mission Branch**: `issue-360-activated-closure`
**Created**: 2026-11-05
**Status**: Draft
**Input**: User description: "Activation register and activated-pack closure (ADR-0055 steps 1–2): the activation
document storage and the nine activation writes reserved by #125 (#291), the `depends_on` facts per pinned pack
(#359) and the closure rooted at an activation's packs (#360)."

## Context

Core cannot ask the charter service what is in force for a project until the service stores that project's
activation and resolves packs from it ([ADR-0055](/adr/0055-cutover-sequencing-the-java-service-first/) D1). Today
every activation operation answers `501`, and the compose roots at every configured pack. This mission builds the
activation register and the activated-pack closure. The effective reads and the layer merge are the next mission.

Rulings this mission builds to: [ADR-0020](/adr/0020-offering-and-activations/) D2–D9,
[ADR-0013](/adr/0013-persistent-deactivation/) D2–D5,
[ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) D2, D4–D6, and the discovery decisions of
2026-11-05 recorded under `decisions/` (all nine writes; the `enhances` pull; the lineage pass-through; the closure
read; absent means none).

## User Scenarios & Testing *(mandatory)*

The actor in every story is **core's seam client**, writing and reading one project's activation on the service
on behalf of that project.

### User Story 1 - Record and read an activation document (Priority: P1)

Core sends a project's activation document. The service validates it, stores it in canonical form, and answers
with its revision. Core reads it back, replaces it under an optimistic-concurrency check, and deletes it.

**Why this priority**: Nothing else in the cutover works until the service holds the document (ADR-0020 D5).

**Independent Test**: Create a document, read it, replace it with `If-Match`, read it again, delete it. Each answer
carries the expected status, revision and `ETag`; a stale `If-Match` is refused.

**Acceptance Scenarios**:

1. **Given** a valid document with unsorted, duplicated `selected.directive` entries, **When** core creates it,
   **Then** the service answers `201`, the stored document lists them sorted and once, and `ETag` equals `revision`.
2. **Given** a stored document, **When** core `PUT`s the `config` it fetched unchanged, **Then** the service answers
   `200` with the same `ETag` (the round trip).
3. **Given** a stored document, **When** core replaces it with an `If-Match` that names an older revision, **Then**
   the service answers `412` and the stored document is unchanged.
4. **Given** a document naming an artifact id that no pack in its closure holds, **When** core creates it, **Then**
   the service answers `422 activation_invalid` naming the id.
5. **Given** a document that denies a locked artifact, **When** core creates it, **Then** the service answers `409
   artifact_locked` naming the owning pack, and nothing is stored.
6. **Given** a stored document, **When** core deletes it twice, **Then** the first answers `204` and the second `404
   activation_not_found`.
7. **Given** a stored document, **When** the service restarts, **Then** the document reads back byte-identical.

---

### User Story 2 - See the packs an activation builds on (Priority: P1)

Core reads which packs an activation actually composes: the `depends_on` closure rooted at the activation's packs,
in dependency order, with each pack's version and its `depends_on` facts.

**Why this priority**: The closure decides which artifacts the writes and the cascade can see (#360), and core needs
the dependency facts per pinned pack (#359).

**Independent Test**: Three available packs, alpha depending on beta, and gamma unrelated. An activation of alpha
reads back beta then alpha, each with its facts; gamma is absent.

**Acceptance Scenarios**:

1. **Given** the available packs alpha (depends on beta), beta and gamma, and an activation whose `packs` is alpha,
   **When** core reads the activation's packs, **Then** the answer lists beta then alpha, alpha's entry carries its
   `depends_on` facts, and gamma is absent.
2. **Given** an activation with no `packs` key, **When** core reads its packs, **Then** the answer is the closure of
   every configured pack.
3. **Given** an activation whose closure cannot compose (for example a dependency outside the declared range),
   **When** core reads its packs, **Then** the service refuses with the composition code that names the pack, as the
   offering does today.

---

### User Story 3 - Select and unselect one artifact, with cascade (Priority: P2)

Core selects one artifact. The service adds it to the document's selection and, when asked to cascade, also selects
what it reaches over the cascade relations, skipping and reporting anything denied or out of the cascade's kind
scope. Unselecting removes it.

**Why this priority**: Per-artifact selection is how a project changes what is in force between full replaces.

**Independent Test**: A fixture where directive D requires tactic T, T is enhanced by tactic E, and tactic X is
denied and reached from D. Selecting D with cascade selects D, T and E, reports X as skipped-deactivated, and E's
provenance names `enhances`.

**Acceptance Scenarios**:

1. **Given** the fixture above, **When** core selects D with cascade over every kind, **Then** `selected` gains D, T
   and E; `ActivationChange.applied` reports T via `requires` and E via `enhances`; X is in `skippedDeactivated`
   with its `requiredBy`.
2. **Given** the same fixture and a cascade scope of `directive` only, **When** core selects D, **Then** T and E are
   in `skippedByScope` and are not selected.
3. **Given** a profile P that specializes from parent Q, and Q requires directive R, **When** core selects P with
   cascade, **Then** P and R are selected and Q is not; when Q is denied, R is not reached and Q is reported in
   `skippedDeactivated`.
4. **Given** a selected artifact, **When** core unselects it, **Then** it leaves `selected` and the answer reports
   the change.
5. **Given** an absent `selected.directive`, **When** core selects one directive, **Then** the list holds exactly that
   directive (absent means none).

---

### User Story 4 - Deactivate and reactivate one artifact (Priority: P2)

Core deactivates an artifact: the service adds the id to the deny list, unselects it, and with cascade also
unselects what it alone kept in force, keeping anything another active artifact still reaches. Reactivating removes
the deny entry.

**Why this priority**: Persistent deactivation is ADR-0013's reason to exist; it must survive every other write.

**Independent Test**: Deactivate a directive with cascade where one of its requirements is shared with another
selected directive; the shared one stays selected and is reported as `skippedShared`.

**Acceptance Scenarios**:

1. **Given** directive D selected with T it alone reaches, and T2 that another selected directive also reaches,
   **When** core deactivates D with cascade, **Then** D is in `deactivated`, D and T leave `selected`, and T2 stays
   and is reported in `skippedShared`; only D gets a deny entry.
2. **Given** a locked artifact, **When** core deactivates it, **Then** the service answers `409 artifact_locked` and
   nothing changes.
3. **Given** a denied artifact, **When** core reactivates it, **Then** its deny entry is removed and, when the deny
   list empties, the key is omitted.

---

### User Story 5 - Apply a preset (Priority: P3)

Core applies a preset from a pack in the closure. Each key the preset lists replaces that key in the document;
`force` also clears the deny entries that match the preset's members.

**Why this priority**: Presets are how a project starts; they come after the verbs they are built from.

**Independent Test**: A preset listing `selected.directive` and `activatedKinds` replaces both keys and leaves
`selected.tactic` untouched.

**Acceptance Scenarios**:

1. **Given** a document with `selected.directive` [A] and `selected.tactic` [T], **When** core applies a preset
   listing `selected.directive` [B, C] and `activatedKinds` [directive], **Then** `selected.directive` is [B, C],
   `activatedKinds` is [directive], `selected.tactic` is still [T], and `applied` reports the two replaced keys.
2. **Given** a preset member that the document denies, **When** core applies it without `force`, **Then** the member
   is not in force and the deny entry stays; with `force` the deny entry is removed.
3. **Given** a preset id no pack in the closure holds, **When** core applies it, **Then** the service answers `404
   preset_not_found`.

### Edge Cases

- A stored document names an id a pack upgrade removed: `GET` still answers it and flags the id; a `PUT` that still
  carries the id answers `422` (ADR-0059 D4).
- A stored deny entry names an artifact that is now locked: the entry is kept but not applied; denying it anew is
  refused `409`.
- Two concurrent writes with the same `If-Match`: exactly one succeeds; the other answers `412`.
- A per-artifact write without `If-Match`: `428`.
- A write body over 1 MiB, or a `null` list or list item: `422` (folded in from #323).
- `PUT` to an unknown id with `If-None-Match: *` creates it (`201`); with an `If-Match`, `412`.
- A cascade cycle (A requires B requires A) terminates; each node is visited once.
- The cascade reaches a `<pack>::` reference to a pack outside the closure: the compose already refuses it
  (`pack_reference_pack_missing`), so the write is refused with that code.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Create an activation | As core, I want `POST /activations` and `PUT` with `If-None-Match: *` to store a validated document and answer `201` with its revision, so that the service holds the project's activation. | High | Open | [build] | no |
| FR-002 | Read an activation | As core, I want `GET /activations/{id}` to answer the stored document with `ETag` equal to its revision, and `404 activation_not_found` for an unknown id. | High | Open | [build] | no |
| FR-003 | Replace an activation | As core, I want `PUT /activations/{id}` with `If-Match` to replace the document, `412` on a stale validator and `428` when the validator is missing, so that concurrent writers never lose an update. | High | Open | [build] | no |
| FR-004 | Delete an activation | As core, I want `DELETE /activations/{id}` to answer `204`, honour `If-Match`, and answer `404` on a second delete. | High | Open | [build] | no |
| FR-005 | Canonical stored form and revision | As core, I want lists sorted and deduplicated, an empty deny list omitted, ids stored as declared ids, and the revision computed as `sha256:` of the canonical serialisation, so that a fetched document round-trips with the same `ETag`. | High | Open | [build] | no |
| FR-006 | Write validation | As core, I want an unknown id, kind, pack or preset, a duplicate pack, a `null` list or item, or a body over 1 MiB refused `422 activation_invalid` with violations, so that only valid documents are stored. | High | Open | [build] | no |
| FR-007 | Locked artifacts cannot be denied | As core, I want a write that denies a locked artifact refused `409 artifact_locked` naming the owning pack, and a stored deny on a now-locked artifact kept but not applied. | High | Open | [build] | no |
| FR-008 | Absent means none | As core, I want an absent `selected.<kind>`, `activatedKinds` or `missionTypes` to mean nothing in force, the same as `[]`, while both stay distinct on the wire; an absent `packs` means every configured pack. | High | Open | [build] | no |
| FR-009 | Activation closure read | As core, I want `GET /activations/{id}/packs` to answer the `depends_on` closure rooted at the activation's packs, in dependency order, each entry with pack id, name, version, level and its `depends_on` facts, refusing with the composition codes when the closure cannot compose. | High | Open | [build] | no |
| FR-010 | Writes see only the closure | As core, I want every write to resolve artifact and preset ids against the activation's closure only, so that an id in an available but unreached pack is refused `422`. | High | Open | [build] | no |
| FR-011 | Select and unselect | As core, I want `PUT`/`DELETE …/selections/{kind}/{artifactId}` to add or remove one id from `selected`, clearing the id's own deny entry on select, and to report the change as an `ActivationChange`. | High | Open | [build] | no |
| FR-012 | Cascade on select | As core, I want a cascading select to walk `requires`, `suggests`, `refines`, `scope` and `instantiates` forward and unbounded, follow `<pack>::` targets, add what it reaches to `selected`, stop at denied artifacts and report them in `skippedDeactivated` (with `viaRequires` and `requiredBy`), and report out-of-scope kinds in `skippedByScope`. | High | Open | [build] | no |
| FR-013 | The enhances pull | As core, I want a cascade to bring in every artifact that `enhances` an artifact it puts in force, scoped by the cascade kinds, written into `selected`, walked in turn, and reported with provenance `enhances` and an empty `requiredBy`; a directly selected enhancer does not pull its target. | High | Open | [build] | no |
| FR-014 | Lineage pass-through | As core, I want the cascade to pass through every `specializes_from` ancestor of an artifact it reaches, following every FR-012 relation and the enhances pull from each ancestor, without putting any ancestor in force; a denied ancestor stops the walk there and is reported in `skippedDeactivated`. | Medium | Open | [build] | no |
| FR-015 | Deactivate and reactivate | As core, I want `PUT …/exclusions/{kind}/{artifactId}` to add the id to `deactivated` and unselect it, and with cascade to unselect what only it kept in force (walking the same relations, the enhances pull included) while keeping and reporting in `skippedShared` anything another active artifact still reaches; only the named id gets a deny entry. `DELETE` removes the deny entry. | High | Open | [build] | no |
| FR-016 | Apply a preset | As core, I want `PUT …/presets/{presetId}` to replace each key the preset lists (`activatedKinds` and `missionTypes` included), leave other keys untouched, clear matching deny entries only with `force`, report the replaced keys in `applied`, and answer `404 preset_not_found` for a preset outside the closure. | Medium | Open | [build] | no |
| FR-017 | Stale stored documents | As core, I want a stored document that names an id the closure no longer holds to be served as stored with the id flagged, and a write that still carries it refused `422`, so that a pack upgrade never breaks a read. | Medium | Open | [build] | no |
| FR-018 | Activation answer without offering data | As core, I want the `Activation` answer to carry no `summary` or `governanceFingerprint`, so that its bytes depend only on the stored document and its strong `ETag` is the revision. | Medium | Open | [build] | no |
| FR-019 | Durable storage | As core, I want documents stored behind a repository port with an in-memory adapter and a file adapter under `<config-root>/activations/<id>/`, so that a restart loses nothing and tests run without disk. | High | Open | [build] | no |
| FR-020 | The offering is unchanged | As core, I want `/offering` and its compose refusals to keep answering as today (every configured pack is a root), so that existing scenarios stay meaningful. | Medium | Open | [ratchet] | yes — paired with FR-009's acceptance scenario 1 on the same three-pack fixture, which must exclude gamma |
| FR-021 | Effective reads stay reserved | As core, I want the four effective reads to keep answering `501` until the resolution mission builds them. | Low | Open | [ratchet] | yes — paired with FR-001, whose create on the same activation must answer `201` |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Complexity limits | Every new or changed method stays within the Checkstyle limits of PR #368: cyclomatic complexity ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1; the suppression register does not grow. | Maintainability | High | Open |
| NFR-002 | Scenario coverage | Each of the nine writes and the closure read has at least one behavioural `@contract` scenario that fails before the operation is built and passes after (ADR-0059 D1); 0 activation operations are left pinned only by a `501` scenario except the four effective reads. | Quality | High | Open |
| NFR-003 | Round-trip property | A property test over at least 1,000 generated valid documents shows that a `PUT` of a fetched `config` answers `200` with the same `ETag` in 100% of cases. | Reliability | High | Open |
| NFR-004 | Concurrency | With 8 concurrent writers on one activation and the same starting `If-Match`, exactly 1 write succeeds and 7 answer `412`, in 100 of 100 runs. | Reliability | High | Open |
| NFR-005 | Durability | A document written by the file adapter reads back byte-identical after a service restart in 100% of the scenario runs; a crash mid-write leaves either the old or the new document, never a partial one. | Reliability | High | Open |
| NFR-006 | Write latency (report-only) | A cascading select over a 500-artifact fixture completes within 200 ms p95 on the CI runner, reported but never gating (ADR-0057). | Performance | Low | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Contract first | Request and response shapes come from `contracts/charter/openapi.yaml`; every contract change (the closure read, the `enhances` reason value, `summary`/`governanceFingerprint` leaving `Activation`) lands in the contract with a contract-changelog fragment before the code that serves it. | Technical | High | Open |
| C-002 | Hexagonal slices | The activation domain lives in its own slice behind ports, per `docs/architecture/hexagonal-service.md`; only the API ring names generated types; `HexagonalArchitectureTest` covers the new slice. | Technical | High | Open |
| C-003 | No core changes | Nothing in spec-kitty core changes; core maps its documents to the contract (ADR-0020 D4). | Business | High | Open |
| C-004 | Branch base | The mission builds on the branch of PR #368 and rebases when it merges. | Technical | Medium | Open |
| C-005 | No disabled tests | No test is skipped, disabled or quarantined, and no new Checkstyle, SpotBugs or PMD suppression is added. | Quality | High | Open |
| C-006 | Divergences recorded | Each deliberate difference from core (absent means none; the enhances pull; the lineage pass-through; deny-on-locked refused) has a divergence-register entry. | Business | Medium | Open |

### Key Entities

- **Activation document** (`ActivationConfig`): what one project activated: the packs it builds on, the kinds and
  mission types in force, the selected ids per kind, and the deny list per kind. Three-state lists: absent, `[]`, a
  set; absent and `[]` both mean none.
- **Activation** (answer envelope): the stored document plus its id, revision and links.
- **Revision**: the content hash of the document's canonical form; the strong validator.
- **Activation closure**: the packs reachable over `depends_on` from the activation's packs, in dependency order; the
  only packs whose artifacts and presets the activation can name.
- **Activation change** (`ActivationChange`): what one per-artifact or preset write changed, with each change's
  provenance and the skipped report.
- **Skipped report**: `skippedByScope`, `skippedDeactivated` (with `viaRequires`, `requiredBy`),
  `unreachedBehindDeactivated`, `skippedShared`.
- **Locked artifact**: an artifact whose owning pack's mutability level forbids changing it; it can never be denied.

## Assumptions

- The required floor today is the set of locked artifacts (pack mutability, ADR-0012). An org's `required_<kind>`
  arrives with #11 and joins the same `409` path then.
- The effective reads, `summary` and `governanceFingerprint`'s new home, and the `If-None-Match`/`304` declarations on
  the reads land with the resolution mission (ADR-0059 D5); this mission removes the two fields from `Activation`.
- The layer merge is not built here; within one activation the closure resolves through the existing composition
  resolver, so a sibling contest is refused as today.
- The cascade's "active" set for `skippedShared` is the document's `selected` plus the locked artifacts.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 9 of 9 activation writes and the closure read answer as the contract declares; 0 of them answer `501`. — [build] · no-op passable: no
- **SC-002**: 100% of the acceptance scenarios above run as `@contract` BDD scenarios and pass in the per-PR gate. — [build] · no-op passable: no
- **SC-003**: A project's activation survives a service restart unchanged in 100% of runs. — [build] · no-op passable: no
- **SC-004**: Every existing `/offering` and composition scenario still passes unchanged. — [ratchet] · no-op passable: yes — paired with SC-001's closure read on the same fixtures
