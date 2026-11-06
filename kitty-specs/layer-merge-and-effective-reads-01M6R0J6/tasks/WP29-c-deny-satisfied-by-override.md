---
work_package_id: WP29
title: C · Deny on a required artifact satisfied by an override, and PR C docs
dependencies:
- WP28
- WP26
requirement_refs:
- FR-013
- FR-014
- FR-016
- C-002
- C-007
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T143
- T144
- T145
- T146
phase: PR C
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/scenarios/activation-deny-satisfied.feature
create_intent:
- contracts/charter/changes/2026-11-07-c-deny-satisfied-by-override.md
- contracts/charter/scenarios/activation-deny-satisfied.feature
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/DocumentRulesOverrideTest.java
- docs/changelog/2026-11-07-c-graph-and-context.md
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/responses/ArtifactPolicyRefused.yaml
- contracts/charter/changes/2026-11-07-c-deny-satisfied-by-override.md
- contracts/charter/scenarios/activation-deny-satisfied.feature
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/DocumentRulesOverrideTest.java
- docs/architecture/logical-model.md
- docs/changelog/2026-11-07-c-graph-and-context.md
role: implementer
tags: []
tracker_refs: []
---
# WP29: C · Deny on a required artifact satisfied by an override, and PR C docs

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

- **IC-13 (Q9):** accept a deny entry on a required `T` when an active override of `T` (judged by the in-force
  resolution over the document as it will be stored) satisfies the requirement and `T` is not locked; locked `T` stays
  `409 artifact_locked`; with only an enhancer active, `409 artifact_required`. Scope per DIV-204: create judges every
  deny; replace, deactivate, apply-preset with `force` and select with `includeDeactivated` judge only the deny entries
  they add (A4); removing the override later is not refused, and the read lists the deny in `denyNotApplied`.
- **Docs for PR C:** `logical-model.md` and `offering-and-activation.md` (graph and context); ADR-0059 amendment R6
  (the write rule and R11's read rules for D2 and D3); DIV-204 updated; a docs changelog fragment.

Implementation command:
`spec-kitty agent action implement WP29 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR C (stacked on PR B).
- **Concerns:** IC-13, IC-12 (PR C). **Requirements:** FR-013, FR-014, FR-016, C-002, C-007.
- The override facts come through `PacksSession` (C-002), consulted only when a write adds a deny entry on a required
  artifact. `CascadeRules` is unchanged: a floor artifact is never a cascading-deactivate candidate (F2).
- The issue comments on #10, #11, #103, #357 and #374 are closeout actions for the orchestrator, not files here.

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


### Files shared with other work packages

These files are owned by another WP (the no-overlap rule) but this WP must make the small edit named. Make only that edit, and record a one-line rationale in the commit message (`out-of-map: <file> — <why>`):

- `service/activations/src/main/java/ai/speckitty/charter/activations/domain/DocumentRules.java` — the override predicate in the floor refusal (owned by WP04)
- `service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationWrites.java` — pass the session to the rule (owned by WP04)
- `contracts/charter/paths/activations.yaml` — description of `createActivation` (owned by WP01)
- `contracts/charter/paths/activations_activationId.yaml` — description of `replaceActivation` (owned by WP01)
- `contracts/charter/paths/activations_activationId_exclusions_kind_artifactId.yaml` — description of the deactivate (owned by WP01)
- `contracts/charter/schemas/CharterRefusalCode.yaml` — `artifact_required` text (owned by WP11)
- `docs/adr/0059-switch-gate-cascade-edges-and-activation-rulings.md` — amendment R6 (owned by WP09)
- `docs/conformance/divergence-register.md` — DIV-204 (owned by WP09)
- `docs/architecture/offering-and-activation.md` — the graph and the context (owned by WP26)
- `docs/conformance/ruleset-scenario-trace.md` — fill the PR C rows and mark the trace complete (owned by WP09)


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
- At closeout the mission branch is cut into the stacked PRs #399 → A0 → A → B → C; keep this WP's commits inside its PR letter (C).

## Subtasks

### T143: Contract red first

Descriptions of the three writes, `ArtifactPolicyRefused.yaml`, the `artifact_required` text, a `### Changed` fragment saying #399's shipped write behaviour changes.

### T144: Red scenarios

`activation-deny-satisfied.feature`: deactivate required T with active `E overrides T` → `200`; only an enhancer → `409 artifact_required`; locked T → `409 artifact_locked`; a replace adding override and deny → `200`; a replace with the deny and no override → `409`; unselect E afterwards → `200` and the skipped report lists the deny in `denyNotApplied`.
The three `409` cases (enhancer only, locked T, replace without override) already pass under #399: label them in the
feature as green-before regression controls. Red evidence is required only for the three `200` cases (bound glue,
assertion failure against the PR B head).

### T145: The rule

`DocumentRules` floor refusal consults the in-force states for the stored-after document; tests in `DocumentRulesOverrideTest` per scope (create, replace, deactivate, force preset, includeDeactivated).

### T146: Docs

`logical-model.md`, `offering-and-activation.md`, ADR-0059 R6, DIV-204, docs changelog; run the docs checks.

## Test strategy

`DocumentRulesOverrideTest`, `activation-deny-satisfied.feature`, the existing activation features unchanged, docs checks.

## Risks

- A merge refusal now reaches the writes that add a deny entry on a required artifact: the scenario set includes one such refusal.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first (an assertion failure with every step bound, decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`) and now pass.
- **Exit check:** `:activations:test` and the contract suite pass; `activation-deny-satisfied.feature` green with red recorded for the three `200` cases; every row of the ruleset trace names an existing scenario.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
