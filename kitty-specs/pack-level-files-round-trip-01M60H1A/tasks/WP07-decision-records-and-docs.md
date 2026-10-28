---
work_package_id: WP07
title: "ADR-0043, amendment notes, changelog, glossary and architecture docs"
dependencies:
- WP06
requirement_refs:
- FR-016
- FR-017
- NFR-003
- SC-005
planning_base_branch: feat/195-pack-level-round-trip
merge_target_branch: feat/195-pack-level-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/195-pack-level-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/195-pack-level-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T026
- T027
- T028
- T029
agent: claude
history:
- at: '2026-10-28T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/adr/0043-pack-level-files-sanitized-shapes-and-the-derived-manifest.md
- docs/changelog/2026-10-28-pack-level-files-round-trip.md
execution_mode: planning_artifact
owned_files:
- docs/adr/0043-pack-level-files-sanitized-shapes-and-the-derived-manifest.md
- docs/adr/0031-requirement-is-authored-as-a-preset.md
- docs/adr/0036-round-trip-fidelity-is-equality-of-meaning.md
- docs/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam.md
- docs/adr/0040-content-form-and-payload-kinds.md
- docs/adr/README.md
- docs/changelog/2026-10-28-pack-level-files-round-trip.md
- docs/glossary/**
- docs/architecture/logical-model.md
- docs/architecture/descriptor-schemas.md
- docs/architecture/packs-resource-model.md
role: documentarian
tags: []
task_type: implement
---
# Work Package Prompt: WP07 — ADR-0043, amendment notes, changelog, glossary and architecture docs

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `documentarian`

## Objective

Record the decisions (spec FR-017) and keep the docs living. The docs checks must stay green: `check_docs.py all`,
markdownlint-cli2 and cspell.

## Subtask T026 — ADR-0043

**Title:** "Pack-level files: sanitized storage shapes, presets with must-have items, and the derived manifest".

**Status:** Proposed, pending operator acceptance on #195.

**Decisions:**

1. The sanitized-shape rules S1–S8 for pack-level storage objects.
2. The preset shape, with `must_have` per item. This answers ADR-0031's two open questions, and its decision 6 on
   representation:
   - required items are marked per item;
   - "governing" is derived;
   - `required_*` is refused by the closed schema;
   - the migration is #206.
3. Empty activation lists are refused (one spelling).
4. `pack.yaml` is closed (ADR-0036 decision 3's follow-up, done).
5. The manifest:
   - the authored-pack variant only;
   - its derived fields and their recipes (LF content hash; RFC 8785 manifest hash);
   - recomputed on write, never checked on read, verified by the gate;
   - membership is carried as read, with #110 owning regeneration.
6. The rule for kinds without a schema. This closes ADR-0036 decision 6 and open question 3: every covered file is a
   `files` rule with a closed schema, a `payloads` rule of a built non-yaml form, or an exclusion. A YAML file is
   never held without a schema.
7. Validate mode agrees with load (D9), and the divergence from core's `validate_pack` is registered.
8. `pack.md` uses #194's `markdown` form (pending PR-A).
9. The fingerprint order.
10. The `Pack` slots, with the rule of three noted.

**Also:** the divergence table and the upstream issues (placeholders); merged storage objects that break the
sanitized rule (grounding-squad §6) as an epic #186 follow-up; alternatives; consequences.

## Subtask T027 — Amendment notes

Use the status-section amendment style of ADR-0036's #193 note:

- **ADR-0031:** its open questions are answered, by ADR-0043.
- **ADR-0036:** add per-kind table rows for `preset` and `pack-manifest`, with their derived fields. Decision 6 and
  open question 3 are answered, and decision 3's follow-up is done.
- **ADR-0037:** decision 4's "pack-level files (#195)" is done; `LoadedPack.manifest`; the undeclared-keys write
  refusal is gone.
- **ADR-0040:** the fingerprint tail order.

Add an index line for ADR-0043 in `docs/adr/README.md`.

## Subtask T028 — Changelog and glossary

- `docs/changelog/2026-10-28-pack-level-files-round-trip.md`: impact first, in the style of the sibling entries.
- Glossary:
  - "activation preset" (updated);
  - "preset item";
  - "must-have mark";
  - "governing preset";
  - "pack manifest";
  - "derived field".

  Edit the glossary's `definitions.yaml` append-only, and update `cspell.json` if needed (an out-of-map edit,
  recorded).

## Subtask T029 — Architecture pages and the conformance divergence

- `logical-model.md`: pack-level content.
- `descriptor-schemas.md`: the two new schemas and the closed descriptor.
- `packs-resource-model.md`, if it lists files.
- Add a new divergence entry for validate mode against core, in the conformance register. This is an out-of-map edit,
  recorded: WP03 owns the file and edits another section.

## Definition of Done

- `python3 scripts/docs/check_docs.py all`, `npx markdownlint-cli2` and `npx cspell` are green on the changed docs.
- `./gradlew verifyAll` is green on Java 25.

## Branch Strategy

Planning base and merge target: `feat/195-pack-level-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP07 --agent claude --mission pack-level-files-round-trip-01M60H1A`.

Tests come first (charter: test first, no fake green). Each refusal is paired with a clean control on the same fixture.
`contracts/charter/**` is never edited (C-001). Shared files are edited append-only, except the edits plan.md lists.
