---
work_package_id: WP03
title: Storage binding and YAML format
dependencies:
- WP01
- WP02
requirement_refs:
- FR-002
- FR-003
- FR-007
- C-002
planning_base_branch: feat/mission-step-contract-round-trip
merge_target_branch: feat/mission-step-contract-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/mission-step-contract-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/mission-step-contract-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T008
- T009
- T010
- T011
phase: Phase 2 - User Story 2
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/MissionStepContract
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/MissionStepContractDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/MissionStepContractYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/MissionStepContractBinding.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MissionStepContractRoundTripTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/MissionStepContractDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/MissionStepContractYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/MissionStepContractBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlFormats.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MissionStepContractRoundTripTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP03 — Storage binding and YAML format

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Bind `mission_step_contract` in the ADR-0037 registry: a DSO, a YAML format and a binding, registered by one line
in `KindBindings` and one in `YamlFormats` (spec FR-001, FR-002, FR-003, FR-007).

After this WP, the pack load, the whole-pack load and write, the commit path and the fingerprint include every
`*.step-contract.yaml`.

**Out-of-map edit (recorded):** the one-line append in `KindBindings.ALL` belongs to this WP, although WP01 owns
the file. It is the registry's documented join point (ADR-0037 decision 4).

## Branch Strategy

Planning base and merge target: `feat/mission-step-contract-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP03 --agent claude --mission mission-step-contract-round-trip-01M5YHYD`.

## Patterns to mirror

- `TacticDso.java`, `TacticYaml.java` and `TacticBinding.java` (steps with nested optionals).
- `YamlFields`: `required`, `optional`, `list` and `strings` read scalars as text.
- `YamlDumpEmitter`: `mapping()`, `scalarInto`, `stringsInto` and `mappingsInto` omit an empty optional or list.
  The dump schema quotes strings that a YAML 1.1 reader would mistype.

## Subtask T008 — `MissionStepContractDso` [P]

Make it a package-private record mirroring the schema, with nested records `Step`, `Input`, `Delegation` and `Gate`:

- `id`, `schemaVersion`, `action` and `mission` are `String`.
- `steps` is `List<Step>`; `gates` is `List<Gate>`.
- `Step(id, description, Optional<String> command, List<Input> inputs, Optional<Delegation> delegatesTo, Optional<String> guidance)`.
- `Input(flag, source, Optional<Boolean> optional)`.
- `Delegation(String kind, List<String> candidates)`. The kind is plain text here; the binding maps it to
  `ArtifactKind`.
- `Gate(onTransition, handler, Optional<String> handlerKind, schemaVersion, Optional<Boolean> failOpen, Optional<String> provenance)`.

Each compact constructor null-checks and copies its lists. Write the class javadoc in the style of `TacticDso`:
record equality is the fidelity assertion.

## Subtask T009 — `MissionStepContractYaml`

`enum MissionStepContractYaml implements YamlFormat<MissionStepContractDso> { INSTANCE; }`

- **`read(Map<?, ?> root)`**:
  - Use `required` and `optional` for text, and `list` for lists.
  - Add a private `bool(Map<?, ?> owner, String key)`. It returns `Optional<Boolean>`: present only when the value
    `instanceof Boolean`; null or absent is empty. The schema has already checked the type.
  - `delegates_to` is read when it is a `Map`; null means absent.
- **`write(MissionStepContractDso)`**:
  - Top-level key order: `schema_version`, `id`, `action`, `mission`, `steps`, `gates`.
  - Step key order: `id`, `description`, `command`, `inputs`, `delegates_to` (`kind`, `candidates`), `guidance`.
  - Gate key order: `on_transition`, `handler`, `handler_kind`, `schema_version`, `fail_open`, `provenance`.
  - Input key order: `flag`, `source`, `optional`.
  - Put a `Boolean` value into the mapping as a `Boolean`, so it dumps as `true`/`false`, not `"true"`.
  - Omit empty optionals and lists (`mappingsInto`, `scalarInto`). `candidates` is never empty: the schema and the
    domain refuse that.

## Subtask T010 — Binding and registration

`enum MissionStepContractBinding implements KindBinding<MissionStepContractDso, MissionStepContract> { INSTANCE; }`

- `kind()` is `MISSION_STEP_CONTRACT`; `entityType()`; `dsoType()`; `idOf(dso)` is `dso.id()`.
- `schema()` returns `"mission-step-contract"`, and `fileToken()` returns `"step-contract"`. The javadoc cites
  `file-kinds.yaml`.
- `toEntity(pack, dso)` builds the entity:
  - map the delegation kind with `ArtifactKind.fromToken(kind)`;
  - `.orElseThrow(() -> new IllegalArgumentException("unknown artifact kind '" + kind + "' in delegates_to"))`.
- `toDso(entity)` is the inverse; the delegation kind becomes `kind.token()`.
- Append `MissionStepContractBinding.INSTANCE` as the **last** entry of `KindBindings.ALL`, and
  `MissionStepContractYaml.INSTANCE` as the last entry of `YamlFormats.ALL`. Appending keeps the fingerprint of
  every pack that has no step contract (NFR-002).

## Subtask T011 — Tests (write first)

`MissionStepContractRoundTripTest` in `packs.infra.storage`, using `YamlSerializationStrategy` and
`FilesystemPackStore`/`DomainRepositoryService` over a `@TempDir` pack. Cover:

1. **Full shape.** A planted file with every field: two steps, one with `inputs` (`optional: false` written and one
   input without `optional`), `delegates_to`, `command` and folded `guidance` (`>` with a trailing newline); and a
   gate with all six fields (`fail_open: true`). Read it, map to the entity and back, write, and read again: the
   DSOs are equal. The written text holds `fail_open: true` unquoted, and `schema_version: '1.0'` or `"1.0"`
   quoted. Parse the written text with `Yaml.load` and assert that the value `instanceof Boolean`.
2. **Absent stays absent.** A minimal file (one step with `id` and `description`) writes no `command`, `inputs`,
   `delegates_to`, `guidance` or `gates` key.
3. **Defaults kept as written.** A gate without `handler_kind` and `fail_open` writes neither. A gate with
   `handler_kind: mission_step_contract` and `fail_open: true` writes both.
4. **Unknown keys refused** at the top level, in a step, in `delegates_to`, in an input and in a gate. Each read is
   `FileRead.Unreadable` with an `additionalProperties` violation. Through `DomainRepositoryService.load`, the pack's
   `violations()` names the file and the artifact is absent.
5. **Pack load from a nested directory.**
   - `missions/built_in_step_contracts/implement.step-contract.yaml` loads into `Pack.artifacts(MISSION_STEP_CONTRACT)`.
   - `PackFileSet.files(pack, MISSION_STEP_CONTRACT)` lists it.
   - A new contract saved through `DomainRepositoryService` is created as `<id>.step-contract.yaml` in the pack
     directory and loads back.
6. **Unknown delegation kind.** A file whose `delegates_to.kind` is outside the enum is refused by the schema.

## Definition of Done

- The tests are green, and so are `KindBindingsTest` (now with six bindings) and the style check.
- One commit: `feat(storage): bind the mission step contract kind (#191)`.

## Reviewer guidance

- Booleans survive as booleans, and strings that look like numbers stay strings.
- No reader rule tolerates a malformed file (ADR-0036 ruling 3).
