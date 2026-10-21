# Implementation Plan: Pack repositories and validation module

**Branch**: `feat/pack-repositories-validation` | **Date**: 2026-10-21 | **Spec**: `kitty-specs/pack-repositories-validation-01M5EET9/spec.md`
**Input**: Mission specification from `kitty-specs/pack-repositories-validation-01M5EET9/spec.md`

## Summary

Rebuild the closed PR #138 as the **cornerstone** DDD + hexagonal reference. The pack descriptor + tactic round-trip is re-expressed as a DDD aggregate (pack root / tactic child entity) with identifier value objects, storage-transparent per-type repositories whose ports live in the domain and whose `@Service` implementation lives at a persistence edge (DIP), a pluggable `SerializationStrategy` over a value-equal DSO carrying the round-trip fidelity, and guards expressed through a new pure `validation` module. Per the operator's resolved Decision Moment (DM `01M5F3GQ…`), the service is **migrated now onto ADR-0006's vertical-concern slice layout** (`kernel` + `packs` slices, `ai.speckitty.charter.<concern>.<layer>` packages, a `bootstrap` composition root), landed as **one PR with phased, behavior-preserving commits** (skeleton → migration → validation → feature → docs).

## Technical Context

**Language/Version**: Java 25 (toolchain), Gradle (Kotlin DSL, `build-logic` convention plugins)
**Primary Dependencies**: Spring Boot 4.1.1 (webmvc starter, composition root only); SnakeYAML Engine (YAML load/dump); networknt json-schema-validator (storage ring only); jspecify 1.0 (nullability); JUnit 5 + AssertJ (tests); ArchUnit (layering/purity gate); palantir-java-format (spotless)
**Storage**: Filesystem YAML packs (pack.yaml + `*.tactic.yaml`), behind the domain repository ports; atomic temp→ATOMIC_MOVE, pack-root-contained writes
**Testing**: JUnit 5 + AssertJ; ArchUnit fitness functions (ADR-0006 slice rules + known-bad controls); `./gradlew verifyAll`; test economy per DIRECTIVE_041 (one focused non-vacuous test per concern, with named positive controls)
**Target Platform**: Linux JVM service
**Project Type**: Single multi-module service (vertical-concern slices)
**Performance Goals**: N/A (correctness/fidelity mission; no latency target)
**Constraints**: Onion layering, inward-only dependencies, pure plain-Java domain (jspecify only) — ArchUnit-enforced; semantic round-trip (NOT byte-identical); replace-existing writes only
**Scale/Scope**: Whole-service re-slice (6 existing modules → `kernel`/`packs`/`validation`/`bootstrap` + `charter-api`) + a new `validation` module + the pack-repository feature + DDD docs. No new runtime dependency (modular-validators is **vendored**, not a Gradle dependency; DSO↔entity mapping is hand-written — no MapStruct).

## Charter Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Charter paradigms (compact context): behaviour-driven-development, specification-by-example, **domain-driven-design**, c4-incremental-detail-modeling. This mission is a direct application of all four.

| Gate | Status |
|---|---|
| DIRECTIVE_001 (Architectural Integrity / separation of concerns) | **Honoured** — slices + onion layering + DIP; ArchUnit-gated. |
| DIRECTIVE_003 (Decision Documentation) | **Honoured** — Decision Moments (DM 01M5EEV6 modelling rule; DM 01M5F3GQ slice adoption); ADR-0006 to be amended to Accepted/realized; glossary + How-to-DDD doc. |
| DIRECTIVE_010 (Specification Fidelity) | **Honoured** — behaviour unchanged by the migration (NFR-002/005); feature traces to FR/NFR/SC. |
| DIRECTIVE_025 (Boy Scout) | **Honoured** — fixing the #138 identity/fidelity defect and the ADR-0006/reality divergence are in-domain debt folds. |
| DIRECTIVE_030 (Test & Typecheck gate) | **Honoured** — `verifyAll` + compiler + ArchUnit per WP. |
| DIRECTIVE_041 (Test economy) | **Honoured** — one non-vacuous test per concern + named positive controls; no shape-pins. |
| DIRECTIVE_051 (Supply-chain) | **N/A / NIL** — no dependency added, upgraded, or removed (validation is vendored source, not a Gradle dep); recorded in research.md. |
| USE_C4_MODEL_TECHNIQUES | **Honoured** — C4 component + structural class diagrams authored (PlantUML), embedded in the Architectural Patterns doc. |
| RECONCILE_CHANGE_SCOPE_TENSIONS | **Honoured** — the slice-vs-layer tension was escalated and resolved (DM 01M5F3GQ); scope landed as one phased PR by operator decision. |

