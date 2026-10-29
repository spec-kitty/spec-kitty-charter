---
work_package_id: WP09
title: Migration harness and open-packs PR
dependencies:
- WP04
- WP06
requirement_refs:
- FR-006
- NFR-001
- C-007
planning_base_branch: feat/194a-procedure-antipattern-toolguide
merge_target_branch: feat/194a-procedure-antipattern-toolguide
branch_strategy: Planning artifacts for this mission were generated on feat/194a-procedure-antipattern-toolguide. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/194a-procedure-antipattern-toolguide unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-procedure-antipattern-toolguide-round-trip-01M60H9B
base_commit: 7c39bc4733a09797a74c15c1f691b76146da142f
created_at: '2026-10-29T07:23:54.576667+00:00'
subtasks:
- T044
- T045
- T046
- T047
- T048
phase: PR-A2 - Migration
agent: claude
history:
- at: '2026-10-29T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/migration/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/migration/PackMigrationHarness.java
- service/packs/src/test/java/ai/speckitty/charter/packs/migration/LegacyProcedureReader.java
- service/packs/src/test/java/ai/speckitty/charter/packs/migration/LegacyToolguideReader.java
- service/packs/src/test/java/ai/speckitty/charter/packs/migration/GuideRestructurer.java
- service/packs/src/test/java/ai/speckitty/charter/packs/migration/FieldMappingVerifier.java
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/migration/**
- service/packs/build.gradle.kts
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP09 — Migration harness and open-packs PR

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Migrate the pinned procedures, toolguides and guides (built-in, internal, qa) to the sanitized shapes **with this
service's own writer**, verify the migration field by field, and land the result upstream in spec-kitty-open-packs.
The harness is throwaway (C-007): test source set only, excluded from the default build, deleted in WP10.

## Branch Strategy

Planning branch and merge target `feat/194a-procedure-antipattern-toolguide`; lanes from `lanes.json`.
`spec-kitty agent action implement WP09 --agent <name> --mission procedure-antipattern-toolguide-round-trip-01M60H9B`.
**PR-A2.** The open-packs work happens in `/home/user/spec-kitty-open-packs` on a branch
`feat/charter-194-sanitized-procedures-toolguides` (never push to its `main`).

## Context

- `research.md` D7, D11; plan IC-06; data-model mapping; content-squad mapping table
  `research/content-squad/analyst.md` §4–6 (heading → fixed block) and `research/content-squad/doctrine.md`.
- `build.gradle.kts` of `service/packs`: test tasks and tags (`openPacksRoundTrip` includes `open-packs`, excludes
  `whole-pack`; `test` — check what tags it excludes). Add a `migration` tag excluded from `test`,
  `openPacksRoundTrip` and `wholePackRoundTrip`, and a dedicated `packMigration` task that runs only it with
  `-PmigrationTarget=<dir>`.

## Subtasks

### T044 — Harness

- `LegacyProcedureReader` / `LegacyToolguideReader`: read the **old** shapes as plain YAML maps (this is the only code
  that knows them; it lives in `src/test`). Fail on any key the mapping does not cover.
- Convert to the new domain types (WP03/WP06) and write with the production `ProcedureYaml`/`ToolguideYaml` and the
  companion path (`MarkdownCodec`) into a target directory that is **not** the pinned checkout (guard: refuse if the
  target equals or is inside `openPacksDir` at `openPacksRef`).
- Mapping: procedure `name`→`title`, `purpose`→`description`, step `actor: human`→`role: operator`,
  `actor: agent|system`→ no role, anti-pattern `name`→`title`, `description`→`content`, references unchanged in
  shape (`{type, id, reason?}`); toolguide `guide_path`→`<id>.guide.md`, guide file renamed and moved beside the
  toolguide.

### T045 — Field-mapping verifier

`FieldMappingVerifier`: for every source file, every source key path maps to exactly one target key path (or to a
listed, justified drop — e.g. `actor: agent`), and every target value equals the source value after the declared
transform; counts in equal counts out (41/41 procedures, 24/24 toolguides and guides); running the harness on its own
output is a no-op (idempotence through the new readers).

### T046 — Guide restructuring

`GuideRestructurer` maps each legacy guide to fixed blocks and freeform sections following the content-squad table:
preamble → `overview` (merged before an existing intro section if both exist), synonyms → fixed blocks
(`Overview`/`Purpose and When to Use`… per the table), everything else → freeform sections in order; H1 dropped
(rewritten from title). Non-mechanical cases (stray attribution, provenance notes, banners, `---` rules, emoji
headings) are listed in a `manual-decisions.md` report with the chosen treatment (e.g. Drupal attribution →
`references`; QA-RESULT-INGEST provenance note → dropped) — every one appears in the open-packs PR description for
human review.

### T047 — Run and artifacts

Run `packMigration` for built-in, internal and qa into a copy of the open-packs tree; produce per-class diff
artifacts (procedures, toolguide YAML, guides) and the verifier report; round-trip the migrated tree through the new
store (all 41 + 24 load and compare equal).

### T048 — Issues and PR

In spec-kitty-open-packs (session has push access): file one issue per class of change (procedure shape, toolguide
`guide_path` and guide file names, guide structure, plus one per non-mechanical decision class) naming the files and
the required change and linking charter #194; commit the migrated files on the branch above; open a PR "Migrate
procedures and toolguide guides to the charter's sanitized shapes (charter #194)" with the issues linked, the manual
decisions listed and the verifier summary attached. Do not merge it. Report the PR URL and the branch head SHA to the
orchestrator (the merge is an operator decision; WP10 waits for it).

## Definition of Done

- Harness runs only via `packMigration`; `./gradlew :packs:test` green and unaffected.
- Verifier green: zero unmapped keys, 41/41 and 24/24, idempotent.
- Open-packs issues and PR exist; URLs recorded in the WP's review handoff.
- Mark T044–T048 done.

## Risks / reviewer guidance

- Reviewer: the harness can never write into the pinned checkout; old-shape knowledge exists only in `src/test`;
  every manual decision is listed for human review upstream.
