# Mission Specification: Graph documents are the sole source of edges

**Mission Branch**: `claude/240-drop-edge-fields`
**Created**: 2026-11-05
**Status**: Draft
**Input**: Issue #240 and the operator ruling of 2026-11-05 on it: drop the artifact fields that mint
relationship edges. Edges are authored only in a pack's graph documents (`*.graph.yaml` root shards,
`drg/fragment.yaml`) and written through one relationship API (`putPackRelationship`).

## Intent Summary

- **Primary actor**: a pack author (a person or an agent) who publishes doctrine to the charter
  service, and the pack curator who maintains `spec-kitty-open-packs`.
- **Trigger**: an author registers or edits an artifact, or adds a relationship between two
  artifacts.
- **Desired outcome**: an edge has exactly one home. The author writes it in the pack's graph
  documents, or registers it through the relationship write. No artifact field creates an edge, and
  no artifact registration carries one.
- **Rule that always holds**: the edges the service reads, writes and round-trips are the edges in
  the graph documents. Nothing else is consulted, so the two copies cannot disagree.
- **Boundary**: designed against this repository's own model, contract and packs, as if core did not
  exist (charter, "Designing during the rewrite"). Fields that look like references but are runtime
  data or selection stay unchanged (FR-013).
- **Confirmation**: the operator ruling on #240 is the confirmed intent. The per-field questions it
  left open were asked on #240 (comment 6663603068) and of the parent session; the three that change
  scope are deferred Decision Moments below, and the mission proceeds on the recorded
  recommendation for each, kept reversible.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One write path for an edge (Priority: P1)

A pack author registers a directive. The registration body carries the directive's own content
only. To relate the directive to a tactic, the author registers the relationship through the
relationship write. Reading the directive back shows its content; reading the pack's relationships
shows the edge.

**Why this priority**: it is the ruling itself. Two write paths for one edge is the defect.

**Independent Test**: register a directive, then a relationship from it; a body that still carries
a relationships array or an edge-minting field is refused.

**Acceptance Scenarios**:

1. **Given** a pack, **When** the author registers a directive with no edge fields, **Then** it is
   created and read back value-equal, with no relationships on the artifact.
2. **Given** a directive registration body carrying `relationships` or `references`, **When** it is
   sent, **Then** it is refused as an unknown member, and nothing is written.
3. **Given** a registered directive, **When** the author registers a relationship from it through the
   relationship write, **Then** the directive's references read lists the edge with its relation.

---

### User Story 2 - Pack files hold edges only in graph documents (Priority: P1)

A pack curator authors a directive, paradigm, tactic, procedure, styleguide or toolguide file. The
pack schemas accept no edge-minting field. Every edge the curator wants is in a graph document.

**Why this priority**: the pack files are the other half of the two-copies problem.

**Independent Test**: validate a pack file that carries a dropped field against the pack schema; it
is refused. A file without it is accepted.

**Acceptance Scenarios**:

1. **Given** a tactic file with top-level `references`, **When** it is loaded, **Then** it is refused
   as carrying an unknown field.
2. **Given** a directive file whose `procedures` holds prose steps, **When** it is loaded, **Then** it
   is accepted and no edge is minted from it (see D1).

---

### User Story 3 - The round trip compares graph-document edges only (Priority: P1)

The whole-pack round-trip gate loads a pack, writes it back, and compares meaning. Edges are
compared from the graph documents alone. A pinned open-packs file that still carries a dropped field
is migrated in open-packs first; this change moves the pin to the migrated commit (plan P5).

**Why this priority**: acceptance criterion 3 of #240; the gate is what keeps the rule true.

**Independent Test**: run the round-trip gates against the migrated open-packs pin; every file of a
modelled kind round-trips, and no file carries a removed field.

**Acceptance Scenarios**:

1. **Given** the migrated open-packs pin, **When** the gate runs, **Then** every edge compared comes
   from a `*.graph.yaml` or `drg/fragment.yaml`.
2. **Given** a pack file that still carries a removed field, **When** it is loaded, **Then** it is
   refused as an unknown field.

---

### User Story 4 - The decision is recorded and the record set agrees (Priority: P2)

An architect reads the ADRs. One new ADR records the per-field decision; the ADRs it changes carry a
dated amendment pointing at it; every "until #189 rules" or #167 citation points at the decision.

**Independent Test**: the docs checks pass, and no ADR still defers edge authority to #189 or #167.

### Edge Cases

- A field value that is prose rather than a reference (directive `procedures`) mints nothing and is
  not migrated into the graph (D1).
- A field reference with no matching graph edge (17 in open-packs) is added to the graph by the
  open-packs migration; 6 of those point opposite to an existing edge and are left to the curator.
