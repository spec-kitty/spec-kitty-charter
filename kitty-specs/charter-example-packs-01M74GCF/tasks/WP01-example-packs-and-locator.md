---
work_package_id: WP01
title: Example packs and the ExamplePacks locator
dependencies: []
subtasks:
- T001
- T002
- T003
- T004
- T005
requirement_refs:
- FR-001
- NFR-002
- C-002
planning_base_branch: claude/adr-open-packs-456-458-adf2fo
merge_target_branch: claude/adr-open-packs-456-458-adf2fo
branch_strategy: single_branch
execution_mode: code_change
owned_files:
- service/packs/src/testFixtures/**
- service/packs/build.gradle.kts
- service/deployable/build.gradle.kts
authoritative_surface: service/packs/src/testFixtures/
create_intent:
- service/packs/src/testFixtures/java/ai/speckitty/charter/packs/examples/ExamplePacks.java
agent_profile: java-jenny
role: implementer
agent: claude
history:
- at: '2026-11-11T05:30:00Z'
  actor: claude
  action: created by /spec-kitty.tasks
---

# WP01 — Example packs and the ExamplePacks locator

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with `/spk-charter-profile-load java-jenny` before reading further, and load the action context with `spec-kitty charter context --action implement --json`.

## Objective

Create the three canonical example packs and the `ExamplePacks` locator every pack-reading test will use (ADR-0070 decision 1 and 2).

## Context

- ADR-0070 (`docs/adr/0070-charter-tests-own-example-packs-not-open-packs-content.md`), the plan doc (`docs/plans/charter-example-packs.md`), and this mission's `plan.md`, `research.md`, `data-model.md`.
- Canonical shapes only (charter companion, 2026-11-10): `role`/`alt-roles`, named principles, `fallback_profile`. Invalid shapes belong to the `planted/` error-handling packs.
- Red first: each moved assertion must fail against an incomplete example before it passes.

## Branch Strategy

Planning branch and final merge target: `claude/adr-open-packs-456-458-adf2fo` (PR #474). Topology `single_branch`: no lane worktree; the work happens in the repository root checkout, sequentially, as `lanes.json` records.

Implement with:

```bash
spec-kitty agent action implement WP01 --agent claude --mission charter-example-packs-01M74GCF
```

## Subtask T001 — Add the `java-test-fixtures` plugin to `:packs` and `testImplementation(testFixtures(project(":packs")))` to `:deployable`

- In `service/packs/build.gradle.kts` add `` `java-test-fixtures` `` to the plugins block; give the test-fixtures source
  set the same Checkstyle/Spotless/Error Prone treatment the test source set has (follow `build-logic` conventions).
- In `service/deployable/build.gradle.kts` add `testImplementation(testFixtures(project(":packs")))`.
- Verify: `./gradlew :packs:compileTestFixturesJava :deployable:compileTestJava` passes; no new artifact appears in
  `gradle/verification-metadata.xml` (the plugin ships with Gradle).

## Subtask T002 — Author the `built-in` example pack

Under `service/packs/src/testFixtures/resources/example-packs/built-in/`, canonical shapes only:
- `pack.yaml` (`schema_version: "1.0"`, built-in level, `mutability_policy: policy.yaml`, `fallback_profile: generic-agent`).
- `policy.yaml` locking `agent_profile:generic-agent`, a kind default and one `extendable` artifact.
- One file of every artifact kind at full shape: directive, tactic, styleguide (named principles), toolguide (+ guide
  companion), paradigm (with principles), procedure, agent profiles (`generic-agent` with `role: generic`, a
  `role`/`alt-roles` implementer, a reviewer), mission type with lifecycle and guard, mission step contract, step prompts
  with empty slots, a skill with body, a glossary pack, an asset (one binary payload), a template, presets.
- Graph shards (`*.graph.yaml`) carrying the edges the tests need (`requires`, `suggests`, `specializes_from`).
- A `pack-manifest.yaml` with hashes computed by the existing recipe (generate, then check in).
- Each file carries a one-line comment naming the rule or field it exercises.

## Subtask T003 — Author the `org-base` example pack

`example-packs/org-base/`: `pack_level: org`, `depends_on` built-in, `drg/fragment.yaml`, qualified references
(`built-in::directive:...`), a step enhancement with prompt fragments into a built-in step's slot, a specialist profile
`specializes_from` a built-in profile, a policy with `extendable` and `open` entries, an `org-charter.yaml`.

## Subtask T004 — Author the `project` example pack

`example-packs/project/`: `pack_level: project`, `depends_on` org-base, an `overrides` edge onto an open artifact of
org-base, and `fallback_profile` naming built-in's locked `generic-agent` (closure lookup, ADR-0068).

## Subtask T005 — Write `ExamplePacks` and its self-test

`ai.speckitty.charter.packs.examples.ExamplePacks` (test fixtures): `root()`, `packsDirectory()`, `packs()` (the
`PackLocation`s of the three packs, configured with their levels), `pack(String name)`. It copies nothing; it resolves
the classpath resource directory to a `Path`. A self-test in `:packs` loads each pack through the production store,
asserts zero violations and zero warnings, and composes the project pack's closure through the offering read.
Red first: the self-test fails before the packs are complete.

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
