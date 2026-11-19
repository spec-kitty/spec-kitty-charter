# Mission Specification: Serve tensions

**Mission Branch**: `kitty/mission-serve-tensions-01M7RDX1` (lands on `feat/602-tensions`, then by pull request to `main`)
**Created**: 2026-11-18
**Status**: Draft
**Input**: Issue [#602](https://github.com/spec-kitty/spec-kitty-charter/issues/602) and
[ADR-0045](/adr/0045-tension-beside-the-relationships-and-nodes-keep-every-declared-field/) as accepted on 2026-11-18,
with the operator's rulings D1 to D12, F1 to F3 and the example-pack ruling (decision
DM-01M7RDXACK9SVR5E3G21BD9KW2).

## Intent Summary

- **Primary actor.** A contract consumer (core, after incorporation, or any client of the service) that reads an
  activation, and a pack author whose packs declare tensions.
- **Trigger.** A consumer reads an activation's effective graph, an action's effective context, or the activation's
  tensions. A pack author loads, validates or registers a pack that holds tension edges.
- **Outcome.** The consumer sees every tension in force, the tensions that bear on one action with what reconciles
  them, and each co-activated pair nothing reconciles, with the two ways to fix it. The pack author's tensions are
  read, written and registered without loss, and an inconsistent pack is refused with a clear reason.
- **Invariant.** A tension is a relationship of its own pair class: it never replaces, and is never replaced by, the
  one structural relationship of its pair. Within one pack, a pair has at most one tension, written in canonical
  orientation.
