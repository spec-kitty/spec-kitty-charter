# Mission Specification: Application-edge read path on the Pack model

**Mission Branch**: `feat/pack-repositories-validation`
**Created**: 2026-10-21
**Status**: Draft
**Input**: First real functional read path on the single canonical Pack model; reclassify the application layer as an outer edge adapter; per-layer composition + deployable aggregate for modular wired tests; delete the first-pass scaffolding outright.

## User Scenarios & Testing *(mandatory)*

The service so far only proved a persistence-through-domain round-trip. Its only live HTTP read endpoints (pack listing, action-context) run on first-pass `CharterPack`/`CharterOffering` scaffolding; the canonical `Pack` aggregate and its repository ports are wired but unused. This mission makes the canonical model do real work for the first time — starting with a pack's tactics — and settles the layering the rest of the service will copy.

### User Story 1 - Retrieve a pack's tactics by identity (Priority: P1)

A charter-service API caller asks for the tactics of a pack, named by its identity, and receives them — or a clean, client-facing refusal when the pack is absent or unreadable. The request is served by an application-layer read service over the canonical `Pack` aggregate and its repository ports; the controller is a thin projection that adds no business logic.

**Why this priority**: This is the first real functional capability on the canonical model and the whole reason the aggregate + ports exist. Everything else in the mission exists to support it being correct and copyable.

**Independent Test**: Stand up the wired read path (application service + repository ports + storage edge) and request the tactics of a known pack fixture through the production path; assert the returned tactics match the fixture, and that an unknown pack identity yields a client-facing not-found refusal rather than a server error.

**Acceptance Scenarios**:

1. **Given** a readable pack with two tactics, **When** a caller requests that pack's tactics by identity, **Then** both tactics are returned with their domain fields intact and in a stable order.
2. **Given** a pack identity that no stored pack declares, **When** a caller requests its tactics, **Then** the service refuses with a client-facing not-found (4xx-class) outcome, not an internal-error outcome.
3. **Given** a tactic that loads but violates a domain invariant, **When** its pack's tactics are retrieved, **Then** the violation is surfaced as a reported finding and the request does not crash.

---

### User Story 2 - One canonical model, scaffolding removed (Priority: P1)

A service contributor opening the codebase finds exactly one pack aggregate root and one set of repository ports. The first-pass round-trip scaffolding (`CharterPack`, `CharterOffering`, `CharterPackRepository`, `FileSystemCharterPackRepository`) and the endpoints that depended on it (pack listing, action-context) are gone — not deprecated, not coexisting.

