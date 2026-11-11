---
work_package_id: WP02
title: Whole-pack round trip over the example packs
dependencies:
- WP01
subtasks:
- T006
- T007
- T008
- T009
requirement_refs:
- FR-002
planning_base_branch: claude/adr-open-packs-456-458-adf2fo
merge_target_branch: claude/adr-open-packs-456-458-adf2fo
branch_strategy: single_branch
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackOpenPacksRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectation.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackReport.java
- service/packs/src/test/resources/whole-pack/**
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePack
create_intent: []
agent_profile: java-jenny
role: implementer
agent: claude
history:
- at: '2026-11-11T05:30:00Z'
  actor: claude
  action: created by /spec-kitty.tasks
---

# WP02 — Whole-pack round trip over the example packs

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with `/spk-charter-profile-load java-jenny` before reading further, and load the action context with `spec-kitty charter context --action implement --json`.

## Objective

Run the whole-pack round-trip gate over the example packs, with empty expectation lists.

## Context

- ADR-0070 (`docs/adr/0070-charter-tests-own-example-packs-not-open-packs-content.md`), the plan doc (`docs/plans/charter-example-packs.md`), and this mission's `plan.md`, `research.md`, `data-model.md`.
- Canonical shapes only (charter companion, 2026-11-10): `role`/`alt-roles`, named principles, `fallback_profile`. Invalid shapes belong to the `planted/` error-handling packs.
- Red first: each moved assertion must fail against an incomplete example before it passes.

## Branch Strategy

Planning branch and final merge target: `claude/adr-open-packs-456-458-adf2fo` (PR #474). Topology `single_branch`: no lane worktree; the work happens in the repository root checkout, sequentially, as `lanes.json` records.

Implement with:

```bash
spec-kitty agent action implement WP02 --agent claude --mission charter-example-packs-01M74GCF
```

## Subtask T006 — Point `WholePackRoundTripTest` at `ExamplePacks.pack("built-in")`

Replace every `OpenPacksCorpus` call; drop `assertPinned`. Keep the two-way ratchet and the report. Run it: every
modelled file of the built-in example round-trips with its meaning (ADR-0036).

## Subtask T007 — Point `WholePackOpenPacksRoundTripTest` at all three example packs

Rename the class `WholePackExamplesRoundTripTest` (it no longer reads open-packs). Every example pack loads with no
known failure and round-trips whole, assets, presets and companions included.

## Subtask T008 — Empty the expectation lists

`whole-pack/expected-red.yaml` (`kinds`, `files`, `empty_kinds`), `known-failures.yaml`, `exclusions.yaml`: the
examples carry every kind, so each list is empty or carries only a documented exclusion. A non-empty `empty_kinds` means
WP01 missed a kind: fix the example, never the list. `WholePackExpectationTest` keeps proving the ratchet with its own
fixtures.

## Subtask T009 — Keep the `whole-pack` tag and the `wholePackRoundTrip` task

The task keeps its tag filter; its `openPacksDir` input and `openPacksRef` property are removed in WP07, so here
only the test sources change. Verify `./gradlew :packs:wholePackRoundTrip -PopenPacksDir=/nonexistent` still passes
once WP07 lands (record the command in the WP review).

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
