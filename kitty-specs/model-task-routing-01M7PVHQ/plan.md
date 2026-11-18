# Implementation Plan: Model-task routing: the catalog kind and the advisory recommendation

**Branch**: `feat/108-model-task-routing` | **Date**: 2026-11-18 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/model-task-routing-01M7PVHQ/spec.md`; design authority
[ADR-0080](../../docs/adr/0080-the-routing-catalog-kind-and-the-model-recommendation.md) (accepted 2026-11-18).

## Summary

Add the `routing_catalog` artifact kind (kernel, pack schema, storage binding, content rules, example packs), serve it
through four literal reads (pack list and item, activation twins), give a mission step an optional `model_tier`, and
add `getModelRecommendation`, an advisory read under an activation that ranks the catalog in force for a task type and
sets the effective profile's model beside it. The kind follows the `skill` precedent; the recommendation follows
`selectAgentProfile` (ETag, 304, source fingerprint, refusals before the tag). No code starts before #355 (PR #599)
merges (C-001); this plan is checked again against that main before implementation.

## Technical Context

**Language/Version**: Java 25 (service), OpenAPI 3.1 contract, JSON Schema 2020-12 pack schemas
**Primary Dependencies**: Spring Boot (as in the service today), Jackson, the generated contract interfaces; no new dependency
**Storage**: pack files on disk through the existing pack store (`KindBinding`); activations in the existing activation store
**Testing**: `./gradlew verifyAll` on Java 25: unit tests, ArchUnit, `@contract` scenarios (`contracts/charter/scenarios/`), the switch-gate register, the whole-pack round trip over charter's example packs (ADR-0070)
**Target Platform**: the charter service (Linux container and native image), as today
**Project Type**: single repository, multi-module Gradle (`service/*`, `contracts`)
**Performance Goals**: none beyond today's reads; a catalog is tens of models, ranking is linear in it
**Constraints**: deterministic answer (NFR-001), exact decimal (NFR-002), no clock dependence, no change to core or open-packs from here (C-003, C-004)
**Scale/Scope**: one new kind, five new operations (four catalog reads, one recommendation), one step field, three refusal codes

Supply chain: no dependency is added, upgraded or removed, so DIRECTIVE_051 has nothing to check.

## Charter Check

- **The charter's model is canonical**: the kind is designed in ADR-0080 on its own merits; core's shape is evidence
  only, and every difference is in the divergence register (DIV-701 to DIV-707). Pass.
- **Tests and examples carry only the canonical shape**: example packs and contract examples use the new shape; an old
  or invalid shape appears only in invalid-content tests (FR-002). No field is excluded from the round trip. Pass.
- **BDD, scenarios first**: each FR gets `@contract` scenarios before its implementation; the switch-gate register
  gets an entry per operation. Pass.
- **Adversarial review squad at the plan point and before merge** (contract, architecture and domain change): the
  plan-point squad runs on this plan; findings and dispositions go in `research.md`. Before merge, an independent
  reviewer session reviews the WPs (never self-review).
- **Gates before merge**: `verifyAll` on Java 25, the style gates, the whole-pack round trip, the docs checks. Pass by
  design; verified at implementation.
- **Issues stay small**: no issue filed from here; the open-packs move is filed by the orchestrator (C-004).

## Project Structure

### Documentation (this mission)

```text
kitty-specs/model-task-routing-01M7PVHQ/
├── spec.md
├── plan.md            # this file
├── research.md        # decisions, the post-#355 recheck, squad findings
├── data-model.md      # the catalog, the recommendation and their rules
├── quickstart.md      # how to exercise the reads on the example packs
├── contracts/         # the operation and schema sketches the WPs turn into contract files
├── decisions/         # Decision Moments (ADR-0080 rulings)
└── checklists/
```

### Source Code (repository root)

```text
service/kernel/…/domain/            ArtifactKind, NodeKind, kinds/RoutingCatalog marker, KindTypes, KindRef
service/routing/                    NEW domain module (as service/profiles): RoutingCatalog value types, the ranker
service/routing-edge/               NEW edge module (as service/profiles-edge): recommendation use case, controllers
service/packs/…/infra/storage/      RoutingCatalogBinding, RoutingCatalogDso, RoutingCatalogYaml, KindBindings, YamlFormats
service/packs/…/application/        ArtifactReadService (SERVED, LITERAL_ITEM_KINDS, refusal map)
service/artifacts-edge/             catalog pack reads and activation twins (as skills)
service/activations/                EffectiveReadService (catalog winners, recommendation inputs), EffectiveTag
service/missions/ , missions-edge/  MissionStep.modelTier
contracts/packs/                    file-kinds.yaml, routing-catalog.schema.json, mission-type step, preset/policy/manifest/graph schemas
contracts/charter/                  paths, schemas, examples, refusal codes, scenarios, switch-gate.yaml, changes/
service/packs/src/testFixtures/resources/example-packs/   catalogs, presets, policy, a step with model_tier, a profile with model
docs/                               service-contract.md R14 row, divergence register (open), logical model, guides
```

**Structure Decision**: two new modules mirror `profiles` (domain, a dependency of `packs` for the binding) and
`profiles-edge` (the use case and controllers). The four catalog reads live in `artifacts-edge` beside the skill reads.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Two new modules | the ranker is domain logic with no Spring, and the read has its own use case | putting the ranker in `packs` or `activations` mixes routing rules into storage or activation code; ArchUnit keeps slices apart |

## Implementation Concern Map

### IC-01 — The routing_catalog kind

- **Purpose**: make the catalog a stored, validated, layered, activatable artifact kind.
- **Relevant requirements**: FR-001, FR-002, FR-005, SC-003
- **Affected surfaces**: kernel kinds (`ArtifactKind`, `NodeKind` with `declarable=false`, `edgeTarget=false`, the
  `kinds/RoutingCatalog` marker and the sealed `permits` lists it belongs to, `KindTypes`, `KindRef`); every contract and
  pack schema enum of kinds (`ActivatableKind`, `ArtifactKindToken`, `NodeKind`, `TaxonomyKind`, `CascadeKind`,
  `SelectedArtifacts`, `DeactivatedArtifacts`, `Preset.activatedRoutingCatalogs`, `ActivationConfig.schema.json`,
  `preset`, `policy`, `pack-manifest`, `charter`, `graph-shard`, `org-fragment`, lint ruleset); `file-kinds.yaml`;
  `routing-catalog.schema.json`; the `routing` domain module; the storage binding; the content rules of FR-002 as a
  pack ruleset; `KindTaxonomy.model.yaml`; example packs (built-in catalog, org-base catalog of the same id and a
  second id, policy entries, presets); whole-pack fixtures.
- **Sequencing/depends-on**: none (first, after #355).
- **Risks**: #355 reshapes the rulesets, `PackFileKind` and the kind table; recheck the surfaces on the merged main
  (research R1). The parity tests that pin enum order fail loudly; keep `ArtifactKind`'s declaration order the
  contract's order.

### IC-02 — The catalog reads

- **Purpose**: serve the catalog in a pack and in force under an activation.
- **Relevant requirements**: FR-003, FR-004, FR-014
- **Affected surfaces**: `openapi.yaml` paths and tags `RoutingCatalogs`, `EffectiveRoutingCatalogs`; path files for
  the four operations; schemas `RoutingCatalog`, `RoutingCatalogSummary`, `RoutingCatalogList`,
  `EffectiveRoutingCatalogRead` and their parts; examples; `ArtifactReadService`; `artifacts-edge` ports, adapters,
  controllers; `EffectiveReadService` and `EffectiveTag` read constants; `ActivationMapper` link; scenarios
  `routing-catalogs.feature`, `effective-routing-catalogs.feature` and the rows in `every-operation`,
  `unexpected-failure`, `read-refusals`, `effective-reads` (the `?kind=` 400 detail); `switch-gate.yaml`.
- **Sequencing/depends-on**: IC-01.
- **Risks**: the literal path must win over the templated read (`PackArtifactRoutingTest`, `ActivationRoutingTest`).

### IC-03 — The step's model tier

- **Purpose**: let a step offer a model tier.
- **Relevant requirements**: FR-010
- **Affected surfaces**: `mission-type.schema.json` `$defs.Step.model_tier`; `MissionTypeDso.Step`; `MissionStep`
  domain; `MissionStep.yaml` and `MissionStepSummary.yaml` if it lists step fields; `StepView`, `MissionBodies`,
  `EffectiveReadMapper`; an example step with a tier; round trip.
- **Sequencing/depends-on**: none; independent of IC-01.
- **Risks**: open-packs steps gain an optional field only, so existing packs stay valid.

### IC-04 — The ranker

- **Purpose**: the pure rules of ADR-0080 decisions 10 to 12.
- **Relevant requirements**: FR-007, FR-008, FR-009, NFR-001, NFR-002
- **Affected surfaces**: `service/routing` domain: score in `BigDecimal`, the rank key per objective, the tiers in
  play, the two top-ranked sets with all ties, the flags, the presentation order (rank, then model id).
- **Sequencing/depends-on**: IC-01 (the catalog value types).
- **Risks**: decimal scale and equality: compare with `compareTo`, never `equals`; the desirability constants are
  exact decimals (0.66, 0.33, 0.5).

### IC-05 — The recommendation read

- **Purpose**: `getModelRecommendation` under an activation.
- **Relevant requirements**: FR-006, FR-011, FR-012, FR-013, FR-014
- **Affected surfaces**: path file, `ModelRecommendation` and `ModelRecommendationCandidate` schemas, examples;
  `CharterRefusalCode` (`routing_catalog_not_in_force` 409, `routing_catalog_conflict` 409, `task_type_not_found`
  404) and their refusal examples; `routing-edge` use case and controller; `EffectiveReadService.modelRecommendation`
  (one session: catalog winners in force, effective profile resolution, step place); `EffectiveTag` read
  `RECOMMENDATION` over the normalized query; scenarios `model-recommendation.feature` with one scenario per rule
  and per refusal; rows in `every-operation`, `unexpected-failure`, `errors`; `switch-gate.yaml`.
- **Sequencing/depends-on**: IC-01, IC-03, IC-04 (IC-02 for the `catalog` link).
- **Risks**: the refusal order must hold before the 304; the effective profile is the ADR-0078 resolution
  (`PacksSession.profileResolution`), not the selection's declared fields.

### IC-06 — Records

- **Purpose**: make the decision visible where clients look, and close out.
- **Relevant requirements**: FR-015, SC-004
- **Affected surfaces**: `contracts/charter/changes/2026-11-…-routing-catalog-and-model-recommendation.md`;
  `docs/api/service-contract.md` (R14 row, operation rows, the decided-reads table of #94); the divergence register
  (DIV-701 to DIV-707 to `open`, with scenario evidence); `docs/architecture/logical-model.md` kind row; a guide note
  for pack authors.
- **Sequencing/depends-on**: IC-02, IC-05.
- **Risks**: none beyond keeping the register's two forms in step (`DivergenceRegisterTest`).
