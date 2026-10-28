---
work_package_id: WP02
title: "Preset in the domain"
dependencies: []
requirement_refs:
- FR-002
- FR-003
planning_base_branch: feat/195-pack-level-round-trip
merge_target_branch: feat/195-pack-level-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/195-pack-level-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/195-pack-level-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T004
- T005
- T006
agent: claude
history:
- at: '2026-10-28T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/Preset
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Preset.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PresetItem.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PresetTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPresetsTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Preset.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PresetItem.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Pack.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PresetTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPresetsTest.java
role: implementer
tags: []
task_type: implement
---
# Work Package Prompt: WP02 — Preset in the domain

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

`Preset` and `PresetItem` as domain values, with the derived required set, plus a presets slot on the `Pack`
(spec FR-002, FR-003; data-model.md).

## Subtask T004 — `PresetItem` and `Preset`

- `PresetItem(String id, boolean mustHave)`: the id is not blank.
- `Preset`:
  - `id`: grammar `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, at most 64 characters.
  - `description`: not blank.
  - `activatedKinds`: `List<ArtifactKind>`, unique.
  - `artifacts`: `Map<ArtifactKind, List<PresetItem>>`. Keys only from the nine activatable kinds (directive,
    tactic, styleguide, toolguide, paradigm, procedure, agent_profile, mission_step_contract, anti_pattern). Every
    list is non-empty, keeps its order and has unique item ids.
  - `missionTypes`: `List<PresetItem>`, unique.
- Guard the invariants with `ModularRuleset` and `Constraints`, as `Pack` does. A breach names the preset and the id.
- Derived:
  - `required()` returns a map from each kind to its must-have ids, plus the mission types.
  - `governing()` returns true when `required()` is not empty.
- Equality is by value. Javadoc in the house style.

## Subtask T005 — `Pack` holds presets

- Add `presets()`: `List<Preset>`, sorted by id.
- Builder slot `preset(Preset)`. Carry the presets in `toBuilder` and `with`.
- Generalise `sortedByPath` into one keyed helper, used by the asset files and the presets. A duplicate key breaches,
  with the asset-file message unchanged.
- The class doc now records the pack-level slots (asset files, presets), and notes that the rule of three has fired,
  with `PackLevelContent` extraction as a follow-up (research P8).

## Subtask T006 — Tests

- `PresetTest`: each invariant breach is paired with a valid preset. Cover `required()` and `governing()` with and
  without marks. Item order is kept.
- `PackPresetsTest`:
  - presets are sorted;
  - a duplicate id breaches;
  - `toBuilder().build()` and `with(artifact)` keep the presets (directly, research P5);
  - the asset-file duplicate message is unchanged.

## Definition of Done

- `./gradlew :packs:test` is green, including `HexagonalArchitectureTest`.
- No storage change in this WP.

## Branch Strategy

Planning base and merge target: `feat/195-pack-level-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP02 --agent claude --mission pack-level-files-round-trip-01M60H1A`.

Tests come first (charter: test first, no fake green). Each refusal is paired with a clean control on the same fixture.
`contracts/charter/**` is never edited (C-001). Shared files are edited append-only, except the edits plan.md lists.
