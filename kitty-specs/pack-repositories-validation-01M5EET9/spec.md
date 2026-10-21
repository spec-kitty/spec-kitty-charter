# Mission Specification: Pack repositories and validation module

**Mission Branch**: `feat/pack-repositories-validation`
**Created**: 2026-10-21
**Status**: Draft
**Input**: Operator review of the closed PR #138 (pack descriptor + tactic round-trip), rebuilt as the **cornerstone** reference for the service's DDD + hexagonal style. Four review concerns — unwired adapter, wide constructors, non-transparent port, scattered validation — expanded through design dialogue into: a DDD aggregate (pack root / tactic child entity) with identifier value objects; storage-transparent per-type repositories implemented at a persistence edge (DIP); a shared framework-free validation module; a pluggable serialization strategy with a DSO persistence shape; screaming-architecture module renames; and first-class DDD docs/glossary.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read and write a pack element by domain identity, format-transparently (Priority: P1)

A charter-service developer reads a single tactic (or the pack descriptor) into a domain model and writes it back, addressing it by **domain identity** (`PackIdentifier` + `TacticIdentifier`), with no filesystem path, YAML, or adapter type in the call.

**Why this priority**: the core review steer and the seam the registration-distribution work will build on. Without it the mission delivers nothing.

**Independent Test**: through `TacticRepository` (`save(Tactic)` / `load(PackIdentifier, TacticIdentifier)`) and `PackDescriptorRepository` (`get`/`save`) over a fixture pack, assert the returned/round-tripped domain model — with only domain types in the port signatures.

**Acceptance Scenarios**:
1. **Given** a pack containing tactic `alpha`, **When** `load(packId, alphaId)` is called, **Then** it returns the populated `Tactic` and the port signature names only domain types.
2. **Given** a mutated `Tactic`, **When** `save(tactic)` then `load(packId, id)`, **Then** the refetched entity reflects the mutation.
3. **Given** an id the pack does not contain, **When** `load` is called, **Then** it returns `Optional.empty()` and does not throw.
4. **Given** a resolved write target outside the pack root (e.g. via symlink), **When** `save` runs, **Then** it is refused before any bytes are written and the target is untouched.

---

### User Story 2 - The semantic round-trip survives, proven at the persistence edge (Priority: P1)

A maintainer must be sure the reshape preserves the behavioural invariant: parse → write-back → re-parse reconstructs an **equal** representation — proven on the **value-equal DSO** at the adapter, not on the entity's identity equality.

**Why this priority**: the one load-bearing invariant; the reshape must preserve it exactly.

**Independent Test**: fetch a fixture pack's descriptor + tactics, map to DSOs, write to a fresh directory holding none of their content, refetch, and assert DSO equality (order/duplicate-sensitive, incl. `active_charter`/`pack_drg`); plus a mutation control.

**Acceptance Scenarios**:
1. **Given** a fixture pack, **When** its descriptor and tactics are written to a fresh directory and refetched, **Then** the refetched **DSOs** equal the originals (order + duplicates + `active_charter`/`pack_drg` preserved).
2. **Given** a mutation written through the port, **When** the element is refetched, **Then** the change is visible (equality is falsifiable, not a tautology).

---

### User Story 3 - Load the whole pack as one aggregate; bad files are reported, not thrown (Priority: P1)

A caller loads the pack as one DDD aggregate via an aggregating repository that composes the leaf repositories; a schema-broken file is reported through a separate inspection query while the valid elements still load.

**Why this priority**: the aggregate is the DDD root and the partial-tolerant read is a documented behaviour of #138 that must carry over.

**Independent Test**: the aggregating repository returns a `Pack` composed of the descriptor + tactics; with one broken tactic file, the valid elements load and the violation surfaces via the inspection query (not from `load`).

**Acceptance Scenarios**:
1. **Given** the leaf repositories over a fixture pack, **When** the aggregating repository loads it, **Then** it returns one `Pack` aggregate (descriptor + tactics).
2. **Given** a pack with a schema-broken tactic file, **When** it is loaded, **Then** the valid elements are present and the violation is reported via the inspection query, not thrown from `load`.
3. **Given** a pack with a missing/unreadable `pack.yaml`, **When** the descriptor is fetched, **Then** it is refused (a snapshot cannot exist without its descriptor).

---

### User Story 4 - State invariants once, fail-fast or collect-all (Priority: P2)

A developer in any module (including the plain-Java domain) states a constraint once and applies it either **fail-fast** (`guard`, throws on first breach) or **collect-all** (`check`, accumulates every breach), via a shared framework-free `validation` module.

