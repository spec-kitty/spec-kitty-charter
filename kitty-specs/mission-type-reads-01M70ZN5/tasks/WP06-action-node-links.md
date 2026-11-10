---
work_package_id: WP06
title: 'Step links on action graph nodes and the action context (#450 part)'
dependencies: [WP05]
requirement_refs:
- FR-008
planning_base_branch: issue-99-mission-type-reads
merge_target_branch: issue-99-mission-type-reads
branch_strategy: "Planning artifacts for this mission were generated on issue-99-mission-type-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-99-mission-type-reads unless the human explicitly redirects the landing branch."
subtasks:
- T020
- T021
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectiveReadMapper.java
execution_mode: code_change
owned_files:
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectiveReadMapper.java
- contracts/charter/schemas/EffectiveGraphNode.yaml
- contracts/charter/schemas/EffectiveActionContext.yaml
- contracts/charter/scenarios/effective-graph-and-context.feature
- contracts/charter/scenarios/effective-reads.feature
- contracts/charter/scenarios/effective-mission-types.feature
---
# WP06 · Step links on action graph nodes and the action context (#450 part)

## Goal

Step links on action graph nodes and the action context (#450 part).

## Subtasks

- **T020** EffectiveReadMapper: action:<type>/<step> graph nodes and the action context's action carry artifact (pack-scoped step) and effective-artifact (activation step) links
- **T021** Scenarios: the graph and action context of this repository's charter link software-dev's steps; mission_type and mission_step_contract items carry the profiles mission's links

## Test first

Each subtask opens with a failing scenario or unit test (DIRECTIVE_034). No test is skipped or weakened to go green (QA_003).

## Notes

Only links; the action-context scoping half of #450 is out of scope.

See [plan.md](../plan.md) and [spec.md](../spec.md).
