---
work_package_id: WP06
title: Decision record and living docs
dependencies:
- WP03
requirement_refs:
- FR-014
- FR-012
- C-001
planning_base_branch: feat/asset-template-round-trip
merge_target_branch: feat/asset-template-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/asset-template-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/asset-template-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T028
- T029
- T030
- T031
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

# Work Package Prompt: WP06 — Decision record and living docs

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `architect-alphonso`
- **Role**: `implementer`

## Objective

Record the decisions of this mission and keep the living docs true (DIRECTIVE_003, DIRECTIVE_037): a new ADR-0040,
ADR-0036 revised in place (it is still Proposed; operator ruling R11), the logical model's assets/templates section,
glossary terms, and a changelog entry. Sources of truth: `research.md` (rulings R1–R11, D1–D9, P1–P20), `plan.md`,
`data-model.md`, `contracts/file-kinds-content-form.md`, and the code as WP01–WP05 land it. Docs describe shipped
behaviour: re-read the merged code before finalizing wording.

## Branch Strategy

Planning branch and final merge target: `feat/asset-template-round-trip`. Lanes from `lanes.json`. Implement with:

```bash
spec-kitty agent action implement WP06 --agent claude --mission asset-template-round-trip-01M5YY7G
```

## Subtasks

### T028 — ADR-0040 and the index

- Check `main` first: `git fetch origin main && git ls-tree --name-only origin/main docs/adr/` — 0039 is earmarked for
  #227; if 0040 is taken by a sibling, take the next free number and say so in the handoff.
- Follow the house ADR shape (read ADR-0035, ADR-0037: frontmatter, Status, Context, Decision, Consequences, Risks,
  Alternatives, References). Status **Proposed**, decider the operator, rulings of 2026-10-27 on #193.
- Decision points: (1) a file's content form (`yaml`, `markdown`, `text`, `bytes`) is declared by its file-kind rule
  in `contracts/packs/file-kinds.yaml`; extension and `mime` never choose it; `mime` stays validated metadata;
  (2) the `payloads` section for schema-less files, consulted after `files` and `exclusions`; payload rules do not
  overlap each other; (3) asset payloads and top-level templates are `bytes`; #193 builds only `bytes`, `markdown` is
  #192's to build on the same key; (4) widens ADR-0037 decision 4 for non-YAML content: the walk classifies every
  file; asset files are the one pack-level collection beside the kind map (R1); templates map through a concrete
  binding, no second registry until the rule of three; (5) missing payload refused as a violation (R3); (6) file mode
  is not meaning (R5); (7) `asset:` and `template:` URNs become valid relationship endpoints (R6) — a served
  behaviour change with no contract shape change; (8) fingerprint: payload kinds digested last, so packs without them
  keep their digest; packs with them get a new `Charter-Source-Fingerprint`/ETag once.
- Alternatives: extension-based rows (ADR-0036 as written); `mime` decides; by-reference classification only; format
  on `KindBinding`; a sealed binding union; switching the gate to `WholePackStore`.
- Append the ADR to `docs/adr/README.md`'s index (append-only).

### T029 — ADR-0036 revised in place

- Decision 2 "Other files" paragraph: replace extension-based choice with "compared as the file's content form, which
  its file-kind rule declares (ADR-0040)".
- Per-kind table: the intro grows to "which files exist, which schema and which content form"; the `*.asset.yaml` row
  says the payload is an `asset-payload` file compared as `bytes`; replace the `*.md` row and the "any other file"
  row with rows that defer to the declared content form (`asset-payload` and top-level `template` → bytes; markdown
  kinds list themselves when they land, #192/#194; files no rule classifies are out of the gate). Remove the
  `**/*.config.yaml` row's exclusion wording (now an asset payload, R1) and add the `*/assets/**/README.md` exclusion.
- Add a dated revision note at the top (house style: read how other ADRs record amendments).

### T030 — Logical model and glossary

- `docs/architecture/logical-model.md` "Skills, glossary packs, assets and templates": an asset is a sidecar plus an
  asset file under `assets/`; a payload may have no sidecar; a template's identity is its path under `templates/`
  (`template:writing/meeting-minutes.md`); link ADR-0040.
- Glossary `site/.contextive/definitions.yaml`: add **content form** (distinct from `YamlFormat` the codec, `mime` the
  media type and `SerializationStrategy` storage), **asset payload** (asset file), and refine **asset**/**template**
  if present. Follow the file's existing entry shape; append-only.

### T031 — Changelog, open-packs issue text, docs checks

- `docs/changelog/2026-10-27-asset-and-template-round-trip.md` in the house style (read
  `docs/changelog/README.md` and `2026-10-27-whole-pack-seam.md`): impact first — assets and templates now load and
  round-trip; fingerprints change once for packs holding them; `asset:`/`template:` relationship endpoints; the
  six built-in sidecars are refused until open-packs fixes them.
- Put the final open-packs issue text (from `research.md`, with the gate output once WP05 has run) in the handoff so
  the operator can file it.
- Run `python3 scripts/docs/check_docs.py all`, `npx markdownlint-cli2` and `npx cspell` on the changed files (see
  `docs/guides/` and CI config for the exact invocations) and make them green; add new words to `cspell.json` only
  if they are real terms, and say so (that file is shared: append-only).

## Definition of Done

- Docs checks green; ADR index lists ADR-0040; ADR-0036 has no extension-based comparison rule left; wording matches
  the merged code.

## Risks / reviewer guidance

- ADR numbering collision with siblings: check `main` before committing.
- Reviewer: every claim in ADR-0040 maps to a test or code path in WP01–WP05.
