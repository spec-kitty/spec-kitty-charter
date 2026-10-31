---
work_package_id: WP03
title: Mission-type binding and companions
dependencies: [WP01]
requirement_refs:
- FR-002
- FR-003
- FR-007
planning_base_branch: issue-194-mission-type-consolidation
merge_target_branch: issue-194-mission-type-consolidation
branch_strategy: Planning artifacts for this mission were generated on issue-194-mission-type-consolidation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-194-mission-type-consolidation unless the human explicitly redirects the landing branch.
subtasks:
- T008
- T009
- T010
- T011
- T012
history: []
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- contracts/packs/schemas/mission-step-prompt.schema.json
- contracts/packs/schemas/mission-step-guidelines.schema.json
execution_mode: code_change
owned_files:
- 'service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBinding.java'
- contracts/packs/schemas/mission-step-prompt.schema.json
- contracts/packs/schemas/mission-step-guidelines.schema.json
- contracts/packs/file-kinds.yaml
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/MissionType*.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBindings.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlFormats.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileStems.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/**
- service/packs/src/test/resources/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---
# Work Package Prompt: WP03 — Mission-type binding and companions

## Do This First: Load Agent Profile

Load the agent profile `java-jenny` (`spec-kitty agent profile show java-jenny`) and follow its guidance.

## Objective

Add the companion structure schemas and the `files` rules (`**/*.mission-type.yaml`, `*/missions/*/steps/*.prompt.md`, `*/missions/*/steps/*.guidelines.md`, markdown form) in one block after the existing rules. Build `MissionTypeDso`, `MissionTypeYaml` (schema key order, S16 omission, one writer method per sub-item) and `MissionTypeBinding` (token and schema `mission-type`, `idIsFileStem`, companions per step in order, frontmatter refused, derived paths refused when they differ, `template_path` must resolve inside the pack), appended last to `KindBindings` and `YamlFormats`. Generalize the stem check in `FileStems` so a mission type's path must be `missions/<id>/<id>.mission-type.yaml`; let `FilesystemPackStore.newFile` create a binding's nested file and its directories after the id grammar has refused `/` and `..`. Tests: fixture packs round-trip equal; misplaced files refused (root, nested, `mission_types/`); committed positive controls through the production write path (step title changed, steps swapped, on-demand step dropped, prompt section edited, reference reordered); create and traversal tests; `KindBindingsTest` updates; `PackFileKindTest` samples; a `GeneratedPackTree` mission-type shape. No pinned path matches the new rules, so `check` stays green.

## Coordination note (from WP02 review)

WP02 already added the `**/*.mission-type.yaml` `files` rule (PackSchemas loads only schemas a rule names), its
`PackFileKindTest` sample, the `PackSchemasTest` count (20), the `PresetTest` exclusion and `MissionTypeSchemaTest`.
WP03 adds only the two companion rules and schemas, and updates those tests' counts for them.

## Context

- ADR-0044 (`docs/adr/0044-mission-types-consolidated-into-one-sanitized-shape.md`), [spec.md](../spec.md),
  [plan.md](../plan.md), [data-model.md](../data-model.md), research dispositions in [research.md](../research.md).
- Test first: write the failing acceptance and unit tests named above before the code. Zero new suppressions; methods
  at complexity 15 or below; Javadoc on every type and member (doclint).

## Definition of Done

- The named tests exist, failed first, and pass.
- `./gradlew :packs:test :packs:openPacksRoundTrip` green (and `check` stays green: no pinned path is affected before WP06).
- Owned files only; shared-file edits in one block.
