---
work_package_id: WP03
title: Procedure domain and sanitized schema
dependencies:
- WP02
requirement_refs:
- FR-001
- FR-003
- SC-002
- C-004
- C-005
planning_base_branch: feat/194a-procedure-antipattern-toolguide
merge_target_branch: feat/194a-procedure-antipattern-toolguide
branch_strategy: Planning artifacts for this mission were generated on feat/194a-procedure-antipattern-toolguide. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/194a-procedure-antipattern-toolguide unless the human explicitly redirects the landing branch.
subtasks:
- T012
- T013
- T014
- T015
phase: PR-A2 - Procedures
agent: claude
history:
- at: '2026-10-29T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Procedure.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ProcedureIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ProcedureStep.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ProcedureReference.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AntiPatternEntry.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ProcedureEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ProcedureSchemaTest.java
execution_mode: code_change
owned_files:
- contracts/packs/schemas/procedure.schema.json
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Procedure.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ProcedureIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ProcedureStep.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ProcedureReference.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AntiPatternEntry.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ProcedureEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ProcedureSchemaTest.java
- service/packs/src/test/resources/schema-fixtures/procedure/**
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP03 — Procedure domain and sanitized schema

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Rewrite `contracts/packs/schemas/procedure.schema.json` to the sanitized shape and model the procedure in the domain
as a `CharterArtifact`, following `Tactic`. No storage binding yet (WP04).

## Branch Strategy

Planning branch and merge target `feat/194a-procedure-antipattern-toolguide`; lanes from `lanes.json`.
`spec-kitty agent action implement WP03 --agent <name> --mission procedure-antipattern-toolguide-round-trip-01M60H9B`.
**PR-A2.**

## Context

- `data-model.md` "Procedure"; `research.md` D1–D3; ADR-0032 (steps name roles, not profiles), ADR-0035
  (`CharterArtifact` in the kernel), ADR-0036.
- Template: `domain/Tactic.java` (builder, `ModularRuleset` RULES guarded in the constructor, `CharterArtifact`
  methods `kind()`, `localId()`, `label()`, `description()`, `relationships()`), `TacticStep`, `TacticReference`,
  `StyleguideAntiPattern`; `kernel.domain.ArtifactKind.PROCEDURE` already exists.
- The current procedure schema has `name`, `purpose`, step `actor` (enum human/agent/system),
  `ProcedureAntiPattern {name, description}`, `ProcedureReference {type, id, reason?}`. All 41 pinned procedures
  validate against it; after this WP they will **not** validate against the new one — that is intended and fixed by the
  migration (WP09/WP10). Nothing loads procedures yet, so no test goes red here.

## Subtasks

### T012 — Rewrite `procedure.schema.json`

Closed objects throughout (`additionalProperties: false`). Keep the `$id` and file name (core consumes the name;
ADR-0037 "a binding names its schema"). Shape:

```json
required: [schema_version, id, title, description, entry_condition, exit_condition, steps]
schema_version: {type: string, const: "1.0"}
id: {type: string, pattern: "^[a-z0-9]+(-[a-z0-9]+)*$"}
title, description, entry_condition, exit_condition, notes: {type: string, minLength: 1}
steps: {type: array, minItems: 1, items: {$ref: "#/$defs/ProcedureStep"}}
anti_patterns: {type: array, items: {$ref: "#/$defs/AntiPatternEntry"}}
references: {type: array, items: {$ref: "#/$defs/Reference"}}
applies_to_languages: {type: array, items: {type: string}}
$defs:
  ProcedureStep: {required: [title], properties: {title, description, role, on_success, on_failure}}
    role: {type: string, pattern: "^[a-z][a-z0-9-]*$"}   # open role token; never a profile id (see T014)
  AntiPatternEntry: {required: [title], properties: {title, content, bad_example, good_example}}
  Reference: {required: [type, id], properties: {type: {$ref: ArtifactKind}, id, when, reason}}
  ArtifactKind: enum of the kernel ArtifactKind tokens (copy the list the current schema uses; add none)
```

Remove `ActorRole`, `ProcedureAntiPattern`, `ProcedureReference` defs. Validate the schema loads under
`PackSchemas`' strict dialect.

### T013 — `ProcedureIdentifier`, `Procedure` entity

- `ProcedureIdentifier(String value)` like `TacticIdentifier` (same kebab rule).
- `Procedure` implements `CharterArtifact`: fields per data-model; `label()` = title, `description()` = description;
  builder; RULES: id/title/description/conditions non-blank, at least one step.
- `relationships()` returns `List.of()` — **procedure references are never minted** (research D3, #189). Javadoc
  says so and why.

### T014 — `ProcedureStep`, `AntiPatternEntry`, `ProcedureReference`

- `ProcedureStep(String title, Optional<String> description, Optional<String> role, Optional<String> onSuccess,
  Optional<String> onFailure)`.
- `role` is an open role token (ADR-0032 D1/D2/D5). Refuse a token that names an agent profile: the domain cannot see
  the profile catalog, so the rule is lexical — refuse tokens of the built-in profile-id form `<name>-<name>` that
  match a profile id pattern? **No**: do not guess. Keep `role` an open token validated only by the pattern, and record
  in javadoc that profile ids are refused at the catalog boundary when roles are resolved (out of scope). The spec's
  US3-AS3 only requires that a role round-trips and an absent role stays absent.
- `AntiPatternEntry(String title, Optional<String> content, Optional<String> badExample, Optional<String> goodExample)`
  — a value, no identity. It is shared later by name only; do not change `StyleguideAntiPattern` (merged kind, #186
  follow-up).
- `ProcedureReference(ArtifactKind type, String id, Optional<String> when, Optional<String> reason)`.

### T015 — Tests

- `ProcedureEntityTest`: valid build; each RULES breach; `relationships()` is empty even with references.
- `ProcedureSchemaTest` (schema fixtures under `src/test/resources/schema-fixtures/procedure/`): a sanitized procedure
  validates; **twins** each refused: `name` instead of `title`, `purpose` instead of `description`, step `actor`,
  anti-pattern `name`/`description`, unknown top-level key, integer `schema_version`, `role` with an uppercase letter.
  Each refused twin differs from the accepted fixture in exactly that one property (non-vacuity).

## Definition of Done

- `./gradlew :packs:test` green (Java 25). Style checks clean. Changes inside `owned_files`.
- `spec-kitty agent tasks mark-status T012 T013 T014 T015 --status done`.

## Risks / reviewer guidance

- Reviewer: schema is closed everywhere; no retired names accepted; `relationships()` empty with a reason.
- `PackSchemasTest` counts are unchanged (procedure schema already bundled).
