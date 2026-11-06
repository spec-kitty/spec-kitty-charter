---
work_package_id: WP09
title: 'A0 · Docs: ADR amendments, divergences and the core hand-over for A0'
dependencies:
- WP08
requirement_refs:
- FR-040
- FR-041
- C-007
- C-003
- SC-002
- SC-005
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T040
- T041
- T042
- T043
- T149
phase: PR A0
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/changelog/2026-11-07-a0-kind-model.md
- docs/conformance/ruleset-scenario-trace.md
execution_mode: planning_artifact
model: ''
owned_files:
- docs/adr/0020-offering-and-activations.md
- docs/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest.md
- docs/adr/0050-mission-types-and-lifecycles-as-declared-state-machines.md
- docs/adr/0059-switch-gate-cascade-edges-and-activation-rulings.md
- docs/conformance/divergence-register.md
- docs/changelog/2026-11-07-a0-kind-model.md
- docs/conformance/ruleset-scenario-trace.md
role: documentarian
tags: []
tracker_refs: []
---
# WP09: A0 · Docs: ADR amendments, divergences and the core hand-over for A0

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `documentarian`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Keep every reader's picture current for PR A0 (DIRECTIVE_037): amend the ADRs the kind model and the ruling changes
touch, update the divergence register, and draft the core hand-over text (no edit in core, C-003).

Implementation command:
`spec-kitty agent action implement WP09 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A0 (own PR, stacked on #399 `issue-291-activation-writes`, before PR A; decision DM-01M6RFGB9NVD9MDWZ6AP0PBYH7).
- **Concerns:** IC-12 (A0 part), IC-20 (ADR list). **Requirements:** FR-040, FR-041, C-007.
- **ADR amendments (from `research/a7-kind-model-2026-11-06.md` item 6 and the architecture lens item 10):**
  ADR-0020 (D4 table, the three states, rulings 4–5, D7 precedence); ADR-0042 D2 (preset kinds); ADR-0050 decision 6
  (mark built; reconcile its "absent means all" with ADR-0053 d10); ADR-0059 D2.2 (lines ~110–126), D6 (~204) and the
  follow-ups (~302–304). Use the amendment-note style of the existing ADR-0059 amendments (A2–A5): dated, citing the
  decision ids.
- **Divergences:** DIV-203 and DIV-501 updated; one new DIV: core's `activated_kinds` narrowing is not honoured (a
  core project that narrows it sees a larger effective set). Use the next free DIV number in the file at the time.
- **The rules page** (`docs/architecture/composition-and-activation-rules.md`) is already updated for A7; change
  its status table rows from "planned (layer-merge; PR A0)" to "built (PR A0)" only for what WP01–WP08 actually built
  — this is an out-of-map edit, see below.

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


### Files shared with other work packages

These files are owned by another WP (the no-overlap rule) but this WP must make the small edit named. Make only that edit, and record a one-line rationale in the commit message (`out-of-map: <file> — <why>`):

- `docs/architecture/composition-and-activation-rules.md` — flip the status-table rows of the A0 rules to built


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

### T040: ADR-0020 and ADR-0042 amendments

Write the dated amendment notes; do not rewrite the decision text. Cite A7, Q1–Q4 and the v2 decision ids.

### T041: ADR-0050 and ADR-0059 amendments

ADR-0050 decision 6 marked built and reconciled with ADR-0053 d10; ADR-0059 D2.2/D6 notes for the mission-type cascade (W32 withdrawn) and the uniform kinds; B5 and A4 notes under the existing amendment list.

### T042: Divergence register

DIV-203, DIV-501 updated; the new DIV for `activated_kinds` narrowing; check every DIV id is unique.

### T043: Docs changelog and core hand-over text

- `docs/changelog/2026-11-07-a0-kind-model.md` per `docs/changelog/README.md`.
- In the same file (or a section the PR description can quote), the core hand-over: regenerate the strict DTOs;
  `activatedKinds`/`missionTypes` are refused; mission types are selected under `selected.mission_type`;
  `activated_kinds` narrowing is gone. Nothing is edited or filed in core.

### T149: Ruleset scenario trace (SC-002, SC-005)

New `docs/conformance/ruleset-scenario-trace.md`: one row per counter-example A1–A15 and B1–B8 of the ruleset review
(`kitty-specs/layer-merge-and-effective-reads-01M6R0J6/research/ruleset-review-2026-11-06.md`) and per acceptance
scenario of User Stories 1–6: the ruled answer, the decision id, the feature file and scenario name that pins it, and
the PR. Fill the A0 rows (from WP08's hand-off); leave the PR A, B and C rows with the planned feature file and
`planned`. WP20, WP26 and WP29 fill their rows. Run the docs checks.

## Test strategy

The docs checks: `python3 scripts/docs/check_docs.py` and markdownlint on the changed files.

## Risks

- The docs checks (`check_docs.py`, markdownlint, cspell, lychee) run on every new term: add cspell words with the change.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- **Exit check:** `python3 scripts/docs/check_docs.py` and markdownlint pass on the changed files; every A0 row of the trace names a scenario that exists.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
