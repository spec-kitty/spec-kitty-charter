# Mission Specification: Charter conceptual model, user journeys and scenarios

**Mission Branch**: `ccr-cf5d5d31-obgsju` (draft PR #199)
**Created**: 2026-10-26
**Status**: Draft
**Mission**: documentation
**Input**: Formalize the conceptual model agreed in conversation, add the user journeys with diagrams
and BDD scenarios, move the charter glossary into this repository, and record the design changes in
architecture pages and ADRs.

## Documentation Scope

**Iteration Mode**: `mission_specific` (the charter's conceptual model, its user journeys and the
design changes they imply; not the whole docsite).
**Target Audience**:
- **Governance leads and pack authors** (decide and author governance; non-implementers): the
  conceptual model and the journeys.
- **Charter service contributors** (Java engineers): the logical model, ADRs and scenarios.
- **Contract consumers and core maintainers** (integrators): the journeys that cross the service
  boundary and their scenarios.

Mixed-audience flag: the journey pages serve all three. Each journey page names its primary
reader in its frontmatter (`audience`), as every page in this docsite does.
**Selected Divio Types**: explanation (conceptual model, journeys, ADRs), reference (glossary,
logical model), and executable specification (BDD scenarios).
**Languages Detected**: Markdown docsite (doc-kitty convention), Mermaid diagrams, Gherkin
scenarios run by the Java service's Cucumber suite.
**Generators to Use**: none new. The glossary stops being generated from core.

### Source of truth

`docs/context/conceptual-model.md` on this branch holds every decision the operator made in the
concept sessions of 2026-10-26. Discovery is complete: this mission records those answers and does
not re-ask them.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Know what each concept means (Priority: P1)

A governance lead or pack author reads one page and learns what a Charter Pack, the offering, the
active charter, a requirement, integrity, a paradigm, a procedure, a mission type, a role, a
profile and a creed mean, and how they relate, without reading any field, file or class.

**Why this priority**: every later design choice (domain model, DTOs, storage, API) is judged
against this meaning.

**Independent Test**: a reader who has not taken part in the concept sessions can answer "what
does a derived pack may do to an artifact it builds on, and when is that refused?" from the page
alone.

**Acceptance Scenarios**:

1. **Given** the conceptual model page, **When** a reader looks up any concept named above,
   **Then** they find its meaning, what it is for, how it relates to the others, and what it is not.
2. **Given** the page, **When** a reader searches it for fields, file names or classes, **Then**
   they find none outside a clearly separated pointer to the architecture section.

---

### User Story 2 - See how people use the charter (Priority: P1)

A reader follows one of eleven user journeys from a persona's goal to its outcome, with a diagram
of who calls what.

**Why this priority**: the journeys test the model against real use and drive the API design.

**Independent Test**: each journey page answers who, why, which steps, which service operations,
and which of those exist today.

**Acceptance Scenarios**:

1. **Given** a journey page, **When** a reader opens it, **Then** it shows the persona, goal,
   steps, a Mermaid sequence or flow diagram, the operations it needs marked as existing,
   envisioned or missing, and a link to its BDD scenarios.

---

### User Story 3 - Check the journeys as executable scenarios (Priority: P2)

A service contributor runs the contract scenarios; every journey has scenarios that reference it,
and the ones whose surfaces are missing are tagged `@WIP` and do not run.

**Why this priority**: scenarios turn the journeys into the acceptance net for later slices.

**Independent Test**: the Cucumber suite passes with the new files present, and every new scenario
file names its journey.

**Acceptance Scenarios**:

1. **Given** the scenario files, **When** the suite runs, **Then** `@WIP` scenarios are excluded and
   every scenario that runs passes.
2. **Given** a journey page, **When** a reader follows its scenario link, **Then** the scenario file
   names that journey in a comment.

---

### User Story 4 - Find terms in this repository's glossary (Priority: P2)

A reader looks up a charter term (ethos, creed, tension, impacts, operator, fallback profile, role
pin, removal, activation context, …) in the glossary this repository owns.

**Independent Test**: every term the conceptual model introduces has a glossary entry, and the
glossary is no longer regenerated from core.

---

### User Story 5 - Trace design changes to decisions (Priority: P2)

A contributor sees which accepted decisions changed (ADR-0009, ADR-0012, ADR-0014, ADR-0026,
ADR-0027) and why, and which core decision needs amending later (core ADR `2026-10-03-1`).

**Independent Test**: each changed rule in the conceptual model points to the ADR or amendment that
records it.

### Edge Cases

- A concept whose current implementation contradicts the model (steps naming profiles, core's
  weighted profile scoring): the model states the target meaning; the gap is recorded, not hidden.
- A journey whose surface does not exist yet: its scenarios are `@WIP`, never deleted or skipped
  silently.
- The glossary terms core still uses (`in_tension_with`, `impacts` as a relation): kept as
  deprecated or superseded entries pointing to the new terms, not dropped.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status | Delivery | No-op passable? |
|---|---|---|---|---|
| FR-001 | `docs/context/conceptual-model.md` states every agreed concept by meaning only (purpose, relations, what it is not), with the guiding principles first; working-note research findings move to an appendix and representation notes move to the architecture section. | open | [build] | no |
| FR-002 | `docs/context/user-journeys/` holds one page per journey, eleven in total, each with persona, goal, steps, a Mermaid sequence or flow diagram, its service operations marked exists / envisioned / missing, and a link to its scenario file. | open | [build] | no |
| FR-003 | `contracts/charter/scenarios/` holds BDD scenarios concretizing every journey; each file references its journey in a comment; scenarios whose surfaces are missing are tagged `@WIP`. | open | [build] | no |
| FR-004 | The Cucumber runner excludes `@WIP` scenarios, and the contract scenarios README documents the tag. | open | [build] | no |
| FR-005 | The charter glossary is owned by this repository: the core import is retired, and the glossary carries every term the conceptual model introduces or renames, with superseded terms marked and pointing to their replacements. | open | [build] | no |
| FR-006 | `docs/architecture/logical-model.md` becomes the translation from concept to technical representation and absorbs the representation notes (role value object, activation resolve and register operations, mission-type layout, the `CharterArtifact` home question). | open | [build] | no |
| FR-007 | A new ADR renames the relationships: `impacts` is an artifact's per-value list and `tension` is the relationship between two rules, superseding the relation naming in ADR-0009 and ADR-0014. | open | [build] | no |
| FR-008 | ADR-0012 is amended: a derived pack may remove `open` and `extendable` artifacts, only `locked` is fully protected, and role locks live in the pack-root policy file. | open | [build] | no |
| FR-009 | ADR-0026 is amended: removal (pack-wide, by a derived pack) is distinct from deactivation (per project). | open | [build] | no |
| FR-010 | ADR-0027 is amended: a `tension` relationship may exist alongside one structural relationship between the same two artifacts. | open | [build] | no |
| FR-011 | The needed amendment of core ADR `2026-10-03-1` (local and hosted deployment options) is recorded in this repository; core is not changed. | open | [build] | no |
| FR-012 | Every changed rule in the conceptual model links to the ADR or amendment that records it. | open | [build] | no |

### Non-Functional Requirements

| ID | Requirement | Status |
|---|---|---|
| NFR-001 | All eight `check_docs.py` checks, markdownlint-cli2 and cspell pass on the changed pages with 0 findings. | open |
| NFR-002 | The conceptual model page contains 0 field names, file names or class names outside its pointer to the architecture section. | open |
| NFR-003 | The Cucumber contract suite passes with 0 failures and 0 undefined steps among scenarios that run. | open |
| NFR-004 | Every page uses the canonical terminology: 0 `terms` findings; "Mission", never "feature". | open |

### Constraints

| ID | Constraint | Status |
|---|---|---|
| C-001 | No change to the core repository (`spec-kitty/spec-kitty`) or to `spec-kitty-open-packs`. | active |
| C-002 | No change to service production code; the only test-side change is the `@WIP` exclusion. | active |
| C-003 | No change to the OpenAPI contract in this mission. | active |
| C-004 | Values and creeds: concepts only; the numeric layer stays gated by experiment #2538 and is not specified. | active |
| C-005 | Commercial (Brain Kitty) capabilities are named only where the open-core boundary needs them; RACI-style org structures are not described. | active |

### Key Entities

- **Conceptual model page**: meaning of every concept.
- **User journey page**: one persona goal, its steps, diagram and operations.
- **Scenario file**: Gherkin concretizing one journey.
- **Glossary entry**: one term with definition, status and related terms.
- **ADR / amendment**: one recorded decision and its consequences.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 11 of 11 journeys have a page, a diagram and a scenario file that names the journey. — [build] · no-op passable: no
- **SC-002**: 100% of concepts introduced or renamed in the working notes have a glossary entry. — [build] · no-op passable: no
- **SC-003**: The docs checks and the contract suite pass with 0 findings and 0 failures. — [build] · no-op passable: no
- **SC-004**: A reviewer outside the concept sessions can answer five sample questions (derived-pack removal, profile selection order, what an upgrade changes, where the active charter lives, what a paradigm is) from the conceptual model alone. — [build] · no-op passable: no

### Quality Gates

- Heading hierarchy is proper; no broken links; spelling passes.
- Diagrams are Mermaid, in the page (diagram-as-code).
- An adversarial review squad reviews the conceptual model and ADRs before the PR leaves draft
  (project charter: architecture and domain changes).

## Assumptions

- **ASM-001**: The operator's answers recorded in `docs/context/conceptual-model.md` are final for
  this mission; new questions found while formalizing are raised, not decided silently.
- **ASM-002**: The Cucumber engine honours a tag filter set in the test configuration.
- **ASM-003**: The existing glossary content imported from core stays as the starting point and is
  edited here from now on.

## Out of Scope

- Implementing any new service operation, schema or domain change.
- Changing core's glossary, ADRs or code; changing open-packs content.
- The numeric values layer (interaction matrix, ripple arithmetic, ranking).
- Reclassifying shipped paradigms and procedures (handled in open-packs).
