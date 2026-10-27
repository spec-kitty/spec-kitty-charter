---
work_package_id: WP08
title: Decision record and living docs
dependencies:
- WP06
- WP07
requirement_refs:
- FR-014
- C-001
planning_base_branch: feat/asset-template-round-trip
merge_target_branch: feat/asset-template-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/asset-template-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/asset-template-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T032
- T033
- T034
- T035
phase: Phase 3 - Proofs, gate and records
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: architect-alphonso
role: implementer
authoritative_surface: docs/
execution_mode: code_change
owned_files:
- docs/adr/0040-content-form-and-payload-kinds.md
- docs/adr/0036-round-trip-fidelity-is-equality-of-meaning.md
- docs/adr/README.md
- docs/architecture/logical-model.md
- docs/changelog/2026-10-27-asset-and-template-round-trip.md
- site/.contextive/definitions.yaml
- cspell.json
create_intent:
- docs/adr/0040-content-form-and-payload-kinds.md
- docs/changelog/2026-10-27-asset-and-template-round-trip.md
tags: []
task_type: implement
---
# Work Package Prompt: WP08 — Decision record and living docs

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `architect-alphonso`
- **Role**: `implementer`

## Objective

Record the decisions of this mission and keep the living docs true (DIRECTIVE_003, DIRECTIVE_037): ADR-0040, ADR-0036
revised in place (still Proposed; R11), the logical model, glossary terms and a changelog entry. Sources:
`research.md` (R1–R11, D1–D9, P1–P20), `plan.md`, `data-model.md`, `contracts/file-kinds-content-form.md`, and the code
as WP01–WP07 landed it. Hand off a claim→test list: every claim in ADR-0040 mapped to the test that proves it.

## Branch Strategy

Planning branch and final merge target: `feat/asset-template-round-trip`. Execution worktrees are allocated per
computed lane from `lanes.json`; do not pick a base branch by hand. Implement with:

```bash
spec-kitty agent action implement WP08 --agent claude --mission asset-template-round-trip-01M5YY7G
```

## Validation discipline

Run the targeted tests for the files you touch and the owning module's fast tier (`./gradlew :packs:test
:packs:check`; `:deployable:test` when named). Never run a whole-repo heavy sweep (`verifyAll` is CI's,
directive NO_FULL_HEAVY_SUITES_IN_MISSION); record the exact commands and pass/fail counts in the review handoff.
Java 25 via `JAVA_HOME=/usr/lib/jvm/temurin-25`; Maven Central HTTP 429 → wait and retry.

## Subtasks

### T032 — ADR-0040 and the index

- Check `main` first (`git fetch origin main && git ls-tree --name-only origin/main docs/adr/`): 0039 is earmarked
  for #227; if 0040 is taken by a sibling, take the next free number and say so.
- House ADR shape (read ADR-0035, ADR-0037). Status **Proposed**; decider the operator; rulings of 2026-10-27 on #193.
- Decisions: (1) content form (`yaml`, `markdown`, `text`, `bytes`) declared by the file-kind rule; extension and
  `mime` never choose it; `mime` stays validated metadata; the table admits only built forms (`yaml`, `bytes`); (2)
  the `payloads` section, consulted after `files` and `exclusions`, no overlap among payload rules; (3) asset payloads
  and top-level templates are `bytes`; `markdown` is #192's; (4) widens ADR-0037 decision 4: the walk classifies every
  file; asset files are the one pack-level collection beside the kind map (R1); templates via a concrete binding, no
  second registry before the rule of three; (5) missing payload refused as a violation (R3); (6) file mode not meaning
  (R5); (7) `asset:`/`template:` relationship endpoints (R6), a served behaviour change with no contract shape change;
  (8) fingerprint: payload kinds digested last; packs holding them get a new `Charter-Source-Fingerprint`/ETag once;
  revisions computed over bytes with unchanged values for text.
- Alternatives: extension-based rows; `mime` decides; by-reference only; format on `KindBinding`; sealed binding
  union; switching the gate to `WholePackStore`.
- Append to `docs/adr/README.md`'s index.

### T033 — ADR-0036 revised in place

- Decision 2 "Other files": compared as the file's content form, declared by its file-kind rule (ADR-0040).
- Per-kind table: intro "which files exist, which schema and which content form"; `*.asset.yaml` row → payload is an
  `asset-payload` file compared as `bytes`; replace the `*.md` and "any other file" rows with rows deferring to the
  declared content form (`asset-payload`, top-level `template` → bytes; markdown kinds list themselves when they land);
  the `**/*.config.yaml` row → asset payload (R1); add `*/assets/**/README.md` excluded.
- A dated revision note in house style.

### T034 — Logical model and glossary

- `docs/architecture/logical-model.md` "Skills, glossary packs, assets and templates": asset = sidecar + asset file
  under `assets/`; a payload may have no sidecar; template identity = path under `templates/`
  (`template:writing/meeting-minutes.md`); link ADR-0040.
- `site/.contextive/definitions.yaml`: add **content form** (vs `YamlFormat`, `mime`, `SerializationStrategy`) and
  **asset payload**; refine **asset**/**template** if present; append-only, existing entry shape.

### T035 — Changelog, open-packs issue text, docs checks

- `docs/changelog/2026-10-27-asset-and-template-round-trip.md` in house style (`docs/changelog/README.md`,
  `2026-10-27-whole-pack-seam.md`): impact first.
- Final open-packs issue text (from `research.md` plus WP07's gate output) in the handoff for the operator to file.
- `python3 scripts/docs/check_docs.py all`, markdownlint-cli2, cspell on changed files green (find exact invocations in
  CI config); append real terms to `cspell.json` only.

## Definition of Done

- Docs checks green; index lists the ADR; ADR-0036 has no extension-based comparison rule left; claim→test list in the
  handoff.

## Risks / reviewer guidance

- Reviewer: check every ADR-0040 claim against the claim→test list and the merged code.