**Why this priority**: This PR is the layering reference future slices copy. Two aggregate roots for the same concept (the squad's top finding) would teach copiers to wire the wrong one. Removing the scaffolding dissolves that ambiguity by deletion.

**Independent Test**: A repository-wide search finds zero references to the removed scaffolding types in `main`, and the removed routes no longer resolve; the build and the architecture gate stay green with a single aggregate root.

**Acceptance Scenarios**:

1. **Given** the merged mission, **When** the codebase is searched for the scaffolding types, **Then** none remain in production code.
2. **Given** the service is running, **When** a caller hits the former pack-listing or action-context routes, **Then** those routes are no longer served (the inbound contract no longer declares them).

---

### User Story 3 - Modular-but-wired testing via a deployable aggregate (Priority: P2)

A contributor writes a test that provisions one layer's slice wired to its real collaborators — without hand-assembling the whole application — because each layer owns its own composition configuration and a `deployable` entrypoint aggregates them.

**Why this priority**: Cheap, honest wiring tests are what keep the edge-adapter layering from rotting; they are the payoff of the per-layer configuration split.

**Independent Test**: A single modular wired test provisions the read path through the aggregate composition and exercises the production bean graph (not hand-instantiated collaborators).

**Acceptance Scenarios**:

1. **Given** the per-layer configurations and the deployable aggregate, **When** a modular wired test boots a layer slice, **Then** the real beans of that layer and its required collaborators are provisioned and the read path works end-to-end.
2. **Given** the persistence edge service, **When** the context is composed, **Then** exactly one instance of each repository port bean exists (no duplicate-bean conflict).

---

### User Story 4 - Application-as-edge layering is enforced (Priority: P2)

The domain and kernel rings stay framework-free; the application and infrastructure rings are peer outer edges that may carry framework wiring. The architecture gate enforces this and its known-bad controls still bite, so the boundary cannot silently rot, and the architecture docs match the realized code.

**Why this priority**: The reclassification of the application layer from "pure inner ring" to "outer edge adapter" is a doctrine change (ADR-0006). If the gate and docs don't move with it, the reference contradicts itself.

**Independent Test**: The revised architecture gate passes on production classes, its negative-control fixtures still fail naming their violator, and a planted framework import in the domain or kernel is rejected.

**Acceptance Scenarios**:

1. **Given** the revised gate, **When** it runs against production classes, **Then** domain and kernel carry zero framework dependencies and the application edge is permitted them.
2. **Given** a planted framework annotation in a domain or kernel class, **When** the gate runs, **Then** it fails naming that class.

### Edge Cases

- A pack identity that is not a directory name, or that resolves to a path escaping the pack root — refused fail-closed (preserve the existing symlink/path-escape protection).
- A tactic file that reads but fails a domain invariant — reported as a violation, never a crash or silent drop.
- Removal of the pack-listing / action-context endpoints — callers of those routes receive a not-served outcome; this is an intended inbound-contract change, recorded as such.
- Duplicate pack-level composition — a widened component scan must not register a repository port bean twice.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Retrieve a pack's tactics by identity | As an API caller, I want a pack's tactics by pack identity so that I can read its guidance. | High | Open | [build] | no — paired with the unknown-pack refusal row (FR-002) on the same fixture |
| FR-002 | Refuse absent/unreadable pack client-side | As an API caller, I want a missing pack to return a client-facing not-found so that I am not told it is a server fault. | High | Open | [build] | no — paired with the successful-retrieval row (FR-001) |
| FR-003 | Application-edge read service, thin API | As a contributor, I want the read logic in an application-layer service invoked by a thin controller so that business logic never lives in the entrypoint. | High | Open | [build] | no — asserted by a controller-has-no-logic check plus the wired read test |
| FR-004 | Exactly one pack aggregate root | As a contributor, I want a single canonical Pack aggregate so that the reference is unambiguous. | High | Open | [build] | no — absence test over the removed scaffolding types |
| FR-005 | Remove pack-listing & action-context endpoints | As a maintainer, I want the unmodeled endpoints removed so that no route runs on deleted scaffolding. | High | Open | [build] | no — route-no-longer-resolves test |
| FR-006 | Per-layer composition configuration | As a contributor, I want each layer to own its composition config so that layers compose independently. | High | Open | [build] | no — each layer's config provisions its beans in isolation |
| FR-007 | Deployable aggregate for modular wired tests | As a contributor, I want a deployable entrypoint aggregating the layer configs so that a modular wired test is cheap. | Medium | Open | [build] | no — at least one modular wired test boots a layer slice through it |
| FR-008 | Scan-driven persistence edge, single bean | As a contributor, I want the persistence edge service wired by component scan with no duplicate bean so that wiring has one signal. | Medium | Open | [build] | no — single-bean assertion in the wired context |
| FR-009 | Defined tactic save create-vs-update contract | As a port implementer, I want the save contract defined so that adapters do not diverge. | Medium | Open | [ratchet] | no — port-level contract test pins the create-vs-update behaviour |
| FR-010 | Single owner for pack violations | As a port implementer, I want pack violations owned by the aggregate so that the duplicate query seam cannot diverge. | Medium | Open | [build] | no — the collapsed seam is exercised through the aggregate owner |
| FR-011 | Revised architecture gate: application is an edge | As a maintainer, I want the gate to treat application as an edge ring so that the boundary is enforced after the doctrine change. | High | Open | [ratchet] | no — known-bad controls still bite; planted domain framework import is rejected |
| FR-012 | Architecture docs reflect application-as-edge | As a reader, I want ADR-0006, the hexagonal doc, the patterns doc and the C4/class diagrams updated so that the reference matches the code. | Medium | Open | [build] | no — docs name application as an edge ring; diagrams regenerated |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Point-read efficiency | Retrieving one pack's tactics must not scan unrelated packs; resolving a pack identity must not parse every tactic of every pack. Target: a single-pack tactic read touches only that pack's files; p95 under 300 ms for a pack of ≤ 50 tactics on a local store. | Performance | Medium | Open |
| NFR-002 | No new exposure | The read endpoints keep the existing bind/security posture (loopback bind per configuration); the mission adds no unauthenticated surface and exposes no write port over HTTP. | Security | High | Open |
| NFR-003 | Purity stays double-gated | Domain and kernel rings carry zero framework dependencies, enforced by both the build-time dependency rejection and the ArchUnit purity rules; violations = 0. | Architecture | High | Open |
| NFR-004 | Test economy | Every test added guards real behaviour or a standing invariant through the production entry point (DIRECTIVE_041); closeout vetting finds no RETIRE-worthy additions (no shape-pins, duplicates, or inert controls). | Maintainability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | One model, no coexistence | Exactly one pack aggregate model ships; the first-pass scaffolding is deleted outright with no deprecation period or parallel path (operator directive — the service is days old; there is no legacy). | Technical | High | Open |
| C-002 | Tactics-only read scope | Only tactic retrieval is in scope. The read model for descriptor, DRG and other pack artefacts is not yet ruled and is explicitly out of scope. | Scope | High | Open |
| C-003 | Only the domain is sacred | Domain + kernel rings MUST stay framework-free; application and infrastructure are peer outer edges permitted framework wiring. ADR-0006's pure-application rule is revised accordingly. | Technical | High | Open |
| C-004 | Inbound-contract change recorded | Removing the pack-listing and action-context endpoints changes the inbound REST contract; the OpenAPI contract and its regeneration are handled in plan and the mission records contracts as changed. | Technical | High | Open |
| C-005 | Java construction style | Public construction is via fluent chainable builders; a package-private full constructor serves serialization only; many-field types are a final class, not a public record. | Technical | Medium | Open |
| C-006 | Read-only surface | Write ports stay unexposed to HTTP this mission; their save contract is pinned by a port-level contract test, not an endpoint. | Scope | Medium | Open |
| C-007 | No heavy suites in-mission | Targeted verification only during the mission (gates, changed-file tests, the architecture battery); CI owns the full runs (NO_FULL_HEAVY_SUITES_IN_MISSION). | Process | Medium | Open |

### Key Entities

- **Pack**: the single canonical aggregate root; owns its tactics as child entities and its own invariants (every tactic belongs to the pack; no duplicate tactic identities).
- **Tactic**: a child entity of a pack, identity-equal, retrieved by this mission's read path.
- **PackIdentifier / TacticIdentifier**: value-object identities used to address a pack and its tactics.
- **Repository ports**: the domain-owned contracts for reading packs and tactics, realized at the persistence edge; their signatures name only domain/kernel/JDK types.
- **Application read service**: the application-edge concretization that orchestrates the domain + ports and is invoked by the thin API projection.
- **Deployable composition**: the aggregate configuration that `@Import`s each layer's own configuration so modular wired tests can provision slices.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An API caller retrieves a known pack's tactics by identity end-to-end through the wired production path, and an unknown identity yields a client-facing not-found — both demonstrated by a wired test. — [build] · no-op passable: no
- **SC-002**: The service presents exactly one pack aggregate model: a repository-wide search finds zero references to the removed scaffolding types, and the removed routes no longer resolve. — [build] · no-op passable: no
- **SC-003**: A contributor can provision a modular wired test of a single layer slice through the deployable aggregate, exercising real beans (at least one such test exists and passes). — [build] · no-op passable: no
- **SC-004**: The revised architecture gate enforces application-as-edge — domain/kernel framework dependencies = 0, every rule's known-bad control still fails naming its violator, and a planted framework import in domain/kernel is rejected. — [ratchet] · no-op passable: no
