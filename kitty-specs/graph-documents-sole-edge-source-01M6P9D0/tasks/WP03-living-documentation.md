---
work_package_id: WP03
title: Living documentation
dependencies:
- WP01
- WP02
requirement_refs:
- NFR-002
planning_base_branch: claude/240-drop-edge-fields
merge_target_branch: claude/240-drop-edge-fields
branch_strategy: Planning artifacts for this mission were generated on claude/240-drop-edge-fields. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into claude/240-drop-edge-fields unless the human explicitly redirects the landing branch.
subtasks:
- T014
- T015
- T016
phase: Phase 3 - Documentation
history:
- at: '2026-11-05T17:40:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/changelog/2026-11-05-edges-only-in-graph-documents.md
execution_mode: planning_artifact
model: ''
owned_files:
- docs/guides/**
- docs/architecture/**
- docs/context/**
- docs/changelog/2026-11-05-edges-only-in-graph-documents.md
- cspell.json
role: documentarian
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP03 – Living documentation

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
Use language identifiers in code blocks: ````java`, ````bash`

---

## Objectives & Success Criteria

- The living docs say that an edge is authored only in a pack's graph documents and written only
  through `putPackRelationship`.
- No page outside the historical records describes `directive_refs`, typed artifact `references`,
  styleguide or toolguide `references`, or a registration body's `relationships`.
- A changelog entry records the change.
- The docs checks pass.

## Context & Constraints

- Read the new ADR (WP01) and the WP02 diff first.
- **Do not edit** `docs/adr/**` (WP01 owns it), `docs/changelog/README.md`, or `docs/adr/README.md`.
  The last two are generated indexes (ADR-0047).
- Historical plans (`docs/development/epic-186-*`) and earlier changelog entries stay as they are.
- `related:` entries must be real page slugs.

## Branch Strategy

- Planning base and merge target: `claude/240-drop-edge-fields`.
- Run: `spec-kitty agent action implement WP03 --agent <name> --mission graph-documents-sole-edge-source-01M6P9D0`.

## Subtasks & Detailed Guidance

### Subtask T014 – Living docs pages

Find the pages with
`grep -rln "directive_refs\|directiveRefs\|typed reference\|relationships array\|references:" docs --include=*.md`,
then filter out `adr/`, `changelog/` and `development/epic-186-*`. These are known to need edits:

- `docs/guides/compose-packs.md`: cross-pack references now come only from graph edges.
- `docs/architecture/logical-model.md` and `docs/architecture/descriptors.md`: the artifact fields
  and the derived relations.
- `docs/context/conceptual-model.md`: "a relationship is authored in the graph documents".
- `docs/context/user-journeys/06-publish-a-pack.md` and `09-upgrade-packs.md`: register the
  relationship through the relationship write.

`contracts/charter/scenarios/README.md` belongs to WP02.

### Subtask T015 – Docs changelog entry

`docs/changelog/2026-11-05-edges-only-in-graph-documents.md`. Copy the frontmatter and shape of a
recent entry, such as `2026-11-05-open-packs-real-compose.md`. Say what changed for a pack author and
for a client, link ADR-0060 and #240, and name the open-packs migration.

### Subtask T016 – Docs checks

Run `check_docs.py` (all eight), markdownlint-cli2, cspell and the frontmatter description length
check (≤180).

## Test Strategy

The docs checks, plus the grep from T014 coming back empty outside history.

## Risks & Mitigations

- Editing a generated index by mistake. Do not.

## Review Guidance

- Does every page that described a removed field now describe graph edges?
- Is no historical record rewritten?

## Activity Log

- 2026-11-05T17:40:00Z – system – Prompt created.
