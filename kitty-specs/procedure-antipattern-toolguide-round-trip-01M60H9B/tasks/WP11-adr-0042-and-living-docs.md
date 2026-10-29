---
work_package_id: WP11
title: ADR-0043 and living docs
dependencies:
- WP04
- WP05
- WP06
- WP07
- WP08
requirement_refs:
- C-003
- C-004
- C-001
- NFR-003
planning_base_branch: feat/194a-procedure-antipattern-toolguide
merge_target_branch: feat/194a-procedure-antipattern-toolguide
branch_strategy: Planning artifacts for this mission were generated on feat/194a-procedure-antipattern-toolguide. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/194a-procedure-antipattern-toolguide unless the human explicitly redirects the landing branch.
subtasks:
- T054
- T055
- T056
- T057
- T058
phase: PR-A2 - Docs
agent: claude
history:
- at: '2026-10-29T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: architect-alphonso
authoritative_surface: docs/adr/
create_intent:
- docs/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides.md
- docs/changelog/2026-10-29-procedures-and-toolguide-guides-round-trip.md
- docs/development/epic-186-sanitized-shape-follow-ups.md
execution_mode: code_change
owned_files:
- docs/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides.md
- docs/adr/0039-a-kind-binding-can-carry-companion-files.md
- docs/adr/README.md
- docs/guides/service-build.md
- docs/changelog/2026-10-29-procedures-and-toolguide-guides-round-trip.md
- docs/development/epic-186-sanitized-shape-follow-ups.md
- site/.contextive/definitions.yaml
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP11 — ADR-0043 and living docs

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `architect-alphonso`
- **Role**: `implementer`

## Objective

Record the decisions: ADR-0043 (the sanitized-shape rule and the shapes of this PR), the ADR-0039 amendment
(companions carry a content form; ownership tightened), the service-build guide, glossary definitions, the A2
changelog entry, and the epic #186 follow-up list.

## Branch Strategy

Planning branch and merge target `feat/194a-procedure-antipattern-toolguide`; lanes from `lanes.json`.
`spec-kitty agent action implement WP11 --agent <name> --mission procedure-antipattern-toolguide-round-trip-01M60H9B`.
**PR-A2.**

**Out-of-map edit (recorded)**: append one row to `docs/changelog/README.md` (owned by WP02, approved before).

## Context

- Mission `research.md` (D1–D12), `data-model.md`, `decisions/`, the three squad syntheses under `research/`; ADR
  format: read ADR-0039 and ADR-0040 for structure, front matter (okf fields), Status/amendment conventions and the
  index row format in `docs/adr/README.md`. ADR numbers: 0041 is #227's, #195 merged as **ADR-0042** (`docs/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest.md`: the sanitized-shape rules S1–S8 for pack-level objects); **#194 takes ADR-0043**. ADR-0043 builds on S1–S8 — cite them, extend them to artifact kinds (e.g. `title`/`description`/`content`, `<role>_path`, `role`), and do not restate or contradict them.
- Architect report `research/squad/architect.md` §2 (rules R1–R13) and §4 (the 14 merged-schema violations).

## Subtasks

### T054 — ADR-0043

Title along the lines of "Sanitized pack shapes; procedures and toolguide guides in the pack store". Decisions:
1. The sanitized-shape rules for artifact kinds, building on ADR-0042's S1–S8 (R1–R13 where they add to S1–S8; `content` names a body; `title` the label;
   `description` the summary; `<role>_path` relative to the artifact's file; closed objects); divergent pack content is
   fixed upstream, never tolerated (ADR-0036 d5).
2. Procedure shape; actor → role (ADR-0032); references kept, never minted pending #189.
3. Anti-pattern entries; no standalone kind (ADR-0037 d4); graph-node tags under #236.
4. Toolguide guide: structured, fixed blocks and freeform sections, stored as `<id>.guide.md` Markdown companion;
   one home for title/summary/commands/references.
5. Guide over HTTP: content or URI; response carries content; fetch posture (direct, JEP 418 resolver guard,
   allowlist, limits) and its consequences (proxy-only deployments; documented CommonMark divergences such as HTML
   comments).
6. Migration by the service's own writer and re-pin, rather than known-failure listing.
7. Operating as if core does not exist (epic #186 ruling) as context.
Consequences, alternatives (from the squads), and links to the decisions.

### T055 — ADR-0039 amendment; index [P]

Dated amendment: companions declare a form (`text`, `markdown` + structure schema); a companion may not claim any
classified path; decision 2's "content is text today" updated. Append the ADR-0043 row to `docs/adr/README.md`.

### T056 — Service-build guide, glossary [P]

`docs/guides/service-build.md`: the guide fetch configuration (`charter.guide-fetch.*`), the Markdown form, the new
red-window lesson if useful. `site/.contextive/definitions.yaml`: append terms `guide` (toolguide guide), `Markdown
content form`, `sanitized shape` (definitions grounded in ADR-0043; check `docs/context/` for canonical wording).

### T057 — A2 changelog [P]

`docs/changelog/2026-10-29-procedures-and-toolguide-guides-round-trip.md` (impact first: the breaking HTTP change,
the new pin, the shapes) and its README row.

### T058 — Epic #186 follow-up list [P]

`docs/development/epic-186-sanitized-shape-follow-ups.md`: the 14 merged-schema violations (architect §4) plus the
`content` renames for merged kinds (styleguide anti-patterns, templates, assets), each with file, issue and proposed
change. The orchestrator posts it on #186.

## Definition of Done

- `python3 scripts/docs/check_docs.py all`, markdownlint-cli2, cspell green; front matter valid.
- Mark T054–T058 done.

## Risks / reviewer guidance

- Reviewer: ADR matches what was built (cross-check against code, not only the plan); no claim about core behaviour.
