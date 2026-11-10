---
work_package_id: WP06
title: 'Phase 1 · Rulings recorded: ADR annotations, load rules, schema pages, changelog'
dependencies:
- WP04
- WP05
requirement_refs:
- FR-012
- C-005
- C-006
- SC-005
planning_base_branch: issue-424-428-adr-0066-service-alignment
merge_target_branch: issue-424-428-adr-0066-service-alignment
branch_strategy: Planning artifacts for this mission were generated on issue-424-428-adr-0066-service-alignment. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-424-428-adr-0066-service-alignment unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-adr-0066-step-enhancement-service-alignment-01M6ZRYK
base_commit: ec727769c52f15f8ae610c1275f1afc0a4184b1f
created_at: '2026-11-09T21:01:16.277586+00:00'
subtasks:
- T027
- T028
- T029
- T030
phase: Phase 1 (before PR C)
agent: claude
history:
- timestamp: '2026-11-09T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/architecture/composition-and-activation-rules.md
create_intent:
- docs/changelog/2026-11-09-step-enhancements-and-prompt-fragments.md
execution_mode: code_change
model: sonnet
owned_files:
- docs/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions.md
- docs/adr/0066-a-step-enhancement-may-contribute-prompt-fragments.md
- docs/architecture/composition-and-activation-rules.md
- docs/architecture/pack-schemas/step-enhancement.md
- docs/architecture/pack-schemas/prompt-fragment.md
- docs/changelog/2026-11-09-step-enhancements-and-prompt-fragments.md
role: implementer
tags: []
tracker_refs:
- '#424'
- '#428'
- '#434'
---
# WP06: Phase 1 · Rulings recorded: ADR annotations, load rules, schema pages, changelog

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or read
`/home/user/spec-kitty-open-packs/packs/built-in/agent_profiles/scribe-sally.agent.yaml` in full.

