---
work_package_id: WP11
title: Phase 2 · Docs for the compose rules and the effective-prompt read
dependencies:
- WP06
- WP08
- WP10
requirement_refs:
- FR-012
- SC-005
planning_base_branch: issue-424-428-adr-0066-service-alignment
merge_target_branch: issue-424-428-adr-0066-service-alignment
branch_strategy: Planning artifacts for this mission were generated on issue-424-428-adr-0066-service-alignment. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-424-428-adr-0066-service-alignment unless the human explicitly redirects the landing branch.
subtasks:
- T049
- T050
- T051
phase: Phase 2 (after PR C merges)
agent: claude
history:
- timestamp: '2026-11-09T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/api/service-contract.md
create_intent: []
execution_mode: code_change
model: sonnet
owned_files:
- docs/api/service-contract.md
- docs/development/http-status-codes.md
- docs/development/rest-resource-conventions.md
- site/.contextive/definitions.yaml
role: implementer
tags: []
tracker_refs:
- '#424'
- '#428'
---
# WP11: Phase 2 · Docs for the compose rules and the effective-prompt read

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or read
`/home/user/spec-kitty-open-packs/packs/built-in/agent_profiles/scribe-sally.agent.yaml` in full.

- **Profile**: `scribe-sally`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⛔ Blocked until

**This WP MUST NOT be started until PR C of `layer-merge-and-effective-reads-01M6R0J6` — its WP27, WP28 and WP29,
including #429, the OD-6 E6 amendment and the OD-9 shared "pack holds step" predicate — is merged into `main`, and
this mission branch has been rebased onto that `main`,** and until WP08 and WP10 are approved. Operator ruling D2 /
C-004.

PR C surfaces this WP describes or links: the rules page's E6 text (OD-6, written by PR C — link, do not rewrite),
E12 (enhancers of an action, both ways), E15/E16 rows (merge-hot), the action-context read and its `NodeReference`,
`action_not_found`.

## Objective

Bring the docs level with Phase 2: the compose rules (FR-008, FR-009 with OD-1, OD-2, OD-4, OD-9) and the
effective-prompt read (FR-010, OD-5, OD-7, OD-8) on the rules page; the operation and status lists that name
operations (C-11); the glossary term "effective step prompt" with one name per concept in every layer (A13); and the
Phase 2 part of the mission's dated changelog page.

Implementation command:
`spec-kitty agent action implement WP11 --agent claude --mission adr-0066-step-enhancement-service-alignment-01M6ZRYK`

## Context

- **Concern:** IC-08 of `plan.md`, its Phase 2 part ("the rest with IC-06/IC-07"), and IC-05's docs surfaces (C-11).
- **Requirements:** FR-012, SC-005.
- **Rulings:** OD-1 (B), OD-2 (A), OD-4 (i), OD-5, OD-6 (PR C's text), OD-7, OD-8, OD-9, A13, C-11.
- **Read first:** `plan.md` IC-05 ("Lists that name operations"), IC-06, IC-07, IC-08; the WP07, WP08 and WP10
  hand-off notes (what was actually built and any name drift from PR C); WP06's hand-off note (the final changelog
  filename).

### Files shared with other work packages

Make only the edits named, each with `out-of-map: <file> — <why>`:

- `docs/architecture/composition-and-activation-rules.md` (WP06) — fill the "Compose rules" and "Effective step
  prompt" parts of the "Step enhancements and prompt fragments" section.
- `docs/changelog/2026-11-09-step-enhancements-and-prompt-fragments.md` (WP06; use the final name from WP06's hand-off)
  — add the Phase 2 outcomes, staying under 3000 characters.

## Project constraints (apply to every subtask)

- **Never edit generated hubs** (`docs/adr/README.md`, `docs/changelog/README.md`, `docs/glossary/**/README.md`). The
  glossary page regenerates from `site/.contextive/definitions.yaml` (ADR-0029); do not hand-edit the page.
- **Describe what was built**, cross-checked against the merged code and contract, not the plan alone.
- **A divergence entry** only if review found the read differs from core's model in a way the register tracks (next
  free id at `main` was DIV-238; PR C may have taken it). If one is needed, it is outside `owned_files`: escalate to the
  run lead first.
- **Docs checks:** `python3 scripts/docs/check_docs.py`, `npx markdownlint-cli2 "docs/**/*.md"`,
  `npx cspell --no-progress "docs/**/*.md"`.
