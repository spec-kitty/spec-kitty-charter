# Mission Specification: Pack parse/write-back round-trip

**Mission Branch**: `feat/pack-roundtrip-io`
**Created**: 2026-10-20
**Status**: Draft
**Input**: User description: "Implement the pack parse → domain-model → write-back round-trip in the charter offering service, scoped to the pack descriptor plus one low-complexity leaf artifact kind (tactic), through a repository the domain uses to fetch and write elements. Graph shape and relationships deferred."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read a pack into domain models (Priority: P1)

A pack author keeps a pack as a directory of YAML files on the local filesystem. The charter offering service must load that pack's descriptor and its tactic artifacts as plain domain models the rest of the service can reason over, without the caller knowing anything about files or YAML.

**Why this priority**: Nothing downstream can happen until a pack can be read into the domain. This is the foundational half of the round-trip and the first thing that delivers standalone value (a fetch path a caller can use).

**Independent Test**: Point the new repository port at a fixture pack directory containing a descriptor and tactic files; fetch it; assert the returned domain models carry every modelled field, including each tactic's structured steps and structured references. Deliverable on its own: a working read path.

**Acceptance Scenarios**:

1. **Given** a filesystem pack with a valid descriptor and a tactic whose `steps` each carry a title (and some a description, examples, and nested references) and whose top-level `references` list carries full `name/type/id/when` entries, **When** the service fetches the pack through the port, **Then** the returned tactic model carries every step as a structured step and every reference as a structured value, verbatim and uninterpreted — asserted directly against the fixture's known literal values (every step field, each reference's `name/type/id/when`, and nested step references), **not** against a re-fetch (a re-fetch comparison is blind to a field the reader drops on both sides).
2. **Given** a pack directory that also contains a schema-invalid or unreadable file, **When** the pack is fetched, **Then** the valid descriptor and tactics are still returned and the invalid file is surfaced as a reported violation — a per-element, partial-tolerant granularity distinct from the all-or-nothing offering load — rather than silently dropped or crashing the fetch.
3. **Given** a descriptor-only pack (no tactic files), **When** it is fetched, **Then** the pack model returns with an empty (not null, not absent) tactic collection.

---

### User Story 2 - Write domain models back faithfully (Priority: P1)

The service must write descriptor and tactic domain models back to the filesystem so that re-reading the written output reconstructs an equal domain model. This is the write half, and together with Story 1 it closes the round-trip that is the mission's reason to exist.

**Why this priority**: The round-trip invariant is the whole point of the mission; write-back is only meaningful paired with read. Equal priority to Story 1 because neither delivers the invariant alone.

**Independent Test**: Take the domain models produced by Story 1, write them through the port to a fresh directory, re-fetch, and assert the re-read model equals the original. A paired same-fixture positive control mutates one field and asserts inequality (equality is non-vacuous); the replace-existing scenario (mutate→write→refetch returns the mutation) proves the write path itself is load-bearing.

**Acceptance Scenarios**:

1. **Given** descriptor and tactic domain models parsed from a fixture pack, **When** they are written through the port and re-fetched, **Then** the re-fetched domain model equals the original (`parse(write(parse(x))) == parse(x)`); a same-fixture control that mutates one field before the compare asserts inequality.
2. **Given** a single tactic fixture that has some optional fields present and some absent, and a `references` list containing a repeated id in a specific order, **When** it is written and re-read, **Then** present stays present, absent stays absent (no default injected; explicit-null and absent are the same state), and the references come back in the same order with the duplicate preserved (list semantics, not a de-duplicated set).
3. **Given** a target location that already holds a tactic for an id, **When** the service writes a mutated model for that same id and re-fetches, **Then** the existing element is replaced and the refetch returns the mutated model (proving the write path is load-bearing).

---

### User Story 3 - The domain reaches storage only through the repository port (Priority: P2)

Every read and write the domain performs goes through a repository port it owns; the filesystem/YAML details live in the infrastructure adapter behind that port. The layering is enforced mechanically, not by convention, and the enforcement is itself proven able to fail.

**Why this priority**: The round-trip could be demonstrated with a layering leak; this story makes the architecture directive an enforced, non-vacuous invariant so the vertical is a sound foundation for later pack work rather than a shortcut.

**Independent Test**: Run the ArchUnit layering battery; assert zero violations, and demonstrate with a known-bad control that the rule fires if an infra type/import is introduced into a domain package or the port signature.

**Acceptance Scenarios**:

1. **Given** the domain's fetch/write calls, **When** the code is inspected by ArchUnit, **Then** the domain references only the repository port and domain types — no YAML/filesystem/infra type.
2. **Given** the repository port interface, **When** its method signatures are inspected, **Then** every parameter and return type is a plain domain model (no infra DTO, no `Path`/stream type leaking the storage mechanism), and a known-bad control confirms the signature rule would go red if one were introduced.

### Edge Cases

