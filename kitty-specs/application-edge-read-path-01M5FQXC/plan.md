# Implementation Plan: Application-edge read path on the Pack model

**Branch**: `feat/pack-repositories-validation` | **Date**: 2026-10-21 | **Spec**: [spec.md](./spec.md)
**Input**: Mission specification from `kitty-specs/application-edge-read-path-01M5FQXC/spec.md`

## Summary

Build the charter service's first real functional read path — retrieving a pack's **tactics** by identity — on the single canonical `Pack` aggregate and its repository ports, served by an application-layer service through a thin API projection. Reclassify the `application` layer from a pure inner ring to an outer **edge** adapter (peer to persistence), give every layer its own code-based `@Configuration`, and rename `bootstrap` → `deployable` to hold the `@SpringBootApplication` and an aggregate `DeployableConfiguration` that `@Import`s each layer config (enabling modular `@SpringBootTest` slices). Delete the first-pass `CharterPack*` scaffolding and the pack-listing / action-context endpoints outright (one model, no coexistence), revise ADR-0006 and the ArchUnit gate to match, and fold the second-opinion squad's findings (single violations owner, upsert save contract, injected serialization strategy, point-read efficiency).

## Technical Context

**Language/Version**: Java 25 (Temurin)
**Primary Dependencies**: Spring Boot (Web MVC) at the `deployable` + `infra.api` edge; SnakeYAML + networknt json-schema at `infra.storage`; jspecify (null-marking) in all hand-written code; Gradle (Kotlin DSL) with `build-logic` convention plugins (`charter.domain-conventions`, `charter.hand-written`)
**Storage**: Filesystem/YAML pack store, entirely behind the persistence edge (`infra.storage`); no database
**Testing**: JUnit 5 + AssertJ; ArchUnit architecture battery (ADR-0006 gate) with biting known-bad controls; modular `@SpringBootTest` wired slices via the deployable aggregate; PIT on `kernel`. Targeted Gradle verification only in-mission (NO_FULL_HEAVY_SUITES_IN_MISSION) — CI owns full runs
**Target Platform**: Linux server, loopback-bound HTTP service
**Project Type**: single (multi-module Gradle service: `kernel`, `packs`, `deployable`)
**Performance Goals**: a single-pack tactic read touches only that pack's files; resolving a pack identity does not parse every tactic of every pack; p95 < 300 ms for a pack of ≤ 50 tactics on a local store (NFR-001)
**Constraints**: domain + kernel rings framework-free, double-gated (build-time dependency rejection + ArchUnit); no HTTP write surface this mission (C-006); fluent-builder construction style (C-005); one canonical model, no coexistence (C-001); tactics-only read scope (C-002)
**Scale/Scope**: one service; ~3 slice modules; the tactic read path end-to-end; removal of two endpoint families and the scaffolding model

**Supply-chain security**: No new third-party dependency is introduced — the mission reuses Spring Boot, SnakeYAML, networknt, jspecify, ArchUnit already present. `DIRECTIVE_051` / `supply-chain-install-safety` therefore has no new install to vet; recorded as "no dependency delta" in research.md. If planning surfaces a new dependency, run the install-safety controls before implementation.

## Charter Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **DIRECTIVE_001 (Architectural Integrity)**: PASS — the mission sharpens separation of concerns (domain pure; application/infra as explicit peer edges; one aggregate root; thin controllers). The reclassification is enforced by the revised gate, not just prose.
- **DIRECTIVE_003 (Decision Documentation)**: PASS — the application-as-edge reclassification is a material decision captured as an ADR-0006 revision (FR-012) plus the mission decision records.
- **DIRECTIVE_010 (Specification Fidelity)**: PASS — scope is pinned by resolved Decision Moments (read-only, tactics-only, delete-scaffolding, upsert save, APPLICATION_IS_EDGE, bootstrap→deployable).
- **DIRECTIVE_025 (Boy Scout)**: PASS — folding the squad's port-hardening findings (single violations owner, injected strategy, point-read) is in-domain debt folded deliberately, not silently absorbed.
- **DIRECTIVE_041 (Test economy)**: PASS — NFR-004 + closeout vetting; every new test guards real behaviour through the production path; contract test covers both save arms, not shape.
- **USE_C4_MODEL_TECHNIQUES / c4-incremental-detail-modeling**: PASS — the C4 component + class diagrams are updated to the edge layering (FR-012).
- **RECONCILE_CHANGE_SCOPE_TENSIONS**: PASS — the delete-outright vs tactics-only tension was reconciled with the operator (retire the unmodeled endpoints; follow-up mission models them).
- **NO_FULL_HEAVY_SUITES_IN_MISSION**: PASS — C-007.

