---
title: Descriptors
description: Every charter descriptor file — pack, manifest, graph fragment, org charter, preset and each artifact kind — with its purpose, key fields, file naming, location and DRG links.
doc_status: draft
updated: 2026-10-14
type: Architecture
kind: Reference
related:
  - architecture/logical-model
  - architecture/descriptor-schemas
  - architecture/resolution-model
audience:
  - profile: pack-author
    guidance_text: Read this to find the file, fields and naming of each descriptor you author.
---

# Descriptors

A **descriptor** is a YAML file that declares one charter component. This page lists each
descriptor kind. Core validates them with the YAML schemas in its `src/charter/offering/schemas/` and the Pydantic models in its `src/charter/offering/` (core is [`spec-kitty/spec-kitty`](https://github.com/spec-kitty/spec-kitty)); when the charter contract lands here, they become part of it. The concepts behind them are in [Logical model](/architecture/logical-model/);
entity diagrams of the schemas are in [Descriptor schemas](/architecture/descriptor-schemas/).
"Required" lists the fields core's schema requires; "key fields" adds the ones most
descriptors carry.

> **Status:** exists in core; this repository holds no descriptor schemas yet. [What the labels mean](/#status).

## Where descriptors live

| Pack level | Root | Per-kind directory | Graph |
|---|---|---|---|
| `built-in` | `packs/built-in/` | `<plural>/` (for example `directives/`, `agent_profiles/`) | one `<kind>.graph.yaml` shard per node kind that has built-in nodes, at the pack root |
| `org` | the configured pack path | `<plural>/` | one `drg/fragment.yaml` |
| `project` | `.kittify/charter-packs/` | `directive/`, `tactic/`, `styleguide/`, `procedure/` (singular); every other kind plural | project fragment |

Where each level is configured: `built-in` ships with Spec Kitty (source home moving to
`spec-kitty-open-packs`, core ADR `2026-08-16-2`) and uses sharded graph files plus a generated
`pack-manifest.yaml`; `org` packs are listed under `charter_packs.org.packs` in
`.kittify/config.yaml` (core's `packs/internal/` is an org pack that never ships to
consumers); the `project` pack sits under `.kittify/charter-packs/`.

The singular project directories for four kinds are a deliberate asymmetry, declared once in
core's `PROJECT_KIND_DIRS`. Built-in step contracts live under
`packs/built-in/missions/built_in_step_contracts/`, and `mission_step_contract`, `template`
and `anti_pattern` have no built-in content directory of their own. "Content directory" means
the directory of a kind's layered repository; for example `packs/built-in/templates/` exists
but is not a content directory in this sense. Per-kind facts are in the
[Artifact kinds table](/architecture/logical-model/#artifact-kinds).

## Pack-level descriptors

### `pack.yaml` — pack descriptor

Authored, never machine-rewritten. Identifies the pack and declares its level.

| Field | Meaning |
|---|---|
| `pack_id` | ULID, the pack's stable identity |
| `pack_version` | semantic version of the pack content (built-in) |
| `name` | human handle |
| `parent_pack` | parent pack, or `null` for a root pack |
| `pack_level` | `built-in`, `org` or `project` (core ADR `2026-10-11-2`); a mismatch with where the pack is configured refuses with `PACK_LEVEL_MISMATCH`, a missing value with `PACK_LEVEL_MISSING` |
| `active_charter` | org packs: path to the org charter, usually `org-charter.yaml` |
| `pack_drg` | org packs: path to the graph fragment, usually `drg/fragment.yaml` |

DRG: none directly; it scopes the nodes the pack contributes.

### `pack-manifest.yaml` — generated pack manifest

Machine-written beside `pack.yaml` and never hand-edited. Lists the pack's `constituents`,
each with `id`, `kind`, `path`, `content_hash` and `provenance_path`. Core regenerates it with
`spec-kitty charter pack regenerate-graph`.

### `<kind>.graph.yaml` — built-in graph shard

One shard per node kind that has built-in nodes, at the built-in pack root (`directive.graph.yaml`,
`action.graph.yaml`, `mission_type.graph.yaml`, …).

| Field | Meaning |
|---|---|
| `schema_version` | `"1.0"` |
| `generated_at`, `generated_by` | provenance of the shard |
| `nodes[]` | `urn` (`<kind>:<id>`), `kind` (a `NodeKind`), `label` |
| `edges[]` | `source`, `target`, `relation`, optional `when`, `reason` |

Unknown keys refuse the document (`DRGGraphSchemaError`).

### `drg/fragment.yaml` — org graph fragment

The single graph file of an org pack.

| Field | Meaning |
|---|---|
| `pack_name`, `source_kind`, `source_ref` | where the pack comes from (for example `local_path`) |
| `layer_index`, `provenance_marker` | the layer this fragment merges into (`org`) |
| `nodes[]` | `id`, `kind` (plural form, for example `directives`), `title`, `body_path` to the descriptor file |
| `edges[]` | as in the built-in shards; endpoints may be URNs or bare ids declared in `nodes` |

Edges in a fragment land in the same merged graph cascade reads.

### `org-charter.yaml` — org charter policy

What an org pack enforces on every project that registers it.

| Field | Meaning |
|---|---|
| `org_name` | the organization |
| `skill_namespace` | prefix for rendered skills; lowercase ASCII, starts with a letter, at most 32 characters; `spk-`, `spec-kitty-` and `spec-kitty.` are reserved |
| `required_<plural>` | per org-requirable kind, for example `required_directives` and `required_skills`; semantics in [Artifact kinds](/architecture/logical-model/#artifact-kinds) |

### `presets/<name>.yaml` — activation preset

A named set of activations a pack ships. Schema: `activation-preset.schema.yaml`.

| Field | Meaning |
|---|---|
| `name`, `description` | required |
| `activated_<plural>` | per-kind allowlist; absent-key behavior is in [Artifact kinds](/architecture/logical-model/#artifact-kinds) |
| `activated_kinds` | optional kind gate |
| `mission_type_activations` | mission types the preset activates |

The built-in pack ships `default` (no per-kind lists) and `minimal` (curated directives and
tactics for `software-dev`).

## Artifact descriptors

Each artifact descriptor is one file named `<name>.<suffix>`, placed in the kind's directory.
Its `id` becomes the node URN `<kind>:<id>`; its relations to other artifacts live in the
graph (shards or fragment), not in the descriptor, except where noted.

### Directive — `*.directive.yaml`

A binding rule. Schema: `directive.schema.yaml`.

- **Required:** `id`, `schema_version`, `title`, `intent`, `enforcement`.
- **Key fields:** `scope`, `procedures`, `integrity_rules`, `validation_criteria`,
  `explicit_allowances`, `references`.
- **Naming:** `NNN-<slug>.directive.yaml` for numbered built-ins (id `DIRECTIVE_NNN`); ids
  match `^[A-Z][A-Z0-9_-]*$`.
- **DRG:** `directive:<ID>`; typically `requires` or `suggests` tactics.

### Tactic — `*.tactic.yaml`

A reusable technique as ordered steps. Schema: `tactic.schema.yaml`.

- **Required:** `id`, `schema_version`, `name`, `steps`.
- **Key fields:** `purpose`, `failure_modes`, `applies_to_languages`, `references`, `notes`.
- **Naming:** `<kebab-id>.tactic.yaml`; built-ins may sit in topic subdirectories.
- **DRG:** `tactic:<id>`; target of directive and action edges.

### Styleguide — `*.styleguide.yaml`

Conventions for a kind of work. Schema: `styleguide.schema.yaml`.

- **Required:** `id`, `schema_version`, `title`, `scope`, `principles`.
- **Key fields:** `patterns`, `anti_patterns`, `tooling`, `quality_test`,
  `applies_to_languages`, `references`, `structural_lint_config`.
- **DRG:** `styleguide:<id>`.

### Toolguide — `*.toolguide.yaml`

How to use one tool. Schema: `toolguide.schema.yaml`.

- **Required:** `id`, `schema_version`, `tool`, `title`, `guide_path`, `summary`.
- **Key fields:** `commands`, `applies_to_languages`, `last_updated`, `references`.
- `guide_path` points at a Markdown guide beside the descriptor.
- **DRG:** `toolguide:<id>`.

### Paradigm — `*.paradigm.yaml`

A way of thinking about the work. Schema: `paradigm.schema.yaml`.

- **Required:** `schema_version`, `id`, `name`, `summary`.
- **Key fields:** `directive_refs`, `references`.
- **DRG:** `paradigm:<id>`; some built-in paradigm nodes are re-kinded as `anti_pattern`.

### Procedure — `*.procedure.yaml`

A multi-step operating procedure with entry and exit conditions. Schema:
`procedure.schema.yaml`.

- **Required:** `schema_version`, `id`, `name`, `purpose`, `entry_condition`,
  `exit_condition`, `steps`.
- **Key fields:** `anti_patterns`, `applies_to_languages`, `notes`, `references`.
- **DRG:** `procedure:<id>`.

### Agent profile — `*.agent.yaml`

An agent's identity and specialization. Schema: `agent-profile.schema.yaml`. Field names are
kebab-case.

- **Required:** `profile-id`, `name`, `purpose`, `specialization`.
- **Key fields:** `description`, `schema-version`, `roles`, `capabilities`,
  `routing-priority`, `max-concurrent-tasks`, `collaboration`, `mode-defaults`,
  `initialization-declaration`, `specialization-context`, `directive-references`,
  `tactic-references`, `toolguide-references`, `styleguide-references`,
  `self-review-protocol`, `model`, `effort`.
- **DRG:** `agent_profile:<id>`. Lineage is a `specializes_from` edge in the graph, never a
  profile field; `delegates_to` edges declare runtime handoff.

### Mission step contract — `*.step-contract.yaml`

The steps of one action of one mission type.

- **Fields:** `schema_version`, `id`, `action`, `mission`, `steps[]`. Each step has `id`,
  `description` and either a `command` or a `delegates_to` block (`kind` plus `candidates`).
- **DRG:** `mission_step_contract:<id>`, tied to `action:<mission>/<action>`.

### Glossary pack — `*.glossary-pack.yaml`

Terms a pack contributes.

- **Fields:** `id`, `description`, `provenance`, `terms[]` (each with `surface`,
  `definition`, `status`, `confidence`).
- **DRG:** `glossary_pack:<id>`.

### Pack skill — `*.skill.yaml` plus body

A shared, parameterized entry point (core ADR `2026-09-27-1`). Schema: `skill.schema.yaml`.

- **Required:** `schema_version` (`"1.0"`), `id`, `title`, `description`, `form`.
- **Key fields:** `triggers`, `body_path` (prompt form, for example `<id>.skill.md`),
  `expands_to` (`target` of `builtin:…` or `cli:spec-kitty …`), `parameters[]`,
  `invocation`, `tools`, `version`, `maintainers`, `enhances`, `overrides`.
- **Naming:** id is lowercase ASCII, `[a-z0-9]` segments joined by single `-`, at most 64
  characters. Rendered as `<skill_namespace>-<id>/SKILL.md`.
- **DRG:** `skill:<id>`.

### Asset — `*.asset.yaml` sidecar

Describes one blob. Model: `AssetManifest` (frozen, extra keys forbidden).

- **Fields:** `id`, `mime`, `path` (relative to the pack's `assets/` directory, contained),
  optional `title`.
- **Naming:** `<blob-file-name>.asset.yaml` beside the blob.
- **DRG:** `asset:<id>`.

### Mission type — `missions/mission_types/<id>.yaml`

Declares a mission type (`schema_version`, `id`, `display_name`, and its workflow). Its
actions and governance are graph data: `mission_type:<id>` `requires` its actions and
`scope`s type-wide governance. `mission-type` is an operator token, not an `ArtifactKind`.

### Anti-pattern and template

- **Anti-pattern** has no descriptor file. It is a node re-kinded as `anti_pattern` inside
  another kind's graph shard, the target of `rejects` edges.
- **Template** has no descriptor pattern. Templates are mission-tier content resolved
  through the template chain and linked with `instantiates` edges.

## Project-side configuration

These are not pack descriptors but are inputs to resolution:

| File | Holds |
|---|---|
| `.kittify/config.yaml` | `charter_packs.org.packs` (registered org packs), `charter_packs.project.skill_namespace`, and the active charter keys for an unmigrated project |
| `.kittify/charter/charter.yaml` | the charter bundle, including the active charter for a migrated project |
| `.kittify/charter/charter.md` | display companion of the charter |
| `.kittify/skills-manifest.json` | ownership of rendered pack skills |
