---
work_package_id: WP01
title: Closed schemas for the preset and the pack manifest
dependencies: []
requirement_refs:
- FR-001
- FR-006
- C-003
planning_base_branch: feat/195-pack-level-round-trip
merge_target_branch: feat/195-pack-level-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/195-pack-level-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/195-pack-level-round-trip unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-level-files-round-trip-01M60H1A
base_commit: ee7677a1564e83d41ae0b8fdd3019ffeb9f96455
created_at: '2026-10-28T06:13:24.563785+00:00'
subtasks:
- T001
- T002
- T003
agent: claude
history:
- at: '2026-10-28T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/packs/schemas/preset
create_intent:
- contracts/packs/schemas/preset.schema.json
- contracts/packs/schemas/pack-manifest.schema.json
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackLevelSchemasTest.java
execution_mode: code_change
owned_files:
- contracts/packs/schemas/preset.schema.json
- contracts/packs/schemas/pack-manifest.schema.json
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackLevelSchemasTest.java
role: implementer
tags: []
task_type: implement
---
# Work Package Prompt: WP01 — Closed schemas for the preset and the pack manifest

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Write the two closed JSON Schemas of the sanitized pack-level shapes (spec FR-001, FR-006). The `file-kinds.yaml` rules
that put them to use come later (WP04, WP06), so this WP changes no classification and leaves every existing test as
it is.

## Subtask T001 — `preset.schema.json`

Draft 2020-12, `$id` `https://schemas.spec-kitty.ai/charter/packs/preset.schema.json`, `additionalProperties: false`
at every level, with a `description` in the style of the other schemas.

- Required: `schema_version` (`const: "1.0"`), `id` (pattern `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, `maxLength` 64) and
  `description` (string, `minLength` 1).
- `activated_kinds`: array, `minItems` 1, `uniqueItems`, items an enum of the 13 kind plurals: directives, tactics,
  styleguides, toolguides, paradigms, procedures, agent_profiles, mission_step_contracts, anti_patterns, templates,
  assets, glossary_packs, skills.
- `activated_{directives,tactics,styleguides,toolguides,paradigms,procedures,agent_profiles,mission_step_contracts,anti_patterns,mission_types}`:
  each an array of `$ref: #/$defs/item`, `minItems` 1.
- `$defs.item`: closed object. Required `id` (string, `minLength` 1). Optional `must_have` (`const: true`).

## Subtask T002 — `pack-manifest.schema.json`

The schema is closed. Required: `schema_version` (`const: "1.0"`), `manifest_hash` and `constituents`.

- `manifest_hash`: pattern `^[0-9a-f]{64}$`.
- `constituents`: array of closed `{kind, id, path, content_hash}`, all required.
  - `kind`: an enum of the 13 artifact kind tokens from `file-kinds.yaml` `artifact_kinds`.
  - `id`, `path`: string, `minLength` 1.
  - `content_hash`: the 64-hex pattern.
- `presets`: array, `minItems` 1, of closed `{id, path, content_hash}`.
  - `id`: the preset id pattern.
  - `path`: pattern `^presets/[a-z][a-z0-9]*(-[a-z0-9]+)*\.yaml$`.

## Subtask T003 — `PackLevelSchemasTest`

Build `new PackSchemas("packs/schemas/", List.of("preset", "pack-manifest"))`. Pair each refusal with a clean document,
and assert on the JSON pointer and keyword, not only on "has a violation".

- **Accepted:** a clean preset with every list and `must_have: true`.
- **Refused:**
  - `name:` → `additionalProperties`
  - `required_directives:`
  - `mission_type_activations:`
  - `must_have: false` → `const`
  - `activated_tactics: []` → `minItems`
  - an item with an extra key
  - `schema_version: 1.0` as a number
  - an `id` with a dot
- **Manifest:** clean is accepted. Refused: `charter:`, `source_type:`, `provenance_path` on a constituent,
  `presets: []`, a 63-hex hash, and an unknown `kind`.
- **The pinned files:** the pinned built-in presets and manifest (read from `../spec-kitty-open-packs`, or skipped as
  the other open-packs tests do) are refused, and the test names the first unknown key. This documents the
  divergence.

## Definition of Done

- Both schemas compile against the Draft 2020-12 metaschema, which `PackSchemas` checks.
- `PackLevelSchemasTest` is green, each refusal paired with a clean control.
- `./gradlew :packs:test` is green, and no other test changed.

## Branch Strategy

Planning base and merge target: `feat/195-pack-level-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP01 --agent claude --mission pack-level-files-round-trip-01M60H1A`.

Tests come first (charter: test first, no fake green). Each refusal is paired with a clean control on the same fixture.
`contracts/charter/**` is never edited (C-001). Shared files are edited append-only, except the edits plan.md lists.