- **Profile**: `scribe-sally`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Make the docs say what the service does after phase 1 and record the phase-1 rulings: the kept layout on ADR-0053
open question 2 (D7), the guard and emit shape on ADR-0066 (D6, #434), the load moment of ADR-0066 decision 8 as
built (OD-3 (a), OD-7), the load rules on the rules page, the two pack-schema pages, and one dated docs changelog
page. No new ADR is written (D7: ADR-0067 is not taken).

**Moved out by the split (operator ruling, 2026-11-09):** the ADR-0066 decision 8 annotation for OD-1 (only an active
enhancement refuses), the decision 7 annotation for OD-2 (a step inherits its mission type's mutability level), and
the conceptual-model integrity note are the phase-2 mission's, written when its compose checks are built. This WP does
not write them. `docs/context/conceptual-model.md` is not owned or edited by this mission.

Implementation command:
`spec-kitty agent action implement WP06 --agent claude --mission adr-0066-step-enhancement-service-alignment-01M6ZRYK`

## Context

- **Concern:** IC-08 of `plan.md`, its phase-1 part ("the ADR-0053/D6 annotations and schema pages with IC-01/IC-02",
  plus the load-moment note). Read IC-08, the "Split into two missions" section and the ruled table in full.
- **Requirements:** FR-012 (phase-1 part), SC-005 (the #428 guard/emit item closed by the recorded shape and #434).
- **Rulings:** D6 (fields `add_guards`, `add_lifecycle_guards`, `emits`, mirroring ADR-0054 d10 and ADR-0050 d15–16;
  not edges; built under #434), D7 (layout `missions/<type>/enhancements/` per mission type, directory checked
  against the edge), OD-3 (a) (how the load codes reach the wire), OD-7 (registration `diagnostics`), T-19 (no
  positive control claimed for ADR annotation text). OD-1 / D13 and OD-2 / D14 are ruled but recorded by the phase-2
  mission.
- **Read first:** `plan.md` IC-08; `research.md` D6, D7; the two ADRs in full; `docs/_meta/doc-rules.yaml`
  (frontmatter, changelog naming and the 3000-character limit, generated hubs); the decision moments under the
  mission's `decisions/`.

### Files shared with other work packages

None in this mission. The phase-2 mission (#438) will later edit `composition-and-activation-rules.md` and add
to the docs changelog; leave a clear section structure for it (a "Step enhancements and prompt fragments" section
with a "Load rules" sub-heading, so "Compose rules" and "Effective step prompt" can follow).

## Project constraints (apply to every subtask)

- **Never edit generated hubs:** `docs/adr/README.md`, `docs/changelog/README.md`, `docs/glossary/**/README.md`
  (ADR-0047, `glossary-generated`). Terms are authored in `site/.contextive/definitions.yaml`, not on pages (the
  phase-2 mission (#438) handles the one new term).
- **Annotations are dated and additive.** Keep each ADR's existing text; add a dated block ("Annotation 2026-11-09"
  or the file's existing "Amendment YYYY-MM-DD" style — follow the file). Cite the ruling and decision id.
- **Describe built behaviour only.** Phase-2 behaviour (compose refusals, the read, step integrity) is not described
  here, neither on the rules page nor in an ADR annotation; the phase-2 mission records it when it is built.
- **Docs checks (the closeout's pinned versions):** `python3 scripts/docs/check_docs.py all`,
  `npx markdownlint-cli2@0.18.1 "docs/**/*.md" "kitty-specs/adr-0066-step-enhancement-service-alignment-01M6ZRYK/**/*.md"`,
  `npx cspell@10.4.0 --no-progress "docs/**/*.md"`. A markdownlint finding in the mission's own Markdown is reported
  to the run lead (this WP owns no mission file), not fixed here. Add a word to `docs/_meta/spelling-words.txt` only if unavoidable, and
  then list that edit as an escalation note to the run lead (it is outside `owned_files`).
- **PR C boundary (C-004):** do not edit `docs/architecture/logical-model.md` (WP29's). The E6 amendment text is PR C's
  (OD-6); do not write it.
- **Commits** end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01E5wJUD4c1fuEbXL3umMvKS`; no AI model name in commits or files.

## Branch Strategy

- Planning branch and merge target: `issue-424-428-adr-0066-service-alignment`.
- Lanes from `lanes.json`. Claimable once WP04 and WP05 are approved or done (the pages describe their wire
  behaviour).

## Subtasks

### T027: ADR-0053 open question 2 — the kept layout (D7)

**Purpose:** record that "beside the kind" for steps is the per-type `enhancements/` directory.

**Steps:**

1. In `docs/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions.md`, under "Open questions", item 2
   ("Where an enhancing artifact lives", around line 247, already ruled 2026-11-03), add a dated annotation
   (2026-11-09): for steps, the place is `missions/<type>/enhancements/<id>.step-enhancement.yaml` with its
   `*.prompt-fragment.md` companions beside it — per mission type, not a pack-wide area. The `<type>` directory must
   equal the type of the enhancement's single `enhances` edge, and is checked against the edge, never read instead of
   it (ADR-0064, C-003). Ids are unique per pack (D8).
2. Do not renumber or rewrite the existing text; keep the struck-through question and its 2026-11-03 ruling.

**Validation:** `check_docs.py` (frontmatter, links), markdownlint.

### T028: ADR-0066 — guard/emit shape and the load-moment note

**Purpose:** close #428's guard/emit checklist item by a recorded shape (SC-005) and record how the load codes reach
the wire. The d8 (OD-1) and d7 (OD-2) annotations are the phase-2 mission's; do not write them here.

**Steps:**

1. In `docs/adr/0066-a-step-enhancement-may-contribute-prompt-fragments.md`, add dated annotations (2026-11-09), in
   the file's existing style:
   - **Guards and emits (D6):** a step enhancement's guard and emit additions are fields, not edges:
     `add_guards`, `add_lifecycle_guards`, `emits`, mirroring ADR-0054 d10 and ADR-0050 d15–16. The schema stays at
     `prompt_fragments` until #434 builds them; an enhancement with no fragment is refused while they are not built
     (D12).
   - **Decision 8, load moment:** how the three load codes reach the wire, in the words of the mission's
     `contracts/README.md`, "How a load fault reaches the wire (canonical)" (OD-3 (a)): a per-file violation code
     inside `422 validation_failed` at registration; `500 pack_unreadable` naming the first faulty file on composing
     reads (the same load seat serves both); the unversioned `prompt_slot_not_empty` warning in the registration
     result's `diagnostics` (OD-7). Do not restate more than that; link the rules page for the detail.
2. Keep `decision_status` and `decision_date` frontmatter as they are unless `doc-rules.yaml` requires an `updated`
   bump; follow the rule.

**Validation:** `check_docs.py`; the ADR index check (the hub is generated — do not touch it).

### T029: Rules page (load rules) and pack-schema pages

**Purpose:** the reference pages say what Phase 1 serves.

**Steps:**

1. `docs/architecture/composition-and-activation-rules.md`: add a "Step enhancements and prompt fragments" section
   with a "Load rules" sub-heading: FR-004 (fragment content), FR-005 (structure, D11 companions, orphans), FR-006
   (one `enhances` edge to an `action:`, no `overrides` edge from the enhancement (R-OVR: an enhancement only adds),
   directory = edge type, id unique per pack with the second file withheld (operator ruling A2), at least one entry;
   id = stem is the store's stem rule, a schema violation and not `prompt_fragment_invalid`, operator ruling A1),
   FR-007 (slot grammar, the descriptor version gate, the warning), each with its code and moment (V, L) as in
   `data-model.md`; the precedence of P9. Do not add "Compose rules" or "Effective step prompt" sub-headings: the
   phase-2 mission adds them when it builds them. Do not describe unbuilt behaviour as built.
2. `docs/architecture/pack-schemas/step-enhancement.md`: the shape (id, `schema_version`, `prompt_fragments`), the
   layout (D7), the rules and codes, guards/emits deferred to #434, no target field (C-003).
3. `docs/architecture/pack-schemas/prompt-fragment.md`: body only, no frontmatter, no marker of any id (fences
   included), LF only (CR/BOM refused), named by exactly one entry, canonical render is its meaning.

**Validation:** `check_docs.py`, markdownlint, cspell.

### T030: The dated docs changelog page

**Purpose:** one changelog page for this mission (the phase-2 mission writes its own).

**Steps:**

1. Create `docs/changelog/2026-11-09-step-enhancements-and-prompt-fragments.md` (rename to the landing date if later;
   record the final name in your hand-off note) with the frontmatter the other changelog pages carry and a title the
   generated index can list. Under 3000 characters. Content: step enhancements are held, round-trip and are
   selectable (#424, #428); the load checks and their codes; the registration `diagnostics`; the raised definition
   change; links to the rules page and the two ADR annotations.
2. Do not edit `docs/changelog/README.md` (generated).

**Validation:** `check_docs.py` (changelog naming, title, length), markdownlint, cspell.

## Test strategy

- Docs checks are the gate. FR-012's positive control for the **schema description** is
  `StepEnhancementSchemaDescriptionTest` (WP04); the ADR annotations have no positive control and none is claimed
  (T-19).

## Gates (keep green)

- `python3 scripts/docs/check_docs.py all` (all eight checks), `npx markdownlint-cli2@0.18.1 "docs/**/*.md"
  "kitty-specs/adr-0066-step-enhancement-service-alignment-01M6ZRYK/**/*.md"`, `npx cspell@10.4.0 --no-progress
  "docs/**/*.md"` (the closeout's pinned versions).

## Definition of Done

- Every subtask done and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- Docs checks green; no generated hub edited; nothing outside `owned_files` (escalate a spelling-words edit).
- Hand-off note names the final changelog filename (the phase-2 mission links to it).

## Risks

- Describing phase-2 behaviour (compose refusals, the read, step integrity) misleads readers until the phase-2
  mission ships it: leave it out of every page this WP touches.
- The changelog page's 3000-character limit: keep it to outcomes and links.

## Reviewer guidance

- Check each annotation is dated, cites its ruling and decision id, and leaves the original text intact.
- Check the guard/emit annotation names the three fields and #434 (SC-005).
- Check no generated hub, no `logical-model.md` change and no `conceptual-model.md` change.
- Check no d8 (OD-1) or d7 (OD-2) annotation was written: those are the phase-2 mission's.
