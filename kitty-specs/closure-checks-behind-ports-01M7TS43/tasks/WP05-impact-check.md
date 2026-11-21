---
work_package_id: WP05
title: The impact check port
dependencies:
- WP04
requirement_refs:
- FR-001
- FR-002
- FR-004
- FR-009
- FR-010
- FR-011
- NFR-001
- C-001
- C-002
- C-003
planning_base_branch: feat/556-closure-checks-behind-ports
merge_target_branch: feat/556-closure-checks-behind-ports
branch_strategy: Planning artifacts for this mission were generated on feat/556-closure-checks-behind-ports. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/556-closure-checks-behind-ports unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-closure-checks-behind-ports-01M7TS43
base_commit: 631c20951d7f18a9335e463f73cc674dae292609
created_at: '2026-11-20T08:34:19.323054+00:00'
subtasks:
- T017
- T018
- T019
- T020
- T021
- T022
agent: claude
history:
- at: '2026-11-20T05:20:00Z'
  actor: claude::java-jenny
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/ImpactCheck.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ImpactCheck.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureImpactCheck.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureImpactCheckTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/UninstallReadCountTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ImpactCheck.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationImpact.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureImpactCheck.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackDependents.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosurePackDependents.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackUninstallService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactRemovalService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipRemovalService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/StagedPackViews.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ActivationImpact.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CommitStorePackWriteStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StagedPacks.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StagedPackViewFactory.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationMapper.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureImpactCheckTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/UninstallReadCountTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationImpactTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationImpactActivationsAffectedTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PerArtifactImpactTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackUninstallServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRegistrationScaleTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PackRegistrationMapperNoticesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PackRegistrationMapperSourceTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP05 — The impact check port

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else, and apply its
initialization, boundaries and directives.

Plan: [plan.md](../plan.md). Squad dispositions: [research/squad-plan.md](../research/squad-plan.md). Spec:
[spec.md](../spec.md).

## Branch Strategy

Planning branch and final merge target: `feat/556-closure-checks-behind-ports`. Execution worktrees are allocated per computed lane from `lanes.json`.
Implement with `spec-kitty agent action implement WP05 --agent claude --mission closure-checks-behind-ports-01M7TS43`.

## Objective

`ImpactCheck` replaces direct calls of `RegistrationImpact`. It collects across closures, absorbs #517's dependents
finder, and returns the notices in one pass (FR-002, FR-004, FR-009, FR-011; squad A2, A3, A6, R2 to R4).

## Subtasks

- **T017 The `ImpactCheck` port** (`packs.domain`):
  - `PackChange` is sealed: `Registration(Replacement)`, `Write(PackIdentifier, UnaryOperator<PackCompositionFacts>,
    Optional<Pack>)`, `Removal(ConfiguredPack, Optional<PackDescriptor>, Set<Urn> held)`;
  - `Replacement` and `Notices` move here from `RegistrationImpact`;
  - `Impact(ClosureVerdict verdict, Notices notices)`;
  - `Impact judge(PackChange)`, and the `Rule<PackChange>` view.
- **T018 `ClosureImpactCheck`.** `git mv` `RegistrationImpact` to `ClosureImpactCheck`, which implements the port.
  - **Every closure, in `ClosureRoots` order.** Compose it, and lift a compose refusal (as `inClosure` rewords it) to
    its faults. Otherwise run the relation rules. A read failure propagates.
  - **Only a closure that passed** gets its snapshot, notices and change. A snapshot refusal is that closure's fault.
  - **Then the walk** of every single-root closure that passed, as `judgeFallbacksAndPins` iterates today.
  - **Then the gate.** `activations.requireComposable` runs only when the verdict passes.
  - **Sessions.** `judge(PackChange)` opens a session. `judge(StagedPackViews.Session, PackChange)` is the
    application overload.
- **T019 The removal.**
  - Move `ClosurePackDependents`' finders in. Dependents give one `pack_has_dependents` fault, whose violations are
    the dependents, as `PackDependents.refusal` builds them today, and nothing is composed.
  - Delete `PackDependents` and `ClosurePackDependents`.
  - `PackUninstallService` judges through the overload, in its own session.
- **T020 The callers.**
  - `PackRegistrationService`, `ArtifactRemovalService`, `RelationshipRemovalService` and `CommitStorePackWriteStore`
    name `ImpactCheck` and throw `PackRefusals.of(impact.verdict())` when it fails.
  - `CommitStorePackWriteStore` loses its fallback that builds the concrete class (squad A6). Wire the bean where the
    tests build the store.
  - `StagedPackViews`, `StagedPacks`, `StagedPackViewFactory`, `ActivationImpact` and `PackRegistrationMapper` follow
    the moved types.
- **T021 The census.** `Finding(pack, List<ClosureFault>)` and `logCensus` logs each fault.
- **T022 Tests.**
  - `RegistrationImpactTest` becomes `ClosureImpactCheckTest`. Its single-fault assertions are kept.
  - US1.3: two closures, one refusal naming both.
  - US3.1 and US3.2.
  - `UninstallReadCountTest`: one descriptor read per pack per delete (#517 NFR-001).
  - A dry-run delete with an unreadable closure member rethrows `pack_unreadable`.
  - The #517 scenarios (`pack-uninstall.feature`, `registration-dry-run.feature`) pass unchanged.

## Done when

- `./gradlew :packs:test :deployable:test` passes: the ArchUnit gate (C-002) and the scenarios.
- No class names `RegistrationImpact`, `PackDependents` or `ClosurePackDependents`.

## Always

- Record each subtask with `spec-kitty agent tasks mark-status`.
- Tests carry only the canonical shape, except error-handling cases (invalid packs).
- No legacy type beside its replacement, and no wire code added, removed or renamed (C-001, C-006).
- Signed commits, with no model name.

## Reviewer guidance

Check each subtask against its acceptance scenario, check that single-fault answers are unchanged (SC-002), and check
that no ArchUnit rule is widened.
