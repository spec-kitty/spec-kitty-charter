---
work_package_id: WP01
title: The fault model and the closure edge's mapper
dependencies: []
requirement_refs:
- FR-008
- C-006
- SC-002
planning_base_branch: feat/556-closure-checks-behind-ports
merge_target_branch: feat/556-closure-checks-behind-ports
branch_strategy: Planning artifacts for this mission were generated on feat/556-closure-checks-behind-ports. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/556-closure-checks-behind-ports unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-closure-checks-behind-ports-01M7TS43
base_commit: 58576876630241403b20c64f286d8820adf208d5
created_at: '2026-11-20T05:13:27.716447+00:00'
subtasks:
- T001
- T002
- T003
- T004
agent: claude
history:
- at: '2026-11-20T05:20:00Z'
  actor: claude::java-jenny
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClosureFault.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClosureFault.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClosureVerdict.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ClosureFaultTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ClosureVerdictTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClosureFault.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClosureVerdict.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCodes.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRefusals.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ClosureFaultTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ClosureVerdictTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCodesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRefusalsTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP01 — The fault model and the closure edge's mapper

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else, and apply its
initialization, boundaries and directives.

Plan: [plan.md](../plan.md). Squad dispositions: [research/squad-plan.md](../research/squad-plan.md). Spec:
[spec.md](../spec.md).

## Branch Strategy

Planning branch and final merge target: `feat/556-closure-checks-behind-ports`. Execution worktrees are allocated per computed lane from `lanes.json`.
Implement with `spec-kitty agent action implement WP01 --agent claude --mission closure-checks-behind-ports-01M7TS43`.

## Objective

One fault type and one mapper, which every later package uses (plan "The fault model"; squad P2 to P7).

## Subtasks

- **T001 `ClosureFault`** (`packs.domain`).
  - A record of `Reason reason`, `List<RefusalPack> packs`, `List<RefusalViolation> violations` and
    `List<RequiringPack> requiredBy`, each list copied.
  - `code()` returns the `RefusalCode` whose token is `reason.code().value()`.
  - It has three lifts, and each keeps the source's detail and packs:
    - `of(PackCompositionResolver.Refusal)`;
    - `of(CharterRefusal)`, which also keeps its violations and `requiredBy`;
    - `of(CompositionRules.CompositionFault)` (#614 unifies this lift away later).
  - The constructor refuses a code outside `PackCodes.closure`'s set.
  - It never lifts an unrewritten `pack_unreadable` (squad A2). `of(CharterRefusal)` throws `IllegalArgumentException`
    for one, and the callers rethrow it before they lift.
- **T002 `PackCodes.closure(RefusalCode)`.**
  - It looks up one `ReasonCode` per code of a closed set, built once in `PackCodes`, so
    `PACK_CODES_HAVE_ONE_OWNER` holds.
  - The set is:
    - the `CLOSURE` codes;
    - the reference codes and the case-only match;
    - `pack_unreadable` as the substep delegation raises it, and `validation_failed`;
    - the step enhancement target codes;
    - `self_override`, `conflicting_relations`, `override_not_permitted`, `override_cycle`, `enhances_cycle` and
      `specialization_cycle`;
    - the four `fallback_profile_*` codes, and the role pin and profile enhancement codes;
    - `governance_policy_violation` and `pack_has_dependents`.
  - Take the exact list from the `RefusalCode`s raised by `RegistrationImpact`, `ImpactRelations`, the walk rules,
    `PackMutabilityPolicy`, `ClosurePackDependents` and `OfferingReadService.composed`, and grep them.
  - A parameterized test maps every member, and refuses one outside the set.
- **T003 `ClosureVerdict`**: `faults`, `rationale()` (the reasons, in order), `isPassing()`, and `and(ClosureVerdict)`.
  It holds no refusal mapping (squad P5).
- **T004 `PackRefusals.of(ClosureVerdict)`**, public. It throws `IllegalArgumentException` on a passing verdict.
  - **One fault** is answered as `new CharterRefusal`/`withPacksAndViolations`/`withRequiredBy` would answer it today,
    with every field byte-identical (SC-002).
  - **Two or more faults:**
    - the code is the first fault's;
    - the violations are, per fault, a header `("", code, detail)` followed by its own violations;
    - the packs are the union, in first-seen order;
    - `requiredBy` is the union, in first-seen order;
    - the detail is `<first detail> (N fault(s); every one is in violations)`.
  - The 100-item cap stays `CharterRefusal`'s.
  - Check whether `CharterRefusal` has a factory that sets packs, violations and `requiredBy` together. If not, add
    the smallest one in the kernel, as an out-of-map edit recorded with its rationale.

## Done when

- The unit tests cover:
  - one fault of each shape (plain, with violations, with `requiredBy`), equal to today's refusal;
  - two and three faults, by D1;
  - the closed code set.
- `./gradlew :packs:test :deployable:test --tests '*HexagonalArchitecture*'` passes.

## Always

- Record each subtask with `spec-kitty agent tasks mark-status`.
- Tests carry only the canonical shape, except error-handling cases (invalid packs).
- No legacy type beside its replacement, and no wire code added, removed or renamed (C-001, C-006).
- Signed commits, with no model name.

## Reviewer guidance

Check each subtask against its acceptance scenario, check that single-fault answers are unchanged (SC-002), and check
that no ArchUnit rule is widened.