**Why this priority**: unifies the guard vocabulary (review point 4); repositories can ship before every call site migrates.

**Independent Test**: the `validation` module exposes `Constraint`/`ModularRuleset` with `guard()`/`check()`; a module (incl. the domain) depends on it and the amended purity suite stays green.

**Acceptance Scenarios**:
1. **Given** a ruleset, **When** `guard(value)` meets a breach, **Then** it throws immediately naming the breached constraint (a bespoke, non-generic exception).
2. **Given** the same ruleset, **When** `check(value)` meets two breaches, **Then** the result reports both.
3. **Given** the adapter read path, **When** a pack has one broken tactic, **Then** breaches are collected (not thrown) and surface as inspection violations.

---

### User Story 5 - Readable construction; screaming structure; DDD documented (Priority: P2)

Entities are built with fluent builders (value objects are records); the modules scream intent (`domain`, `application`, `persistence`, `web`, `validation`); and the DDD approach is captured for future implementers.

**Why this priority**: review point 2 + the operator's "cornerstone — implementers mimic existing code" directive. Ergonomics/structure/docs set the precedent the codebase propagates.

**Independent Test**: entities expose only a fluent builder (package-private all-args ctor for adapter reconstruction); value objects/DSOs are records; modules are renamed with ArchUnit layer labels updated and green; the glossary + guides exist and document the specific rules.

**Acceptance Scenarios**:
1. **Given** the `Tactic` entity, **When** code outside its package constructs it, **Then** only the fluent builder is reachable.
2. **Given** a value object (`TacticReference`), **When** constructed, **Then** it is a record with value equality.
3. **Given** the renamed modules, **When** `verifyAll` runs, **Then** the ArchUnit layer rules (now `Domain`/`Application`/`Persistence`/`Web`/`Validation`) pass.

