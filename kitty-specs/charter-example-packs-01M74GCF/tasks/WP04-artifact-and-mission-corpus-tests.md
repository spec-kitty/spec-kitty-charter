---
work_package_id: WP04
title: Re-point the artifact, mission-type and prompt-slot tests
dependencies:
- WP01
subtasks:
- T013
- T014
- T015
- T016
requirement_refs:
- FR-004
planning_base_branch: claude/adr-open-packs-456-458-adf2fo
merge_target_branch: claude/adr-open-packs-456-458-adf2fo
branch_strategy: single_branch
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDoctrineCorpusRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/markdown/MarkdownCorpusTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ProcedureAndGuideCorpusTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GlossaryPackAndSkillCorpusTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GlossaryPackAndSkillSchemaTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MissionTypeCorpusTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MissionStepContractCorpusTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PromptSlotLoadChecksTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/markdown/PromptSlotsCanonicalCorpusTest.java
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/
create_intent: []
agent_profile: java-jenny
role: implementer
agent: claude
history:
- at: '2026-11-11T05:30:00Z'
  actor: claude
  action: created by /spec-kitty.tasks
---

# WP04 — Re-point the artifact, mission-type and prompt-slot tests

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with `/spk-charter-profile-load java-jenny` before reading further, and load the action context with `spec-kitty charter context --action implement --json`.

## Objective

Move the artifact, mission-type and prompt-slot corpus tests onto the example packs, asserting the same properties.

## Context

- ADR-0070 (`docs/adr/0070-charter-tests-own-example-packs-not-open-packs-content.md`), the plan doc (`docs/plans/charter-example-packs.md`), and this mission's `plan.md`, `research.md`, `data-model.md`.
- Canonical shapes only (charter companion, 2026-11-10): `role`/`alt-roles`, named principles, `fallback_profile`. Invalid shapes belong to the `planted/` error-handling packs.
- Red first: each moved assertion must fail against an incomplete example before it passes.

## Branch Strategy

Planning branch and final merge target: `claude/adr-open-packs-456-458-adf2fo` (PR #474). Topology `single_branch`: no lane worktree; the work happens in the repository root checkout, sequentially, as `lanes.json` records.

Implement with:

```bash
spec-kitty agent action implement WP04 --agent claude --mission charter-example-packs-01M74GCF
```

## Subtask T013 — Artifact round-trip tests

`PackDoctrineCorpusRoundTripTest` (rename `PackArtifactExamplesRoundTripTest`), `MarkdownCorpusTest`, `ProcedureAndGuideCorpusTest`, `GlossaryPackAndSkillCorpusTest`, `GlossaryPackAndSkillSchemaTest`: read `ExamplePacks.packsDirectory()`; replace named open-packs packs (`qa`, `internal`) by the example pack that holds the kind; assert the same property (every file round-trips / validates).

## Subtask T014 — Mission-type and step-contract tests

`MissionTypeCorpusTest`, `MissionStepContractCorpusTest`: assertions about real mission types (`software-dev`, counts, cave-kitty) become assertions about the example mission type; keep each rule's assertion (lifecycle states, guards, step ids).

## Subtask T015 — Prompt-slot tests

`PromptSlotLoadChecksTest`, `PromptSlotsCanonicalCorpusTest`: the built-in example's step prompts hold empty slots; the org-base enhancement fills them; zero prompt-slot findings under descriptor 1.0.

## Subtask T016 — Remove the `open-packs` tag from these classes

They now run in `:packs:test`. Run each class red-first against an incomplete example (a removed file) to prove it reads the examples, then green.

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
