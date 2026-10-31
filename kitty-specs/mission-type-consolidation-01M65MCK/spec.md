# Mission Specification: Mission-type consolidation into the sanitized model

**Mission Branch**: `issue-194-mission-type-consolidation`
**Created**: 2026-10-30
**Status**: Draft
**Input**: Charter #194 PR-B (part of epic #186): consolidate the mission tier into the sanitized model of a mission
type and its mission steps, and bring it into the whole-pack round trip. Grounding: `research.md`, the squad reports
under `research/squad/`, the decision moments under `decisions/`, and
[ADR-0044](../../docs/adr/0044-mission-types-consolidated-into-one-sanitized-shape.md), written first.

## Intent Summary

A charter maintainer runs the whole-pack round trip against the pinned Charter Pack content. Each mission type is read
from one designed file, with its steps, their order, roles, prompts, guidelines, expected artifacts and doctrine
references, into the charter domain and written back with equal meaning (ADR-0036). The eight old file families that
spread a mission type over a pack are retired: the service does not read them, and a stale one is refused, not
skipped. The pinned packs are migrated to the new shape with the service's own writer and re-pinned.

Operator decisions shaping this mission (2026-10-28 and 2026-10-30, recorded on #194 and under `decisions/`):

- The sanitized shape wins over a round trip as-is; every DSO gets a designed schema; no tolerance logic.
- Two kinds in the model, mission type and mission step, with no plural step kind: a mission type holds its steps
  (D1). The store holds one file per type, its steps as sub-items of `steps` (the ordered sequence) and
  `on_demand_steps` (D1a, D3).
- `mission.yaml`, `mission-runtime.yaml`, `governance-profile.yaml`, `expected-artifacts.yaml`, `actions/*/index.yaml`,
  `mission_types/<t>.yaml` and `step.yaml` fold into the mission type (D2, D5). Profiles become roles.
- Layout nested by type, prompts and guidelines as Markdown companions (D4).
- The domain lives in a new `:missions` module (D6).
- The pinned packs are migrated by a throwaway harness; the result lands in `spec-kitty-open-packs` through the
  orchestrator and `openPacksRef` moves to it. No core change.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A mission type round-trips from one file (Priority: P1)

A maintainer's pack holds `missions/<type>/<type>.mission-type.yaml`. It is read into a `MissionType` with its ordered
steps and its on-demand steps, and written back so that it compares equal by meaning.

**Why this priority**: it is the model the rest of the mission rests on.

**Independent Test**: a fixture pack with one mission type of two sequenced steps and one on-demand step round-trips
through the whole-pack store and compares equal; changing one step title is reported as a difference (positive
control).

**Acceptance Scenarios**:

1. **Given** a mission-type file in the sanitized shape, **When** the pack round-trips, **Then** it compares equal and
   the step order is preserved.
2. **Given** a mission-type file whose `id` is not its file stem, or whose path is not
   `missions/<id>/<id>.mission-type.yaml` (at the pack root, nested deeper, under `mission_types/`), **When** read or
   validated, **Then** it is refused naming the file; the correctly placed neighbour is accepted.
3. **Given** two steps with the same id across `steps` and `on_demand_steps`, **When** read, **Then** the read is
   refused naming the id.
4. **Given** a file carrying a retired field (`display_name`, `depends_on`, `agent_profile`, `sequence_index`, an empty
   list, `blocking: false`), **When** read, **Then** the schema refuses it naming the field; the sanitized neighbour is
   accepted.
5. **Given** a step with `role: operator` and one with no role, **When** read and written, **Then** both round-trip as
   authored.
6. **Given** the round trip through the production writer, **When** a step title is changed, two steps are swapped, an
   on-demand step is dropped or a reference is reordered, **Then** the comparison reports a difference at that path
   (committed positive controls).
7. **Given** a new mission type registered through the store, **When** written, **Then** it lands at
   `missions/<id>/<id>.mission-type.yaml` with its companions under `steps/`; an id with `/` or `..` is refused.

### User Story 2 - Step prompts and guidelines travel with their step (Priority: P1)

A step names `steps/<id>.prompt.md` and optionally `steps/<id>.guidelines.md` beside its mission-type file. Each is read
as a Markdown document belonging to the mission type and written back with equal meaning.

**Independent Test**: a fixture step with a prompt and guidelines round-trips; editing one section of the prompt is
reported as a difference at that section.

**Acceptance Scenarios**:

1. **Given** a step naming a prompt and guidelines, **When** the pack round-trips, **Then** both compare equal section
   by section.
2. **Given** a `prompt_path` that is not `steps/<id>.prompt.md`, names a missing file, or leaves the pack, **When**
   read, **Then** the read is refused naming the mission type and the path.
3. **Given** a prompt whose frontmatter holds only `description`, **When** read and written back, **Then** the
   description is the prompt's detailed description and the file is byte-identical; a prompt frontmatter key other than
   `description`, or guidelines with frontmatter, is refused (ruling Q1 as refined on 2026-10-30).
4. **Given** a `steps/*.prompt.md` file no step names, **When** the whole-pack gate runs, **Then** it reports the file
   red as an unmodelled companion, as for a toolguide guide.
6. **Given** a `prompt_path` present on a step, **When** written, **Then** it is the derived `steps/<id>.prompt.md`
   (S8); **When** a stored value differs, **Then** the load refuses it.
5. **Given** a prompt whose prose names a directive, **When** read, **Then** it stays content and no relationship is
   created.

### User Story 3 - Doctrine references and expected artifacts are kept, never minted (Priority: P1)

A mission type and its steps carry `references` (`{type, id, when?, reason?}`). A step carries
`expected_input_artifacts` (preconditions, `{artifact_key, path_pattern, blocking?}`) and `expected_output_artifacts`
(postconditions, `{artifact_key, artifact_class, path_pattern, blocking?, template_path?}`); the type carries its own
`expected_output_artifacts` (ruling Q2). They round-trip verbatim and in order, and no relationship is
minted from a reference.

**Independent Test**: a fixture type with type-level and step-level references and expected artifacts round-trips
equal, and the pack's relationship set is unchanged by them.

**Acceptance Scenarios**:

1. **Given** references on the type and a step, **When** the pack is read, **Then** they are kept in authored order and
   `MissionType.relationships()` is empty.
2. **Given** a reference whose `type` is not one of `directive`, `tactic`, `paradigm`, `styleguide`, `toolguide`,
   `procedure`, **When** read, **Then** the schema refuses it.
3. **Given** an expected artifact with `blocking: true` and one without, **When** round-tripped, **Then** both keep
   their meaning; `blocking: false` is refused.
4. **Given** the same `artifact_key` at two steps, **When** read, **Then** it is accepted; twice in one list, **Then**
   it is refused.
5. **Given** a `template_path` naming no file or leaving the pack, **When** read, **Then** it is refused naming the
   step and the path.

### User Story 4 - Retired mission files cannot hide (Priority: P1)

A pack that still holds a retired file is refused by name, in the store's load and in validate mode alike. The
retired families: `**/mission_types/*.yaml`, `**/missions/*/mission.yaml`, `**/missions/*/mission-runtime.yaml`,
`**/governance-profile.yaml`, `**/missions/*/expected-artifacts.yaml`, `**/actions/*/index.yaml`,
`**/mission-steps/**/*` (step.yaml, prompt.md, guidelines.md) and `**/missions/*/command-templates/*`.

**Independent Test**: a fixture pack holding one retired file of each family, Markdown included, fails the load and
validate mode with one refusal per file, naming the file and the rule's reason; the gate reports each in its
`retired:` bucket; adding or removing one changes the pack's fingerprint.

**Acceptance Scenarios**:

1. **Given** a pack with `missions/x/mission-runtime.yaml`, **When** loaded or validated, **Then** it is refused naming
   the file as retired.
2. **Given** the pinned packs at the new pin, **When** the open-packs round trip runs, **Then** no retired file is
   found.

### User Story 5 - The pinned mission tier is migrated and round-trips (Priority: P1)

The built-in, cave-kitty and qa mission tiers at the current pin are migrated by a throwaway harness that maps them by
ADR-0044's tables and writes them with the service's own writer. The result lands in `spec-kitty-open-packs`; the
charter re-pins to it and re-vendors `packs/qa`.

**Independent Test**: at the new pin, `:packs:openPacksRoundTrip` loads every pack with zero refusals and
`:packs:wholePackRoundTrip` reports every mission-type file green; `other:missions` is narrowed to mission templates.

**Acceptance Scenarios**:

1. **Given** the harness run against the old pin, **When** its output is compared field by field with the sources,
   **Then** every kept field maps as ADR-0044 says and every dropped field is in the drop list.
2. **Given** the new pin, **When** the gates run, **Then** six mission types round-trip equal and no harness class
   remains in the source tree.

### Edge Cases

- A step with no prompt (research `accept`, the empty retrospect prompts) has no `prompt_path`.
- The qa steps' prompts are its `command-templates/<id>.md`; cave-dev's steps exist only in its runtime and command
  templates.
- software-dev's runtime `discovery` reaches step directory `research` through its prompt: one step, `discovery`.
- software-dev's `retrospect` action index has no step: its references are not migrated, and an open-packs issue says
  so.
- Directive references by stem (`042-common-docs`) are migrated to ids (`DIRECTIVE_042`).
- `missions/**/README.md` orientation notes and `missions/<type>/templates/**` stay outside the model (an exclusion and
  the narrowed expected-red entry).

> **Note (2026-10-31).** ADR-0044 decision 5 and the plan superseded three statements below: mission templates are
> `template` payloads (`*/missions/*/templates/**/*`, id `<type>/<path>`), not entries on a narrowed `other:missions`
> expected-red entry, which is gone; and the README exclusions are named per place (`*/missions/README.md`,
> `*/missions/*/README.md`, `*/missions/*/templates/**/README.md`), not `*/missions/**/README.md`. FR-003, FR-009 and
> the out-of-scope line keep their original wording for the record.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status |
|---|---|---|
| FR-001 | `contracts/packs/schemas/mission-type.schema.json`: a closed Draft 2020-12 schema of the ADR-0044 decision 3 shape (type, step sub-item, expected artifact, reference), with every optional list at least one entry. | Proposed |
| FR-002 | Structure schemas `mission-step-prompt` and `mission-step-guidelines` for the Markdown companions: optional H1 title, preamble, sections; a prompt's optional frontmatter holds only its detailed `description`, guidelines hold none. A step's `description` is at most 160 characters (refined Q1). | Proposed |
| FR-003 | `file-kinds.yaml`: `mission_type` joins `artifact_kinds`; `files` rules for `**/*.mission-type.yaml` and the two companion globs (`content_form: markdown`); at the re-pin the seven #194 mission exclusions are removed, `*/missions/**/README.md` is excluded and a `retired` section names every retired family; unknown top-level keys are refused. | Proposed |
| FR-004 | `ArtifactKind.MISSION_TYPE` (kernel), backed by the existing `NodeKind.MISSION_TYPE`; no `mission_step` artifact or node kind and no fragment plural. | Proposed |
| FR-005 | A new `:missions` Gradle module holding `MissionType`, `MissionStep` and their values (`StepDocument`, `ExpectedArtifact`, `MissionReference`, `PathConventions`), plain Java over the kernel, wired into the ArchUnit slice rules. | Proposed |
| FR-006 | `MissionType` invariants: letter-first kebab id; at least one sequenced step; step ids unique across both collections; artifact keys unique within each list; references restricted to the six guidance kinds; `relationships()` empty. The domain holds no file paths. | Proposed |
| FR-007 | A `MissionTypeBinding` with DSO and YAML format in `:packs`: the file's path is `missions/<id>/<id>.mission-type.yaml`; companions are each step's prompt and guidelines at their derived paths; `template_path` resolves inside the pack; a new mission type is created at its path, directories included. | Proposed |
| FR-008 | Retired mission files are classified `Retired`: refused by name in the load and in validate mode, bucketed `retired:` by the gate, digested in the fingerprint tail. | Proposed |
| FR-009 | The whole-pack gate and the open-packs round trip cover mission types; `other:missions` is narrowed to mission templates and command templates no longer exist. | Proposed |
| FR-010 | A throwaway harness migrates the pinned mission tiers by ADR-0044's tables; its output, a patch bundle and the open-packs issue drafts are delivered to the orchestrator; the harness is deleted before merge. | Proposed |
| FR-011 | `openPacksRef` moves to the migrated open-packs commit; `packs/qa` is re-vendored. | Proposed |
| FR-012 | Docs: ADR-0044 (first), divergence entries (mission type as an artifact kind; retired files), the descriptor and kind pages, `contracts/packs/README.md`, the CHANGELOG and the #186 follow-ups. | Proposed |

### Non-Functional Requirements

| ID | Requirement | Status |
|---|---|---|
| NFR-001 | No HTTP schema or operation change. `/relationships` accepts a `mission_type:` source where it refused 409: recorded in the contract CHANGELOG with a scenario. | Proposed |
| NFR-002 | Zero new suppressions; methods at cyclomatic complexity 15 or below; Checkstyle, Error Prone, NullAway, Spotless and doclint green. | Proposed |
| NFR-003 | No core change; no tolerance of a retired or variant field. | Proposed |
| NFR-004 | Edits to files shared with #260 (`file-kinds.yaml`, `expected-red.yaml`, gate tests, indexes) stay in one block each, touching no #260 entry. | Proposed |

### Constraints

| ID | Constraint | Status |
|---|---|---|
| C-001 | No push to `spec-kitty-open-packs`: migration output goes to the orchestrator as a patch bundle under `research/`. | Fixed |
| C-002 | ADR-0042 S1 to S8 and ADR-0043 S9 to S16 apply unchanged. | Fixed |

### Key Entities

- **MissionType**: id, title, description, content, `requiresSubstantiveSpec`, path conventions, expected artifacts,
  references, `List<MissionStep>` sequence, `Set<MissionStep>` on demand.
- **MissionStep**: id, title, description, role, prompt, guidelines, template, expected artifacts, references. Owned
  by its type.
- **StepDocument**: a step's prompt or guidelines: optional title, preamble, sections `{title, content}`.

## Success Criteria *(mandatory)*

- SC-001: At the new pin, all six pinned mission types round-trip equal in the whole-pack gate and the open-packs round
  trip, with zero refusals.
- SC-002: `./gradlew verifyAll` green on Java 25, with the counts recorded in the PR.
- SC-003: No file of a retired family remains in the pinned packs or the vendored copies.
- SC-004: Every pinned field is mapped or listed as dropped with a reason in ADR-0044 and the research table.

## Out of scope

- Mission templates under `missions/<type>/templates/**` (stay on the narrowed `other:missions` entry; a #186
  follow-up).
- Step contracts (`mission_step_contract`) beyond being left as they are.
- Edges from references, and the `action` node's mapping to steps (#189).
- HTTP operations for mission types.
- Core.
