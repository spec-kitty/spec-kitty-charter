---
work_package_id: WP01
title: 'Missions edge module, wiring and ArchUnit rules'
dependencies: []
requirement_refs:
- FR-006
- NFR-002
planning_base_branch: issue-99-mission-type-reads
merge_target_branch: issue-99-mission-type-reads
branch_strategy: "Planning artifacts for this mission were generated on issue-99-mission-type-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-99-mission-type-reads unless the human explicitly redirects the landing branch."
subtasks:
- T001
- T002
- T003
agent_profile: java-jenny
authoritative_surface: service/missions-edge/
create_intent:
- service/missions-edge/build.gradle.kts
execution_mode: code_change
owned_files:
- settings.gradle.kts
- service/missions-edge/build.gradle.kts
- service/deployable/build.gradle.kts
- service/charter-reports/build.gradle.kts
- service/deployable/src/main/java/ai/speckitty/charter/deployable/DeployableConfiguration.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java
- service/deployable/src/test/java/ai/speckitty/charter/missions/
---
# WP01 · Missions edge module, wiring and ArchUnit rules

## Goal

Missions edge module, wiring and ArchUnit rules.

## Subtasks

- **T001** Add `:missions-edge` (settings, build file, deployable dependency, charter-reports `serviceModules`, the four `missions.*.config` packages in the deployable scan)
- **T002** ArchUnit: the profiles mission's edge-ring rule shapes applied to missions, plus MISSIONS_NAME_PACKS_ONLY_FROM_ADAPTER and MISSIONS_NAME_ACTIVATIONS_ONLY_FROM_ADAPTER
- **T003** Known-bad fixtures under service/deployable/src/test/.../missions/*/badfixture that each new rule bites

## Test first

Each subtask opens with a failing scenario or unit test (DIRECTIVE_034). No test is skipped or weakened to go green (QA_003).

## Notes

Rebase on the profiles mission's first push (ADR-0037 amendment and `:profiles-edge` rule shapes) and mirror them. Red first: each new rule's fixture test fails before the rule exists. Verify with `:deployable:test --tests '*HexagonalArchitectureTest*'`.

See [plan.md](../plan.md) and [spec.md](../spec.md).
