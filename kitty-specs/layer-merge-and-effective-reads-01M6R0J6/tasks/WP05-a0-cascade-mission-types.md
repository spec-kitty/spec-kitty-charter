---
work_package_id: WP05
title: 'A0 · Cascade and selection: mission types and the includeDeactivated scope'
dependencies:
- WP03
requirement_refs:
- FR-034
- FR-040
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-layer-merge-and-effective-reads-01M6R0J6
base_commit: eb9f0bf49c3081bcf109d0b201349ed0c49a42dd
created_at: '2026-11-06T16:32:43.230811+00:00'
subtasks:
- T020
- T021
- T022
- T023
- T024
phase: PR A0
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/domain/CascadeRules.java
create_intent: []
execution_mode: code_change
model: ''
owned_files:
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/CascadeRules.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/CascadeScope.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/Reach.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ReachPlan.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationSelectionService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationExclusionService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/Reachability.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ReachRequest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ReachResult.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesSelectTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesClearingTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesDeactivateTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesLineageTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesEnhancesTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesReportingTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeScopeTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/Reaches.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationSelectionServiceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationExclusionServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ReachabilityTest.java
role: implementer
tags: []
tracker_refs: []
---
# WP05: A0 · Cascade and selection: mission types and the includeDeactivated scope

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

- **Q1 (FR-040):** selecting a mission type cascades to the steps it holds, as `requires` does; rule W32 ("a mission
  type never cascades", `ActivationSelectionService.java:70`) is withdrawn. The reach lives in `packs`
  (`Reachability`), so the mission-type → step edges are walked there.
- **Q2 (FR-040):** a mission type can be deactivated like any kind, except when required or locked (`409`).
- **B5 (FR-034):** a select with `includeDeactivated` clears deny entries only on artifacts its walk **reaches and
  selects**, never on lineage ancestors it passes through.

Implementation command:
`spec-kitty agent action implement WP05 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A0 (own PR, stacked on #399 `issue-291-activation-writes`, before PR A; decision DM-01M6RFGB9NVD9MDWZ6AP0PBYH7).
- **Concern:** IC-19 (row B5), IC-20. **Requirements:** FR-034, FR-040.
- **Rulings:** B5 `DM-01M6RA9XQSH3E07C7VCPCVD6R4`; A7 Q1 `DM-01M6RCXTS0SMDNTWZ8G7GJ2P5S`, Q2
  `DM-01M6RCXZ9HSHT4K3443YYZ8ZFC`; A13 is accepted and documented only (`DM-01M6RA8XSM9CZZX1ADX6G80T3X`, no code).
- **Code areas:** `CascadeRules.java` (clearing, lines 28–33), `ActivationSelectionService.java:63-77`.
- **Ruleset:** W13–W24, W20 (reach, step term), W21 (scope), W22 (clearing), W32 (withdrawn).
- **Not here:** FR-038 (the cascade walks the merged edges) is PR B, WP23.

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

### T020: Mission-type reach to steps (Q1)

- Tests first (`ReachabilityTest`): a reach from `mission_type:M` yields the step nodes `action:M/s` the closure
  holds (and their `requires` closure, per W20).
- `Reachability` adds the mission-type → step term; `ReachRequest` accepts a mission-type root.

### T021: Selection service: no special case

- `ActivationSelectionService` stops forcing `cascade=false` for mission types; `CascadeScope` admits
  `mission_type` (W21).
- Tests: select M cascading selects its steps; select M with `cascade=false` selects M only.

### T022: Mission types deniable (Q2)

- `ActivationExclusionService` accepts `kind=mission_type`; a required or locked mission type answers `409
  artifact_required` / `artifact_locked` (same code path as other kinds).
- Tests for each outcome.

### T023: includeDeactivated clears reached-only (B5)

- Tests first (`CascadeRulesClearingTest`): lineage A ← B ← C with deny on A and B, select C with
  `includeDeactivated` where the walk passes B's lineage to A but selects only C and its reach → A's deny entry
  stays; a reached-and-selected artifact's deny is cleared.
- Change the clearing in `CascadeRules` (lines 28–33) to the reached-and-selected set.

### T024: Report and lineage tests

- Update `CascadeRulesReportingTest` and `CascadeRulesLineageTest` for the new mission-type and clearing behaviour;
  rewrite the tests that pinned "a mission type never cascades" (list them in the hand-off note).

## Test strategy

The Cascade*Tests, `ActivationSelectionServiceTest`, `ActivationExclusionServiceTest`, `ReachabilityTest`.

## Risks

- The squad estimated about 45 test methods pin the old shape across WP03–WP08; rewrite, never disable.
- `ReachabilityBudgetTest` (timing) must not regress: run `:packs:timingBudgets` if it exists, else note the p95.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first (an assertion failure with every step bound, decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`) and now pass.
- **Exit check:** `:activations:test` and `:packs:test --tests '*Reachability*'` pass; a mission-type select names its steps in `applied`, and a deny on a required mission type answers `artifact_required` in a unit test.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
