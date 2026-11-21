---
work_package_id: WP06
title: The mutability check replaces the write policy gate
dependencies:
- WP05
requirement_refs:
- FR-001
- FR-007
- C-001
planning_base_branch: feat/556-closure-checks-behind-ports
merge_target_branch: feat/556-closure-checks-behind-ports
branch_strategy: Planning artifacts for this mission were generated on feat/556-closure-checks-behind-ports. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/556-closure-checks-behind-ports unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-closure-checks-behind-ports-01M7TS43
base_commit: 1d03ed6582c6169f291be3255436ed6557226594
created_at: '2026-11-20T09:56:41.778708+00:00'
subtasks:
- T023
- T024
- T025
- T026
agent: claude
history:
- at: '2026-11-20T05:20:00Z'
  actor: claude::java-jenny
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/MutabilityCheck.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MutabilityCheck.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PermissiveMutabilityCheck.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MutabilityCheck.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/WritePolicyGate.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MutabilityLevel.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackMutabilityPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PermissiveWritePolicyGate.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PermissiveMutabilityCheck.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/DirectiveRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ParadigmRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/StyleguideRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ToolguideRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipRegistrationService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackMutabilityPolicyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackMutabilityPolicyClosureTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ArtifactRemovalServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RelationshipRemovalServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RelationshipRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/DirectiveRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ParadigmRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/StyleguideRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ToolguideRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RemovalConcurrencyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RefusedRemovalTrailTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRegistrationEdgeDiffTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/UnreadableStoredPackWriteTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PerFileWriteContentTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ToolguideControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ParadigmControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RelationshipControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/DirectiveControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/StyleguideControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackManifestRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PresetRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackCommitStoreTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP06 — The mutability check replaces the write policy gate

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else, and apply its
initialization, boundaries and directives.

Plan: [plan.md](../plan.md). Squad dispositions: [research/squad-plan.md](../research/squad-plan.md). Spec:
[spec.md](../spec.md).

## Branch Strategy

Planning branch and final merge target: `feat/556-closure-checks-behind-ports`. Execution worktrees are allocated per computed lane from `lanes.json`.
Implement with `spec-kitty agent action implement WP06 --agent claude --mission closure-checks-behind-ports-01M7TS43`.

## Objective

`MutabilityCheck` replaces `WritePolicyGate` outright. A registration and the multi-endpoint writes name every refused
write (FR-007, D5, squad A7, R5). The check still runs before the impact check, and its `409` ends the write.

## Subtasks

- **T023 The `MutabilityCheck` port** (`packs.domain`):
  - `Writes` is sealed: `Live(Pack, List<Write>)` or `Staged(Pack, List<Write>)`;
  - `Operation` and `Write` move here;
  - `ClosureVerdict judge(Writes)`, and the `Rule<Writes>` view.
  - `WritePolicyGate` is deleted, and `MutabilityLevel`'s reference follows.
- **T024 `PackMutabilityPolicy`** implements the port.
  - Each write that its level forbids is one `governance_policy_violation` fault, with today's detail. The closure is
    still read once for all the writes.
  - `PermissiveWritePolicyGate` becomes `PermissiveMutabilityCheck`.
- **T025 The callers** judge all their writes at once and throw `PackRefusals.of`:
  - the per-kind registration services;
  - `RelationshipRegistrationService` (both endpoints);
  - `RelationshipRemovalService` and `ArtifactRemovalService` (every edge or artifact);
  - `PackRegistrationService` (`Staged`).
  The last three are owned by WP05. Edit them out of map and record the rationale (a type change only).
- **T026 Tests.**
  - US4.1: two locked writes give one `409` with two violations, in diff order.
  - US4.2: one write gives the same answer as today.
  - Every test that named `WritePolicyGate` moves to `MutabilityCheck`, with its assertions unchanged.

## Done when

- `./gradlew :packs:test :deployable:test` passes, and nothing names `WritePolicyGate`.

## Always

- Record each subtask with `spec-kitty agent tasks mark-status`.
- Tests carry only the canonical shape, except error-handling cases (invalid packs).
- No legacy type beside its replacement, and no wire code added, removed or renamed (C-001, C-006).
- Signed commits, with no model name.

## Reviewer guidance

Check each subtask against its acceptance scenario, check that single-fault answers are unchanged (SC-002), and check
that no ArchUnit rule is widened.
