---
work_package_id: WP05
title: 'validate --full: delegations, references, enhancement targets (#632 item 3, ruling R-s)'
dependencies:
- WP03
- WP11
- WP12
requirement_refs:
- FR-006
- NFR-002
- NFR-003
- SC-001
planning_base_branch: feat/pack-doctor-coverage
merge_target_branch: feat/pack-doctor-coverage
branch_strategy: Planning artifacts for this mission were generated on feat/pack-doctor-coverage. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-doctor-coverage unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-doctor-coverage-01M7YMQK
base_commit: 6058157c8df3e2e21936583d0f51bd1afc02319e
created_at: '2026-11-22T06:56:25.416594+00:00'
subtasks:
- T022
- T023
- T024
phase: Phase 2 - The rules
history:
- at: '2026-11-21T13:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
- at: '2026-11-21T22:30:00Z'
  actor: planner-priti
  action: Unheld by ruling R-s (DM-01M801NHF0BFAX05EA3F19KHV7) - option (a); specializes_from cycle dropped from this WP
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/set/ClosureWalkPhase.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/FullPassParityTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/EnhancementCompose.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCompositionResolverTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/EnhancementComposeTest.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/set/ClosureWalkPhase.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/FullPassParityTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP05 - validate --full: delegations, references, enhancement targets (ruling R-s)

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`AGENTS.md`, `.kittify/charter/charter.md`, ADR-0082, the mission `spec.md` (FR-006, US3), `plan.md` (**D5**, and D11
for the CI timing), `data-model.md` ("`--full` phase"), `research/squad-dispositions.md` and ruling R-s in
`decisions/` (`DM-01M801NHF0BFAX05EA3F19KHV7`).

## Objective

Ruling R-s (2026-11-21, the operator via the dispatcher: "1. as recommended", option (a) of plan D5):
"validate --full = delegations + references + enhancement targets only, each an existing domain function run per own
closure in registration's phase order, no new walk; registration/offering use the same collect-all forms."

`pack-doctor validate --full` adds one domain phase with exactly those three checks. The `specializes_from` cycle,
relations, edge-index and own-closure-walk checks are **not** this WP: they land in the factory with #636 slices 2-4
and 7 (WP08 records them in ADR-0083), and the PR writes `Refs #632`.

## Branch Strategy

- Planning branch and merge target: `feat/pack-doctor-coverage` (PR to `main`). Mission topic branch:
  `kitty/mission-pack-doctor-coverage-01M7YMQK`.
- The workspace comes from `lanes.json`. Implement with
  `spec-kitty agent action implement WP05 --agent claude --mission pack-doctor-coverage-01M7YMQK`.
- Commits: no model names and no Co-Authored-By trailer (repository rule). Never push to `main`.

## Context

- The three collect-all functions already exist and return every fault:
  - `PackCompositionResolver.unresolvedDelegations` (`PackCompositionResolver.java:634`): one `ClosureFault` per
    candidate, `pack_unreadable` with the violation `substep_delegation_unresolved`;
  - `PackCompositionResolver.referenceFaults` (`:582`): `pack_reference_unqualified`, `pack_reference_pack_missing`,
    `pack_reference_artifact_missing`;
  - `EnhancementCompose.targetFaults` (`EnhancementCompose.java:218`): `pack_reference_pack_missing`,
    `pack_reference_artifact_missing`.

  Read each first-fault caller (`OfferingReadService.judged` phases 2-4, `ClosureImpactCheck`, the
  `checkReferences`-style wrappers) and make sure each takes the first element of these forms, with no second body.
- The own closure is `PackCompositionResolver.compositionClosure` rooted at the pack, as WP03's phase uses it. Write
  no traversal (NFR-003).
- Registration's phase order is delegations, then references, then targets (plan D5).

## Subtasks

### T022 — One body per check
- Red first: a test per check that a pack with two faults of that check gets both from the collect-all form, and that
  the first-fault caller answers the first of them (where a caller still has its own loop, the test is red).
- Remove any duplicate first-fault body so each caller reads the collect-all form. The existing scenarios and
  `PackCompositionResolverTest`/`EnhancementComposeTest` expectations stay unchanged.
- Refresh the two comments in `PackCompositionResolverTest` that say an anti-pattern is "held only as a graph node"
  (`:479`, `:636`): after WP10 and WP11 it is held by its file (comment-only).

### T023 — `validate --full`
- `ClosureWalkPhase` (new, `pack-validation`): per pack, over its own closure, runs the three checks in order and
  collects every fault; `PackSetCheck` runs it after CLOSURE, only when CLOSURE has no violation, and only under
  `--full`.
- `PackSetVerdict.Check` gains one value for it. Its name is the operator's answer to analysis Q2 (recommended
  `REFERENCES`; not `CLOSURE_WALK`, which would name the deferred own-closure walk). If Q2 is unanswered when you
  start, use `REFERENCES` and list it in the review notes.
- `Arguments` gains `--full` (WP02's file; a justified out-of-map edit). `PackSetCheck` is WP03's file; the call is a
  justified out-of-map edit, listed.
- Fill the second timed CI step that WP02 left inert: `validate --round-trip --full` over the example packs within
  30 s (NFR-002; `.github/workflows/service.yml`, WP02's file, a justified out-of-map edit).

### T024 — Parity table (SC-001)
- `FullPassParityTest`: one parameterized table of planted faults: an unqualified reference, a missing referenced
  artifact, a missing target step, an unresolved delegation, plus WP03's two preset-item codes, WP04's
  `manifest_stale`, and WP10-WP12's anti-pattern refusals (a file missing `severity`, an inline block, a file-less
  node). It drives registration (the deployable harness) and `pack-doctor validate --full`, and asserts equal codes
  per row.
- A row with a `specializes_from` cycle asserts that registration refuses it and that `--full` does **not** report
  it yet, with a comment naming #636. When #636 lands it, the row flips.

## Test commands

```bash
./gradlew :packs:test :pack-validation:test :pack-doctor:test
./gradlew :deployable:test
```

## Definition of Done
- [ ] R-s quoted here (done) and in the PR body.
- [ ] One body per check; first-fault callers read the collect-all forms.
- [ ] `--full` runs the three checks per own closure, in registration's order; no new traversal.
- [ ] The parity table is green, with the cycle row documenting the remainder.
- [ ] The `--full` CI step is timed within 30 s.

## Stop conditions
- A check needs a walk that no existing domain function performs: stop. R-s rules no new walk.
- A first-fault caller's code or order changes on an existing scenario: stop and report.
- The `specializes_from` cycle check is asked of this WP: it is not (R-s); refer it to #636.
