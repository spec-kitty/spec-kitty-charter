---
work_package_id: WP05
title: 'Activation-scoped mission type, step and skeleton reads'
dependencies: [WP04]
requirement_refs:
- FR-007
- SC-001
planning_base_branch: issue-99-mission-type-reads
merge_target_branch: issue-99-mission-type-reads
branch_strategy: "Planning artifacts for this mission were generated on issue-99-mission-type-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-99-mission-type-reads unless the human explicitly redirects the landing branch."
subtasks:
- T017
- T018
- T019
agent_profile: java-jenny
authoritative_surface: service/missions-edge/src/main/java/ai/speckitty/charter/missions/infra/activations/
execution_mode: code_change
owned_files:
- service/missions-edge/src/main/java/ai/speckitty/charter/missions/infra/activations/**
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java
- contracts/charter/paths/
---
# WP05 · Activation-scoped mission type, step and skeleton reads

## Goal

Activation-scoped mission type, step and skeleton reads.

## Subtasks

- **T017** activations.application EffectiveReadService.missionTypeHolding(activationId, typeId) on the profiles winner read: the winning type, skeletons and companion bytes from its holding pack, and the merged step contracts whose mission is the type
- **T018** missions.infra.activations adapter for ActivationMissionTypes; controller methods for the three activation operations; un-reserve them in the contract
- **T019** Scenarios: activation reads, unreached mission type is mission_type_not_found, unknown activation is activation_not_found; layer-merge winner from a project-level override

## Test first

Each subtask opens with a failing scenario or unit test (DIRECTIVE_034). No test is skipped or weakened to go green (QA_003).

## Notes

Depends on the profiles PR's EffectiveReadService.artifact; build on its branch.

See [plan.md](../plan.md) and [spec.md](../spec.md).
