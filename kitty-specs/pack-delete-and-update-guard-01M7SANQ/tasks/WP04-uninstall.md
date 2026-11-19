---
work_package_id: WP04
title: 'DELETE /packs/{packId}: the store, the service, the controller and the audit'
dependencies:
- WP02
- WP03
requirement_refs:
- FR-011
- FR-015
- FR-001
- FR-002
- FR-003
- FR-006
- FR-007
- NFR-002
planning_base_branch: feat/517-pack-delete-and-update-guard
merge_target_branch: feat/517-pack-delete-and-update-guard
branch_strategy: Planning artifacts for this mission were generated on feat/517-pack-delete-and-update-guard. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/517-pack-delete-and-update-guard unless the human explicitly redirects the landing branch.
subtasks:
- T013
- T014
- T015
- T016
- T017
history:
- at: '2026-11-19T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/PackUninstallService.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackUninstallService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackUninstallController.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackUninstallServiceTest.java
- contracts/charter/scenarios/registration/pack-uninstall.feature
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRemovalStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackUninstallService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CommitStorePackWriteStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackCommitStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackUninstallController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RegistrationPaths.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/WorktreeGuard.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RegistrationAuditFilter.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackUninstallServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackCommitStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RegistrationPathsTest.java
- contracts/charter/scenarios/registration/pack-uninstall.feature
tags: []
tracker_refs: []
---

# WP04 — DELETE /packs/{packId}: the store, the service, the controller and the audit

Plan: [plan.md](../plan.md). Squad dispositions: [research/squad-plan.md](../research/squad-plan.md).

## Subtasks

- **T013** `PackRemovalStore.removeWhole`: hide stale asides, one rename to `.pack-discard-*` under the generation bracket, delete; heal the history; refuse a linked pack
- **T014** `PackUninstallService.uninstall(packId, Preconditions, Mode)`: 404, 428, then under the locks identity 409, 412, dependents 409, compose 422, remove
- **T015** The controller for `deletePack` (204, or 200 `PackImpact` on a dry run) behind the registration flag; the `RegistrationApi` delegation
- **T016** The audit: `RegistrationPaths` and `WorktreeGuard` learn `deletePack`; a `dryRun=true` request is never entered
- **T017** `pack-uninstall.feature` `@contract` scenarios with dry-run twins; `PackCommitStoreTest` crash/repair and swap-failure cases

## Done when

- Each subtask is recorded with `spec-kitty agent tasks mark-status`.
- `./gradlew verifyAll` is green on Java 25.
- Tests carry only the canonical shape (charter companion), except error-handling cases.