Re-check after Phase 1: no new charter conflicts introduced by the slice carve.

## Project Structure

### Documentation (this mission)

```
kitty-specs/pack-repositories-validation-01M5EET9/
├── plan.md              # This file
├── research.md          # Phase 0 output (decisions + findings-disposition)
├── data-model.md        # Phase 1 output (slice carve + aggregate/VO/port/DSO model)
├── quickstart.md        # Phase 1 output (validation scenarios)
├── occurrence_map.yaml  # DIRECTIVE_035 rename/migration map (8 categories)
└── tasks.md             # Phase 2 output (/spec-kitty.tasks — NOT created here)
```

### Source Code (repository root) — target ADR-0006 slice layout

```
service/
  kernel/                         # one kernel module, two pure sub-packages (DM 01M5F6RC):
    ai.speckitty.charter.kernel.domain       # shared Pack/DRG vocab: DoctrineGraph, DrgNode, DrgEdge,
                                             # NodeKind, Relation, PackDescriptor, PackLevel, Urn, Fingerprint,
                                             # Pep440, RefusalCode, CharterRefusal, ArtifactKind
    ai.speckitty.charter.kernel.validation   # pure (java + jspecify ONLY) guard lib: Constraint, Constraints,
                                             # Checks, ModularRuleset(guard/check), Evaluation, Reason, Rationale, Constrained
  packs/                          ai.speckitty.charter.packs.{domain,application,infra.storage,infra.api}
    # domain:        Pack (aggregate root), Tactic (child entity), TacticStep/TacticReference/PackPaths (VO),
    #                PackIdentifier/TacticIdentifier (VO), ports (TacticRepository, PackDescriptorRepository,
    #                aggregating pack repository, inspection query), CharterPack/CharterOffering/AssetMimeTable/
    #                Diagnostic/PackElementViolation(kept)/PackValidation/PackInspector/PackPolicy/...
    # application:   ConsistentLoad, *Query use cases (contract-neutral)
    # infra.storage: DomainRepositoryService (@Service, realizes ports), SerializationStrategy +
    #                YamlSerializationStrategy, TacticDso/PackDescriptorDso, filesystem storage, PackSchemas, YamlDumpEmitter
    # infra.api:     REST controllers (was adapter-in-rest)
  charter-api/                    # generated OpenAPI contract DTOs (unchanged)
  charter-reports/                # AGGREGATION-ONLY (no java sources); its serviceModules map MUST be updated to {kernel, packs, bootstrap} or verifyAll breaks
  bootstrap/                      ai.speckitty.charter.bootstrap  (was charter-service; composition root; Spring wiring + component-scan; owns the ArchUnit gate)
contracts/                        # top-level; OpenAPI checks + ClosureChecker/Vocabulary; part of verifyAll (verify closure vocab survives rename — scoped, likely no change)
```

**Structure Decision**: ADR-0006 vertical-concern slices (operator DM `01M5F3GQ`). With only the pack concern materialized today, `kernel` is kept minimal (shared Pack/DRG vocab; YAGNI until a second slice exists) and now **also houses `kernel.validation`** — the pure guard library as a second kernel sub-package (DM `01M5F6RC`), so ADR-0006's single-kernel model is preserved (`DOMAIN_IS_PURE` broadens `..kernel.domain..` → `..kernel..`; a validation-purity rule gates `..kernel.validation.. → {java, jspecify}`). The migration is behaviour-preserving and ArchUnit-gated.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Whole-service re-slice in a feature mission | Operator decision (DM 01M5F3GQ) to set the ADR-0006 slice precedent on the cornerstone PR | Layer-rename-only (prior option) was rejected by the operator as not the true screaming architecture; a split migration PR was rejected in favour of one phased PR |
| New `validation` module (not reuse modular-validators) | Domain purity (its deps break ArchUnit) + not publishable turnkey | Depending on the jar rejected — drags Guava/commons/log4j; reimplement a ~250-LOC jspecify-only subset instead |
| DSO layer distinct from domain entities | Round-trip fidelity must be asserted on a value-equal shape, not the identity-equals entity | Using the entity's equals as the persistence oracle was #138's defect |

## Implementation Concern Map

> Concerns, not work packages. `/spec-kitty.tasks` turns these into phased WPs (operator-approved phasing: skeleton → migrate → validation → feature → docs → verification, one PR).

