---
work_package_id: WP07
title: Contract, scenarios and decision records
dependencies:
- WP06
requirement_refs:
- FR-012
- FR-013
- FR-014
- NFR-003
- C-004
- C-005
- SC-001
- SC-003
planning_base_branch: feat/556-closure-checks-behind-ports
merge_target_branch: feat/556-closure-checks-behind-ports
branch_strategy: Planning artifacts for this mission were generated on feat/556-closure-checks-behind-ports. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/556-closure-checks-behind-ports unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-closure-checks-behind-ports-01M7TS43
base_commit: d13f3d1367bf571a0dc433dc40706dd47ce0f420
created_at: '2026-11-20T10:44:10.840616+00:00'
subtasks:
- T027
- T028
- T029
- T030
agent: claude
history:
- at: '2026-11-20T05:20:00Z'
  actor: claude::java-jenny
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/scenarios/registration/closure-faults.feature
create_intent:
- contracts/charter/scenarios/registration/closure-faults.feature
- contracts/charter/changes/2026-11-20-closure-refusals-name-every-fault.md
execution_mode: code_change
owned_files:
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/changes/2026-11-20-closure-refusals-name-every-fault.md
- contracts/charter/scenarios/registration/closure-faults.feature
- docs/adr/0068-a-pack-declares-its-fallback-profile.md
- docs/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check.md
- docs/adr/0081-pack-uninstall-and-the-version-order.md
role: implementer
tags: []
tracker_refs: []
---

# WP07 — Contract, scenarios and decision records

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else, and apply its
initialization, boundaries and directives.

Plan: [plan.md](../plan.md). Squad dispositions: [research/squad-plan.md](../research/squad-plan.md). Spec:
[spec.md](../spec.md).

## Branch Strategy

Planning branch and final merge target: `feat/556-closure-checks-behind-ports`. Execution worktrees are allocated per computed lane from `lanes.json`.
Implement with `spec-kitty agent action implement WP07 --agent claude --mission closure-checks-behind-ports-01M7TS43`.

## Objective

Record the change in the contract and the ADRs, and prove it end to end (FR-012 to FR-014).

## Subtasks

- **T027 The contract.**
  - The `CharterRefusalCode.yaml` descriptions of the closure, reference, fallback, role-pin, enhancement and
    `governance_policy_violation` codes say that a refusal's violations name every fault, with a header per fault when
    there are several.
  - A change fragment under `### Changed` (additive, D1), in the format of the existing fragments.
- **T028 `closure-faults.feature`** holds `@contract` scenarios on charter's own example packs, uploaded inline, as
  `whole-pack-registration.feature` does:
  - US1.1, US1.2, US2.1, US2.2 and US4.1;
  - each asserts `/problem/code`, the violation count and each `/problem/violations/k/code`.
  - It must pass the ADR-0059 D1 gate.
- **T029 The ADRs.**
  - **ADR-0068:** an amendment saying where the rule runs (behind `ClosureWalk`) and that it reports every breach.
  - **ADR-0075:** a slice-7 amendment, dated 2026-11-20, recording:
    - the three ports, their subjects and their consumers (squad A4);
    - the closure edge's mapper `PackRefusals.of(ClosureVerdict)` beside the two the 2026-11-16 amendment names;
    - the S1 ambiguity, flagged;
    - spec A1 (no floor rule);
    - that `GraphWalk` and `Reachability` are not needed;
    - the activations gate staying first-fault (R10);
    - D6 (parity in open-packs' `charter_service_check.py`);
    - the deferred `CompositionFault` unification (#614).
    Update the front matter's `decision_status` and `updated`.
  - **ADR-0081:** a note that `ImpactCheck` absorbed `PackDependents`.
- **T030 The gates.**
  - `./gradlew verifyAll` on Java 25. The mutation floors are judged by the weekly or dispatched run, never in-mission (NFR-003).
  - The docs checks: `check_docs.py`, markdownlint-cli2 and cspell.

## Done when

- Everything above is green, and the PR body lists the issues: `Closes #556`, with the parity note.

## Always

- Record each subtask with `spec-kitty agent tasks mark-status`.
- Tests carry only the canonical shape, except error-handling cases (invalid packs).
- No legacy type beside its replacement, and no wire code added, removed or renamed (C-001, C-006).
- Signed commits, with no model name.

## Reviewer guidance

Check each subtask against its acceptance scenario, check that single-fault answers are unchanged (SC-002), and check
that no ArchUnit rule is widened.
