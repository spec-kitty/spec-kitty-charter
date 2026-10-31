# Mission Specification: DRG tension edges and node fields round-trip

**Mission Branch**: `issue-235-236-drg-round-trip`
**Created**: 2026-10-31
**Status**: Draft
**Input**: Orchestrator brief for spec-kitty-charter #235 and #236 (epic #186): two Doctrine Reference
Graph (DRG) round-trip losses found by the whole-pack gate (#187). Kind: bugfix, red-first.

## Intent Summary

- **Primary actor:** a pack author whose graph documents the charter service reads into its domain
  model and writes back. The whole-pack round-trip gate (#187) checks this on the pinned built-in
  pack.
- **Trigger:** the service reads a graph document, then writes it again (the gate, or any later
  whole-pack write).
- **Desired outcome:** every edge and every declared node field comes back with the same meaning
  (ADR-0036):
  - a tension edge stays a tension edge, spelled as the author wrote it;
  - node tags (in their order), node provenance and fragment-node body paths survive.
- **Invariants:**
  - Tension is the ADR-0028 relationship between two rules. It is never the retired signed `impacts`
    edge, and it is counted apart from the one structural relationship a pair may hold (ADR-0028 D3).
  - Nothing changes on the HTTP contract (decision DM-01M68A2TYC1VMDPCJ8B76602YH).
- **Source:** the brief was comprehensive (objective, constraints, done-when). It was taken through
  brief intake, and the orchestrator's instruction to stop only at genuine decisions stands in for
  interactive confirmation.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A tension edge round-trips as a tension (Priority: P1)

A pack author marks two rules as in tension with an `in_tension_with` edge and a reason. They may also
state a structural relationship on the same pair, as the built-in pack does for `DIRECTIVE_024` and
`DIRECTIVE_025`. After the service reads the document and writes it back, the edge is still
`in_tension_with`, between the same two endpoints in the same orientation, with the same reason and
condition. It is not turned into an `impacts` edge, it carries no strength, and the structural
relationship beside it survives too.

**Why this priority**: today the tension is lost outright (#235). A written shard states a plain
`impacts` edge, so a real conflict between two rules disappears from the pack.

**Independent Test**: round-trip a shard that holds a tension edge beside a structural edge on the same
pair, and compare source and written meaning with the gate's comparator.

**Acceptance Scenarios**:

1. **Given** a shard with `A in_tension_with B` (reason R) and `B requires A`, **When** it is read
   and written back, **Then** the written shard holds both edges, unchanged, and no `impacts` edge.
2. **Given** the pinned built-in `directive.graph.yaml`, **When** the whole-pack gate runs, **Then**
   the file round-trips and its expected-red entry is gone.
3. **Given** an `in_tension_with` edge that also writes an `impacts` strength, **When** it is read,
   **Then** it is refused with a finding, as today, and is not bound.

### User Story 2 - Node tags, provenance and fragment body paths survive (Priority: P1)

A pack author tags graph-shard nodes, records a node's provenance, or points an org-fragment node at
its body file. After the service reads the document and writes it back, every one of these fields is
still there, and the tags are in the order they were written.

**Why this priority**: today the domain node model has none of these fields, so a written shard loses
them all (#236). The built-in `anti_pattern.graph.yaml` has 13 tagged nodes.

**Independent Test**: round-trip a shard whose nodes carry tags in a non-sorted order plus a
provenance, and a fragment whose node carries a `body_path`. Compare the written meaning.

**Acceptance Scenarios**:

1. **Given** a shard node with `tags: [b, a]` and a `provenance`, **When** it is read and written
   back, **Then** the written node has `tags: [b, a]`, in that order, and the same provenance.
2. **Given** a fragment node with a `body_path`, **When** it is read and written back as a fragment,
   **Then** the written node has the same `body_path`.
3. **Given** the pinned built-in `anti_pattern.graph.yaml`, **When** the whole-pack gate runs,
   **Then** the file round-trips and its expected-red entry is gone.
4. **Given** a node with no tags, provenance or body path, **When** it is read and written back,
   **Then** the written node holds none of those keys (absent stays absent).

### Edge Cases

- **A tension pair that also holds a structural relationship** (ADR-0028 D3): both survive, neither
  replaces the other.
- **A tension edge written in the non-canonical orientation**: the triple is kept as written; the reader
  never turns it around (ADR-0036 D4).
- **An `in_tension_with` edge that also writes `impacts`**: refused, with today's finding text.
- **An `impacts` edge**: the relation ADR-0028 retires keeps today's reading and rules, so this mission
  changes nothing about it.
- **Duplicate tags or an empty tag list**: kept as written, in order. An absent list and an empty list
  mean the same (`GraphDocument` rule).
- **A registered-relationships shard**:
  - It is service-written and never holds a tension.
  - A hand-edited one that holds a tension is reported (tension is authored, never registered), so the
    store refuses to rewrite it instead of dropping the tension silently.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Tension reads as tension | As a pack author, I want an `in_tension_with` edge read as the ADR-0028 tension relationship between its two endpoints, keeping its orientation, reason and condition, and never as the `impacts` relation with a strength, so that the conflict it states is kept. | High | Open | [build] | no — a no-op keeps today's impacts −1 reading, which the test refutes |
| FR-002 | Tension writes back as written | As a pack author, I want a tension relationship written back as an `in_tension_with` edge with its reason and condition and no strength, so that the written shard means what the source meant (ADR-0036 D4). | High | Open | [build] | no — a no-op writes `impacts` |
| FR-003 | Tension beside a structural relationship | As a pack author, I want a tension and a structural relationship on the same pair both kept, so that two rules can require each other and still conflict (ADR-0028 D3). | High | Open | [build] | no — positive control: the `requires` edge on the same fixture is asserted present too |
| FR-004 | Invalid tension still refused | As a pack author, I want an `in_tension_with` edge that also writes `impacts` refused with a finding, so that the strength is never silently dropped. | Medium | Open | [ratchet] | yes — paired with FR-001's valid tension on the same fixture, which binds |
| FR-005 | Shard node tags kept in order | As a pack author, I want a graph-shard node's `tags` kept through read and write, in written order (ADR-0036 E10, E15). | High | Open | [build] | no — fixture tags are deliberately unsorted |
| FR-006 | Shard node provenance kept | As a pack author, I want a graph-shard node's `provenance` kept through read and write. | High | Open | [build] | no |
| FR-007 | Fragment node body path kept | As a pack author, I want an org-fragment node's `body_path` kept through read and a fragment write. | High | Open | [build] | no |
| FR-008 | Absent stays absent | As a pack author, I want a node without tags, provenance or body path written without those keys. | Medium | Open | [ratchet] | yes — paired with FR-005/FR-006 on the same fixture, where the fields are present |
| FR-009 | Expected-red entries retired by the ratchet | As a maintainer, I want the `directive.graph.yaml` and `anti_pattern.graph.yaml` expected-red entries removed because the gate reports them green, so that the baseline stays honest. | High | Open | [build] | no — the ratchet fails a listed file that turns green and an unlisted red |
| FR-011 | No silent tension drop in the registered shard | As a maintainer, I want a tension found in the registered-relationships shard reported, so that a registration rewrite never drops it silently. | Medium | Open | [build] | no — positive control: the same shard without the tension binds |
| FR-010 | Records stay true | As a reader of the docs, contract text and divergence register, I want no record to still say the service reads `in_tension_with` as `impacts` −1, so that the documentation matches the behaviour. | Medium | Open | [build] | no — grep for the old claim |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Full build green | `./gradlew verifyAll` and `./gradlew :packs:wholePackRoundTrip` pass on Java 25: 0 failing tests, 0 Checkstyle warnings, 0 javadoc or NullAway errors. | Reliability | High | Open |
| NFR-002 | No new suppressions | 0 new entries in `config/checkstyle/suppressions.xml`, 0 new `@SuppressWarnings`, 0 new exclusions or known-failure entries. | Maintainability | High | Open |
| NFR-003 | Bounded complexity | Every method added or changed has cyclomatic complexity ≤ 15. | Maintainability | High | Open |
| NFR-004 | Gate counts do not regress | The whole-pack gate's modelled files that round-trip go up by exactly 2 against the baseline (`directive.graph.yaml`, `anti_pattern.graph.yaml`); no file that round-trips today goes red. | Reliability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | No HTTP contract change | No path, schema, enum value or wire shape in `contracts/charter/` changes. The relation catalogue answered and accepted on `/relationships` is unchanged. Description text that states the retired reading may be corrected. | Technical | High | Open |
| C-002 | Pack schemas unchanged | `contracts/packs/schemas/*` are not changed; they already allow every field involved. | Technical | High | Open |
| C-003 | Kernel stays pure | New kernel types use JDK types only (ArchUnit `KERNEL_IS_PURE`). | Technical | High | Open |
| C-004 | No overlap with #194 PR-B | The `kinds: other:missions` expected-red entry and the pinned qa and cave-kitty fragments are not touched; whoever lands second rebases. | Process | Medium | Open |
| C-005 | Red first | Each behaviour change is preceded by a failing test on the production path (reader → policy → writer) that the change turns green. | Process | High | Open |

### Key Entities

- **Tension**:
  - The ADR-0028 relationship between two rules: two endpoints in the order written, an optional reason
    and an optional condition, and no strength.
  - It is held beside the structural relationships and is not counted in their one-per-pair identity.
- **DRG node**: a node of the graph, with identity (URN), optional label, and now its optional
  provenance, ordered tags and optional body path.
- **Graph shard / org fragment**: the two graph document shapes. A shard names nodes by URN; a fragment
  names them by id and plural kind and may give a `body_path`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The whole-pack gate passes with 0 expected-red file entries for `directive.graph.yaml` and `anti_pattern.graph.yaml` (2 entries removed, 0 added) — [build] · no-op passable: no
- **SC-002**: Both tension edges and all 13 tagged anti-pattern nodes of the pinned built-in pack come back identical in meaning (0 differences reported for those two files) — [build] · no-op passable: no
- **SC-003**: The full build passes with 0 new suppressions and 0 new known-failure entries — [ratchet] · no-op passable: yes — paired with SC-001, which a no-op fails
- **SC-004**: 0 records in the docs, contract text or divergence register still say a tension edge is read as `impacts` with strength −1 — [build] · no-op passable: no

## Assumptions

- The `impacts` relation keeps today's reading and rules: ADR-0028 retires it, but removing it is not
  part of this bugfix.
- Writing a fragment from the domain is needed only to prove FR-007. The whole-pack gate round-trips no
  org fragment.
- Tension identity uses ADR-0036's directed triple `(source, target, relation)`, compared as written.
  Canonical orientation is a pack-consistency concern, not something the reader enforces.

## Out of Scope

- Retiring the `impacts` relation or adding ADR-0028's per-value `impacts` list.
- Exposing tension on the HTTP API (that is a contract decision for the operator).
- The pinned pack's fragments and the mission kinds (#194).