No violations requiring Complexity Tracking.

## Project Structure

### Documentation (this mission)

```
kitty-specs/application-edge-read-path-01M5FQXC/
├── plan.md              # This file
├── research.md          # Phase 0 output (squad findings disposition, decisions, no-dependency-delta)
├── data-model.md        # Phase 1 output (Pack/Tactic/ports/DSO, upsert save contract)
├── quickstart.md        # Phase 1 output (how to run the tactic read path + a modular wired test)
├── contracts/           # Phase 1 output (OpenAPI delta: +tactics endpoints, −listing/action-context)
└── tasks.md             # Phase 2 output (/spec-kitty.tasks — NOT created here)
```

### Source Code (repository root)

```
service/
├── kernel/                         # PURE (no framework) — charter.domain-conventions
│   └── src/main/java/ai/speckitty/charter/kernel/
│       ├── domain/                 # shared Pack/DRG vocabulary, RefusalCode
│       └── validation/             # Constraint / ModularRuleset (guard/check)
├── packs/
│   └── src/main/java/ai/speckitty/charter/packs/
│       ├── domain/                 # PURE — Pack aggregate root, Tactic child entity, identifier VOs, repository ports
│       ├── application/            # EDGE (reclassified) — @Service read concretizations (tactic retrieval use case)
│       │   └── config/             # ApplicationConfiguration (@Configuration, scans application)
│       └── infra/
│           ├── storage/            # EDGE — DomainRepositoryService (@Service, scan-driven), FilesystemPackStore, SerializationStrategy bean
│           │   └── config/         # StorageConfiguration (@Configuration + @ComponentScan)
│           └── api/                # EDGE — thin controllers (tactic endpoints), security annotations
│               └── config/         # ApiConfiguration (@Configuration + @ComponentScan)
└── deployable/                     # renamed from bootstrap — @SpringBootApplication + DeployableConfiguration (@Import all layer configs)

contracts/charter/                  # canonical OpenAPI (repo-level) — tactics endpoints added, listing/action-context removed
docs/adr/0006-*                     # revised Proposed→Accepted edit: application is an edge ring
docs/architecture/                  # hexagonal-service.md, architectural-patterns-applied.md, C4 + class diagrams regenerated
```

**Structure Decision**: Keep the three-module layout (`kernel`, `packs`, `deployable`); the slice internals (`domain`/`application`/`infra.{storage,api}`) stay packages within `packs`. Each edge layer gains a small `config/` sub-package for its `@Configuration`; `deployable` aggregates them. The `config/` placement keeps the framework-bearing composition out of the layer's behavioural classes and gives the gate a clean target.

## Complexity Tracking

*No Charter Check violations — section intentionally empty.*

## Implementation Concern Map

> Concerns are not work packages. `/spec-kitty.tasks` translates these into WPs.

### IC-01 — Application-as-edge doctrine: gate + ADR + docs

- **Purpose**: Reclassify the application layer as an outer edge ring and make the architecture gate and docs say so, so the boundary is enforced and the reference is self-consistent.
- **Relevant requirements**: FR-011, FR-012, NFR-003, C-003
- **Affected surfaces**: `service/bootstrap/src/test/.../HexagonalArchitectureTest.java` (→ deployable test), ADR-0006, `docs/architecture/hexagonal-service.md`, `architectural-patterns-applied.md`, C4 + class diagrams (+ regenerated SVGs)
- **Sequencing/depends-on**: none (foundational; the new application-edge code must land under the revised rule)
- **Risks**: the known-bad control must still bite after the rule flips polarity; purity rules for domain/kernel must stay untouched; diagram SVG regeneration must match PlantUML sources (CI gate).

### IC-02 — Per-layer composition configs + deployable rename

- **Purpose**: Give each layer its own `@Configuration`, rename `bootstrap`→`deployable` with an aggregate `DeployableConfiguration`, and make `DomainRepositoryService` scan-driven (drop the explicit `@Bean`), so wiring has one signal per layer and modular `@SpringBootTest` is cheap.
- **Relevant requirements**: FR-006, FR-007, FR-008
- **Affected surfaces**: `service/bootstrap/` → `service/deployable/`, new `*/config/*Configuration.java`, `settings.gradle.kts`, module build files, redistribution of `CharterWiring`/`GuardWiring`/`PackPersistenceWiring`
- **Sequencing/depends-on**: IC-01 (edge rule must exist before application config lands)
- **Risks**: single-bean invariant (no duplicate repository-port bean from a widened scan); gate rule `ONION_ONLY_BOOTSTRAP_COMPOSES_ADAPTERS` + NullMarked references retarget to `deployable`.

