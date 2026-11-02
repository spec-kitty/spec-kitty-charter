---
work_package_id: WP05
title: Page text around the diagrams, and the changelog
dependencies:
- WP02
- WP04
requirement_refs:
- FR-009
- FR-011
- FR-013
- C-001
planning_base_branch: feat/docsite-upgrade-and-pack-ecosystem-diagrams
merge_target_branch: feat/docsite-upgrade-and-pack-ecosystem-diagrams
branch_strategy: Planning artifacts for this mission were generated on feat/docsite-upgrade-and-pack-ecosystem-diagrams. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/docsite-upgrade-and-pack-ecosystem-diagrams unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09
base_commit: 46e2c51543392c8b78273f369fb5cbda0fcdd4a2
created_at: '2026-11-02T21:22:51.755581+00:00'
subtasks:
- T021
- T022
- T023
- T024
history: []
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/changelog/2026-11-02-docsite-dockitty-0-6-0-and-pack-ecosystem-diagrams.md
execution_mode: code_change
model: sonnet
owned_files:
- docs/architecture/descriptor-schemas.md
- docs/architecture/descriptors.md
- docs/architecture/architectural-patterns-applied.md
- docs/changelog/2026-11-02-docsite-dockitty-0-6-0-and-pack-ecosystem-diagrams.md
role: implementer
tags: []
tracker_refs: []
---

# WP05: Page text around the diagrams, and the changelog

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt. From the repository root, the CLI is `uv run --project ../spec-kitty spec-kitty …`.

- **Profile**: `scribe-sally`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

WP03 and WP04 redrew the pack-model diagrams to the decided design of ADR-0048 to ADR-0052, and added `descriptor-mission-type-lifecycle`. WP02 added the API reference and the pack-schema pages. Now make the pages match:
- No page may claim today's sources, data or status for a diagram that now draws the decided design (FR-013).
- Each diagram's page says what it draws, and that the code does not do this yet (FR-009).
- The new diagram and the schema pages are reachable (FR-011).
- One changelog fragment records the whole mission.

Read first:
- `spec.md`: FR-009, FR-011, FR-013.
- `research.md` R-06 "Page text": the exact locations, with line numbers as of the plan. Re-find them, because lines move.
- The WP03 and WP04 hand-offs: the pending lists, and the render recipe.
- The WP02 hand-off: the hub path `docs/architecture/pack-schemas/` and the API URL `/reference/api/main/`.

Style: plain language, active voice, impact first. Match the surrounding pages.

## Branch strategy

- Planning branch and final merge target: `feat/docsite-upgrade-and-pack-ecosystem-diagrams`.
- Run `spec-kitty agent action implement WP05 --agent claude --mission docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09`.
- Author commits as `Stijn Dejongh <stijn.dejongh@sddevelopment.be>` through the `GIT_AUTHOR_*` and `GIT_COMMITTER_*` env vars. Subjects use the form `docs(architecture): …` or `docs(changelog): …`.

## Test economy (DIRECTIVE_041)

No tests. The guards are the docs gates listed in T024.

## Subtasks

### T021 — `docs/architecture/descriptor-schemas.md`

- **Embeds.** For each redrawn diagram embedded here (`pack-write-model`, `descriptor-kinds-overview`, `descriptor-doctrine-kinds`, `descriptor-mission-skill-glossary`, `descriptor-governance`):
  - Replace the "Sources: `src/charter/...`" lines (around 42-46, 76-78, 84, 100-104, 152-153) with the ADRs each diagram draws from.
  - Keep any core source reference only where it explains *today's* shape, and label it so.
  - Reword the "Decided, not built yet" notes beside them (around :106, :119, :155). They currently say the diagram is not redrawn until built; they must now say the diagram shows the decided design (ADR refs), that pending marks show unruled open questions, and that today's code and schemas still have the old shape.
