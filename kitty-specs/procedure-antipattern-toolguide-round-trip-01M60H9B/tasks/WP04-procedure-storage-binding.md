---
work_package_id: WP04
title: Procedure storage binding
dependencies:
- WP03
requirement_refs:
- FR-002
- FR-003
- FR-003a
- FR-007
- FR-008
- SC-004
- SC-005
- C-005
planning_base_branch: feat/194a-procedure-antipattern-toolguide
merge_target_branch: feat/194a-procedure-antipattern-toolguide
branch_strategy: Planning artifacts for this mission were generated on feat/194a-procedure-antipattern-toolguide. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/194a-procedure-antipattern-toolguide unless the human explicitly redirects the landing branch.
subtasks:
- T016
- T017
- T018
- T019
- T020
phase: PR-A2 - Procedures
agent: claude
history:
- at: '2026-10-29T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ProcedureDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ProcedureYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ProcedureBinding.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ProcedureYamlTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ProcedureEdgesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/RetiredFieldNamesTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ProcedureDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ProcedureYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ProcedureBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBindings.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlFormats.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ProcedureYamlTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ProcedureEdgesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/RetiredFieldNamesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/KindBindingsTest.java
- service/packs/src/test/resources/roundtrip-fixtures/procedure/**
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP04 — Procedure storage binding

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Store procedures through the ADR-0037 registry: `ProcedureDso`, `ProcedureYaml`, `ProcedureBinding`, appended to
`KindBindings` and `YamlFormats`. Prove references are never minted (with a tactic positive control) and that no main
source accepts a retired field name.

**Known red window**: registering the binding makes the pinned built-in procedures (old shape) refused, so
`:packs:openPacksRoundTrip` and `:packs:wholePackRoundTrip` go red at the old pin until WP10 moves the pin (plan
IC-06; tasks.md). `:packs:test` must stay green. Reviewers verify against fixtures.

## Branch Strategy

Planning branch and merge target `feat/194a-procedure-antipattern-toolguide`; lanes from `lanes.json`.
`spec-kitty agent action implement WP04 --agent <name> --mission procedure-antipattern-toolguide-round-trip-01M60H9B`.
**PR-A2.**

## Context

- Template: `TacticDso`, `TacticYaml`, `TacticBinding` and their tests (`TacticYamlTest`, corpus tests); the registry
  javadoc in `KindBindings` (append only — order is the digest order) and `YamlFormats`.
- `KindBinding` defaults: `fileToken` = `procedure`, `schemaName` = `procedure`, `idKey` = `id` (no overrides needed).
- `ArtifactEdges.build(...)` is what entities call to mint typed references as `SUGGESTS`; `Procedure.relationships()`
  (WP03) returns empty and must stay so.

## Subtasks

### T016 — `ProcedureDso`, `ProcedureYaml`

- DSO record mirrors the schema exactly (snake_case keys mapped to camelCase components), nested records for step,
  anti-pattern entry, reference. Optional fields as `Optional`, lists never null.
- `ProcedureYaml.read(Map)` reads a schema-valid tree (the schema has already refused unknown or retired keys — do
  **not** add any fallback for `name`/`purpose`/`actor`); `write(dso)` emits keys in schema order using the same
  emitter as `TacticYaml`, omitting absent optionals and empty lists.

### T017 — `ProcedureBinding`; registration

- `enum ProcedureBinding implements KindBinding<ProcedureDso, Procedure>` with `toEntity`/`toDso`.
- Append `ProcedureBinding.INSTANCE` to `KindBindings.ALL` and `ProcedureYaml.INSTANCE` to `YamlFormats.ALL` (last
  entries; note in the commit message that this is the append-only registry edit).
- `KindBindingsTest`: the new kind has its format; counts updated.

### T018 — References are never minted

`ProcedureEdgesTest`: one fixture pack (under `roundtrip-fixtures/procedure/`) holding a tactic and a procedure whose
reference lists are identical in shape and point at targets that exist in the pack (e.g. a directive). Load it through
the store; assert the tactic contributes its `SUGGESTS` relationship and the procedure contributes none, while the
procedure entity still holds its references. Both halves in one test (non-vacuity).

### T019 — Round-trip and registry tests

`ProcedureYamlTest`: sanitized fixtures round-trip DSO → YAML → DSO → entity → DSO equal; anti-pattern entries with
only a title, title+content, one example, both examples; step with and without `role`; references with and without
`when`/`reason`. A retired-shape fixture is refused at load with the schema violation (its sanitized twin loads).

### T020 — Retired-name source scan [P]

`RetiredFieldNamesTest`: scan `service/packs/src/main/java/**/Procedure*.java` and
`service/packs/src/main/java/**/ProcedureYaml.java` for the string literals `"name"`, `"purpose"`, `"actor"`,
and assert none is read as a procedure key (keep the check targeted: these words legitimately appear elsewhere).
Pair it with the refused-fixture assertion from T019 (SC-005). Add a comment block in nothing else — the
`expected-red.yaml` `kind: procedure` entry is removed by WP10, not here.

## Definition of Done

- `./gradlew :packs:test` green; style checks clean; changes inside `owned_files`.
- `:packs:openPacksRoundTrip` red **only** because of pinned procedures in the old shape (state the failing files in
  the review handoff).
- Mark T016–T020 done.

## Risks / reviewer guidance

- Reviewer: registry edits are appends; no tolerance for retired keys; edge test has both halves in one fixture.
