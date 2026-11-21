---
work_package_id: WP04
title: The closure walk
dependencies:
- WP03
requirement_refs:
- FR-001
- FR-005
planning_base_branch: feat/556-closure-checks-behind-ports
merge_target_branch: feat/556-closure-checks-behind-ports
branch_strategy: Planning artifacts for this mission were generated on feat/556-closure-checks-behind-ports. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/556-closure-checks-behind-ports unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-closure-checks-behind-ports-01M7TS43
base_commit: c9a2d76e7bdb7b50ea72596c141a239bf900f948
created_at: '2026-11-20T07:39:58.333019+00:00'
subtasks:
- T013
- T014
- T015
- T016
agent: claude
history:
- at: '2026-11-20T05:20:00Z'
  actor: claude::java-jenny
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClosureWalk.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClosureFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClosureWalk.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/OwnClosureWalk.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/OwnClosureWalkTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClosureFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClosureWalk.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/OwnClosureWalk.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureRoleFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ProfileEnhancers.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/FallbackProfileRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RolePinRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ProfileEnhancementRule.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/OwnClosureWalkTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureRoleFactsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/FallbackProfileImpactTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RolePinImpactTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ProfileEnhancementImpactTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP04 — The closure walk

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else, and apply its
initialization, boundaries and directives.

Plan: [plan.md](../plan.md). Squad dispositions: [research/squad-plan.md](../research/squad-plan.md). Spec:
[spec.md](../spec.md).

## Branch Strategy

Planning branch and final merge target: `feat/556-closure-checks-behind-ports`. Execution worktrees are allocated per computed lane from `lanes.json`.
Implement with `spec-kitty agent action implement WP04 --agent claude --mission closure-checks-behind-ports-01M7TS43`.

## Objective

`ClosureWalk` is a port over a domain view of one declaring pack's own closure. The ADR-0068 fallback, the role pins and
the profile enhancements report every breach (FR-001, FR-005, squad A1).

## Subtasks

- **T013 `ClosureFacts` (domain).** Do this first, with no behaviour change.
  - It is an interface of domain types only: `holds`, `winnerPackOf`, `isLocked`, the role facts `RolePinRule` reads,
    and the profile enhancers and winners `ProfileEnhancementRule` reads.
  - `ClosureView` implements it, reading `ClosureRoleFacts` and `ProfileEnhancers` itself (declared in review 1).
  - A closure whose layers do not merge has no enhancement fault, as before (declared in review 1).
- **T014** `FallbackProfileRule.check`, `RolePinRule.check` and `ProfileEnhancementRule.check` return
  `List<ClosureFault>`, and the three `Breach` records are deleted.
  - `fallback_profile_unknown` alone ends the fallback's list. Otherwise every one of `not_locked`, `not_role_neutral`
    and `not_agent` that holds is listed.
  - The detail is today's, with the closure named as `RegistrationImpact` names it.
- **T015 The `ClosureWalk` port** (`packs.domain`):
  - its subject is `OwnClosure(declaring, descriptor, ClosureFacts closure, Profiles profiles,
    Optional<ProfileEnhancementGateway> merge)`;
  - `ClosureVerdict walk(OwnClosure)` runs the fallback, then the pins, then the enhancements;
  - it exposes a `Rule<OwnClosure>` view.
  - `OwnClosureWalk` (application, `@Component`) implements it.
  - `Profiles` moves to the domain beside the port.
- **T016** Tests:
  - US2.1: not locked and not role-neutral gives two faults.
  - US2.2: unknown gives one fault.
  - US2.3: a pin fault comes after the fallback faults.
  - A closure whose layers do not merge gives no enhancement fault.
  - The existing impact tests keep their codes.

## Bridge

`RegistrationImpact.judgeFallbacksAndPins` calls the walk and throws `PackRefusals.of` until WP05.

## Done when

- `./gradlew :packs:test` and the ArchUnit test pass. The domain names no application type.

## Always

- Record each subtask with `spec-kitty agent tasks mark-status`.
- Tests carry only the canonical shape, except error-handling cases (invalid packs).
- No legacy type beside its replacement, and no wire code added, removed or renamed (C-001, C-006).
- Signed commits, with no model name.

## Reviewer guidance

Check each subtask against its acceptance scenario, check that single-fault answers are unchanged (SC-002), and check
that no ArchUnit rule is widened.
