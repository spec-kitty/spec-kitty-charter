---
work_package_id: WP03
title: Diagram toolchain and the pack-composition diagrams
dependencies: []
requirement_refs:
- FR-007
- FR-008
- FR-010
- C-002
- C-003
planning_base_branch: feat/docsite-upgrade-and-pack-ecosystem-diagrams
merge_target_branch: feat/docsite-upgrade-and-pack-ecosystem-diagrams
branch_strategy: Planning artifacts for this mission were generated on feat/docsite-upgrade-and-pack-ecosystem-diagrams. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/docsite-upgrade-and-pack-ecosystem-diagrams unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09
base_commit: 2998aca8d3d544f8d800c10ba4411b36e1577377
created_at: '2026-11-02T19:58:58.962096+00:00'
subtasks:
- T012
- T013
- T014
- T015
- T016
history: []
agent_profile: diagram-daisy
authoritative_surface: docs/architecture/diagrams/
create_intent: []
execution_mode: code_change
model: sonnet
owned_files:
- docs/architecture/diagrams/_style.iuml
- docs/architecture/diagrams/pack-write-model.puml
- docs/architecture/diagrams/pack-write-model.svg
- docs/architecture/diagrams/descriptor-kinds-overview.puml
- docs/architecture/diagrams/descriptor-kinds-overview.svg
- docs/architecture/diagrams/descriptor-governance.puml
- docs/architecture/diagrams/descriptor-governance.svg
role: implementer
tags: []
tracker_refs: []
---

# WP03: Diagram toolchain and the pack-composition diagrams

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt. From the repository root, the CLI is `uv run --project ../spec-kitty spec-kitty …`.

- **Profile**: `diagram-daisy`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Set up a render recipe that matches CI, and add the «pending» convention. Then redraw three pack-model diagrams to the decided design of ADR-0048 to ADR-0052 (Accepted 2026-11-02): `pack-write-model`, `descriptor-kinds-overview` and `descriptor-governance`.

The operator chose to **replace** today's picture with the decided design (spec decision DM-01M6EV0R4SHXB96AP33N7ZMDAY). **The one rule:** no diagram draws an open question as decided (FR-008, C-002). A pending element is drawn as the decision text shapes it and is visibly marked with its ADR and open-question number. It is never left out and never drawn as settled.

Read first:
- `spec.md`: US4, FR-007, FR-008, FR-010, C-002, C-003.
- `research.md` R-06, which is your review checklist: per-diagram changes and pending lists, with ADR decision and open-question numbers.
- The five ADRs, `docs/adr/0048…0052-*.md`, especially each one's Decision and Open questions sections. Verify every change and pending item in R-06 against the ADR text before you draw it. R-06 was written by a reviewer and may contain mistakes; the ADR wins.

## Branch strategy

- Planning branch and final merge target: `feat/docsite-upgrade-and-pack-ecosystem-diagrams`.
- Run `spec-kitty agent action implement WP03 --agent claude --mission docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09`.
- Author commits as `Stijn Dejongh <stijn.dejongh@sddevelopment.be>` through the `GIT_AUTHOR_*` and `GIT_COMMITTER_*` env vars.
- Subjects use the form `docs(diagrams): …`.

## Test economy (DIRECTIVE_041)

No tests. The guard is the existing "PlantUML diagrams are current" check (`scripts/docs/check_docs.py plantuml`), run as CI runs it.

## Subtasks

### T012 — Render recipe that matches CI (C-003)

Graphviz is not installed locally. Java 21 is (`~/.sdkman/candidates/java/temurin-21`), and Docker is available.

1. Read the "PlantUML diagrams are current" job in `.github/workflows/docs-cleanliness.yml`: its runner image, Graphviz install, Java version and flags. Also read how `scripts/docs/check_docs.py` (`plantuml` subcommand, around :600-720) renders and compares, and what `PLANTUML_JAR` it reads.
2. Fetch the jar from `scripts/docs/plantuml_pins.json` (`plantuml_jar_url`) and verify `plantuml_jar_sha256`.
3. Render in a container that matches CI as closely as practical, for example `ubuntu:24.04` with `apt-get install graphviz openjdk-21-jre-headless` and the jar mounted. Run the check inside the same container: `PLANTUML_JAR=/plantuml.jar python3 scripts/docs/check_docs.py plantuml`, with `pip install -r scripts/docs/requirements.txt` there first.
4. **Baseline.** Before you change anything, run the check on the untouched tree. It must pass. This proves your recipe reproduces CI. If it fails on unchanged diagrams, the recipe differs from CI: fix the recipe, never the diagrams.
5. Write the exact recipe, as one shell block, into your hand-off. WP04 and WP05 reuse it. Do not commit it as a script; WP05 updates the regeneration section of the docs page.

### T013 — The «pending» convention in `_style.iuml`

Add:
- `skinparam class<<pending>> { BackgroundColor #FFF3CD; BorderColor #B8860B }`, reusing the yellow of the not-yet-built note in `class-diagram.puml` (P12);
- a documented field suffix, `<color:#8A6D00>[pending ADR-00xx OQn]</color>`, for a single pending field or relation label;
- a short comment block explaining when to use each.

Each diagram that uses the convention adds a `legend` line: "Yellow «pending» / [pending …]: decided in the cited ADR, but the cited open question is not ruled." Diagrams that do not use the stereotype must render the same as before. Confirm that with the check.

