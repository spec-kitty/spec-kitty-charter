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
- **Adversarial review squad at the plan point and before merge** (contract, architecture and domain change):
  - At the plan point, the squad ran on this plan; its findings and dispositions are in `research.md`.
  - Before merge there are two gates. An independent reviewer session reviews each WP (never self-review). After
    WP10, a pre-merge adversarial squad (domain, contract and architecture lenses, profile-loaded) reviews the whole
    diff, and its findings are dispositioned in `research.md`.
  - Then the operator approves. The PR stays a draft until all of this is recorded (standing rule, 2026-11-18).
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

All concerns start after #355 (PR #599) merges (C-001, research R1). The plan-point squad's findings are folded in
below; their dispositions are in `research.md`.

### IC-00 — The routing slice and its governance

- **Purpose**: register the two new modules as a slice that the architecture gates check.
- **Relevant requirements**: NFR-003
- **Affected surfaces**: `settings.gradle.kts`; `service/routing` and `service/routing-edge` build files (as `profiles`,
  `profiles-edge`); `service/deployable` dependencies; `charter-reports` `serviceModules`; `.github/workflows/sonar.yml`
  and `mutation-testing.yml` module lists; `HexagonalArchitectureTest`: `SLICES`, `DOMAIN_RINGS`, a
  `ROUTING_DOMAIN_IS_PURE` rule with its bite control, `ENTRYPOINT_STAYS_IN_ITS_SLICE` and `OUTSIDE_THE_DOMAIN` gaining
  `routing.domain`; an amendment to ADR-0037 (and the ADR-0006 slice list) for `packs.infra.storage` binding
  `routing.domain`.
- **Sequencing/depends-on**: none.
- **Risks**: `SLICE_COVERAGE` fails loudly if a module is left out, which is the intent.

### IC-01 — The routing_catalog kind

- **Purpose**: make the catalog a stored, validated, layered, activatable artifact kind.
- **Relevant requirements**: FR-001, FR-002, FR-005, SC-003
- **Affected surfaces**:
  - **Kernel.** `ArtifactKind.ROUTING_CATALOG` and `NodeKind.ROUTING_CATALOG`, appended last so the contract order and
    the taxonomy indexes of the earlier kinds hold (`offering.feature` pins them). `declarable=false` and
    `edgeTarget=false`. The `kinds/RoutingCatalog` marker joins only the `Kind` permits list, plus `KindTypes` and
    `KindRef`.
  - **Cost tier.** `CostTier` (`low` < `medium` < `high` < `premium`) in the kernel, with its order. It is the one tier
    vocabulary for the catalog, the step (IC-03) and the ranker (IC-04).
  - **Endpoint table.** `EndpointTable`: `ENHANCES` and `OVERRIDES` exclude `ROUTING_CATALOG`, since their diagonals
    otherwise follow `activatable()`. A layer override needs no edge (composition rules: the same id across levels).
    The ADR-0077 decision 5 rows are amended in the same change so `EndpointTableAdrTest` keeps the record and the code
    equal. Check `CascadeRules` (l.419, 465) and `CascadeKind` for the same assumption that an activatable kind is
    reachable by an edge.
  - **Contract and pack schemas.** Every enum of kinds: `ActivatableKind`, `ArtifactKindToken`, `NodeKind`,
    `TaxonomyKind`, `CascadeKind`, `SelectedArtifacts`, `DeactivatedArtifacts`, `Preset.activatedRoutingCatalogs`,
    `ActivationConfig.schema.json` and its corpus, and the `preset`, `policy`, `pack-manifest`, `charter`,
    `graph-shard` and `org-fragment` schemas, plus the lint ruleset.
  - **Pack files and schema.** `file-kinds.yaml`; `contracts/packs/conformance/classifier-cases.yaml` (a
    `**/*.routing-catalog.yaml` case); `routing-catalog.schema.json`.
  - **Storage and domain.** The `routing` domain types; `RoutingCatalogBinding`, `RoutingCatalogDso` and
    `RoutingCatalogYaml` in `KindBindings` and `YamlFormats`.
  - **The FR-002 content rules.** A packs-owned `RoutingCatalogFacts` view in `PackContents`, with the rules in
    `PackRulesets.content()` (ADR-0075), so `pack-check` inherits them. Write them against #599's `PackContents`. A
    catalog has no outward references, so `ReferenceFacts` needs no new subject; state this in the WP.
  - **Example packs and fixtures.**
    - `KindTaxonomy.model.yaml`.
    - Example packs per the research R7 matrix: catalogs, policy entries, presets, a profile `model`, a step
      `model_tier`.
    - Regenerate the manifests and recheck the count-asserting preset, effective and skipped-report scenarios.
    - The whole-pack fixtures.
  - **Records.** `contracts/packs/CHANGELOG.md`.
- **Sequencing/depends-on**: IC-00.
- **Risks**: `PackContents` is the record #599 rewrites, so the facts view lands on its merged shape. The parity tests
  pin the enum order.

### IC-02 — The catalog reads

