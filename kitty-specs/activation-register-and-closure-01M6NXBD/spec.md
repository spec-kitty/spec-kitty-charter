# Mission Specification: Activation register and activated-pack closure

**Mission Branch**: `issue-360-activated-closure`
**Created**: 2026-11-05
**Status**: Accepted 2026-11-06 (post-plan review folded 2026-11-05)
**Input**: User description: "Activation register and activated-pack closure (ADR-0055 steps 1–2): the activation
document storage and the nine operations #125 reserved (eight writes and `getActivation`, #291), the `depends_on` facts per pinned pack
(#359) and the closure rooted at an activation's packs (#360)."

## Context

Core cannot ask the charter service what is in force for a project until the service stores that project's
activation and resolves packs from it ([ADR-0055](/adr/0055-cutover-sequencing-the-java-service-first/) D1). Today
every activation operation answers `501`, and the compose roots at every configured pack. This mission builds the
activation register and the activated-pack closure. The effective reads (R9, the read-time `enhances` pull and its
reporting) and the layer merge are the next mission.

Rulings this mission builds to:

- [ADR-0020](/adr/0020-offering-and-activations/) D2–D9 and its 2026-10-31 amendment.
- [ADR-0013](/adr/0013-persistent-deactivation/) D2–D5.
- [ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) (on `main`) as revised on PR #376 by the operator's
  rulings of 2026-11-05, which supersede this mission's 13:15 discovery decisions on the `enhances` pull and the
  lineage pass-through:
  - **D2.2:** the pull runs at read time, both ways, and never writes `selected`.
  - **D2.4:** lineage passes `requires` and `scope` only, up the whole ancestor chain.
- The other discovery and planning decisions under `decisions/`:
  - the nine operations #125 reserved (eight writes and `getActivation`);
  - the closure read;
  - absent means none;
  - slice placement;
  - storage;
  - two PRs;
  - the policy file;
  - the required floor.

## User Scenarios & Testing *(mandatory)*

The actor in every story is **core's seam client**, writing and reading one project's activation on the service on
behalf of that project. Behavioral scenarios live in a new `@contract` feature file; the `@reserved` pins stay in
`activations.feature`.

### User Story 1 - Record and read an activation document (Priority: P1)

Core sends a project's activation document. The service validates it, stores it in canonical form, and answers
with its revision. Core reads it back, replaces it (optionally under an optimistic-concurrency check), and deletes it.

**Why this priority**: Nothing else in the cutover works until the service holds the document (ADR-0020 D5).

**Independent Test**: Create a document, read it, replace it with `If-Match`, read it again, delete it. Each answer
carries the expected status, revision and `ETag`; a stale `If-Match` is refused.

**Acceptance Scenarios**:

1. **Given** a valid document with unsorted, duplicated `selected.directive` entries, **When** core creates it,
   **Then** the service answers `201`, the stored document lists them sorted and once, and `ETag` equals the quoted
   `revision`.
2. **Given** a stored document, **When** core `PUT`s the `config` it fetched unchanged with `If-Match`, **Then** the
   service answers `200` with the same `ETag` (the round trip).
3. **Given** a stored document, **When** core replaces it with an `If-Match` that names an older revision, **Then**
   the service answers `412` and a follow-up `GET` returns the old revision.
4. **Given** a stored document, **When** core replaces it without `If-Match`, **Then** the service answers `200` with
   the new revision (last writer wins, ADR-0020 D6).
5. **Given** a document naming an artifact id that no pack in its closure holds, **When** core creates it, **Then**
   the service answers `422 activation_invalid` with a violation naming the id, and nothing is stored.
6. **Given** a document whose deny list names a must-have item of a governing preset in its closure, **When** core
   creates it, **Then** the service answers `409 artifact_required` with `requiredBy` naming the pack, and nothing is
   stored. The same with an artifact a pack policy file locks answers `409 artifact_locked`.
7. **Given** a document carrying `deactivated.directive: []`, **When** core creates it, **Then** the service answers
   `422 activation_invalid` (an empty deny list is never stored).
8. **Given** a stored document, **When** core deletes it twice, **Then** the first answers `204` and the second `404
   activation_not_found`.
9. **Given** an unknown id, **When** core `PUT`s with `If-None-Match: *`, **Then** the service answers `201`; the same
   request on an existing id answers `412`.

---

### User Story 2 - See the packs an activation builds on (Priority: P1)

Core reads which packs an activation actually composes: the `depends_on` closure rooted at the activation's packs,
in dependency order, with each pack's identity, version and resolved dependencies, in the shape the offering already
uses for a pack.

**Why this priority**: The closure decides which artifacts the writes can see (#360), and core needs the dependency
facts per pinned pack (#359).

**Independent Test**: Three available packs: alpha depends on beta, gamma is unrelated. An activation of alpha reads
back beta then alpha, alpha's entry carries its resolved dependency on beta, and gamma is absent.

**Acceptance Scenarios**:

1. **Given** the available packs alpha (depends on beta), beta and gamma, and an activation whose `packs` is alpha,
   **When** core reads the activation's packs, **Then** the answer lists beta then alpha; alpha's `dependsOn` names
   beta's `descriptorPackId`, the range and the held version; gamma is absent.
2. **Given** the same packs and gamma broken (unreadable `pack.yaml`), **When** core reads the alpha activation's
   packs, **Then** the answer is still beta then alpha (a pack outside the closure is never judged).
3. **Given** an activation with no `packs` key, **When** core reads its packs, **Then** the answer is the closure of
   every configured pack, in the offering's order.
4. **Given** an activation whose closure cannot compose (a dependency outside the declared range), **When** core
   reads its packs, **Then** the service answers `500` with `pack_dependency_out_of_range` naming the pack, as the
   offering does.

---

### User Story 3 - Select and unselect one artifact, with cascade (Priority: P2)

Core selects one artifact. The service adds it to the document's selection and, when asked to cascade, also selects
what it reaches over the five cascade relations and through profile lineage, skipping and reporting anything denied
or outside the cascade's kind scope. Unselecting removes it.

**Why this priority**: Per-artifact selection is how a project changes what is in force between full replaces.

**Independent Test**: A fixture where directive D requires tactic T, D requires tactic X, X is denied, and T is
enhanced by tactic E. Selecting D with cascade selects D and T, reports X as skipped-deactivated, and does not select
E (the `enhances` pull runs at read, ADR-0059 D2.2).

**Acceptance Scenarios**:

1. **Given** the fixture above, **When** core selects D with cascade `["all"]`, **Then** `selected.directive` is [D]
   and `selected.tactic` is [T]; `applied` lists D and T; X is in `skipped.skippedDeactivated` with `viaRequires: true`
   and `requiredBy` D; E is not selected; the answer carries a new `ETag`.
2. **Given** the same fixture and cascade `["directive"]`, **When** core selects D, **Then** only D is selected and T
   is in `skippedByScope`.
3. **Given** the same fixture, **When** core selects D with cascade and `includeDeactivated: true`, **Then** X is
   selected too, and its deny entry, and every deny entry the `enhances` pull would reach from what the walk
   selects, appear in `cleared` (ADR-0013 D4, ADR-0059 D2.2). With E denied in the starting document, E is in
   `cleared` and in `applied` (removed from `deactivated`), and **not** in `selected`.
4. **Given** a profile P that specializes from Q, and Q requires directive R and suggests directive S, **When** core
   selects P with cascade, **Then** P and R are selected, and Q and S are not. When Q is denied, R is not reached and
   is in `unreachedBehindDeactivated`; Q is in `skippedDeactivated`.
5. **Given** a selected artifact, **When** core unselects it, **Then** it leaves `selected`, `applied` lists it (it
   was removed from `selected`), `cleared` is empty, and the answer carries a new `ETag`. Unselecting an id that is not selected answers `200` with
   the same revision.
6. **Given** an absent `selected.directive`, **When** core selects one directive, **Then** the list holds exactly
   that directive (absent means none).
7. **Given** kind `mission_type`, **When** core selects a mission type the closure holds, **Then** it joins
   `missionTypes`; an unknown one answers `404 mission_type_not_found`.
8. **Given** an artifact id the closure does not hold, **When** core selects it, **Then** the service answers `404
   artifact_not_found`; cascade `["all", "tactic"]` answers `422 activation_invalid`.

---

### User Story 4 - Deactivate and reactivate one artifact (Priority: P2)

Core deactivates an artifact: the service adds the id to the deny list, unselects it, and with cascade also
unselects what only it kept in force, keeping anything another active artifact still reaches. Reactivating removes
the deny entry.

**Why this priority**: Persistent deactivation is ADR-0013's reason to exist; it must survive every write that does not explicitly clear it.

**Independent Test**: Deactivate a directive with cascade where one of its requirements is also required by another
selected directive; the shared one stays selected and is reported in `skippedShared`.

**Acceptance Scenarios**:

1. **Given** directive D selected with T that only D reaches, and T2 that another selected directive also reaches,
   **When** core deactivates D with cascade, **Then** D is in `deactivated`, D and T leave `selected`, T2 stays and is
   in `skippedShared` naming the other directive; only D gets a deny entry; `applied` lists D (added to
   `deactivated`, removed from `selected`) and T (removed from `selected`), and not T2.
2. **Given** a tactic T3 reached only through D, and an active selected artifact E2 that `enhances` T3, **When** core
   deactivates D with cascade, **Then** T3 stays and is in `skippedShared` naming E2 (ADR-0059 D2.2).
3. **Given** a locked or required artifact, **When** core deactivates it, **Then** the service answers `409
   artifact_locked` or `artifact_required` and the revision is unchanged.
4. **Given** a denied artifact, **When** core reactivates it, **Then** its deny entry is in `cleared` and the id is in
   `applied` (removed from `deactivated`), and when the
   deny list empties the key is omitted. Reactivating an id that is not denied answers `200` with the same revision.

---

### User Story 5 - Apply a preset (Priority: P3)

Core applies a preset from a pack in the closure. Each key the preset lists replaces that key in the document;
`force` also clears the deny entries that match the preset's members.

**Why this priority**: Presets are how a project starts; they come after the verbs they are built from.

**Independent Test**: A preset listing directives and `activatedKinds` replaces both keys and leaves
`selected.tactic` untouched.

**Acceptance Scenarios**:

1. **Given** a document with `selected.directive` [A] and `selected.tactic` [T], **When** core applies a preset
   listing directives [B, C] and `activatedKinds` [directive], **Then** `selected.directive` is [B, C],
   `activatedKinds` is [directive], `selected.tactic` is still [T], and `applied` lists A (removed from `selected`),
   B and C (added). The `activatedKinds` change is not in `applied`: a kind is not an `ArtifactReference`, so
   `applied` holds artifact and mission-type ids only.
2. **Given** a preset member the document denies, **When** core applies it without `force`, **Then** the member is
   not selected and the deny entry stays; with `force`, the deny entry is in `cleared` and the member is selected.
3. **Given** a preset id no pack in the closure holds, **When** core applies it, **Then** the service answers `404
   preset_not_found`.

---

### User Story 6 - A pack declares locks (Priority: P2)

A pack author ships a mutability policy file and names it from `pack.yaml`. The service reads it, and the locks it
declares bind every later pack and every activation over that pack.

**Why this priority**: Without it, `artifact_locked` can never be raised (FR-022).

**Acceptance Scenarios**:

1. **Given** an org pack whose `pack.yaml` names `policy.yaml` locking `directive:D1`, **When** core creates an
   activation denying D1, **Then** the service answers `409 artifact_locked`.
2. **Given** a built-in pack that leaves D1 `open` and an org pack that locks it, **When** the closure holds both,
   **Then** D1 is locked (the highest level along the closure wins; a later pack never lowers it).
3. **Given** a `pack.yaml` naming a policy file that is missing or fails its schema, **When** the pack is read,
   **Then** it is unreadable (`pack_unreadable`) on the offering and on any activation closure that holds it.

### Edge Cases

- A stored document names an id that a pack upgrade removed. `GET` serves the document as stored, unchanged.
  Flagging the id belongs to the effective reads (next mission, ADR-0059 D4), because a flag on `Activation` would
  break its strong `ETag` (ADR-0059 D5). A write that still carries the id answers `422`.
- A stored deny entry names an artifact that is now locked or required: the entry is kept (it is not applied at
  read, next mission); denying it anew is refused `409`.
- Two concurrent writes with the same `If-Match`: exactly one succeeds, and the other answers `412`.
- A per-artifact write without `If-Match` answers `428`. A request carrying both `If-Match` and `If-None-Match` answers
  `400`.
- A write body over 1 MiB, an unknown JSON member, or a `null` list or list item answers `422` (#323, ADR-0020 build
  notes).
- A cascade cycle (A requires B requires A) terminates; each node is visited once.
- A `<pack>::` reference to a pack outside the closure cannot occur: the compose refuses it
  (`pack_reference_pack_missing`, `500`) before any write runs.
- A version pin that differs from the held version answers `422 activation_invalid`.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Create an activation | As core, I want `POST /activations` and `PUT` with `If-None-Match: *` to store a validated document and answer `201` with its revision, so that the service holds the project's activation. | High | Open | [build] | no |
| FR-002 | Read an activation | As core, I want `GET /activations/{id}` to answer the stored document with `ETag` equal to its quoted revision and `304` on a matching `If-None-Match`, and `404 activation_not_found` for an unknown id. | High | Open | [build] | no |
| FR-003 | Replace an activation | As core, I want `PUT /activations/{id}` to replace the document: with `If-Match`, `412` on a stale validator; without it, last writer wins (ADR-0020 D6). | High | Open | [build] | no |
| FR-004 | Delete an activation | As core, I want `DELETE /activations/{id}` to answer `204`, honour `If-Match`, and answer `404` on a second delete. | High | Open | [build] | no |
| FR-005 | Canonical stored form and revision | As core, I want allow lists sorted and deduplicated (`packs` keeps its order), ids stored as declared ids, and the revision computed as `sha256:` of the JCS serialisation, so that a fetched document round-trips with the same `ETag`. | High | Open | [build] | no |
| FR-006 | Write validation | As core, I want an unknown id, kind, pack or preset in the body, a duplicate pack, a version pin that differs from the held version, an empty deny list, an unknown JSON member, a `null` list or item, or a body over 1 MiB refused `422 activation_invalid` with violations, so that only valid documents are stored. | High | Open | [build] | no |
| FR-007 | The floor cannot be denied | As core, I want a write that denies a required or locked artifact refused `409 artifact_required` or `artifact_locked`, with `requiredBy` naming the pack, and nothing stored. | High | Open | [build] | no |
| FR-008 | Absent means none | As core, I want an absent `selected.<kind>`, `activatedKinds` or `missionTypes` to mean nothing in force, the same as `[]`, while both stay distinct on the wire; an absent `packs` means every configured pack. | High | Open | [build] | no |
| FR-009 | Activation closure read | As core, I want `GET /activations/{id}/packs` to answer the `depends_on` closure rooted at the activation's packs, in dependency order, each entry in the offering's pack shape (`packId`, `descriptorPackId`, level, name, version, resolved `dependsOn`), refusing with the composition codes when the closure cannot compose. | High | Open | [build] | no |
| FR-010 | Writes see only the closure | As core, I want every write to resolve ids against the activation's closure only: in a body, an id outside it answers `422`; in a path, `404 artifact_not_found`, `mission_type_not_found` or `preset_not_found`; a per-artifact or preset write without `If-Match` answers `428`, and a path kind outside the activatable kinds answers `422 activation_invalid` with a violation at the path. Preconditions are judged before content: `428`, `412` and `400` (malformed validators) come before any `422` or `409`, and a refused precondition reads no packs. | High | Open | [build] | no |
| FR-011 | Select and unselect | As core, I want `PUT`/`DELETE …/selections/{kind}/{artifactId}` to add or remove one id from `selected` (or `missionTypes` for kind `mission_type`), clearing the id's own deny entry on select, and answer an `ActivationChange`; `applied` lists every id the write added to or removed from `selected`, `deactivated` or `missionTypes`, and `cleared` the deny entries it removed (the contract's `ActivationChange` rule); a no-op answers `200` with the same revision and empty `applied` and `cleared`. | High | Open | [build] | no |
| FR-012 | Cascade on select | As core, I want a cascading select to walk `requires`, `suggests`, `refines`, `scope` and `instantiates` forward and unbounded, follow `<pack>::` targets, add what it reaches to `selected`, stop at denied artifacts and report them in `skippedDeactivated` (with `viaRequires` and `requiredBy`), report what lies only behind them in `unreachedBehindDeactivated`, and report out-of-scope kinds in `skippedByScope`; `["all"]` cannot be mixed with kinds (`422`). | High | Open | [build] | no |
| FR-013 | Include deactivated | As core, I want `includeDeactivated: true` to take denied artifacts back into the cascade, clearing their deny entries and every deny entry the read-time `enhances` pull would reach from what the walk selects (both directions: enhancers of a selected artifact and targets of a selected enhancer, transitively over `enhances` only; the write's own cascade still walks only the five relations), all reported in `cleared`; the write never adds a pulled artifact to `selected` (ADR-0059 D2.2). | Medium | Open | [build] | no |
| FR-014 | Lineage pass-through | As core, I want the cascade to pass through every `specializes_from` ancestor of a profile it reaches, following only `requires` and `scope` from each ancestor, without putting any ancestor in force; a denied ancestor stops the pass-through and is reported. | Medium | Open | [build] | no |
| FR-015 | Deactivate and reactivate | As core, I want `PUT …/exclusions/{kind}/{artifactId}` to add the id to `deactivated` and unselect it, and with cascade to unselect what only it reached over the five relations, keeping and reporting in `skippedShared` anything another active artifact reaches or an active enhancer references; only the named id gets a deny entry. `DELETE` removes the deny entry, reported in `cleared` and in `applied`. `applied` lists every id the write added to or removed from `selected`, `deactivated` or `missionTypes`, and `cleared` the deny entries it removed (the contract's `ActivationChange` rule); a no-op answers `200` with the same revision and empty `applied` and `cleared`. | High | Open | [build] | no |
| FR-016 | Apply a preset | As core, I want `PUT …/presets/{presetId}` to replace each key the preset lists (`activatedKinds` and `missionTypes` included), leave other keys untouched, and clear matching deny entries only with `force`. A preset lists a key when its file holds at least one member for it; a key with an empty list counts as not listed (default; research.md open review item 4). `applied` lists every id the write added to or removed from `selected`, `deactivated` or `missionTypes`, and `cleared` the deny entries it removed (the contract's `ActivationChange` rule); a no-op answers `200` with the same revision and empty `applied` and `cleared`; `activatedKinds` changes are not reported in `applied` (a kind is not an `ArtifactReference`). | Medium | Open | [build] | no |
| FR-017 | Stale stored documents | As core, I want a stored document that names an id the closure no longer holds to be served unchanged by `GET`, and a write that still carries it refused `422`, so that a pack upgrade never breaks a read. | Medium | Open | [build] | no |
| FR-018 | Activation answer without offering data | As core, I want the `Activation` answer to carry no `summary` or `governanceFingerprint`, with both moved to an effective read in the contract, so that its bytes depend only on the stored document. | Medium | Open | [build] | no |
| FR-019 | Durable storage | As core, I want documents stored behind a repository port with an in-memory adapter and a file adapter under `<config-root>/activations/<encoded id>/activation.json`, where the directory name is a reversible, case-safe encoding of the id,, so that a restart loses nothing and tests run without disk. | High | Open | [build] | no |
| FR-020 | The offering is unchanged | As core, I want `/offering` and its compose refusals to keep answering as today (every configured pack is a root). | Medium | Open | [ratchet] | yes — paired with US2 AS1 and AS2 (FR-009) on the same fixtures, which must exclude gamma |
| FR-021 | Effective reads stay reserved | As core, I want the four effective reads to keep answering `501` until the resolution mission builds them; their contract gains `If-None-Match`/`304` and the moved `summary`. | Low | Open | [ratchet] | yes — paired with FR-001, whose create on the same activation must answer `201` |
| FR-022 | Pack mutability policy file | As a pack author, I want a pack descriptor's optional `mutability_policy` to name a pack-relative policy file, which the schema constrains to `policy.yaml` for now (ADR-0063 records the choice), holding a default level per kind and a level per artifact URN, which the service reads per pack, so that a locked artifact is enforced at the closure's highest level; a named but missing or invalid file makes the pack unreadable. | High | Open | [build] | no |
| FR-023 | Required floor from governing presets | As core, I want the must-have items of every governing preset in the activation's closure to form its required floor. | High | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Complexity limits | Every new or changed method stays within the Checkstyle limits PR #368 defined (still off on `main`, held by construction and measured by hand by the reviewer): cyclomatic complexity ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1; the suppression register does not grow. | Maintainability | High | Open |
| NFR-002 | Scenario coverage | Each built operation (the nine operations #125 reserved (eight writes and `getActivation`), the closure read) has at least one behavioural `@contract` scenario that fails before the operation is built and passes after; a `@reserved` pin does not count (ADR-0059 D1). | Quality | High | Open |
| NFR-003 | Round-trip property | A property test over at least 1,000 generated valid documents shows that a `PUT` of a fetched `config` with its `If-Match` answers `200` with the same `ETag` in 100% of cases. | Reliability | High | Open |
| NFR-004 | Concurrency | With 8 concurrent writers on one activation and the same starting `If-Match`, exactly 1 write succeeds and 7 answer `412`, in 100 of 100 runs, against the file adapter. | Reliability | High | Open |
| NFR-005 | Durability | A file-adapter integration test recreates the application context and reads every written document back byte-identical; an injected failure between the temp-file write and the move leaves the previous document readable and no partial file, in 100% of runs. | Reliability | High | Open |
| NFR-006 | Write latency (report-only) | A cascading select over a 500-artifact fixture completes within 200 ms p95 on the CI runner, reported but never gating (ADR-0057). | Performance | Low | Open |
| NFR-007 | One view per request | Every write and the closure read take one verified snapshot of the activation's closure (one `ConsistentRead`); a pack change during the request answers `stale_input`, never a mixed answer. | Reliability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Contract first | Request and response shapes come from `contracts/charter/openapi.yaml`; every contract change (the closure read, `summary`/`governanceFingerprint` moving, `If-None-Match`/`304`, the absent-means-none texts) lands in the contract with a contract-changelog fragment before the code that serves it. | Technical | High | Open |
| C-002 | Hexagonal slices, no shadow logic | The activation slice owns only the document, its storage and its write rules; every question about pack content is answered by `packs` through one port; the activation domain never reaches its application ring; `HexagonalArchitectureTest` covers the new slice. | Technical | High | Open |
| C-003 | No core changes | Nothing in spec-kitty core changes; core maps its documents to the contract (ADR-0020 D4). | Business | High | Open |
| C-004 | Branch base | The mission builds on `main`, which carries PR #368 and PR #376 (ADR-0059), both merged; PR A and PR B rebase over #377, #379, #384, #390, #391, #392, #393 and #394 as they merge. | Technical | Medium | Open |
| C-005 | No disabled tests | No test is skipped, disabled or quarantined, and no new Checkstyle, SpotBugs or PMD suppression is added. | Quality | High | Open |
| C-006 | Divergences recorded | Each deliberate difference from core has a divergence-register entry (DIV-203 onwards): absent means none (skills lose their "required" default too); the lineage pass-through; deny on a required or locked artifact refused at write; the closure-scoped required floor; an enhancer is never written to `selected` (`enhances` at write, DIV-207). | Business | Medium | Open |

### Key Entities

- **Activation document** (`ActivationConfig`): what one project activated: the packs it builds on, the kinds and
  mission types in force, the selected ids per kind, and the deny list per kind. Three-state allow lists: absent, `[]`,
  a set; absent and `[]` both mean none. A deny list is never empty.
- **Activation** (answer envelope): the stored document plus its id, revision and links.
- **Revision**: the content hash of the document's canonical form; the strong validator.
- **Activation closure**: the packs reachable over `depends_on` from the activation's packs, in dependency order; the
  only packs whose artifacts and presets the activation can name.
- **Activation change** (`ActivationChange`): what one per-artifact or preset write changed: the new revision,
  `applied` and `cleared` artifact references, and the skipped report.
- **Skipped report**: `skippedByScope`, `skippedDeactivated` (with `viaRequires`, `requiredBy`),
  `unreachedBehindDeactivated`, `skippedShared`.
- **Locked artifact**: an artifact whose mutability level, the highest along the closure, is `locked`.
- **Required artifact**: a must-have item of a governing preset in the closure. Locked implies required (ADR-0012 D7).

## Assumptions

- The required floor is the must-have items of every governing preset in the activation's closure (ADR-0031,
  ADR-0042). This closure scope was an assumed default (planning decision 2026-11-05); the operator ruled on
  2026-11-06 that it stands.
- For a preset id held by several closure packs, the last holder in merge order wins (ADR-0053 d6), and the required
  floor follows that copy only (FR-023). Two packs of one level that hold the id and are not related by `parent_pack`
  are refused as `pack_sibling_conflict` (operator ruling of 2026-11-06); a child merges after the parent it extends.
- The `skippedShared` active set is the document's `selected` together with the required floor, which includes the
  locked artifacts.
- `missionTypes` stays the document's mission-type key. ADR-0050's move of mission types into `selected` is out of
  scope (ADR-0059 D6 "or its successor").
- No pack ships a policy file yet (ADR-0030 Risks). The built-in pack's own policy file and the
  `replaceable-builtins.yaml` migration (ADR-0012 D5) are follow-ups.
- The layer merge is not built here. Within one activation the closure resolves through the existing composition
  resolver, so a sibling contest inside the closure is refused as today.
- #360's acceptance ("absent from the offering") is re-pointed at the closure read. The change is recorded on the
  issue when PR A opens.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 9 of 9 activation writes and the closure read answer as the contract declares; 0 of them answer `501`. — [build] · no-op passable: no
- **SC-002**: 100% of the acceptance scenarios above run as `@contract` BDD scenarios and pass in the per-PR gate. — [build] · no-op passable: no
- **SC-003**: A project's activation survives a context restart unchanged in 100% of the file-adapter integration runs. — [build] · no-op passable: no
- **SC-004**: Every existing `/offering` and composition scenario still passes unchanged. — [ratchet] · no-op passable: yes — paired with SC-001's closure read on the same fixtures
