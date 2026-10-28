# Mission Specification: Procedure, anti-pattern and toolguide guide round trip

**Mission Branch**: `feat/194a-procedure-antipattern-toolguide`
**Created**: 2026-10-28
**Status**: Draft
**Input**: Charter #194 PR-A (part of epic #186; absorbs #168): round-trip the procedure kind with a sanitized storage
shape, prove inline anti-pattern entries round-trip with their owners (graph-node tags stay under #236), and
round-trip the toolguide Markdown guides. Grounding: the pre-spec squad (`research.md`).

## Intent Summary (confirmed 2026-10-28)

A charter maintainer runs the whole-pack round trip against the pinned Charter Pack content. Procedures are read
into the charter domain and written back with equal meaning (ADR-0036), in a deliberately designed storage shape;
the anti-patterns that styleguides and procedures carry inline ride inside their owner; and the Markdown guide each
toolguide points to is read and written together with its toolguide. The rule that must always hold: storage shapes
are designed, not mirrored from the pack. Pack content that diverges from a designed shape is reported to
spec-kitty-open-packs and recorded on the gate's known-failure list; the service never adds logic to tolerate it.

Operator decisions that shape this mission (2026-10-28, recorded on #194):

- The sanitized-shape requirement wins over #194's "round-trip as-is" wording.
- `procedure.schema.json` is sanitized in place (it is canonical in this repository). Superseded in part by the
  operator ruling on epic #186 (2026-10-28): missions operate as if core does not exist, so no core issue is drafted;
  pack divergences go to spec-kitty-open-packs only.
- Anti-pattern graph-node tags stay under #236.
- #194 may remove its own exclusion entries from `contracts/packs/file-kinds.yaml`; it absorbs procedure and the
  toolguide Markdown guides from #168.
- This mission builds ADR-0040's `markdown` content form; sibling #195 consumes it (coordinated with the parent
  session), so this PR lands early.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Toolguide guides travel with their toolguide (Priority: P1)

A maintainer loads a pack whose toolguides name a Markdown guide. The guide is read as Markdown content belonging
to its toolguide and is written back with the same meaning, so a pack round trip no longer leaves the guides behind.

**Why this priority**: It builds the Markdown content form that sibling mission #195 needs, so it lands first.

**Independent Test**: Round-trip every toolguide in the built-in pack and compare each guide's Markdown before and
after; the gate no longer reports `other:toolguides`.

**Acceptance Scenarios**:

1. **Given** a toolguide that names a guide, **When** the pack is read and written back, **Then** the guide is present
   at its path with equal content and the toolguide still names it.
2. **Given** a toolguide that names a guide which does not exist, **When** the pack is read, **Then** the read is
   refused with a refusal that names the toolguide and the missing guide.
3. **Given** a Markdown file under `toolguides/` that no toolguide names, **When** the pack is read, **Then** it is
   classified and reported, not silently dropped.

### User Story 2 - Procedures round-trip in a sanitized shape (Priority: P1)

A maintainer loads a pack with procedures. Each procedure is read into a domain procedure and written back in the
designed storage shape: a stable identity, a title, a description, ordered steps that name the role performing
them, inline anti-patterns in the shared entry shape, and references in the shared reference shape.

**Why this priority**: Procedures are the kind #194 and #168 exist for; the gate lists them as wholly red.

**Independent Test**: Round-trip a sanitized procedure fixture and every procedure in the built-in, internal and qa
packs; sanitized files compare equal, diverging files appear exactly as their known-failure entries say.

**Acceptance Scenarios**:

1. **Given** a procedure in the sanitized shape, **When** it is read and written back, **Then** the written file is
   equal in meaning to the original.
2. **Given** a procedure using a retired or unknown field (for example `actor`, or `name` in place of `title`),
   **When** it is read, **Then** it is refused (ADR-0036: unknown fields are refused), and the pinned pack file that
   does so is listed on the known-failure list with its open-packs issue.
3. **Given** a procedure step naming a role, **When** it round-trips, **Then** the role is kept; no step names an
   agent profile (ADR-0032).
4. **Given** a procedure with references, **When** it is read, **Then** the references are kept on the procedure but
   no relationship is created from them in the charter's relationship graph (pending #189).

### User Story 3 - Inline anti-patterns ride with their owner (Priority: P2)

A maintainer's styleguides and procedures list anti-patterns inline. Those entries share one shape and travel with
their owner; nothing is lost and nothing becomes a standalone file.

**Why this priority**: Anti-patterns have no file of their own (ADR-0037 decision 4); proving they survive in both
owners closes #194's anti-pattern item apart from the graph-node tags.

**Independent Test**: Round-trip styleguides and procedures carrying inline anti-patterns, with and without examples.

**Acceptance Scenarios**:

1. **Given** an inline anti-pattern with a name and description only, **When** its owner round-trips, **Then** the
   entry is equal.
2. **Given** an inline anti-pattern that also has bad and good examples, **When** its owner round-trips, **Then**
   both examples are kept.
3. **Given** the pinned `anti_pattern.graph.yaml`, **When** the gate runs, **Then** it is still reported red for the
   node tags only, exactly as its expected-red entry under #236 says.

### Edge Cases

- A pinned file diverges from the designed shape: it is refused on read and must match a known-failure entry keyed to
  an open-packs issue; an unlisted divergence fails the gate, and a listed file that starts round-tripping fails the
  gate with "remove it" (#247).
- A guide path that leaves the pack root, or that differs from an existing path only by letter case, is refused
  (ADR-0039, ADR-0040).
- Two toolguides naming the same guide: refused with a refusal naming both.
- Packs other than built-in (internal, qa) use the same shapes; they are proven by corpus tests, since the gate covers
  the built-in pack only.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Sanitized procedure shape | As a maintainer, I want the procedure storage shape and its schema to follow the cross-kind sanitized-shape rules (required string `schema_version` "1.0", `id` equal to the file stem, `title`, `description`, steps naming a `role`, one shared anti-pattern entry shape, one shared reference shape, closed objects) so that procedures are stored consistently with the other kinds. | High | Open | [build] | no |
| FR-002 | Procedure round trip | As a maintainer, I want procedures read into a domain procedure and written back with equal meaning so that the procedure kind leaves the gate's red baseline. | High | Open | [build] | no |
| FR-003 | Inline anti-patterns in both owners | As a maintainer, I want inline anti-pattern entries in styleguides and procedures to share one entry shape (name and description required, bad and good examples optional) and round-trip with their owner so that no anti-pattern is lost. | High | Open | [build] | no |
| FR-004 | Toolguide guides as Markdown companions | As a maintainer, I want the Markdown guide a toolguide names read and written as Markdown content of that toolguide so that `other:toolguides` leaves the gate's red baseline. | High | Open | [build] | no |
| FR-005 | Markdown content form | As a maintainer, I want ADR-0040's declared `markdown` content form built so that Markdown files are read and written as Markdown content, for this mission's guides and for sibling #195. | High | Open | [build] | no |
| FR-006 | Divergence reported, not tolerated | As a maintainer, I want every pinned file that diverges from a designed shape refused on read and listed on the known-failure list, each entry keyed to a filed spec-kitty-open-packs issue naming the file and the required change, so that the gate stays exact and the pack is fixed upstream. | High | Open | [build] | no |
| FR-007 | Anti-pattern graph unchanged | As a maintainer, I want `anti_pattern.graph.yaml` to stay expected-red for its node tags only, under #236, so that this mission neither hides nor claims that defect. | Medium | Open | [ratchet] | yes — paired with FR-003's positive round trip on the same pinned pack |
| FR-008 | Procedure references kept, not minted | As a maintainer, I want procedure references stored in the shared reference shape but not turned into relationships until #189 rules on edge authority, so that the relationship graph does not gain conflicting duplicate edges. | Medium | Open | [build] | no |
| FR-009 | Own exclusions removed | As a maintainer, I want the file-kind exclusions this mission makes obsolete removed from `contracts/packs/file-kinds.yaml` so that the files are classified by their new rules. | Medium | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Corpus coverage | 100% of `*.procedure.yaml` files and 100% of toolguide guides in the built-in, internal and qa packs at the pinned commit are exercised by a corpus round-trip test; each either round-trips or matches a known-failure entry. | Reliability | High | Open |
| NFR-002 | Gate exactness | The whole-pack gate passes with zero unlisted reds and zero listed items that round-trip, at the pinned commit. | Reliability | High | Open |
| NFR-003 | Build | `./gradlew verifyAll` passes on Java 25 in CI, including the Java style check, and the docs checks pass. | Maintainability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | No HTTP contract change | The charter HTTP contract does not change. | Technical | High | Open |
| C-002 | Shared files append-only | Edits to shared files (registry wiring, file kinds, gate baseline, known-failure list, CHANGELOG, glossary definitions, ADR index) are append-only, except removing this mission's own exclusions and its own baseline entries. | Process | High | Open |
| C-003 | Decision recorded | The sanitized-shape rules and the shapes this mission designs are recorded in ADR-0042 (reserved for #194). | Process | High | Open |
| C-004 | Design against the charter, not core | Shapes follow the charter's own model, contract and packs; no compatibility with core's current formats and no core issue (operator ruling of 2026-10-28 on epic #186: core changes when the service is incorporated into core). | Process | High | Open |
| C-005 | No tolerance logic | No reader, writer or binding accepts a retired or variant field to make pinned content pass (ADR-0036 decision 5). | Technical | High | Open |
| C-006 | Land early | This PR stays scoped to procedure, inline anti-patterns, toolguide guides and the Markdown content form so that #195 can rebase on it. | Process | Medium | Open |

### Key Entities

- **Procedure**: a reusable, ordered way of working; identity, title, description, entry and exit conditions, ordered
  steps (each naming the role that performs it), inline anti-patterns, references.
- **Anti-pattern entry**: a value carried inside a styleguide or procedure: name, description, optional bad and good
  examples. Not an artifact; no identity of its own.
- **Toolguide guide**: Markdown content that belongs to one toolguide, located by that toolguide's guide path.
- **Known-failure entry**: one pinned pack file expected to differ, with where it differs, why, and the open-packs issue
  that fixes it.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The gate's red baseline loses the `procedure` and `other:toolguides` entries, and the gate passes at the pinned commit. — [build] · no-op passable: no
- **SC-002**: 41 of 41 procedure files across the three packs are accounted for: each round-trips or matches a known-failure entry keyed to an open-packs issue. — [build] · no-op passable: no
- **SC-003**: Every toolguide guide in the built-in pack round-trips with equal content. — [build] · no-op passable: no
- **SC-004**: Every inline anti-pattern entry in the built-in pack's styleguides and procedures survives the round trip. — [build] · no-op passable: no
- **SC-005**: Zero tolerance code paths: no retired or variant field name is accepted anywhere in the procedure reader, writer or schema. — [build] · no-op passable: no

## Assumptions

- The internal and qa packs are not covered by the gate; they are proven by corpus tests only.
- Linking inline anti-pattern entries to the anti-pattern graph nodes is out of scope (#154).
- The 14 sanitized-shape violations found in already-merged schemas are listed on epic #186 as follow-ups and are not
  changed here.

## Out of Scope

- Mission types, mission steps and mission templates (PR-B of #194).
- Graph-node tags (#236); edge authority for references (#189); the fate of the standalone procedure kind (#150) beyond
  round-tripping it.
- Any change to core or to open-packs content in this repository.
