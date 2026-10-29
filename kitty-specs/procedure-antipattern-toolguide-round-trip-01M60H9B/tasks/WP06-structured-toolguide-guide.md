---
work_package_id: WP06
title: Structured toolguide guide
dependencies:
- WP05
requirement_refs:
- FR-004
- FR-009
- FR-011
- SC-003
planning_base_branch: feat/194a-procedure-antipattern-toolguide
merge_target_branch: feat/194a-procedure-antipattern-toolguide
branch_strategy: Planning artifacts for this mission were generated on feat/194a-procedure-antipattern-toolguide. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/194a-procedure-antipattern-toolguide unless the human explicitly redirects the landing branch.
subtasks:
- T026
- T027
- T028
- T029
- T030
- T031
phase: PR-A2 - Guides
agent: claude
history:
- at: '2026-10-29T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Guide.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GuideBlock.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GuideSection.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GuideMarkdown.java
- contracts/packs/schemas/toolguide-guide.schema.json
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/GuideTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ToolguideGuideTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Guide.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GuideBlock.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GuideSection.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Toolguide.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GuideMarkdown.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ToolguideDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ToolguideYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ToolguideBinding.java
- contracts/packs/schemas/toolguide.schema.json
- contracts/packs/schemas/toolguide-guide.schema.json
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/GuideTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ToolguideEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ToolguideGuideTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ToolguideDsoRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GeneratedPackTree.java
- service/packs/src/test/resources/roundtrip-fixtures/toolguide/**
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP06 — Structured toolguide guide

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

A toolguide's guide becomes a structured `Guide` value, stored as a Markdown companion `<id>.guide.md` beside the
toolguide, named by `guide_path` relative to the toolguide's file. The H1 is written from the toolguide `title`.

**Known red window**: pinned toolguides name repo-rooted `UPPER_CASE.md` guides, which the sanitized binding refuses;
`:packs:openPacksRoundTrip` and the gate are red at the old pin until WP10 (tasks.md). `:packs:test` stays green.

**Out-of-map edit (recorded)**: append the guide `files` rule to `contracts/packs/file-kinds.yaml` (owned by WP02,
approved before this WP starts).

## Branch Strategy

Planning branch and merge target `feat/194a-procedure-antipattern-toolguide`; lanes from `lanes.json`.
`spec-kitty agent action implement WP06 --agent <name> --mission procedure-antipattern-toolguide-round-trip-01M60H9B`.
**PR-A2.**

## Context

- `data-model.md` "Guide"; `research.md` D5, D10, D11; content-squad mapping in
  `research/content-squad/analyst.md` §4–6 (for fixtures).
- `domain/Toolguide.java` (fields incl. `guidePath`), `ToolguideDso/Yaml/Binding`, `toolguide.schema.json`,
  `SkillBinding` as the companion template; WP05's companion form.
- Canonical fixed-block headings (exact text): `## Overview`, `## When to use`, `## Setup`, `## Usage`,
  `## Cautions`, `## Known limitations`, `## References`. Order on write: title, overview, when_to_use, setup, usage,
  freeform sections…, cautions, known_limitations, references.

## Subtasks

### T026 — `Guide`, `GuideBlock`, `GuideSection`

- `GuideBlock` enum of the seven fixed blocks, each with its canonical heading and order; `required()` true only for
  `OVERVIEW`.
- `GuideSection(String title, String content)`; title must not equal any fixed heading ignoring case.
- `Guide(Map<GuideBlock, String> blocks, List<GuideSection> sections)` with RULES: overview present and non-blank;
  contents obey the Markdown model invariants (no column-0 H1/H2, no leading/trailing blank lines).

### T027 — `GuideMarkdown`: Guide ↔ `MarkdownDocument`; H1 rule

- `toDocument(title, guide)`: H1 = toolguide title; no frontmatter; empty preamble; blocks and sections in write order.
- `fromDocument(title, doc)`: refuse frontmatter, a non-empty preamble, a missing overview, fixed blocks out of order or
  duplicated, a heading equal to a fixed heading in another case, a freeform section after `cautions` (or after
  `known_limitations`/`references`), an H1 that differs from `title`. An absent H1 is accepted (incoming HTTP content,
  FR-011); the writer always adds it.

### T028 — Schemas [P]

- `toolguide.schema.json`: `guide_path` pattern `^[a-z0-9]+(-[a-z0-9]+)*\.guide\.md$` (relative, beside the
  toolguide); no other field changes (renames of merged fields are #186 follow-ups).
- New `toolguide-guide.schema.json`: structure schema over the WP02 JSON rendering of the parsed model: no
  `frontmatter`; `preamble` `""`; `sections` items `{title, content}` with `title` minLength 1; standard 2020-12
  keywords only (order and reserved-heading rules are enforced in `GuideMarkdown`, not the schema).

### T029 — Toolguide binding with the guide companion

- `Toolguide` holds `Guide guide`; `guidePath` stays the stored relative path (domain invariant: equals
  `id + ".guide.md"`).
- `ToolguideDso` carries the companion content; `ToolguideBinding.hasCompanions()` true, `companions(dso)` declares
  one Markdown companion with schema `toolguide-guide`, `withCompanions` fills it; `toEntity`/`toDso` map through
  `GuideMarkdown`.
- `ToolguideYaml` unchanged except `guide_path` semantics; no tolerance for repo-rooted paths.

### T030 — Guide file rule

Append to `contracts/packs/file-kinds.yaml` a `files` rule `match: "**/toolguides/*.guide.md"`,
`schema: toolguide-guide`, `content_form: markdown`, and mark it as the toolguide's companion kind so WP05's ownership
rule admits the toolguide's own guide. Update `PackFileKindTest` count via the smallest edit (recorded out-of-map if
needed).

### T031 — Tests

- `GuideTest`: rules; each refusal with an accepted neighbour.
- `ToolguideGuideTest`: fixture toolguide + guide round-trips; one-character change in a section reported; refusals
  from T027; missing guide file, guide leaving the pack, case-only collision refused; guide prose link to a styleguide
  creates no relationship.
- Update `ToolguideEntityTest`, `ToolguideDsoRoundTripTest`, `GeneratedPackTree` for the new guide shape.

## Definition of Done

- `./gradlew :packs:test` green; style clean. `:packs:openPacksRoundTrip` red only for pinned toolguides/guides (list
  them in the handoff).
- Mark T026–T031 done.

## Risks / reviewer guidance

- Reviewer: one home for the title (H1 written, compared, never stored separately); no repo-rooted path accepted;
  preamble refusal paired with an accepted overview twin.
