# Mission Specification: Regular artefact read & registration

**Mission Branch**: `feat/regular-artefacts`
**Created**: 2026-10-25
**Status**: Draft
**Input**: Wire the regular charter artefacts for read + registration through the API, skeleton-first. Grounded in ADR-0025 (relationships as first-class domain value objects, lazy repository-backed walk) and ADR-0026 (the write surface is functional registration, not CRUD).

## User Scenarios & Testing *(mandatory)*

The actors are **pack authors** and **authoring tooling** that talk to the charter service over its contract. Today they can read only tactics and can write nothing. Each story below is an independently demonstrable slice; the first is the MVP skeleton that proves the shared graph core and the registration pattern end to end, and the later stories replicate that proven pattern.

### User Story 1 - Graph core and the directive reference slice (Priority: P1)

A pack author registers a directive together with the relationships it declares to other components, reads the directive back exactly as submitted, and walks its relationships to discover the components it points to — each related component fetched on demand rather than by loading the whole graph.

**Why this priority**: This is the skeleton. It delivers the shared relationship/graph core and takes one top-level artefact (the directive — a natural Doctrine Reference Graph entry point) fully through read, registration, and traversal. Every later story copies this slice, so nothing else can be trusted until it works.

**Independent Test**: Register a directive carrying relationships, read it back and confirm value equality, then walk from the directive and confirm the related components are returned by on-demand resolution. Fully testable without any other artefact kind being wired.

**Acceptance Scenarios**:

1. **Given** a pack, **When** a client registers a directive whose body carries its fields and one or more relationships, **Then** the directive is stored and a later read returns it value-equal to what was submitted, relationships included.
2. **Given** a stored directive with relationships, **When** a client walks the graph from it, **Then** each related component is fetched on demand through its own kind's read path and the walk never loads components the start cannot reach.
3. **Given** two components A and B, **When** a client registers a relationship between them and then registers another relationship for the same pair, **Then** only one relationship exists between A and B (the second replaces the first; identity is the unordered pair).
4. **Given** a registration whose body is malformed or whose relationship names an unknown component kind, **When** it is submitted, **Then** it is refused with a Problem response and the pack is left unchanged; a well-formed registration on the same pack succeeds.

---

### User Story 2 - Styleguide and paradigm reach parity (Priority: P2)

A pack author reads and registers styleguides and paradigms through the same registration surface proven for directives.

**Why this priority**: Styleguide and paradigm already persist but have no read or write surface; this closes their gap by replicating the US1 pattern. It is valuable on its own (two more artefact kinds become usable) and depends only on the US1 skeleton.

**Independent Test**: Register a styleguide and a paradigm (each with relationships where applicable), read each back value-equal, and walk a paradigm's relationships to its directives.

**Acceptance Scenarios**:

1. **Given** a pack, **When** a client registers a styleguide and reads it back, **Then** it is value-equal to what was submitted.
2. **Given** a paradigm that references directives, **When** a client reads it and walks its relationships, **Then** the referenced directives are resolved on demand.

---

### User Story 3 - Toolguide becomes a first-class kind (Priority: P3)

A pack author reads and registers toolguides — a kind that does not exist in the domain yet — through the same surface.

**Why this priority**: Toolguide is net-new modelling, so it is sequenced last: it proves the pattern also carries a brand-new kind, not only kinds that already persisted. It depends on the US1 skeleton and reuses the US2 replication.

**Independent Test**: Register a toolguide and read it back value-equal; confirm it participates in the pack aggregate's invariants.

**Acceptance Scenarios**:

1. **Given** a pack, **When** a client registers a toolguide and reads it back, **Then** it is value-equal to what was submitted.
2. **Given** a pack that already holds a toolguide with a given id, **When** a client registers a second toolguide with the same id, **Then** the write is refused and the pack is unchanged; a distinct id succeeds.

### Edge Cases

