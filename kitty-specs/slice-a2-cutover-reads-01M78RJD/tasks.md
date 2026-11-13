# Tasks: Slice A2, preset, asset and skill reads

**Mission**: `slice-a2-cutover-reads-01M78RJD`
**Branch**: `kitty/mission-slice-a2-cutover-reads-01M78RJD` (mission), landing on `slice-a2-536-cutover-reads`, then `main` by PR
**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Research**: [research.md](research.md) · **Squad**: [research/squad-post-plan.md](research/squad-post-plan.md)

Gradle runs one build at a time in this session (`--max-workers=1`, in the foreground), so work packages run in sequence even where their files would allow parallel lanes. Each work package lands green on its own: its commits pass the module tests it touches and `:deployable:test`.

**Shared registries.** Every operation needs entries in these files:

- `contracts/charter/openapi.yaml`
- `contracts/charter/scenarios/switch-gate.yaml`
- `every-operation.feature`
- `unexpected-failure.feature`
- `read-refusals.feature`
- `docs/development/hypermedia-hateoas.md`
- `contracts/lint/ruleset.yaml` (the relation enum)

WP01 owns these files. WP02 to WP08 add their own rows to them as small recorded out-of-map edits ("rows for the operations this WP adds"), so no other WP declares them. Each WP writes its own change fragment file.

## Subtask Index

| ID | Description | WP | Parallel |
|---|---|---|---|
| T001 | Contract: preset paths, `Preset`/`PresetItem`/`PresetList` schemas, examples, `preset_not_found` text, `Presets` tag | WP01 | |
| T002 | `PresetReadService` in `:packs` on `ConsistentRead`, with the existing pack check | WP01 | |
| T003 | `PresetsController` and the pack's `presets` link | WP01 | |
| T004 | `pack-presets.feature`, plus switch-gate, every-operation, unexpected-failure and read-refusals rows | WP01 | |
| T005 | Relation table `presets` row, the lint enum, and the change fragment | WP01 | |
| T006 | `ArtifactReadService`: `LITERAL_ITEM_KINDS`, `ITEM_KINDS` derivation, `SERVED`/`LISTED`, `ABSENT` | WP02 | |
| T007 | `ArtifactRemovalService` `REMOVABLE` set and its DELETE pin scenario | WP02 | |
| T008 | Activation templated item read gates on activatable ∩ listed | WP06 | |
| T009 | Contract kind lists: `ArtifactKindToken`, `parameters/ArtifactKind`, `Artifact.yaml` members; `Asset`/`Skill` schemas | WP02 | |
| T010 | Asset id pattern in `asset.schema.json` and `AssetIdentifier`; planted fixture; registration refusal | WP02 | |
| T011 | Update the pinned-set tests and the `artifacts.feature` text; `?kind=asset`/`?kind=skill` list scenarios | WP02 | |
| T012 | `service/artifacts-edge` module: build file, `settings.gradle.kts`, `:deployable` dependency | WP03 | |
| T013 | Rings: `application`, `infra.api`, `infra.packs`, `infra.activations`, with package-info and config | WP03 | |
| T014 | `HexagonalArchitectureTest`: the artifacts-edge rules and known-bad fixtures | WP03 | |
| T015 | Mutation floor group and a smoke wiring test | WP03 | |
| T016 | Contract: asset list, item and payload paths, `AssetSummary`/`AssetList`, payload headers | WP04 | |
| T017 | `ForeignArtifactRead.held(pack, kind)` and `payload(pack, assetId)` | WP04 | |
| T018 | One domain media-type rule extracted from `PackPolicy.mimeProblem`; validate findings unchanged | WP04 | |
| T019 | Edge body mapper for an asset; `AssetsController` list and item | WP04 | |
| T020 | Payload read modelled on `getPackDocument`/`PackDocumentETag`: ETag, 304, headers, Content-Type | WP04 | |
| T021 | `assets.feature` and registry rows | WP04 | |
| T022 | Contract: skill list and item paths, `SkillSummary`/`SkillList` | WP05 | |
| T023 | Edge body mapper for a skill (R8 fields, body) | WP05 | |
| T024 | `SkillsController` list and item, riding `ForeignArtifactRead` | WP05 | |
| T025 | `skills.feature` and registry rows | WP05 | |
| T026 | Contract: the five effective asset and skill paths, `EffectiveAssetRead`, `EffectiveSkillRead` | WP06 | |
| T027 | Packs-side held winners of a non-activatable kind; activations asset winner; `EffectiveTag` values | WP06 | |
| T028 | Verified effective payload (fingerprint check, `stale_input`) | WP06 | |
| T029 | `EffectiveAssetsController` and `EffectiveSkillsController` in the edge | WP06 | |
| T030 | `effective-assets-and-skills.feature`, the stale unit test and registry rows | WP06 | |
| T031 | Contract: the references twin path and its tag | WP07 | |
| T032 | Winner plus fingerprint from activations; the pack references read verified against it | WP07 | |
| T033 | `EffectiveReferencesController` with `effective-artifact` links | WP07 | |
| T034 | `effective-references.feature` with the parity scenario, and registry rows | WP07 | |
| T035 | `ActivationMapper` links for #409 | WP08 | |
| T036 | `Activation.yaml` `_links` description and examples | WP08 | |
| T037 | Relation table rows and the lint enum for the activation relations | WP08 | |
| T038 | `activation-links.feature`: root to select, by links only | WP08 | |
| T039 | `docs/api/service-contract.md` rows for the 13 operations and the links | WP09 | |
| T040 | ADR-0074 amendment (decision 9, assets under an activation) and built status; ADR-0019 annotation | WP09 | |
| T041 | Routing pins: literal versus templated, both trees | WP09 | |
| T042 | Final sweep: fragments, `verifyAll`, docs checks | WP09 | |

