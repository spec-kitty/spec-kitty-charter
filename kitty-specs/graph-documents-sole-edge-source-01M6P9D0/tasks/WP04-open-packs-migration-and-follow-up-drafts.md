---
work_package_id: WP04
title: open-packs migration and follow-up drafts
dependencies: []
requirement_refs:
- FR-012
- C-003
planning_base_branch: claude/240-drop-edge-fields
merge_target_branch: claude/240-drop-edge-fields
branch_strategy: Planning artifacts for this mission were generated on claude/240-drop-edge-fields. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into claude/240-drop-edge-fields unless the human explicitly redirects the landing branch.
subtasks:
- T017
- T018
- T019
phase: Phase 1 - Migration draft
history:
- at: '2026-11-05T17:40:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: doctrine-daphne
authoritative_surface: kitty-specs/graph-documents-sole-edge-source-01M6P9D0/research/
create_intent:
- kitty-specs/graph-documents-sole-edge-source-01M6P9D0/research/open-packs-migration.md
- kitty-specs/graph-documents-sole-edge-source-01M6P9D0/research/open-packs-part-a.patch
- kitty-specs/graph-documents-sole-edge-source-01M6P9D0/research/open-packs-issue-draft.md
- kitty-specs/graph-documents-sole-edge-source-01M6P9D0/research/graphwalk-follow-up-issue-draft.md
- kitty-specs/graph-documents-sole-edge-source-01M6P9D0/research/migrate.py
execution_mode: planning_artifact
model: ''
owned_files:
- kitty-specs/graph-documents-sole-edge-source-01M6P9D0/research/**
role: curator
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP04 – open-packs migration and follow-up drafts

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter (or any user-defined profile), and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `doctrine-daphne`
- **Role**: `curator`
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
Use language identifiers in code blocks: ````java`, ````bash`

---

## Objectives & Success Criteria

- `research/open-packs-part-a.patch` applies cleanly to `spec-kitty-open-packs` at `dad323f` with
  `git apply`. On the patched checkout:
  - `main`'s corpus gates give the results recorded in research.md R7;
  - WP02's gates pass.
- `research/open-packs-migration.md` describes what the parent session files:
  - the files edited and why;
  - the 28 edges added, with their relations;
  - the 31 `reason` merges and the 1 `when` merge;
  - the 9 references subsumed by a reverse edge;
  - the 26 citations dropped, each listed;
  - the manifest restamp;
  - the open-packs validator, test and doc edits that part A or part B needs;
  - the part B list (27 fragment `when` texts).
- `research/open-packs-issue-draft.md` and `research/graphwalk-follow-up-issue-draft.md` are ready to
  file. Each carries a title, a body and its labels.
- The generator script `research/migrate.py` is checked in, so the parent session can regenerate the
  patch at a different base.

## Context & Constraints

- **Do not write to `spec-kitty-open-packs`.** The read-only clone is `/home/user/spec-kitty-open-packs`.
  The draft is produced on a scratch copy:
  `/tmp/claude-0/-home-user-spec-kitty-charter/eaaf1313-9aef-56a1-9b9a-09e87ee4ac55/scratchpad/op-mig`.
- The generator is in the scratchpad (`migrate.py`), and its report is `mig-report.json`.
- The relations chosen for new edges follow the interim mapping for fields that minted (`suggests`
  for typed refs). For the others they follow the source kind's dominant graph relation:
  `mission_type` uses `scope`, and procedure, styleguide and toolguide use `suggests`. Flag this
  choice for the curator in the description.
- An edge whose unordered pair already has an edge in the other direction is not added (ADR-0027).

## Branch Strategy

- Planning base and merge target: `claude/240-drop-edge-fields`.
- Run: `spec-kitty agent action implement WP04 --agent <name> --mission graph-documents-sole-edge-source-01M6P9D0`.

## Subtasks & Detailed Guidance

### Subtask T017 – Part A patch and description

1. Regenerate on a fresh copy:
   `cp -a /home/user/spec-kitty-open-packs <copy>`, then `python3 -I migrate.py <copy> <report>`.
2. Write the patch with `git -C <copy> diff > research/open-packs-part-a.patch`.
3. Check it: `git apply --check` on another fresh copy.
4. Write `open-packs-migration.md`, built from the report. Its sections cover the counts, each table
   listed under Objectives, the ordering (part A, then the charter PR re-pins, then part B), and the
   validator. The open-packs validator (`scripts/ci/validate_pack_schemas.py`) has these to change:
   - `BARE_ID_LISTS` (:751);
   - `TYPED_REFERENCE_SCHEMAS`;
   - `written_references()`;
   - the `CHARTER_COMMIT` pin.

   Its tests are `tests/ci/test_validate_pack_schemas.py` :458-495, and its docs are
   `docs/artefacts/index.md` :52-74.

   State which of these must move in part A, because they read fields part A deletes, and which can
   wait for part B.

### Subtask T018 – Part B draft

Part B lands after the charter PR merges:

- the 27 fragment `when` texts (from `part_b_when` in the report), written as exact YAML edits per
  fragment;
- the move of the validator's `CHARTER_COMMIT` to the charter merge commit;
- the removal of the validator's readers of the dropped fields, if part A did not already remove
  them.

### Subtask T019 – Issue drafts

- **open-packs issue**: the title is "Move artifact reference fields into the graph documents
  (charter #240)". The body gives the context, parts A and B, and the acceptance criteria. Use the
  placeholder key `open-packs#TBD-edge-fields` where the charter PR refers to it.
- **charter follow-up**: the title is "Retire GraphWalk or re-base it on graph-document edges". The
  body explains that only tests construct it, and that once #240 lands it walks only derived guard
  relations. Retiring it amends ADR-0025's walk decision; the change also covers `ComposeResolver`,
  `GraphWalker` and `Descent`.

## Test Strategy

`git apply --check` of the patch on a fresh copy, then the charter gates on the patched copy, as in
research R7.

## Risks & Mitigations

- **The base moves.** If open-packs `main` has moved past `dad323f`, the parent session regenerates
  the patch with `migrate.py` at the new base.

## Review Guidance

- Is every count in the description backed by the report?
- Is every dropped citation listed?

## Activity Log

- 2026-11-05T17:40:00Z – system – Prompt created.