### IC-01 — Slice skeleton & ArchUnit re-gating
- **Purpose**: Stand up the `kernel` (with `kernel.domain` + `kernel.validation`), `packs`, `bootstrap` modules + `ai.speckitty.charter.<concern>.<layer>` package scaffolding, and rewrite the layering gate to ADR-0006's named rules.
- **Relevant requirements**: FR-013, FR-014, C-003, C-010; NFR-005.
- **Affected surfaces**: `settings.gradle`, `service/*/build.gradle.kts`, `build-logic`, `service/charter-reports` `serviceModules` map (→ new module set), `contracts/` (verify), and `HexagonalArchitectureTest` (→ `bootstrap`).
- **ArchUnit rewrite — inventory ALL, map each** (the live test is ~10 production guard families + known-bad controls, NOT 6; see `occurrence_map.yaml` `archunit_rules_rewrite`): PORT to ADR-0006 globs (ONION_DEPENDS_INWARD, DOMAIN_IS_PURE over `..kernel..`, PORTS_LIVE_IN_DOMAIN, STORAGE_RETURNS_DOMAIN, ENTRYPOINT_OVER_APPLICATION, TRANSPORT_STAYS_IN_ADAPTER, SLICES_DO_NOT_CROSS, GENERATED_API_NOT_IN_DOMAIN); ADD a validation-purity rule (`..kernel.validation.. → {java, jspecify}`); KEEP-as-supplement the code-ownership rules (port-signatures extended to allow `..kernel..`; `internal_error`; the three pack-policy finding/refusal-code rules; the `com.networknt` containment rule; null-marked; no-test-seam) repointed to the new packages. **No guard silently dropped.** Every known-bad control is **co-located in the NEW packages** so glob rules are not vacuously green.
- **Sequencing/depends-on**: none (first phase) — but each rule's rewrite **co-lands with the module move that populates its packages** (IC-02), to avoid a vacuous-gate / unenforced window.
- **Risks**: glob rules over empty new packages pass trivially until code moves — closed by co-landing + known-bad controls in the new packages.

