---
work_package_id: WP02
title: Known-failure workflow docs and changelog
dependencies:
- WP01
requirement_refs:
- FR-009
planning_base_branch: feat/whole-pack-known-failures
merge_target_branch: feat/whole-pack-known-failures
branch_strategy: Planning artifacts for this mission were generated on feat/whole-pack-known-failures. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/whole-pack-known-failures unless the human explicitly redirects the landing branch.
subtasks:
- T008
- T009
- T010
phase: Phase 2 - Documentation
history:
- at: '2026-10-27T18:10:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/changelog/2026-10-27-whole-pack-known-failures.md
execution_mode: planning_artifact
model: ''
owned_files:
- docs/guides/service-build.md
- docs/changelog/2026-10-27-whole-pack-known-failures.md
- docs/changelog/README.md
role: documentarian
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02 – Known-failure workflow docs and changelog

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter (or any user-defined profile), and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `documentarian`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## ⚠️ IMPORTANT: Review Feedback

**Read this first if you are implementing this task!**

- **Has review feedback?**: Check the `review_ref` field in the event log (via `spec-kitty agent tasks status`).
- **You must address all feedback** before your work is complete. Feedback items are your implementation TODO list.
- **Report progress**: As you address each feedback item, record what you changed with `spec-kitty agent tasks add-history <WPID> --note "..."` (stored in the status event log).

---

## Review Feedback

*[If this WP was returned from review, the reviewer feedback reference appears in the status event log.]*

---

## Markdown Formatting

Wrap HTML/XML tags in backticks: `` `<div>` ``, `` `<script>` ``
Use language identifiers in code blocks: ````yaml`, ````bash`

---

## Objectives & Success Criteria

Contributors can find, in the gate's docs, when to use the known-failure list and the three-step workflow; the
changelog announces it (FR-009). Done when the gate section names the list, its entry fields, the issue format, every
new problem code from WP01 and the report's new count, and the docs checks pass.

## Context & Constraints

- Depends on WP01: quote the exact problem codes and labels it shipped (`KNOWN_FAILURE` status, report label `known failure`, `UNEXPECTEDLY_GREEN`, `CHANGED_RED`, `STALE_KNOWN_FAILURE`, `KNOWN_FAILURE_UNMODELLED`, `AMBIGUOUS_ENTRY`). Read `service/packs/src/test/resources/whole-pack/known-failures.yaml` and `WholePackExpectation.java` on the lane before writing.
- ADR-0036 decision 5 (filing a pack inconsistency) is the authority for the workflow; link it, don't restate its checklist.
- Style: the repository's docs voice (plain, short sentences, British-neutral spelling as in the surrounding page), Markdown frontmatter as in sibling changelog entries. Glossary terms: say "charter", never "doctrine" (retired 2026-10-27).
- Shared files: `docs/changelog/README.md` is edited by sibling missions; add one line at the top of the list, never reorder. In `docs/guides/service-build.md` add to the gate section only.

## Branch Strategy

- **Strategy**: populated by finalize-tasks
- **Planning base branch**: `feat/whole-pack-known-failures`
- **Merge target branch**: `feat/whole-pack-known-failures` (the mission then lands on `main` by PR)

> Execution worktrees are allocated per computed lane from `lanes.json`. Implement with
> `spec-kitty agent action implement WP02 --agent claude --mission whole-pack-known-failures-01M5Z72Y`.

## Subtasks & Detailed Guidance

### Subtask T008 – Gate docs

- **Purpose**: The workflow lives where contributors read about the gate.
- **Steps**: In `docs/guides/service-build.md`, section "The whole-pack round-trip gate", after the paragraph about `expected-red.yaml` and `exclusions.yaml` and its ratchet bullets, add a short subsection or paragraph group:
  1. What the list is for: a file of a modelled kind that fails because the pack's own data is inconsistent (ADR-0036 decision 5), not a service defect (expected red) and not a file the gate does not compare (exclusion). Path: `service/packs/src/test/resources/whole-pack/known-failures.yaml`.
  2. Entry fields: `path`, `at` (as the report's detail shows it; the whole `refused: …` detail for a refused file), `reason`, `issue` in the form `spec-kitty/spec-kitty-open-packs#<number>`; one entry per file.
  3. The workflow, as a numbered list: (1) file the issue in spec-kitty-open-packs as [ADR-0036 decision 5](/adr/0036-round-trip-fidelity-is-equality-of-meaning/) describes; (2) add the entry; (3) when the fix merges, move `openPacksRef` and remove the entry in the same change — the gate fails with `UNEXPECTEDLY_GREEN` until you do.
  4. What fails the gate: a malformed entry or issue (load error), `CHANGED_RED` when the differences move, `STALE_KNOWN_FAILURE`, `KNOWN_FAILURE_UNMODELLED`, `AMBIGUOUS_ENTRY`.
  5. The report shows known failures as their own count and per-kind column.
  6. Update the page's `updated:` frontmatter date to `2026-10-27T00:00:00Z` if it is older.
- **Files**: `docs/guides/service-build.md`.
- **Parallel?**: Yes, with T009.

### Subtask T009 – Changelog

- **Steps**:
  1. Create `docs/changelog/2026-10-27-whole-pack-known-failures.md` with the frontmatter shape of `docs/changelog/2026-10-27-whole-pack-round-trip-gate.md` (`okf_version`, `title`, `description`, `doc_status: active`, `updated`, `type: Changelog`, `kind: Changelog`, `related` including `guides/service-build` and `adr/0036-round-trip-fidelity-is-equality-of-meaning`, `audience` with `charter-service-contributor`).
  2. Title: "2026-10-27 — The whole-pack gate carries known pack failures until the pin moves". Body: two or three short paragraphs: what changed (#247), the self-expiry, the issue format, that the list ships empty, link to the gate docs.
  3. Add the index line at the top of the list in `docs/changelog/README.md`.
- **Files**: the two changelog files.

### Subtask T010 – Docs checks

- **Steps**: Run the docs checks the charter names (see `docs/guides/service-build.md` or `docs/guides/contributing.md` for the exact commands): `python3 scripts/check_docs.py` (all checks), `npx markdownlint-cli2` on the touched files, `npx cspell` on the touched files. Add any new legitimate word to `cspell.json` only if the check flags it (that file is shared: append only).
- **Notes**: Record the commands and results with `spec-kitty agent tasks add-history WP02 --note "..."`.

## Risks & Mitigations

- Drift from WP01's real codes: copy them from the code, not from this prompt.
- Changelog index conflicts with siblings: rebase on `main` before review and re-place the line at the top.

## Review Guidance

- Every problem code in `WholePackExpectation` that concerns the known-failure list is named in the docs.
- The workflow names ADR-0036 decision 5 and the pin bump.
- The docs checks pass.

## Progress & Status

Progress, history, and status all live in the status event log (`status.events.jsonl`) — never in this prompt file.

- **Record a progress note**: `spec-kitty agent tasks add-history WP02 --note "<what you did>"`
- **Change WP status**: `spec-kitty agent tasks move-task WP02 --to <status>`
- **View history and status**: `spec-kitty agent tasks status`
