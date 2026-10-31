---
title: "ADR-0044: Mission types consolidated into one sanitized shape"
description: One mission-type file per type holds its steps as sub-items, with Markdown prompt and guideline companions; eight file families retired; a missions module.
doc_status: draft
updated: 2026-10-30T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0039-a-kind-binding-can-carry-companion-files
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0035-charter-artifact-interface-lives-in-the-kernel
  - adr/0032-steps-name-roles-and-profile-selection-order
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before changing how a mission type or its steps are read, written, validated or modelled, or before touching the missions module.
  - profile: pack-author
    guidance_text: Read this for the one file a mission type is authored in, how its steps, prompts and guidelines are laid out, and which old mission files the service no longer reads.
  - profile: core-maintainer
    guidance_text: Read this for the consolidated mission-type shape core moves to when it is incorporated, and for the files and fields it retires.
---

# ADR-0044: Mission types consolidated into one sanitized shape

## Status

**Proposed** — 2026-10-30. Decider: Stijn Dejongh (operator). Records the operator rulings of 2026-10-28 and
2026-10-30 on [#194](https://github.com/spec-kitty/spec-kitty-charter/issues/194) (part of epic
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186)), held as decision moments under
`kitty-specs/mission-type-consolidation-01M65MCK/decisions/`. The second pull request of #194 (PR-B) implements it;
the record stays Proposed pending the operator's acceptance, which that pull request asks for. The operator's rulings
Q1 (a step's description is for human display; the prompt is for the agent) and Q2 (a step's expected inputs and
outputs close it as precondition, step, postcondition) followed the post-plan review squad's findings. The operator
refined Q1 on 2026-10-30: the step description in the mission-type file is the description and is length-bound, and a
prompt's frontmatter may stay, as a more detailed description.

It **applies** the sanitized-shape rules S1 to S8 of
[ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/) and S9 to S16 of
[ADR-0043](/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides/) to the mission tier, and restates
them here as the rule every storage object of the service follows. It adds no new rule letter: the mission tier needs
none. Neither record is amended. It reverses in passing statements of ADR-0036, ADR-0037, ADR-0040
and ADR-0042 that a mission type is no artifact kind or its files are excluded; each carries a dated
note.

