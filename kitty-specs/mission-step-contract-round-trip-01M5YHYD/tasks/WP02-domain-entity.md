---
work_package_id: WP02
title: Domain entity
dependencies: []
requirement_refs:
- FR-001
- FR-008
- NFR-001
- C-004
planning_base_branch: feat/mission-step-contract-round-trip
merge_target_branch: feat/mission-step-contract-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/mission-step-contract-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/mission-step-contract-round-trip unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-mission-step-contract-round-trip-01M5YHYD
base_commit: 1fb4d518cd0b4c9840c80f73a7a9e32d1dfd4892
created_at: '2026-10-27T11:42:27.066431+00:00'
subtasks:
- T005
- T006
- T007
phase: Phase 1 - Foundations
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MissionStepContract.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MissionStepContractIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ContractStep.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepDelegation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepInput.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GateBinding.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/MissionStepContractTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MissionStepContract.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MissionStepContractIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ContractStep.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepDelegation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepInput.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GateBinding.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/MissionStepContractTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP02 — Domain entity

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Model a mission step contract as a domain artifact in `packs.domain` (spec FR-001, FR-008, NFR-001;
[data-model.md](../data-model.md)). It holds every field the closed schema
`contracts/packs/schemas/mission-step-contract.schema.json` declares, and keeps optional fields as written or
absent. It mints no relationships.

## Branch Strategy

Planning base and merge target: `feat/mission-step-contract-round-trip`. The worktree comes from `lanes.json`.
Implement with `spec-kitty agent action implement WP02 --agent claude --mission mission-step-contract-round-trip-01M5YHYD`.

## Context and patterns to mirror

- `Paradigm.java`: identity equality, a `ModularRuleset` of `Constraints.of(message, predicate)`, a package-private
  constructor, a fluent `Builder` whose `build()` does `Objects.requireNonNull` per required field, and
  `CharterArtifact` members.
- `ParadigmIdentifier.java`: the identifier pattern (copy its shape and validation exactly).
- `TacticStep.java`: a public record value with `RULES.guard` in the compact constructor and `List.copyOf`.
- `CharterArtifact` (kernel):
  - `kind()`, `packId()`, `localId()`, `label()`;
  - `description()`, an `Optional`;
  - `schemaVersion()`, an `Optional`;
  - `relationships()`;
  - `urn()`, derived from the others.
- ArchUnit (`service/deployable/.../HexagonalArchitectureTest.java`): `packs.domain` may use the kernel and the JDK
  only.

## Subtask T005 — Value records [P]

Make each a public record in `packs.domain` with javadoc on every component.

- **`StepInput(String flag, String source, Optional<Boolean> optional)`**: `flag` and `source` are not blank (the
  schema says `minLength: 1`). `optional` is kept as written: empty means absent, *not* `false`.
- **`StepDelegation(ArtifactKind kind, List<String> candidates)`**: `candidates` is non-empty and no candidate is
  blank. Keep the order and duplicates.
- **`ContractStep(String id, String description, Optional<String> command, List<StepInput> inputs, Optional<StepDelegation> delegatesTo, Optional<String> guidance)`**:
  `id` is not blank. `description` is required and may be any string; the schema allows empty, so do not add a
  rule the schema does not have. Copy the lists.
- **`GateBinding(String onTransition, String handler, Optional<String> handlerKind, String schemaVersion, Optional<Boolean> failOpen, Optional<String> provenance)`**:
  - Javadoc: "inert data: what a binding does is #155's".
  - No rules beyond non-null, because the schema only types the fields.
  - `handlerKind` stays text, since it is a closed enum that #155 owns.

Messages read like the existing ones ("A mission step contract step needs an id that is not blank.").

## Subtask T006 — Identifier and entity

1. `MissionStepContractIdentifier`: copy `ParadigmIdentifier`.
2. `MissionStepContract implements CharterArtifact`:
   - Fields: `id`, `packId`, `schemaVersion`, `action`, `mission`, `List<ContractStep> steps`,
     `List<GateBinding> gates`.
   - Invariants (`RULES`):
     - the schema version is not blank (no `1.0` pattern: the schema sets none);
     - `action` is not blank;
     - `mission` is not blank;
     - there is at least one step.
   - **Do not** refuse duplicate step ids. Validate mode owns that rule (`PackPolicy` step rules, research D5).
     Write a one-line comment saying so.
   - `kind()` returns `ArtifactKind.MISSION_STEP_CONTRACT`.
   - `localId()` returns `id.value()`.
   - `label()` returns `action`, as the CRG node's `label` is.
   - `description()` returns `Optional.empty()`.
   - `schemaVersion()` returns `Optional.of(schemaVersion)`.
   - `relationships()` returns `List.of()`. Javadoc: delegations are values; the pinned shard has no step-contract
     edges, and minting them is #148's.
   - Accessors: `id()`, `action()`, `mission()`, `steps()`, `gates()`.
   - `equals`/`hashCode` by identifier. `toString` is `MissionStepContract[<id> of pack <pack>]`.
   - `Builder`: `id`, `packId`, `schemaVersion`, `action`, `mission`, `step(ContractStep)` (appends) and
     `gate(GateBinding)` (appends).

## Subtask T007 — Tests (write first)

Create `MissionStepContractTest` in `service/packs/src/test/java/ai/speckitty/charter/packs/domain/`, using JUnit 5,
AssertJ and `@DisplayName` sentences, as in the neighbouring tests. Cover:

- a contract with two steps and one gate builds; steps and gates keep their order, duplicates included;
- the kind, local id, URN `mission_step_contract:<id>`, label (`action`), empty description and empty
  relationships;
- each invariant refuses with a `ConstraintBreachException`: blank action, blank mission, blank schema version, no
  steps, a blank step id, empty candidates, a blank candidate, a blank input flag;
- two duplicate step ids are **accepted** by the entity (pin the decision);
- identity equality: the same id with other fields different is equal; a different id is not equal;
- `StepInput` keeps `Optional.of(false)` apart from `Optional.empty()`.

## Definition of Done

- `./gradlew :packs:test --tests '*MissionStepContractTest*'` is green, and so are the style check and the
  `:deployable` ArchUnit test.
- Only `owned_files` are touched.
- One commit: `feat(domain): the mission step contract artifact (#191)`.

## Reviewer guidance

- No storage type and no YAML word in `packs.domain`.
- Every schema field has a home. Nothing is invented: no role, no profile (ADR-0032).
