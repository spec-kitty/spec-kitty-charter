---
work_package_id: WP07
title: 'Records: ADR-0078, service contract, models and open-packs drafts'
dependencies:
- WP06
requirement_refs:
- C-005
- FR-011
planning_base_branch: slice-g-538-profile-resolution
merge_target_branch: slice-g-538-profile-resolution
branch_strategy: Planning artifacts for this mission were generated on slice-g-538-profile-resolution. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-g-538-profile-resolution unless the human explicitly redirects the landing branch.
subtasks:
- T031
- T032
- T033
phase: Phase 4 - Records
history:
- at: '2026-11-15T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: architect-alphonso
authoritative_surface: docs/
create_intent:
- docs/adr/0078-agent-profile-selection-performer-and-effective-profile.md
- kitty-specs/slice-g-profile-resolution-01M7DFBF/research/open-packs-drafts.md
execution_mode: planning_artifact
owned_files:
- docs/adr/0078-agent-profile-selection-performer-and-effective-profile.md
- docs/adr/README.md
- docs/api/service-contract.md
- docs/architecture/logical-model.md
- docs/context/conceptual-model.md
- docs/glossary/**
- kitty-specs/slice-g-profile-resolution-01M7DFBF/research/open-packs-drafts.md
role: architect
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP07 — Records: ADR-0078, service contract, models and open-packs drafts

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `architect-alphonso`
- **Role**: `architect`

## Objective

Record what slice G decided and built (plan P6). Recheck the ADR number against `main` and the open PRs before
writing: 0076 is #106's and 0077 is slice F's.

## Subtasks

### T031 ADR-0078
Records D1 to D7 (`research.md` §6a). It amends:
- ADR-0030: the pin's shape;
- ADR-0032 d5: `operator` is reserved for human performers;
- ADR-0032 d9: the tie-break is unreachable under the ruled order; say why;
- ADR-0069 d4.5: no fallback for `operator`;
- ADR-0053 d22: profiles keep field-merge; step merges stay add-only.

Flag the derived points for review: the composition order, the fold order of several parents, and that D6
supersedes the 2026-10-19 "not a copy of core" steer for inheritance. Regenerate the ADR index (`check_docs.py`
`adr` check).

### T032 Docs
- `docs/api/service-contract.md:284`: the R8 row decided, naming the operations, the scenarios and the corpus cases.
- `docs/architecture/logical-model.md:540-560`: selection is decided, and the "Selection operation (candidate,
  undecided)" bullet goes.
- `docs/context/conceptual-model.md` "Role pin" and "The operator".
- Glossary: *profile selection*, *performer*.
- Run the docs checks: `check_docs.py`, markdownlint-cli2, cspell.

### T033 Open-packs drafts
In `research/open-packs-drafts.md`:
- `open-packs#TBD-retire-sentinel`: `human-in-charge` moves to `performer: human`, `role: operator`, covering
  open-packs#139.
- `open-packs#TBD-specialization-acyclic`.

Do not file these; the orchestrator files them.

## Definition of Done
- The docs checks are green.
- Every ruling is traceable from the ADR to `research.md`.

## Branch Strategy

Planning base and final merge target: `slice-g-538-profile-resolution` (mission branch
`kitty/mission-slice-g-profile-resolution-01M7DFBF`). The execution workspace is allocated per computed lane from
`lanes.json`; do not pick a base branch by hand. Sign every commit. Add no Co-Authored-By trailer and no model names.

```bash
spec-kitty agent action implement WP07 --agent claude --mission slice-g-profile-resolution-01M7DFBF
```

## Read first
`spec.md`, `plan.md`, `research.md` §6a (rulings) and `research/squad-post-plan.md`, all in
`kitty-specs/slice-g-profile-resolution-01M7DFBF/`. Also `.kittify/charter/charter.md` ("Designing during the
rewrite": canonical shape only).
