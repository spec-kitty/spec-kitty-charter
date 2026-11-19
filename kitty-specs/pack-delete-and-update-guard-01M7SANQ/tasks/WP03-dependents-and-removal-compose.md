---
work_package_id: WP03
title: PackDependents and the removal compose
dependencies:
- WP01
requirement_refs:
- FR-004
- FR-005
- FR-014
- NFR-001
- C-002
- C-003
planning_base_branch: feat/517-pack-delete-and-update-guard
merge_target_branch: feat/517-pack-delete-and-update-guard
branch_strategy: Planning artifacts for this mission were generated on feat/517-pack-delete-and-update-guard. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/517-pack-delete-and-update-guard unless the human explicitly redirects the landing branch.
subtasks:
- T009
- T010
- T011
- T012
history:
- at: '2026-11-19T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/PackDependents.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackDependents.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosurePackDependents.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosurePackDependentsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationImpactRemovalTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ActivationImpact.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackDependents.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosurePackDependents.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationImpact.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureRoots.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/StagedPackViews.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/InboundReferences.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StagedPackViewFactory.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ActivationImpactAdapter.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalCode.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosurePackDependentsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationImpactRemovalTest.java
tags: []
tracker_refs: []
---

# WP03 — PackDependents and the removal compose

Plan: [plan.md](../plan.md). Squad dispositions: [research/squad-plan.md](../research/squad-plan.md).

## Subtasks

- **T009** `ActivationImpact.activationRoots()` returns `List<ActivationRoots>` (id, packs); `ActivationImpactAdapter` and the callers move with it
- **T010** The `PackDependents` port and `ClosurePackDependents`: direct `depends_on` and `parent_pack`, activations naming the pack, and inbound referrers; fails closed on a pack that cannot be read
- **T011** `StagedPackViews.Session.without` and `RegistrationImpact.checkRemoval(Session, ConfiguredPack)`, on one session
- **T012** `RefusalCode.PACK_HAS_DEPENDENTS` and the refusal shaped like `artifact_referenced` (packs and capped violations)

## Done when

- Each subtask is recorded with `spec-kitty agent tasks mark-status`.
- `./gradlew verifyAll` is green on Java 25.
- Tests carry only the canonical shape (charter companion), except error-handling cases.
