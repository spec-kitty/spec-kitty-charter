---
work_package_id: WP26
title: 'B · Docs for PR B: resolution model, offering page, divergences'
dependencies:
- WP25
requirement_refs:
- FR-014
- C-007
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T131
- T132
- T133
phase: PR B
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/architecture/
create_intent:
- docs/changelog/2026-11-07-b-effective-reads.md
execution_mode: code_change
model: ''
owned_files:
- docs/architecture/resolution-model.md
- docs/architecture/offering-and-activation.md
- docs/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions.md
- docs/changelog/2026-11-07-b-effective-reads.md
role: documentarian
tags: []
tracker_refs: []
---
# WP26: B · Docs for PR B: resolution model, offering page, divergences

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `documentarian`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Document PR B (IC-12, PR B part): the merge is built (`resolution-model.md`, #357's "not yet in force" note removed);
two reads no longer answer `501` (`offering-and-activation.md`); DIV-209 (a layer override replaces whole), DIV-210
(an active `E overrides T` hides T and satisfies a requirement on T), DIV-211 (typed skipped report), DIV-212 (an
enhancer is in force only with a target in force); DIV-205 ratified; the ADR-0053 d5 note on #357; the ADR-0059
follow-up ticks.

Implementation command:
`spec-kitty agent action implement WP26 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR B (stacked on PR A).
- **Concern:** IC-12 (PR B). **Requirements:** FR-014, C-007. Use the next free DIV ids at the time if 209–212 are taken.

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


### Files shared with other work packages

These files are owned by another WP (the no-overlap rule) but this WP must make the small edit named. Make only that edit, and record a one-line rationale in the commit message (`out-of-map: <file> — <why>`):

- `docs/conformance/divergence-register.md` — DIV-205 ratified, DIV-209 to DIV-212 added (owned by WP09)
- `docs/adr/0059-switch-gate-cascade-edges-and-activation-rulings.md` — tick the follow-ups PR B closes (owned by WP09)
- `docs/architecture/composition-and-activation-rules.md` — flip the PR B rules' status rows to built


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

### T131: Architecture pages

`resolution-model.md` and `offering-and-activation.md` updated to what PR B built.

### T132: Divergences

DIV-205, DIV-209–DIV-212 in the register.

### T133: ADR notes and changelog

ADR-0053 d5 note; ADR-0059 ticks; `docs/changelog/2026-11-07-b-effective-reads.md`; run the docs checks.

## Test strategy

`python3 scripts/docs/check_docs.py`, markdownlint, cspell.

## Risks

- Keep the rules page status in sync only for what is built.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
