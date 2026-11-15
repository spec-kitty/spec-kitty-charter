---
work_package_id: WP11
title: Open-packs drafts and closeout evidence
dependencies: [WP10]
requirement_refs:
- FR-020
- C-006
planning_base_branch: slice-f-537-kind-taxonomy
merge_target_branch: slice-f-537-kind-taxonomy
branch_strategy: Planning artifacts for this mission were generated on slice-f-537-kind-taxonomy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-f-537-kind-taxonomy unless the human explicitly redirects the landing branch.
subtasks:
- T050
- T051
agent: claude
history:
- at: '2026-11-13T20:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: kitty-specs/slice-f-kind-taxonomy-01M7B19K/research/
create_intent:
- kitty-specs/slice-f-kind-taxonomy-01M7B19K/research/open-packs-drafts/README.md
- kitty-specs/slice-f-kind-taxonomy-01M7B19K/research/closeout-evidence.md
execution_mode: planning_artifact
owned_files:
- kitty-specs/slice-f-kind-taxonomy-01M7B19K/research/open-packs-drafts/**
- kitty-specs/slice-f-kind-taxonomy-01M7B19K/research/closeout-evidence.md
role: implementer
---

# Work Package Prompt: WP11 - Open-packs drafts and closeout evidence

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/` and adopt it.

Then read these files in `kitty-specs/slice-f-kind-taxonomy-01M7B19K/`:

- `spec.md`: its Intent Summary and the requirements this work package maps to.
- `plan.md`: concern IC-05.
- `data-model.md`.
- `research.md`: R6, R14 and R15.
- `research/squad-post-plan.md`.

## Objective

spec-kitty-open-packs can follow the model. This WP writes draft issues, each with a content sketch, into the mission's research directory, with `open-packs#TBD-<slug>` placeholders for the orchestrator to file (charter.md). It also records the closeout evidence for #112, #514, #520, #515, #533 and #350.

## Context

- The sizes come from the squad (M-M6): 28 step contracts; 26 `requires` edges with 23 action nodes; 80 `scope` edges; writing's 34 bare candidates become `foundation::<id>`; and open-packs has no lifecycle or guard today.
- This session cannot write to open-packs.

## Subtasks

### T050: Drafts

Write each draft under `research/open-packs-drafts/`, with a title, why, what, acceptance, the exact file list, and a generated content sketch (script output) where it helps:

1. `TBD-fold-step-contracts`: fold the 28 step contracts into the substeps of their steps, then remove the files and the 2 graph fragments.
2. `TBD-step-requires-edges`: add 26 edges and 23 action nodes (cave-dev 8, qa 7, software-dev 8, research 2, documentation 1).
3. `TBD-delegation-scope-edges`: add the 80 `scope` edges from each step's action (list in `delegation-edge-coverage.md`, corrected).
4. `TBD-pre-review-guard`: author foundation's work-package lifecycle and the `spec-kitty-pre-review` guard, then attach it (ADR-0054 decision 12).
5. `TBD-validator-and-docs`: update `scripts/ci/validate_pack_schemas.py` and its tests for the retired kind and relations, plus `docs/packs/*` and `docs/guides/author-a-charter-pack.md`.

Add a `README.md` index with the ordering note: open-packs raises its pin only after F is merged and released (ADR-0070).

### T051: Evidence

- Re-run `research/edge-census.py` against a scratch copy of open-packs with drafts 1 to 3 applied by a script, if you can. It should refuse 0 edges, find 0 unlinked steps and 0 unscoped candidates.
- Write `research/closeout-evidence.md`: for each issue (#112, #514, #520, #515, #533, #350), the PR file or scenario that shows the behavior. Also the narrowing comment text for #513, and the ticks for #94 and #537.

## Validation

- Each draft is complete enough for the orchestrator to file it as-is.
- The evidence file names a test or scenario for each issue.

## Definition of Done

- T050 and T051 are done and committed. This WP holds planning artifacts only.
- Every commit is signed, carries no `Co-Authored-By` trailer and names no model.
- The tests this work package touches are green. Gradle runs in the foreground with `--max-workers=1`, one run at a time.
- Out-of-map edits are recorded with a one-line rationale in the commit message.

## Risks and reviewer guidance

- **The scratch run modifying the real open-packs checkout.** Copy to the scratchpad first.

## Branch Strategy

Planning happened on `slice-f-537-kind-taxonomy`, which is also the final merge target. `finalize-tasks` computes the
execution lanes into `lanes.json`, and each lane gets its own worktree. Do not choose a base branch by hand.

```bash
spec-kitty agent action implement WP11 --agent claude --mission slice-f-kind-taxonomy-01M7B19K
```

## Coordination

- **Slice C** is already on `main` (PR #566), and this branch is based on it.
- **#554 and #556 (epic #549).** Until #554 lands, the read check stays in `PackPolicy`, and whichever lands second rehomes it. Check the open PRs before each WP.
- **#551.** It changes the `kernel.validation` API; the second of the two to land rebases.
- **#106.** It renames `glossary_pack` to `glossary`; the second to land rebases.
- **#552.** It drops `MissionStepContractIdentifier` from its list.
- **Slice D close-out (#367, #371).** It runs in parallel, so rebase or merge `main` often. Never force-push a pushed branch.
