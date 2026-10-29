---
work_package_id: WP02
title: Markdown form for files rules
dependencies:
- WP01
requirement_refs:
- FR-005
- NFR-002
- NFR-003
- C-003
- C-006
planning_base_branch: feat/194a-procedure-antipattern-toolguide
merge_target_branch: feat/194a-procedure-antipattern-toolguide
branch_strategy: Planning artifacts for this mission were generated on feat/194a-procedure-antipattern-toolguide. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/194a-procedure-antipattern-toolguide unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-procedure-antipattern-toolguide-round-trip-01M60H9B
base_commit: 9f4833b20ca3ef57b8fff47a29382d017bc9abbc
created_at: '2026-10-29T05:52:13.305606+00:00'
subtasks:
- T006
- T007
- T008
- T009
- T010
- T011
phase: PR-A1 - Markdown content form
agent: claude
history:
- at: '2026-10-29T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MarkdownFilesRuleTest.java
- docs/changelog/2026-10-29-markdown-content-form.md
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ContentForm.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileKind.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackSchemas.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileKindTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackSchemasTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackCensus.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MeaningComparator.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MarkdownFilesRuleTest.java
- service/packs/src/test/resources/markdown-rule-fixtures/**
- contracts/packs/file-kinds.yaml
- contracts/packs/README.md
- docs/adr/0040-content-form-and-payload-kinds.md
- docs/changelog/2026-10-29-markdown-content-form.md
- docs/changelog/README.md
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP02 — Markdown form for files rules

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Make `markdown` a built content form: the file-kind table admits it on `files` rules, a rule's structure schema is
applied to the parsed Markdown model, and the whole-pack gate compares a Markdown file by its model instead of as
YAML. Record it in an ADR-0040 amendment and a changelog entry. After this WP, sibling #195 only has to add its
`pack.md` rule and its pack model. No gate outcome changes at the current pin (no rule in the table declares
`markdown` yet except in test fixtures).

## Branch Strategy

Planning branch and final merge target: `feat/194a-procedure-antipattern-toolguide`; lanes from `lanes.json`.
`spec-kitty agent action implement WP02 --agent <name> --mission procedure-antipattern-toolguide-round-trip-01M60H9B`.
**PR-A1** (with WP01).

## Context

- `research.md` D6; plan IC-01; ADR-0040 decisions 1 and 3 (`docs/adr/0040-content-form-and-payload-kinds.md`).
- `ContentForm` (javadoc says `MARKDOWN` is declared, not built); `PackFileKind` `BUILT_FORMS =
  EnumSet.of(YAML, BYTES)` and `contentForm(rule)` refuses unbuilt forms; `FileRule(match, schema, contentForm)` and
  `Mapped(kind, Optional schema, contentForm)`.
- `PackSchemas` loads bundled schemas strictly (`DisallowUnknownKeywordFactory`), offline, by kind name from
  `packs/schemas/<kind>.schema.json`, validates loaded YAML trees. A Markdown rule's schema validates the parsed model
  rendered as a JSON tree: `{"frontmatter": {...}|absent, "title": "..."|absent, "preamble": "...", "sections":
  [{"title": "...", "content": "..."}]}`.
- Gate (test side): `WholePackCensus.contentFormOf(pack, path)` returns the rule's form; `MeaningComparator` compares
  YAML via `Yaml.load` and is used by `DomainRoundTrip` / `WholePackRoundTripTest` / `WholePackOpenPacksRoundTripTest`;
  BYTES files are compared byte for byte (see `WholePackRoundTripTest:209`).

## Subtasks

### T006 — `ContentForm.MARKDOWN` built; `PackFileKind` admits it

1. Add `MARKDOWN` to `BUILT_FORMS`; update `ContentForm` javadoc (built here; `TEXT` stays unbuilt).
2. `files` rules may declare `content_form: markdown` with a `schema`; a `markdown` rule's schema names a structure
   schema (see T007). `payloads` rules stay `bytes` only (unchanged).
3. Refuse at table load: a `markdown` rule whose schema is not loadable; any other combination the current rules
   refuse keeps refusing.

### T007 — `PackSchemas`: structure schemas for Markdown rules

1. Load the schema named by a `markdown` rule like any other bundled schema (same strict dialect: standard 2020-12
   keywords only — no `x-` keywords).
2. Add `validateMarkdown(kind, MarkdownDocument)` that renders the model to the JSON tree above (absent optional
   fields omitted, not null) and validates it, returning the same violation type the YAML validation returns.
3. Tests in `PackSchemasTest`: a test-only structure schema (under `src/test/resources/markdown-rule-fixtures/`, loaded
   through the same loader the bundled set uses — follow how existing tests load planted schemas) accepts a valid
   document and refuses one missing a required section; update the bundled-kind count assertion only if the bundled
   set changes (it should not in this WP).

### T008 — Gate comparison by content form

1. `WholePackCensus`: make the dispatch over `ContentForm` exhaustive (a `switch` with no `default`, so `TEXT`
   fails loudly with "not built") and expose the form for Markdown files.
2. `MeaningComparator`: add `compareMarkdown(sourceText, writtenText)` returning differences located by model path
   (reuse WP01's `differences(a, b)`); a refused document on either side is a difference naming the refusal. Never pass
   Markdown text to `Yaml.load`.
3. Wherever the gate selects a comparison by form (`WholePackRoundTripTest`, `DomainRoundTrip` callers), route
   `MARKDOWN` to `compareMarkdown`. Keep YAML and BYTES behaviour byte-identical.

### T009 — Fixture pack with a `markdown` rule; positive control

`MarkdownFilesRuleTest`: a temporary copy of `PackFileKind.bundled()`'s table plus one test rule (e.g.
`match: "*/notes/*.md"`, `schema: <test structure schema>`, `content_form: markdown`) classifies a fixture file as
`Mapped(kind, schema, MARKDOWN)`; parse → validate → write → compare passes; a fixture with one changed character in
a section is reported at `sections[i].content`; a fixture missing a required section is refused by the schema. (No
store consumer exists yet — #195 adds the first `files`-rule Markdown file to the store; this test proves the
classification, schema and comparison path.)

### T010 — Table header, README, count tests [P]

1. `contracts/packs/file-kinds.yaml` header comment: `markdown` is built; what a `markdown` rule declares; how the
   structure schema validates the parsed model.
2. `contracts/packs/README.md`: same, one paragraph.
3. `PackFileKindTest`: a `markdown` files rule is accepted; `text` is still refused; counts unchanged at the pin.

### T011 — ADR-0040 amendment; A1 changelog entry [P]

1. Amend ADR-0040 (dated amendment note in its Status, as earlier amendments did): decision 1 — `files` rules may
   declare `markdown`; decision 3 — `markdown` is built: model, equality, refusals, canonical write, structure schemas
   (summarize research D6/D12; do not cite ADR-0042, which does not exist yet). Keep the ADR's front-matter `updated`
   current.
2. `docs/changelog/2026-10-29-markdown-content-form.md` (match the frontmatter and voice of the latest entries, e.g.
   `2026-10-27-asset-and-template-round-trip.md`) and append its row to `docs/changelog/README.md` (newest first).
3. Run `python3 scripts/docs/check_docs.py all`, `npx markdownlint-cli2` and `npx cspell` on the changed docs.

## Definition of Done

- `./gradlew :packs:test :packs:openPacksRoundTrip :packs:wholePackRoundTrip` green at the current pin with **no
  change in gate outcomes**; `./gradlew verifyAll` green once before review.
- Docs checks green. Changes confined to `owned_files`.
- Exit criterion E1 (plan.md) met together with WP01.

## Risks / reviewer guidance

- Reviewer: confirm no Markdown text can reach `Yaml.load` in the gate; the `switch` over `ContentForm` is exhaustive;
  `TEXT` still refused; YAML/BYTES comparisons unchanged (run the gate before and after).
- Collision with #195: this WP owns the form; #195 adds only its rule (parent session informed).
