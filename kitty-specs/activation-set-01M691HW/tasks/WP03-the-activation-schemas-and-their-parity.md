---
work_package_id: WP03
title: The activation schemas and their parity
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-006
- FR-011
- NFR-003
- NFR-004
- SC-003
- C-004
- C-005
- C-006
planning_base_branch: issue-125-activation-set
merge_target_branch: issue-125-activation-set
branch_strategy: Planning artifacts for this mission were generated on issue-125-activation-set. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-125-activation-set unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-activation-set-01M691HW
base_commit: 974184ff1da431b8d986539984578a783b78a356
created_at: '2026-10-31T13:41:31.149644+00:00'
subtasks:
- T012
- T013
- T014
- T015
- T016
- T017
- T018
- T019
phase: Phase 2 - Declare
agent: claude
history:
- at: '2026-10-31T13:45:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/offering/
create_intent:
- contracts/offering/schemas/ActivationConfig.schema.json
- contracts/charter/schemas/ActivationConfig.yaml
- contracts/charter/schemas/Activation.yaml
- contracts/charter/schemas/ActivationChange.yaml
- contracts/charter/schemas/ActivationSummary.yaml
- contracts/charter/schemas/ActivationSkippedReport.yaml
- contracts/charter/schemas/Links.yaml
- contracts/charter/schemas/Link.yaml
- contracts/_shared/schemas/RequiringPack.yaml
- contracts/_shared/schemas/RefusalViolation.yaml
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ActivationConfigSchemaParityTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ActivationJson.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ActivationJsonTest.java
- contracts/charter/schemas/SkippedReport.yaml
- contracts/charter/schemas/SkippedDeactivated.yaml
- contracts/charter/schemas/SharedSkip.yaml
- contracts/charter/schemas/ArtifactReference.yaml
- contracts/charter/schemas/PackPin.yaml
- contracts/charter/schemas/SelectedArtifacts.yaml
- contracts/charter/schemas/DeactivatedArtifacts.yaml
- contracts/charter/schemas/ArtifactIdList.yaml
- contracts/charter/schemas/DeniedArtifactIdList.yaml
- contracts/charter/schemas/ArtifactId.yaml
- contracts/charter/schemas/ActivatableKind.yaml
- contracts/charter/schemas/SelectableKind.yaml
- contracts/charter/schemas/CascadeKind.yaml
- contracts/charter/schemas/CascadeScope.yaml
- contracts/charter/schemas/LanguageToken.yaml
- contracts/charter/schemas/SelectionRequest.yaml
- contracts/charter/schemas/ExclusionRequest.yaml
- contracts/charter/schemas/PresetApplication.yaml
execution_mode: code_change
model: ''
owned_files:
- contracts/offering/**
- contracts/charter/schemas/Activation*.yaml
- contracts/charter/schemas/Effective*.yaml
- contracts/charter/schemas/SkippedReport.yaml
- contracts/charter/schemas/SkippedDeactivated.yaml
- contracts/charter/schemas/SharedSkip.yaml
- contracts/charter/schemas/ArtifactReference.yaml
- contracts/charter/schemas/PackPin.yaml
- contracts/charter/schemas/SelectedArtifacts.yaml
- contracts/charter/schemas/DeactivatedArtifacts.yaml
- contracts/charter/schemas/ArtifactIdList.yaml
- contracts/charter/schemas/DeniedArtifactIdList.yaml
- contracts/charter/schemas/ArtifactId.yaml
- contracts/charter/schemas/ActivatableKind.yaml
- contracts/charter/schemas/SelectableKind.yaml
- contracts/charter/schemas/CascadeKind.yaml
- contracts/charter/schemas/CascadeScope.yaml
- contracts/charter/schemas/LanguageToken.yaml
- contracts/charter/schemas/SelectionRequest.yaml
- contracts/charter/schemas/ExclusionRequest.yaml
- contracts/charter/schemas/PresetApplication.yaml
- contracts/charter/schemas/Links.yaml
- contracts/charter/schemas/Link.yaml
- contracts/charter/schemas/_index.yaml
- contracts/_shared/schemas/Problem.yaml
- contracts/_shared/schemas/RequiringPack.yaml
- contracts/_shared/schemas/RefusalViolation.yaml
- contracts/_shared/VENDORED.md
- contracts/src/main/java/ai/speckitty/charter/contracts/closure/**
- contracts/src/test/java/ai/speckitty/charter/contracts/closure/**
- contracts/src/test/java/ai/speckitty/charter/contracts/offering/**
- contracts/src/test/resources/closure/**
- contracts/build.gradle.kts
- build-logic/src/main/kotlin/charter.contract-api.gradle.kts
- service/packs/build.gradle.kts
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ActivationConfigSchemaParityTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ActivationJson.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ActivationJsonTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP03 – The activation schemas and their parity

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave
according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Write every closed schema of `data-model.md`, the canonical JSON Schema, and the three checks
that keep the OpenAPI mirror equal to it (FR-001 to FR-004, FR-006, FR-011, NFR-003, NFR-004,
SC-003). Design authority: `data-model.md` and `research.md` D-1, D-2, D-3, D-8, D-9, D-12,
D-13, D-14 and squad S2, S4, S5, S8, S9, S15, S19, S20, S24, S25, S27, S28.

## Branch Strategy

Planning base and merge target: `issue-125-activation-set`. Lanes from `lanes.json`.

## Subtasks

### T012 — `contracts/offering/schemas/ActivationConfig.schema.json` and the corpus

- JSON Schema 2020-12, `$id` `https://schemas.spec-kitty.ai/charter/offering/ActivationConfig.schema.json`
  (follow `contracts/packs/schemas/*.schema.json` for `$schema`/`$id` style), `$defs` for
  every part, `additionalProperties: false` everywhere, property order as `data-model.md`.
  `schemaVersion`: integer 1..1. Descriptions state the three states, the skill default, the
  `missionTypes` absent/[] rule, canonical form and the revision (D-4), and the members left out
  by ruling.
- Corpus `contracts/offering/corpus/ActivationConfig/{valid,invalid}/NN-<slug>.json`, at least 10
  each, covering D-13 exactly. A `README.md` in `corpus/` says what each case pins.

### T013 — OpenAPI mirror

`contracts/charter/schemas/ActivationConfig.yaml` and its parts as separate files
(`PackPin`, `SelectedArtifacts`, `DeactivatedArtifacts`, `ArtifactIdList`,
`DeniedArtifactIdList`, `ArtifactId`, `ActivatableKind`, `LanguageToken`), whole-file
`$ref`s only, keywords limited to what ClosureChecker and ContractConformance accept (plus
`minItems`, WP02). Every property: `description` and `x-source` (authored members:
`path: contracts/offering/schemas/ActivationConfig.schema.json`, `symbol: "#/properties/<name>"`
or the `$defs` pointer) or `x-derived`. Add each file to `schemas/_index.yaml`.

### T014 — The other schemas

`Activation` (with `governanceFingerprint`, D-14), `ActivationSummary`, `ActivationChange`,
`ArtifactReference`, `SkippedReport`, `SkippedDeactivated`, `SharedSkip`,
`ActivationSkippedReport`, `EffectiveArtifact`, `EffectiveArtifactList` (with `pageInfo` from
`_shared`), `EffectiveGraph`, `EffectiveGraphNode`, `EffectiveGraphEdge`,
`EffectiveActionContext`, `SelectableKind`, `CascadeKind`, `CascadeScope`,
`SelectionRequest`, `ExclusionRequest`, `PresetApplication`, and `Links` (open keys,
`additionalProperties: {$ref: Link.yaml}`) / `Link` (closed: `href` required, `templated`,
`method`, `title`) per `docs/development/hypermedia-hateoas.md`. Server-computed members use
`x-derived` (`rule`, `inputs`). The two `requiredBy` descriptions name their item type and
point at each other (S25). Every success schema requires `_links`.

### T015 — Shared `Problem` members

`_shared/schemas/Problem.yaml` gains optional `requiredBy` (`RequiringPack`) and
`violations` (`RefusalViolation`), both arrays, described as present only on 409
`artifact_required`/`artifact_locked` and 422 `activation_invalid`. `RequiringPack`
(`packId` with an inline slug pattern, `via` array) and `RefusalViolation` (`pointer`,
`code` snake_case pattern, `detail`) live in `_shared/schemas` with inline patterns (no
`charter/` refs, S28). Record under "Changed from the origin" in `_shared/VENDORED.md`.

### T016 — ClosureChecker `_links` exemption

ClosureChecker reports a map as an open position; exempt exactly a property named `_links`
whose schema is the `Links` component (a map of closed `Link`), with a fixture in
`contracts/src/test/resources/closure/` that passes and one that fails (a map elsewhere, or a
`_links` whose values are open). Update its Javadoc. If #126 has landed with the same exemption,
keep one.

### T017 — Structural parity (`contracts/src/test/.../offering/ActivationConfigMirrorTest`)

Resolve both sides into one tree each (file `$ref`s on the mirror, `#/$defs/` on the JSON),
strip exactly `title`, `description`, `examples`, `$comment`, `$schema`, `$id`, `$defs`
(after resolution) and `x-*`, compare for equality. Negative control: a copy of the mirror with
one keyword changed (e.g. `minItems` removed from `DeniedArtifactIdList`) must compare unequal.
If `contracts` lacks a JSON parser, use snakeyaml-engine (it parses JSON) already on its
classpath; add no dependency.

### T018 — Corpus and kind alignment (`service/packs`)

`ActivationConfigSchemaParityTest`: networknt (already a `service/packs` dependency) validates
every corpus document against the JSON file and against the resolved mirror; valid pass, invalid
fail, on both. Kind alignment (D-3): `SelectedArtifacts`/`DeactivatedArtifacts` property names
equal `ArtifactKind`s with `activatable()` minus `MISSION_TYPE` (wire tokens), both directions;
every `activated_<plural>` key of `contracts/packs/schemas/charter.schema.json` except
`activated_kinds` maps through `ArtifactKind.plural()` to one of them. Wire
`contracts/offering` and `contracts/charter/schemas` as test inputs with system properties in
`service/packs/build.gradle.kts` (pattern at ~154-175).

### T019 — Generator null handling

Set `containerDefaultToNull` to `true` in `charter.contract-api.gradle.kts` (comment why: absent
must stay apart from `[]`, D-12). Check every existing generated model still passes
`NullOmissionTest` and the service compiles (required lists are constructor-set). Add
`ActivationJson` (as `ProblemJson`): a `NON_NULL` mix-in for the activation models, registered
the same way `ProblemJson` is. `ActivationJsonTest`: `{"schemaVersion":1}` and
`{"schemaVersion":1,"selected":{"directive":[]}}` deserialize and reserialize unchanged.

## Definition of Done

- `./gradlew :contracts:check` (bundle, lint, closure) and `:packs:test` green;
  `NullOmissionTest` and `GeneratedModelNamesTest` green.
- No schema uses a keyword the tools reject; every property cited and described.

## Risks

- Unreferenced schema files: the bundle only includes schemas reachable from paths. They become
  reachable in WP04; the closure and lint checks over files still run now.
- `containerDefaultToNull` may change existing generated code paths; fix call sites, do not
  suppress.

`spec-kitty agent action implement WP03 --agent claude --mission activation-set-01M691HW`