### IC-03 — Tactic read service (application edge)

- **Purpose**: An application-layer `@Service` that retrieves a pack's tactics (all, and one by id) over the `Pack` aggregate + repository ports, returning domain results and a clean refusal for an absent pack/tactic.
- **Relevant requirements**: FR-001, FR-002, FR-003, NFR-001
- **Affected surfaces**: `service/packs/src/main/java/.../application/` (new read service + its result/refusal shapes)
- **Sequencing/depends-on**: IC-02 (needs the application config + scan)
- **Risks**: point-read efficiency (NFR-001) — resolve identity via a descriptor-only read, do not fan out across all packs/tactics.

### IC-04 — Inbound API + OpenAPI contract change

- **Purpose**: Thin controllers exposing `GET /packs/{packId}/tactics` and `GET /packs/{packId}/tactics/{tacticId}`; update the canonical OpenAPI contract to add these and remove the listing/action-context operations; regenerate the API interfaces.
- **Relevant requirements**: FR-001, FR-002, FR-005, C-004
- **Affected surfaces**: `contracts/charter/**` (OpenAPI), generated `ai.speckitty.charter.api.*`, `service/packs/.../infra/api/` (new tactic controller; remove `PacksController`/`ContextController`), `meta.json` contracts field
- **Sequencing/depends-on**: IC-03
- **Risks**: contract regeneration must stay consistent with the ArchUnit `TRANSPORT_STAYS_IN_ADAPTER` / `GENERATED_API_NOT_IN_DOMAIN` rules; removed operations must disappear from the contract, not just the controller.

### IC-05 — Delete the first-pass scaffolding

- **Purpose**: Remove `CharterPack`, `CharterOffering`, `CharterPackRepository`, `FileSystemCharterPackRepository`, `ConsistentLoad`, `PackListingQuery`, `ActionContextQuery` and their tests — one model, no coexistence.
- **Relevant requirements**: FR-004, FR-005, C-001, US2
- **Affected surfaces**: `service/packs/.../domain/CharterPack*.java`, `CharterOffering.java`, `.../infra/storage/FileSystemCharterPackRepository.java`, `.../application/{ConsistentLoad,PackListingQuery,ActionContextQuery}.java`, their tests, and any ArchUnit control fixtures referencing them
- **Sequencing/depends-on**: IC-04 (the tactic read path replaces what these served before deletion)
- **Risks**: deletion must not strand references; `InfoController`/`ServiceInfoQuery` must keep working; `PackPolicy` and any shared domain types the scaffolding touched must survive.

### IC-06 — Repository-port hardening (squad findings)

- **Purpose**: Fold the second-opinion squad's durable-contract findings: single owner for pack violations (collapse `PackInspectionQuery.partialReads` into the aggregate), upsert `save` contract with an atomic create arm + both-arm contract test, inject `SerializationStrategy` as a bean, and gate the storage parameter half.
- **Relevant requirements**: FR-009, FR-010, NFR-001, C-005
- **Affected surfaces**: `service/packs/.../domain/{Pack,PackInspectionQuery,TacticRepository}.java`, `.../infra/storage/{DomainRepositoryService,FilesystemPackStore,SerializationStrategy}.java`, the ADR-0006 gate `STORAGE_RETURNS_DOMAIN` (parameter half)
- **Sequencing/depends-on**: none (can proceed alongside IC-03)
- **Risks**: upsert create arm must be atomic + pack-root-contained like the replace arm; collapsing the violations seam must not change what callers observe.

### IC-07 — Modular wired tests + closeout test vetting

- **Purpose**: Demonstrate a modular `@SpringBootTest` slice through the deployable aggregate and keep the suite honest (DIRECTIVE_041).
- **Relevant requirements**: FR-007, NFR-004, SC-003
- **Affected surfaces**: `service/deployable/src/test/`, `service/packs/src/test/`
- **Sequencing/depends-on**: IC-02, IC-03
- **Risks**: test economy — a wired slice test must exercise real beans and a real regression, not duplicate the gate or pin shape.