- **Commits** end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01JuXv6fZ4jMGna4kLBUA2jr`; no AI model name in commits or files.

## Branch Strategy

- Planning branch and merge target: `issue-424-428-adr-0066-service-alignment`.
- Lanes from `lanes.json`. Claimable once WP06, WP08 and WP10 are approved or done (and the "Blocked until" condition
  holds). It is the last WP of the mission.

## Subtasks

### T049: Rules page — compose rules and the effective step prompt

**Purpose:** the reference page states the compose-time and read-time rules.

**Steps (shared edit on `composition-and-activation-rules.md`):**

1. **Compose rules:**
   - `prompt_slot_missing`: an **active** enhancement naming a slot the prompt in force does not declare (including
     after an override drops it); `500` on composing reads, `422` at registration and on writes that rebuild the merge;
     an inactive one does not refuse (OD-1 B, OD-4).
   - Target resolution: qualified in a pack of the `depends_on` closure that holds the step; bare in the enhancing pack
     itself, else `pack_reference_unqualified`; "holds the step" is the shared OD-9 predicate (link its rule id).
   - Integrity: a step inherits its mission type's mutability level; `422 override_not_permitted` at registration,
     `500 pack_unreadable` on reads, as C21 (OD-2).
   - Only the winning copy of a same-id enhancement contributes (A11).
2. **Effective step prompt:** E12 for the read (enhancers of `action:M/s`, active only, no pull either way, d4); the
   answer (prompt in force as canonical text, every declared slot, ordered fragments by level, descriptor name,
   enhancement id); `409 mission_type_not_in_force` (OD-5); `diagnostics` (OD-7); the renderer sentence for a legacy
   non-empty slot (OD-8); the `prompt` rel on the action context. Link PR C's E6 text rather than restating it.

**Validation:** `check_docs.py` (links and anchors), markdownlint, cspell.

### T050: Operation and status lists; the glossary term

**Purpose:** C-11 and A13.

**Steps:**

1. `docs/api/service-contract.md` (around :88 and :256): list `getEffectiveStepPrompt` with the effective reads.
2. `docs/development/http-status-codes.md`: the `304`, `400`, `404`, `409` (`mission_type_not_in_force`) and `500`
   rows name the new operation where they list operations.
3. `docs/development/rest-resource-conventions.md` (:137): the operation list.
4. `site/.contextive/definitions.yaml`: add "Effective step prompt" (the prompt in force plus, per slot, the active
   fragments in order; served, not rendered by the charter; avoid "rendered prompt"), next to the existing "Step
   enhancement" entry; check "prompt fragment", "slot" and "prompt in force" entries match the spec's Domain Language,
   adding any that are missing. One name per concept: "effective step prompt", `EffectiveStepPrompt`,
   `EffectivePromptSlot`, `EffectivePromptFragment`.
5. Run the glossary generation the repo documents (if it is part of the docs build, let it regenerate; do not commit a
   hand-edited glossary page).

**Validation:** docs checks, including `glossary-generated`.

### T051: The changelog page (Phase 2) and the final docs run

**Purpose:** one dated page records the whole mission.

**Steps:**

1. Shared edit on the WP06 changelog page: add the Phase 2 outcomes — the compose refusals, the effective-prompt read,
   `409 mission_type_not_in_force`, `_links.prompt` — with links to the rules page section. Keep it under 3000
   characters (shorten Phase 1 prose if needed, keeping its facts).
2. Run all docs checks over `docs/**` and the mission's Markdown:
   `npx markdownlint-cli2 "docs/**/*.md" "kitty-specs/adr-0066-step-enhancement-service-alignment-01M6ZRYK/**/*.md"`.

**Validation:** all docs checks green.

## Test strategy

- Docs checks are the gate; no positive control is claimed for prose (T-19). Cross-check every code, status and
  operation name against the merged contract (`contracts/charter/openapi.yaml`, `CharterRefusalCode.yaml`).

## Gates (keep green)

- `python3 scripts/docs/check_docs.py` (all eight checks), markdownlint-cli2, cspell, `RelationTableParityTest`
  unaffected (WP10 owns the HATEOAS table).

## Definition of Done

- Every subtask done and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- Docs checks green; no generated hub edited; nothing outside `owned_files` beyond the two listed shared edits.

## Risks

- PR C may have renamed a rule id or symbol: cite what is merged, not the plan's working names.
- The 3000-character changelog limit across two phases.

## Reviewer guidance

- Check each rule statement against the merged code and its scenario.
- Check the glossary entry is authored in `definitions.yaml`, not on the generated page.
