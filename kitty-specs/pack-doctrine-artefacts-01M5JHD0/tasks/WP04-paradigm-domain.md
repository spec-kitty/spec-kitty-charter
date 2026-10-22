---
work_package_id: WP04
title: Paradigm domain + DSO
dependencies:
- WP01
requirement_refs:
- FR-009
- FR-010
- FR-014
- FR-019
- NFR-002
- C-001
planning_base_branch: feat/pack-doctrine-artefacts
merge_target_branch: feat/pack-doctrine-artefacts
subtasks:
- T011
- T012
- T013
phase: Phase 2 - Kinds
agent: claude
history:
- at: '2026-10-23T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ParadigmIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ParadigmReference.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Paradigm.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ParadigmDso.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ParadigmEntityTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ParadigmIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ParadigmReference.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Paradigm.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ParadigmDso.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ParadigmEntityTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP04 — Paradigm domain + DSO

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objectives & Success Criteria

Model the **paradigm** — a way of working — the leanest kind: `name` + `summary` +
`directive_refs` + typed `references`. Canonical shape:
`contracts/packs/schemas/paradigm.schema.json`; entity table in
[data-model.md](../data-model.md) ("Paradigm"). Template: `Tactic.java` / `TacticDso.java`.

## Context & Constraints

- House style as WP02 (identifier VO, package-private ctor + fluent builder, ruleset,
  identity equality).
- `Paradigm implements CharterArtifact`: `kind()`=`ArtifactKind.PARADIGM`,
  `label()`=`name`, `description()`=`Optional.of(summary)`, `edges()` via `ArtifactEdges`
  (bare `directive_refs` → target kind `directive`, `REQUIRES`; typed `references` → `SUGGESTS`).

## Branch Strategy

- **Planning base / merge target**: `feat/pack-doctrine-artefacts`. Worktree per lane from `lanes.json`.

```bash
spec-kitty agent action implement WP04 --agent claude --mission pack-doctrine-artefacts-01M5JHD0
```

Build: `./gradlew :packs:test --tests '*ParadigmEntityTest'`.

## Subtasks & Detailed Guidance

### Subtask T011: identity + reference VOs `[P]`
- `ParadigmIdentifier(String value)` — invariant `^[a-z][a-z0-9-]*$`.
- `ParadigmReference(String type, String id)` record — both required (note: no `when`/`reason`,
  unlike directive; m6).

### Subtask T012: `Paradigm` entity + ruleset
Fields: `id`, `packId`, `schemaVersion`, `name`, `summary`, `List<String> directiveRefs`,
`List<ParadigmReference> references`. Ruleset: non-blank `schemaVersion` (`^1\.0$`),
`name`, `summary`; **every `directiveRefs` item non-blank** (R4/m-3). Implement
`CharterArtifact`.

### Subtask T013: `ParadigmDso`
Flat record: `id, schemaVersion, name, summary, List<String> directiveRefs,
List<Reference> references` with nested `record Reference(String type, String id)`.
Immutable list copies; required fields non-null.

## Red-first evidence (required)

`ParadigmEntityTest` seen failing first, then green; both hashes in history.

## Definition of Done

- [ ] T011–T013 done. `ParadigmEntityTest`: required-field invariants (reject blank
  `summary` with a valid control); blank `directiveRefs` item rejected; `edges()` yields a
  `directive`-target `REQUIRES` edge for each `directive_ref` and `SUGGESTS` for typed refs;
  identity equality.
- [ ] No field outside the canonical schema. `:packs:test` targeted run green.
- [ ] Did not touch shared adapter/aggregate files.

## Risks

- Giving `ParadigmReference` a `when`/`reason` it does not have in the schema — C-001 violation.
- Forgetting the non-blank directive_refs invariant — `edges()` would throw on `[""]`.

## Reviewer guidance

Confirm the paradigm stays lean (no invented fields), `directive_refs` become
`directive`-kind edges, and the required-field invariants have same-fixture positive controls.
