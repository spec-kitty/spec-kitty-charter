# Implementation Plan: Activation register and activated-pack closure

**Branch**: `issue-360-activated-closure` (cut from PR #368's branch) | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/activation-register-and-closure-01M6NXBD/spec.md`

## Summary

The charter service gains an **activations** slice. It answers "what should be resolved for this project?", while
**packs** stays the authority on "what is available". An activation is a stored document that references packs by
id. For everything about pack content it delegates to `packs`: the composition and its `depends_on` closure,
artifact existence, graph edges, locks and presets. There is no shadow implementation.

The mission lands as two stacked PRs:

- **PR A** builds:
  - the slice, its storage, and create, read, replace and delete;
  - the closure read `GET /activations/{id}/packs`, which carries each pack's `depends_on` facts (#359, #360);
  - the mutability policy-file reader in `packs`, so `artifact_locked` is real;
  - the required floor, taken from the governing presets in the closure.
- **PR B** builds:
  - select, unselect, deactivate and reactivate, with the cascade (ADR-0059 D2 and the 2026-11-05 rulings);
  - apply-preset.

## Technical Context

**Language/Version**: Java 25 (Temurin 25.0.5), Gradle 9.8.1 wrapper, Kotlin DSL build-logic conventions
**Primary Dependencies**: Spring Boot (web, the existing service stack), the generated `charter-api` interfaces and DTOs
(OpenAPI generator), Jackson with the existing `ActivationJson` NON_NULL mix-ins, networknt JSON Schema for pack files;
no new external library planned (a JCS canonicaliser is written in-house over Jackson's tree model — see research R3)
**Storage**: files under `<config-root>/activations/<activationId>/activation.json` (atomic temp-file + fsync + atomic
move; per-id in-process lock); an in-memory adapter for tests and the scenario suite
**Testing**: Cucumber `@contract` scenarios in `contracts/charter/scenarios/` (red first), JUnit 5 unit tests per
domain class, a property test (≥1,000 generated documents) for the round trip, a concurrency test (8 writers),
ArchUnit (`HexagonalArchitectureTest`), `ReservedOperationCoverageTest`; per-PR gate `./gradlew verifyAll`
**Target Platform**: the charter service (JVM jar and GraalVM native image, loopback daemon per worktree)
**Project Type**: single multi-module Gradle service (`service/*`)
**Performance Goals**: a cascading select over a 500-artifact fixture completes in under 200 ms p95 on CI, reported but
not gating (ADR-0057)
**Constraints**: Checkstyle limits of PR #368 (CC ≤ 15, NPath ≤ 1000, ≤ 60 lines/method, nested-if depth ≤ 1), no
new suppressions, no disabled tests, native-image friendly (no reflection beyond the existing Jackson/Spring hints)
**Scale/Scope**: about 10 contract operations served; one new module; changes to `packs` domain, application and
storage, `kernel`, `deployable` and contracts

## Charter Check

| Charter rule | How the plan meets it |
|---|---|
| Non-trivial work is a mission, with an adversarial squad on contract, architecture and domain changes | This mission; a squad runs after tasks and before each PR (closeout). |
| BDD first (behaviour-driven-development, specification-by-example) | Every served operation gets its `@contract` scenario written and failing before the code (IC-05, IC-08). |
| DDD and hexagonal slices | The new slice has domain, application and infra rings. Ports: `ActivationRepository`, and the read port over packs. ArchUnit is generalized (#140). |
| No compatibility with core's formats, no "core first" | Absent means none, now. Divergences are recorded in the register; core is not touched. |
| C4 incremental detail | The container view is unchanged. The component view of the new slice is in research.md R1. |
| DIRECTIVE_051 supply chain | No new dependency. If one becomes necessary, research.md records the directive's checks before it lands. |
| NO_FULL_HEAVY_SUITES_IN_MISSION | WPs run module-scoped tasks. `verifyAll` runs once per PR at closeout. |

No violations.

## Project Structure

### Documentation (this mission)

```
kitty-specs/activation-register-and-closure-01M6NXBD/
├── spec.md · plan.md · research.md · data-model.md · quickstart.md
├── contracts/          # contract delta sketches (the real change lands in contracts/charter/)
├── decisions/          # discovery and planning decision moments
└── tasks/              # created by /spec-kitty.tasks
```

### Source Code (repository root)

```
service/
├── activations/                         # NEW top-level slice (depends on :packs, :kernel, :charter-api)
│   └── src/main/java/ai/speckitty/charter/activations/
│       ├── domain/        ActivationDocument, ActivationId, Revision, Canonical (JCS), DocumentRules,
│       │                  CascadeRules, SkippedReport, PresetApplication, ActivationRepository (port)
│       ├── application/   ActivationService (CRUD), ActivationSelectionService, ActivationExclusionService,
│       │                  ActivationPresetService, AvailablePacks (port onto packs), config/
│       └── infra/
│           ├── api/       ActivationController (moved from packs.infra.api), ActivationJson, mappers
│           ├── packs/     PacksAvailability (the AvailablePacks adapter over packs' application services)
│           └── storage/   InMemoryActivationRepository, FileActivationRepository, config/
├── packs/                                # extended, never shadowed
│   ├── domain/            PackCompositionResolver.resolveRooted, closure helpers; PackCompositionFacts.edges;
│   │                      MutabilityPolicySource (unchanged) ; PolicyFile model
│   ├── application/       OfferingReadService.composed(roots), AvailabilityQueries (holds / holderOf / lockOf /
│   │                      requiredIn / presetIn / edges), GraphWalk relation filter
│   └── infra/storage/     PolicyYaml reader; descriptor `mutability_policy`; DomainRepositoryService edges
├── kernel/                ArtifactKind.InForceWhenAbsent → NONE; RefusalCode additions if any
└── deployable/            wiring of the activations configs; activation storage root handed from ConfigRoot;
                           HexagonalArchitectureTest generalised; BDD glue (ActivationSteps)
contracts/
├── charter/openapi.yaml, paths/, schemas/, scenarios/activations.feature, scenarios/composition.feature, changes/
└── packs/schemas/policy.schema.json (NEW), pack-descriptor.schema.json (+ mutability_policy)
docs/adr/0061-*.md (policy file), docs/conformance/divergence-register.md, docs/changelog/, docs/architecture/
```

**Structure Decision**: `activations` is a new top-level slice that depends on `packs`, following the planning
decision of 2026-11-05. It reaches pack content only through a narrow read port (`AvailablePacks`). An adapter in
`activations.infra.packs` implements that port over `packs`' public application services, so the domain and the
application ring never import packs. The activation controller moves to `activations.infra.api`, and the generated-type rule allows any
slice's `infra.api` (#140). `packs` gains the small public queries that the port needs. It does not duplicate any of
them.

## Complexity Tracking

| Departure | Why needed | Simpler alternative rejected because |
|---|---|---|
| A new Gradle module | ADR-0020: the built activation set is a slice of its own; operator ruling 2026-11-05 | Placing it inside `packs` keeps growing that module and mixes "available" with "in force" |
| Generalising ArchUnit (#140) | A second slice serves HTTP | Keeping the controller in `packs.infra.api` would make `packs` own activation transport |

## Implementation Concern Map

The concerns are listed in dependency order. Those marked **A** land in PR A, those marked **B** in PR B.

### IC-01 (A) — packs: rooted composition and closure facts

- **Rooted compose.** `PackCompositionResolver` resolves the `depends_on` closure of a given set of root pack ids
  instead of treating every configured pack as a root. Packs outside the closure are not judged, so a sibling
  contest outside it no longer refuses. `OfferingReadService` gains `composed(roots)`; `/offering` keeps calling it
  with every configured pack.
- **Closure facts.** For each pack the rooted compose exposes id, name, version, level and its `depends_on` facts.
- **Requirements:** FR-009, FR-020 (#359, #360).
- **Packs change:** a public `closureOf` (also following `parent_pack`) and `resolveRooted`. Nothing is copied into
  activations.

### IC-02 (A) — packs: mutability policy file and the required floor

- **Policy file.**
  - The descriptor gains `mutability_policy`, and a new closed `policy.schema.json` defines the file
    (`kind_defaults`, `artifacts`).
  - `PolicyYaml` reads it into the existing `MutabilityPolicySource`.
  - `PackMutabilityPolicy` uses the loaded sources.
- **Queries across a compose.**
  - `lockOf(closure, urn)` answers the level and the owning pack.
  - `requiredIn(closure)` answers the must-have items of every governing preset in the closure, each with its pack.
- **ADR-0061** records the format.
- **Requirements:** FR-007, FR-022, FR-023.
- **Risk:** existing whole-pack round trips and the pack-file sets must carry the new file (ADR-0042 manifest rules).

### IC-03 (A) — the activations slice skeleton

- **New module.** `service/activations` with its build file, and registration in `settings.gradle.kts`,
  `charter-reports` and the `sonar.yml` module list.
- **Controller move.** The controller and `ActivationJson` move from `packs.infra.api`.
- **ArchUnit (#140).**
  - `SLICES` gains `activations`.
  - The per-slice rules replace the `PACKS_*` constants.
  - Any slice's `infra.api` may name the generated types.
  - Bad-fixtures are added for the new slice.
- **Deployable wiring.**
  - The activations config packages.
  - The activation storage root, taken from `ConfigRoot.activations()` and handed in as a value, because
    `ConfigRoot` stays in packs.
  - The `AvailablePacks` adapter, in `activations.infra.packs`, over packs' application services.
- **Requirements:** C-002, NFR-001.

### IC-04 (A) — the activation document domain

- **`ActivationDocument`.** It holds three-state lists and builds the canonical form: sorted and deduplicated, with
  declared ids and no empty deny list.
- **The JCS canonicaliser and the `sha256:` revision.**
- **`DocumentRules` validation,** with every check asked through `AvailablePacks` for the activation's closure:
  - unknown id, kind, pack or preset;
  - a duplicate pack;
  - `null` lists or items;
  - a body over 1 MiB;
  - a deny entry on a locked or required artifact.
- **Absent means none.** The kernel's `InForceWhenAbsent` changes to "none".
- **Requirements:** FR-005, FR-006, FR-007, FR-008, FR-017, FR-018.

### IC-05 (A) — storage and the CRUD operations

- **`ActivationRepository` port.**
  - The in-memory adapter.
  - The file adapter, with an atomic move and a per-id lock.
- **`ActivationService`** answers create (`POST`, and `PUT` with `If-None-Match: *`), get, replace (`If-Match`,
  `412`, `428`) and delete.
- **The closure read** `GET /activations/{id}/packs`.
- **Contract A:**
  - the closure-read operation and its `ActivationPackList` schema;
  - `summary` and `governanceFingerprint` removed from `Activation`;
  - the `x-reserved` markers removed from the five operations built here;
  - a contract-changelog fragment.
- **Scenarios first.** The @WIP closure scenario is rewritten against the new read, and `ReservedOperationCoverageTest`
  drops from 13 to 8.
- **Requirements:** FR-001 to FR-004, FR-009, FR-010, FR-019, FR-021, NFR-002 to NFR-005, SC-001 to SC-004
  (PR A part).

### IC-06 (B) — packs: graph edges for the cascade

- **Typed edges.** `PackCompositionFacts` keeps its graph-document edges as `(source, relation, target)` (enhances,
  overrides, specializes_from), filled where `DomainRepositoryService.compositionFacts` reads them today.
- **Pure helpers over a closure:**
  - a forward walk filtered by relation set, built on `GraphWalk` or its domain core and following `<pack>::`
    through `holderOf`;
  - a reverse `enhances` lookup;
  - `specializes_from` ancestors.
- **Every node kind.** Edges are read for every kind that is authored in graphs, not only the five read kinds, so
  profiles and procedures take part.
- **Requirements:** enables FR-012 to FR-015.

### IC-07 (B) — the cascade rules (activations domain)

- **`CascadeRules`** sit on top of the IC-06 walk:
  - which relations count;
  - the kind scope;
  - stop at a deny entry;
  - the `enhances` pull, which is scoped, goes into `selected`, and is walked in turn;
  - the lineage pass-through, which is transitive and stops at a denied ancestor;
  - the skipped-report buckets;
  - `skippedShared` on a cascading deactivate. Its active set is `selected` plus the required floor.
- **The domain stays pure:** the walk is handed in through the port.
- **Requirements:** FR-012 to FR-015.

### IC-08 (B) — the per-artifact and preset operations

- **The writes:**
  - select and unselect;
  - deactivate and reactivate;
  - apply-preset (replace each listed key; `force` clears matching deny entries).
- **Each answers an `ActivationChange`,** with `If-Match`, `412` and `428`.
- **Contract B:**
  - the `enhances` value in the provenance enumeration;
  - the remaining `x-reserved` markers removed from the writes;
  - a changelog fragment.
- **Scenarios first.** `ReservedOperationCoverageTest` drops to 4, the effective reads.
- **Requirements:** FR-011 to FR-016, SC-001 and SC-002 (PR B part).

### IC-09 (A and B) — documentation and divergences

- **Divergence-register entries:**
  - absent means none;
  - the `enhances` pull;
  - the lineage pass-through;
  - deny-on-required and deny-on-locked refused at write;
  - whole-artifact layer override (ADR-0059 D3, recorded only).
- **Architecture docs:** `docs/architecture/offering-and-activation.md` and `hexagonal-service.md` (the new slice).
- **Changelogs:** docs changelog fragments per PR.
- **Review item:** the closure-scoped required floor is logged as an open review item.
- **Requirements:** C-006.
