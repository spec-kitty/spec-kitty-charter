---
work_package_id: WP02
title: Vocabulary and schemas
dependencies: []
requirement_refs:
- FR-006
- FR-007
- C-002
planning_base_branch: feat/192-glossary-pack-skill-round-trip
merge_target_branch: feat/192-glossary-pack-skill-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/192-glossary-pack-skill-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/192-glossary-pack-skill-round-trip unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-glossary-pack-skill-round-trip-01M5YVEC
base_commit: 48e0329febae0de666b46a991d4d6e1d01a4d39c
created_at: '2026-10-27T16:19:05.452074+00:00'
subtasks:
- T005
- T006
- T007
- T008
phase: Phase 1 - Foundations
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/packs/
create_intent:
- contracts/packs/schemas/glossary-pack.schema.json
- contracts/packs/schemas/skill.schema.json
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GlossaryPackAndSkillSchemaTest.java
execution_mode: code_change
owned_files:
- contracts/packs/schemas/glossary-pack.schema.json
- contracts/packs/schemas/skill.schema.json
- contracts/packs/file-kinds.yaml
- contracts/packs/README.md
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackSchemas.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileKindTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackSchemasTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/SchemaKeywordCoverageTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GlossaryPackAndSkillSchemaTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP02 — Vocabulary and schemas

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Give both kinds closed schemas and map their files to them (spec FR-006, FR-007; plan IC-02/IC-03 schema bullets and
IC-04 "Vocabulary"; post-plan R2, R3). Pack schemas are canonical in this repository (ADR-0015); core consumes them.
After this WP no file of either kind is an exclusion, and an unknown key at any depth is refused by `PackSchemas`.

No binding exists yet: the files are classified and validate, but nothing loads them until WP03/WP04.

## Branch Strategy

Planning base and merge target: `feat/192-glossary-pack-skill-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP02 --agent claude --mission glossary-pack-skill-round-trip-01M5YVEC`.

## Subtasks

### T005 — `glossary-pack.schema.json`

Author from core's `src/charter/offering/glossary_packs/models.py` (`GlossaryPack`, `GlossaryTerm`,
`extra="forbid"`), in the style of the existing schemas (2-space JSON, `$schema`, `$id`, `$defs`, `title`,
`description`):

- `$schema`: `https://json-schema.org/draft/2020-12/schema`; `$id`:
  `https://schemas.spec-kitty.ai/charter/packs/glossary-pack.schema.json` (`PackSchemas` requires `ID_PREFIX + file`).
- Top level: `type: object`, `additionalProperties: false`, `required: [id, provenance, terms]`;
  `id` string `minLength: 1`; `provenance` string; `description` `type: [string, "null"]`;
  `terms` array, `minItems: 1`, items `$ref: #/$defs/GlossaryTerm`.
- `$defs.GlossaryTerm`: closed; `required: [surface, definition, confidence, status]`; `surface` and `definition`
  strings; `confidence` `number`; `status` string; `see_also`, `synonyms_to_avoid`, `aliases`, `banned_synonyms`
  `type: [array, "null"]` of strings; `introduced_in_mission` `type: [string, "null"]`.
- `description` text: say it is authored here from core's model (core exports no schema) and that surface
  uniqueness is the domain's invariant (JSON Schema cannot express it).

### T006 — `skill.schema.json`

Import core's `src/charter/offering/schemas/skill.schema.yaml` (at core `581b5c46da`) as JSON, unchanged in meaning:
closed at every level, `required: [schema_version, id, title, description, form]`, `schema_version` `const: "1.0"`,
the id `pattern` and `maxLength: 64`, `form` enum `[prompt, wrapper]`, `expands_to` (closed, `target` pattern),
`parameters` items closed, `invocation` closed, and the two `allOf` `if/then` rules with `not: {required: …}`.
Only `$schema` and `$id` (`…/charter/packs/skill.schema.json`) follow this repository's convention; say in
`description` where it was imported from.

### T007 — Vocabulary, counts, samples

- `contracts/packs/file-kinds.yaml`: add `files` rules `**/*.glossary-pack.yaml → glossary-pack` and
  `**/*.skill.yaml → skill` (after `asset`, before the `charter.yaml` comment block); remove the two matching
  `exclusions` entries. No other rule moves.
- `PackSchemas.java` javadoc "The 14 schemas" → "The 16 schemas".
- `PackSchemasTest.thereAreFourteenBundledSchemas` → `thereAreSixteenBundledSchemas`, `hasSize(16)`, javadoc.
- `PackFileKindTest`: `fileRules()` `hasSize(16)`; move the `core/skills/a.skill.yaml` and
  `core/glossary/a.glossary-pack.yaml` samples from `EXCLUDED` to `MAPPED` with their schemas.
- `contracts/packs/README.md`: "14 in all" → "16 in all"; mention the two new schemas' sources in one sentence.

### T008 — Keyword coverage and schema tests

- `SchemaKeywordCoverageTest`: add `not` and `maxLength` to `FIXED_FALLBACK` (they get the fixed fallback text,
  `<o>: '<pointer>' breaks schema keyword '<k>'`). If the fallback text is not what `PackPolicy` renders for them,
  stop and record it rather than editing `PackPolicy`.
- `GlossaryPackAndSkillSchemaTest` (new): through `PackSchemas.bundled().validate(...)` on parsed YAML text:
  - every pinned glossary pack and skill under `OpenPacksCorpus.packsDirectory()` validates (tag `open-packs`
    only if `OpenPacksCorpus` requires it — check how `PackSchemasTest` reaches real files), plus this repository's
    `packs/internal` and `packs/qa` copies;
  - an unknown key is refused at each level: glossary top level, a term; skill top level, `expands_to`, a parameter,
    `invocation`; each violation names `additionalProperties` and the pointer;
  - `confidence: "0.9"` (string) refused; `terms: []` refused; a prompt skill without `body_path` refused; a wrapper
    skill with `body_path` refused (the `not` keyword); an id of 65 characters refused (`maxLength`), and each of the
    last two yields a stable violation detail.

## Definition of Done

- `./gradlew :packs:test --tests '*PackSchemas*' --tests '*PackFileKind*' --tests '*SchemaKeywordCoverage*'
  --tests '*GlossaryPackAndSkillSchema*' --tests '*VocabularyEquality*'` green on Java 25.
- `./gradlew :packs:openPacksRoundTrip` still green (no binding yet, so nothing new loads).
- No exclusion names either kind; no other `file-kinds.yaml` rule changed.

## Risks / reviewer guidance

- A schema stricter than core's would refuse a file core accepts: compare key by key with the pydantic model and
  the core YAML schema. A schema looser than core's lets an unknown key through: every object must be closed.
- `$id` must match `PackSchemas`' rule or the bundle fails to load (the error message says which).
