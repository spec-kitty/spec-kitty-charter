---
work_package_id: WP03
title: The relation rules collect every fault
dependencies:
- WP02
requirement_refs:
- FR-003
- NFR-002
planning_base_branch: feat/556-closure-checks-behind-ports
merge_target_branch: feat/556-closure-checks-behind-ports
branch_strategy: Planning artifacts for this mission were generated on feat/556-closure-checks-behind-ports. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/556-closure-checks-behind-ports unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-closure-checks-behind-ports-01M7TS43
base_commit: 7594eecdbd7f5500adbec3298a6e03817bd674d1
created_at: '2026-11-20T06:49:14.377019+00:00'
subtasks:
- T009
- T010
- T011
- T012
agent: claude
history:
- at: '2026-11-20T05:20:00Z'
  actor: claude::java-jenny
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/ImpactRelations.java
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ImpactRelationsTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ImpactRelations.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/SpecializationCycleRule.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ImpactRelationsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ImpactRelationsEnhancementTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/SpecializationCycleImpactTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP03 — The relation rules collect every fault

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else, and apply its
initialization, boundaries and directives.

Plan: [plan.md](../plan.md). Squad dispositions: [research/squad-plan.md](../research/squad-plan.md). Spec:
[spec.md](../spec.md).

## Branch Strategy

Planning branch and final merge target: `feat/556-closure-checks-behind-ports`. Execution worktrees are allocated per computed lane from `lanes.json`.
Implement with `spec-kitty agent action implement WP03 --agent claude --mission closure-checks-behind-ports-01M7TS43`.

## Objective

`ImpactRelations` and `SpecializationCycleRule` return every fault, with no implied duplicates (FR-003, NFR-002,
squad R1).

## Subtasks

- **T009** `ImpactRelations.check(composed)` returns `List<ClosureFault>`.
  - Each rule returns every match in edge order, and the rules run in the fixed order. `Violation` is deleted.
  - The packs named are the writer, then the places' packs, each as a `RefusalPack`, as `RegistrationImpact.refusal`
    builds them today.
- **T010** `cyclesIn(graph)`.
  - It returns every cycle once, from its first node, and a node on a reported cycle starts no new one. It stays
    iterative.
  - `ungroundedEnhancer` leaves out the places on a reported enhances cycle.
  - `overrideCycle` leaves out a self-loop already reported as `self_override`.
- **T011** `SpecializationCycleRule.check` returns every cycle as `ClosureFault`s.
- **T012** Tests:
  - US1.1: a self-override plus an enhances cycle gives exactly two faults, in order.
  - US1.2: one fault gives exactly one, with today's detail.
  - An A-B enhances cycle gives exactly one fault.
  - The census `CYCLE` fixture in `RegistrationImpactTest` stays at one fault.
  - A long chain still raises no stack overflow.

## Bridge

`RegistrationImpact` consumes these results until WP05. Bridge it by throwing `PackRefusals.of` over the closure's
list. Keep the bridge minimal, and record it with its rationale.

## Done when

- `./gradlew :packs:test` passes.

## Always

- Record each subtask with `spec-kitty agent tasks mark-status`.
- Tests carry only the canonical shape, except error-handling cases (invalid packs).
- No legacy type beside its replacement, and no wire code added, removed or renamed (C-001, C-006).
- Signed commits, with no model name.

## Reviewer guidance

Check each subtask against its acceptance scenario, check that single-fault answers are unchanged (SC-002), and check
that no ArchUnit rule is widened.
