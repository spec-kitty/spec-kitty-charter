# Mission Specification: Slice F: the kind taxonomy and the mission-type schema

**Mission Branch**: `kitty/mission-slice-f-kind-taxonomy-01M7B19K` (lands on `slice-f-537-kind-taxonomy`, then `main` by pull request)
**Created**: 2026-11-13
**Status**: Draft
**Input**: Slice F ([#537](https://github.com/spec-kitty/spec-kitty-charter/issues/537)): #112 (R18), #514, #520, #515
and #533, plus #350 by ruling. The grounding note is [`research.md`](research.md).

## Intent Summary (confirmed by the operator, 2026-11-13)

Three people use this model:

- a **pack author**, who writes a pack's mission types and graph documents;
- a **client of the charter contract**, such as core's generated client after the cutover;
- **spec-kitty-open-packs**, which follows the model.

**The problem today:**

- The client cannot read which kinds and relations exist, or which kinds a relation may join. It re-derives them from
  hand-kept lists.
- A mission type's steps are authored in two places: the mission-type document, and a separate `mission_step_contract`
  artifact that holds each step's substeps.
- Any relation may join any two authored kinds.

**What this mission does:**

- serves the kind taxonomy as data;
- makes the mission-type step the single authoring surface, by folding the step contract's substeps into it and
  retiring the kind outright;
- gives each relation the source and target kinds it may connect, typed in the domain and refused at read and at
  write;
- retires the relations that have no place in the model.

The charter's model is canonical: old shapes are replaced outright, and open-packs follows.

**Decision Moments resolved at specify** (operator interview, 2026-11-13):

- **`DM-01M7B1YE4J84K0T4ZA27M48QKG` (R18 shape).** The taxonomy is served as `GET /offering/kinds`, with two lists:
  kinds, and relations with their allowed endpoints.
  - It holds none of core's org facts (`effective_when_absent`, `selection_overlayable`, `org_requirable`).
  - It is not deferred to the merge minor.
- **`DM-01M7B1YH1GF15RD0G4ZPSA8KW9` (retire the step contract).** The step contract's substeps fold into the
  mission-type step.
  - The substeps are a flat, ordered list, one level deep.
  - Every field is kept: `id`, `description`, `command`, `inputs`, `delegates_to` and `guidance`.
  - A delegation is kept as data. Each of its candidates must also have a `scope` edge from the step's action.
  - Then the kind is retired.
  - #513 stays open, narrowed to recursion and `DECOMPOSES_INTO`.
- **`DM-01M7B1YKY84DQHZFDXEWNJGB5E` (`template_set`).** `template_set` is dropped from the charter schema outright.
- **`DM-01M7B1YQR5ZKQPDW6DX64KPNRN` (type-to-step edges).** The `mission_type -requires-> action` edge stays authored
  and must be complete: one edge per step, on-demand steps included. A pack that misses one is refused at load.
- **`DM-01M7B1YTXP6QR1NQB784H4VX3W` (endpoint table).** The endpoint table of research R6 is adopted as drafted,
  recorded in an ADR-0064 amendment.
- **`DM-01M7B1YXJHQCBSYJ145NJC77V5` (retired relations).** `impacts`, `replaces` and `delegates_to` are retired.
  `replaces` was sometimes used to mean `overrides`, so a `replaces` edge migrates to `overrides`.
- **`DM-01M7B1Z03VF9A36FBV65431HR3` (#447).** Slice C delivers #447 (its FR-014).
  - F folds C's `OverrideSourceRule` into the endpoint rule.
  - That fault keeps C's violation code `overrides_source_not_activatable`.
  - Every other endpoint fault is `relationship_endpoint_not_allowed`.
- **`DM-01M7B1Z30AV54T9WM2WR61EK30` (#350).** #350 is included: a procedure edge to an action is refused by the table.

**Ruled before this mission:**

- ADR-0064 decision 13 (operator, 2026-11-12) sets the mechanism for #515.
- ADR-0054 decision 12 moves the one step-contract gate onto the foundation lifecycle's `in_progress -> for_review`
  transition.
- The operator ruled on 2026-11-13 that implementation starts only after slice C (#467) merges, or rebases onto it.
- #514's text asks for the change to land behind core #3420 and #3419. charter.md forbids core-first ordering, so
  this mission does not wait on core.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read the kind taxonomy (Priority: P1)

A client reads one resource and learns:

- every kind the model knows: its token, its plural, whether it is an artifact, whether an activation selects it,
  and whether a graph document may name it;
- every relation: whether it is directed or derived, and which source and target kinds it may join.

The client no longer keeps its own copy of these lists.

**Why this priority**: R18 is one of the last undecided cutover reads (epic #94), and #515's table is only useful to
a client if it is served.

**Independent Test**: Read `/offering/kinds` against a running service and compare it with the domain's kind and
relation enums.

**Acceptance Scenarios**:

1. **Given** a running service, **When** a client reads `/offering/kinds`, **Then** it answers `200`. The answer
   lists one row per node kind and one row per relation, in the model's order. Each relation row lists its allowed
   `{source, target}` kind pairs.
2. **Given** the model's enums, **When** a parity test compares them with the served table, **Then** every kind,
   plural, flag and endpoint pair matches. A deliberately skewed copy fails the test, as a positive control.
3. **Given** the read, **When** the client looks for `mission_step_contract`, `impacts`, `replaces` or
   `delegates_to`, **Then** none appears.

---

### User Story 2 - Author a step's substeps in the mission type (Priority: P1)

A pack author writes a step's substeps (each with a description, a command, inputs, a delegation and guidance)
inside the mission-type document, next to the step. There is no separate step-contract file. A client reads the
substeps with the step.

**Why this priority**: it makes the step the single authoring surface (#520's intent and core's retirement of the
legacy step contract), and it is the precondition for retiring the kind (#514).

**Independent Test**: Load a pack whose mission type authors substeps, read the step, and see the substeps in
authored order. Load a pack that still ships a `*.step-contract.yaml`, and see it refused.

**Acceptance Scenarios**:

1. **Given** a mission type whose step authors two substeps, **When** the step is read in a pack or under an
   activation, **Then** the answer carries both substeps in authored order, with every field as written.
2. **Given** a substep that delegates to a kind and candidates, **When** the pack is loaded, **Then** it is refused
   in either of two cases, each with a named violation:
   - a candidate does not resolve to an artifact of that kind;
   - the step's action has no `scope` edge to the candidate.
3. **Given** a pack that still contains a `*.step-contract.yaml` file or a `mission_step_contract.graph.yaml`
   fragment, **When** it is loaded or registered, **Then** it is refused as a retired file kind.
4. **Given** a request for any `mission_step_contract` path, kind filter or selection, **When** it reaches the
   service, **Then** it is refused as an unknown kind or path. No step-contract operation, link, schema, enum value or
   refusal code remains.

---

### User Story 3 - A disallowed edge is refused at read and at write (Priority: P1)

A pack author writes an edge whose source and target kinds the relation does not allow, for example
`procedure -requires-> action` (#350) or an `overrides` edge between two kinds.

- On write, the service refuses it and names the edge.
- On read, a stored pack that holds such an edge is refused, so a read and a write never disagree.

**Why this priority**: ADR-0064 decision 13 makes this part of the model. Without it, every kind can be joined to
every other kind.

**Independent Test**: Register one invalid example pack per disallowed combination, and `PUT` the same edge through
`putPackRelationship`. Both are refused with the named violation.

**Acceptance Scenarios**:

1. **Given** an edge the table does not allow, **When** it is written through `putPackRelationship` or a whole-pack
   registration, **Then** the answer is `422 validation_failed` with the violation `relationship_endpoint_not_allowed`.
   The violation names the relation and both kinds, and nothing is written.
2. **Given** a stored pack holding such an edge, **When** a composing read meets it, **Then** the answer is
   `pack_unreadable`, naming the document and the edge. Validate mode reports the same edge as a blocking finding.
3. **Given** an `overrides` edge whose source kind is not activatable, **When** it is written or read, **Then** it is
   refused with slice C's violation `overrides_source_not_activatable`, as slice C ships it.
4. **Given** an `overrides` edge between two different activatable kinds, **When** it is written or read, **Then** it
   is refused with `relationship_endpoint_not_allowed`.
5. **Given** the domain, **When** code builds a relationship whose kinds the relation does not allow, **Then** it does
   not compile: the constructors are typed by their endpoint kinds.

---

### User Story 4 - A mission type's chain is complete and its schema closed (Priority: P2)

A pack author's mission type carries:

- one `mission_type -requires-> action` edge per step, on-demand steps included;
- no authored `action_sequence` or `template_set`.

A pack that breaks either rule is refused with a named reason. The project charter carries no `template_set`.

**Why this priority**: #520's rules hold in the model today, but no test proves the refusals. The type-to-step chain
is incomplete in open-packs: 21 edges for 47 steps.

**Independent Test**: Load one invalid example pack per rule and see each refused. Load the corrected example packs
and see them accepted.

**Acceptance Scenarios**:

1. **Given** a mission type with a step that has no `requires` edge from its type, **When** the pack is loaded,
   **Then** it is refused with a named violation that names the type and the step.
2. **Given** a mission type that authors `action_sequence` or `template_set`, **When** the pack is loaded, **Then**
   it is refused as an unknown field, by its name.
3. **Given** a project charter that carries `template_set`, **When** it is read, **Then** it is refused as an
   unknown field.

---

### User Story 5 - Retired relations are gone (Priority: P2)

`impacts`, `replaces` and `delegates_to` disappear from the model, the contract and the pack schemas.

- A graph document that still uses one is refused as an unknown relation.
- A former `replaces` edge is written as `overrides`.

**Why this priority**: each relation must have an endpoint row or go (ADR-0064 decision 13). These three have none
in the model: ADR-0028 retired `impacts`, `replaces` is legacy, and no authored `delegates_to` edge exists.

**Independent Test**: Load a pack that uses each retired relation, and see each refused as an unknown relation.

**Acceptance Scenarios**:

1. **Given** a graph document with an `impacts`, `replaces` or `delegates_to` edge, **When** the pack is read or the
   edge is written, **Then** it is refused as an unknown relation.
2. **Given** charter's example packs and fixtures, **When** the round trip and the contract scenarios run, **Then**
   none uses a retired relation, except an error-handling fixture for that refusal.

### Edge Cases

- **A delegation candidate held by another pack of the closure.** It resolves through the qualified form
  `<pack>::<kind>:<id>`, as other cross-pack references do.
- **An on-demand step.** It needs its `requires` edge like a step in the run order.
- **A `replaces` edge in a stored pack.** It is refused as an unknown relation. Rewriting it to `overrides` is a
  content migration, done in open-packs, not by the service.
- **An edge that breaks two rules.** An `overrides` edge from a non-activatable kind to a different kind reports
  `overrides_source_not_activatable` only, since slice C's rule is checked first. The order is fixed so that tests
  are deterministic.
- **A self-qualified source** (`<thispack>::<kind>:<id>`). It is judged by its kind, like an unqualified one.
- **Glossary.** If #106 has landed, the `vocabulary` target kind is `glossary`; otherwise it is `glossary_pack`. The
  table follows `ArtifactKind`.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status | Delivery | No-op passable? |
| --- | --- | --- | --- | --- |
| FR-001 | A client reads the kind taxonomy at `GET /offering/kinds` (#112). It returns a `kinds` list (kind, plural, artifact, activatable, authoredInGraphs, links to the kind's list reads where they exist) and a `relations` list (relation, directed, derived, allowed `{source, target}` kind pairs). Both lists are in model order. | Approved | [build] | no |
| FR-002 | The taxonomy carries no `effective_when_absent`, `selection_overlayable` or `org_requirable` fact, and the read's deferral to the merge minor is removed from the contract documentation. | Approved | [build] | no |
| FR-003 | A parity test keeps the served taxonomy equal to the domain's kind and relation model, with a skewed positive control. | Approved | [build] | no |
| FR-004 | A mission-type step may author `substeps`: an ordered, one-level list of `{id, description, command, inputs, delegates_to {kind, candidates}, guidance}`. Substep ids are unique within the step. The step reads in both trees return them in authored order. | Approved | [build] | no |
| FR-005 | Every candidate of a substep's `delegates_to` must resolve to an artifact of the named kind in the pack's closure, and the step's `action:<type>/<step>` node must have a `scope` edge to it. A pack that breaks either is refused at load and at registration, with a named violation. | Approved | [build] | no |
| FR-006 | The `mission_step_contract` kind is retired outright (#514): its kind and node values, operation (`getEffectiveMissionStepContract`), schemas, parameters, responses, examples, refusal code (`mission_step_contract_not_found`), `step-contract` link, preset and activation members, pack schema, file kinds and domain types. A `*.step-contract.yaml` file or `mission_step_contract.graph.yaml` fragment is refused as a retired file kind. | Approved | [build] | no |
| FR-007 | #533 is closed as moot, with evidence that the literal step-contract read no longer exists. | Approved | [folded] | no |
| FR-008 | Charter's example packs and fixtures move their step-contract content into step substeps. This covers the 5 files in the `built-in` example pack and the planted and digest fixtures. The `review` gate becomes the foundation lifecycle guard on `in_progress -> for_review` (ADR-0054 decision 12). | Approved | [build] | no |
| FR-009 | An authored `action_sequence` or `template_set` in a mission type is refused as an unknown field by name, proven by one invalid example pack per field and a `@contract` scenario (#520). | Approved | [ratchet] | no |
| FR-010 | `template_set` is removed from the project charter schema (#520). A charter that carries it is refused as an unknown field. | Approved | [build] | no |
| FR-011 | Every step of a mission type, on-demand steps included, has an authored `mission_type -requires-> action:<type>/<step>` edge in the pack's graph documents. A pack that misses one is refused at load and at registration, with a violation that names the type and the step. | Approved | [build] | no |
| FR-012 | Each relation names the source and target kinds it may connect, per research R6's table as drafted, recorded in an ADR-0064 amendment (#515). | Approved | [build] | no |
| FR-013 | The domain builds a relationship only through constructors typed by their endpoint kinds, so a disallowed combination does not compile. | Approved | [build] | no |
| FR-014 | An edge the table does not allow is refused on write (`putPackRelationship`, whole-pack registration) as `422 validation_failed` with the violation `relationship_endpoint_not_allowed`, naming the relation and both kinds. On read it is refused as `pack_unreadable`, and validate mode reports it as a blocking finding. | Approved | [build] | no |
| FR-015 | Slice C's `OverrideSourceRule` is folded into the endpoint rule and keeps its violation code `overrides_source_not_activatable` for an `overrides` edge whose source is not activatable. An `overrides` edge between two different kinds is `relationship_endpoint_not_allowed`. | Approved | [build] | no |
| FR-016 | A `procedure` edge whose target is an `action` is refused by the table (#350), proven by an invalid example pack. | Approved | [folded] | no |
| FR-017 | `impacts`, `replaces` and `delegates_to` are retired from the domain, the contract and the pack schemas. An edge that uses one is refused as an unknown relation. | Approved | [build] | no |
| FR-018 | Charter's example packs, fixtures and contract examples carry only edges the table allows. The `suggests styleguide -> anti_pattern` edge becomes `rejects`. The `impacts` edges and any `replaces` edges are removed or rewritten as `overrides`. Error fixtures are rewritten so each exercises one fault. | Approved | [build] | no |
| FR-019 | Every breaking change of this mission is recorded in one dated `### Breaking` fragment under `contracts/charter/changes/`. | Approved | [build] | no |
| FR-020 | Open-packs drafts are written into this mission's research directory with `open-packs#TBD-<slug>` placeholders. They cover: removing the 28 step-contract files and 2 graph fragments, moving their substeps into the mission types, adding the missing `scope` and `requires` edges, moving the gate to the lifecycle guard, retiring the three relations (a `replaces` edge becomes `overrides`), the schema change, and the validator update. | Approved | [build] | no |

### Non-Functional Requirements

| ID | Requirement | Status |
| --- | --- | --- |
| NFR-001 | `./gradlew verifyAll` passes on Java 25, with strict dependency verification and the whole-pack round trip over charter's example packs. It reports 0 failures and 0 skipped contract scenarios. | Approved |
| NFR-002 | Every refusal this mission adds is covered by at least one `@contract` scenario and one invalid example pack. The switch-gate register reports the new read `ready`. | Approved |
| NFR-003 | A scan of the main sources finds 0 occurrences of `mission_step_contract`, `MissionStepContract`, `IMPACTS`, `REPLACES` and `DELEGATES_TO` outside retired-name lists. | Approved |
| NFR-004 | No new dependency enters the build. | Approved |
| NFR-005 | Gradle runs in the foreground with `--max-workers=1`, one run at a time. | Approved |

### Constraints

| ID | Constraint | Status |
| --- | --- | --- |
| C-001 | The charter's model is canonical. No deprecation window and no legacy reader. Tests and examples use only the canonical shape, except error-handling tests (charter.md, 2026-11-10). | Approved |
| C-002 | No core-first ordering. #514's "behind core #3420/#3419" is not followed. Core aligns through the generated client. | Approved |
| C-003 | Implementation starts only on top of slice C (#467). It merges first, or this mission rebases onto it, because both change `PackPolicy`, `RelationshipRegistrationService`, `DomainRepositoryService`, `EffectiveReadService`, `MissionBodies` and `RefusalCode`. | Approved |
| C-004 | Until #554 lands, the read-side endpoint check sits where graph documents are bound: `PackPolicy`, and the load path in `DomainRepositoryService`. Whichever of F and #554 lands second rehomes it, and the wire codes do not change (ADR-0075 decision 4). | Approved |
| C-005 | ADR-0075 decision 2 is amended to drop `MissionStepContractIdentifier`, so #552 drops it from its identifier list. Whichever of F and #551 lands second rebases onto the other's `kernel.validation` API. | Approved |
| C-006 | Pack content is not written in spec-kitty-open-packs. Drafts go to this mission's research directory, and the orchestrator files them. | Approved |
| C-007 | The contract break ships in a 0.x minor (ADR-0022). | Approved |
| C-008 | Out of scope: recursive substeps and `DECOMPOSES_INTO` (#513, narrowed), step transitions (#434), core's guard reader (#339), and the `glossary` kind (#106, which the table follows). | Approved |

## Success Criteria *(mandatory)*

- **SC-001** A client reads every kind and every relation, with its allowed endpoints, from one resource. The table
  equals the model in 100% of its rows. — [build] · no-op passable: no
- **SC-002** 0 step-contract files, operations, schemas or enum values remain in charter's contract, schemas,
  example packs and main sources. — [build] · no-op passable: no
- **SC-003** Each of the 13 relations has an endpoint row: `requires`, `suggests`, `applies`, `scope`, `vocabulary`,
  `instantiates`, `refines`, `specializes_from`, `enhances`, `overrides`, `reconciles_tension`, `rejects` and
  `implemented_by`. Tension is held beside the relations as a `Tension` (ADR-0028) and is judged by the same G -> G
  rule. For each row, a disallowed combination is refused at read and at write. — [build] · no-op passable: no
- **SC-004** Charter's example packs load with 0 refusals. Each invalid example pack is refused with exactly its one
  named fault. — [build] · no-op passable: no
- **SC-005** Against the open-packs drafts, the draft table refuses 0 open-packs edges once the drafts are applied.
  This is checked by re-running the census script. — [ratchet] · no-op passable: no
- **SC-006** #112, #514, #520, #515, #533 and #350 are closed with evidence. #94 and #537 are ticked. #513 is narrowed
  with a comment. — [build] · no-op passable: no

## Key Entities

- **Kind**: a node kind of the model. It has a token and a plural, and is an artifact or not, activatable or not, and
  authored in graphs or not.
- **Relation**: a typed edge label. It is directed or not and derived or not, and has a set of allowed
  `{source kind, target kind}` pairs.
- **Mission type, step, substep**: the type's ordered `steps` (plus `on_demand_steps`). Each step has an ordered,
  one-level `substeps` list. A substep holds `description`, `command`, `inputs`, `delegates_to` and `guidance`.
- **Delegation**: a substep's `delegates_to {kind, candidates[]}`. This is runtime handoff data (ADR-0064 decision 11),
  mirrored by `scope` edges from the step's action.

## Domain Language

| Term | Meaning | Avoid |
| --- | --- | --- |
| step | an entry of a mission type's `steps` or `on_demand_steps`; its graph node is `action:<type>/<step>` | step contract |
| substep | an entry of a step's `substeps`, one level, authored in the mission type | contract step |
| template | the artifact kind only | template set, step template (for a prompt) |
| endpoint kinds | the source and target kinds a relation may join | reference type |
| kind taxonomy | the served table of kinds and relations | kind facts (core's term) |

## Assumptions

- The "scope edge per candidate" rule is enforced as a load refusal, like the step-edge rule.
- The research census stands: under the table, 0 open-packs edges and 3 example-pack edges are refused before
  migration.
- No `replaces` edge exists in charter's example packs, fixtures or open-packs (checked 2026-11-13). The migration
  rule is recorded for content that appears later.
- Slice C ships `OverrideSourceRule` and its refusal as on `slice-c/effective-step-context` at `b336e590`. If C
  changes it before merging, this mission follows C.
- The 28 open-packs step-contract files fold one-for-one into the step of their mission type that they name. Each one
  names a step of its type, including on-demand steps (checked 2026-11-13).
