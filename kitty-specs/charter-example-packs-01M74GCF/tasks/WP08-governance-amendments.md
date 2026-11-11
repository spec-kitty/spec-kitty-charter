---
work_package_id: WP08
title: Governance amendments and the open-packs follow-up
dependencies: []
subtasks:
- T028
- T029
- T030
requirement_refs:
- FR-008
planning_base_branch: claude/adr-open-packs-456-458-adf2fo
merge_target_branch: claude/adr-open-packs-456-458-adf2fo
branch_strategy: single_branch
execution_mode: code_change
owned_files:
- .kittify/charter/charter.md
- .kittify/charter/charter.yaml
- .kittify/charter/generated/directives/002-risk-appetite-directive.directive.yaml
- .kittify/charter-packs/directive/002-risk-appetite-directive.directive.yaml
authoritative_surface: .kittify/charter/
create_intent: []
agent_profile: curator-carla
role: implementer
agent: claude
history:
- at: '2026-11-11T05:30:00Z'
  actor: claude
  action: created by /spec-kitty.tasks
---

# WP08 — Governance amendments and the open-packs follow-up

## ⚡ Do This First: Load Agent Profile

Load the `curator-carla` profile with `/spk-charter-profile-load curator-carla` before reading further, and load the action context with `spec-kitty charter context --action implement --json`.

## Objective

Amend the governance texts ADR-0070 changes and file the open-packs validation-job follow-up.

## Context

- ADR-0070 (`docs/adr/0070-charter-tests-own-example-packs-not-open-packs-content.md`), the plan doc (`docs/plans/charter-example-packs.md`), and this mission's `plan.md`, `research.md`, `data-model.md`.
- Canonical shapes only (charter companion, 2026-11-10): `role`/`alt-roles`, named principles, `fallback_profile`. Invalid shapes belong to the `planted/` error-handling packs.
- Red first: each moved assertion must fail against an incomplete example before it passes.

## Branch Strategy

Planning branch and final merge target: `claude/adr-open-packs-456-458-adf2fo` (PR #474). Topology `single_branch`: no lane worktree; the work happens in the repository root checkout, sequentially, as `lanes.json` records.

Implement with:

```bash
spec-kitty agent action implement WP08 --agent claude --mission charter-example-packs-01M74GCF
```

## Subtask T028 — Amend the charter companion

"Gates before merge": the whole-pack round trip runs over charter's example packs (ADR-0070). The intro's amendment list gains 2026-11-11.

## Subtask T029 — Amend PROJECT_002

The open-packs clause: open-packs proves its YAML round-trips against a pinned charter release; charter proves round-trip fidelity on its own example packs. Edit through the charter workflow (`spec-kitty charter` interview/generate), then review the generated diff so only these clauses change.

## Subtask T030 — File the open-packs validation-job issue

In spec-kitty-open-packs: a CI job validating every pack against a pinned charter release (ADR-0070 decision 3), linking ADR-0070 and PR #474.

## Definition of Done

- Every subtask's validation holds; the touched modules' tests pass (`./gradlew :packs:test :deployable:test` as relevant).
- `spotlessCheck` and Checkstyle clean on touched sources.
- No test reads open-packs content (except the WP07 drift job).

## Risks

- A rewritten assertion that no longer proves its rule (a vacuous green). Reviewers check each moved test has a failing control.
- Example packs drifting towards what is easy to test; WP03's coverage check is the guard.

## Reviewer Guidance

- Compare each moved test's assertions with the original: same rule, now over owned examples.
- Reject any expectation-list entry or exclusion added to make a test pass.
