---
work_package_id: WP07
title: Documentation
dependencies:
- WP06
requirement_refs:
- FR-012
- NFR-001
planning_base_branch: issue-194-mission-type-consolidation
merge_target_branch: issue-194-mission-type-consolidation
branch_strategy: Planning artifacts for this mission were generated on issue-194-mission-type-consolidation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-194-mission-type-consolidation unless the human explicitly redirects the landing branch.
subtasks:
- T023
- T024
history: []
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent: []
execution_mode: code_change
owned_files:
- docs/**
- contracts/charter/CHANGELOG.md
- contracts/charter/scenarios/**
- contracts/packs/README.md
- site/.contextive/definitions.yaml
role: documentarian
tags: []
task_type: implement
tracker_refs: []
---
# Work Package Prompt: WP07 — Documentation

## Do This First: Load Agent Profile

Load the agent profile `scribe-sally` (`spec-kitty agent profile show scribe-sally`) and follow its guidance.

## Objective

Divergence register entries (mission type as an artifact kind; the retired families; `/relationships` accepting a `mission_type:` source) and the contract CHANGELOG line with a `/relationships` scenario; `docs/architecture/descriptors.md` and `descriptor-schemas.md`; `contracts/packs/README.md` schema count; glossary and `docs/context/domain.md` (mission step, on-demand step, action, step contract, step guidelines, recipe ingredients now in `references`); `docs/development/epic-186-sanitized-shape-follow-ups.md` (mission templates; step-contract `action`/`mission` keys); a changelog entry and its index line. Docs checks, markdownlint 0.18.1 and cspell green.

## Context

- ADR-0044 (`docs/adr/0044-mission-types-consolidated-into-one-sanitized-shape.md`), [spec.md](../spec.md),
  [plan.md](../plan.md), [data-model.md](../data-model.md), research dispositions in [research.md](../research.md).
- Test first: write the failing acceptance and unit tests named above before the code. Zero new suppressions; methods
  at complexity 15 or below; Javadoc on every type and member (doclint).

## Definition of Done

- The named tests exist, failed first, and pass.
- `python3 scripts/docs/check_docs.py all` green (and `check` stays green: no pinned path is affected before WP06).
- Owned files only; shared-file edits in one block.
