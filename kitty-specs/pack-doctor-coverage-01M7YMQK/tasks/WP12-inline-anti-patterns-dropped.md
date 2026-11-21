---
work_package_id: WP12
title: The procedure and styleguide shapes drop anti_patterns (R-r; closes issue 154)
dependencies:
- WP10
requirement_refs:
- FR-015
- FR-010
- NFR-001
- C-002
- SC-004
planning_base_branch: feat/pack-doctor-coverage
merge_target_branch: feat/pack-doctor-coverage
branch_strategy: Planning artifacts for this mission were generated on feat/pack-doctor-coverage. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-doctor-coverage unless the human explicitly redirects the landing branch.
subtasks:
- T046
- T047
- T048
- T049
- T050
phase: Phase 1 - Foundation
history:
- at: '2026-11-21T22:30:00Z'
  actor: planner-priti
  action: Prompt generated for ruling R-r (all of issue 154 in this PR) - the inline drop, ADR-0052 d16
agent_profile: java-jenny
authoritative_surface: contracts/packs/schemas/
create_intent:
- contracts/charter/scenarios/registration/inline-anti-patterns.feature
- contracts/charter/changes/2026-11-21-inline-anti-patterns-dropped.md
- service/packs/src/test/resources/schema-fixtures/procedure/refused-inline-anti-patterns.procedure.yaml
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/antipattern/InlineAntiPatternCheckTest.java
execution_mode: code_change
owned_files:
- contracts/packs/schemas/procedure.schema.json
- contracts/packs/schemas/styleguide.schema.json
- contracts/charter/schemas/Styleguide.yaml
- contracts/charter/schemas/StyleguideRegistration.yaml
- contracts/charter/schemas/StyleguideAntiPattern.yaml
- contracts/charter/schemas/Procedure.yaml
- contracts/charter/schemas/ProcedureAntiPattern.yaml
- contracts/charter/examples/Artifact.styleguide.yaml
- contracts/charter/examples/Artifact.procedure.yaml
- contracts/charter/scenarios/procedures.feature
- contracts/charter/scenarios/styleguides.feature
- contracts/charter/scenarios/artifacts.feature
- contracts/charter/scenarios/registration/inline-anti-patterns.feature
- contracts/charter/changes/2026-11-21-inline-anti-patterns-dropped.md
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Procedure.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Styleguide.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AntiPatternEntry.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StyleguideAntiPattern.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ProcedureBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ProcedureDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ProcedureYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StyleguideBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StyleguideDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StyleguideYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactDtos.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/StyleguideController.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ProcedureEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/StyleguideEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RegistrationListsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/StyleguideControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DoctrineRepositoryContractTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DoctrineRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DoctrineValidationParityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ProcedureSchemaTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ProcedureYamlTest.java
- service/packs/src/test/resources/schema-fixtures/procedure/**
- service/packs/src/test/resources/roundtrip-fixtures/procedure/**
- service/deployable/src/test/java/ai/speckitty/charter/deployable/DoctrineWiringRoundTripTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/antipattern/InlineAntiPatternCheckTest.java
- docs/architecture/descriptors.md
- docs/architecture/descriptor-schemas.md
- docs/guides/author-a-procedure.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP12 - The procedure and styleguide shapes drop anti_patterns (R-r; closes #154)

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`AGENTS.md`, `.kittify/charter/charter.md` (the canonical model: replace the shape outright, no legacy reader),
ADR-0052 decision 16 and its follow-ups ("Contract, breaking on a 0.x minor"), ADR-0022, the mission `spec.md`
(FR-015 and the edge cases), `plan.md` (**D14, read it whole**), `data-model.md` ("Inline `anti_patterns` block") and
ruling R-r in `decisions/` (`DM-01M801H54DXR0447M3F2ZQWFPZ`).

## Objective

Drop `anti_patterns` from the procedure and styleguide shapes in one contract-family change (ADR-0052 d16): the packs
schemas, the domain, the storage forms, the API mapping and the charter contract. A pack file that still carries the
key is refused as an unknown key, on every surface. With WP10 and WP11 this delivers #154, which the PR closes.

## Branch Strategy

- Planning branch and merge target: `feat/pack-doctor-coverage` (PR to `main`). Mission topic branch:
  `kitty/mission-pack-doctor-coverage-01M7YMQK`.
- The workspace comes from `lanes.json`. Implement with
  `spec-kitty agent action implement WP12 --agent claude --mission pack-doctor-coverage-01M7YMQK`.
- Commits: no model names and no Co-Authored-By trailer (repository rule). Never push to `main`.

## Context

- WP10 has landed in your base: no example styleguide or procedure carries `anti_patterns` (`ExamplePackCoverageTest`).
  Check first; if one does, stop.
- **The refusal is the closed schema's.** Both roots are closed (`procedure.schema.json:69`,
  `styleguide.schema.json:121`). Once the property is gone, the one validated read
  (`YamlSerializationStrategy.readValidated`, `:69-91`) answers `artifact_schema_error` at the file, pointer the root,
  detail `<origin>: unknown key 'anti_patterns'` (`Wording.additionalPropertiesDetail`, `Wording.java:263-265`; code
  `Wording.codeFor`, `:183-194`). This is #154's "refused as an unknown field", with a contract code
  (`CharterRefusalCode.yaml:419-421`). Do not mint a code and do not add a rule.
- **Canonical model.** Delete the shapes outright: no deprecated property, no reader that tolerates the old key, no
  `@Deprecated` type. Core aligns later through the generated client.

## Subtasks

### T046 — The refusal, red first, on every surface
- New `contracts/charter/scenarios/registration/inline-anti-patterns.feature` (existing whole-pack steps): a
  styleguide with an `anti_patterns` block, and a procedure with one, are each refused 422 `validation_failed` with
  one violation `artifact_schema_error` at the file and the detail above; control: the same files without the block
  register.
- `InlineAntiPatternCheckTest` (`pack-validation`, validate mode over a copied example pack): the same code, pointer
  and detail (ADR-0075 d9).
- A per-artifact write: `StyleguideControllerTest` sends `antiPatterns` in a `StyleguideRegistration` and gets the
  request-schema refusal (the schema is closed, `StyleguideRegistration.yaml:18`).
- `ProcedureSchemaTest`: `refused-inline-anti-patterns.procedure.yaml` (new) is refused with the unknown key. It
  replaces `refused-anti-pattern-name` and `refused-anti-pattern-description`, whose subject (the inline entry's own
  fields) no longer exists: delete those two fixtures and their cases, and say so in the review notes.
- All red before T047.

### T047 — The packs schemas
- `procedure.schema.json`: drop `anti_patterns` (`:111-117`) and `$defs.AntiPatternEntry` (`:5-32`).
- `styleguide.schema.json`: drop `anti_patterns` (`:159-165`) and `$defs.AntiPattern` (`:5-34`); fix the root
  `description`, which names anti-patterns (`:122`).
- `contracts/packs/CHANGELOG.md` (WP10's; a justified out-of-map edit, one line): a breaking line under the heading
  the file uses for a removed field, naming ADR-0052 d16 and the unknown-key refusal.
- `./gradlew :contracts:check`.

### T047a — The example packs drop their inline blocks (moved from WP10 T042)
- Red first: add `ExamplePackCoverageTest`'s "no styleguide or procedure file of any example pack carries an
  `anti_patterns` key" (saved from WP10 at `research/no-inline-test.java.txt`; red: five files carry one).
- Remove the 18 inline entries from the five built-in owners (`styleguides/boring-code-review`, `aggregate-design-rules`,
  `java-conventions`; `procedures/adversarial-squad-deployment`, `example-mapping-workshop`) in the same commit as T047's
  schema drop, so `everySchemaPropertyHasAnExample` stays green (WP10's patch is at
  `research/inline-removal.patch`; re-derive it if it no longer applies). The procedures are WP10's files and the styleguides sit
  in WP03's glob: justified out-of-map edits, listed in the review notes.
- Restamp `built-in/pack-manifest.yaml` with `ManifestStampHelper` (WP04) once lane-d is merged, else through
  `ManifestMembership.stamp`, and re-record `facts/example-packs.json` only if it changes; explain each change.

### T048 — Domain, storage and API
- Delete `AntiPatternEntry` and `StyleguideAntiPattern`. Remove `antiPatterns` from `Procedure` and `Styleguide`
  (fields, builder methods, invariants, equality), from `ProcedureDso`/`ProcedureYaml`/`ProcedureBinding` and
  `StyleguideDso`/`StyleguideYaml`/`StyleguideBinding` (the key order loses one key), and from `ArtifactDtos` and
  `StyleguideController`.
- Rewrite every test that builds an inline entry to the new shape: `ProcedureEntityTest`, `StyleguideEntityTest`,
  `RegistrationListsTest`, `StyleguideControllerTest`, `DoctrineRepositoryContractTest`, `DoctrineRoundTripTest`,
  `DoctrineValidationParityTest` (its "patterns / anti_patterns non-empty" comment and the `ANTI` constant),
  `ProcedureYamlTest`, `DoctrineWiringRoundTripTest` (deployable). Keep each test's intent; delete only the
  assertions about the removed field.
- Fixtures: remove the `anti_patterns` block from `schema-fixtures/procedure/*` (the seven that carry it only
  incidentally: `accepted`, `refused-integer-schema-version`, `refused-name-instead-of-title`,
  `refused-purpose-instead-of-description`, `refused-step-actor`, `refused-unknown-top-level-key`,
  `refused-uppercase-role`) and from `roundtrip-fixtures/procedure/full.procedure.yaml` (4 entries). Each still
  proves what it proved before.

### T049 — The charter contract
- `Styleguide.yaml`: drop `antiPatterns` from `required` (`:17`) and the property (`:64-71`).
  `StyleguideRegistration.yaml`: drop the property (`:67-74`). `Procedure.yaml`: drop it from `required` (`:8`) and
  the property (`:61-68`). Delete `StyleguideAntiPattern.yaml` and `ProcedureAntiPattern.yaml`.
- Examples: `Artifact.styleguide.yaml:15` (`antiPatterns: []`) and `Artifact.procedure.yaml:24` (the list) lose it.
- Scenarios: `procedures.feature` (`:61` the planted file's block, `:103` the expected body, `:120` the
  `holds exactly 0 items` assertion), `styleguides.feature:52` and `artifacts.feature:129` (the request bodies).
- Change fragment `2026-11-21-inline-anti-patterns-dropped.md` under `### Breaking`: `antiPatterns` leaves
  `Styleguide`, `StyleguideRegistration` and `Procedure`; `StyleguideAntiPattern` and `ProcedureAntiPattern` are
  gone; a pack file or a request with the key is refused; core regenerates its client and drops the two types
  (ADR-0022).
- `./gradlew :deployable:test` (the scenarios and the contract tests), and the generated-model build.

### T050 — Live docs
- `docs/architecture/descriptors.md`: the styleguide key fields (`:265`) and the procedure key fields (`:309`) lose
  `anti_patterns`; the note at `:313-315` says the file kind is built, file-less nodes are refused and the inline
  field is gone (WP10, WP11, this WP).
- `docs/architecture/descriptor-schemas.md:101-103`: the same.
- `docs/guides/author-a-procedure.md:62` and `:117` ("Write anti-patterns inline in `anti_patterns` for now"): an
  anti-pattern is its own file, rejected by a `rejects` edge in the pack's graph.
- `docs/conformance/divergences.yaml:1909-1913` and `divergence-register.md:720-721` (the service's procedure shape
  names the inline entry): WP02's `docs/conformance/**`, a justified out-of-map edit; regenerate the register with its
  tool if there is one.
- `check_docs.py`, markdownlint-cli2 and cspell over the changed files.

## Test commands

```bash
./gradlew :contracts:check :packs:test :pack-validation:test :pack-doctor:test
./gradlew :deployable:test
./gradlew :pack-validation:wholePackRoundTrip
./gradlew spotlessCheck
```

## Definition of Done
- [ ] `procedure.schema.json` and `styleguide.schema.json` carry no `anti_patterns`; the charter contract carries no
      `antiPatterns`; the two inline types are deleted in the contract and the service.
- [ ] A pack file with the key is refused `artifact_schema_error` (unknown key) by registration and
      `pack-doctor validate` alike; a request with it is refused by its schema.
- [ ] Every test, fixture, example and scenario carries the new shape only, except the one refusal fixture.
- [ ] A `### Breaking` fragment and a packs `CHANGELOG` line. Every behavior change was red first (NFR-001).

## Stop conditions
- The unknown-key refusal does not answer `artifact_schema_error` at the file (for example a reader drops unknown keys
  before validation): stop and report. D14 rests on the closed schema answering it.
- A test can only pass by keeping the field optional, by excluding it from a round-trip or parity check, or by
  exempting a fixture: stop and report (charter).
- An example pack still carries an inline block: WP10 is incomplete in your base; stop.

## Risks and reviewer guidance
- A reviewer checks that nothing tolerates the old key (no `@JsonIgnoreProperties`, no lenient reader), that the
  deleted fixtures were replaced by the one refusal fixture, and that each rewritten test keeps its intent.
- Open-packs' 60 files with inline blocks (26 procedures, 34 styleguides) are refused at its repin until curated;
  drafted in `research/open-packs-TBD-anti-pattern-files.md`, not filed.
- `.kittify/charter-packs/` and `.kittify/charter/generated/` (spec-kitty's own doctrine for this repository) carry
  inline blocks; no charter test reads them and they are not example packs. Leave them (plan D14).
