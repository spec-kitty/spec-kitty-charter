---
work_package_id: WP04
title: Doctrine, glossary and mission-type diagrams
dependencies:
- WP03
requirement_refs:
- FR-007
- FR-008
- FR-009
- FR-010
- FR-012
- SC-004
planning_base_branch: feat/docsite-upgrade-and-pack-ecosystem-diagrams
merge_target_branch: feat/docsite-upgrade-and-pack-ecosystem-diagrams
branch_strategy: Planning artifacts for this mission were generated on feat/docsite-upgrade-and-pack-ecosystem-diagrams. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/docsite-upgrade-and-pack-ecosystem-diagrams unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09
base_commit: 6d44acff5cd919062984f74f4a48924f5af0baaf
created_at: '2026-11-02T20:09:19.487376+00:00'
subtasks:
- T017
- T018
- T019
- T020
history: []
agent_profile: diagram-daisy
authoritative_surface: docs/architecture/diagrams/
create_intent:
- docs/architecture/diagrams/descriptor-mission-type-lifecycle.puml
- docs/architecture/diagrams/descriptor-mission-type-lifecycle.svg
execution_mode: code_change
model: sonnet
owned_files:
- docs/architecture/diagrams/descriptor-doctrine-kinds.puml
- docs/architecture/diagrams/descriptor-doctrine-kinds.svg
- docs/architecture/diagrams/descriptor-mission-skill-glossary.puml
- docs/architecture/diagrams/descriptor-mission-skill-glossary.svg
- docs/architecture/diagrams/descriptor-mission-type-lifecycle.puml
- docs/architecture/diagrams/descriptor-mission-type-lifecycle.svg
- docs/architecture/diagrams/class-diagram.puml
- docs/architecture/diagrams/class-diagram.svg
- docs/architecture/diagrams/c4-components.puml
- docs/architecture/diagrams/c4-components.svg
role: implementer
tags: []
tracker_refs: []
---

# WP04: Doctrine, glossary and mission-type diagrams

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt. From the repository root, the CLI is `uv run --project ../spec-kitty spec-kitty …`.

- **Profile**: `diagram-daisy`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

- Redraw `descriptor-doctrine-kinds` and `descriptor-mission-skill-glossary` to the decided design of ADR-0049, ADR-0050 and ADR-0052.
- Add one new diagram, `descriptor-mission-type-lifecycle`: a mission type as a declared state machine (FR-012). No existing diagram draws a mission type.
- Correct the stale "Procedure deferred pending #150" note in the two code-structure diagrams. They otherwise stay as they are, because they draw the built Java service, which the records do not change.

**The one rule** (FR-008, C-002): no diagram draws an open question as decided. Pending elements are drawn as the decision text shapes them, and they are marked with the «pending» stereotype or the `[pending ADR-00xx OQn]` suffix that WP03 added to `_style.iuml`, plus a legend line.

Read first:
- `spec.md`: US4, FR-007 to FR-010, FR-012.
- `research.md` R-06, your checklist.
- ADR-0049, ADR-0050 and ADR-0052 (`docs/adr/`): their Decision and Open questions sections. Verify R-06 against them; the ADR wins.
- WP03's hand-off for the **render recipe**: the CI-matching container. Reuse it exactly.

## Branch strategy

- Planning branch and final merge target: `feat/docsite-upgrade-and-pack-ecosystem-diagrams`.
- This WP shares WP03's lane.
- Run `spec-kitty agent action implement WP04 --agent claude --mission docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09`.
- Author commits as `Stijn Dejongh <stijn.dejongh@sddevelopment.be>` through the `GIT_AUTHOR_*` and `GIT_COMMITTER_*` env vars. Subjects use the form `docs(diagrams): …`.

## Test economy (DIRECTIVE_041)

No tests. The guard is `scripts/docs/check_docs.py plantuml`, run in WP03's recipe container.

## Subtasks

### T017 — Redraw `descriptor-doctrine-kinds`

- Remove the inline anti-pattern classes and the `anti_patterns` fields on procedure and styleguide (52 d4, d16).
- Add `rejects: RejectsRef{type, id, reason}[]` on directive, tactic, paradigm, styleguide and procedure (52 d9). Check the exact list and whether agent profiles are excluded.
- Add an `AntiPattern` class with severity `smell | severe | breaking` (52 d8, d10).
- Pending: the `AntiPattern` field shape (52 OQ2) and the default severity (52 OQ4).
- Add a title naming the ADRs, and the legend.

### T018 — Redraw `descriptor-mission-skill-glossary`

- `GlossaryPack` and `GlossaryTerm` become:
  - `GlossaryContext{schema_version, id, name, description, tags?, applies_to?, terms}`
  - `Term{id, name, definition, examples?, aliases?, avoid?, status, see_also?}`