- A tactic with `steps` omitted or empty is schema-invalid (`steps` is required, `minItems: 1`) → surfaced as a violation, not a modelled state.
- Optional-field presence vs absence must survive the round-trip in both directions on one shared fixture; explicit YAML `null` (the schema's `default: null` for `purpose`/`notes`/step `description`) and an absent key collapse to the same empty domain state.
- `references` order **and** duplicates are preserved as written (opaque list of structs, not a de-duplicated set); each reference's `name/type/id/when` is carried verbatim.
- Nested step references carry the same struct shape and the same verbatim/opaque treatment as top-level references.
- A descriptor-only pack round-trips with an empty (not null) tactic collection; a multi-tactic pack round-trips with the same members in the same order.
- `PackDescriptor.undeclaredKeys` holds key *names* without their values, so it cannot be re-emitted on write; it is therefore excluded from the round-trip equality contract for this vertical and the proving fixture's descriptors carry none (carrying undeclared-key values is a deferred follow-up). This is distinct from `active_charter`/`pack_drg`, which the schema *declares* and which this mission now models (above) so they survive write-back.
- Scalar typing must be pinned: unquoted `schema_version: 1.0` parses as a float and `x: null` as null, so the reader's stringification and the writer's re-emission must round-trip stably; fixtures quote such scalars. (All modelled tactic/descriptor fields are strings or string lists.)
- Absent-list and empty-list collapse to the same empty domain state (single-sourced with the optional-scalar null==absent rule above); this is load-bearing for FR-006/FR-009/NFR-002.
- Write is new surface: a crash mid-write must not leave a partial/corrupt file, and a write must refuse any path escaping the pack root.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Fetch descriptor into a domain model | As the charter service, I want to fetch a pack's descriptor as a domain model through the new port so that callers read packs without touching files. | High | Open | [build] | no |
| FR-002 | Fetch tactics into structured domain models | As the charter service, I want a pack's tactics fetched as domain models — each carrying its structured steps and structured (verbatim) references — so that leaf doctrine is fully available in the domain, not just an id/kind stub. | High | Open | [build] | no |
| FR-003 | Write descriptor back via the port | As the charter service, I want to write a descriptor domain model back to the filesystem through the port so that changes persist. | High | Open | [build] | no |
| FR-004 | Write tactics back via the port | As the charter service, I want to write tactic domain models (steps and references included) back to the filesystem through the port so that changes persist. | High | Open | [build] | no |
| FR-005 | Semantic round-trip equality | As a maintainer, I want re-reading written output to yield a domain model equal to the original so that no modelled information is lost in a round-trip. | High | Open | [build] | no — non-vacuity proven by a same-fixture control that mutates one field, **writes, and refetches**, asserting the refetch differs from the unmutated baseline (routed through the port, never an in-memory `equals()` tautology); write-is-load-bearing shares FR-007's mutate→write→refetch. Reader-drop blindness is covered separately by FR-002's literal oracle. |
| FR-006 | Preserve optional presence, reference order and duplicates | As a maintainer, I want one shared fixture — some optional fields present, some absent, and a references list with a repeated id — to round-trip with presence/absence, order and duplicates intact so that equality is faithful, not normalized away. | High | Open | [build] | no — single shared fixture carries both the present and the absent halves |
| FR-007 | Replace an existing element on write | As the charter service, I want writing an element for an existing id to replace it, and a subsequent fetch to return the updated model, so that the port fetches and writes *existing* elements, not only new ones. | Medium | Open | [build] | no |
| FR-008 | Partial-tolerant fetch surfaces invalid files | As a maintainer, I want the single-pack fetch to return valid descriptor/tactics while reporting an invalid or unreadable file as a violation — a per-element granularity distinct from the offering load's all-or-nothing refusal — so that a bad file neither hides nor crashes the pack. | Medium | Open | [build] | no — paired with a valid-file positive control on the same fixture |
| FR-009 | Collection and descriptor-only fidelity | As a maintainer, I want the tactic collection to round-trip with the same members and order, and a descriptor-only pack to round-trip with an empty (not null) collection, so that collection shape is faithful. | Medium | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Enforced, non-vacuous onion layering | ArchUnit layering battery passes with zero violations (no infrastructure type/annotation in a `charter-domain` package; the new port's signatures expose only domain types) AND a **committed** known-bad control points the same production rule at a dedicated known-bad sample class and asserts a violation is reported (not a manual/commented-out toggle). Belt-and-suspenders atop the existing `HexagonalArchitectureTest.theCoreIsPlainJava`, which already forbids non-`java`/jspecify dependencies in domain. | Maintainability | High | Open |
| NFR-002 | Round-trip coverage + pinned equality semantics | 100% of the descriptor and tactic elements in the proving fixture round-trip to an equal domain model. Equality is structural record equality over modelled fields; all collections (steps, references, tactics) are order- and duplicate-sensitive value lists, never sets; explicit-null and absent collapse to one empty state; `undeclaredKeys` is excluded from the equality contract and the fixture descriptors carry none. | Correctness | High | Open |
| NFR-003 | Build stays green under existing gates | `./gradlew verifyAll` on Java 25 passes, including palantir-java-format style check and the existing pack-schema validation, with no new gate regressions. | Reliability | Medium | Open |
| NFR-004 | Safe write path | Element writes are atomic (write-temp-then-rename or equivalent — a crash mid-write leaves no partial/corrupt file) and refuse any path that escapes the pack root. Atomicity is proven via a failure-injection seam (fail between temp-write and rename; assert the target is untouched and no stray temp remains); the path-escape refusal is paired with a same-fixture in-root-path-accepted positive control. The id→path mapping is resolved in the **adapter** (locate-by-id), not carried on the domain model. | Reliability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Local filesystem source only | Only the local-filesystem pack source is in scope; no network, service-stored, or published-artifact source kinds. | Technical | High | Open |
| C-002 | Descriptor + tactic only; graph deferred | Only the descriptor and the tactic artifact kind are modelled and round-tripped. Graph shards, relationships, and the domain graph shape are deferred to the future creed/values epic; glossary, mission-step-contract, agent-profile, and asset kinds are out of scope. | Technical | High | Open |
| C-003 | Reuse plumbing; model/writers/tactic-reader are new | Reuse the schema/validation and YAML plumbing (`PackSchemas`, `ParsedYaml`, `YamlDocument`/`ScalarWriter`) and the onion layering. The Tactic domain model, the tactic-content reader (tactics are read today only as `ArtifactFacts(file, kind, id)`), the descriptor writer and the tactic writer are all NEW — the only existing writer, `GraphShardWriter`, writes graph shards and is not reused here. Do not add or change the REST or GraphQL surface. | Technical | High | Open |
| C-004 | Semantic, not byte-identical; content carried opaquely | Fidelity is semantic domain-model equality, not byte-identical text. Tactic references are carried verbatim as structured values (`name`, `type`, `id`, `when`) and steps as structured values (`title`, `description`, `examples`, nested `references`); none of it is interpreted or modelled as typed domain relationships — carrying the struct opaquely is precisely how "relationships deferred" (C-002) is honoured even though `TacticReference.type` names an artifact-kind relationship. | Technical | High | Open |
| C-005 | Independent of the paused design mission and core | The vertical does not depend on core #6372 nor on the paused design mission `pack-registration-distribution-01M56M49`; it must not pre-empt that mission's decisions on sources, trust, or distribution surface. | Business | Medium | Open |
| C-006 | New aggregate and new port, distinct from existing | Introduce a NEW descriptor+tactics pack aggregate and a NEW repository port. Do not reuse `CharterPack` (which requires a non-null `DoctrineGraph`, re-introducing the deferred graph scope) and do not overload `CharterPackRepository` (whose `load()→CharterOffering` is all-or-nothing). The new port's fetch is single-pack and partial-tolerant; its write is per-element by id. | Technical | High | Open |

### Key Entities *(include if the mission involves data)*

- **Pack (domain, new aggregate)**: a pack identity plus its descriptor and its ordered tactic collection, from one filesystem location. Distinct from the existing `CharterPack` aggregate (which requires a graph).
- **Pack descriptor (domain)**: the existing `PackDescriptor`, **extended** to model `active_charter` and `pack_drg` — both optional pack-relative *path strings* the schema declares but the current model silently drops (leaving them neither modelled nor in `undeclaredKeys`, so a from-scratch writer would delete them from disk). Its `undeclaredKeys` field (names without values) remains outside the equality contract; the proving fixture includes a control descriptor carrying `active_charter`/`pack_drg` so the modelling is proven. Extending the shared record ripples to `PackDescriptorReader` and existing constructor callers — in scope.
- **Tactic (domain, new)**: required `id`, `schema_version`, `name`, and a non-empty ordered list of steps; optional `purpose`, `failure_modes`, `applies_to_languages`, `references`, `notes`.
- **Tactic step (domain, new)**: required `title`; optional `description`, ordered `examples`, and nested `references`.
- **Tactic reference (domain, new, carried opaquely)**: `name`, `type` (artifact-kind token), `id`, `when` — carried verbatim, never interpreted as a domain relationship.
- **Pack repository (port, domain-owned, new)**: fetches a single pack's descriptor + tactics from a location (partial-tolerant: returns valid elements, reports violations) and writes a single element back by id (replace-existing). Signatures speak only domain models. Distinct from `CharterPackRepository`.
- **Filesystem pack repository (infra adapter)**: the `adapter-out-yaml` implementation of the port; parses and serializes YAML behind it, reusing `PackSchemas`/`ParsedYaml`/`YamlDocument`/`ScalarWriter`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every descriptor and tactic element in the proving fixture pack round-trips to an equal domain model, with a same-fixture control proving the equality check can fail and FR-007's mutate→write→refetch proving the write path is load-bearing. — [build] · no-op passable: no
- **SC-002**: The domain performs 100% of its pack fetch/write through the new repository port — zero direct infrastructure references from any `charter-domain` package, as proven by ArchUnit with a known-bad control showing the rule fires. — [build] · no-op passable: no
- **SC-003**: `./gradlew verifyAll` (including ArchUnit layering and palantir-java-format style) is green with no new gate regressions. — [ratchet] · no-op passable: yes — a green build is the regression ratchet; non-vacuity comes from the new SC-001/SC-002 tests
