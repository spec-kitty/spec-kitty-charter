---
work_package_id: WP03
title: Glossary pack kind
dependencies:
- WP02
requirement_refs:
- FR-001
- FR-003
- FR-004
- FR-010
- NFR-001
- C-003
- C-005
planning_base_branch: feat/192-glossary-pack-skill-round-trip
merge_target_branch: feat/192-glossary-pack-skill-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/192-glossary-pack-skill-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/192-glossary-pack-skill-round-trip unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-glossary-pack-skill-round-trip-01M5YVEC
base_commit: 3905a15ad16f24de51cc3ec75a62fd8de0d85f97
created_at: '2026-10-27T16:42:14.501908+00:00'
subtasks:
- T009
- T010
- T011
- T012
phase: Phase 2 - The kinds
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/GlossaryPack
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GlossaryPack.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GlossaryPackIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GlossaryTerm.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GlossaryPackDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GlossaryPackYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GlossaryPackBinding.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/GlossaryPackTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GlossaryPackYamlTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GlossaryPack.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GlossaryPackIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GlossaryTerm.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GlossaryPackDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GlossaryPackYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GlossaryPackBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBindings.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlFormats.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/GlossaryPackTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GlossaryPackYamlTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP03 — Glossary pack kind

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Bind `glossary_pack` in the ADR-0037 registry (spec FR-001, FR-003, FR-004, FR-010; plan "Glossary pack (IC-02)").
After this WP every `*.glossary-pack.yaml` loads into the `Pack` aggregate and writes back by meaning; a new one is
created as `<id>.glossary-pack.yaml`; the fingerprint digests them.

**Joint ownership note**: this WP owns `KindBindings.java` and `YamlFormats.java`; WP04 appends one line to each
after this WP (recorded there).

## Branch Strategy

Planning base and merge target: `feat/192-glossary-pack-skill-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP03 --agent claude --mission glossary-pack-skill-round-trip-01M5YVEC`.

## Patterns to mirror

`MissionStepContract` / `MissionStepContractIdentifier` / `StepInput` (domain), `MissionStepContractDso` /
`MissionStepContractYaml` / `MissionStepContractBinding` (storage), `MissionStepContractTest` and
`MissionStepContractRoundTripTest` (tests). Same javadoc density, `ModularRuleset` + `Constraints.of` invariants,
entity identity equality, builder.

## Subtasks

### T009 — Domain

- `GlossaryPackIdentifier(String value)`: not blank (the schema allows any non-empty string; do not invent a
  pattern; the internal pack's id is `spk-internal-glossary`).
- `GlossaryTerm(String surface, String definition, double confidence, String status, List<String> seeAlso,
  Optional<String> introducedInMission, List<String> synonymsToAvoid, List<String> aliases,
  List<String> bannedSynonyms)`: surface and status not blank; `confidence` finite; lists copied (empty = absent,
  ADR-0036 decision 2).
- `GlossaryPack implements CharterArtifact`: id, packId, provenance, `Optional<String> description`, terms
  (authored order). Invariants: at least one term; surfaces unique (message names the duplicates, sorted, as core
  does). `kind()` `GLOSSARY_PACK`; `label()` the id; `schemaVersion()` empty (the file has none);
  `description()` the description; `relationships()` `List.of()` with javadoc (FR-010). Builder with `term(...)`.

### T010 — DSO and YAML format

- `GlossaryPackDso(id, provenance, Optional<String> description, List<Term> terms)` with nested
  `Term(surface, definition, double confidence, status, List<String> seeAlso, Optional<String> introducedInMission,
  List<String> synonymsToAvoid, List<String> aliases, List<String> bannedSynonyms)`. Javadoc: record equality is the
  round-trip assertion; lists keep order and duplicates; absent, null and empty lists read alike.
- `GlossaryPackYaml`: read with `YamlFields` (`required`, `optional`, `strings`, `list`); `confidence` from the
  parsed `Number` (`doubleValue()`); the schema guarantees the type. Write key order `id`, `provenance`,
  `description`, `terms`; a term `surface`, `definition`, `confidence`, `status`, `see_also`,
  `introduced_in_mission`, `synonyms_to_avoid`, `aliases`, `banned_synonyms`. `confidence` goes into the mapping as a
  `Double` (it dumps as a number: `1.0`, `0.95`); strings through the dump schema (quoted when they would read as
  another type). Empty lists and absent optionals are omitted.

### T011 — Binding and registration

- `GlossaryPackBinding` (enum `INSTANCE`): `kind()` `GLOSSARY_PACK`, `fileToken()` and `schemaName()` both
  `"glossary-pack"`, `idOf` the id, `toEntity`/`toDso` mapping every field.
- Append `GlossaryPackBinding.INSTANCE` to `KindBindings.ALL` and `GlossaryPackYaml.INSTANCE` to `YamlFormats.ALL`
  (last position; packs without glossary packs keep their digest).

### T012 — Tests

- `GlossaryPackTest` (domain): builds; refuses no terms; refuses a duplicate surface (message names it); blank
  surface/status refused; non-finite confidence refused; identity equality; `relationships()` empty; URN
  `glossary_pack:<id>`.
- `GlossaryPackYamlTest`: a planted file with every field set non-default (all five optional term fields, a
  multi-line folded definition, `confidence: 1.0` and `0.6`, a string that would read as a number, e.g. status
  `"1.0"`) reads, maps to the entity and back, writes, and compares equal by `MeaningComparator.adr0036()`
  (`compareTexts("glossary-pack", …)`); term order pinned on a deliberately unsorted fixture; written
  `confidence` is a YAML number; `KindBindings.bySchema("glossary-pack")` and `PackFileKind` classify
  `<pack>/x.glossary-pack.yaml` to this binding; the store creates a new glossary pack at
  `<id>.glossary-pack.yaml` and finds it again; a pack holding `glossary_packs/spk-internal.glossary-pack.yaml`
  with `id: spk-internal-glossary` loads and writes back at the same path.
- Run the existing `KindBindingsTest`, `DomainRoundTripTest`, `PackFileSetExactnessTest`; adjust only a hard-coded
  kind list, if any, by appending (record the out-of-map edit in the commit message).

## Definition of Done

- `./gradlew :packs:test` and `:packs:check` green on Java 25; ArchUnit green (entity in `packs.domain`, no storage
  import).
- `./gradlew :packs:openPacksRoundTrip` green: `internal` and `qa`'s glossary packs now load with no violation.
- `:packs:wholePackRoundTrip` will report `UNEXPECTEDLY_GREEN kind other:glossary_packs` / a modelled kind
  baseline problem until WP06 updates `expected-red.yaml` — note it, do not edit that file here.

## Risks / reviewer guidance

- `confidence` must never round-trip as a string (E3-style regression) — the planted test checks the YAML node type.
- Do not normalize or sort terms; do not reject `status` values outside `active/draft/deprecated` (core does not).
