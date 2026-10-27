---
work_package_id: WP01
title: Schema-name seam
dependencies: []
requirement_refs:
- FR-004
- C-003
planning_base_branch: feat/mission-step-contract-round-trip
merge_target_branch: feat/mission-step-contract-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/mission-step-contract-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/mission-step-contract-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
phase: Phase 1 - Foundations
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBindings.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlSerializationStrategy.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/KindBindingsTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP01 — Schema-name seam

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Let a `KindBinding` name the schema that validates its files, beside the file token it already names. The store and
the strategy must resolve a schema to a kind through the registry instead of assuming the schema name is the kind's
token (spec FR-004; research D1; grounding G1).

The `mission_step_contract` kind has three spellings:

- token `mission_step_contract`
- schema `mission-step-contract`
- file token `step-contract`

If it were bound today, `PackFileSet.loaded` would call `ArtifactKind.fromToken("mission-step-contract")`. That
returns empty, so **no file would load, silently**. `YamlSerializationStrategy.read` would validate against a
schema named `mission_step_contract`, which does not exist.

This WP changes **no behaviour** for the five bound kinds, whose schema equals their token. It lands as one isolated
commit so #190 (`agent-profile`/`agent`) can reuse it.

## Branch Strategy

Planning base and merge target: `feat/mission-step-contract-round-trip`. The execution worktree comes from the lane
`finalize-tasks` computes (`lanes.json`). Implement with
`spec-kitty agent action implement WP01 --agent claude --mission mission-step-contract-round-trip-01M5YHYD`.

## Context

- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBinding.java`: `fileToken()` defaults
  to `kind().token()`. Mirror it.
- `KindBindings.java`: the registry; `of(ArtifactKind)` looks a binding up by kind.
- `PackFileSet.java:134-148` (`loaded`): classifies each YAML file with `PackFileKind.bundled().classify(prefix + file)`,
  then maps `mapped.kind()` (a **schema name**) with `ArtifactKind.fromToken`.
- `YamlSerializationStrategy.java:35`: `String schema = kind.kind().token();`.
- `contracts/packs/file-kinds.yaml`: the single authority mapping file globs to schemas.

## Subtask T001 — `schema()` and `bySchema()`

1. In `KindBinding`, add, beside `fileToken()`:

   ```java
   /**
    * The name of the schema that validates this kind's files, as {@code file-kinds.yaml} maps
    * {@code *.<fileToken>.yaml} to it. It is the kind's token unless the pack format names the schema otherwise
    * (a mission step contract is validated by {@code mission-step-contract}).
    *
    * @return the schema name
    */
   default String schema() {
       return kind().token();
   }
   ```

2. In `KindBindings`, add `static Optional<KindBinding<?, ?>> bySchema(String schema)`, filtering `ALL` by
   `binding.schema().equals(schema)`. Javadoc: "the binding whose files the named schema validates; empty when no
   bound kind uses it". Update the class javadoc where it says "no other storage class names a kind" only if it
   becomes inaccurate (it does not).

## Subtask T002 — Resolve through the registry

1. In `PackFileSet.loaded`, replace the `ArtifactKind.fromToken(mapped.kind()).filter(KindBindings.kinds()::contains)`
   chain with `KindBindings.bySchema(mapped.kind()).map(KindBinding::kind)`. Keep the `EnumMap` and the order.
2. In `YamlSerializationStrategy.read`, use `String schema = kind.schema();`. The `IllegalStateException` message
   keeps naming the schema.
3. Search `packs.infra.storage` for any other place that turns a schema name into a kind, or a kind into a schema
   name (`grep -n "fromToken\|token()" *.java`). Fix any such place the same way, and note it in the commit message.
   `FilesystemPackStore.newFile` uses the token only in messages, which is fine.

## Subtask T003 — Tests

In `KindBindingsTest`:

1. `everyBoundKindHasASchema`: iterate `KindBindings.all()` and validate with `binding.schema()`.
2. `entityTypesMatchTheKinds`: compare `entityType().getSimpleName().toLowerCase(Locale.ROOT)` with
   `kind().token().replace("_", "")`. The entity of `mission_step_contract` is `MissionStepContract`.
3. New test, **"each binding's file name is classified to its own schema by file-kinds.yaml"**. For every binding,
   `PackFileKind.bundled().classify("probe-pack/" + binding.fileName("x"))` must be a `PackFileKind.Mapped` whose
   `kind()` equals `binding.schema()`. This ties both hooks to the single authority.
4. New test: `bySchema(binding.schema())` returns that binding for every binding, and `bySchema("no-such")` is empty.

## Subtask T004 — Regression

Run `./gradlew :packs:test`. Every existing test must pass unchanged, in particular:

- `PackFileSetTest` and `PackFileSetExactnessTest` (the digest lists of the five kinds);
- `KindBindingsTest.theFirstFiveKeepTheirOrder`;
- the whole-pack gate tests.

No fingerprint value may move.

## Definition of Done

- The four subtasks are done; `./gradlew :packs:test` and `:packs:spotlessCheck` (or the repo's style task) are green.
- No file outside `owned_files` is changed.
- One commit: `feat(storage): a binding names its schema (#191, ADR-0037 decision 4)`.

## Reviewer guidance

- Look for another schema = token assumption that slipped through. `grep -rn "fromToken(mapped" service/packs` must
  be empty.
- The new tests must fail if `schema()` is removed from a binding that needs it. WP03's binding is the positive
  control once it lands.
