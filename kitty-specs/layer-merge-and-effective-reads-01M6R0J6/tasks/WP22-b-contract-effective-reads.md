---
work_package_id: WP22
title: 'B · Contract B: effective list and skipped report, red first'
dependencies: [WP21]
requirement_refs:
- FR-013
- FR-014
- FR-015
- FR-016
- FR-017
- FR-020
- FR-021
- FR-022
- FR-026
- FR-027
- FR-028
- FR-032
- NFR-002
- C-001
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts were generated on issue-10-layer-merge-effective-reads; execution worktrees come from lanes.json and completed changes merge back into issue-10-layer-merge-effective-reads. The four PRs (A0, A, B, C) are cut from it at closeout.
subtasks:
- T109
- T110
- T111
- T112
- T113
phase: PR B
assignee: ''
agent: claude
shell_pid: ''
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/schemas/
create_intent:
- contracts/charter/changes/2026-11-07-b-effective-reads.md
- contracts/charter/scenarios/layer-merge.feature
- contracts/charter/scenarios/effective-reads.feature
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/schemas/EffectiveArtifact.yaml
- contracts/charter/schemas/EffectiveArtifactReason.yaml
- contracts/charter/schemas/EffectiveArtifactList.yaml
- contracts/charter/schemas/EffectiveArtifactState.yaml
- contracts/charter/schemas/SkippedReport.yaml
- contracts/charter/schemas/ActivationSkippedReport.yaml
- contracts/charter/schemas/SkippedDeactivated.yaml
- contracts/charter/paths/activations_activationId_artifacts.yaml
- contracts/charter/paths/activations_activationId_skipped.yaml
- contracts/charter/changes/2026-11-07-b-effective-reads.md
- contracts/charter/scenarios/layer-merge.feature
- contracts/charter/scenarios/effective-reads.feature
- contracts/charter/scenarios/activations.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ReservedOperationCoverageTest.java
role: implementer
---
# WP22: B · Contract B: effective list and skipped report, red first

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Declare what `listEffectiveArtifacts` and `getActivationSkippedReport` answer, unreserve them, and write their
failing scenarios. Also declare IC-17's write-side members (`ActivationChange.warnings`, `Activation.denyNotApplied`).

Implementation command:
`spec-kitty agent action implement WP22 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR B (stacked on PR A).
- **Concerns:** IC-08 in full, IC-17 (contract), IC-20 (read side: `kind_not_activated` and `skippedByScope` go
  here, not in WP01). **Requirements:** FR-013–FR-017, FR-020–FR-022, FR-026 (read half: `pinNotHeld`), FR-027
  (activation half), FR-028 (unheld report), FR-032, NFR-002, C-001.
- **Ruling Q1:** the list keeps ADR-0020 D7's shape: every closure artifact with a `state`; "in force" is
  `state=active`.
- **Keyset cursor:** (`kind`, `id`, `ETag`); `summary` counts identical on every page; a cursor from another tag is
  `400`.

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


### Files shared with other work packages

These files are owned by another WP (the no-overlap rule) but this WP must make the small edit named. Make only that edit, and record a one-line rationale in the commit message (`out-of-map: <file> — <why>`):

- `contracts/charter/schemas/ActivationChange.yaml` — add `warnings: [{code, artifact}]` (codes `enhanced_target_not_in_force`, `overridden`) (owned by WP01)
- `contracts/charter/schemas/Activation.yaml` — add `denyNotApplied: [urn]` (owned by WP01)


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
- At closeout the mission branch is cut into the stacked PRs #399 → A0 → A → B → C; keep this WP's commits inside its PR letter (B).

## Subtasks

### T109: EffectiveArtifact and reasons

`EffectiveArtifact` gains required `packId`, `level`, optional `satisfiedBy` (an override only); `kind` takes the merged kind enum (mission types listed). `EffectiveArtifactReason` drops `kind_default` and `kind_not_activated`, gains `overridden`, `mission_type_not_in_force`, `enhanced_target_not_in_force`, `required_not_in_force` (bound/ungrounded floor items — check the data model for the exact token). `required` and `summary.required` descriptions per research R11.

### T110: SkippedReport buckets

Required `staleIds`, `denyNotApplied`, `languageFiltered`, `overridden`, `missionTypeNotInForce`, `overrideNotApplied`, `enhancedTargetNotInForce`, `pinNotHeld`, `unheldRequired`; `skippedByScope` removed. A write's `ActivationChange.skipped` answers them empty.

### T111: Unreserve the two reads

Remove `x-reserved` from both operations; document `pack_unreadable` for a refused override and an override cycle; the keyset cursor; `ETag`/`304`. `activations.feature` `501` pins retired; `ReservedOperationCoverageTest` 4 → 2.

### T112: Changelog fragment

One fragment with one `### Breaking` block (members removed/required, reasons changed) and `### Added`/`### Changed`.

### T113: Red scenarios

`layer-merge.feature`: US3 AS1, US6, the A1 union, the #357 settlement, a refused override (`500 pack_unreadable`). `effective-reads.feature`: US3 AS2–AS7, US4; a required T hidden by an active override is satisfied; an enhancer alone does not satisfy T, which is put in force; an enhancer whose target is out of force is `inactive`; an override of a locked T is not applied; `F → E → T` chain leaves F (A3); an enhancer ring is not in force (A8); a step of a denied mission type is not in force (binding); a required step whose mission type is not in force is dropped and reported (`DM-01M6RFGFW3S0YJ222QMHD2W11B`); a pin no pack holds is in `pinNotHeld`. Fixtures authored inline per the plan's Fixtures list.

## Test strategy

Contract lint, `ReservedOperationCoverageTest`, the two feature files red for the right reason.

## Risks

- The fixture list is long: author each fixture next to the rule it proves (C-004).

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
