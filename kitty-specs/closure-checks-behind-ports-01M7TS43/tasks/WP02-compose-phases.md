---
work_package_id: WP02
title: Every compose phase collects
dependencies:
- WP01
requirement_refs:
- FR-006
planning_base_branch: feat/556-closure-checks-behind-ports
merge_target_branch: feat/556-closure-checks-behind-ports
branch_strategy: Planning artifacts for this mission were generated on feat/556-closure-checks-behind-ports. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/556-closure-checks-behind-ports unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-closure-checks-behind-ports-01M7TS43
base_commit: 11172e9d5a76f6a2c346a55d52a8723672952f2b
created_at: '2026-11-20T05:55:32.232848+00:00'
subtasks:
- T005
- T006
- T007
- T008
agent: claude
history:
- at: '2026-11-20T05:20:00Z'
  actor: claude::java-jenny
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java
create_intent: []
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/EnhancementCompose.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/EdgeIndex.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/CompositeComponentResolver.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCompositionResolverTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/EnhancementComposeTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/OfferingReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/EdgeIndexTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/CompositeComponentResolverTest.java
- contracts/charter/scenarios/composition.feature
role: implementer
tags: []
tracker_refs: []
---

# WP02 — Every compose phase collects

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else, and apply its
initialization, boundaries and directives.

Plan: [plan.md](../plan.md). Squad dispositions: [research/squad-plan.md](../research/squad-plan.md). Spec:
[spec.md](../spec.md).

## Branch Strategy

Planning branch and final merge target: `feat/556-closure-checks-behind-ports`. Execution worktrees are allocated per computed lane from `lanes.json`.
Implement with `spec-kitty agent action implement WP02 --agent claude --mission closure-checks-behind-ports-01M7TS43`.

## Objective

Each phase of `OfferingReadService.composed` answers every fault of its phase, on the read and the write path (FR-006,
squad R6). The phases stay sequential: a phase runs only when the earlier ones pass.

## Subtasks

- **T005** In `PackCompositionResolver`, these return `List<Refusal>` (or `List<ClosureFault>` where they raise a
  `CharterRefusal`):
  - `refuseUnresolvedDelegations`;
  - `checkReferences` with `checkQualifiedReference`;
  - `refuseCaseOnlyMatches`.

  Faults come in the artifacts' order. A single-reference caller takes the list. Leave `resolve()` for its other
  callers. The offering reads `faults()` for the resolver phase.
- **T006** `EnhancementCompose.targetHeld`, and the target checks it backs, return every target fault.
- **T007** `OfferingReadService.composed` runs the phases in today's order. Each phase lifts its faults to
  `ClosureFault`s and, when there are any, throws `PackRefusals.of(new ClosureVerdict(faults))`.
  - The phases are: resolver `faults()`, delegations, references, step enhancement targets, presets, case-only.
  - `EdgeIndex` (a list per pack) and `CompositeComponentResolver` (one reference) map through `PackRefusals.of`.
  - A read failure (`pack_unreadable` from a descriptor read) propagates, as today.
- **T008** Tests:
  - one case per phase with two or more faults, giving one refusal, with the code first and every violation;
  - one single-fault case per phase, equal to today's answer;
  - a `@contract` scenario in `composition.feature` with three reference faults (US5).

## Done when

- `./gradlew :packs:test` passes, and so do the scenarios under `contracts/charter/scenarios/effective-*.feature` and
  `composition.feature`. The single-fault answers are unchanged.

## Always

- Record each subtask with `spec-kitty agent tasks mark-status`.
- Tests carry only the canonical shape, except error-handling cases (invalid packs).
- No legacy type beside its replacement, and no wire code added, removed or renamed (C-001, C-006).
- Signed commits, with no model name.

## Reviewer guidance

Check each subtask against its acceptance scenario, check that single-fault answers are unchanged (SC-002), and check
that no ArchUnit rule is widened.
