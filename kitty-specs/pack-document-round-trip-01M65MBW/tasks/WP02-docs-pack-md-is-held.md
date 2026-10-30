---
work_package_id: WP02
title: 'Docs: pack.md is held'
dependencies:
- WP01
requirement_refs:
- FR-011
planning_base_branch: issue-260-pack-md-round-trip
merge_target_branch: issue-260-pack-md-round-trip
branch_strategy: Planning artifacts for this mission were generated on issue-260-pack-md-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-260-pack-md-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T007
- T008
phase: Phase 2 - Docs
history:
- at: '2026-10-30T06:30:00Z'
  actor: claude
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent: []
execution_mode: planning_artifact
owned_files:
- docs/**
- contracts/packs/README.md
role: implementer
tags: []
task_type: implement
tracker_refs: []
---
# Work Package Prompt: WP02 — Docs: pack.md is held

Load the `scribe-sally` profile first (`spec-kitty agent profile show scribe-sally`).

## Subtasks

- **T007** ADR-0042: a dated amendment under Status (decision 8 done, 9's tail, 10's third slot, the title is
  independent of `name`), annotations at decisions 8, 9 and 10, the per-kind row, the consequence and the follow-up.
  ADR-0037: lift the pending qualifiers, add a dated note. ADR-0040: the first stored `markdown` rule and the
  frontmatter-equality ruling (stricter than ADR-0036, open for review). ADR-0036: annotate the `*.md` row.
- **T008** `docs/changelog/2026-10-30-pack-document-round-trip.md` and its hub; `docs/architecture/logical-model.md`;
  `contracts/packs/README.md` (20 schemas); the `file-kinds.yaml` header.

## Definition of Done

`check_docs.py all`, markdownlint-cli2 and cspell (CI's pinned versions) clean.
