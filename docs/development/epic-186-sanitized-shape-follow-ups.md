---
okf_version: "0.2"
title: Epic 186 sanitized-shape follow-ups
description: The merged pack schemas and storage objects that break the sanitized-shape rules of ADR-0042 and ADR-0043, each with its file, the issue that owns it and the proposed change.
doc_status: active
updated: 2026-10-30T00:00:00Z
type: Guide
kind: Reference
related:
  - adr/0044-mission-types-consolidated-into-one-sanitized-shape
  - adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before picking up a shape change under epic 186, to see what is left and which rule it serves.
  - profile: pack-author
    guidance_text: Read this for the merged shapes that will change, so pack content can follow in one migration per kind.
---

# Epic 186 sanitized-shape follow-ups

[ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/) set the
sanitized-shape rules S1 to S8 for pack-level objects, and
[ADR-0043](/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides/) extended them to
artifact kinds with S9 to S16. Both changed only the objects they shaped. This list holds what was
already merged and breaks a rule. It comes from the #194 architect review (the mission's
`research/squad/architect.md`, section 4) and the operator's `content` ruling of 2026-10-28. The
items are for epic [#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186); #194
changes none of them but the toolguide id grammar and core's mission step pointers (item 13), both
recorded below as fixed. #194's second pull request applies the rules to the mission tier
([ADR-0044](/adr/0044-mission-types-consolidated-into-one-sanitized-shape/)); what it leaves is
under [Residuals of the mission types](#residuals-of-the-mission-types).

Each change is made the same way: the schema here first, then the pack content in
`spec-kitty-open-packs` with the service's own writer, then a re-pin (ADR-0043 decision 6). Schemas
are under `contracts/packs/schemas/`. The orchestrator posts this list on epic #186 when #194's
second pull request is opened; an item marked "#186" is tracked there and gets an issue of its own
when it is picked up. An item that is pack content in `spec-kitty-open-packs` has an open-packs
issue, filed with that pull request.

## The merged-schema violations

| # | File | What breaks a rule | Rule | Issue | Proposed change |
|---|---|---|---|---|---|
| 1 | `agent-profile.schema.json` | kebab-case keys (`profile-id`, `schema-version`, `directive-references`) mixed with snake_case `applies_to_languages`; `AgentProfileDso` special-cases its id key | S1, S9 | #186 | snake_case throughout; `id` and `schema_version` |
| 2 | `agent-profile.schema.json`, `AgentProfileDso` | both `purpose` and `description` on one artifact | S6, S11 | #186 | one summary, `description` |
| 3 | `agent-profile`, `directive`, `tactic`, `paradigm`, `styleguide`, `toolguide` schemas | five reference shapes: the tactic's requires a stray `name` and `when`; styleguide and toolguide use plain strings; the agent profile has per-kind lists; the paradigm has `directive_refs` beside `references`; the directive has a bare `procedures` list | S14 | [#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189), then #186 | one `Reference` `{type, id, when?, reason?}`, once #189 rules on edge authority |
| 4 | `tactic`, `paradigm`, `agent-profile` schemas; graph shard and org fragment nodes | the label is `name` (tactic, paradigm, agent profile), `title` (directive, styleguide, toolguide, skill, asset), `label` (graph node) or `title` (fragment node) | S3, S10 | #186 | `title` |
| 5 | `directive`, `paradigm`, `toolguide`, `tactic` schemas | the summary is `intent` (directive), `summary` (paradigm, toolguide) or `purpose` (tactic) | S3, S11 | #186 | `description` |
| 6 | `org-charter`, `charter`, `agent-profile`, `asset`, `glossary-pack`, `mission-step-contract` schemas | `schema_version` is an integer (org charter), semver (charter), optional (agent profile), absent (asset, glossary pack) or has no pattern (step contract) | S2 | #186 | required string `"1.0"` |
| 7 | `directive`, `asset`, `glossary-pack`, `mission-step-contract` schemas | id patterns: UPPER_SNAKE for directives; none for the other three | S1 | #186 | one kebab slug equal to the file stem; directive ids are a known coupling, recorded before they change |
| 7a | `tactic`, `paradigm`, `styleguide` schemas | the id pattern `^[a-z][a-z0-9-]*$` admits a trailing or doubled hyphen (`a-`, `a--b`), and no rule ties the id to the file stem | S1 | #186 | `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, the toolguide's and procedure's grammar, and the id equal to the file stem |
| 7b | `tactic`, `paradigm`, `styleguide`, `directive` schemas | `schema_version` is `pattern: ^1\.0$` rather than `const: "1.0"` | S2 | #186 | `const: "1.0"`, as the procedure schema has it |
| 8 | `styleguide.schema.json` `AntiPattern` | `{name, description, bad_example, good_example}`, all required, beside the procedure's `AntiPatternEntry` | S10, S12 | #186 | `AntiPatternEntry`: `title` required, `content`, `bad_example` and `good_example` optional |
| 9 | `mission-step-contract.schema.json` | ids are ad hoc (`specify` for software-dev, `plan-specify` for others) while `mission` and `action` already hold the key | S1, S5 | #186 | derive the id as `<mission>/<action>`, or drop the repeated fields |
| 10 | `charter.schema.json` | `mission_type_activations` where every other list is `activated_<plural>`; `org-charter.schema.json` has `required_*` for twelve kinds and none for mission types | S3 | #186; [#206](https://github.com/spec-kitty/spec-kitty-charter/issues/206) for the `required_*` lists | `activated_mission_types`; the org lists move to presets under #206 |
| 11 | `org-fragment.schema.json` | the edge lacks the `when` the graph shard's edge has; nodes are `id` and `title` where shard nodes are `urn` and `label` | S3, S6 | #186 | one node and edge vocabulary for shards and fragments |
| 12 | `pack-descriptor.schema.json` | `additionalProperties: true` | S4 | done by #195 ([ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/) decision 4) | none left |
| 13 | core's mission step (`prompt_template`, `template_file`) | pointers not named `<role>_path` | S13 | fixed by #194 ([ADR-0044](/adr/0044-mission-types-consolidated-into-one-sanitized-shape/)) | done: a step has `prompt_path` and `guidelines_path`, both derived from its id, and an expected output names its template with `template_path` |
| 14 | open-packs `packs/built-in/missions/built_in_step_contracts/__init__.py` and `packs/built-in/toolguides/system_tools/__init__.py` | empty Python package markers in a content pack, which no file-kind rule classifies | S5 | [open-packs#94](https://github.com/spec-kitty/spec-kitty-open-packs/issues/94) | remove it from the pack |

## Renames to `content`

The operator ruled on 2026-10-28 that a field carrying a body is `content` (S12). The shapes #194
designed use it already: the guide's freeform sections and the procedure's anti-pattern entries.
The merged kinds the ruling names follow:

| File | Field today | Issue | Proposed change |
|---|---|---|---|
| `styleguide.schema.json` `AntiPattern` | `description` holds the body | #186 | rename to `content` (with item 8 above) |
| `asset.schema.json` | `path` names the payload, anchored at the pack's `assets/` rather than at the sidecar ([open-packs#75](https://github.com/spec-kitty/spec-kitty-open-packs/issues/75) for the anchoring) | #186 | name the body file with a `<role>_path` field, relative to the sidecar (S13) |
| templates (`*/templates/**`, [ADR-0040](/adr/0040-content-form-and-payload-kinds/)) | none: a template is its own file, held as bytes | #186 | none while a template has no sidecar; a sidecar, if one comes, names its body with a `<role>_path` field |

## Residuals of #194 itself

The toolguide YAML kept its merged shape apart from `guide_path`:

| File | What breaks a rule | Rule | Issue | Proposed change |
|---|---|---|---|---|
| `toolguide.schema.json` | `summary` | S11 | #186 | `description` |
| `toolguide.schema.json` | `references` are plain strings | S14 | #189, then #186 | the shared `Reference` shape |
| `toolguide.schema.json` | `last_updated` is a string or `null` | S5, S7 | #186 | drop it (provenance lives in the pin), or make it a required date |
| `toolguide.schema.json` | `schema_version` is a pattern, not `const: "1.0"` | S2 | #186 | the procedure schema's form (with item 7b) |
| `toolguide.schema.json`, `ToolguideIdentifier` | the id pattern admitted a trailing or doubled hyphen (`a-`, `a--b`), whose guide file `<id>.guide.md` the `guide_path` pattern refuses: a registration was accepted, then the toolguide dropped out of the next load and could not be repaired through the API | S1 | fixed by #194 | done: the id is `^[a-z][a-z0-9]*(-[a-z0-9]+)*$` in the schema and `ToolguideIdentifier`, and the API refuses `a-` and `a--b` with 422 |
| toolguide store and validate mode | no rule ties a toolguide's `id` to its file stem, as A1 does for procedures | S1 | #186 | add the stem rule (`KindBinding.idIsFileStem`) for toolguides once the two qa files below are fixed; added now, it would refuse pinned content |
| open-packs `packs/qa/toolguides/qa-promise-trace-lint.toolguide.yaml` and `qa-result-ingest.toolguide.yaml` | the ids `qa-promise-trace-lint-guide` and `qa-result-ingest-guide` differ from their file stems, so each guide (`<id>.guide.md`) does not sit by the toolguide's stem | S1 | [open-packs#93](https://github.com/spec-kitty/spec-kitty-open-packs/issues/93) | rename the ids to the file stems (and the guides with them), or the files to the ids |

## Residuals of the mission types

The second pull request of #194 consolidated the mission tier into one mission-type file per type
([ADR-0044](/adr/0044-mission-types-consolidated-into-one-sanitized-shape/)). It left these as they
were:

| File | What breaks a rule or is left outside the model | Rule | Issue | Proposed change |
|---|---|---|---|---|
| mission templates, `missions/<type>/templates/**` | held as template payloads, byte for byte, with the id `<type>/<path>`; no sidecar describes one. The whole-pack gate counts them as templates and round-trips their bytes | none: outside the model | #186 | decide whether a mission template gets a modelled shape; while it has none, a step output's `template_path` is the only file reference to it |
| open-packs `packs/qa/missions/qa/templates/plan-field-declaration.yaml` | excluded with no schema; core has no model for it either | none: outside the model | #186 | model it with the qa mission type, or move it out of the pack |
| `mission-step-contract.schema.json` | `action` and `mission` name the step and its mission type in a third key vocabulary, beside the mission type's `id` and the step's `id` | S3, S5 | #186 | key the contract by the mission type's id and the step's id, with item 9 |
| the graph's `action:<type>/<step>` node and the `mission_step` domain type | two names for one step; a step's `references` mint no relationship | S3 | [#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189) | settle once #189 rules on edge authority |
