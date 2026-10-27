# Mission Specification: Mission step contract round trip

**Mission Branch**: `feat/mission-step-contract-round-trip`
**Created**: 2026-10-27
**Status**: Draft
**Input**: Issue [#191](https://github.com/spec-kitty/spec-kitty-charter/issues/191), part of epic
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186) (Wave 1, alongside #190, #192
and #193).

## Intent Summary

The **primary actor** is the whole-pack round-trip gate (#187) and, through it, every pack author
whose pack carries mission step contracts. The **trigger** is a pack directory being loaded into the
service's domain and written back. The **outcome**: every `*.step-contract.yaml` the pinned packs
carry is read into a domain artifact of kind `mission_step_contract` and written back with the same
meaning, so the gate turns this kind from expected red to green. The **invariant**: nothing a reader
of the file can observe is lost or invented; a field outside the schema is refused, never carried.

**Confirmation basis.** The operator's brief for this Wave 1 mission states the scope (round trip
only, through the ADR-0037 registry, flip only this kind in the gate), the rulings (unknown fields
refused, equality by meaning, pack inconsistencies fixed upstream) and the channel for questions
(the issue). Discovery was minimized on that instruction; the pre-spec grounding squad
([research/grounding-squad.md](research/grounding-squad.md)) replaced the interview, and the calls
made without a ruling are listed under Assumptions and asked on #191.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Every pinned step contract round-trips (Priority: P1)

The gate loads the built-in pack at the pinned open-packs commit, writes it to a fresh directory and
compares each source file with the written one by meaning.

**Why this priority**: it is the issue's done-when and the epic's safety net.

**Independent Test**: the whole-pack gate runs with this kind no longer expected red; each of the
21 built-in step contracts is green.

**Acceptance Scenarios**:

1. **Given** the built-in pack at `openPacksRef`, **When** the gate runs, **Then** all 21 `*.step-contract.yaml` files are green and no other file changes status.
2. **Given** `review.step-contract.yaml` with one gate binding (`fail_open: true`), **When** it round-trips, **Then** the written binding has the same six fields and `fail_open` is still a boolean.
3. **Given** a step whose `guidance` is a folded multi-line string, **When** it round-trips, **Then** the written value is the same string, trailing newline included.

---

### User Story 2 - A step contract is a domain artifact of its pack (Priority: P1)

A pack's step contracts load into the `Pack` aggregate as artifacts of kind `mission_step_contract`,
with their steps in authored order, and are written back at the path they came from.

**Why this priority**: the round trip goes through the domain, not around it.

**Independent Test**: load a pack directory with step contracts in a nested directory, write it,
reload, and compare the aggregates and the paths.

**Acceptance Scenarios**:

1. **Given** a pack with `missions/built_in_step_contracts/implement.step-contract.yaml`, **When** the whole pack is loaded and written, **Then** the file is written at the same relative path.
2. **Given** a new step contract with no recorded path, **When** it is written, **Then** it lands at `<id>.step-contract.yaml` in the pack directory, where a registration creates a new artifact and a load finds it again.
3. **Given** a step contract file with a key the schema does not declare, at any depth, **When** the pack loads, **Then** the file is refused as a schema violation naming the key, and the whole-pack write refuses the pack.
4. **Given** two step contract files declaring the same id in one pack, **When** the pack loads, **Then** it is refused as for any kind.

---

### User Story 3 - A sibling kind with a different file name joins the same way (Priority: P2)

A kind whose schema name or file name differs from its token (this one; `agent_profile` in #190)
binds itself by naming them, with no change to the store, the strategy or the aggregate.

**Why this priority**: it removes the collision #190 would otherwise hit, and the silent no-load
failure the grounding found.

**Independent Test**: a registry test checks, for every bound kind, that its schema name and file
token are what `file-kinds.yaml` maps, and that its files load.

**Acceptance Scenarios**:

1. **Given** every bound kind, **When** the registry test runs, **Then** each kind's new-file name is classified by `file-kinds.yaml` to that kind's schema.
2. **Given** a bound kind with files in the pinned pack, **When** the pack loads, **Then** at least one artifact of that kind is in the aggregate.
3. **Given** the five kinds bound before this mission, **When** a pack holding none of this kind's files is fingerprinted, **Then** its fingerprint is unchanged.

### Edge Cases

- A step with `delegates_to`, `command` and `guidance` absent round-trips with them absent; the writer never adds `null` or empty values.
- A gate binding or input that writes a field equal to its schema default (`handler_kind`, `fail_open`, `optional`) keeps it written; one that omits it keeps it omitted.
- `inputs` appears in no pinned file; it is read and written all the same (planted test).
- The `qa` pack's 7 contracts have no `pack.yaml` above them, so no gate loads that pack; they are covered by a corpus test that reads and writes every pinned `*.step-contract.yaml` file directly.
- A string that a YAML 1.1 reader would read as a boolean or number (`"1.0"`, `"true"`) stays a string when written.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status | Delivery | No-op passable? |
|----|-------------|--------|----------|-----------------|
| FR-001 | A step contract file is read into a domain artifact of kind `mission_step_contract` holding every field the schema declares: id, schema version, action, mission, steps in order (id, description, command, inputs, delegates_to kind and candidates, guidance) and gate bindings in order (every field). | Draft | [build] | no |
| FR-002 | The artifact is written back as a step contract file whose meaning equals the source under ADR-0036: optional fields absent stay absent, written ones stay written, lists keep order and repetition, booleans stay booleans. | Draft | [build] | no |
| FR-003 | The kind is bound in the ADR-0037 registry, so the pack load, the whole-pack load and write, the commit path and the source fingerprint include its files. | Draft | [build] | no |
| FR-004 | A binding names the schema that validates its files and the token in its file names; the store and the strategy resolve them through the registry, never by assuming they equal the kind token. | Draft | [build] | no |
| FR-005 | The whole-pack gate models `mission-step-contract`; its expected-red entry is removed, and every built-in step contract is green. | Draft | [build] | no |
| FR-006 | Every `*.step-contract.yaml` in the pinned open-packs checkout (28 files, including the `qa` pack) reads, writes and reads back with equal meaning. | Draft | [build] | no |
| FR-007 | A field outside the schema, at any level of a step contract, refuses the file as a schema violation that names the key. | Draft | [ratchet] | no |
| FR-008 | The artifact mints no relationships; step delegations stay values of the artifact. | Draft | [build] | no |

### Non-Functional Requirements

| ID | Requirement | Status |
|----|-------------|--------|
| NFR-001 | The ArchUnit rules hold: the entity lives in `packs.domain` and names no storage type; the kernel is unchanged. | Draft |
| NFR-002 | Packs without step contract files keep their source fingerprint byte for byte (0 changed digests in the fingerprint tests). | Draft |
| NFR-003 | `./gradlew verifyAll` passes on Java 25 and the docs checks pass with 0 findings. | Draft |

### Constraints

| ID | Constraint | Status |
|----|------------|--------|
| C-001 | No change to the HTTP contract (`contracts/charter/`); a needed change is raised on #191 instead. | Draft |
| C-002 | No reader rule, normalization or exemption works around a pack inconsistency (ADR-0036 ruling 3); one found is filed in `spec-kitty-open-packs`. | Draft |
| C-003 | Edits to shared files are append-only blocks for this kind, except the schema-name seam (FR-004), which is one isolated commit. | Draft |
| C-004 | Gate-binding semantics (#155), promotion to a served kind (#148) and a role field on steps (ADR-0032, upstream schema) are out of scope. | Draft |

## Success Criteria *(mandatory)*

- **SC-001**: 28 of 28 pinned step contract files round-trip with equal meaning. — [build] · no-op passable: no
- **SC-002**: The gate reports 21 of 21 built-in step contracts green and no new red or expected-red entry. — [build] · no-op passable: no
- **SC-003**: Binding this kind changed no line of the aggregate, the store or the strategy beyond the schema-name seam. — [ratchet] · no-op passable: yes (paired with SC-001 as its positive control)
- **SC-004**: `./gradlew verifyAll` is green on Java 25. — [ratchet] · no-op passable: yes (paired with SC-001)

## Key Entities

- **Mission step contract**: the steps of one action of one mission type; identity is its `id` within the pack.
- **Contract step**: an id, a description and optionally a command, inputs, a delegation and guidance.
- **Delegation**: an artifact kind and the non-empty list of candidate ids that could carry out the step.
- **Gate binding**: inert data naming a lane transition and a handler (#155 owns what it means).
- **Step input**: a flag, its source and whether it is optional.

## Assumptions

- **A1 (schema name)**: the binding names its schema; ADR-0037 decision 4 is amended in place
  (it is still Proposed). Asked on #191.
- **A2 (identity)**: the artifact's local id is the file's `id`; the CRG nodes in
  `mission_step_contract.graph.yaml` use `<mission>/<action>`. The round trip is unaffected; which
  identity is canonical is asked on #191.
- **A3 (contract prose)**: the fingerprint descriptions in `contracts/charter/` that list the
  digested kinds become incomplete; they are not edited here (C-001). Asked on #191.
- **A4 (duplicate step ids)**: stay a validate-mode rule, not an entity invariant.
- **A5**: built-in and qa fingerprints and ETags change once (ADR-0037, Negative).

## Out of scope

- Gate-binding semantics (#155), promotion to a full kind with endpoints (#148).
- Mission types, mission steps and actions (#194); a role field on steps (upstream schema).
- The other Wave 1 kinds (#190, #192, #193).
