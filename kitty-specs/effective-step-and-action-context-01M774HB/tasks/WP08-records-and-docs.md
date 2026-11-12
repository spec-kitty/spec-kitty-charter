---
work_package_id: WP08
title: Records and docs
dependencies:
- WP02
- WP04
- WP05
- WP07
requirement_refs:
- FR-010
- FR-011
- FR-012
- SC-005
- C-007
- C-008
- C-009
planning_base_branch: slice-c/effective-step-context
merge_target_branch: slice-c/effective-step-context
branch_strategy: Planning artifacts for this mission were generated on slice-c/effective-step-context. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-c/effective-step-context unless the human explicitly redirects the landing branch.
subtasks:
- T035
- T036
- T037
- T038
- T039
history:
- timestamp: '2026-11-12T06:30:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/changelog/2026-11-12-effective-step-and-action-context.md
execution_mode: planning_artifact
owned_files:
- docs/adr/0018-action-context-read-and-seam-access-surface.md
- docs/adr/0066-a-step-enhancement-may-contribute-prompt-fragments.md
- docs/architecture/composition-and-activation-rules.md
- docs/conformance/divergence-register.md
- docs/conformance/divergences.yaml
- docs/api/service-contract.md
- docs/development/http-status-codes.md
- docs/development/rest-resource-conventions.md
- docs/glossary/charter/README.md
- docs/context/conceptual-model.md
- docs/changelog/2026-11-12-effective-step-and-action-context.md
- docs/changelog/README.md
- docs/plans/roadmap.md
- docs/plans/next-slices-2026-11-11.md
role: documentarian
tags: []
tracker_refs: []
---

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or run `spec-kitty agent profile show scribe-sally`.

- **Profile**: `scribe-sally`
- **Role**: `documentarian`
- **Agent/tool**: `claude`

## Objective

Make the records say what the service now does (FR-010 to FR-012, SC-005), in the repository's conventions: dated,
additive amendments and annotations; no rewriting of a decision's text.

## Context

- Spec FR-010, FR-011, FR-012; research R1–R10; squad findings D1–D9 (`research/plan-squad-2026-11-12.md`), each with
  its disposition. Follow D-findings exactly; they name file and line.
- Charter `docs/development/issue-tracker.md` for the closeout (not this WP's files, but the PR body uses them).

## Subtasks

### T035 — ADR-0018

- Status block: "**Amendment 2026-11-12** (slice C, operator ruling, Decision Moment `01M774HQ8N5EVYDRTP16MBJCN3`)";
  bump `decision_status` and `updated`.
- D5: the ruling — the effective context lists an in-force directive, tactic or paradigm reached by the walk, scoped by
  the action's mission type, or scoped by no action and no mission type, with its closure by steps 2–5; each item says
  `appliesThrough`; D5's "selection roots and their closure" is what this builds. Note the corpus clause "core(activated)
  ⊆ charter" is superseded for activated cases by the mission-type scope (DIV-245).
- D2: annotate "The mission-type node is not walked" — still true of the offering read; the effective read walks its
  scope as pass 2 (R9's "type-wide guidance reaches an action through R9").
- D3: annotate that the via/hops invariant holds for the offering read and within a pass of the effective form, which
  does not serialize `via` or `hops`.

### T036 — ADR-0066

Dated annotations (2026-11-12), not edits: d8 (OD-1: only an in-force enhancement refuses `prompt_slot_missing`; the
moments are composing reads, registration, the impact-checked writes and the activation writes, OD-4); d7's integrity
bullet (OD-2: a step inherits its mission type's level; `422 override_not_permitted`, `500 pack_unreadable`); d9 (the
read is built; the text is served as stored, which is "unchanged"; `409 mission_type_not_in_force` and why it differs
from the context's 200-empty; the change is `### Breaking`, not additive, because the enum is closed); d4 cited as the
ground of the 409; a "now built" annotation after the 2026-11-09 one (do not edit it); a built marker on the
Follow-ups' "effective-prompt read" line.

### T037 — The rules page

`docs/architecture/composition-and-activation-rules.md`:

- define the status label "Built (slice C)" beside the others (D4) and use it;
- E16 rewritten to the three passes, the ⚠ "Unclear" note removed, pinned by the WP01/WP02 tests and scenarios;
  E12 (enhancers over every pass's entries); E17 (`v3`, `PROMPT`); E18 (the `409`); E6 (the unheld-target branch now
  covers only shapes C37 does not refuse, with its updated pins);
- C22 and C25 amended for the inherited lock; new **C37** (target held) and **C38** (slot declared, in force only);
  F6's precedence (composition codes, then `pack_unreadable`, then `prompt_slot_missing`); G8's list and its "Partly
  built" note, with how "active" is judged per stored activation (R7); the R-REL `relate` path stated;
- section 7 status rows; section 8's intro and its "`prompt_slot_missing` stays reserved" paragraph.

### T038 — Divergence register

- `divergence-register.md`: a dated note on DIV-244 ("lists the artifacts in force the walk reaches" is now the
  ownership rule); **DIV-245**: the effective context lists project-wide and type-scoped D/T/P with their closure;
  mission-type scope is honoured, core ignores it (core leaks type-scoped roots across mission types); one entry for both
  parts (D3). Update the block prose ("DIV-201..244" → 245, the grouping list).
- `divergences.yaml`: the same entry, with its pinning scenario (`effective-context-scoping.feature`) and test names.

### T039 — Contract docs, glossary, model, changelog, plans

- `docs/api/service-contract.md`: the R3 row (decided, research R6: `getEffectiveGraph`, `listArtifactReferences`, the
  registration reference checks; `/offering/graph` waits for the offering-merge minor); the R9 row (the ownership rule,
  the prompt read); the operation lists (C-11) with `getEffectiveStepPrompt`.
- `docs/development/http-status-codes.md` (`409 mission_type_not_in_force`, `prompt_slot_missing` statuses),
  `rest-resource-conventions.md` (the read).
- Glossary (`{#slug}` anchor, `## Term`, definition, `**Meta:**` block): "effective step prompt", "project-wide
  artifact", "scope owner"; amend "effective action context".
- `docs/context/conceptual-model.md` integrity note: a step inherits its mission type's mutability level.
- Changelog page `docs/changelog/2026-11-12-effective-step-and-action-context.md` (front matter as
  `2026-11-12-sharper-refusals.md`) and the `docs/changelog/README.md` index; link the 16-directive table in the
  scoping scenario.
- #439 and #447: the rules page E9 (no "smallest URN"; every overrider, level then URN) and E14, C25/C27 (an
  `overrides` edge's source is activatable), with "Built (slice C)" pins from WP10 and WP11.
- `docs/plans/roadmap.md` (slice C row "match core" → "explain every directive in force", progress) and
  `next-slices-2026-11-11.md` (slice C status).
- Run the docs checks: `python3 scripts/check_docs.py` (all eight), markdownlint-cli2, cspell, as CI runs them.

## Branch Strategy

- Planning branch: `kitty/mission-effective-step-and-action-context-01M774HB`; final merge target: `slice-c/effective-step-context` (then a pull request into `main`).
- Execution worktrees are allocated per computed lane from `lanes.json`; never pick a base by hand.
- Implementation command: `spec-kitty agent action implement WP08 --agent claude --mission effective-step-and-action-context-01M774HB`

## Definition of Done

- Every D-finding of the squad is applied where it says; the docs checks pass.

## Risks and reviewer guidance

- Annotate, never rewrite a decision. Every "Built (slice C)" rule names its pinning tests.
