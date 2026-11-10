---
work_package_id: WP02
title: 'Contract: schemas, operations, refusals, relations, fragments; red scenarios'
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-005
- FR-009
- NFR-001
- SC-002
planning_base_branch: issue-99-mission-type-reads
merge_target_branch: issue-99-mission-type-reads
branch_strategy: "Planning artifacts for this mission were generated on issue-99-mission-type-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-99-mission-type-reads unless the human explicitly redirects the landing branch."
subtasks:
- T004
- T005
- T006
- T007
- T008
- T009
agent_profile: java-jenny
authoritative_surface: contracts/charter/
create_intent:
- contracts/charter/scenarios/mission-types.feature
execution_mode: code_change
owned_files:
- contracts/charter/openapi.yaml
- contracts/charter/schemas/
- contracts/charter/paths/
- contracts/charter/parameters/
- contracts/charter/responses/
- contracts/charter/examples/
- contracts/charter/changes/
- contracts/charter/scenarios/mission-types.feature
- contracts/lint/ruleset.yaml
- docs/development/hypermedia-hateoas.md
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalCode.java
---
# WP02 · Contract: schemas, operations, refusals, relations, fragments; red scenarios

## Goal

Contract: schemas, operations, refusals, relations, fragments; red scenarios.

## Subtasks

- **T004** Schemas MissionType, MissionStep, MissionStepContract, MissionSkeleton with x-source/x-derived citations; Artifact oneOf gains missionType and missionStepContract; ArtifactKindToken and ArtifactKind parameter gain both tokens
- **T005** Paths and the MissionTypes tag: the three pack operations and the three activation operations (x-reserved, 501); getPackArtifact's description names the literal mission_type read
- **T006** Refusal codes mission_step_contract_not_found and skeleton_not_found (minimal hunk, rebased after #437); raised-by texts of mission_type_not_found and action_not_found; a 404 response per new operation
- **T007** Link relations mission-type, step-contract, skeleton in hypermedia-hateoas.md and in link-relations-are-known; examples for every 200
- **T008** Change fragments: ### Breaking and ### Added
- **T009** Red scenarios contracts/charter/scenarios/mission-types.feature: mission type, sequence and on-demand step, flat and nested skeleton (D9 name), step contract item and ?kind= list, /references for both kinds, refusal order including bare-pack pack_unreadable

## Test first

Each subtask opens with a failing scenario or unit test (DIRECTIVE_034). No test is skipped or weakened to go green (QA_003).

## Notes

Scenarios must run and fail (not be undefined) before WP03/WP04. Contract checks: `:contracts:check` and `:charter-api:validateContract`. Keep the CharterRefusalCode.yaml hunk minimal.

See [plan.md](../plan.md) and [spec.md](../spec.md).
