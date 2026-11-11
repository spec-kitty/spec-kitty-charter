---
work_package_id: WP08
title: Governance amendments and the open-packs follow-up
dependencies: []
requirement_refs:
- FR-008
- C-001
- C-003
planning_base_branch: claude/adr-open-packs-456-458-adf2fo
merge_target_branch: claude/adr-open-packs-456-458-adf2fo
branch_strategy: Planning artifacts for this mission were generated on claude/adr-open-packs-456-458-adf2fo. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into claude/adr-open-packs-456-458-adf2fo unless the human explicitly redirects the landing branch.
subtasks:
- T028
- T029
- T031
- T030
history:
- at: '2026-11-11T05:30:00Z'
  actor: claude
  action: created by /spec-kitty.tasks
agent_profile: curator-carla
authoritative_surface: .kittify/charter/
create_intent: []
execution_mode: code_change
owned_files:
- .kittify/charter/charter.md
- .kittify/charter/charter.yaml
- .kittify/charter/interview/answers.yaml
- .kittify/charter/generated/**
- .kittify/charter/provenance/**
- .kittify/charter-packs/directive/002-risk-appetite-directive.directive.yaml
- docs/adr/0070-charter-tests-own-example-packs-not-open-packs-content.md
role: implementer
tags: []
tracker_refs: []
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

The open-packs clause: open-packs proves its YAML round-trips against a pinned charter release; charter proves round-trip fidelity on its own example packs. Edit the authored sources (`.kittify/charter/interview/answers.yaml` and `.kittify/charter-packs/directive/002-risk-appetite-directive.directive.yaml`), run `spec-kitty charter generate`, and reject every generated change outside these clauses; keep the companion's operator amendment lines, which the generator does not reproduce.

## Subtask T031 — Record the issue link

Add the open-packs issue link to ADR-0070's References and to the WP review.

## Subtask T030 — File the open-packs validation-job issue

In spec-kitty-open-packs: a CI job validating every pack against a pinned charter release (ADR-0070 decision 3), linking ADR-0070 and PR #474.

## Definition of Done

- Only the two clauses (gates; PROJECT_002) and the amendment line change in the generated diff; anything else is rejected.
- The open-packs validation-job issue exists and its link is recorded in ADR-0070 and the WP review.
- The touched modules' tests pass; `spotlessCheck` and Checkstyle clean on touched sources.

## Risks

- A rewritten assertion that no longer proves its rule (a vacuous green). Reviewers check each moved test has a failing control.
- Example packs drifting towards what is easy to test; WP03's coverage check is the guard.

## Reviewer Guidance

- Compare each moved test's assertions with the original: same rule, now over owned examples.
- Reject any expectation-list entry or exclusion added to make a test pass.
