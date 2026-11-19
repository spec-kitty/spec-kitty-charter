---
work_package_id: WP02
title: The version order, previousVersion and label equality
dependencies:
- WP01
requirement_refs:
- FR-015
- C-001
- C-005
- FR-008
- FR-009
- FR-010
planning_base_branch: feat/517-pack-delete-and-update-guard
merge_target_branch: feat/517-pack-delete-and-update-guard
branch_strategy: Planning artifacts for this mission were generated on feat/517-pack-delete-and-update-guard. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/517-pack-delete-and-update-guard unless the human explicitly redirects the landing branch.
subtasks:
- T005
- T006
- T007
- T008
history:
- at: '2026-11-19T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRegistrationService.java
create_intent:
- contracts/charter/scenarios/registration/version-order.feature
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Pep440Range.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/Pep440RangeTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackVersionHistory.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationMapper.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackVersionHistoryTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PackRegistrationMapperNoticesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PackRegistrationMapperSourceTest.java
- contracts/charter/scenarios/registration/version-order.feature
- contracts/charter/scenarios/registration/whole-pack-registration.feature
tags: []
tracker_refs: []
---

# WP02 — The version order, previousVersion and label equality

Plan: [plan.md](../plan.md). Squad dispositions: [research/squad-plan.md](../research/squad-plan.md).

## Subtasks

- **T005** `Pep440Range.compareVersions`, tested by precedence (pre, post, dev, epoch, local)
- **T006** `applyVersionRule`: refuse not-newer with other bytes `409 pack_version_conflict`, naming both versions; `RegistrationOutcome.previousVersion` and its mapping
- **T007** `PackVersionHistory.requireNotReused` matches labels by PEP 440 equality; javadoc without "label, not an order"
- **T008** `version-order.feature` `@contract` scenarios; rewrite the backward version steps in `whole-pack-registration.feature`

## Done when

- Each subtask is recorded with `spec-kitty agent tasks mark-status`.
- `./gradlew verifyAll` is green on Java 25.
- Tests carry only the canonical shape (charter companion), except error-handling cases.
