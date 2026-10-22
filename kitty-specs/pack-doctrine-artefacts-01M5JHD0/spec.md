# Mission Specification: Pack doctrine artefacts — directive, styleguide, paradigm

**Mission Branch**: `feat/pack-doctrine-artefacts`  
**Created**: 2026-10-22  
**Status**: Draft  
**Input**: Model the directive, styleguide and paradigm charter artefacts as first-class pack child entities with full YAML round-trip, mirroring the established `Tactic` pattern, unified by a shared `CharterArtifact` interface.

## User Scenarios & Testing *(mandatory)*

The charter service today models only the **tactic** as a rich pack entity; the
other charter artefacts are read as generic facts and cannot be loaded, validated
or written back faithfully. This mission gives three more kinds — **directive**,
**styleguide**, **paradigm** — the same first-class treatment, each preserving the
distinct purpose the glossary assigns it, and introduces a thin `CharterArtifact`
family interface so the aggregate, repositories, DRG and round-trip can bind to
the family without erasing each kind's identity.

Canonical shapes come from **spec-kitty-open-packs** (the source of truth);
validation mirrors core per **ADR-0017**. The work continues the
screaming-architecture packs slice on current `main` (**ADR-0006**).

### User Story 1 - Directive as a first-class pack entity (Priority: P1)

A pack author authors a **directive** (a binding governance rule) in a pack's
YAML. The storage adapter loads it into the domain as a typed `Directive` entity,
validates its invariants, and serializes it back field-for-field.

**Why this priority**: The directive is the normative core of the doctrine family
— tactics, procedures and paradigms all ultimately carry out directives. Loading
it faithfully is the highest-value slice and is independently shippable.

**Independent Test**: Load every directive in the open-packs fixture corpus into
`Directive`, serialize back, and assert field-wise equivalence; assert invariant
violations are rejected with a precise message.

**Acceptance Scenarios**:

1. **Given** a valid directive YAML entry, **When** the storage adapter loads it, **Then** a `Directive` is produced carrying `intent`, `enforcement`, `scope`, `procedures`, `integrity_rules`, `validation_criteria`, `explicit_allowances` and `references`, and serializing it back yields a field-equivalent entry.
2. **Given** a directive missing a required field (`id`, `schema_version`, `title`, `intent` or `enforcement`), **When** it is validated, **Then** the `ModularRuleset` rejects it naming the violated invariant.
3. **Given** a loaded `Directive`, **When** it is fetched through its repository port by `DirectiveIdentifier`, **Then** the same entity is returned (identity equality).

---

### User Story 2 - Styleguide as a first-class pack entity (Priority: P1)

A pack author authors a **styleguide** (conventions for the shape of a work
product). The storage adapter loads it as a typed `Styleguide` entity — carrying
its scope, principles, patterns/anti-patterns, tooling, quality test and
structural lint config — validates it, and serializes it back faithfully.

**Why this priority**: The styleguide is the richest of the three (machine-lintable
config) and governs how work products are shaped; faithful round-trip is required
for the service to read and emit it without loss.

**Independent Test**: Round-trip every styleguide in the open-packs corpus with
zero field drift, including the nested `tooling` object and `structural_lint_config`.

**Acceptance Scenarios**:

1. **Given** a valid styleguide YAML entry, **When** the storage adapter loads it, **Then** a `Styleguide` is produced carrying `scope`, `principles`, `patterns`, `anti_patterns`, `tooling`, `quality_test`, `structural_lint_config`, `applies_to_languages` and `references`, and serializing it back yields a field-equivalent entry.
2. **Given** a styleguide missing a required field (`id`, `schema_version`, `title`, `scope` or a non-empty `principles`), **When** it is validated, **Then** the `ModularRuleset` rejects it naming the violated invariant.
3. **Given** a loaded `Styleguide`, **When** it is fetched through its repository port by `StyleguideIdentifier`, **Then** the same entity is returned.

---

### User Story 3 - Paradigm as a first-class pack entity (Priority: P1)

A pack author authors a **paradigm** (a way of working, e.g. domain-driven design)
that points at the directives it is realized by. The storage adapter loads it as a
typed `Paradigm` entity, validates it, and serializes it back faithfully.

