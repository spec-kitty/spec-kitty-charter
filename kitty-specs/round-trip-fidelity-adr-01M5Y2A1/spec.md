# Mission Specification: Round-trip fidelity decision (ADR-0036)

**Mission Branch**: `feat/round-trip-fidelity-adr`
**Created**: 2026-10-27
**Status**: Draft
**Mission**: documentation
**Input**: Issue #188 (part of epic #186): record the operator's rulings on round-trip fidelity in a
new ADR that defines, per file kind, what "the same meaning" compares, how an unknown field is
refused, and how a pack inconsistency is filed against `spec-kitty-open-packs`. No production code.

## Documentation Scope

**Iteration Mode**: `mission_specific` (one decision record and the pages it changes).
**Target Audience**:
- **Charter service contributors** (Java engineers building #187 and #189): the equality rules, the
  refusal path and the per-kind table, precise enough to build a comparator from.
- **Pack authors and open-packs maintainers**: what a pack must look like to pass, and how a
  finding reaches them.
- **Core maintainers**: what changes on the wire (`pack_unknown_key` retired) and the
  forward-compatibility cost.

Mixed-audience flag: the ADR serves all three; its frontmatter names each reader.
**Selected Divio Types**: explanation (the ADR) and reference (the per-kind table inside it).
**Languages Detected**: Markdown docsite (doc-kitty convention).
**Generators to Use**: none.

### Source of truth

The operator's rulings on #188 (2026-10-27) and #195 (presets), and the pre-spec grounding squad
(`research/grounding-squad.md`). This mission records those rulings and derives the rules from
them; it does not re-ask them. Readings the agent derived (not ruled) are marked as such in the ADR
and were posted on #188 for the operator to overrule.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Build the round-trip comparator without re-deciding (Priority: P1)

A contributor on #187 reads ADR-0036 and builds the comparator that judges whether a written file
has the same meaning as its source, for every file kind the gate covers.

**Why this priority**: #187 cannot say "equal" without it; the epic is blocked on it.

**Independent Test**: For each worked example in the ADR, a reader who has not seen this mission
says "equal", "not equal" or "refused" and gives the rule, from the ADR alone.

**Acceptance Scenarios**:

1. **Given** a source file and a written file that differ only in key order, quoting and comments,
   **When** the contributor applies the ADR, **Then** they judge them equal and cite the rule.
2. **Given** two DRG shards whose edges differ only in order and repetition, **When** the
   contributor applies the ADR, **Then** they judge them equal; **and given** a reverse edge on the
   same pair, **Then** they judge it a pack inconsistency, not a duplicate.
3. **Given** a list of tactic steps in a different order, **When** the contributor applies the ADR,
   **Then** they judge the files not equal.

### User Story 2 - Know what happens to an unknown field (Priority: P1)

A contributor on #189, or a pack author, learns what the service does when a file carries a field
its schema does not declare: where it is refused, with which code, and what the reader sees.

**Independent Test**: the ADR names the refusal path, the finding's content (file, JSON pointer,
"unknown key"), the wire code per file kind, and the contract follow-up.

**Acceptance Scenarios**:

1. **Given** a `pack.yaml` with an undeclared key, **When** it is loaded, **Then** the ADR says the
   pack is refused as `pack_unreadable` with a finding naming the key, and nothing is carried through.
2. **Given** an artifact file with an undeclared key, **When** it is loaded, **Then** the ADR says it
   takes the same path as any other schema violation of that kind.

### User Story 3 - File a pack inconsistency (Priority: P2)

A contributor whose round trip exposes an inconsistency in a source pack files it against
`spec-kitty-open-packs` and knows what this repository does until the fix lands.

**Independent Test**: the ADR gives a filing checklist (where, what the issue must carry, what
follows in this repository) that a contributor can follow without asking.

### Edge Cases

