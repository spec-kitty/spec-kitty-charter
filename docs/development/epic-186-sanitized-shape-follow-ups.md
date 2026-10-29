---
okf_version: "0.2"
title: Epic 186 sanitized-shape follow-ups
description: The merged pack schemas and storage objects that break the sanitized-shape rules of ADR-0042 and ADR-0043, each with its file, the issue that owns it and the proposed change.
doc_status: active
updated: 2026-10-29T00:00:00Z
type: Guide
kind: Reference
related:
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
items are for epic [#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186); none is
changed by #194.

Each change is made the same way: the schema here first, then the pack content in
`spec-kitty-open-packs` with the service's own writer, then a re-pin (ADR-0043 decision 6). Schemas
are under `contracts/packs/schemas/`. "To file" means no issue exists yet; the item is filed
under epic #186 when it is picked up.

## The merged-schema violations

| # | File | What breaks a rule | Rule | Issue | Proposed change |
|---|---|---|---|---|---|
| 1 | `agent-profile.schema.json` | kebab-case keys (`profile-id`, `schema-version`, `directive-references`) mixed with snake_case `applies_to_languages`; `AgentProfileDso` special-cases its id key | S1, S9 | #186, to file | snake_case throughout; `id` and `schema_version` |
| 2 | `agent-profile.schema.json`, `AgentProfileDso` | both `purpose` and `description` on one artifact | S6, S11 | #186, to file | one summary, `description` |
| 3 | `agent-profile`, `directive`, `tactic`, `paradigm`, `styleguide`, `toolguide` schemas | five reference shapes: the tactic's requires a stray `name` and `when`; styleguide and toolguide use plain strings; the agent profile has per-kind lists; the paradigm has `directive_refs` beside `references`; the directive has a bare `procedures` list | S14 | [#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189), then #186 | one `Reference` `{type, id, when?, reason?}`, once #189 rules on edge authority |
| 4 | `tactic`, `paradigm`, `agent-profile` schemas; graph shard and org fragment nodes | the label is `name` (tactic, paradigm, agent profile), `title` (directive, styleguide, toolguide, skill, asset), `label` (graph node) or `title` (fragment node) | S3, S10 | #186, to file | `title` |
| 5 | `directive`, `paradigm`, `toolguide`, `tactic` schemas | the summary is `intent` (directive), `summary` (paradigm, toolguide) or `purpose` (tactic) | S3, S11 | #186, to file | `description` |
| 6 | `org-charter`, `charter`, `agent-profile`, `asset`, `glossary-pack`, `mission-step-contract` schemas | `schema_version` is an integer (org charter), semver (charter), optional (agent profile), absent (asset, glossary pack) or has no pattern (step contract) | S2 | #186, to file | required string `"1.0"` |
| 7 | `directive`, `asset`, `glossary-pack`, `mission-step-contract` schemas | id patterns: UPPER_SNAKE for directives; none for the other three | S1 | #186, to file | one kebab slug equal to the file stem; directive ids are a known coupling, recorded before they change |
| 8 | `styleguide.schema.json` `AntiPattern` | `{name, description, bad_example, good_example}`, all required, beside the procedure's `AntiPatternEntry` | S10, S12 | #186, to file | `AntiPatternEntry`: `title` required, `content`, `bad_example` and `good_example` optional |
| 9 | `mission-step-contract.schema.json` | ids are ad hoc (`specify` for software-dev, `plan-specify` for others) while `mission` and `action` already hold the key | S1, S5 | #186, to file | derive the id as `<mission>/<action>`, or drop the repeated fields |
| 10 | `charter.schema.json` | `mission_type_activations` where every other list is `activated_<plural>`; `org-charter.schema.json` has `required_*` for twelve kinds and none for mission types | S3 | #186; [#206](https://github.com/spec-kitty/spec-kitty-charter/issues/206) for the `required_*` lists | `activated_mission_types`; the org lists move to presets under #206 |
| 11 | `org-fragment.schema.json` | the edge lacks the `when` the graph shard's edge has; nodes are `id` and `title` where shard nodes are `urn` and `label` | S3, S6 | #186, to file | one node and edge vocabulary for shards and fragments |
| 12 | `pack-descriptor.schema.json` | `additionalProperties: true` | S4 | done by #195 ([ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/) decision 4) | none left |
| 13 | core's mission step (`prompt_template`, `template_file`) | pointers not named `<role>_path` | S13 | the mission-type kind, when it is built | `prompt_path`, and an `output` `path`; `guidelines.md` gets a `guidelines_path` |
| 14 | open-packs `missions/built_in_step_contracts/__init__.py` | a Python package marker in a content pack, which no file-kind rule classifies | S5 | open-packs, to file | remove it from the pack |

## Renames to `content`

The operator ruled on 2026-10-28 that a field carrying a body is `content` (S12). The shapes #194
designed use it already: the guide's freeform sections and the procedure's anti-pattern entries.
The merged kinds the ruling names follow:

| File | Field today | Issue | Proposed change |
|---|---|---|---|
| `styleguide.schema.json` `AntiPattern` | `description` holds the body | #186, to file | rename to `content` (with item 8 above) |
| `asset.schema.json` | `path` names the payload, anchored at the pack's `assets/` rather than at the sidecar ([open-packs#75](https://github.com/spec-kitty/spec-kitty-open-packs/issues/75) for the anchoring) | #186, to file | name the body file with a `<role>_path` field, relative to the sidecar (S13) |
| templates (`*/templates/**`, [ADR-0040](/adr/0040-content-form-and-payload-kinds/)) | none: a template is its own file, held as bytes | #186, to file | none while a template has no sidecar; a sidecar, if one comes, names its body with a `<role>_path` field |

## Residuals of #194 itself

The toolguide YAML kept its merged shape apart from `guide_path`:

| File | What breaks a rule | Rule | Issue | Proposed change |
|---|---|---|---|---|
| `toolguide.schema.json` | `summary` | S11 | #186, to file | `description` |
| `toolguide.schema.json` | `references` are plain strings | S14 | #189, then #186 | the shared `Reference` shape |
| `toolguide.schema.json` | `last_updated` is a string or `null` | S5, S7 | #186, to file | drop it (provenance lives in the pin), or make it a required date |
| `toolguide.schema.json` | `schema_version` is a pattern, not `const: "1.0"`; the id pattern admits a trailing `-` | S1, S2 | #186, to file | the procedure schema's forms |
