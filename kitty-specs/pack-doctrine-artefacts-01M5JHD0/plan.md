# Implementation Plan: Pack doctrine artefacts — directive, styleguide, paradigm

**Branch**: `feat/pack-doctrine-artefacts` | **Date**: 2026-10-23 | **Spec**: [spec.md](./spec.md)
**Input**: Mission specification from `kitty-specs/pack-doctrine-artefacts-01M5JHD0/spec.md`

## Summary

Model three more charter artefacts — **directive**, **styleguide**, **paradigm** —
as first-class pack child entities with full YAML round-trip, each mirroring the
established `Tactic` pattern (typed identifier VO → entity with package-private
all-args ctor + fluent chainable builder + `ModularRuleset` invariants → flat DSO
record whose record-equality is the fidelity assertion → `SerializationStrategy`
read/write seam → per-type repository port implemented by the storage adapter).
A thin `CharterArtifact` interface in `packs.domain` unifies the family on its
genuine common spine without collapsing the three distinct concepts; `Tactic` is
retrofitted onto it. Approach, structure, and the one open representation choice
(styleguide nested config) are recorded below and in [research.md](./research.md);
the entity shapes are in [data-model.md](./data-model.md).

## Technical Context

**Language/Version**: Java 25 (temurin), Gradle (wrapper)
**Primary Dependencies**: none new — reuses the existing kernel (`ModularRuleset`, `Constraints`, `ArtifactKind`, `Urn`, `NodeKind`, `DrgEdge`, `Relation`), the packs-slice domain (`Pack`, `PackIdentifier`), and the storage adapter's SnakeYAML-based `YamlSerializationStrategy`/`YamlDumpEmitter`. JSpecify for nullness.
**Storage**: pack files on the filesystem (`FilesystemPackStore`); canonical YAML shapes vendored from spec-kitty-open-packs; validation against the bundled JSON schemas (`additionalProperties:false`).
**Testing**: JUnit 5 + the project's domain/DSO/round-trip test style (`TacticEntityTest`, `PackRoundTripTest`, `DomainRepositoryServiceTest` as templates); ArchUnit `HexagonalArchitectureTest` for layering; `./gradlew verifyAll` is the aggregate gate (CI runs it — not run in full locally per `NO_FULL_HEAVY_SUITES_IN_MISSION`).
**Target Platform**: JVM library/service (the charter service), screaming-architecture packs slice.
**Project Type**: single (the `service/` multi-module Gradle build; the `packs` + `kernel` modules).
**Performance Goals**: N/A — correctness/fidelity mission, no throughput target. Load/serialize is per-pack-file and not on a hot path.
**Constraints**: round-trip fidelity is DSO-record field-equivalence after canonical normalization (NFR-001); validation parity with the canonical schema per ADR-0017 (NFR-002); must not break the hexagonal gates incl. `KERNEL_IS_PURE`, `SLICE_COVERAGE`, `EVERY_SLICE_IS_REGISTERED` (NFR-003); no field invented beyond the canonical schema (C-001).
**Scale/Scope**: 3 new kinds + 1 family interface + 1 Tactic retrofit; ~4 new domain entities, ~4 identifier VOs, ~4 DSO records, 3 repository ports + adapter impls, `SerializationStrategy` seam extension, and the `CharterArtifact` spine. Out of scope: toolguide, procedure, assets, agent profiles, mission_type system (C-004).

## Charter Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

Charter present (`software-dev-default`; paradigms DDD, BDD, spec-by-example, C4-incremental). Relevant directives and this plan's posture:

