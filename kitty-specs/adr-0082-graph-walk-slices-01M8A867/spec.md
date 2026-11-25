# Mission Specification: ADR-0082 slices 2 to 4: one graph walk, rules from the factory

**Mission Branch**: `kitty/mission-adr-0082-graph-walk-slices-01M8A867` (lands on `feat/adr-0082-graph-walk-slices`; one pull request per slice to `main`)
**Created**: 2026-11-25
**Status**: Draft
**Input**: Operator dispatch of #637, #638 and #639 (children of epic #636), with the operator rulings of 2026-11-25 on D1 to D4 and the pre-spec squad (`research/pre-spec-squad.md`).

## Intent Summary

- **Primary actors**: a pack author who registers, writes or removes a pack through the service, reads it back, or checks it with `pack-doctor`; and the contributor who adds the next pack check.
- **Trigger**: any pack registration, write, removal, read of a composed closure, or `pack-doctor validate` run.
- **Desired outcome**: every walk over the pack graph and every pack rule lives in the packs domain. One walker runs the rules that a factory hands out, so the API, the reads and `pack-doctor` cannot disagree. A new check becomes a rule plus a scope, not a new walk.
- **Rules that always hold**:
  - Refusal codes, details, origins and the order of findings stay as they are, except where the operator ruled a change (FR-011).
  - The first finding decides the refusal code, so findings stay rule-first.
- **Rulings that bound the scope** (operator, 2026-11-25):
  - the five walks of the composition resolver move too;
  - the full type design is built (graph nodes, a scope, a visitor driver);
  - read-time and registration codes for the edge-index refusals are unified;
  - `pack-doctor` picks up the specialization cycle and the relation rules in this mission.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One place walks the pack graph (Priority: P1)

A contributor adding a pack check finds every walk over `depends_on` and `parent_pack`, every cycle and group search, and the `enhances` grounding in one domain class. A guard fails the build when an application class writes its own walk.

**Why this priority**: every later slice builds on it, and #632's `specializes_from` cycle check needs the cycle search in the domain.

**Independent Test**: The guard fails on a class that walks by hand and passes on the production code. Every moved walk has a test that pins its order and its answer.

**Acceptance Scenarios**:

1. **Given** a class in the application layer that walks the pack graph with a work queue, or reads a descriptor's edges to walk them, **When** the build runs, **Then** the walk-containment guard fails and names the class.
2. **Given** any closure that registration, a write, a removal or a read judged before, **When** it is judged now, **Then** the answer is the same refusal code, detail, origin and order of findings.

---

### User Story 2 - One walker runs the own-closure rules from the factory (Priority: P1)

The fallback-profile, role-pin and profile-enhancement rules come from the rule factory. The one walker runs them over a closure, selected by a predicate on the closure's facts. The judging port that ran them before is gone.

**Why this priority**: it removes the second closure walk and makes the walker the mechanism the later slices use.

**Independent Test**: The factory hands out the own-closure phase, and the walker over a closure with mixed breaches returns them in today's order.

**Acceptance Scenarios**:

1. **Given** a declaring pack whose fallback profile is unknown and whose two pins name no profile, **When** the pack is registered, **Then** the refusal lists the fallback's breach first and then each pin's, with today's codes and origins.
2. **Given** a closure with relation faults of several rules, **When** the walker runs the rules, **Then** the findings are ordered by rule first, then by the order of the subjects, as they are today.

---

### User Story 3 - Relation and edge-index refusals are factory rules with one code per fault (Priority: P1)

The relation rules (self-override, conflicting relations, override not permitted, a locked step enhanced, override and enhances cycles, an ungrounded enhancer, the specialization cycle) and the edge-index refusals come from the factory's relations phase. A read and a registration of the same closure answer the same fault with the same code.

**Why this priority**: it completes moving judging code out of the application layer, and ends the case where a read answers `500 pack_unreadable` for a fault that registration refuses with its own code.

**Independent Test**: For each edge-index fault, a closure that holds it is registered and read. Both answers carry one code, and the relations gate still stops the edge-index rules from running.

**Acceptance Scenarios**:

