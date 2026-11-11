---
work_package_id: WP07
title: Build and CI without open-packs; nightly drift watch
dependencies:
- WP02
- WP03
- WP04
- WP05
- WP06
- WP09
- WP10
requirement_refs:
- FR-005
- FR-006
- FR-007
- NFR-001
- NFR-003
- C-003
- SC-001
- SC-002
- C-001
planning_base_branch: claude/adr-open-packs-456-458-adf2fo
merge_target_branch: claude/adr-open-packs-456-458-adf2fo
branch_strategy: Planning artifacts for this mission were generated on claude/adr-open-packs-456-458-adf2fo. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into claude/adr-open-packs-456-458-adf2fo unless the human explicitly redirects the landing branch.
subtasks:
- T024
- T025
- T026
- T027
history:
- at: '2026-11-11T05:30:00Z'
  actor: claude
  action: created by /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: .github/workflows/
create_intent:
- .github/workflows/open-packs-drift.yml
- service/packs/src/driftTest/java/ai/speckitty/charter/packs/drift/OpenPacksDriftTest.java
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/OpenPacksCorpus.java
- build-logic/src/main/kotlin/OpenPacksCheckout.kt
- build-logic/src/test/kotlin/OpenPacksCheckoutTest.kt
- build-logic/src/test/kotlin/SonarWorkflowGuardTest.kt
- build-logic/src/test/kotlin/ServiceReleaseGateTest.kt
- .github/workflows/ci.yml
- service/packs/src/driftTest/**
- .github/workflows/whole-pack.yml
- .github/workflows/service.yml
- .github/workflows/timing-nightly.yml
- .github/workflows/mutation-nightly.yml
- .github/workflows/sonar.yml
- .github/workflows/service-release.yml
- .github/workflows/open-packs-drift.yml
role: implementer
tags: []
tracker_refs: []
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

## Subtask T024 — Precondition and build files


Precondition: the inventory grep (Definition of Done) finds nothing. Then in `service/packs/build.gradle.kts` and `service/deployable/build.gradle.kts` (owned by WP01; edited here after it, recorded as an out-of-map edit): delete `openPacksRoundTrip`, `openPacksProfiles`, `tasks.check { dependsOn(openPacksProfiles) }`, the `open-packs` tag split, and the `openPacksDir`/`openPacksRef` inputs and `charter.openPacksDir`/`charter.openPacksRef` system properties on every test task, the default `test` included. Delete `OpenPacksCorpus`. Keep `OpenPacksCheckout` only if `syncPacks` or the drift job uses it; otherwise delete it and its test.

## Subtask T025 — Workflows and their guard tests


Keep the pinned open-packs checkout only where `syncPacks` runs (`service.yml`, `sonar.yml`, `mutation-nightly.yml`, `timing-nightly.yml`) and drop `-PopenPacksDir` from every Gradle test invocation. Rewrite `if:` conditions that key on the checkout step (e.g. `sonar.yml`) rather than deleting the step. Rewrite the `whole-pack.yml` header and drop its open-packs checkout. Update `ci.yml` path routers and keep `open-packs-drift.yml` out of them. Update the build-logic guard tests that pin workflow shapes (`SonarWorkflowGuardTest.kt`, `ServiceReleaseGateTest.kt`, others found by grep).

## Subtask T026 — Nightly drift watch


`.github/workflows/open-packs-drift.yml`: `schedule` + `workflow_dispatch` only, `permissions: issues: write`, a `concurrency` group. It checks out open-packs `main` and runs a `driftTest` source set of `:packs` (excluded from `check` and `verifyAll`) that round-trips every open-packs pack through the production store. On a test failure it opens or updates one issue labelled `open-packs-drift` (reuse the de-duplication pattern of `mutation-nightly.yml`); a build failure is reported as such, not as drift.

## Subtask T027 — Prove green without open-packs test input


(a) In a clean worktree: `syncPacks` from the pinned checkout, then `./gradlew verifyAll` with no `-PopenPacksDir`: green. (b) CI on PR #474: every required check green. Record both.

## Definition of Done

- Precondition before T025: `grep -rE "OpenPacksCorpus|charter.openPacksDir|packs/(qa|internal)" service/*/src/test` finds nothing.
- Neither build file wires `charter.openPacksDir`/`openPacksRef` into any test task, the default `test` included.
- `open-packs-drift.yml` has no `pull_request` or `push` trigger; `driftTest` is excluded from `check`/`verifyAll`.
- `syncPacks -PcheckPacks=true` passes after `syncPacks` from the pinned checkout (FR-007 control).
- Proof recorded: (a) a clean worktree with packs synced and no `-PopenPacksDir`: `verifyAll` green; (b) CI on PR #474 green.
- The touched modules' tests pass; `spotlessCheck` and Checkstyle clean on touched sources.

## Risks

- A rewritten assertion that no longer proves its rule (a vacuous green). Reviewers check each moved test has a failing control.
- Example packs drifting towards what is easy to test; WP03's coverage check is the guard.

## Reviewer Guidance

- Compare each moved test's assertions with the original: same rule, now over owned examples.
- Reject any expectation-list entry or exclusion added to make a test pass.
