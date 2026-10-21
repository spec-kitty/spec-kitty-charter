---
work_package_id: WP05
title: Migrate adapter-out-yaml → packs.infra.storage
dependencies:
- WP04
requirement_refs:
- FR-013
- NFR-002
- SC-005
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-repositories-validation-01M5EET9
base_commit: 98ba735b180ee1b1ca13ff442571a23ed838fd94
created_at: '2026-10-21T13:48:14.335882+00:00'
subtasks:
- T014
- T015
- T016
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/package-info.java
execution_mode: code_change
owned_files:
- service/adapter-out-yaml/**
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/package-info.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP05 – Migrate adapter-out-yaml → packs.infra.storage

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Relocate the YAML persistence adapter from `ai.speckitty.charter.yaml` to `ai.speckitty.charter.packs.infra.storage`, verbatim, keeping the format-specific `Yaml…` class names (`YamlDumpEmitter`, `YamlDocument`, `Yaml`, `ScalarWriter`, `ParsedYaml`). Step 4 of the bottom-up migration (IC-02). No logic change.

## Context

- The `Yaml…`-name-keeping rule and the networknt-confinement rule: [occurrence_map.yaml](../occurrence_map.yaml) `categories.code_symbols` + `archunit_rules_rewrite` (`onlyTheYamlAdapterUsesNetworknt`).
- Storage-ring role: [data-model.md](../data-model.md) `packs.infra.storage`; [plan.md](../plan.md) IC-02 02d.
- The strategy/DSO/`DomainRepositoryService` and the new round-trip tests are **not** added here — WP10/WP11 add them on top of the moved adapter.

### Subtask T014 — Move the adapter main
- **Purpose**: Populate `packs.infra.storage`.
- **Steps**:
  1. Move all `ai.speckitty.charter.yaml` main sources to `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/`, rewriting `package` to `ai.speckitty.charter.packs.infra.storage` and imports to `kernel.domain`/`packs.domain`/`packs.application`.
  2. **Keep** the `Yaml…` class names unchanged.
  3. Add `infra/storage/package-info.java` (`@NullMarked`).
  4. Activate the SnakeYAML + networknt dependency placements in `service/packs/build.gradle.kts` (staged in WP01).
- **Files**: the adapter main tree → `packs/infra/storage/`; new package-info.
- **Validation**: `./gradlew :packs:compileJava`.
- **Edge cases**: `FilesystemPackElementRepository` (implements the retiring `PackElementRepository`) and `TacticReader`/`TacticWriter` (handle the roundtrip cluster) move verbatim now; WP05 retires/replaces them. Do not retire anything here.

### Subtask T015 — Move the adapter tests + fixtures
- **Steps**:
  1. Move the adapter test sources + fixtures (`PackFixtures`, `OpenPacksCorpus`, `DivergenceRegister`, `roundtrip-fixtures`, `vendored-packs`) into the packs slice test tree, rewriting packages/imports.
  2. The `PackElementRoundTripTest` / `PackElementWriteSafetyTest` battery moves with the module **but is replaced in WP10** against the new ports — leave the moved copies intact here; WP10 owns their replacement.
- **Files**: adapter test tree → `packs/.../infra/storage/` test sources + resources.
- **Validation**: `./gradlew :packs:test`.
- **Edge cases**: the pre-existing environmental `VendoredPacksTest` / `spec-kitty-open-packs` pin behaviour carries over unchanged (quickstart §5).

### Subtask T016 — Downstream rewrites + de-registration
- **Steps**:
  1. Rewrite the composition root's imports of `charter.yaml.*` to `packs.infra.storage.*`.
  2. Remove the `adapter-out-yaml` module directory once drained; `settings.gradle.kts` de-registration is WP07's — do not edit it here.
  3. `verifyAll`.
- **Files**: `service/adapter-out-yaml/**` (removed), composition-root import edits in their home module.
- **Validation**: `./gradlew verifyAll`.

## Branch Strategy

- Planning branch `feat/pack-repositories-validation`; final merge target `feat/pack-repositories-validation`.
- Execution worktrees per lane from `lanes.json`. Depends on WP04; precedes WP06.

## Definition of Done

- The adapter lives in `packs.infra.storage` with `Yaml…` names kept; networknt confined to this package.
- `./gradlew verifyAll` green.

## Risks

- **Networknt leak** → the `only ..packs.infra.storage.. depends on com.networknt` rule (WP07) must stay biting; keep every networknt import inside this package.
- **Premature retirement** → `FilesystemPackElementRepository`/`TacticReader`/`TacticWriter` move as-is; WP05 reshapes them.

## Reviewer guidance

- Confirm move-only diffs and kept `Yaml…` names.
- Confirm the SnakeYAML/networknt build placements moved with the adapter.
- Confirm no retirement or logic change leaked into this WP.