**Why this priority**: The paradigm is the leanest kind (`summary` + `directive_refs`)
but completes the "easy" prose tier and exercises cross-artefact references as DRG
edges, de-risking the deferred kinds.

**Independent Test**: Round-trip every paradigm in the open-packs corpus with zero
field drift, and assert each `directive_refs` entry surfaces as a DRG edge.

**Acceptance Scenarios**:

1. **Given** a valid paradigm YAML entry, **When** the storage adapter loads it, **Then** a `Paradigm` is produced carrying `name`, `summary`, `directive_refs` and `references`, and serializing it back yields a field-equivalent entry.
2. **Given** a paradigm missing a required field (`schema_version`, `id`, `name` or `summary`), **When** it is validated, **Then** the `ModularRuleset` rejects it naming the violated invariant.
3. **Given** a loaded `Paradigm`, **When** its `edges()` are read, **Then** each `directive_refs` entry appears as a DRG edge to the referenced directive.

---

### User Story 4 - The CharterArtifact family interface (Priority: P2)

A consumer of the domain (the aggregate, the DRG builder, a generic report) treats
directive, styleguide, paradigm and tactic uniformly through a `CharterArtifact`
interface exposing only the genuine common spine, while each concrete entity keeps
its distinct body.

**Why this priority**: The interface earns its keep by normalizing the few true
universals (`label`, `description`, `edges`, `urn`) so family-wide code binds once;
it is additive and depends on US1–US3 existing, so it is P2.

**Independent Test**: Assert each of the four entities is assignable to
`CharterArtifact` and that `label()`/`description()`/`edges()`/`urn()` return the
kind-correct normalized values; assert `Tactic`'s pre-existing behaviour is
unchanged.

**Acceptance Scenarios**:

1. **Given** a `Directive`, `Styleguide`, `Paradigm` or `Tactic`, **When** it is viewed as `CharterArtifact`, **Then** `kind()` returns the correct `ArtifactKind`, `label()` returns the entity's `title` or `name`, `description()` returns `intent`/`summary`/`purpose` (empty for styleguide), and `urn()` returns `Urn(NodeKind.of(kind), localId)`.
2. **Given** a `Tactic` retrofitted to implement `CharterArtifact`, **When** its existing tactic-only behaviour (steps, failure modes) is exercised, **Then** it is unchanged (regression-pinned).

### Edge Cases