1. **Given** a stored closure in which an `overrides` edge resolves to its own source, **When** the closure is read and when it is registered, **Then** both answer the same code, and the read no longer answers `500 pack_unreadable`.
2. **Given** a closure with both a relation fault and an edge-index fault, **When** it is registered, **Then** only the relation fault is reported, as today.

---

### User Story 4 - pack-doctor judges the specialization cycle and the relations (Priority: P2)

`pack-doctor validate --full` reports a `specializes_from` cycle and the relation faults of each root's composed closure with the same codes that registration answers, so open-packs can retire the matching Python rules (#632 item 3).

**Why this priority**: it closes a gap that open-packs' CI still covers in Python.

**Independent Test**: Parity rows: the same pack set refused by registration and by `pack-doctor` gives the same code and pointer.

**Acceptance Scenarios**:

1. **Given** two profiles of a set that specialize each other across packs, **When** `pack-doctor validate --full` runs over the set, **Then** it exits 1 with `specialization_cycle`, as registration refuses it.

### Edge Cases

- A self-override that appears only after the layer merge re-points an edge.
- An `enhances` chain that is both grounded and reaches a reported cycle: it is excused, as today.
- A successor that is no key of the graph is a dead end, never part of a cycle.
- A closure with no `enhances` or `overrides` edges skips the relation rules but still runs the specialization cycle.
- A preset-item fault beside an own-closure breach is reported with it, not gated away.
- The census decorates details differently from the write path (" (packs ...)" against "In the closure rooted at ..."); both stay until slice 6.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Application walks move to the domain | As a contributor, I want every walk over the pack graph that the application layer wrote by hand to live in `PackGraph`, so that walks cannot disagree. | High | In progress (PR #665) | [build] | no --- the guard's bad fixture fails |
| FR-002 | Resolver walks move to the domain | As a contributor, I want the composition resolver's dependency order, dependency-cycle search, shortest-cycle search, `depends_on`-only closure and `parent_pack` line walk in `PackGraph`, each proved by a test, so that one class walks the graph. | High | Open | [build] | no --- each moved walk has a test that pins its order |
| FR-003 | Walk-containment guard | As a contributor, I want the build to fail when an application class walks the pack graph or reads a descriptor's edges to walk them, so that the containment holds. | High | In progress (PR #665) | [build] | no --- a known-bad fixture must fail it |
| FR-004 | Graph nodes, scope and driver | As a contributor, I want `PackGraph` to drive one visitor over typed graph nodes (pack, artifact, edge), constrained by a scope that only selects, so that a check is a rule plus a scope. | High | Open | [build] | no --- a walk over a scope visits only selected nodes |
| FR-005 | One walker with rule-first order | As a pack author, I want the walker to report findings rule first, then subject in input order, and stop later phases after a phase records a violation, so that the refusal code stays the same. | High | Open | [build] | no --- a multi-rule fixture pins the order |
| FR-006 | Own-closure rules from the factory | As a contributor, I want the fallback-profile, role-pin and profile-enhancement rules in `packs.domain.rules`, handed out by `PackRulesets.ownClosure()` and selected by predicates on the closure's facts, so that registration, every write and the census get them from one place. | High | Open | [build] | no --- the deleted port no longer compiles; order fixture |
| FR-007 | References on the walker | As a contributor, I want the `REFERENCES` checks to run on the same walker with no hand-written phase loop beside it, so that there is one mechanism. | Medium | Open | [build] | no --- `FullPassParityTest` rows stay green |
| FR-008 | Relations phase from the factory | As a contributor, I want the relation rules and the specialization cycle handed out by `PackRulesets.relations()`, judging a domain subject of the composed closure, so that no application class builds a relation refusal. | High | Open | [build] | no --- `ImpactRelationsTest` cases rerun on the rules |
| FR-009 | Edge-index refusals as rules | As a contributor, I want the edge-index refusals to be relations-phase rules, with the relations gate kept ahead of them, so that no application class builds one. | High | Open | [build] | no --- a mixed-fault fixture pins the gate |
| FR-010 | Hand-written gates removed | As a contributor, I want the gates between the relations, edge-index and own-closure phases to be the walker's, with `ClosureImpactCheck#relationFaults` deleted, so that the application layer only orchestrates. | Medium | Open | [build] | no --- the census and write-path fixtures stay green |
| FR-011 | One code per edge-index fault | As a pack author, I want a read and a registration of the same closure to answer an edge-index fault with the same code, so that the surfaces agree (operator ruling D2). | High | Open | [build] | no --- the read used to answer `500 pack_unreadable` |
| FR-012 | pack-doctor picks up cycle and relations | As a pack author, I want `pack-doctor validate --full` to report the specialization cycle and the relation faults over each root's composed closure, with registration's codes, so that open-packs can retire its Python rules. | Medium | Open | [build] | no --- a parity row refuses on both surfaces |
| FR-013 | Ordered collections | As a pack author, I want no walk or rule to order work by hash order, so that answers are deterministic. | Medium | In progress (PR #665) | [ratchet] | yes --- paired with order-pinning tests on the same fixtures |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Unchanged answers | 0 changed refusal codes, details, origins or finding orders in the existing service, contract-scenario and conformance tests, except the FR-011 wire change, which the changelog and contract docs name. | Reliability | High | Open |
| NFR-002 | No stack exhaustion | Every walk handles a chain of 200,000 packs or nodes without a stack overflow. | Reliability | High | Open |
| NFR-003 | No added cost | `PackRegistrationScaleTest` reports no more than 10% more time per registration than `main` before the mission (report-only, ADR-0057). | Performance | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Replace outright | Each slice deletes what it replaces; no legacy walk, port or reader stays beside the new shape (charter). | Technical | High | Open |
| C-002 | One PR per slice | Slice 2 (#637), slice 3 (#638), slice 4 (#639) and the pack-doctor pickup land as separate pull requests, each with its own adversarial review (ADR-0082 amendment of 2026-11-21). | Business | High | Open |
| C-003 | Leave slice 5's constructors | The test-only `PackMutabilityPolicy` constructors are not touched; slice 5 owns them. | Business | High | Open |
| C-004 | No PIT | No mutation testing runs in this mission (charter, ADR-0062). | Business | High | Open |
| C-005 | Other runners | No edits to the files of #659 (the InForceResolution counters), #658/#662 (containment guards) or #648 (the schema-violation collector); overlaps are raised with the dispatcher. | Business | High | Open |
| C-006 | Proposed records | Every ADR or architecture-page amendment is written as Proposed; only the operator rules. | Business | High | Open |

### Key Entities

- **PackGraph**: the domain's one walker of the pack graph: its edges, descents, cycles and groups, and the visitor driver.
- **Graph node**: a pack, an artifact or an edge between them, each a kind the walk can select.
- **Scope**: the roots of a walk and the predicate that selects nodes; it never names edges.
- **Rule factory** (`PackRulesets`): hands out the phases of rules each surface runs.
- **ValidationWalker**: runs a phase's rules on the nodes their predicates select, into one ordered ledger, behind a phase gate.
- **Closure facts**: the subject the own-closure rules judge, including the declaring pack and its fallback profile.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: No class outside the packs domain walks the pack graph, as the build's guard proves. --- [build] · no-op passable: no
- **SC-002**: Every existing registration, write, removal, read and pack-doctor test passes with 0 changed expected codes or details, except the edge-index rows that FR-011 changes. --- [ratchet] · no-op passable: yes --- paired with the guard and the new rule tests
- **SC-003**: A read and a registration of the same faulty closure answer with 1 code, for every edge-index fault. --- [build] · no-op passable: no
- **SC-004**: pack-doctor and registration agree on 100% of the specialization-cycle and relation parity rows. --- [build] · no-op passable: no

## Assumptions

- The reads, the census and registration keep their own detail wording until slice 6 moves the closure prefix.
- Of the four artifact walks (`GraphWalk`, `Reachability`, `GovernanceWalk`, `ActionContextWalk`), only `GraphWalk` folds into the visitor driver built here, and only if FR-004 needs it. The rest are on epic #636's checklist.
- The contract or conformance version moves only if FR-011 changes a documented response; the plan checks this.
