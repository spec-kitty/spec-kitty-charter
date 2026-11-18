---
work_package_id: WP05
title: 'Docs: ADR-0042 decision 7 and the work breakdown'
dependencies:
- WP03
requirement_refs:
- FR-007
planning_base_branch: feat/355-closure-in-pack-ruleset
merge_target_branch: feat/355-closure-in-pack-ruleset
branch_strategy: Planning artifacts for this mission were generated on feat/355-closure-in-pack-ruleset. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/355-closure-in-pack-ruleset unless the human explicitly redirects the landing branch.
subtasks:
- T022
- T023
history:
- at: '2026-11-18T07:00:00Z'
  actor: java-jenny
  action: Prompt generated from plan.md and research.md
agent_profile: java-jenny
authoritative_surface: docs/
create_intent: []
execution_mode: planning_artifact
owned_files:
- docs/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest.md
- docs/plans/pack-ecosystem-work-breakdown.md
role: implementer
tags: []
tracker_refs:
- '#355'
---
# Work Package Prompt: WP05 - Docs

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else. Then read, in
`kitty-specs/closure-symmetry-01M7PNFT/`: `spec.md`, `plan.md` (the Implementation Concern Map), `research.md` (D1 to D6) and `data-model.md`.

## Branch Strategy

Planning base: `feat/355-closure-in-pack-ruleset`. Final merge target: `feat/355-closure-in-pack-ruleset`, then `main`
by the pull request. The execution worktree is allocated per computed lane from `lanes.json`.

```bash
spec-kitty agent action implement WP05 --agent claude --mission closure-symmetry-01M7PNFT
```

## Objective

Retire ADR-0042 decision 7's known-gap paragraph and clear the work breakdown's item 8 note, now that validate mode,
the load and both write paths judge the closure with one rule.

## Subtasks

### T022 ADR-0042 decision 7

Add a dated amendment (2026-11-18, #355): the domain gap is closed for the closure and the `Executable` and
`Lifecycle` invariants; validate mode states them as errors through the PACK ruleset; a per-file write refuses with
`409 registration_conflict`. Keep the known-gap paragraph's history visible (struck through or quoted under
"Superseded").

### T023 The work breakdown

Clear item 8's note on the closure gap; record that item 13 is no longer gated on #355.

## Definition of Done

- `check_docs.py`, markdownlint and cspell green on the two files.
