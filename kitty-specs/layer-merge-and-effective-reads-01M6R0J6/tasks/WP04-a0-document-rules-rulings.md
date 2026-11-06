---
work_package_id: WP04
title: 'A0 · Document rules: remnant refusal, replace round trip, pins dropped'
dependencies:
- WP03
requirement_refs:
- FR-026
- FR-031
- FR-041
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-layer-merge-and-effective-reads-01M6R0J6
base_commit: 74426123e5931290466cde7cf3341881d7db08fe
created_at: '2026-11-06T16:32:26.252128+00:00'
subtasks:
- T015
- T016
- T017
- T018
- T019
phase: PR A0
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/domain/DocumentRules.java
create_intent: []
execution_mode: code_change
model: ''
owned_files:
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/DocumentRules.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/StaleIds.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ActivationChange.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationWrites.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/DocumentRulesTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/StaleIdsTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationServiceTest.java
role: implementer
tags: []
tracker_refs: []
---
# WP04: A0 · Document rules: remnant refusal, replace round trip, pins dropped

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Apply three rulings to the document write rules that #399 ships:
- **A4 (FR-031):** a full replace judges the floor only on the deny entries it **adds** (`Den(D') \ Den(D)`), and
  drops ids the packs no longer hold, reporting them in `dropped`, so a GET then PUT never fails without a pack change.
- **B1 (FR-026, write half):** a write drops a version pin no pack satisfies, reported in `dropped`, instead of
  `422 version_not_held`.
- **A7 Q4 (FR-041):** the domain refuses nothing about `activatedKinds`/`missionTypes` (they no longer exist in the
  document); the `422` is produced at the API boundary (WP08). Here, remove the kind-gate checks and the mission-type
  branches from the write rules.

Implementation command:
`spec-kitty agent action implement WP04 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A0 (own PR, stacked on #399 `issue-291-activation-writes`, before PR A; decision DM-01M6RFGB9NVD9MDWZ6AP0PBYH7).
- **Concern:** IC-19 (rows A4, B1) and IC-20. **Requirements:** FR-026, FR-031, FR-041.
- **Rulings:** A4 `DM-01M6RA7XC5HC8915GZT2R3H83W`, B1 `DM-01M6RA9BKJY7X9BFV93QW1KM1T`, B2 (keep the eager stale drop)
  `DM-01M6RA9G56DTA5GFY5Z28S0DB1`.
- **Code areas (plan, Changes to built behaviour):** `DocumentRules.java` (`check`, `checkWrite`, `addedDenies`;
  `version_not_held` at lines 36 and 151–165), `StaleIds.java`, `ActivationService.java` (replace).
- **Ruleset:** W5, W6, W11, W12, W26, I5.

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


## Project constraints (apply to every subtask)

- **TDD, contract first (C-001).** Write the failing test or `@contract` scenario first, watch it fail for the right
  reason, then write the code. A contract change lands with its `contracts/charter/changes/` fragment before the code
  that serves it.
- **No disabled tests, no new suppressions (C-006, NFR-001).** No `@Disabled`, no `@WIP` left on a scenario this WP
  makes green, no `@SuppressWarnings`/Checkstyle/PMD/SpotBugs suppression added, and the suppression register does not
  grow. Methods stay within CC ≤ 15, NPath ≤ 1000, ≤ 60 lines and nested-if depth ≤ 1.
- **Hexagonal slices, no shadow logic (C-002).** Merges and walks live in `packs`; `activations` reaches `packs` only
  through the `AvailablePacks` port and `activations.infra.packs`. `HexagonalArchitectureTest` must stay green.
- **No core changes (C-003).** Nothing in spec-kitty core is edited, and no PR or issue is opened in core.
- **git is a host binary (C-008).** Only the system `git` ≥ 2.50.1, never JGit, never a work-tree checkout.
- **Gradle.** Run module tasks, not the full suite (`NO_FULL_HEAVY_SUITES_IN_MISSION`):
  `./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none <module>:check`.
  On a Maven Central `429`, wait and re-run the same command; never turn dependency verification off.
- **Divergences (C-007).** Every behaviour that differs from core gets a divergence-register entry in the PR that
  ships it (the docs WP of that PR writes it; tell it what you changed in your hand-off note).
- **Commits** end with the session's attribution lines; no model id in commits or PR text.

## Branch Strategy

- Planning branch: `issue-10-layer-merge-effective-reads`; final merge target: `issue-10-layer-merge-effective-reads`.
- Execution worktrees are allocated per computed lane from `lanes.json`; do not pick a base branch by hand. Dependencies are a claim gate: this WP can be claimed only when its dependencies are approved or done.
- At closeout the mission branch is cut into the stacked PRs #399 → A0 → A → B → C; keep this WP's commits inside its PR letter (A0).

## Subtasks

### T015: Replace judges only added deny entries (A4)

- Tests first (`DocumentRulesTest`): the review's counter-example — select E and T (E overrides required T), deny
  T, unselect E, GET then PUT the same body → accepted (today `409 artifact_required`).
- `checkWrite` for replace computes `addedDenies` against the stored document; create still judges every deny.

### T016: Replace drops stale ids (A4)

- A replace body naming an id the closure no longer holds no longer answers `422`; the id is dropped and listed in
  `ActivationChange.dropped` (same shape as the per-artifact writes' stale drop, W11).
- Test: GET → pack upgrade removes X → PUT the old body → `200`, `dropped=[X]`.

### T017: Pins no pack satisfies are dropped (B1)

- A `packs` pin `(id, version)` whose version the pack no longer holds is dropped by replace, the per-artifact
  writes and apply-preset, and reported in `ActivationChange.droppedPins` (`{packId, version}`, declared by WP01).
- `createActivation` keeps `422 version_not_held` for a pin in its own body (decision `DM-01M6RN5Y406SB4KA13QYMQ2P8J`):
  test both sides (create refuses, replace with the same body drops).
- `version_not_held` is raised only by create; reads report an unheld pin in PR B (`pinNotHeld`).

### T018: Remove the kind gate from the write rules

- Delete the `activatedKinds` and mission-type branches in `DocumentRules`, `StaleIds`, `ActivationWrites` and
  `ActivationChange` (`research/a7-codeshape.md` item 3 lists them).
- Mission-type deny entries follow the same floor/lock rules as any kind (Q2).

### T019: Service wiring

- `ActivationService.replace` uses the new judgement; the change report carries the drops.
- `ActivationServiceTest` covers create (judges all), replace (added only), and the pin drop.

## Test strategy

`DocumentRulesTest`, `StaleIdsTest`, `ActivationServiceTest`; the review counter-examples A4 and B1 as named tests.

## Risks

- IC-13 (PR C, WP29) later relaxes the floor rule again for a deny satisfied by an override: keep `addedDenies` as a
  reusable function so WP29 only adds a predicate.
- `version_not_held` becomes unreachable on writes: do not delete the code from the contract (B1 keeps reads).

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first (an assertion failure with every step bound, decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`) and now pass.
- **Exit check:** `:activations:test` passes, with the A4 round trip (`GET` then `PUT` after deny-unselect and after a pack upgrade) and the B1 create/replace pair as named tests.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
