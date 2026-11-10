---
work_package_id: WP07
title: 'DELETE for both kinds with the mission-type cascade, docs and usability run'
dependencies: [WP03, WP04]
requirement_refs:
- FR-010
- FR-011
- SC-001
- NFR-004
planning_base_branch: issue-99-mission-type-reads
merge_target_branch: issue-99-mission-type-reads
branch_strategy: "Planning artifacts for this mission were generated on issue-99-mission-type-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-99-mission-type-reads unless the human explicitly redirects the landing branch."
subtasks:
- T022
- T023
- T024
- T025
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactRemovalService.java
create_intent:
- scripts/usability/service-usability-run.sh
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactRemovalService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRemovalStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
- docs/api/service-contract.md
- scripts/usability/service-usability-run.sh
- contracts/charter/scenarios/mission-types.feature
- contracts/charter/scenarios/README.md
---
# WP07 · DELETE for both kinds with the mission-type cascade, docs and usability run

## Goal

DELETE for both kinds with the mission-type cascade, docs and usability run.

## Subtasks

- **T022** ArtifactRemovalService and PackRemovalStore.Removal: a mission type's removal includes its templates; refuse 409 while step contracts bind it or edges name it or its action nodes; If-Match: * for both kinds
- **T023** Scenarios: remove a step contract; remove a mission type with skeletons (all gone); refused while a step contract binds it; authored-graph 409
- **T024** Docs: docs/api/service-contract.md rows for #99/#100/#96, the skeleton naming rule (D9), the effective-artifact links
- **T025** Extend the shared usability script to follow /api/v1/ → software-dev → 13 steps → 5 contracts → skeletons, pack-scoped and under an activation; record the run

## Test first

Each subtask opens with a failing scenario or unit test (DIRECTIVE_034). No test is skipped or weakened to go green (QA_003).

## Notes

The usability script belongs to the profiles mission; extend it, never fork it.

See [plan.md](../plan.md) and [spec.md](../spec.md).