- **Purpose**: serve the catalog in a pack and in force under an activation.
- **Relevant requirements**: FR-003, FR-004, FR-014
- **Affected surfaces**:
  - **Contract.**
    - `openapi.yaml`: the paths and the tags `RoutingCatalogs` and `EffectiveRoutingCatalogs`.
    - Path files with the full status and header sets of the skill precedent: the pack reads carry `PackRevisionETag`;
      the twins take `IfNoneMatch` and answer `304`, `400`, `401`, `403`, `404` and `500`, with
      `Charter-Source-Fingerprint`, `EffectiveETag` and `Charter-Worktree`.
    - A `RoutingCatalogId` parameter (not the asset/skill one).
    - Schemas `RoutingCatalog`, `RoutingCatalogSummary`, `RoutingCatalogList` and `EffectiveRoutingCatalogRead`, with
      `x-source` on every property.
    - The examples.
    - The activation link relation `routing-catalogs`: the lint ruleset `link-relations-are-known`, the table in
      `docs/development/hypermedia-hateoas.md`, `Activation.yaml` and its examples, and `activation-links.feature`.
  - **Service.**
    - `ArtifactReadService`.
    - `artifacts-edge`: ports, adapters and controllers.
    - `EffectiveReadService`, the `EffectiveTag` read constants and `ActivationMapper`.
    - `ArtifactJson` and `EffectiveJson` mix-ins, with `RuntimeHints` (`NullOmissionTest`).
  - **Scenarios.**
    - New: `routing-catalogs.feature` and `effective-routing-catalogs.feature`, with 304 outlines as
      `effective-assets-and-skills.feature` has them.
    - Rows in `every-operation`, `unexpected-failure` and `read-refusals`.
    - The `?kind=` 400 details in `effective-reads.feature` and `artifacts.feature` (l.433).
    - `switch-gate.yaml`.
- **Sequencing/depends-on**: IC-01.
- **Risks**: the literal path must win over the templated read (`PackArtifactRoutingTest`, `ActivationRoutingTest`).

### IC-03 — The step's model tier

- **Purpose**: let a step offer a model tier.
- **Relevant requirements**: FR-010
- **Affected surfaces**:
  - `mission-type.schema.json`: `$defs.Step.model_tier`, the kernel `CostTier` values.
  - `MissionTypeDso.Step` and the `MissionStep` domain record (`Optional<CostTier>`).
  - `MissionStep.yaml`, as `modelTier` on the shared `CostTier` schema, and `MissionStepSummary.yaml` if it lists
    step fields.
  - `StepView`, `MissionBodies` and `EffectiveReadMapper`, with the `MissionJson` mix-in.
  - The example step of R7, and the round trip.
- **Sequencing/depends-on**: IC-01 (`CostTier`).
- **Risks**: the field is optional, so existing packs stay valid.

### IC-04 — The ranker

- **Purpose**: the pure rules of ADR-0080 decisions 10 to 12, as `data-model.md` states them.
- **Relevant requirements**: FR-007, FR-008, FR-009, NFR-001, NFR-002
- **Affected surfaces**: the `service/routing` domain:
  - the score in `BigDecimal`, read from the YAML text with no `double` step;
  - the rank key per objective;
  - the tiers in play, and `top(inside) ∪ top(fitting)` with all ties;
  - the flags;
  - the order: rank descending, then model id by code point, with the profile candidate last.

  Unit tests cover every row of the R7 matrix, plus the all-zero-weights case.
- **Sequencing/depends-on**: IC-01.
- **Risks**: compare with `compareTo`, never `equals`; the wire form of a score is fixed in `data-model.md`.

### IC-05 — The recommendation read

- **Purpose**: `getModelRecommendation` under an activation.
- **Relevant requirements**: FR-006, FR-011, FR-012, FR-013, FR-014
- **Affected surfaces**:
  - **Contract.**
    - The path file under a new tag, `ModelRecommendations`, which `routing-edge` implements.
    - Parameters with patterns: `taskType` (kebab-case), `agentProfile` (spelled as `ArtifactId` is for
      `getEffectiveAgentProfile`), `missionType`, `step`, and `modelTier` (on the `CostTier` schema).
    - Schemas `ModelRecommendation`, `ModelRecommendationCandidate` and `CostTier`, with `x-source` pointing at
      `service/routing`.
    - Link relations `catalog`, `agent-profile` and `step` in the lint ruleset and the hypermedia table.
    - The examples.
    - `CharterRefusalCode`: three codes, each with an `x-refusal` entry (`raised: true`, naming
      `getModelRecommendation` and its place in the order). Update the schema's prose lists of codes by status.
    - The 404 description states that the 404s and 409s interleave.
  - **Service.**
    - The `routing-edge` use case and controller.
    - `EffectiveReadService.modelRecommendation`: one session over the catalog winners in force, the effective profile
      resolution and the step place.
    - An `EffectiveTag` read, `RECOMMENDATION`.
    - A `RoutingJson` mix-in, with hints.
  - **Scenarios.**
    - `model-recommendation.feature`: one scenario per R7 row and per refusal, each asserting the exact ordered
      candidate list with its flags, never containment. One scenario asserts the 500's place in the order.
    - Rows in `every-operation`, `unexpected-failure` and `errors`.
    - `switch-gate.yaml`.
- **Sequencing/depends-on**: IC-01, IC-02 (the `catalog` link), IC-03, IC-04.
- **Risks**: refusals are judged before the 304. The profile candidate comes from the ADR-0078 resolution, not the
  selection's declared fields.

### IC-06 — Records

- **Purpose**: make the decision visible where clients look, and close out.
- **Relevant requirements**: FR-015, SC-004
- **Affected surfaces**:
  - **Contract changelog.** A fragment under `contracts/charter/changes/`, filed as `### Breaking`. Closed enums gain
    `routing_catalog`, `CharterRefusalCode` gains three codes, and `MissionStep` gains `modelTier`. It names core's
    seam: regenerate the DTOs and handle the new kind and codes.
  - **Service contract.** `docs/api/service-contract.md`: the R14 row, the operation rows and #94's decided-reads
    table.
  - **Divergence register.** DIV-701 to DIV-707 move to `open`, with scenario evidence.
  - **Docs.** The kind row in `docs/architecture/logical-model.md`, and a guide note for pack authors.
- **Sequencing/depends-on**: IC-02, IC-05.
- **Risks**: the register's two forms must stay in step (`DivergenceRegisterTest`).
