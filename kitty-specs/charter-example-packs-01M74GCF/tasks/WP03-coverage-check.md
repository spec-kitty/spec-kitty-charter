---
work_package_id: WP03
title: Coverage check over the example packs
dependencies:
- WP01
subtasks:
- T010
- T011
- T012
requirement_refs:
- FR-003
- SC-003
planning_base_branch: claude/adr-open-packs-456-458-adf2fo
merge_target_branch: claude/adr-open-packs-456-458-adf2fo
branch_strategy: single_branch
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/examples/**
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ExampleCoverageTest.java
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/examples/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/examples/ExamplePackCoverageTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ExampleCoverageTest.java
agent_profile: java-jenny
role: implementer
agent: claude
history:
- at: '2026-11-11T05:30:00Z'
  actor: claude
  action: created by /spec-kitty.tasks
---

# WP03 — Coverage check over the example packs

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with `/spk-charter-profile-load java-jenny` before reading further, and load the action context with `spec-kitty charter context --action implement --json`.

## Objective

Fail the build when a pack schema property, an artifact kind or a raised refusal code has no example.

## Context

- ADR-0070 (`docs/adr/0070-charter-tests-own-example-packs-not-open-packs-content.md`), the plan doc (`docs/plans/charter-example-packs.md`), and this mission's `plan.md`, `research.md`, `data-model.md`.
- Canonical shapes only (charter companion, 2026-11-10): `role`/`alt-roles`, named principles, `fallback_profile`. Invalid shapes belong to the `planted/` error-handling packs.
- Red first: each moved assertion must fail against an incomplete example before it passes.

## Branch Strategy

Planning branch and final merge target: `claude/adr-open-packs-456-458-adf2fo` (PR #474). Topology `single_branch`: no lane worktree; the work happens in the repository root checkout, sequentially, as `lanes.json` records.

Implement with:

```bash
spec-kitty agent action implement WP03 --agent claude --mission charter-example-packs-01M74GCF
```

## Subtask T010 — Schema-property coverage

For every bundled pack schema (`PackSchemas.bundled().kinds()`), walk its declared properties (nested `$defs`
included) and assert at least one example file sets each one. Failure message: `<kind>: <json pointer> has no example`.
Pair with a positive control: a fixture schema with an unset property fails.

## Subtask T011 — Artifact-kind coverage

Every `ArtifactKind` with a pack file kind has at least one example file across the three packs.

## Subtask T012 — Refusal-code coverage

In `:deployable`: every code marked `raised: true` in `CharterRefusalCode.yaml` is either produced by an example
pack (a test that loads a mutated copy) or named by a contract scenario's expected `problem code` / violation code.
Read the scenarios the way `errors.feature` does. A code covered by neither fails, naming it.

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