### T014 — Redraw `pack-write-model` (pages: `descriptor-schemas.md`, `descriptors.md`)

Apply R-06's changes:
- `PackDescriptor.depends_on: [{pack_id, range}]` (48 d1, d4).
- `parent_pack` is lineage only, with a ULID pattern (48 d5-6).
- The artifact file list gains `*.anti-pattern.yaml` (52 d8), `*.glossary.yaml` (49 d1) and `*.lifecycle.yaml` (50 d17), and pending `gate`, `event-registry` and `step-enhancement` files.
- `mission_types/` bundles (48 d16, 50 d4).
- `FragmentKind` `glossary_packs` → `glossary`, and add `lifecycles`.
- Remove the "Refused: glossary" note (R14 superseded by 49 d1).

Pending marks:
- `range` grammar (48 OQ4) and `pack_level` required (48 OQ7)
- glossary layout (49 OQ1), anti-pattern layout (52 OQ2)
- gate (50 OQ2), lifecycle layout (50 OQ4), step-enhancement file (50 OQ8)
- registry layout and node kind (51 OQ3)

Put a title or caption line naming the source ADRs, for example `title Pack write model — decided design (ADR-0048…ADR-0052)`, and add the legend.

### T015 — Redraw `descriptor-kinds-overview`

Apply R-06:
- `GlossaryPack` → `Glossary` (context), plus a node-only `term` (49 d1, d4).
- `AntiPattern`, activatable (52 d8, d13).
- `MissionType` as an ordinary kind (50 d3, d6), and `Lifecycle` (50 d17).
- `EventRegistry` «not activatable» (51 d1, d5).
- A `depends_on` self-association on the pack descriptor (48 d8).
- `rejects` → `AntiPattern` (52 d9).
- **Drop today's built-in edge counts**: they are today's data, not the design.

Pending: `Gate` (50 OQ2), `StepEnhancement` (50 OQ7, OQ8), `EventRegistry` activation and node (51 OQ1, OQ3), the `glossary:` URN (49 OQ3). Add the title and legend.

### T016 — Redraw `descriptor-governance`

Apply R-06:
- `depends_on` and the lineage-only `parent_pack` (48 d1, d5).
- `NodeKind`: `glossary_pack` → `glossary` + `term` (49 d4), and add `lifecycle` (50 d17).
- `required_glossary_packs` and `selected_glossary_packs` renamed (49 d1); check the exact new names in ADR-0049.
- `mission_type` in `selected_*` (50 d6).
- `glossary_scope` retired (49 d11).

Pending: gate (50 OQ2), the `event_registry` node kind (51 OQ3), `pack_level` required (48 OQ7), the core `glossary` node clash (49 OQ3). Add the title and legend.

### After T014 to T016

- Render all three with the recipe, and commit the `.puml` and `.svg` together.
- `check_docs.py plantuml` must pass in the recipe container.
- Self-review each diagram against its R-06 pending list and against the ADRs' open-question lists. In the hand-off, list every pending element with its mark, per diagram.

## Definition of Done

- The recipe reproduces CI: the baseline passes on the unchanged tree. The recipe is in the hand-off.
- `_style.iuml` has the pending convention, and unchanged diagrams still pass the check.
- The three diagrams draw the decided design, cite their ADRs, carry a legend, and mark every pending element. Their SVGs are current, and `check_docs.py plantuml` passes.
- No file outside `owned_files` changed. Page text is WP05's.

## Reviewer guidance

- For each diagram, take each ADR's Open questions list and confirm that every element depending on it is marked, and that nothing settled is marked pending without cause.
- Check that today's counts are gone from the kinds overview.
- Re-run the check with the recipe.

## Post-tasks squad amendments (binding; they override the text above where they conflict)

- **Open-question table (replaces "list pending elements"; R-06 is incomplete).** In your hand-off, include one table with **every** open question of ADR-0048 to ADR-0052. Count them from each ADR's own Open questions section; the reviewer counted about 32. For each one, one row:
  - `ADR | OQ# | short text | diagram(s) touching it | drawn+marked / not drawn (reason)`
  - Every element that depends on an open question and is drawn must carry the mark.
  - The reviewer checks the table against the ADRs and runs `grep -c 'pending ADR-00\|<<pending>>'` on each `.puml`.
- R-06 misses at least ADR-0048 OQ5 and OQ6 ("what a compose is", which governs `depends_on`), ADR-0050 OQ3, OQ6 and OQ10 (decisions 26-28, including `term:` URN references), and ADR-0052 OQ3 (graph-fragment anti-pattern nodes, decision 14).

- **T012 recipe details** (implementer lens):
  - In `ubuntu:24.04`, run `apt-get install -y python3-pip python3-venv curl graphviz openjdk-21-jre-headless` and use a venv (PEP 668).
  - Run the container with `--user $(id -u):$(id -g) -e HOME=/tmp` so the SVGs are not root-owned.
  - Set any env vars `check_docs.py` reads, such as `PLANTUML_RENDER_DIR`, and run `git config --global --add safe.directory` as needed.
  - Pre-build the image once from a throwaway Dockerfile in `/tmp`, and put the Dockerfile in your hand-off so WP04 and WP05 can reuse it.
  - The comparison is semantic (`_normalise_svg`), so distro OpenJDK is acceptable.