- A relationship whose `from` endpoint does not resolve within the pack is refused; a relationship whose `to` is external to the pack is accepted (graph endpoints need not be local).
- Registering an artefact whose id already exists in the pack replaces it (PUT, idempotent) or is refused as a duplicate (POST to the collection), consistently.
- A write against a pack whose mutability level forbids it (e.g. locked, or a level the caller may not extend) is refused with a governance refusal.
- A concurrent write with a stale precondition is refused (precondition failed); a sub-resource write with no precondition supplied is refused (precondition required); a fresh precondition succeeds.
- A directed relationship cannot exist in both directions between the same pair (unordered-pair identity); re-registering the reverse replaces rather than adds.
- A removal request (DELETE/PATCH) is not offered by the surface; taking an artefact out of force is deactivation, which is out of scope.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Relationship is a first-class value object with unordered-pair identity | As a pack author, I want a relationship between two components to be a single well-defined thing so that duplicate or contradictory edges cannot exist. | High | Open | [build] | no |
| FR-002 | RelationType carries directionality and propagation behaviour | As a maintainer, I want each relation kind to declare how it propagates so that traversal need not special-case relations. | High | Open | [build] | no |
| FR-003 | A component exposes its relationships by effective origin | As a consumer, I want a component to report the relationships it originates (its `from`, plus bidirectional edges naming it as `to`) so that a walk starts from any component correctly. | High | Open | [build] | no — paired control: a bidirectional edge with the component as `to` is returned; a purely directed edge with it as `to` is not |
| FR-004 | Relationships are walked lazily through per-kind read paths | As a consumer, I want to walk from a component and resolve each neighbour on demand so that the whole graph is never loaded into memory. | High | Open | [build] | no |
| FR-005 | An artefact and its relationships round-trip through storage without loss | As a pack author, I want what I register read back exactly so that persistence never silently drops or reorders content. | High | Open | [build] | no |
| FR-006 | A client can read a pack's directives (all and by id) with relationships populated | As a pack author, I want to fetch directives so that tooling can display and reason over them. | High | Open | [build] | no |
| FR-007 | A client can register a directive (fields + relationships) | As a pack author, I want to register a directive through the API so that content can enter without re-uploading the whole pack. | High | Open | [build] | no |
| FR-008 | Registration revalidates the whole pack aggregate | As a maintainer, I want every write to re-check belongs-to-pack and no-duplicate-id across the aggregate so that a single-child write cannot break pack invariants. | High | Open | [ratchet] | no — paired control: a valid registration succeeds; a duplicate-id registration on the same fixture is refused |
| FR-009 | A client can register a relationship edge, idempotent on the pair | As a pack author, I want to add a relationship between two components so that the graph can be authored incrementally. | High | Open | [build] | no |
| FR-010 | Invalid registrations are refused with a Problem response and no state change | As a pack author, I want clear, typed refusals so that I can correct a bad submission and trust the pack was untouched. | High | Open | [ratchet] | no — paired control: a well-formed request on the same fixture is accepted |
| FR-011 | Mutability policy gates writes | As a governance lead, I want writes beyond a pack's permitted level refused so that locked or protected packs cannot be altered through the API. | Medium | Open | [build] | no — paired control: a within-level write succeeds; an over-level write is refused |
| FR-012 | Writes use optimistic concurrency | As a pack author, I want stale writes rejected so that concurrent edits cannot silently clobber each other. | Medium | Open | [build] | no — paired control: a write with a fresh precondition succeeds; one with a stale precondition is refused |
| FR-013 | Styleguide reaches read + registration parity | As a pack author, I want to read and register styleguides so that they are usable like directives. | Medium | Open | [build] | no |
| FR-014 | Paradigm reaches read + registration parity | As a pack author, I want to read and register paradigms so that they are usable like directives. | Medium | Open | [build] | no |
| FR-015 | Toolguide is added as a readable and registerable kind | As a pack author, I want toolguides modelled and exposed so that tool guidance can be fetched and registered. | Medium | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Bounded traversal memory | A walk from a component issues at most one read per distinct reachable component (cycle-guarded) and never loads artefacts the start component cannot reach; memory does not scale with the pack's total artefact count. | Performance | High | Open |
| NFR-002 | Atomic registration | A refused or failed registration changes zero stored files; a successful one persists the fully revalidated aggregate. No partial writes are observable. | Reliability | High | Open |
| NFR-003 | Round-trip fidelity across kinds | For every in-scope kind, a registered artefact (and its relationships) read back is value-equal to what was submitted, across ordering and present-vs-absent optional fields. | Correctness | High | Open |
| NFR-004 | Contract-described surface | Every read and registration operation, and every refusal, is described by the published contract and returned as the contract's declared types and Problem format. | Interoperability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Registration, not CRUD | The write surface is functional registration only — no DELETE, PATCH, or generic CRUD. Removal from force is deactivation, out of this mission's scope (ADR-0026). | Architecture | High | Open |
| C-002 | No storage formats on the wire | Request and response bodies are the published contract types only; the internal storage serialization objects are never exposed through the API (ADR-0004/0026). | Architecture | High | Open |
| C-003 | Closed relation catalogue | Relationship types are a closed, code-defined catalogue; the API registers relationship edges between existing components, never new relationship types (ADR-0025). | Technical | High | Open |
| C-004 | Procedure out of scope | No procedure entity or endpoint is added; the procedure kind is deferred pending the #150 decision. | Scope | High | Open |
| C-005 | Pure core, edge-driven walk | The relationship/graph model stays framework- and IO-free; the on-demand walk driver lives in the application edge, honouring the onion boundaries (ADR-0006). | Architecture | High | Open |
| C-006 | Grounded in proposed ADRs | The mission is grounded in ADR-0025 and ADR-0026, currently Proposed. If either is amended before implementation, the plan must reconcile with the amendment. | Process | Medium | Open |

### Key Entities *(include if the mission involves data)*

- **Relationship**: a directed-or-bidirectional link between two components, identified by the unordered pair of its endpoints, carrying its type and an optional rationale. At most one exists per pair.
- **RelationType**: the closed catalogue of relation meanings; each one knows its directionality and how activation propagates across it.
- **Component (CharterArtifact)**: a regular charter artefact (directive, styleguide, paradigm, toolguide, tactic) that reports the relationships it originates.
- **Pack**: the aggregate that owns its artefacts and the invariants "every artefact belongs to this pack" and "no two artefacts share an id"; every write is validated against the whole aggregate.
- **Graph walk**: an on-demand traversal that visits components and their relationships, resolving each neighbour through its own kind's read path, with a cycle guard.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A client can register a directive with relationships and read back an identical directive, and the same holds for every in-scope kind (directive, styleguide, paradigm, toolguide). — [build] · no-op passable: no
- **SC-002**: Walking from a registered directive returns its related components by fetching each on demand, and reads strictly fewer components than the pack's total when only a sub-graph is reachable. — [build] · no-op passable: no
- **SC-003**: Every invalid registration (duplicate id, unresolvable `from`, unknown relation endpoint kind, over-level write, stale precondition) is refused with a typed Problem response and leaves the pack byte-for-byte unchanged, while the matching valid request on the same fixture succeeds. — [ratchet] · no-op passable: no
- **SC-004**: All four in-scope regular kinds are both readable and registerable through the published contract, with no DELETE/PATCH offered and no storage format exposed. — [build] · no-op passable: no
