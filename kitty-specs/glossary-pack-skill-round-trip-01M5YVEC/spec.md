# Mission Specification: Glossary pack and skill round trip

**Mission Branch**: `feat/192-glossary-pack-skill-round-trip`
**Created**: 2026-10-27
**Status**: Draft
**Input**: Issue [#192](https://github.com/spec-kitty/spec-kitty-charter/issues/192), part of epic
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186) (Wave 1, alongside #190, #191
and #193).

## Intent Summary

The **primary actor** is the whole-pack round trip (the #187 gate and the whole-pack seam) and,
through it, every pack author whose pack carries glossary packs or pack skills. The **trigger** is a
pack directory being loaded into the service's domain and written back. The **outcome**: every
`*.glossary-pack.yaml` and every `*.skill.yaml`, with the Markdown prompt body its `body_path`
names, is read into a domain artifact of kind `glossary_pack` or `skill` and written back with the
same meaning, the skill and its body as a pair; the gate turns glossary packs from expected red to
green. The **invariant**: nothing a reader of the files can observe is lost or invented; a field
outside the schema is refused, never carried.

**Confirmation basis.** The operator's brief states the scope (these two kinds only, through the
ADR-0037 registry, flip only this mission's kinds in the gate), the rulings (unknown fields refused,
equality by meaning, pack inconsistencies fixed upstream) and the channel for questions (the issue).
The pre-spec grounding squad ([research/grounding-squad.md](research/grounding-squad.md)) replaced
most of the interview. The one scope decision it left open, whether the skill body is in scope, was
put to the operator with the Intent Summary and confirmed: the body is in scope, through a
companion-file seam recorded in a new ADR (decision `01M5YVEVMPMHPE0GDFQD0BZA6F`).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The built-in glossary pack round-trips in the gate (Priority: P1)

The gate loads the built-in pack at the pinned open-packs commit, writes it to a fresh directory
and compares each source file with the written one by meaning.

**Why this priority**: it is the issue's done-when in the gate, and the only kind of this mission
the built-in pack carries.

**Independent Test**: the whole-pack gate runs with glossary packs no longer expected red;
`glossary_packs/spec-kitty-core.glossary-pack.yaml` is green.

**Acceptance Scenarios**:

1. **Given** the built-in pack at `openPacksRef`, **When** the gate runs, **Then** `spec-kitty-core.glossary-pack.yaml` is green, its gate kind is the glossary-pack schema (no longer `other:glossary_packs`), and no other file changes status.
2. **Given** a term whose `definition` is a folded multi-line string, **When** it round-trips, **Then** the written value is the same string, trailing newline included.
3. **Given** a term with `confidence: 1.0`, **When** it round-trips, **Then** the written value is the number 1.0, not a string.

---

### User Story 2 - A skill and its prompt body round-trip as a pair (Priority: P1)

A pack's skills load into the `Pack` aggregate as artifacts of kind `skill`, each holding its
descriptor and, for the prompt form, the text of the body its `body_path` names. The whole-pack
write puts the descriptor at its source path and the body beside it at `body_path`.

**Why this priority**: the issue asks for the pair to be kept on write; a skill without its body has
no prompt.

**Independent Test**: load `packs/internal` (5 skills), write the whole pack, reload, and compare the
five descriptors by meaning and the five bodies exactly.

**Acceptance Scenarios**:

1. **Given** `skills/land-pr.skill.yaml` with `body_path: land-pr.skill.md`, **When** the whole pack is loaded and written, **Then** both files are written at the same relative paths and the body's text is identical.
2. **Given** a skill whose body file is missing, or whose `body_path` leaves the pack directory, **When** the pack loads, **Then** the skill is refused as a read violation naming the path, and the whole-pack write refuses the pack.
3. **Given** a change to a skill's body only, **When** the pack is fingerprinted, **Then** its source fingerprint changes.
4. **Given** a loaded pack, **When** the whole-pack load lists the files it does not model, **Then** no skill body is among them.

---

### User Story 3 - A glossary pack is a domain artifact of its pack (Priority: P1)

A pack's glossary packs load into the `Pack` aggregate as artifacts of kind `glossary_pack`, terms in
authored order, and are written back at the path they came from.

**Why this priority**: the round trip goes through the domain, not around it.

**Independent Test**: load a pack whose glossary pack sits in `glossary_packs/`, write it, reload,
and compare the aggregates and the paths.

**Acceptance Scenarios**:

1. **Given** `glossary_packs/spk-internal.glossary-pack.yaml` declaring `id: spk-internal-glossary`, **When** the whole pack is loaded and written, **Then** the file is written at the same relative path with the same id; no file is renamed.
2. **Given** a new glossary pack with no recorded path, **When** it is written, **Then** it lands at `<id>.glossary-pack.yaml` in the pack directory, where a load finds it again.
3. **Given** a glossary pack with two terms of the same `surface`, or with no terms, **When** the pack loads, **Then** the file is refused.
4. **Given** a glossary pack or skill file with a key the schema does not declare, at any depth, **When** the pack loads, **Then** the file is refused as a schema violation naming the key.

---

### User Story 4 - Every pinned file of both kinds round-trips (Priority: P2)

Not every pack is loaded by the gate or the seam test: the gate covers `built-in` only, and `qa`
has no `pack.yaml`. A corpus test reads, writes and reads back every pinned file of both kinds.

**Why this priority**: it is the only evidence for skills and for three of the four glossary packs.

**Independent Test**: the corpus test lists the files it covered; the list is the 4 glossary packs
and 5 skill pairs of the pinned checkout.

**Acceptance Scenarios**:

1. **Given** the pinned open-packs checkout, **When** the corpus test runs, **Then** 4 of 4 glossary packs and 5 of 5 skills (with bodies) round-trip with equal meaning.
2. **Given** a writer that drops one optional field, **When** the corpus test runs against it, **Then** the test fails (positive control).

### Edge Cases

- Optional term fields (`see_also`, `synonyms_to_avoid`, `introduced_in_mission`, `aliases`, `banned_synonyms`) and optional skill fields (`triggers`, `parameters`, `invocation`, `tools`, `version`, `maintainers`, `overrides`, `enhances`, `expands_to`) absent stay absent; the writer never adds `null`, an empty list or a default.
- A skill declaring `side_effects` and no `model_invocable` keeps `model_invocable` absent; core's forcing of it to `false` is not applied.
- `version: 1.0.0` written in a skill stays written, and stays the string `1.0.0`.
- A wrapper-form skill (`expands_to`, no `body_path`) has no body and no companion file; no pinned file has this form, so it is covered by planted tests.
- A body is compared as text: no trimming, no newline normalization, UTF-8.
- The internal glossary pack's id differs from its file stem (open-packs#47). It round-trips as authored; no rule tolerates or corrects it.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status | Delivery | No-op passable? |
|----|-------------|--------|----------|-----------------|
| FR-001 | A glossary pack file is read into a domain artifact of kind `glossary_pack` holding every field the schema declares: id, provenance, description, and the terms in authored order (surface, definition, confidence, status, see_also, introduced_in_mission, synonyms_to_avoid, aliases, banned_synonyms). | Draft | [build] | no |
| FR-002 | A skill file is read into a domain artifact of kind `skill` holding every field the schema declares, and, for a prompt-form skill, the text of the body its `body_path` names. | Draft | [build] | no |
| FR-003 | Each artifact is written back with meaning equal to the source under ADR-0036: optional fields absent stay absent, written ones stay written, lists keep order and repetition, numbers and booleans keep their type. A skill's body is written at its `body_path`, beside the descriptor, with identical text. | Draft | [build] | no |
| FR-004 | Both kinds are bound in the ADR-0037 registry, so the pack load, the whole-pack load and write, the commit path and the source fingerprint include their files, a skill's body included. | Draft | [build] | no |
| FR-005 | A binding can declare companion files read and written with an artifact (text, relative to the artifact's file, contained in the pack); a missing or escaping companion is a read violation. The seam is recorded in a new ADR. | Draft | [build] | no |
| FR-006 | `contracts/packs/file-kinds.yaml` maps `**/*.glossary-pack.yaml` and `**/*.skill.yaml` to closed schemas (`glossary-pack`, `skill`), replacing their exclusions. | Draft | [build] | no |
| FR-007 | A field outside the schema, at any level of either kind, refuses the file as a schema violation that names the key. A glossary pack with no terms or a repeated surface is refused. | Draft | [build] | no |
| FR-008 | The whole-pack gate models the glossary-pack schema; its expected-red entry is removed, and the built-in glossary pack is green. | Draft | [build] | no |
| FR-009 | Every `*.glossary-pack.yaml` and every `*.skill.yaml` with its body in the pinned open-packs checkout reads, writes and reads back with equal meaning. | Draft | [build] | no |
| FR-010 | Neither artifact mints relationships. | Draft | [build] | no |

### Non-Functional Requirements

| ID | Requirement | Status |
|----|-------------|--------|
| NFR-001 | The ArchUnit rules hold: the entities live in `packs.domain` and name no storage type; the kernel is unchanged. | Draft |
| NFR-002 | Packs without glossary pack or skill files keep their source fingerprint byte for byte (0 changed digests in the fingerprint tests). | Draft |
| NFR-003 | `./gradlew verifyAll` passes on Java 25 and the docs checks pass with 0 findings. | Draft |

### Constraints

| ID | Constraint | Status |
|----|------------|--------|
| C-001 | No change to the HTTP contract (`contracts/charter/`); a needed change is raised on #192 instead. | Draft |
| C-002 | No reader rule, normalization or exemption works around a pack inconsistency (ADR-0036 ruling 3); one found is filed in `spec-kitty-open-packs`. | Draft |
| C-003 | Edits to shared files are append-only blocks for these kinds. The schema-name seam is the one #190 and #191 add (`KindBinding.schemaName()`, `KindBindings.bySchema`), taken unchanged, not built a second time. | Draft |
| C-004 | The companion-file seam carries text only; bytes (asset payloads, templates, #193) and toolguide guides (`guide_path`) extend it later and are out of scope. | Draft |
| C-005 | This repository's own docsite glossary (`site/.contextive/definitions.yaml`, ADR-0029) is not touched; a glossary pack is pack content. | Draft |

## Success Criteria *(mandatory)*

- **SC-001**: 4 of 4 pinned glossary pack files and 5 of 5 pinned skills, with their bodies, round-trip with equal meaning. — [build] · no-op passable: no
- **SC-002**: The gate reports the built-in glossary pack green and no new red or expected-red entry. — [build] · no-op passable: no
- **SC-003**: Binding these kinds changed no line of the aggregate, beyond the companion-file seam and the append-only registry lines (C-003, C-004). — [ratchet] · no-op passable: yes (paired with SC-001 as its positive control)
- **SC-004**: `./gradlew verifyAll` is green on Java 25. — [ratchet] · no-op passable: yes (paired with SC-001)

## Key Entities

- **Glossary pack**: a named, provenance-tagged collection of terms; identity is its `id` within the pack.
- **Glossary term**: a surface (unique within the pack), its definition, a confidence and a status, with optional cross-references, aliases and synonyms to avoid.
- **Skill**: a thin, parameterised entry point of a pack, in prompt form (with a body) or wrapper form (expanding to a command); identity is its `id` within the pack.
- **Skill body**: the Markdown prompt text of a prompt-form skill, held with the skill and written beside it.

## Assumptions

- **A1 (glossary schema source)**: core exports no glossary-pack schema; the closed schema is
  authored here from core's `GlossaryPack`/`GlossaryTerm` model, and proposed back to core.
- **A2 (skill schema)**: the skill schema is imported from core's `skill.schema.yaml` as JSON.
  Core's extra checks beyond it (id length, `overrides` xor `enhances`, unique parameter names,
  cli-target metacharacters) are not entity invariants in this mission.
- **A3 (siblings)**: #190 or #191 merges first and brings the schema-name seam; this mission
  rebases onto it.
- **A4**: the qa, internal and event-driven fingerprints and ETags change once (ADR-0037, Negative).
- **A5 (id versus file stem)**: the internal glossary pack keeps its authored path and id; the
  mismatch is a pack inconsistency tracked upstream as open-packs#47.

## Out of scope

- Asset payloads and templates (#193), toolguide guides, the other Wave 1 kinds (#190, #191).
- Wire endpoints for glossary packs or skills; promotion to a served kind.
- Core's runtime skill behavior (rendering, namespaces, `model_invocable` forcing).
