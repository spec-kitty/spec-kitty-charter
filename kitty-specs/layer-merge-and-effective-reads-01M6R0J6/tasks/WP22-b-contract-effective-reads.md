---
work_package_id: WP22
title: 'B · Contract B: effective list and skipped report, red first'
dependencies:
- WP21
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
- SC-005
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T109
- T110
- T111
- T112
- T113
phase: PR B
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
- contracts/charter/examples/ActivationSkippedReport.nothing-skipped.yaml
- contracts/charter/examples/EffectiveActionContext.implement.yaml
role: implementer
tags: []
tracker_refs: []
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
- **Concerns:** IC-08 in full, IC-17 (contract), IC-20 (read side: `kind_not_activated` goes here, not in WP01;
  `skippedByScope` stays as the write report, empty at read). **Requirements:** FR-013–FR-017, FR-020–FR-022, FR-026 (read half: `pinNotHeld`), FR-027
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
- `contracts/charter/schemas/PackRegistration.yaml` — `notices.activationsAffected` (owned by WP11)
- `contracts/charter/examples/ActivationChange.selected.yaml` — the new empty buckets (owned by WP01)


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

`EffectiveArtifact` gains required `packId`, `level`, optional `satisfiedBy` (an override only); `kind` takes the merged kind enum (mission types listed). `EffectiveArtifactReason` drops `kind_default` and `kind_not_activated`, gains `overridden`, `mission_type_not_in_force` and `enhanced_target_not_in_force`. No separate reason for a required artifact that stays out of force: a required enhancer with no target in force (`DM-01M6R4W3ZMSESETMQ0ZQTCZCFJ`) or a required artifact bound to a mission type not in force (`DM-01M6RFGFW3S0YJ222QMHD2W11B`) keeps that reason, carries `required: true`, and is listed in `requiredNotInForce` (data-model, EffectiveSkippedReport). `required` and `summary.required` descriptions per research R11.

### T110: SkippedReport buckets

Required `staleIds`, `denyNotApplied`, `languageFiltered`, `overridden`, `missionTypeNotInForce`, `overrideNotApplied`, `enhancedTargetNotInForce`, `pinNotHeld`, `requiredNotHeld` (A10: must-have items no pack holds), `requiredNotInForce` (`{kind, artifactId, reason}`). `skippedByScope` **stays**: it is the write report of the `cascade` scope, always empty at read (decision `DM-01M6RN5MTWC98PXEVJNFQSRRJX`). A write's `ActivationChange.skipped` answers the new buckets empty. Update the three examples that carry a `SkippedReport`. Also add `PackRegistration.notices.activationsAffected` (B4, PR B per the contract delta; a shared edit of WP11's schema).

### T111: Unreserve the two reads

Remove `x-reserved` from both operations (the reads' `501` scenarios are replaced, not deleted, by WP25's); document `pack_unreadable` for a refused override and an override cycle; the keyset cursor; `ETag`/`304`. `activations.feature` `501` pins retired; `ReservedOperationCoverageTest` 4 → 2.

### T112: Changelog fragment

One fragment with one `### Breaking` block (members removed/required, reasons changed) and `### Added`/`### Changed`.

### T113: Red scenarios

Decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`: commit both files `@WIP`; WP25 binds the glue, records the red and removes the tag. Every
`When` asserts `200` and a non-empty answer, and every absence check (X absent, E absent) is paired with a positive
control in the same answer.
`layer-merge.feature`: US3 AS1, US6, the A1 union, the #357 settlement, a refused override (`500 pack_unreadable`). `effective-reads.feature`: US3 AS2–AS7, US4; a required T hidden by an active override is satisfied; an enhancer alone does not satisfy T, which is put in force; an enhancer whose target is out of force is `inactive`; an override of a locked T is not applied; `F → E → T` chain leaves F (A3); an enhancer ring is not in force (A8); a step of a denied mission type is not in force (binding); a required step whose mission type is not in force is dropped and reported (`DM-01M6RFGFW3S0YJ222QMHD2W11B`); a pin no pack holds is in `pinNotHeld`; a must-have item no pack holds is in `requiredNotHeld` (A10).
**ETag (US3 AS7):** the `304` with the current tag; after a re-registration that changes a pack of the closure, the
old tag gives `200` with a new `ETag`; a change to a pack outside the closure keeps the tag; `?state=`, `?kind=` and
a cursor page never answer `304` to another representation's tag; after the v1→v2 write the tag changes once.
**Writes (B6/B7, B4):** a select of an enhancer whose target is denied answers `200` with
`warnings: [enhanced_target_not_in_force]`; a select of an overridden artifact warns `overridden`; `GET
/activations/{id}` lists a deny entry the floor overrides in `denyNotApplied` (a positive control: an applied deny is
not listed).
**SC-005 (PR B items):** A1 (union of every governing copy), A2 (pinned final state, see WP23), A3, A8, A9 (bound and
ungrounded floor), A13, B1 reads, B4, B6/B7 each have a named scenario. Fixtures authored inline per the plan's
Fixtures list.

## Test strategy

Contract lint, `OpenApiDocumentTest`, `ReservedOperationCoverageTest`; the two feature files committed `@WIP`
(red is recorded by WP25 with the glue bound).

## Risks

- The fixture list is long: author each fixture next to the rule it proves (C-004).

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first (an assertion failure with every step bound, decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`) and now pass.
- **Exit check:** the contract module check passes with `-PcontractBaseline=none`; `ReservedOperationCoverageTest` counts 2; both feature files parse, are `@WIP`, and contain every scenario listed in T113.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