- **Boundary.** The finding warns; it never blocks an activation. Value impacts and the creed and values work (#16)
  are out of scope.

## Domain Language

| Term | Meaning | Avoid |
|------|---------|-------|
| tension | The authored relation between two guidance rules that pull against each other; relation token `tension` | `in_tension_with`, "conflict", `impacts` −1 |
| side | One of the two rules a tension joins | "end", "pole" |
| reconciler | An artifact in force with a `reconciles_tension` edge to both sides | "arbiter" (core's word) |
| pair class | Structural (every relation but `tension`) or tension; identity is the unordered pair plus its class | — |
| canonical orientation | The smaller URN (lexicographic) is the `source` | — |
| unreconciled tension | A tension in force that no reconciler in force reconciles | "unarbitrated" (core's word) |
| finding | A reported, non-blocking observation about an activation | "warning" (that word is taken by the activation writes) |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See the tensions in force, and the unreconciled ones (Priority: P1)

A consumer reads an activation's tensions and gets every tension whose two sides are in force, each with its
reconcilers, and one finding for each tension nothing in force reconciles, naming the pair and the fixes.

**Why this priority**: It is the read #602 exists for; without it the consumer cannot warn about co-activated rules
that pull against each other.

**Independent Test**: The example pack's five activations, read through the new read; each case answers its expected
tensions and findings.

**Acceptance Scenarios**:

1. **Given** an activation with both sides of a tension in force and a reconciler of both sides in force, **When**
   the consumer reads the activation's tensions, **Then** the tension is listed with that reconciler and there is no
   finding.
2. **Given** an activation with both sides in force and no reconciler, **When** it is read, **Then** there is one
   finding `unreconciled_tension` naming the pair in canonical order, with resolutions `reconcile` and
   `deactivate_one_side` and links to exclude each side.
3. **Given** an activation with only one side in force, **When** it is read, **Then** the tension is not listed and
   there is no finding.
4. **Given** a tension registered with its sides in the non-canonical order (`from` the larger URN), **When** the
   activation is read, **Then** there is one tension and one finding, keyed on the sorted pair. (A pack file can hold
   only the canonical order, FR-004, so the other order reaches the reads through the registration write; two packs
   declaring the same pair collapse to one, FR-006.)
5. **Given** a pair joined by both `requires` and a tension, **When** it is read, **Then** both are kept: the tension
   is listed here and the `requires` edge stays in the effective graph.

### User Story 2 - The effective graph and the action context carry tensions (Priority: P1)

A consumer walking the effective graph sees each tension in force as an edge with relation `tension`, its words and
its reconcilers; an action's context lists the tensions whose two sides are both delivered to that action.

**Why this priority**: The action context is what governs one action; core's governance bundle uses this today.

**Independent Test**: The same five activations, read through the effective graph and one action context each.

**Acceptance Scenarios**:

1. **Given** a tension with both sides in force, **When** the effective graph is read, **Then** it holds an edge
   `tension` from the smaller URN to the larger, with `reconciledBy` (possibly empty) and the authored `when` and
   `reason` when written.
2. **Given** an action whose context lists both sides as in force, **When** the context is read, **Then** its
   `tensions` holds the tension with every reconciler in force, whether or not the reconciler is delivered to the
   action.
3. **Given** a `depth` that cuts one side off, **When** the context is read, **Then** the tension is absent.
4. **Given** a side that is only a suggestion (state inactive), **When** the context is read, **Then** the tension is
   absent.
5. **Given** a mission type not in force, **When** the context is read, **Then** `tensions` is empty.

### User Story 3 - Pack authors write `tension`, and inconsistent tensions are refused (Priority: P1)

A pack author writes `tension` edges in graph documents. The service reads, round-trips and registers them; it refuses
a pack whose tensions are duplicated on a pair or reversed, and it no longer reads `in_tension_with`.

**Why this priority**: The model is canonical; the reads are only as good as the tensions the packs hold.

**Independent Test**: Load fixtures for each refusal, the round trip over charter's example packs, and the
registration scenarios.

**Acceptance Scenarios**:

1. **Given** a pack with one canonical tension beside a `requires` on the same pair, **When** it loads and round-trips,
   **Then** both edges survive with their words.
2. **Given** a pack with two tensions on the same unordered pair, **When** it is loaded, validated or registered,
   **Then** it is refused, naming the pair.
3. **Given** a pack with a tension whose `source` is the larger URN, **When** it is loaded, validated or registered,
   **Then** it is refused, naming the edge.
4. **Given** a graph document that writes `in_tension_with`, **When** it is loaded, **Then** it is refused as an
   unknown relation.
5. **Given** a `putPackRelationship` with `type: tension` on a pair that already has `requires`, **When** it is
   written, **Then** both are held, the tension is stored canonically and written to `registered.graph.yaml`, and a
   later registration into the pack keeps it.
6. **Given** a pair with a registered `requires` and a registered tension, **When** the author removes it with
   `relation=tension`, **Then** only the tension goes; without `relation`, both go.

### User Story 4 - The taxonomy, references and records say `tension` (Priority: P2)

The kind taxonomy names the relation `tension`; the reference reads list tension edges as authored; the service
contract page and the divergence register describe the decided reads and each difference from core.

**Independent Test**: The taxonomy and reference scenarios; the docs checks.

**Acceptance Scenarios**:

1. **Given** the kind taxonomy, **When** it is read, **Then** the row is `tension`, `heldAs: tension`,
   `directed: false`, with the guidance-to-guidance pairs.
2. **Given** an artifact that is the source of a tension edge, **When** its references are read, **Then** the tension
   is listed with relation `tension`.
3. **Given** the governance walk from a profile, **When** it is read, **Then** no artifact is reached through a tension.

### Edge Cases

- A tension aimed at an overridden version ends at the winner; one authored on a hidden version is absent.
- Two packs of a closure each declare the tension on one pair: the first in merge order keeps its words.
- A reconciler with `reconciles_tension` to one side only does not reconcile the tension.
- A reconciler that is itself not in force is not listed.
- A conditional tension (with `when`) is reported like any other; the condition is carried, never evaluated.
- An activation with no tensions answers empty lists, never an absent member.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Tension is a relation of the one catalogue | As a maintainer, I want `tension` to be a relation of the model's one closed catalogue, bidirectional, joining guidance to guidance, carrying `when` and a reason and no strength, with the separate tension value removed, so that there is one model of an edge (ADR-0045 decision 1). | High | Open | [build] | no: a tension edge must bind as relation `tension`, which fails today |
| FR-002 | Pair-class identity | As a maintainer, I want a relationship's identity to be its unordered pair and its pair class, so that a tension and the pair's one structural relationship coexist, and each replaces only its own class on a merge or a registration (ADR-0045 decision 1, ADR-0028 decision 3). | High | Open | [build] | no: registering a tension on a `requires` pair must keep both |
| FR-003 | One spelling, `tension` | As a pack author, I want graph documents, the pack schemas, the readers, the writers, the taxonomy and the contract to spell the relation `tension`, and `in_tension_with` to be refused as an unknown relation, so that the canonical model has one spelling (ADR-0045 decision 2). | High | Open | [build] | no: an `in_tension_with` fixture must be refused |
| FR-004 | Pack tension consistency | As a pack author, I want a pack refused at load, validation and registration when it holds two tensions on one unordered pair, or a tension whose source is not the smaller URN, naming the pair or edge, so that a pack's tensions are consistent (ADR-0045 decision 6). | High | Open | [build] | no: each refusal fixture beside an accepted canonical fixture |
| FR-005 | Registration carries tensions | As a pack author, I want `putPackRelationship` to accept `type: tension`, store it canonically, keep every tension of `registered.graph.yaml` through a registration rewrite, and `deletePackRelationship` to take an optional `relation` parameter, so that a registration never drops or refuses a tension (ADR-0045 decision 5). | High | Open | [build] | no: register-then-register keeps the tension |
| FR-006 | Resolution under an activation | As a consumer, I want tensions resolved through the layer overrides like edges, put in canonical order and collapsed across packs with the first in merge order keeping its words, and a reconciler to count only when it is in force and reaches both sides, so that every read agrees on the tensions in force (ADR-0045 decision 8). | High | Open | [build] | no: the override and one-sided-reconciler cases on the example pack |
| FR-007 | Effective graph carries tensions | As a consumer, I want the effective graph to answer each tension with both sides in force as an edge `tension`, canonical, with `reconciledBy` and its authored `when` and `reason`, so that a graph walk sees the tension (ADR-0045 decision 9). | High | Open | [build] | no: the reconciled-activation graph must hold the edge |
| FR-008 | Action context carries tensions | As a consumer, I want the effective action context to answer a required `tensions` list of the tensions whose two sides are both listed in force in the context, each with every reconciler in force, limited by `depth`, empty for a mission type not in force (ADR-0045 decision 10). | High | Open | [build] | no: the context of the reconciled activation must list it |
| FR-009 | Activation tensions read | As a consumer, I want `getActivationTensions` to answer the activation's tensions in force and one `unreconciled_tension` finding per tension nothing in force reconciles, linked from the activation, with the skipped report's ETag rule, never refusing (ADR-0045 decisions 11, 12). | High | Open | [build] | no: the unreconciled activation must answer one finding |
| FR-010 | Resolution vocabulary | As a consumer, I want each finding to carry `resolutions: [reconcile, deactivate_one_side]` and links to exclude each side and to read each side, so that a client can act on it without building a path (ADR-0045 decision 12). | Medium | Open | [build] | no: the finding's links must resolve to the exclusion write |
| FR-011 | Taxonomy and references | As a consumer, I want the kind taxonomy's row renamed `tension` (`heldAs: tension`, `directed: false`), the reference reads to list tension edges as authored, and the governance walk and cascade never to follow a tension (ADR-0045 decision 13). | Medium | Open | [build] | no: the taxonomy scenario names `tension` |
| FR-012 | Example pack with five activations | As a maintainer, I want one charter example pack holding a reconciled tension, an unreconciled tension, a tension with one side out of force, a tension registered in the non-canonical order (stored canonically), and a `requires` plus a tension on one pair, with one activation per case, each with `@contract` scenarios passing the ADR-0059 D1 gate (#602 acceptance). | High | Open | [build] | no: five scenarios, each failing before the reads exist |
| FR-013 | Records | As a maintainer, I want the service-contract page to move "Tension annotation" into the decided reads and mark the stale "The other reads (expected-artifact manifests)" row (#101) as delivered (ADR-0044, PR #472), so that epic #94's done-when holds, the divergence register to record each intended difference from core (the spelling, the vocabulary, the read shape, the reconciler rule), and the contract CHANGELOG to record the changes, so that the records match the reads. | Medium | Open | [build] | no: the docs checks and the register entry |
| FR-014 | Open-packs draft | As the orchestrator, I want a draft open-packs issue in the Mission's research directory for the content migration (`in_tension_with` to `tension`, canonical orientation, duplicates), with the placeholder `open-packs#TBD-tension-rename`, so that the companion pull request can be filed. | Medium | Open | [build] | no: the draft file exists and is referenced |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Byte-stable answers | Each new or changed read answers byte-identical JSON for the same activation and packs across 3 repeated reads (sorted lists, canonical pairs). | Reliability | High | Open |
| NFR-002 | Round trip | The whole-pack round trip over charter's example packs reports 0 differences, with tension edges included. | Reliability | High | Open |
| NFR-003 | Gates | `./gradlew verifyAll` on Java 25 passes with 0 failures, and `check_docs.py all`, markdownlint and cspell report 0 findings. | Quality | High | Open |
| NFR-004 | No added cost on activations without tensions | A read of an activation whose closure holds no tension answers in no more than 110% of the time of the same read before the change, measured on the existing contract-scenario suite's wall time. | Performance | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Canonical model | No reader of `in_tension_with`, no deprecation window; tests and examples use only `tension`, except error-handling fixtures. | Technical | High | Open |
| C-002 | Contract versioning | Changes land in `0.2.0-SNAPSHOT`; the rename and the stricter loads are recorded under `### Breaking` in the contract CHANGELOG (ADR-0022). | Technical | High | Open |
| C-003 | No enforcement | A finding never refuses or blocks a write or a read. | Business | High | Open |
| C-004 | Sibling repositories | No write to `spec-kitty-open-packs`; its migration goes through the orchestrator as a draft issue. | Process | High | Open |
| C-005 | Coordination with R14 | Keep both sides of textual neighbours with charter #108 (PR #600) on rebase; any new refusal code is appended after #108's. | Process | Medium | Open |

### Key Entities

- **Tension**: a relationship of relation `tension` between two guidance artifacts, canonical orientation, with an
  optional condition and reason.
- **Reconciler**: an artifact in force reaching both sides through `reconciles_tension`.
- **Tension in force**: a tension, resolved through overrides, whose two sides are in force; with its reconcilers.
- **Finding**: an unreconciled tension in force, its code, its pair, its resolutions and its links.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For each of the five example activations, the three reads (activation tensions, effective graph, one
  action context) answer the expected tensions and findings, 15 of 15 checks passing. — [build] · no-op passable: no
- **SC-002**: Every pack fixture that writes `in_tension_with`, a duplicate tension or a reversed tension is refused,
  3 of 3, while the canonical fixture loads. — [build] · no-op passable: no
- **SC-003**: A tension registered on a pair with `requires` survives two later registrations into the same pack, 2
  of 2. — [build] · no-op passable: no
- **SC-004**: The divergence register and the service-contract page name every intended difference from core for
  tensions, 4 of 4 (spelling, vocabulary, read shape, reconciler rule). — [build] · no-op passable: no

## Assumptions

- charter's example packs hold no tension today, so the rename breaks no existing example; the open-packs content
  migrates in its own repository before it bumps its pin (ADR-0070 decision 3).
- `GUIDANCE` (directive, tactic, styleguide, toolguide, paradigm, procedure) is the endpoint set of `tension`, as for
  `reconciles_tension`.

## Out of Scope

- Value impacts (`impacts` lists) and the creed and values work (#16).
- Any enforcement of a finding.
- The open-packs content migration itself (drafted here, filed by the orchestrator).