## Phase 1: Foundation

### WP01: Presets in a pack (#98, P1)

- **Prompt**: [tasks/WP01-presets-in-a-pack.md](tasks/WP01-presets-in-a-pack.md)
- **Goal**: `listPackPresets` and `getPackPreset`. **Priority**: P1.
- **Independent test**: `pack-presets.feature`.
- **Subtasks**:
  - T001 Contract for the preset reads (WP01)
  - T002 PresetReadService (WP01)
  - T003 PresetsController and the presets link (WP01)
  - T004 Scenarios and registry rows (WP01)
  - T005 Relation table, lint enum, fragment (WP01)
- **Dependencies**: none.
- **Risks**: keep the shared registries in enum and table order; slice C edits them too.

### WP02: Kind gates and the asset id pattern

- **Prompt**: [tasks/WP02-kind-gates-and-asset-id.md](tasks/WP02-kind-gates-and-asset-id.md)
- **Goal**: asset and skill become served and listed kinds without opening DELETE or the templated item read. Asset ids are pinned.
- **Subtasks**:
  - T006 Kind sets (WP02)
  - T007 REMOVABLE (WP02)
  - T009 Contract kind lists and the Asset/Skill schemas (WP02)
  - T010 Asset id pattern (WP02)
  - T011 Pinned tests and list scenarios (WP02)
- **Dependencies**: WP01 (it owns the shared registries).
- **Risks**: `ITEM_KINDS` must not pick up asset or skill, or the item read answers 500.

### WP03: The `:artifacts-edge` module

- **Prompt**: [tasks/WP03-artifacts-edge-module.md](tasks/WP03-artifacts-edge-module.md)
- **Goal**: an edge module that can name packs and activations through its adapters, gated by ArchUnit.
- **Subtasks**:
  - T012 Module (WP03)
  - T013 Rings (WP03)
  - T014 ArchUnit (WP03)
  - T015 Mutation floor and smoke test (WP03)
- **Dependencies**: none.
- **Risks**: an empty ring is not checked; the smoke test must prove each ring holds a class.

## Phase 2: Pack-tree reads

### WP04: Asset reads in a pack (#105)

- **Prompt**: [tasks/WP04-asset-reads-in-a-pack.md](tasks/WP04-asset-reads-in-a-pack.md)
- **Subtasks**: T016 to T021.
- **Dependencies**: WP02, WP03.
- **Risks**: Spring's implicit 304; Content-Type negotiation against `Accept`; byte-exact payload.

### WP05: Skill reads in a pack (#107)

- **Prompt**: [tasks/WP05-skill-reads-in-a-pack.md](tasks/WP05-skill-reads-in-a-pack.md)
- **Subtasks**: T022 to T025.
- **Dependencies**: WP02, WP03, WP04 (it shares the edge body mapper).

## Phase 3: Activation-tree reads

### WP06: Activation twins of the asset and skill reads

- **Prompt**: [tasks/WP06-effective-asset-and-skill-reads.md](tasks/WP06-effective-asset-and-skill-reads.md)
- **Subtasks**: T008 (the activation templated gate, moved from WP02 because WP06 owns `EffectiveReadService`), T026 to T030.
- **Dependencies**: WP04, WP05.

### WP07: The references twin (#532)

- **Prompt**: [tasks/WP07-references-twin.md](tasks/WP07-references-twin.md)
- **Subtasks**: T031 to T034.
- **Dependencies**: WP03, WP06 (it shares the edge's activations adapter).

### WP08: Activation links (#409)

- **Prompt**: [tasks/WP08-activation-links.md](tasks/WP08-activation-links.md)
- **Subtasks**: T035 to T038.
- **Dependencies**: WP01 (the relation table).

## Phase 4: Record

### WP09: Docs, ADRs, routing pins and the final sweep

- **Prompt**: [tasks/WP09-docs-adrs-and-sweep.md](tasks/WP09-docs-adrs-and-sweep.md)
- **Subtasks**: T039 to T042.
- **Dependencies**: WP01 to WP08.

## MVP

WP01 (presets, P1) is shippable on its own.