- **Absence vs empty (the fidelity bar).** Round-trip fidelity is DSO-record field-equivalence after canonical normalization — not YAML byte-equivalence. Per the Tactic precedent (`YamlDumpEmitter` omits empty lists and absent optionals; the DSO normalizes absent→empty on read), an explicit `procedures: []` and an omitted `procedures` both normalize to the empty list and compare equal at the DSO-record level. Only genuinely nullable *scalars* (`directive.scope`, `styleguide.quality_test`, `styleguide.tooling`, `styleguide.structural_lint_config`) carry an `Optional` present/absent distinction.
- A styleguide with an absent `tooling` map or `structural_lint_config` round-trips as absent (no invented default); a present-but-empty one round-trips as present-and-empty.
- A paradigm `directive_refs` entry naming a directive absent from the pack still loads: the edge is recorded; referential resolution is a DRG concern, not a load-time invariant. A bare-string ref must be non-empty, because `Urn` rejects an empty id.
- An unknown field in a directive/styleguide/paradigm YAML entry is rejected (closed-world) via the bundled schema's `additionalProperties:false`, exactly as the tactic loader validates — drift against the canonical schema is caught, not silently dropped.
- `description()` on a styleguide returns `Optional.empty()`, and `edges()` on a styleguide returns the empty list: a styleguide's `references` are bare citation strings with no target kind, so they round-trip as *body*, never as DRG edges. Consumers must not assume every artefact has a one-line gist or any edges.
- `directive.scope` (free-text, nullable → `Optional<String>`) and `styleguide.scope` (a required enum) share a name but are different types and must not be unified.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Directive entity + identity | As a pack author, I want a directive loaded as a typed `Directive` child entity with a `DirectiveIdentifier` value object, package-private all-args constructor and public fluent chainable builder, so that it has first-class identity in the domain. | High | Open | [build] | no |
| FR-002 | Directive invariants | As a maintainer, I want directive invariants enforced by a `ModularRuleset<Directive>` mirroring the canonical schema — required `id`/`schema_version`/`title`/`intent`/`enforcement`; `enforcement` a member of `Enforcement` (`required`\|`lenient-adherence`\|`advisory`); and the conditional that `enforcement == lenient-adherence` requires a non-empty `explicit_allowances` — so that malformed directives are rejected with a named violation. | High | Open | [build] | no — paired with a valid-directive positive control on the same fixture |
| FR-003 | Directive round-trip | As a pack author, I want a `Directive` serialized back to YAML field-for-field via a DSO, so that load→serialize is field-equivalent for every modelled field. | High | Open | [build] | no |
| FR-004 | Directive repository port | As a domain consumer, I want a `DirectiveRepository` port in the domain implemented by the storage adapter, so that directives are fetched by `DirectiveIdentifier` and saved through a stable boundary. | High | Open | [build] | no |
| FR-005 | Styleguide entity + identity | As a pack author, I want a styleguide loaded as a typed `Styleguide` child entity (identifier VO + package-private ctor + fluent builder) carrying its scope/principles/patterns body, so that it has first-class identity. | High | Open | [build] | no |
| FR-006 | Styleguide invariants | As a maintainer, I want styleguide invariants enforced by a `ModularRuleset<Styleguide>` — required `id`/`schema_version`/`title`; non-empty `principles`; `scope` a member of the enum (`code`\|`docs`\|`architecture`\|`testing`\|`operations`\|`glossary`); and `patterns`/`anti_patterns` non-empty when present — so that malformed styleguides are rejected with a named violation. | High | Open | [build] | no — paired with a valid-styleguide positive control on the same fixture |
| FR-007 | Styleguide round-trip | As a pack author, I want a `Styleguide` serialized back field-for-field via a DSO — including the structured `patterns`/`anti_patterns` objects, the `tooling` string map and the arbitrary nested `structural_lint_config` tree (representation per FR-018) — so that load→serialize is field-equivalent. | High | Open | [build] | no |
| FR-008 | Styleguide repository port | As a domain consumer, I want a `StyleguideRepository` port in the domain implemented by the storage adapter, so that styleguides are fetched by `StyleguideIdentifier` and saved through a stable boundary. | High | Open | [build] | no |
| FR-009 | Paradigm entity + identity | As a pack author, I want a paradigm loaded as a typed `Paradigm` child entity (identifier VO + package-private ctor + fluent builder) carrying `name`/`summary`/`directive_refs`, so that it has first-class identity. | High | Open | [build] | no |
| FR-010 | Paradigm invariants | As a maintainer, I want paradigm invariants enforced by a `ModularRuleset<Paradigm>` (required `schema_version`/`id`/`name`/`summary`), so that malformed paradigms are rejected with a named violation. | High | Open | [build] | no — paired with a valid-paradigm positive control on the same fixture |
| FR-011 | Paradigm round-trip | As a pack author, I want a `Paradigm` serialized back field-for-field via a DSO, so that load→serialize is field-equivalent. | High | Open | [build] | no |
| FR-012 | Paradigm repository port | As a domain consumer, I want a `ParadigmRepository` port in the domain implemented by the storage adapter, so that paradigms are fetched by `ParadigmIdentifier` and saved through a stable boundary. | High | Open | [build] | no |
| FR-013 | CharterArtifact interface | As a domain consumer, I want a `CharterArtifact` interface in `packs.domain` (it returns `PackIdentifier`, a packs type, so it cannot live in the pure `kernel`) exposing `kind()`, `packId()`, `localId()`, `schemaVersion()`, `label()`, `description()`, `edges()` (returning kernel `DrgEdge`s — no new edge type), and a default `urn()`, so that the four kinds bind to the family through one thin spine. | High | Open | [build] | no |
| FR-014 | Per-kind CharterArtifact implementations | As a domain consumer, I want `Directive`/`Styleguide`/`Paradigm` to implement `CharterArtifact` by normalizing their own fields (`title`\|`name`→`label`; `intent`\|`summary`→`description`, empty for styleguide; edges per FR-019), so that family-wide code reads kind-correct values. | High | Open | [build] | no |
| FR-015 | Tactic retrofit | As a maintainer, I want the existing `Tactic` retrofitted to implement `CharterArtifact` (`label`→`name`, `description`→`purpose`, `edges`→references) while keeping its tactic-only `steps`/`failureModes`/`appliesToLanguages`, so that the whole family is uniform and `Tactic`'s current behaviour is preserved. | Medium | Open | [ratchet] | no — reverting the retrofit leaves `Tactic` non-assignable to `CharterArtifact`, which the family test catches |
| FR-016 | Closed-world field handling | As a maintainer, I want an unknown field in a directive/styleguide/paradigm YAML entry rejected (consistent with the tactic loader), so that drift against the canonical schema is caught rather than silently dropped. | Medium | Open | [build] | no — paired with a known-field positive control on the same fixture |
| FR-017 | Typed Enforcement | As a maintainer, I want a typed `Enforcement` value with token round-trip mirroring `ArtifactKind.token()` (`REQUIRED`↔`"required"`, `LENIENT_ADHERENCE`↔`"lenient-adherence"`, `ADVISORY`↔`"advisory"`), so that the directive's enforcement level is strongly typed and both the enum invariant (FR-002) and field-equivalence fall out naturally. | High | Open | [build] | no |
| FR-018 | Nested-config fidelity | As a pack author, I want `styleguide.structural_lint_config` (an arbitrary nested tree of scalars/lists/maps, `additionalProperties:true`) and `tooling` (string map) represented by a raw-value-preserving, order-stable structure with a defined equality, so that they round-trip with no type or nesting loss — closing the gap where the Tactic serialization toolkit (scalar/strings/mappings, text-coercing reader) cannot. The representation choice is a planning spike (see Complexity Tracking / research). | High | Open | [build] | no |
| FR-019 | Per-kind edge derivation | As a domain consumer, I want each kind's `edges()` derived explicitly: directive = typed `references` (`{type,id,when?,reason?}`) + bare `procedures` strings (target kind `procedure`); paradigm = bare `directive_refs` strings (target kind `directive`) + typed `references` (`{type,id}`); tactic = typed `references`; styleguide = empty. Each edge carries a `Relation` and a target `Urn`, and bare-string refs must be non-empty — so the DRG view is correct per kind and no bare citation is mistaken for an edge. | High | Open | [build] | no — paired with a styleguide-has-no-edges control |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Round-trip fidelity | Loading then serializing any directive/styleguide/paradigm in the open-packs fixture corpus is field-equivalent at the DSO-record level after canonical normalization (absent ≡ empty for non-nullable lists; present/absent preserved for nullable scalars): 0 field drift across every modelled field, including nested objects, the string map and the arbitrary `structural_lint_config` tree. | Correctness | High | Open |
| NFR-002 | Validation parity (ADR-0017) | Every top-level `required` field, format pattern (`id` and `schema_version` per kind), enumerated constraint (`Enforcement`; styleguide `scope`), cardinality rule (`principles` non-empty; `patterns`/`anti_patterns` non-empty when present) and conditional (`lenient-adherence` ⇒ non-empty `explicit_allowances`) in the canonical open-packs schema for each kind has a corresponding `ModularRuleset` or identifier-VO rule, each with a named violation. | Correctness | High | Open |
| NFR-003 | Architecture gate green | New types respect screaming-architecture layering (domain depends on no infra/framework; per-type repository ports in the domain, adapters in infra.storage): `HexagonalArchitectureTest` passes, including `SLICE_COVERAGE`/`EVERY_SLICE_IS_REGISTERED`. | Maintainability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Canonical source of truth | Field sets and invariants derive from the canonical schemas in spec-kitty-open-packs; no field is invented beyond the canonical schema for a kind. | Technical | High | Open |
| C-002 | DDD house style | Follow the established pattern from pack-repositories-validation: typed identifier value object, package-private all-args constructor + public fluent chainable builder, identity equality, DSO serialization with field-wise fidelity, per-type repository port in the domain implemented by the storage adapter. | Technical | High | Open |
| C-003 | Canonical naming | The family and its interface are named `CharterArtifact` — "doctrine" is no longer canonical — and code uses the American spelling `artifact`. | Technical | High | Open |
| C-004 | Scope boundary | Only directive, styleguide and paradigm are in scope. Toolguide and procedure (same family, fast follow-up), and assets, agent profiles and the mission_step_contract / mission_type system are explicitly out of scope. | Business | High | Open |

