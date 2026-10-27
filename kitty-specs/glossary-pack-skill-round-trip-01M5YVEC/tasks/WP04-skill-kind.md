---
work_package_id: WP04
title: Skill kind
dependencies:
- WP01
- WP02
- WP03
requirement_refs:
- FR-002
- FR-003
- FR-004
- FR-010
- NFR-001
- C-003
planning_base_branch: feat/192-glossary-pack-skill-round-trip
merge_target_branch: feat/192-glossary-pack-skill-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/192-glossary-pack-skill-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/192-glossary-pack-skill-round-trip unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-glossary-pack-skill-round-trip-01M5YVEC
base_commit: 37095e29ce925c3090d2e3a6433f782a0de84237
created_at: '2026-10-27T16:51:39.502239+00:00'
subtasks:
- T013
- T014
- T015
- T016
phase: Phase 2 - The kinds
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/Skill
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Skill.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/SkillIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/SkillForm.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/SkillParameter.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/SkillInvocation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/SkillExpansion.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/SkillDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/SkillYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/SkillBinding.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/SkillTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/SkillYamlTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Skill.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/SkillIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/SkillForm.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/SkillParameter.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/SkillInvocation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/SkillExpansion.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/SkillDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/SkillYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/SkillBinding.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/SkillTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/SkillYamlTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP04 — Skill kind

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Bind `skill` in the ADR-0037 registry with its prompt body as a companion (spec FR-002, FR-003, FR-004, FR-010;
plan "Skill (IC-03)"). After this WP every `*.skill.yaml` loads with the text of its `body_path` file, and the store
upserts both.

**Out-of-map edit (recorded):** one line appended to `KindBindings.ALL` and one to `YamlFormats.ALL`, files WP03
owns — the registry's documented join point (ADR-0037 decision 4).

## Branch Strategy

Planning base and merge target: `feat/192-glossary-pack-skill-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP04 --agent claude --mission glossary-pack-skill-round-trip-01M5YVEC`.

## Subtasks

### T013 — Domain

- `SkillIdentifier(String value)`: core's grammar `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, at most 64 characters.
- `SkillForm` enum `PROMPT("prompt")`, `WRAPPER("wrapper")` with `token()` and `fromToken` (unknown →
  `IllegalArgumentException`).
- `SkillParameter(String name, Optional<Boolean> required, Optional<String> description, Optional<String>
  defaultValue)`; name not blank.
- `SkillInvocation(Optional<Boolean> userInvocable, Optional<Boolean> modelInvocable, List<String> sideEffects)`:
  kept as written; javadoc says core's forcing of `model_invocable` off is deliberately not applied (ADR-0036:
  equality is with the file).
- `SkillExpansion(String target, Optional<String> args)`; target not blank.
- `Skill implements CharterArtifact`: id, packId, schemaVersion, title, description, triggers, form,
  `Optional<String> bodyPath`, `Optional<String> body`, `Optional<SkillExpansion> expandsTo`, parameters,
  `Optional<SkillInvocation> invocation`, tools, maintainers, `Optional` version, overrides, enhances.
  Invariants: title and description not blank; prompt ⇒ body path and body present, no expansion; wrapper ⇒
  expansion present, no body path, no body. `label()` the title; `description()` the description;
  `relationships()` empty (FR-010).

### T014 — DSO and YAML format

- `SkillDso` mirrors the file (`schemaVersion`, `id`, `title`, `description`, `triggers`, `form` as text,
  `Optional bodyPath`, `Optional<Expansion> expandsTo`, `List<Parameter> parameters`,
  `Optional<Invocation> invocation`, `tools`, `Optional version`, `maintainers`, `Optional overrides`,
  `Optional enhances`) **plus `Optional<String> body`**, the companion text (not a YAML key).
- `SkillYaml`: booleans read with `instanceof Boolean` (as `MissionStepContractYaml.bool`), written as `Boolean`.
  Write key order: `schema_version`, `id`, `title`, `description`, `triggers`, `form`, `body_path`, `expands_to`
  (`target`, `args`), `parameters` (`name`, `required`, `description`, `default`), `invocation`
  (`user_invocable`, `model_invocable`, `side_effects`), `tools`, `version`, `maintainers`, `overrides`, `enhances`.
  `schema_version` and `version` stay strings (`"1.0"`, `1.0.0`). An `invocation` with every field empty is omitted:
  ADR-0036 decision 2 makes an empty mapping of a declared field equal to absent. The body is never written by the
  format.

### T015 — Binding and registration

- `SkillBinding` (enum `INSTANCE`): `kind()` `SKILL`; file token and schema name are the token `skill` (defaults).
  `companions(dso)`: `[new Companion(bodyPath, body.map(Text::new))]` when `bodyPath` is present, else empty.
  `withCompanions(dso, contents)`: a copy with `body` = the `Text` at `bodyPath`. `toEntity` / `toDso` map every
  field including the body.
- Append the binding and the format (last position).

### T016 — Tests

- `SkillTest` (domain): prompt and wrapper builds; each form invariant refused; id grammar and length; identity
  equality; `relationships()` empty; URN `skill:<id>`.
- `SkillYamlTest`:
  - a planted prompt skill with every field non-default (parameters with and without `required`, a `default`,
    `invocation` with all three fields, `tools`, `overrides`) round-trips by `MeaningComparator`
    (`compareTexts("skill", …)`) through entity and back;
  - `invocation` with `side_effects` and no `model_invocable` writes no `model_invocable` (G7);
  - `version: 1.0.0` stays the string; booleans stay booleans;
  - a planted wrapper skill (`expands_to`, no `body_path`) round-trips and has no companion;
  - store level (`FilesystemPackStore` with the YAML strategy over a temp pack with `pack.yaml`): a skill and its
    body read with the body text byte for byte; a missing body is a `companion` violation and the skill is absent;
    an upsert of a new skill writes `<id>.skill.yaml` and its body; a changed `body_path` removes the old body.
- Run `KindBindingsTest`, `DomainRoundTripTest`; append to a hard-coded kind list only if one exists.

## Definition of Done

- `./gradlew :packs:test` and `:packs:check` green on Java 25; ArchUnit green.
- `./gradlew :packs:openPacksRoundTrip`: `internal`'s five skills load with their bodies and no violation (the
  whole-pack write of companions lands in WP05; until then the seam test may report the bodies missing from the
  written pack — note the observed state in the WP's review notes, do not work around it).

## Risks / reviewer guidance

- No default injected anywhere (`tools: ["*"]`, `version`, `user_invocable`, `required`); no forced
  `model_invocable`.
- `body` must survive `toEntity`/`toDso` unchanged, including CRLF and the absence of a final newline.
