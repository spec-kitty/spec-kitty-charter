---
work_package_id: WP07
title: Build and CI without open-packs; nightly drift watch
dependencies:
- WP02
- WP03
- WP04
- WP05
- WP06
subtasks:
- T024
- T025
- T026
- T027
requirement_refs:
- FR-005
- FR-006
- FR-007
- NFR-001
- C-003
planning_base_branch: claude/adr-open-packs-456-458-adf2fo
merge_target_branch: claude/adr-open-packs-456-458-adf2fo
branch_strategy: single_branch
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/OpenPacksCorpus.java
- build-logic/src/main/kotlin/OpenPacksCheckout.kt
- build-logic/src/test/kotlin/OpenPacksCheckoutTest.kt
- .github/workflows/whole-pack.yml
- .github/workflows/service.yml
- .github/workflows/timing-nightly.yml
- .github/workflows/mutation-nightly.yml
- .github/workflows/sonar.yml
- .github/workflows/service-release.yml
- .github/workflows/open-packs-drift.yml
authoritative_surface: .github/workflows/
create_intent:
- .github/workflows/open-packs-drift.yml
agent_profile: java-jenny
role: implementer
agent: claude
history:
- at: '2026-11-11T05:30:00Z'
  actor: claude
  action: created by /spec-kitty.tasks
---

# WP07 — Build and CI without open-packs; nightly drift watch

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with `/spk-charter-profile-load java-jenny` before reading further, and load the action context with `spec-kitty charter context --action implement --json`.

## Objective

Remove every open-packs input from required tasks and CI, keep the governance sync, and add the nightly drift watch.

## Context

- ADR-0070 (`docs/adr/0070-charter-tests-own-example-packs-not-open-packs-content.md`), the plan doc (`docs/plans/charter-example-packs.md`), and this mission's `plan.md`, `research.md`, `data-model.md`.
- Canonical shapes only (charter companion, 2026-11-10): `role`/`alt-roles`, named principles, `fallback_profile`. Invalid shapes belong to the `planted/` error-handling packs.
- Red first: each moved assertion must fail against an incomplete example before it passes.

## Branch Strategy

Planning branch and final merge target: `claude/adr-open-packs-456-458-adf2fo` (PR #474). Topology `single_branch`: no lane worktree; the work happens in the repository root checkout, sequentially, as `lanes.json` records.

Implement with:

```bash
spec-kitty agent action implement WP07 --agent claude --mission charter-example-packs-01M74GCF
```

## Subtask T024 — Remove the corpus tasks and inputs

In `service/packs/build.gradle.kts` and `service/deployable/build.gradle.kts` (edited by WP01 first; this WP edits after it): delete `openPacksRoundTrip`, `openPacksProfiles`, the `open-packs` tag split, `openPacksDir`/`openPacksRef` test inputs and `charter.openPacksDir`/`charter.openPacksRef` system properties. Delete `OpenPacksCorpus` and, if nothing else uses it, `OpenPacksCheckout` and its test. `syncPacks` and `openPacksRef` in `gradle.properties` stay (FR-007).

## Subtask T025 — Workflows

Remove the open-packs checkout and `-PopenPacksDir` from `whole-pack.yml`, `service.yml`, `timing-nightly.yml`, `mutation-nightly.yml`, `sonar.yml`; keep `syncPacks -PcheckPacks=true` where a job checks the synced governance trees. Update `service-release.yml`'s comment. Update any `build-logic` workflow guard tests and the `ci.yml` path router if it names the removed paths.

## Subtask T026 — Nightly drift watch

New `.github/workflows/open-packs-drift.yml`: `schedule` + `workflow_dispatch` only; checks out open-packs `main`, runs the round trip over its built-in pack with a one-off test task reading it (kept under `service/packs/src/driftTest/` or a system property read only in this job), and on failure opens or updates an issue labelled `open-packs-drift`. Never a required check.

## Subtask T027 — Prove green without open-packs

Run `./gradlew verifyAll` with no open-packs checkout present and record the result. Run `syncPacks -PcheckPacks=true`.

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