- A field reference whose graph edge has a different relation (38): the graph wins, nothing moves.
- A field `when` text the graph edge lacks (19): the migration merges it into the graph edge.
- A cross-pack reference: the graph form `<pack>::<kind>:<id>` carries it; no field form remains.
- A guard's `implemented_by` stays: it is runtime data and its `IMPLEMENTED_BY` relation is derived,
  never authored (FR-013).

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status | Delivery | No-op passable? |
|----|-------------|--------|----------|-----------------|
| FR-001 | One new ADR records, per field, that it is dropped or kept as data, settles `putPackRelationship` as the single write path for an edge, and amends ADR-0024, ADR-0025, ADR-0026, ADR-0027 and ADR-0036 decision 4 with dated amendments. | Draft | [build] | no |
| FR-002 | The ADR citations that defer edge authority to #189 or link #167 (ADR-0024, 0025, 0027, 0036, 0037, 0043, 0044, 0050, 0052 decision 9) point at #240 and the new ADR. | Draft | [build] | no |
| FR-003 | A directive's typed `references`, a paradigm's `references` and `directive_refs`, and a tactic's top-level `references` are removed from the domain, the pack schemas, and the contract's read and registration shapes. | Draft | [build] | no |
| FR-004 | The typed references that mint nothing today (a procedure's, a mission type's and its steps', a tactic step's) are removed the same way (D2, operator ruling). | Draft | [build] | no |
| FR-005 | A styleguide's and a toolguide's citation `references` are removed the same way (D2, operator ruling). | Draft | [build] | no |
| FR-006 | A directive's `procedures` is removed; its prose steps become procedure artifacts in open-packs that the directive requires through graph edges (D1, operator ruling). | Draft | [build] | no |
| FR-007 | The directive and paradigm registration bodies carry no `relationships` array; `putPackRelationship` is the only operation that writes an edge. | Draft | [build] | no |
| FR-008 | The per-kind read shapes carry no `relationships` array, and the `relation` reported by the artifact-references read comes from the graph documents (D3). | Draft | [build] | no |
| FR-009 | No artifact mints an edge from its fields: the interim mapping folded in from #167 is retired, and the tests that pinned its relation values are retired or rewritten to the new rule. | Draft | [build] | no |
| FR-010 | A pack file or registration body that still carries a removed field is refused as an unknown field, with the existing refusal for an unknown member. | Draft | [build] | no |
| FR-011 | The whole-pack round-trip gates compare edges from the graph documents only, against an open-packs pin whose files carry no removed field (the migration lands first; plan P5); an org fragment's edge accepts `when`, as a root shard's does, so no field `when` text is lost. | Draft | [build] | no |
| FR-012 | The open-packs migration is drafted in the mission research directory: the files and exact edits that remove the fields, the graph edges to add, and the `when` texts to merge. | Draft | [build] | no |
| FR-013 | Fields that look like references but are runtime data or selection keep their shape and are listed in the ADR as not relationship authoring (guard `implemented_by`, agent-profile references, skill `enhances`/`overrides`, pack `depends_on`, `activated_*`/`selected_*`, glossary `see_also`, step-contract delegations, lifecycle guards). | Draft | [ratchet] | yes — positive control: their existing tests stay green unchanged |
| FR-014 | The contract records the change as a `### Breaking` changelog fragment on the unreleased line, and its scenarios cover FR-007, FR-008 and FR-010. | Draft | [build] | no |

Resolved (D1, 2026-11-05): a directive's `procedures` is dropped; its steps become procedure artifacts reached through graph edges.

Resolved (D2, 2026-11-05): every reference field goes; each dropped reference keeps a live edge, and a citation moves into the artifact's text.

Resolved (D3, 2026-11-05): the read shapes lose their `relationships` arrays; the references read takes its relation from the graph documents.

### Non-Functional Requirements

| ID | Requirement | Status |
|----|-------------|--------|
| NFR-001 | `./gradlew verifyAll` passes on Java 25 with strict dependency verification; no new dependency enters the build (0 added entries in the verification metadata). | Draft |
| NFR-002 | The docs checks pass: `check_docs.py` (all eight), markdownlint-cli2, cspell, and the CI frontmatter check (every description 50 to 180 characters). | Draft |
| NFR-003 | The contract change touches only the shapes, paths, examples and scenarios that carry the removed fields: 0 unrelated schema files changed. | Draft |

### Constraints

| ID | Constraint | Status |
|----|------------|--------|
| C-001 | Designed as if core did not exist: no compatibility shim for core's formats. | Active |
| C-002 | No contract version bump, no release, no contract tag: the change is a 0.x break on the unreleased 0.2.0-SNAPSHOT line (ADR-0022). | Active |
| C-003 | This session does not write to `spec-kitty-open-packs`; the migration is a draft handed to the parent session. | Active |
| C-004 | The contract change is minimal and self-contained, so contract minors #125 and #126 can follow it. | Active |
| C-005 | The ADR number is chosen when the ADR is written, against `main` and the open PRs. | Active |

## Success Criteria *(mandatory)*

- SC-001: An edge can be authored in exactly 1 place in a pack file (a graph document) and written by exactly 1 operation — [build] · no-op passable: no
- SC-002: 0 artifact kinds mint an edge from a field — [build] · no-op passable: no
- SC-003: The round-trip gates report 0 failures against the migrated open-packs pin — [build] · no-op passable: no
- SC-004: 0 ADRs still defer edge authority to #189 or #167 — [build] · no-op passable: no

## Key Entities

- **Graph document**: a pack's `*.graph.yaml` root shard or `drg/fragment.yaml`; the only authored
  home of an edge.
- **Relationship (edge)**: a directed `(source, target, relation)` with optional `when` and reason;
  identity as in ADR-0027.
- **Edge-minting field**: an artifact field from which the service derives an edge today, or which
  carries an artifact-to-artifact reference.

## Assumptions

- A-1 (D1, recommended): a directive's `procedures` is prose content, so it stays and stops minting.
- A-2 (D2, recommended): every typed artifact-to-artifact reference field goes, minting or not, and
  so do styleguide and toolguide citation `references`, as the ruling names styleguide `references`.
- A-3 (D3, recommended): the read shapes lose their `relationships` arrays too, so edges are never
  kept as a derived copy on an artifact.
- A-4: the runtime and selection fields of FR-013 are not "the like" and stay; the operator is asked
  to confirm on #240 (Q4).
- A-5: the unknown-member refusal already used for request bodies applies; if none exists for a
  body, the plan adds the existing closed-shape refusal, not a new code.

## Dependencies

- The open-packs migration (FR-012) part A is filed and merged by the parent session first; this
  change then moves the pin to it (plan P5). Part B (fragment `when` texts) follows this change.
- PR #376 (ADR-0059, cascade edges) is read alongside the new ADR so the two agree.