- Drop `confidence`, `introduced_in_mission`, `provenance` and `banned_synonyms` (49 d2-5). `banned_synonyms` folds into `avoid`; a note is enough.
- `MissionStepContract` loses `gates` and `GateBinding` (50 d12), and the step contract moves into the `mission_types/<type>/` bundle (50 d5).
- `PackSkill` is unchanged.
- Pending: term `status` values (49 OQ4), `applies_to` as advisory (49 OQ2), the file layout (49 OQ1).
- Add the title and legend. Check every field name against ADR-0049's text.

### T019 — New `descriptor-mission-type-lifecycle` (FR-012)

- `@startuml descriptor-mission-type-lifecycle`: the name must equal the file stem, or `check_docs.py` reports it as "does not render" (`check_docs.py:688-697`).
- Include `_style.iuml` as the other diagrams do.
- A class-diagram view of the declared state machine:
  - `MissionType` with its steps, and `Step` (50 d8);
  - `Transition{to, on?, guard?, on_transition?}` (50 d8-11);
  - the reserved terminal state `end` (50 d11);
  - `ActionPoint{emit}` (50 d15-16);
  - a reference to a `Lifecycle` (50 d17);
  - `Gate` as a guard's target (50 d14);
  - `StepEnhancement` (50 d22-23);
  - term references through `term:` URNs (50 d26).
- Optionally add one small state-machine sketch, for example a three-step example as a PlantUML state diagram in the same file, when it materially helps a reader. Keep it labelled "example".
- Pending marks:
  - `end` (50 OQ1), `Gate` (OQ2), the `Lifecycle` shape (OQ4)
  - the `work_package_lifecycle` field name (OQ5)
  - `StepEnhancement` (OQ7, OQ8)
  - `{kind}` spelling in read paths (OQ9), the `emit` spelling (OQ11)
- Check every OQ number against ADR-0050's own list.
- Add the title naming ADR-0050 (and ADR-0051 for `emit`), and the legend.
- Do **not** draw a named-pack instance graph (built-in → foundation …). Most of it is open (48 OQ1, OQ2, OQ3, OQ8).
- Embedding it in a page is WP05's job. Do not edit pages.

### T020 — The #150 note in `class-diagram` and `c4-components`

- `class-diagram.puml` (around :401) and `c4-components.puml` (around :34) say "Procedure deferred pending #150". ADR-0052 decision 1 has ruled #150.
- Reword both to "Procedure stays a kind (ADR-0052 decision 1); not built yet". Change nothing else.
- `c4-components` includes C4-PlantUML from raw.githubusercontent.com, so rendering needs network access. Make sure the recipe container has it, and that the include is pinned as it is today; do not change the include.
- Re-render both.

### After T017 to T020

- Render with the recipe, commit `.puml` and `.svg` together, and run `check_docs.py plantuml` in the recipe container: it must pass.
- In the hand-off, list every pending element per diagram with its mark.

## Definition of Done

- Two diagrams are redrawn and one is new. Each draws the decided design, cites its ADRs, carries a legend, and marks every pending element.
- The #150 note is corrected in both code-structure diagrams, and nothing else in them changed.
- All SVGs are current, and `check_docs.py plantuml` passes in the CI-matching recipe.
- No file outside `owned_files` changed.

## Reviewer guidance

- Walk ADR-0050's Open questions list item by item against the new diagram.
- Check that the code-structure diagrams differ from main only in the note text: `git diff` on the `.puml` shows a single line each.

## Post-tasks squad amendments (binding; they override the text above where they conflict)

- **Open-question table (replaces "list pending elements"; R-06 is incomplete).** In your hand-off, include one table with **every** open question of ADR-0048 to ADR-0052. Count them from each ADR's own Open questions section; the reviewer counted about 32. For each one, one row:
  - `ADR | OQ# | short text | diagram(s) touching it | drawn+marked / not drawn (reason)`
  - Every element that depends on an open question and is drawn must carry the mark.
  - The reviewer checks the table against the ADRs and runs `grep -c 'pending ADR-00\|<<pending>>'` on each `.puml`.
- R-06 misses at least ADR-0048 OQ5 and OQ6 ("what a compose is", which governs `depends_on`), ADR-0050 OQ3, OQ6 and OQ10 (decisions 26-28, including `term:` URN references), and ADR-0052 OQ3 (graph-fragment anti-pattern nodes, decision 14).

- **T019:** `term:` URN references (ADR-0050 decision 26) rest on OQ10 and must carry the pending mark.
- **T020:** the note in `class-diagram.puml` (around :401) reads "Procedure **kind** deferred pending #150." Find both notes by grep, not by line number.
