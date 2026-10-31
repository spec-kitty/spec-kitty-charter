# Data model: mission-type consolidation

Module `:missions`, package `ai.speckitty.charter.missions.domain` (`@NullMarked`). Plain Java over the kernel. All
collections are immutable; every invariant is guarded through a `ModularRuleset`, as for `Procedure`. The domain holds
no file paths (ADR-0037 decision 5).

## MissionType (aggregate root, `CharterArtifact`, kind `MISSION_TYPE`)

| Field | Type | Invariant |
|---|---|---|
| `id` | `MissionTypeIdentifier` | `^[a-z][a-z0-9]*(-[a-z0-9]+)*$` |
| `packId` | `PackIdentifier` | |
| `schemaVersion` | `String` | not blank |
| `title` | `String` | not blank |
| `description` | `String` | not blank |
| `content` | `Optional<String>` | not blank when present |
| `requiresSubstantiveSpec` | `boolean` | absent in the file means `false` |
| `pathConventions` | `Optional<PathConventions>` | |
| `expectedOutputArtifacts` | `List<ExpectedArtifact>` | the mission's postconditions; keys unique |
| `references` | `List<MissionReference>` | authored order |
| `steps` | `List<MissionStep>` | at least one; the sequence |
| `onDemandSteps` | `List<MissionStep>` | authored order kept; not a run order |

Cross-field: step ids unique across `steps` and `onDemandSteps`. `relationships()` is empty: references are kept,
never minted (#189).

## MissionStep (entity within the aggregate, identified by its id within the type)

| Field | Type | Invariant |
|---|---|---|
| `id` | `String` | `^[a-z][a-z0-9]*(-[a-z0-9]+)*$` |
| `title` | `String` | not blank |
| `description` | `Optional<String>` | not blank when present; at most 160 code points (`DESCRIPTION_MAX_LENGTH`, refined Q1) |
| `role` | `Optional<String>` | `^[a-z][a-z0-9-]*$` |
| `prompt` | `Optional<StepDocument>` | |
| `guidelines` | `Optional<StepDocument>` | |
| `expectedInputArtifacts` | `List<ExpectedInput>` | preconditions; keys unique |
| `expectedOutputArtifacts` | `List<ExpectedArtifact>` | postconditions; keys unique |
| `references` | `List<MissionReference>` | authored order |

## Values

- `StepDocument`: `Optional<String> title`, `Optional<String> description` (a prompt's detailed description, from
  its frontmatter; never blank), `String preamble`, `List<StepSection> sections`; `StepSection`
  `{String title, String content}`. The Markdown content rules are the binding's (the `markdown` form), not repeated
  here.
- `ExpectedArtifact`: `String artifactKey` (`^[a-z][a-z0-9-]*(\.[a-z0-9_-]+)+$`, opaque), `ArtifactClass
  artifactClass` (`INPUT` the mission's input document, `OUTPUT`, `EVIDENCE`, `WORKFLOW`, `RUNTIME`), `String pathPattern` (a workspace glob:
  relative, no `..`), `boolean blocking`, `Optional<String> templatePath` (relative to the mission-type file; checked
  to resolve by the binding).
- `ExpectedInput`: `String artifactKey`, `String pathPattern`, `boolean blocking`: a precondition, no class, no
  template.
- `MissionReference`: kernel `ArtifactKind type` (one of `DIRECTIVE`, `TACTIC`, `PARADIGM`, `STYLEGUIDE`,
  `TOOLGUIDE`, `PROCEDURE`), `String id`, `Optional<String> when`, `Optional<String> reason`.
- `PathConventions`: optional `workspace`, `tests`, `deliverables`, `documentation`, `data`; at least one present;
  workspace directories, not pack files.

## DSO (`:packs`, `MissionTypeDso`)

Mirrors the schema with snake_case keys. Per step: `prompt_path`/`guidelines_path` as presence flags with their derived
values (S8), and the companion documents held as `MarkdownDocument` until `toEntity`. The binding maps
`MarkdownDocument` (title, preamble, sections; a prompt's frontmatter `{description}` as the detailed description,
any other frontmatter refused) to `StepDocument` and back.