It **takes up** [#148](https://github.com/spec-kitty/spec-kitty-charter/issues/148) for the mission type: the mission
type becomes an artifact kind. Step contracts stay as they are.

## Context

Issue #194 asks the whole-pack round trip to cover the mission tier. At `openPacksRef` `5df92a5d` three packs hold it
(built-in with four mission types, cave-kitty and qa with one each), and the charter vendors the qa pack byte for
byte. One mission type is spread over up to eight file families:

| File | What it holds |
|---|---|
| `mission_types/<t>.yaml` | id, display name, `requires_substantive_spec` |
| `missions/<t>/mission.yaml` | name, description, version, phases, artifacts, path conventions, validation checks, MCP tools, agent context, task metadata, command prompts |
| `missions/<t>/mission-runtime.yaml` | the runtime step list with titles, descriptions, `depends_on`, prompt file and agent profile |
| `missions/<t>/governance-profile.yaml` | the guidance a type selects, plus profile, tool and activation lists |
| `missions/<t>/expected-artifacts.yaml` | artifacts required always and per step |
| `missions/<t>/actions/<a>/index.yaml` | the guidance each action loads |
| `missions/mission-steps/<t>/<s>/step.yaml` | the step's display name, type, profile, `depends_on`, sequence index and template |
| `prompt.md`, `guidelines.md`, `command-templates/*.md` | the step's prompt and guidelines |

The pre-spec research (`kitty-specs/mission-type-consolidation-01M65MCK/research.md` and its squad reports) found
that every one of these files breaks the sanitized rules, and that the families contradict each other:

- **One concept, five homes.** The label (`Software Development`, `Software Dev Kitty`) and the version (`1.0.0`,
  `2.2.0`) differ between files of the same type.
- **Two sequences.** `step.yaml`'s `sequence_index` and `in_action_sequence` and the runtime's `steps` order disagree
  for software-dev and research; 15 of 37 `depends_on` lists contradict the runtime chains, all six of which are
  linear.
- **Ids that do not join.** `tasks_outline` against `tasks-outline`, a `done` step that exists nowhere, the runtime's
  `discovery` reaching step `research` only through a prompt file name, an action index with no step.
- **Always-empty fields** (`guidance: null` and `delegates_to: []` in 37 of 37 steps), **profiles where
  [ADR-0032](/adr/0032-steps-name-roles-and-profile-selection-order/) says roles**, and directive references by
  file stem.

On 2026-10-28 the operator ruled that the sanitized shape wins over a round trip as-is, that the mission tier is
consolidated into `mission_type` and `mission_step`, and that `mission-runtime.yaml`, `actions/*/index.yaml`,
`governance-profile.yaml` and `expected-artifacts.yaml` are retired. On 2026-10-30 the operator ruled on the shape.

## Decision

### 1. The rule: every storage object has a designed, sanitized shape

A storage object of the service (a DSO, one file kind) has a deliberately designed shape with a closed JSON schema
under `contracts/packs/schemas/`. It follows S1 to S16: one identity `id` equal to the file stem, `schema_version`
`"1.0"`, closed objects, snake_case keys, `title` the label, `description` the summary, `content` a body,
`<role>_path` a file reference, references `{type, id, when?, reason?}`, `role` for who acts, no retired, variant,
always-null or empty-list field, and one spelling per meaning. When the pinned packs hold another shape, the shape
still wins: the pinned files are migrated by the service's own writer and re-pinned, and a residual divergence is an
open-packs issue. No reader tolerates a retired or variant field
([ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/) decision 5).

### 2. Two kinds in the model, one file in the store (D1, D1a)

The logical model has two kinds, the **mission type** and the **mission step**. A step belongs to exactly one type,
and the type holds its steps as typed collections. The multiplicity is the type's, not a kind's: there is no plural
`mission_steps` kind, and the fragment plural stays refused under ruling R14 (DIV-011 and the terminology rule are
unchanged).

The store holds one file per mission type. Its steps are sub-items of two plural fields:

- `steps`: the ordered sequence. The list order is the run order; it is the only order (decision 4).
- `on_demand_steps`: steps invoked by an explicit command, outside the sequence (software-dev's `charter`,
  `tasks-outline`, `tasks-packages` and `tasks-finalize`; the documentation and research `retrospect` steps). Their
  authored order is kept, as for `references`, but it is not a run order. Step ids are unique across both lists.

`mission_type` becomes an artifact kind (`ArtifactKind.MISSION_TYPE`), bound in the kind registry
([ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/)); its node kind already exists. `mission_step`
is a domain type, not an artifact kind and not a node kind: it has no file, binding or fragment plural of its own.
The graph keeps `action:<type>/<step>` as a step's node until edge authority is ruled
([#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189)). `mission_step_contract` stays a separate
kind: it holds the structural sub-steps of one mission step and what each delegates to. A step's role and expected
artifacts live on the mission step only. A mission type is a pack-manifest constituent kind.

### 3. The mission-type shape (D2, D5)

`contracts/packs/schemas/mission-type.schema.json`, closed at every level:

| Field | Required | Shape | From |
|---|---|---|---|
| `schema_version` | yes | `"1.0"` | S2 |
| `id` | yes | `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, the file stem | `mission_types/<t>.yaml` `id` |
| `title` | yes | string | `display_name` (S10) |
| `description` | yes | string | `mission.yaml` `description`; plan's runtime `mission.description` where `mission.yaml` has none (S11) |
| `content` | no | string: standing context for every step's agent | `mission.yaml` `agent_context` (S12) |
| `requires_substantive_spec` | no | `true` only; absent means not required, never "use a default" | `mission_types/<t>.yaml` (S6); the pinned `false` and absent values match core's built-in defaults |
| `path_conventions` | no | closed object, at least one of `workspace`, `tests`, `deliverables`, `documentation`, `data`: project-workspace directories, not pack files | `mission.yaml` `paths` |
| `expected_output_artifacts` | no | output entries (below), at least one: the mission's own postconditions | `expected-artifacts.yaml` `optional_always` |
| `references` | no | references (below), at least one | `governance-profile.yaml` `selected_*` |
| `steps` | yes | step sub-items (below), at least one, ordered | the runtime `steps` and `step.yaml` |
| `on_demand_steps` | no | step sub-items, at least one | `step.yaml` with no runtime entry (decision 2) |

A **step** sub-item:

| Field | Required | Shape | From |
|---|---|---|---|
| `id` | yes | `^[a-z][a-z0-9]*(-[a-z0-9]+)*$` | runtime `steps[].id`, else the step directory |
| `title` | yes | string | runtime `title`, else `step.yaml` `display_name` (S10) |
| `description` | no | string, 1 to 160 characters: the one-line human summary | the runtime `description`, else the prompt's frontmatter `description` (S11; ruling Q1) |
| `role` | no | role token (`^[a-z][a-z0-9-]*$`) | the runtime `agent-profile` or `step.yaml` `agent_profile`, else the action index's single `agent_profiles` entry, as the profile's primary role (S15, ADR-0032) |
| `prompt_path` | no | derived: `steps/<id>.prompt.md` | `prompt_template`, `command-templates/<id>.md` (S13) |
| `guidelines_path` | no | derived: `steps/<id>.guidelines.md` | the implicit `guidelines.md` (S13) |
| `expected_input_artifacts` | no | input entries, at least one: the step's preconditions | none pinned: no pack declares a precondition yet |
| `expected_output_artifacts` | no | output entries, at least one: the step's postconditions | every `required_by_step.<id>` entry (each is required to leave the step) |
| `references` | no | references, at least one | `actions/<id>/index.yaml` |

A step's `description` is bounded at **160 characters** (Unicode code points; the schema's `maxLength` and a domain
twin), the refined ruling Q1. It is a one-line summary for an overview, a list or a status line; a longer text belongs
in the prompt's detailed description. The bound is measured against the pinned data: the 44 step descriptions the
migration carries run from 19 to 119 characters (median about 72; the longest is documentation's `discover`), and the
28 prompt frontmatter descriptions run from 29 to 111. 160 leaves a third again above the longest pinned text while
still refusing a paragraph. No pinned description is over the bound, so the migration shortens none.

The role falls back to the action index's `agent_profiles` only where neither the runtime nor `step.yaml` names a
profile. That is the two `retrospect` steps (documentation, research), whose index names
`retrospective-facilitator` and whose `step.yaml` has `agent_profile: null`; they get its role, `facilitator`.
software-dev's `retrospect` index also names it, but it belongs to no step and is not carried. The plan action indexes
list no profile. An index naming more than one profile is refused: a step has one role. Where the runtime and
`step.yaml` both name a profile, they are checked equal.

A step's expected inputs and outputs close it (ruling Q2): its inputs are the preconditions, the step does its work,
its outputs are the postconditions. The type's own outputs are the mission's postconditions, not tied to a step. The
fields are spelled "artifact", the spelling of the glossary and the code.

An **input** entry is `{artifact_key, path_pattern, blocking?}`; an **output** entry is
`{artifact_key, artifact_class, path_pattern, blocking?, template_path?}`:

- `artifact_key` keeps the pinned key verbatim, an opaque dotted key
  (`^[a-z][a-z0-9-]*(\.[a-z0-9_-]+)+$`; `_` is admitted for `per_wp`). It is not an identity: the same key may stand
  at two steps (software-dev's `output.tasks.per_wp`), and its segments carry no meaning the other fields depend on.
  Keys are unique within one list. `artifact_` prefixes the family (S3).
- `artifact_class`, on an output only, is one of `input`, `output`, `evidence`, `workflow`, `runtime`: what the
  artifact is to the mission. `input` is the mission's input document: every pinned `input` entry is `spec.md`,
  required at the step that produces it, so it is a postcondition of that step. An input entry has no class. Core's
  `policy` is used by no pack and is left out.
- `path_pattern` is a glob in the mission's workspace, not a pack file (S13 does not apply): relative, no `..`.
- `blocking` is `true` only, absent meaning not blocking (S6). The type's outputs, the pinned `optional_always`, never
  block (`required_always` is empty in every pack).
- `template_path`, on an output only, names the template that produces it, relative to the mission-type file
  (`templates/spec-template.md`). It replaces `step.yaml`'s `template`: the template's own `artifact_key` (`spec`,
  `plan`) was a second key vocabulary for the same artifact (S3), and the glossary's "step template" is the prompt.
  The load refuses a `template_path` that leaves the pack or names no file; the template itself stays an
  unmodelled pack file. qa `implement`'s defect-log template
  becomes an output of that step.

A **reference** is `{type, id, when?, reason?}` (S14). `type` is a kernel artifact-kind token, restricted to the
guidance kinds `directive`, `tactic`, `paradigm`, `styleguide`, `toolguide`, `procedure` (a mission type names roles,
not profiles; ADR-0032). `id` is the artifact's id (a directive stem such as `042-common-docs` becomes
`DIRECTIVE_042`). References are kept verbatim and in authored order and mint no relationship until #189 rules, as for
procedures (ADR-0043 decision 2). The type-level references are the glossary's recipe ingredients; until #189 their
activation meaning is carried by the graph shards' `scope` edges, which equal them. Step-level references are the
step's action-scoped guidance.

**Derived fields (S8).** `prompt_path` is `steps/<id>.prompt.md` and `guidelines_path` is `steps/<id>.guidelines.md`:
whether a step has the field is authored, its value is derived from the step id. The writer writes the derived value,
the load refuses any other value naming the type, the step and the path, and the gate verifies it rather than
comparing it. The fields stay because a companion is named by a field (ADR-0039 decision 1).

Dropped, each for a stated reason (the field-by-field table is in the mission's research,
`research/squad/divergence.md` §4):

- **Repeats (S5):** `mission.key`, `id`/`mission_type` in profiles and manifests, the action name, `step.yaml`'s
  `id`, the plan runtime's nested step copy, and its `runtime` block's `loop_type` (the order is sequential) and
  `terminal_step` (the last step). Each is checked equal to the value kept before it is dropped.
- **Runtime mechanics with no place (S6):** the expected-artifacts `manifest_version`, and the plan `runtime` block's
  `step_transition` and `prompt_template_dir` (the prompts are the steps' companions).
- **Derived from the order (S5, decision 4):** `depends_on`, `sequence_index`, `in_action_sequence`.
- **Conflicting values (S6):** `mission.yaml` `name` and `version`, the runtime `mission.name`, `title` and `version`
  (the pack's own version governs content).
- **Replaced by the runtime title (S6):** a `step.yaml` `display_name` that differs from the runtime `title` (17 steps,
  listed in spec-kitty/spec-kitty-open-packs#96).
- **Always empty or null (S5, S16):** `guidance`, `delegates_to`, `selected_mission_step_contracts`,
  `available_tools`, `activations`, `required_always`, the `done` step.
- **Not the type's to say:** `selected_agent_profiles` and every profile list (a mission type names roles, not
  profiles: ADR-0032 decision 1; activation is the preset's), `template_set`, `step_type` (always `agent`; a human step
  says `role: operator`).
- **Names of core-coded behavior no pack can define:** `validation` (its `checks` and `custom_validators`),
  `mcp_tools`, `task_metadata`, `commands`, `workflow.phases` (a stale display list that names no step), `domain`,
  qa's retired state-machine block (`mission`, `initial`, `states`, `transitions`), and `mission.yaml` `artifacts`
  (bare names and directories with no class or key, duplicating the expected-artifact manifest).

```yaml
schema_version: "1.0"
id: research
title: Research
description: Conduct systematic research with structured methodology and evidence synthesis
steps:
  - id: scoping
    title: Research Scoping
    role: researcher
    prompt_path: steps/scoping.prompt.md
    guidelines_path: steps/scoping.guidelines.md
    expected_output_artifacts:
      - artifact_key: input.spec.research
        artifact_class: input
        path_pattern: spec.md
        blocking: true
        template_path: templates/research-spec-template.md
    references:
      - type: directive
        id: DIRECTIVE_010
      - type: tactic
        id: requirements-validation-workflow
  - id: methodology
    title: Methodology
    role: researcher
```

### 4. One sequence (D3)

`steps` is the one order. The runtime's planner order wins over `step.yaml`'s sequence index, because it is the order
the runtime walks. The pinned `depends_on` lists are dropped: every runtime chain is linear, so the order holds all of
their meaning. A non-linear order is not expressible; when one is needed, a later decision adds it. The software-dev
runtime's `discovery` step and the `research` step directory it reaches through its prompt become one step,
`discovery`, with that prompt.

### 5. Layout and companions (D4, D1a)

```text
<pack>/missions/<type>/<type>.mission-type.yaml
<pack>/missions/<type>/steps/<step>.prompt.md        named by prompt_path
<pack>/missions/<type>/steps/<step>.guidelines.md    named by guidelines_path
<pack>/missions/<type>/templates/...                  template payloads, named by template_path
```

A mission type's templates are templates (the deferral [ADR-0040](/adr/0040-content-form-and-payload-kinds/) left
to #194): the payload rule `*/missions/*/templates/**/*` holds them as `template` payloads, byte for byte, each with
the id `<type>/<path>` that the CRG's `template:` URNs already use, and written back to its mission type's
`templates/`. Their orientation READMEs and hidden files are excluded.

A mission type's place is fixed: the `files` rule `**/*.mission-type.yaml` classifies a mission-type file anywhere,
and the load and validate mode refuse one whose pack-relative path is not `missions/<id>/<id>.mission-type.yaml` (S1
extended from the stem to the path). A new mission type is created at that path.

The prompt and the guidelines are companions
([ADR-0039](/adr/0039-a-kind-binding-can-carry-companion-files/), as amended by ADR-0043) of the `markdown` content
form ([ADR-0040](/adr/0040-content-form-and-payload-kinds/)), each with its own `files` rule and structure schema
(`mission-step-prompt`, `mission-step-guidelines`). A prompt may hold frontmatter, and its frontmatter holds only
`description`: the prompt's detailed description, longer than the step's one-line description, stored and written back
(the refined ruling Q1). Guidelines hold no frontmatter. Where the runtime gives a step no description, the prompt's
frontmatter `description` is also the step's description; the four on-demand software-dev steps this applies to are
listed in spec-kitty/spec-kitty-open-packs#96. A pinned step with no prompt, or an empty one, has no
`prompt_path`. "Guidelines" here are a step's working notes, not the glossary's charter Guideline.

### 6. Retired files

`mission_types/<t>.yaml`, `mission.yaml`, `mission-runtime.yaml`, `governance-profile.yaml`,
`expected-artifacts.yaml`, `actions/<a>/index.yaml`, `mission-steps/**/step.yaml`, the `prompt.md` and
`guidelines.md` beside a retired `step.yaml`, and `command-templates/*.md` are retired. Their `file-kinds.yaml`
exclusions are removed (operator decision 5 of 2026-10-28) and replaced by `retired` rules, each with its reason.
Each rule is anchored to a layout the pinned packs held (the mission tier at the pack root, as qa and cave-kitty had
it, or under `missions/`, as the built-in pack had it), so a payload that only shares a name, such as an asset called
`governance-profile.yaml` or a template under `templates/mission-steps/`, is never retired. A
retired file is classified as retired: the store's load and validate mode refuse it by name, the whole-pack gate
reports it in its own `retired:` bucket, and it is digested in the fingerprint tail, so removing one changes the
fingerprint. A stale file cannot hide. The `retired` rules are removed when the service is incorporated into core and
no supported pack holds the old layout.

### 7. The domain lives in a missions module (D6)

`MissionType`, `MissionStep` and their values live in a new Gradle module `:missions`
(`ai.speckitty.charter.missions.domain`), plain Java over the kernel, as `:profiles` holds the agent profile
([ADR-0035](/adr/0035-charter-artifact-interface-lives-in-the-kernel/) decision 4). The domain holds no file paths
(ADR-0037 decision 5): a step holds its prompt and guidelines as documents. One value is kept as written: an output's
`template_path`, `templates/<path>` relative to the mission-type file, is a reference to a template the pack holds
(the `Template` with the id `<type>/<path>`), checked to name one on read and on write, and not resolved to that
entity in the domain; resolving it, and deciding which of a template's id and its mission type owns its place, is a
recorded follow-up. The pack store binds the kind and depends on the module, never the other way; the ArchUnit slice
rules cover it.

### 8. Migration by the service's own writer, then a re-pin

As ADR-0043 decision 6: a throwaway harness reads each pinned mission tier, maps it by the tables above, writes it
with the service's writer, and is deleted before merge. The result lands in `spec-kitty-open-packs` (spec-kitty/spec-kitty-open-packs#95) with an issue per
required change (spec-kitty/spec-kitty-open-packs#96 to #100), and `openPacksRef` moves to it. The migration also rewrites the qa and cave-kitty CRG fragments'
`body_path`, which name a retired `governance-profile.yaml`, to the mission-type file. Divergences the mapping cannot
settle are open-packs issues: the qa steps' missing `prompt.md` (their prompts are the command templates), the empty
retrospect prompts, software-dev's `retrospect` action index with no step, qa's `selected_agent_profiles` (a preset
activates profiles; qa has none), the step descriptions copied from prompt frontmatter and the dropped step titles,
and the qa implement defect-log, which becomes an output no pinned list named. The retired files' YAML comments are
not carried; spec-kitty/spec-kitty-open-packs#95 names the qa rationale they held.

## Consequences

### Positive

- One file says what a mission type is; its steps, their order, roles, prompts, artifacts and guidance have one home
  each, and the contradictions between the old families can no longer be authored.
- The mission tier joins the whole-pack round trip, so its content is read into the domain and written back with equal
  meaning.
- Mission types become an artifact kind, the shape #148 asks for, without a fragment plural for steps.

### Negative

- Core reads the retired files today. It moves to this shape when the service is incorporated; until then the pinned
  packs no longer serve core's mission tier (epic #186: the rewrite is designed without regard to core).
- A non-linear step order cannot be expressed.
- The `action` node and the `mission_step` domain type name the same thing until #189 rules.
- Pack fingerprints and ETags change once for packs with mission content.
- `PUT /packs/{packId}/relationships` with a `mission_type:` source now resolves where it was refused 409: an additive
  behavior change with no schema or operation change, recorded in the contract CHANGELOG.

### Risks

- A sibling change to the shared vocabulary files (`file-kinds.yaml`, the gate's ledgers) conflicts textually; the
  later change rebases.
- `ArtifactKind`'s facts mirror core, and core has no mission-type artifact kind. A divergence entry records it.

## Alternatives considered

- **One file per step** (`steps/<step>.mission-step.yaml`, with the type listing step ids). Rejected by the operator's
  ruling: the multiplicity is the type's, and the DSO carries steps as sub-items.
- **A `mission_steps` kind and fragment plural.** Rejected: there is no plural kind (D1), and R14 keeps the name
  refused.
- **Off-sequence steps flagged inside `steps`** (`in_sequence: false`). Rejected: it mixes order and membership.
- **Dropping the off-sequence steps.** Rejected: core still invokes them, and their prompts would be lost.
- **Keeping `mission.yaml` as an excluded runtime file.** Rejected (D2): its description, paths and agent context
  belong to the type.
- **The domain in `packs.domain`.** Rejected (D6): the mission type is its own concept, with its own module.
- **Round-tripping the pinned files as-is.** Rejected (operator, 2026-10-28; ADR-0036 decision 5).

## References

- Operator rulings: #194 comments of 2026-10-28 and 2026-10-29 (D1 to D6, D1a); rulings of 2026-10-30 (Q1, its
  refinement, and Q2), relayed by the orchestrator and held in the mission's `decisions/`.
- Research: `kitty-specs/mission-type-consolidation-01M65MCK/research.md` and `research/squad/`.
- [ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/) S1 to S8,
  [ADR-0043](/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides/) S9 to S16 and decision 6.
- [#148](https://github.com/spec-kitty/spec-kitty-charter/issues/148), [#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189),
  DIV-011 in `docs/conformance/divergence-register.md`.