- **DDD (domain-driven-design)** — ✅ aggregate root (`Pack`) + child entities, typed identifier VOs, identity equality, ubiquitous language from the glossary (directive/styleguide/paradigm keep their distinct concepts; "doctrine" is not used as it is no longer canonical — C-003).
- **DIRECTIVE_041 / QA_003 (tests as scaffold, not friction)** — ✅ each new kind gets its entity-invariant, DSO round-trip, and repository tests, with same-fixture positive controls for every refusal/absence assertion (spec's no-op marks); no shape-pinning or inert tests.
- **DIRECTIVE_051 (supply-chain install safety)** — ✅ trivially satisfied: **no dependency added/changed** (see research.md). Silence is not compliance, so it is recorded explicitly.
- **USE_C4_MODEL_TECHNIQUES** — a component-level view of the packs slice with the new family is included in data-model.md (smallest useful level).
- **RECONCILE_CHANGE_SCOPE_TENSIONS** — scope boundary is explicit (C-004); the styleguide nested-config representation is the one scope tension, reconciled in research.md.
- **No full heavy suites in mission** — ✅ targeted module tests only; `verifyAll` left to CI.

No violations. Complexity Tracking below records the single justified representation choice.

## Project Structure

### Documentation (this mission)

```
kitty-specs/pack-doctrine-artefacts-01M5JHD0/
├── plan.md              # This file
├── research.md          # Phase 0: nested-config representation decision, supply-chain note
├── data-model.md        # Phase 1: entity/VO/DSO shapes, invariants, edges, C4 component view
├── quickstart.md        # Phase 1: how to add/round-trip a directive/styleguide/paradigm
└── tasks.md             # Phase 2 (/spec-kitty.tasks — NOT created here)
```

### Source Code (repository root)

```
service/
├── kernel/src/main/java/ai/speckitty/charter/kernel/
│   ├── domain/            # ArtifactKind, Urn, NodeKind, DrgEdge, Relation — REUSED, not modified
│   └── validation/        # ModularRuleset, Constraints — REUSED
└── packs/src/main/java/ai/speckitty/charter/packs/
    ├── domain/            # NEW: CharterArtifact (interface), Directive, Styleguide, Paradigm,
    │                      #      {Directive,Styleguide,Paradigm}Identifier, Enforcement,
    │                      #      {Directive,Styleguide,Paradigm}Reference + Pattern/AntiPattern,
    │                      #      {Directive,Styleguide,Paradigm}Repository (ports);
    │                      #      Tactic retrofit to implement CharterArtifact
    └── infra/storage/     # NEW: {Directive,Styleguide,Paradigm}Dso, StructuralLintConfig value;
                           #      SerializationStrategy seam + YamlSerializationStrategy extended;
                           #      DomainRepositoryService / FilesystemPackStore adapter impls
service/packs/src/test/java/...   # entity, DSO round-trip, repository, and family tests per kind
```

**Structure Decision**: Everything lands in the existing `packs` slice (`domain` + `infra.storage`), reusing the `kernel` unchanged — consistent with ADR-0006 and keeping `SLICES = {"packs"}`. `CharterArtifact` is a `packs.domain` type (M1: it returns `PackIdentifier`, so it cannot live in the pure kernel).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| A raw nested-value representation for `styleguide.structural_lint_config` (FR-018) beyond the scalar/strings/mappings primitives the Tactic DSO uses | The canonical schema makes it `additionalProperties:true` — an arbitrary tree of mixed-type scalars/lists/maps — and the existing text-coercing reader would drop types and nesting, breaking NFR-001 | Reusing the flat `mappings(List<Map>)` primitive cannot represent nesting or preserve non-string scalars; omitting the field violates C-001 (no silent field loss) |

## Implementation Concern Map

> Concerns are NOT work packages. `/spec-kitty.tasks` turns these into WPs — one concern may split into several WPs, or small ones may merge.

- **IC-01 — Family spine (`CharterArtifact`) + kernel edge reuse.** The `packs.domain` interface (kind/packId/localId/schemaVersion/label/description/edges/urn), returning kernel `DrgEdge`. Foundational: US4 and the per-kind `implements` depend on it. **Prerequisite (M-1):** obtain core/spec-kitty-open-packs' DRG-extractor per-field `Relation` mapping (a *cross-repo* input — no in-repo precedent exists). If core cannot be consulted in-mission, the justified interim mapping in [research.md](./research.md) R2 stands (and is low-risk: no test asserts the relation value). (FR-013, FR-019; M1, m9, M-1.)
- **IC-02 — Directive kind.** `DirectiveIdentifier` (id/version patterns), `Enforcement` typed value with explicit token field (FR-017; token has a hyphen, so not a derived lowercase), `Directive` entity + `ModularRuleset` incl. the `lenient-adherence ⇒ explicit_allowances` conditional (FR-002) and the non-blank `procedures[]` item invariant (R4), `DirectiveDso` + its serialization slice + round-trip, `DirectiveRepository` + adapter, edges (typed refs + bare procedures). Pairs with its IC-06 serialization slice. (US1; FR-001–004, FR-017; M3, m-3.)
- **IC-03 — Styleguide kind.** `StyleguideIdentifier`, `Styleguide` entity + ruleset (scope enum, principles non-empty, pattern cardinality), structured `Pattern`/`AntiPattern` VOs, `StyleguideDso` + serialization slice, `StyleguideRepository` + adapter; `edges()`/`description()` empty. Pairs with its IC-06 slice. (US2; FR-005–008; M3, M4, m6.)
- **IC-04 — Styleguide nested-config fidelity.** The `structural_lint_config` (sealed `ConfigValue` raw-tree, raw-scalar typing) + `tooling` representation and its equality, and a raw-tree emitter that **bypasses the omit-empty/null rule** (present-empty mapping + nested nulls emitted — m-5). Folds into IC-03's round-trip (FR-018). The riskiest surface — its own WP/spike. (FR-018; M2, m-4, m-5, S1.)
- **IC-05 — Paradigm kind.** `ParadigmIdentifier`, `Paradigm` entity + ruleset incl. the non-blank `directive_refs[]` item invariant (R4), `ParadigmReference` VO, `ParadigmDso` + serialization slice, `ParadigmRepository` + adapter, edges (bare directive_refs → `directive`, typed refs). Pairs with its IC-06 slice. (US3; FR-009–012; m-3.)
- **IC-06 — Serialization seam + per-kind store surface.** The bulk of the adapter work, and **not corpus-last — each kind's slice lands with that kind** (m-6), since FR-003/007/011 round-trip can't be shown without it. Per kind: extend `SerializationStrategy` with `read<Kind>`/`write<Kind>` (closed-world comes free — `file-kinds.yaml` already maps `**/*.{directive,styleguide,paradigm}.yaml` and `PackSchemas.bundled()` already loads all three with `additionalProperties:false`, so this is just calling `validate("<kind>", …)`); extend `YamlSerializationStrategy`; and extend the tactic-hardcoded `FilesystemPackStore` (`PackRead` carries only `tactics`; `tacticFiles()/locate…/newTacticFile()/read…/write…` are tactic-specific) with per-kind `locate`/`new-file-name` (`<id>.<kind>.yaml`)/upsert plus the `PackRead` extension, and `DomainRepositoryService.assemble()`. (FR-016; n11.)
- **IC-07 — Pack aggregate + Tactic retrofit.** Extend the `Pack` aggregate to hold `List<Directive>`/`List<Styleguide>`/`List<Paradigm>` with the belongs-to-pack + no-duplicate-id invariants and builder adders (m-1, decided: extend — see data-model.md); make `Tactic implements CharterArtifact` (label→name, description→purpose, edges→typed refs, `when ""`→empty) with its existing behaviour regression-pinned. (US4; FR-015; m-1.)
- **IC-08 — Family + corpus + wiring verification.** Tests asserting all four kinds are `CharterArtifact` with kind-correct spine values; a corpus round-trip across the open-packs fixtures; a `deployable` Spring wiring-round-trip analog of `WiringRoundTripTest` for the new kinds (n-1); confirm `HexagonalArchitectureTest` (incl. `KERNEL_IS_PURE`, `SLICE_COVERAGE`) stays green. (SC-001..004; NFR-003.)

**Sequencing:** IC-01 spine first → IC-02/03(+04)/05 each with its IC-06 serialization slice → IC-07 aggregate + retrofit → IC-08 corpus/wiring/gates last.