- **Paragraph on older details.** Rewrite or remove the paragraph about known older details in the core-derived diagrams.
- **Mission type section** (around :110): embed `diagrams/descriptor-mission-type-lifecycle.svg` in the same way as the other embeds, with a one-sentence caption naming ADR-0050.
- **Regeneration section** (around 205-209): it runs an unpinned `plantuml` and says the site "has no PlantUML step". Rewrite it with the pinned-jar recipe from the WP03 hand-off (jar URL and sha256 from `scripts/docs/plantuml_pins.json`, Graphviz, Java 21), and say that the docsite now renders schema diagrams at build time through a pinned render service, while these committed SVGs come from the repository jar.
- **Pack schemas.** Add a short pointer to the schema pages (`pack-schemas/`): "each pack schema has its own page, with a diagram".

### T022 — `docs/architecture/descriptors.md`

- The status note at about 37-39, under the `pack-write-model` embed, says "the schemas exist here and the service's load path validates against them". Retitle it as the decided design (ADR-0048 to ADR-0052). Say the drawing is the decided design, and that today's schemas are in `contracts/packs/schemas/` with a page each under `pack-schemas/`.
- At about 85, 279, 319, 388 and 402: add a light pointer to the redrawn diagram. Those notes describe schema text, not the diagram, so do not rewrite them.

### T023 — `docs/architecture/architectural-patterns-applied.md`

- Where it discusses the class or C4 diagrams' "Procedure deferred pending #150" (around :89-95), align the wording with the corrected note: "Procedure stays a kind (ADR-0052 decision 1); not built yet".
- Keep "They show the realized layout": those diagrams still draw the built service.

### T024 — Changelog fragment and gates

- Add `docs/changelog/2026-11-02-docsite-dockitty-0-6-0-and-pack-ecosystem-diagrams.md`. Copy the frontmatter and layout of a sibling fragment (for example `2026-11-02-pack-ecosystem-adrs-accepted.md`): a bold impact-first lead, then before and after.
- Content:
  - The docsite runs on DocKitty 0.6.0.
  - The charter contract is browsable at `/reference/api/main/`, with the raw contract and its checksum.
  - Every pack schema has a page with a diagram.
  - The architecture diagrams draw the decided pack-ecosystem design, with unruled open questions marked.
- Run from the repository root, and fix every finding:
  - `python3 -m scripts.docs.check_docs all`
  - markdownlint and cspell as `docs-cleanliness.yml` runs them (`npx --yes <tool>@<pinned version>`). Add correct new words to `docs/_meta/spelling-words.txt` only.
  - `uv run pytest tests/architectural/test_no_legacy_terminology.py`, if that file exists in this repo. If it does not, find the terminology check that `check_docs.py terms` runs.
- Editing a long page brings its whole markdownlint violation set into scope; fix what the gate reports.

## Definition of Done

- Every R-06 "Page text" location is addressed, with a one-line note per location in the hand-off.
- The new diagram is embedded with a caption, and the schema pages and API reference are linked.
- The regeneration section uses the pinned jar.
- The changelog fragment is in house style.
- All docs gates are clean.
- No page edits outside `owned_files`. C-001 holds: no contract, schema or service file is touched.

## Reviewer guidance

- Read each embed and its surrounding paragraph together. The text must not claim today's sources or counts for a decided-design drawing, and must say the code does not do it yet.
- Check that the links resolve: `check_docs.py links`, plus the built-link check on the PR.

## Post-tasks squad amendments (binding; they override the text above where they conflict)

- **Execution mode** is `code_change`: it edits published docs. Its base must contain both WP02 (lane with WP01) and WP04 (lane with WP03). The orchestrator checks `lanes.json` before claiming it.
- **`docs/_meta/spelling-words.txt`** is owned by WP02. Edit it here only as a recorded out-of-map edit, adding words only.
- **T021** also covers the intro at `descriptor-schemas.md:31-33` ("other diagrams … drawn from core's models"). Line refs in R-06 are approximate: the "Known older details" paragraph is around 42-47, and the pack-write-model source line is around 54. Find each spot by content.
- **T023:** `architectural-patterns-applied.md` has no "#150" text (grep finds none). Verify that it is absent and record that; edit only what refers to the code-structure diagrams' Procedure note, if anything does.
- **Terminology gate:** `tests/architectural/` does not exist. Use `python3 scripts/docs/check_docs.py terms`, which `all` includes.
