---
work_package_id: WP10
title: Move the deployable and contract-suite tests off the synced trees
dependencies:
- WP01
requirement_refs:
- FR-004
- C-001
- C-003
planning_base_branch: claude/adr-open-packs-456-458-adf2fo
merge_target_branch: claude/adr-open-packs-456-458-adf2fo
branch_strategy: Planning artifacts for this mission were generated on claude/adr-open-packs-456-458-adf2fo. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into claude/adr-open-packs-456-458-adf2fo unless the human explicitly redirects the landing branch.
subtasks:
- T035
- T036
- T037
history:
- at: '2026-11-11T05:30:00Z'
  actor: claude
  action: created by /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/deployable/src/test/java/ai/speckitty/charter/deployable/
create_intent: []
execution_mode: code_change
owned_files:
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ConfigRootWiringTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/CharterWiringTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationListingFaultTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/RegularArtefactWiringTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ProblemResponsesTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/OperationCoverageTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/LinkChecksTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ContractConformanceTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP10 — Move the deployable and contract-suite tests off the synced trees

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with `/spk-charter-profile-load java-jenny` before reading further, and load the action context with `spec-kitty charter context --action implement --json`.

## Objective

Move the deployable and contract-suite tests that read the synced trees onto the example packs.

## Context

- ADR-0070 (`docs/adr/0070-charter-tests-own-example-packs-not-open-packs-content.md`), the plan doc (`docs/plans/charter-example-packs.md`), and this mission's `plan.md`, `research.md`, `data-model.md`.
- Canonical shapes only (charter companion, 2026-11-10): `role`/`alt-roles`, named principles, `fallback_profile`. Invalid shapes belong to the `planted/` error-handling packs.
- Red first: each moved assertion must fail against an incomplete example before it passes.

## Branch Strategy

Planning branch and final merge target: `claude/adr-open-packs-456-458-adf2fo` (PR #474). Topology `single_branch`: no lane worktree; the work happens in the repository root checkout, sequentially, as `lanes.json` records.

Implement with:

```bash
spec-kitty agent action implement WP10 --agent claude --mission charter-example-packs-01M74GCF
```

## Subtask T035 — Inventory the synced-tree reads

 For each class, what it reads from `packs/qa` / `packs/internal` (configured packs, wiring, link targets) and the rule it proves.

## Subtask T036 — Wiring tests

 `ConfigRootWiringTest`, `CharterWiringTest`, `ActivationListingFaultTest`, `RegularArtefactWiringTest`, `ProblemResponsesTest`: configure the service with the example packs (temp copies).

## Subtask T037 — Contract-suite wiring

 `bdd/ContractConformanceTest`, `bdd/OperationCoverageTest`, `bdd/LinkChecksTest`: the contract suite's configured packs come from the example packs; every scenario still passes. Scenarios that declare their own packs keep doing so.

## Definition of Done

- Each listed class reads `ExamplePacks` and no synced tree; the contract suite is configured with the example packs; per-class old/new assertion table.
- The contract scenarios still pass in full.
- The touched modules' tests pass; `spotlessCheck` and Checkstyle clean on touched sources.

## Risks

- A rewritten assertion that no longer proves its rule (a vacuous green). Reviewers check each moved test has a failing control.
- Example packs drifting towards what is easy to test; WP03's coverage check is the guard.

## Reviewer Guidance

- Compare each moved test's assertions with the original: same rule, now over owned examples.
- Reject any expectation-list entry or exclusion added to make a test pass.
