---
work_package_id: WP20
title: 'A · Docs for PR A: host requirement, guides, status codes'
dependencies:
- WP18
- WP19
requirement_refs:
- FR-008
- C-008
- NFR-011
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T099
- T100
- T101
- T102
phase: PR A
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/changelog/2026-11-07-a-pack-registration.md
execution_mode: code_change
model: ''
owned_files:
- README.md
- docs/guides/run-in-a-container.md
- docs/guides/compose-packs.md
- docs/development/http-status-codes.md
- docs/architecture/hexagonal-service.md
- docs/changelog/2026-11-07-a-pack-registration.md
- cspell.json
role: documentarian
tags: []
tracker_refs: []
---
# WP20: A · Docs for PR A: host requirement, guides, status codes

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `documentarian`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Document PR A in the PR that ships it (DIRECTIVE_037): the git host requirement (≥ 2.50.1, no JGit, no work tree) in
README.md and `run-in-a-container.md` (the image must carry it); registration and removal in `compose-packs.md`;
`413`/`415`/`202`/`503` in `http-status-codes.md`; the `infra.archive` ring and the process rule in
`hexagonal-service.md`; the A13 note; a docs changelog fragment.

Implementation command:
`spec-kitty agent action implement WP20 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A (stacked on PR A0).
- **Concern:** IC-12 (PR A). **Requirements:** FR-008, C-008, NFR-011.
- **A13** (`DM-01M6RA8XSM9CZZX1ADX6G80T3X`): a cascading deactivate removes `t` and what its walk reached unless
  something else active keeps it; prior selections are not remembered. Put it in `compose-packs.md` (or the
  activation guide it links to).

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
- At closeout the mission branch is cut into the stacked PRs #399 → A0 → A → B → C; keep this WP's commits inside its PR letter (A).

## Subtasks

### T099: Host requirement

README.md (requirements section) and `run-in-a-container.md`: git ≥ 2.50.1 on `PATH` or `charter.pack-sources.git.executable`; without it the git source answers `503 pack_source_unavailable`; the container image must carry it; registration is off by default (`charter.registration.enabled`).

### T100: Guides

`compose-packs.md`: register (PUT archive, POST source, polling the status URI), remove (DELETE), the version rule, the impact-check refusals, A13.

### T101: Reference pages

`http-status-codes.md` and `hexagonal-service.md`.

### T102: Changelog and spelling

`docs/changelog/2026-11-07-a-pack-registration.md`; cspell words (`curlopt`, …) in `cspell.json`; run the docs checks.

## Test strategy

`python3 scripts/docs/check_docs.py`, markdownlint, cspell, lychee on changed files.

## Risks

- `README.md` and `cspell.json` are touched by other PRs rarely; keep edits minimal.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