- A kind with no schema yet (mission types, presets, skills, glossary packs, `pack-manifest.yaml`).
- An intentionally open map (styleguide `tooling`, charter `overrides`).
- An explicit `null` where the schema accepts it, versus absent.
- Non-YAML files: Markdown bodies, binaries.
- A finding that belongs to core (a schema or validator gap), not to the packs.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status | Delivery | No-op passable? |
|----|-------------|--------|----------|-----------------|
| FR-001 | ADR-0036 records the four operator rulings of #188 as decisions, citing the rulings, and states which statements are the agent's derived readings. | Draft | [build] | no |
| FR-002 | The ADR defines meaning as a comparison of the source file with the written file (not record against record), after reading both into a canonical form. | Draft | [build] | no |
| FR-003 | The ADR defines the generic YAML meaning rules: field set, absent / `null` / empty / schema default, scalar typing, key order, quoting and style, comments, list order (sequence versus set), and opaque open maps. | Draft | [build] | no |
| FR-004 | The ADR decides how an unknown field is refused: in which ring, through which existing path, the finding's content, and the wire code per file kind, and it retires `pack_unknown_key`. | Draft | [build] | no |
| FR-005 | The ADR carries a per-kind table covering every rule in `contracts/packs/file-kinds.yaml` (matched and excluded) plus non-YAML files, giving for each kind what is compared, which lists are sets and the identity of a set member. | Draft | [build] | no |
| FR-006 | The ADR defines the canonical DRG edge form (key, value, set comparison) and how it relates to ADR-0027's one-per-pair invariant and ADR-0028's tension relationship. | Draft | [build] | no |
| FR-007 | The ADR prescribes how a discovered pack inconsistency is filed against `spec-kitty-open-packs`, when it belongs to core instead, and what this repository does until the pin moves. | Draft | [build] | no |
| FR-008 | The ADR scopes kinds that have no schema yet and states when unknown-field refusal starts to apply to them. | Draft | [build] | no |
| FR-009 | The ADR lists what it supersedes in part or clarifies (ADR-0017 R5/R13, ADR-0025 D5, ADR-0027 D2, open question #29) and the follow-up work it creates, and those records carry a dated pointer to ADR-0036. DIV-005/006 describe the code as it stands; the ADR names their closure as follow-up work, and `architecture/descriptors.md` carries a Planned note. | Draft | [build] | no |
| FR-010 | The ADR states how the charter's round-trip wording relates to ruling 2, without editing the charter unless the operator rules so on #188. | Draft | [build] | no |
| FR-011 | The ADR index row, a changelog entry and any spelling words are added, and the docs checks pass. | Draft | [build] | no |
| FR-012 | ADR-0036 has been through an adversarial review squad, with every finding dispositioned, before it leaves Proposed. | Draft | [build] | no |

### Non-Functional Requirements

| ID | Requirement | Threshold | Status |
|----|-------------|-----------|--------|
| NFR-001 | Docs checks | `python3 scripts/docs/check_docs.py all`, markdownlint-cli2 and cspell report 0 errors on the changed files | Draft |
| NFR-002 | Worked examples | At least one example per verdict (equal, not equal, refused), and one per DRG case (reordered, repeated, reverse edge) | Draft |
| NFR-003 | Coverage of the classification | 100% of the rules in `file-kinds.yaml` appear in the per-kind table | Draft |

### Constraints

| ID | Constraint | Status |
|----|------------|--------|
| C-001 | No production or test code changes; the mission touches docs and mission artifacts only. | Active |
| C-002 | Stay in #188's scope: the gate's mechanics belong to #187, the registry and whole-pack seam to #189, presets and the `required_<plural>` migration to #195/#206. | Active |
| C-003 | Accepted ADRs are not edited in their decision text; they get a dated "superseded in part" or clarification pointer (docs/adr/README.md). | Active |
| C-004 | Open questions go to the operator on #188, not decided silently. | Active |

### Key Entities

- **Source file / written file**: the pack file as authored, and as the service writes it back.
- **Canonical form**: the comparable value both are read into.
- **Set member identity**: the key that makes two members of a set list the same member.
- **Pack inconsistency**: a source-pack shape that the domain cannot hold or that breaks a domain invariant.

## Success Criteria *(mandatory)*

- **SC-001**: A fresh-context reviewer reaches the ADR's verdict on every worked example from the ADR alone. — [build] · no-op passable: no
- **SC-002**: Every rule in `file-kinds.yaml` has a row in the per-kind table. — [build] · no-op passable: no
- **SC-003**: Every record the ADR supersedes or clarifies points back to it. — [build] · no-op passable: no
- **SC-004**: The docs checks report zero errors. — [build] · no-op passable: no

## Assumptions

- The operator's open answers on #188 (expected-failure list, charter wording, schemaless kinds)
  may arrive after the draft; the ADR records the recommendation and stays Proposed until they do.
- The schema flip, reader changes, contract change and test changes are implemented by #187,
  #189 or follow-ups the operator places; this mission names them only.
