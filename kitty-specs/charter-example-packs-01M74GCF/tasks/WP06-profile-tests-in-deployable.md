---
work_package_id: WP06
title: Re-point the profile tests in deployable
dependencies:
- WP01
subtasks:
- T021
- T022
- T023
requirement_refs:
- FR-004
planning_base_branch: claude/adr-open-packs-456-458-adf2fo
merge_target_branch: claude/adr-open-packs-456-458-adf2fo
branch_strategy: single_branch
execution_mode: code_change
owned_files:
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ProfileCorpusTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ProfileExamplesEdgeTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/PackGraphEdges.java
- contracts/charter/examples/Artifact.agent_profile.yaml
- contracts/charter/examples/EffectiveAgentProfileRead.java-jenny.yaml
authoritative_surface: service/deployable/src/test/java/ai/speckitty/charter/deployable/
create_intent: []
agent_profile: java-jenny
role: implementer
agent: claude
history:
- at: '2026-11-11T05:30:00Z'
  actor: claude
  action: created by /spec-kitty.tasks
---

# WP06 — Re-point the profile tests in deployable

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with `/spk-charter-profile-load java-jenny` before reading further, and load the action context with `spec-kitty charter context --action implement --json`.

## Objective

Move the deployable profile tests onto the example packs and align the two contract profile examples.

## Context

- ADR-0070 (`docs/adr/0070-charter-tests-own-example-packs-not-open-packs-content.md`), the plan doc (`docs/plans/charter-example-packs.md`), and this mission's `plan.md`, `research.md`, `data-model.md`.
- Canonical shapes only (charter companion, 2026-11-10): `role`/`alt-roles`, named principles, `fallback_profile`. Invalid shapes belong to the `planted/` error-handling packs.
- Red first: each moved assertion must fail against an incomplete example before it passes.

## Branch Strategy

Planning branch and final merge target: `claude/adr-open-packs-456-458-adf2fo` (PR #474). Topology `single_branch`: no lane worktree; the work happens in the repository root checkout, sequentially, as `lanes.json` records.

Implement with:

```bash
spec-kitty agent action implement WP06 --agent claude --mission charter-example-packs-01M74GCF
```

## Subtask T021 — `ProfileCorpusTest` over the example packs

Every example profile is read through the profile source and its body means what its YAML means; the `java-jenny` cases become the example implementer profile (governance, selection under the charter).

## Subtask T022 — `ProfileExamplesEdgeTest` and `PackGraphEdges`

The two contract examples are aligned with an example profile and its built-in graph edges; the test checks each governance entry's `edge` against the example pack's graph files.

## Subtask T023 — Contract examples

Update `Artifact.agent_profile.yaml` and `EffectiveAgentProfileRead.java-jenny.yaml` to the example profile they now mirror (rename the second if its name no longer fits; update references in the contract).

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
