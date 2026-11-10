---
work_package_id: WP04
title: 'Composite read and pack-scoped mission type, step and skeleton reads'
dependencies: [WP01, WP02]
requirement_refs:
- FR-002
- FR-003
- FR-005
- FR-006
- FR-009
- FR-012
- NFR-003
planning_base_branch: issue-99-mission-type-reads
merge_target_branch: issue-99-mission-type-reads
branch_strategy: "Planning artifacts for this mission were generated on issue-99-mission-type-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-99-mission-type-reads unless the human explicitly redirects the landing branch."
subtasks:
- T013
- T014
- T015
- T016
agent_profile: java-jenny
authoritative_surface: service/missions-edge/src/main/java/ai/speckitty/charter/missions/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/MissionTypeHoldingRead.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompanionFiles.java
execution_mode: code_change
owned_files:
- service/missions-edge/src/main/java/ai/speckitty/charter/missions/application/**
- service/missions-edge/src/main/java/ai/speckitty/charter/missions/infra/api/**
- service/missions-edge/src/main/java/ai/speckitty/charter/missions/infra/packs/**
- service/missions-edge/src/test/**
- service/packs/src/main/java/ai/speckitty/charter/packs/application/MissionTypeHoldingRead.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompanionFiles.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
---
# WP04 · Composite read and pack-scoped mission type, step and skeleton reads

## Goal

Composite read and pack-scoped mission type, step and skeleton reads.

## Subtasks

- **T013** packs.application MissionTypeHoldingRead.holding(pack, typeId): one verified read of the type, its templates, the step contracts whose mission is the type and the stored companion bytes (packs.domain port PackCompanionFiles implemented by the store)
- **T014** missions.application MissionTypeReadService over the PackMissionTypes port; views for type, step (bound contract by mission/action, lowest id; bound skeletons by template_path with templates/ stripped) and skeleton (D9 name codec, text|base64)
- **T015** missions.infra.packs adapter narrowing CharterArtifact to MissionType; missions.infra.api MissionTypeController implementing MissionTypesApi with MissionRelation links and a relation parity test
- **T016** Scenarios for the pack-scoped reads turn green; a unit test serves every step of open-packs a2a9379 byte for byte, spdd markers included

## Test first

Each subtask opens with a failing scenario or unit test (DIRECTIVE_034). No test is skipped or weakened to go green (QA_003).

## Notes

Never name ConsistentRead.Verified outside packs; the missions port carries the fingerprint as a string. The literal routes must win over the generic {kind} routes (scenario).

See [plan.md](../plan.md) and [spec.md](../spec.md).
