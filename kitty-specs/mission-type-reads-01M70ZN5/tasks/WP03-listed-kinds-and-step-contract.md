---
work_package_id: WP03
title: 'LISTED kinds, mission_step_contract item, list and references'
dependencies: [WP02]
requirement_refs:
- FR-001
- FR-004
- FR-012
planning_base_branch: issue-99-mission-type-reads
merge_target_branch: issue-99-mission-type-reads
branch_strategy: "Planning artifacts for this mission were generated on issue-99-mission-type-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-99-mission-type-reads unless the human explicitly redirects the landing branch."
subtasks:
- T010
- T011
- T012
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactReadService.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactDtos.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactController.java
- service/packs/src/test/
---
# WP03 · LISTED kinds, mission_step_contract item, list and references

## Goal

LISTED kinds, mission_step_contract item, list and references.

## Subtasks

- **T010** ArtifactReadService: LISTED (the five, mission_type, mission_step_contract) vs the generic item kinds; ABSENT codes; Item.OfMissionStepContract; unit tests first (ArtifactReadServiceTest, WireTokenParityTest)
- **T011** ArtifactDtos maps the step contract with links: mission-type, artifact for each delegation candidate of a listed kind
- **T012** Scenarios for the step contract item, ?kind= lists and /references turn green; a generic-route mission_type item is kind_not_found when the literal route is absent (unit)

## Test first

Each subtask opens with a failing scenario or unit test (DIRECTIVE_034). No test is skipped or weakened to go green (QA_003).

## Notes

Shared with the profiles mission: additive hunks on its branch. The step contract's mission-type link points at the pack-scoped literal route, and only when the pack holds that mission type.

See [plan.md](../plan.md) and [spec.md](../spec.md).