### Edge Cases
- Tactic id absent → `load` returns `Optional.empty()`, never throws.
- Schema-broken / non-UTF-8 / non-YAML tactic file → excluded from `load`, surfaced by the inspection query; descriptor + valid tactics still load.
- Missing/unreadable `pack.yaml` → descriptor fetch refused.
- `save` target resolving outside the pack root (symlink) → refused before any write; target untouched.
- `save` of an element id not yet on disk → out of scope (replace-existing only, C-008); refused deterministically, not silently created.
- Undeclared top-level `pack.yaml` key → captured on read, dropped on write-back (pinned, documented); round-trip equality is scoped to declared content so this is not a contradiction.
- Multiple packs configured (`List<Pack>`) → pack context is an explicit `PackIdentifier`, so one port-impl serves all packs (no per-pack singleton needed).

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Per-type repositories by domain identity | As a developer, I want `TacticRepository` (`save(Tactic)` / `load(PackIdentifier, TacticIdentifier)`) and `PackDescriptorRepository` (`get`/`save`), so that I read/write one element by domain identity without touching storage. | High | Open | [build] | no |
| FR-002 | Storage-transparent port signatures | As a caller, I want the port interfaces to name only domain types (identifiers are domain value objects, never infra), so that the persistence format is transparent. | High | Open | [build] | no — paired with a known-bad control port that names an infra type and must red the ArchUnit rule |
| FR-003 | Absent id returns empty, not error | As a caller, I want `load` of an unknown id to return `Optional.empty()`, so lookups are total. | High | Open | [build] | yes — paired on the same fixture with a present-id row that returns a value |
| FR-004 | Partial-read violations via inspection query | As a maintainer, I want schema/parse violations reported through a separate inspection query, not `load`, so single-element reads stay clean while bad files stay discoverable. | High | Open | [build] | no |
| FR-005 | Aggregating repository assembles the Pack | As a caller, I want an aggregating repository that composes the leaf repositories into the `Pack` aggregate root, so I can load the whole pack through one seam. | High | Open | [build] | no |
| FR-006 | Shared framework-free validation module | As a developer, I want a `validation` module offering `guard()` fail-fast and `check()` collect-all over `Constraint`/`ModularRuleset`, so invariants are stated once and reused. | High | Open | [build] | no |
| FR-007 | Validation applied (bounded) | As a maintainer, I want the entity/value-object constructor guards and the adapter read-path collect-all expressed through the validation vocabulary, so validation is uniform and fail-fast. | Medium | Open | [build] | no |
| FR-008 | Entities via fluent builder + package-private ctor | As a developer, I want `Tactic` and the `Pack` aggregate built via a public fluent builder with a package-private all-args constructor, so no public wide constructor exists. | Medium | Open | [build] | no — paired positive control: the builder constructs an equal entity and the adapter reconstructs via the package-private ctor |
| FR-009 | Value objects / DSOs as records; typed identifiers | As a developer, I want value objects, DSOs and parameter classes (`TacticStep`, `TacticReference`, `PackPaths`, `TacticDso`, `PackIdentifier`, `TacticIdentifier`) as records, so flat carriers stay small and value-equal. | Medium | Open | [build] | no |
| FR-010 | Spring bean wiring through the production path | As the service, I want the edge port-impl wired (`@Service`/scanned) and a wiring test that round-trips through the **wired bean**, so the capability is live, not dead code. | High | Open | [build] | no |
| FR-011 | Serialization strategy + DSO shape | As a maintainer, I want a `SerializationStrategy` with one concrete `YamlSerializationStrategy`, and DSO records as the persistence shape, so the format is pluggable and transparent to the port-impl. | Medium | Open | [build] | no |
| FR-012 | Semantic round-trip on the DSO | As a maintainer, I want `fetch(write(fetch(x)))==fetch(x)` asserted at the persistence edge on the value-equal DSO (scoped to declared content), so fidelity is proven without abusing entity identity. | High | Open | [ratchet] | no — paired with the write→refetch mutation control |
| FR-013 | Screaming-architecture rename (both edges) | As a contributor, I want `adapter-out-yaml → persistence` and `adapter-in-rest → web` (modules, packages, ArchUnit layer labels `Domain`/`Application`/`Persistence`/`Web`/`Validation`), so the structure screams intent. | Medium | Open | [ratchet] | no — the full existing suite is the control that behaviour is unchanged under the new names |
| FR-014 | Validation purity gated by ArchUnit | As a maintainer, I want `theCoreIsPlainJava` amended to admit the `validation` package AND a new rule gating `validation → {java, jspecify}` only, with a committed known-bad control, so the kernel's purity is enforced, not assumed. | High | Open | [build] | no — paired with the known-bad would-import control |
| FR-015 | Glossary: Domain vs Persistence Entity | As a contributor, I want glossary entries headlined by **Domain Entity vs Persistence Entity**, plus value object, aggregate root, port/adapter, projection, DTO vs DSO, identifier VO, so the vocabulary is authoritative. | Medium | Open | [build] | no |
| FR-016 | DDD + architecture docs | As a contributor, I want `docs/development/code-style-and-conventions.md`, a "How to DDD here" guide, and an "Architectural Patterns Applied" page (with the C4 + class diagrams and the renamed-module ↔ pattern-role mapping), so the house style is documented and enforceable. | Medium | Open | [build] | no |
| FR-017 | Changelog entry | As a contributor, I want a `docs/changelog` entry recording the new `validation` module, the repository reshape, and the screaming-architecture rename, so the structural change is traceable. | Low | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Validation module framework-free | The `validation` module compiles against `java.*` + `org.jspecify` only (zero third-party imports), proven by an ArchUnit rule with a committed known-bad control; `charter-domain` depends on it with the amended purity suite green. | Architecture | High | Open |
| NFR-002 | No regression in untouched read paths | The untouched seams (`CharterPackRepository`, `ConsistentLoad`, listing/action-context queries) behave identically. The old `PackElementRepository`/`PackSnapshot` port and its `PackElement*Test` battery are explicitly **replaced/migrated** (the round-trip invariant moves to the new repositories' tests) — not held to "unchanged". | Reliability | High | Open |
| NFR-003 | Writes atomic and pack-contained | Every `save` writes temp → `ATOMIC_MOVE` and refuses a resolved target outside the pack root; proven by a failure-injection control (target unchanged, no stray temp) and a symlink-escape control with an in-root positive control. | Safety | High | Open |
| NFR-004 | Round-trip fidelity preserved | DSO round-trip holds for descriptor + tactics, order/duplicate-sensitive, incl. `active_charter`/`pack_drg`, verified by a literal-value oracle plus a write→refetch mutation control; equality scoped to declared content (undeclared-key drop is a pinned exception). | Correctness | High | Open |
| NFR-005 | Rename preserves behaviour | After the rename, `./gradlew verifyAll` is green (modulo the pre-existing open-packs pin), and every ArchUnit rule that hardcoded `rest`/`yaml`/`networknt` packages is updated and still biting (known-bad controls green). | Maintainability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Lean vendored validation port | The `validation` module is a lean, jspecify-only port of `modular-validators` core (dual `guard()`/`check()`); commons-lang3 removed, the description-only `Constraint.equals` fixed, `Evidence`/dynamic-descriptions/`dsl` omitted. Exact keep/drop manifest lives in the plan. | Technical | High | Open |
| C-002 | Stereotype at the edge, port framework-free | No Spring Data. The domain port is a plain interface; the stereotype annotation (`@Service` now, `@Repository` for a future DB sibling) lives only on the edge concretization. | Technical | High | Open |
| C-003 | Onion layering + DIP | No infra types in the domain; storage returns domain types; the persistence edge implements domain-owned ports (arrows inward); ArchUnit layering + purity enforced with a committed known-bad control. | Architecture | High | Open |
| C-004 | Core invariant | The semantic round-trip is the behavioural contract; byte-identical output is explicitly NOT required. | Business | High | Open |
| C-005 | Type-nature modelling rule | Records for value objects / data-structs / parameter & DTO/DSO classes (builder only where a record is wide); final classes + public fluent builder + package-private all-args ctor for entities (identity + invariants + lifecycle). | Technical | High | Open |
| C-006 | Vendored, attributed, not a dependency | `modular-validators` is vendored (its deps + SNAPSHOT/GitHub-Packages publishing make it unconsumable for domain purity); no Gradle dependency on it; Apache-2.0 attribution + a took/changed ledger. | Technical | High | Open |
| C-007 | Successor to closed #138 | Supersedes the closed PR #138 on a fresh branch; independent of core #6372 (recorded `not-applicable`). | Process | Medium | Open |
| C-008 | Replace-existing writes only | `save` replaces an existing element by id; creating a brand-new on-disk element remains deferred to the registration-distribution mission. | Technical | Medium | Open |
| C-009 | Aggregate boundary | The pack is the aggregate root (identity `PackIdentifier`); `Tactic` is a child entity (identity `TacticIdentifier`, identity-based equals). Relationships/graph are deferred (the graph is a projection; a relationship will be a value object). | Technical | High | Open |
| C-010 | Rename via occurrence map | The screaming-architecture rename is a DIRECTIVE_035 bulk edit driven by an `occurrence_map.yaml` (all 8 categories); format-specific `Yaml…` class names are kept. The ADR-0006 glob-rule vs flat-package-test divergence is reconciled (decision recorded in the plan). | Technical | Medium | Open |

### Key Entities *(include if the mission involves data)*
- **Pack** (*aggregate root*): identity `PackIdentifier`; composes `PackDescriptor` + `Tactic`s; the lightweight pack aggregate (not the graph-coupled `CharterPack`).
- **Tactic** (*child entity*): identity `TacticIdentifier` within the pack; identity-based equals; holds its `PackIdentifier`; owns `TacticStep`/`TacticReference` value objects; built via fluent builder.
- **PackDescriptor, TacticStep, TacticReference, PackPaths, PackIdentifier, TacticIdentifier** (*value objects*, records).
- **TacticDso / PackDescriptorDso** (*persistence shape*, records) — carry the round-trip fidelity assertion.
- **Constraint, ModularRuleset, Rationale, Reason, Evaluation** (*validation module*).
- **TacticRepository, PackDescriptorRepository, aggregating pack repository, inspection query** (*domain ports*); **DomainRepositoryService** (edge `@Service` impl); **SerializationStrategy / YamlSerializationStrategy** (edge strategy).

## Success Criteria *(mandatory)*

### Measurable Outcomes
- **SC-001**: A caller fetches/saves a tactic or descriptor by domain identity with zero filesystem/YAML/adapter types in the repository interface signatures. — [build] · no-op passable: no
- **SC-002**: `charter-domain` (and any other module) depends on the `validation` module with the domain-purity ArchUnit suite green and zero non-jspecify third-party imports in the module. — [build] · no-op passable: no
- **SC-003**: The semantic round-trip `fetch(write(fetch(x)))==fetch(x)` holds through the reshaped repositories, asserted on the value-equal DSO (order/duplicate-sensitive, incl. `active_charter`/`pack_drg`). — [ratchet] · no-op passable: no
- **SC-004**: Every entity is built through a fluent builder with no public wide all-args constructor, and every value object/DSO is a record — verified by construction-shape controls. — [build] · no-op passable: no
- **SC-005**: The modules scream intent (`domain`/`application`/`persistence`/`web`/`validation`), the renamed ArchUnit layer rules pass, and the DDD glossary + guides document the rules. — [ratchet] · no-op passable: no