### IC-02 — Whole-service migration into slices (behaviour-preserving) — SPLIT per source module, bottom-up
- **Purpose**: Move the existing code into the slice layout with no behaviour change, driven by `occurrence_map.yaml`. Becomes **one WP per source module, migrated bottom-up** so `verifyAll` stays green at each step (a Gradle `project()` edge breaks the instant a consumer's dep is renamed). Each WP = move + package/import rewrite + downstream `project()` fix + the co-landing ArchUnit rule change + `verifyAll` green. **No logic change, no retirement** (old-port retirement is IC-04/05).
  - 02a `charter-domain` → `kernel.domain` (+ scaffold `kernel.validation`) — innermost first
  - 02b `charter-domain` pack types → `packs.domain` (carve per data-model: CharterOffering/AssetMimeTable/Diagnostic/PackElementViolation → packs)
  - 02c `charter-read-app` → `packs.application`
  - 02d `adapter-out-yaml` → `packs.infra.storage` (keep `Yaml…` class names)
  - 02e `adapter-in-rest` → `packs.infra.api`
  - 02f `charter-service` → `bootstrap` (+ `settings.gradle`, `charter-reports` serviceModules → {kernel,packs,bootstrap}, `build-logic`, verify `contracts/`)
- **Relevant requirements**: FR-013, C-010; NFR-002, NFR-005.
- **Affected surfaces**: every package declaration + import across the six modules; build files; test sources + fixtures move with their modules.
- **Sequencing/depends-on**: IC-01; each sub-WP depends on the previous (bottom-up build order).
- **Risks**: large mechanical diff kept legible by per-module WPs; the kernel↔packs carve is fixed in data-model (architect-verified: the 13 firm-kernel classes are clean of packs references).

### IC-03 — Validation kernel sub-package (pure, gated)
- **Purpose**: Lean jspecify-only port of modular-validators core with the dual `guard()`/`check()` strategy, as `ai.speckitty.charter.kernel.validation` (nested in the kernel module, DM 01M5F6RC); attribution + took/changed ledger.
- **Relevant requirements**: FR-006, FR-014, NFR-001, C-001, C-006.
- **Affected surfaces**: `service/kernel/**` (`ai.speckitty.charter.kernel.validation`); the validation-purity ArchUnit rule (`..kernel.validation.. → {java, jspecify}`) + known-bad control.
- **Sequencing/depends-on**: IC-02 02a (kernel module + `kernel.validation` scaffold exists).
- **Risks**: must stay framework-free (zero third-party imports); fix the description-only `Constraint.equals`; omit Evidence/dynamic-descriptions/dsl.

### IC-04 — Domain model: aggregate, entity, value objects, ports
- **Purpose**: `Pack` aggregate root (identity `PackIdentifier`) + `Tactic` child entity (identity equals) + value-object records + identifier VOs + the repository ports + inspection query; entities via fluent builder + package-private ctor; guards via the validation vocabulary.
- **Relevant requirements**: FR-005, FR-007, FR-008, FR-009, C-005, C-009.
- **Affected surfaces**: `packs.domain`, `kernel.domain` (shared vocab).
- **Sequencing/depends-on**: IC-02 (slice homes), IC-03 (validation available).
- **Risks**: identity-equals vs the old value-equal `Tactic` record — the round-trip no longer leans on entity equals (moves to DSO).

### IC-05 — Persistence edge: repository impl, strategy, DSO, storage
- **Purpose**: `@Service DomainRepositoryService` realizing the domain ports; ctor-injected `SerializationStrategy` (one `YamlSerializationStrategy`) + filesystem storage (atomic, pack-root-contained, `PackIdentifier`→path); DSO records; hand-written DSO↔entity mapping; round-trip fidelity asserted on the value-equal DSO.
- **Relevant requirements**: FR-001, FR-002, FR-003, FR-004, FR-011, FR-012, NFR-003, NFR-004, C-002, C-004, C-008.
- **Affected surfaces**: `packs.infra.storage`. Likely **~2 WPs** (a: `SerializationStrategy` + `YamlSerializationStrategy` + DSO records + filesystem storage mechanics; b: `DomainRepositoryService` realizing the ports + hand-written DSO↔entity mapping + the deliberate retirement of the old `PackElementRepository`/`PackLocator`). C-004 (round-trip is the contract) is realized here.
- **Sequencing/depends-on**: IC-04 (ports + entities).
- **Risks**: multi-pack handled via explicit `PackIdentifier` (no per-pack singleton); write-of-new-element out of scope (C-008); refusal codes reuse (deferred follow-up noted).

### IC-06 — Wiring through the production path
- **Purpose**: Register the edge impl as a bean in `bootstrap` (component-scan or @Bean) and prove it via a wiring test that round-trips through the **wired bean**.
- **Relevant requirements**: FR-010.
- **Affected surfaces**: `bootstrap`.
- **Sequencing/depends-on**: IC-05.
- **Risks**: no Spring Data; stereotype at the edge only; domain port stays framework-free.

### IC-07 — DDD docs, glossary, changelog
- **Purpose**: Author the pattern-setting docs: `docs/development/code-style-and-conventions.md`, a "How to DDD here" guide, an "Architectural Patterns Applied" page embedding the C4 + class diagrams and the concern↔pattern-role mapping, glossary entries (headlined Domain vs Persistence Entity), and a `docs/changelog` entry. Fold the ADOPT/AVOID learnings (modular-validators + TraMBU).
- **Relevant requirements**: FR-015, FR-016, FR-017.
- **Affected surfaces**: `docs/development/**`, `docs/architecture/**`, `docs/glossary/**`, `docs/changelog/**`; ADR-0006 amended to reflect the realized slice structure.
- **Sequencing/depends-on**: IC-01..IC-06 (documents the realized shape).
- **Risks**: `planning_artifact` WP (docs only); keep separate from code WPs.

### IC-08 — Verification (ACCEPTANCE CHECKLIST, not a separate code WP)
- **Purpose**: Per DIRECTIVE_041, tests ship **inside their concern WP** (not a second materialized test WP). IC-08 is the acceptance view that the following controls exist, each paired with a named positive control: DSO round-trip literal oracle + write→refetch mutation control (IC-05); write-safety failure-injection + symlink-escape with in-root positive control (IC-05); ArchUnit known-bad controls co-located in the new packages — domain-purity, validation-purity, port-signature, transport, networknt, internal-error, pack-policy (IC-01, co-landing with each move); the old `PackElement*Test` battery **replaced** by the new ports' tests (IC-05), `PackElementViolation` retained.
- **Relevant requirements**: NFR-001, NFR-002, NFR-003, NFR-004, NFR-005; SC-001..SC-005.
- **Affected surfaces**: tests live in `kernel`/`packs`/`bootstrap` test sources with their concern WP (no standalone test WP).
- **Sequencing/depends-on**: none of its own — it is the acceptance checklist across IC-01/IC-03/IC-04/IC-05/IC-06.
- **Risks**: test economy (DIRECTIVE_041); every absence-assertion paired with a named positive control; no shape-pins.