### Key Entities

- **CharterArtifact** (`packs.domain` interface): the family supertype. It returns `PackIdentifier` (a packs concept), so it lives in `packs.domain`, not the pure `kernel`. Spine: `ArtifactKind kind()`, `PackIdentifier packId()`, `String localId()`, `String schemaVersion()`, `String label()`, `Optional<String> description()`, `List<DrgEdge> edges()`, default `Urn urn()` (= `new Urn(NodeKind.of(kind()), localId())`). Captures only the genuine universals; each kind keeps its own body. `edges()` reuses the kernel `DrgEdge` (source/target `Urn`, `Relation`, when, reason) — no new edge type is introduced.
- **Directive** (`packs.domain`): a binding governance rule. Body: `title`, `intent`, `Enforcement enforcement`, `Optional<String> scope` (free text), `procedures[]` (bare strings), `integrity_rules[]`, `validation_criteria[]`, `explicit_allowances[]`, `references[]` (`DirectiveReference{type, id, when?, reason?}`). Identity: `DirectiveIdentifier` (id `^[A-Z][A-Z0-9_-]*$`; `schema_version` `^1\.0$`). `description()`=`intent`; edges from typed `references` + bare `procedures`.
- **Styleguide** (`packs.domain`): conventions for the shape of a work product. Body: `title`, `scope` (required enum `code|docs|architecture|testing|operations|glossary`), `principles[]` (non-empty), `patterns[]` (`Pattern{name, description, bad_example?, good_example?}`), `anti_patterns[]` (`AntiPattern{name, description, bad_example, good_example}` — all required), `Optional tooling` (string map), `Optional<String> quality_test`, `Optional structural_lint_config` (arbitrary nested tree, FR-018), `applies_to_languages[]`, `references[]` (bare citation strings — body). Identity: `StyleguideIdentifier` (id `^[a-z][a-z0-9-]*$`; `schema_version` `^1\.0$`). `description()`=empty; `edges()`=empty.
- **Paradigm** (`packs.domain`): a way of working. Body: `name`, `summary`, `directive_refs[]` (bare strings, target kind `directive`), `references[]` (`ParadigmReference{type, id}`). Identity: `ParadigmIdentifier` (id `^[a-z][a-z0-9-]*$`; `schema_version` `^1\.0$`). `description()`=`summary`.
- **Enforcement** (`packs.domain` value): the directive enforcement level — `REQUIRED` / `LENIENT_ADHERENCE` / `ADVISORY` — with token round-trip mirroring `ArtifactKind.token()`.
- **Tactic** (existing, `packs.domain`): a practice that carries out directives. Retrofitted to implement `CharterArtifact`; keeps tactic-only `steps[]`, `failureModes[]`, `appliesToLanguages[]`. `label()`=`name`, `description()`=`purpose`, edges from typed `references`.
- Per-kind **DSO** records mirror each kind's YAML flatly (record equality = the fidelity assertion), each with its own nested `Reference`/`Pattern` records — the per-kind reference shapes differ, so none reuses `TacticDso.Reference`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every directive, styleguide and paradigm in the open-packs fixture corpus round-trips (load→serialize) with zero field drift. — [build] · no-op passable: no
- **SC-002**: A pack loaded via the storage adapter exposes its directives, styleguides and paradigms through their per-type repository ports, fetchable by typed identifier. — [build] · no-op passable: no
- **SC-003**: All four kinds (directive, styleguide, paradigm, tactic) are assignable to `CharterArtifact` and return kind-correct `label()`/`description()`/`edges()`/`urn()`; `Tactic`'s pre-existing behaviour is unchanged. — [build] · no-op passable: no
- **SC-004**: `HexagonalArchitectureTest` (including `SLICE_COVERAGE`) and the full `verifyAll` gate pass on the branch head. — [ratchet] · no-op passable: no
