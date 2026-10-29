---
work_package_id: WP02
title: 'Docs: the document is served'
dependencies:
- WP01
requirement_refs:
- FR-007
- C-002
- C-004
planning_base_branch: feat/209-serve-openapi-document
merge_target_branch: feat/209-serve-openapi-document
branch_strategy: Planning artifacts for this mission were generated on feat/209-serve-openapi-document. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/209-serve-openapi-document unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-serve-openapi-document-01M64FVZ
base_commit: c957c7d769047352e3bdc35d2558cdc7cf4fcd05
created_at: '2026-10-29T19:18:15.408927+00:00'
subtasks:
- T008
- T009
phase: Phase 2 - Documentation
history:
- at: '2026-10-29T19:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/changelog/2026-10-29-the-service-serves-its-contract.md
execution_mode: code_change
model: ''
owned_files:
- docs/development/http-status-codes.md
- docs/adr/0007-rest-hateoas-contract-of-record.md
- docs/changelog/2026-10-29-the-service-serves-its-contract.md
- docs/changelog/README.md
- contracts/charter/CHANGELOG.md
role: documentarian
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02 – Docs: the document is served

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave
according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `documentarian`
- **Agent/tool**: `claude`

---

## Objective

Say what is now true: the service serves `GET /api/v1/openapi.yaml`. Retire every "501 until #209" statement
that describes the current behaviour, without rewriting dated records.

## Branch Strategy

Planning base and merge target: `feat/209-serve-openapi-document`; lands on `main` by pull request.

## Subtasks

### T008 — Retire the interim wording

- `docs/development/http-status-codes.md` (the 501 row): drop the `getOpenApiDocument` interim case, keep the
  `x-reserved` case.
- `docs/adr/0007-rest-hateoas-contract-of-record.md` (amendment, ~line 40): add a dated note (2026-10-29) that
  #209 serves the bundle; do not edit the amendment text.
- `contracts/charter/CHANGELOG.md`: under the unreleased version, a line that the service now serves the
  document (no version bump, no contract change). Leave the earlier "501 until #209" line as the record of
  that change, or reword it if it sits in the same unreleased section and would read as current.

### T009 — Changelog entry

- `docs/changelog/2026-10-29-the-service-serves-its-contract.md`: frontmatter as the existing entries
  (`okf_version`, `title`, `description` 50–180 chars, `doc_status: active`, `updated: 2026-10-29T00:00:00Z`,
  `type`/`kind: Changelog`, `related` slugs that exist, `audience` profiles under `docs/context/audience/`),
  under 3000 characters, impact first; link #209.
- `docs/changelog/README.md`: newest first.

## Definition of Done

- `uv run python -m scripts.docs.check_docs all`, `npx markdownlint-cli2` and cspell green on the changed files.

## Implementation command

```bash
spec-kitty agent action implement WP02 --agent claude --mission serve-openapi-document-01M64FVZ
```
