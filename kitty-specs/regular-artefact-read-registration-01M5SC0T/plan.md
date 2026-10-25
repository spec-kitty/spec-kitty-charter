# Implementation Plan: Regular artifact read & registration

**Branch**: `feat/regular-artefacts` | **Date**: 2026-10-25 | **Spec**: [spec.md](./spec.md)
**Input**: Mission specification from `kitty-specs/regular-artefact-read-registration-01M5SC0T/spec.md`

## Summary

Take the regular charter artifacts (directive, styleguide, paradigm, and the new toolguide) through
a consistent **read + functional-registration** API surface, on a shared **relationship/graph core**,
skeleton-first. Build the kernel graph core and one reference slice (**directive**) end-to-end, then
replicate the proven per-kind slice to styleguide and paradigm and add toolguide. Identity follows
ADR-0027 (one unidirectional relationship per `{from,to}` pair, bounded collapse); the write surface
follows ADR-0026 (functional registration, not CRUD); writes revalidate the whole `Pack` aggregate
and commit atomically. Per operator decision, each kind is modelled and wired **independently (no
shared kind registry)** — #169 stays deferred.

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain via `charterJavaVersion`; CI builds on 25, never 21).
**Primary Dependencies**: Spring Boot Web MVC (edge rings only — `infra.api`, `deployable`); jspecify (nullness); snakeyaml-engine (storage codec, confined to `infra.storage`, ADR-0017); OpenAPI-generated contract DTOs (`charter-api`). The pure core (`kernel`, `packs.domain`) takes **no external dependency** (ArchUnit-enforced, ADR-0006). **No new runtime dependency is introduced by this mission.**
**Storage**: Filesystem pack store (YAML); one flat DSO per kind (record-equality = round-trip assertion, ADR-0024). Registered relationships persist as `DrgEdge` entries in the pack's DRG shard (`<pack>/drg/*.yaml`) via the existing `GraphShardWriter`/shard reader; the domain `Relationship` maps to/from `DrgEdge` at the YAML boundary (ADR-0025).
**Testing**: JUnit Platform + Suite; ArchUnit purity/boundary gate; Cucumber BDD scenarios in `contracts/charter/scenarios/` (specification-by-example, authored first); Spring Boot test for edge wiring; per-kind DSO round-trip record-equality; test-first with same-fixture positive controls (tactic `acceptance-criteria-non-vacuity`).
**Target Platform**: JVM, Linux-server per-worktree daemon.
**Project Type**: single — the Java charter offering service, onion vertical-slice modules.
**Performance Goals**: a relationship walk issues ≤1 repository read per distinct reachable component (cycle-guarded) and never materialises the full DRG (NFR-001).
**Constraints**: open-packs YAML round-trips exactly (PROJECT_002); read content must not diverge from the Python oracle; the OpenAPI contract is honoured and only extended additively (ADR-0009); strict SHA-256 dependency verification; whole-`Pack` aggregate-atomic writes, zero partial state on refusal (NFR-002).
**Scale/Scope**: five regular kinds (tactic already wired for read; directive/styleguide/paradigm/toolguide in scope); procedure out of scope (#150); ~130 DRG edges per pack today.

## Charter Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Charter: `.kittify/charter/charter.md` (Java 25 offering service replacing core's Python charter by rewrite).

- **Oracle non-divergence (never-go-wrong #1).** Read responses for the newly-wired kinds must match the Python charter's content (conformance corpus). **Registration is net-new** — the Python oracle has no write surface — so non-divergence binds the **read** path; the write path is additive with no oracle to match. ✅ with that scoping noted.
- **OpenAPI contract (never-go-wrong #2).** New GET/POST/PUT operations and Problem codes are **additive** only (ADR-0009); no incompatible change reaches core. ✅
- **open-packs round-trip (PROJECT_002).** Honoured via the `Relationship`↔`DrgEdge` mapper and upstream source normalisation (ADR-0027); the charter never rewrites a shard on read. Registered relationships write through `GraphShardWriter` in the canonical shard shape. ✅ (watch item, see Risks)
- **No unverified dependency.** No new runtime dependency; SHA-256 verification unaffected. ✅
- **BDD / SbE, test-first, DDD, C4, ArchUnit purity, Java style.** The plan authors scenarios first, keeps the core framework-free, copies the proven Tactic slice per kind, and respects the layer gates. ✅
- **Adversarial review squad at the plan point and before merge.** The plan-point squad has run (post-specify multi-lens review + the identity corroboration; findings folded in / tracked at spec-kitty/spec-kitty#6667). A **pre-merge squad is owed** per charter. ✅ plan-point / ⏳ pre-merge.

No charter violations to justify; Complexity Tracking omitted.

## Project Structure

### Documentation (this mission)

```
kitty-specs/regular-artefact-read-registration-01M5SC0T/
├── plan.md              # This file
├── research.md          # Phase 0 output — decisions + squad-finding dispositions
├── data-model.md        # Phase 1 output — kernel graph core + per-kind entities
├── quickstart.md        # Phase 1 output — reference-slice walkthrough
└── contracts/           # Phase 1 output — read + registration contract additions
```

### Source Code (repository root)

```
service/
├── kernel/src/main/java/ai/speckitty/charter/kernel/domain/
│   ├── Relationship.java        # NEW: VO (from,to,type,when,rationale); unordered-pair identity (ADR-0027)
│   ├── RelationType.java        # NEW: behaviour-carrying enum (directionality; propagation() deferred)
│   ├── Directionality.java      # NEW: DIRECTED | BIDIRECTIONAL
│   ├── GraphWalker.java         # NEW: visitor (onNode/onRelationship -> Descent)
│   ├── Descent.java             # NEW: CONTINUE | SKIP | STOP
│   ├── DoctrineGraph.java       # holds Relationships for serialize/validate
│   └── DrgEdge.java             # (to move) -> storage DSO; see infra.storage
├── packs/src/main/java/ai/speckitty/charter/packs/
│   ├── domain/
│   │   ├── CharterArtifact.java         # relationships() supersedes edges()
│   │   ├── Directive.java, Styleguide.java, Paradigm.java  # relationships(); registration-ready
│   │   ├── Toolguide.java + ToolguideIdentifier.java       # NEW kind
│   │   ├── ComponentResolver.java       # NEW port: resolve(Urn) -> Optional<CharterArtifact>
│   │   ├── {Directive,Styleguide,Paradigm,Toolguide}Repository.java  # per-kind read+write ports
│   │   └── Pack.java                    # + toolguides collection & invariants; registration revalidation
│   ├── application/
│   │   ├── {Directive,Styleguide,Paradigm,Toolguide}ReadService.java   # per-kind, copy Tactic
│   │   ├── {Directive,Styleguide,Paradigm,Toolguide}RegistrationService.java # load-mutate-revalidate
│   │   └── GraphWalk.java               # NEW: application-layer lazy walk driver (ComponentResolver)
│   └── infra/
│       ├── api/{Directive,Styleguide,Paradigm,Toolguide}Controller.java  # GET + POST/PUT; RelationshipController
│       ├── api/RefusalHandler.java      # + code->status map (422/409/412/428)
│       └── storage/{...}Dso.java, DrgEdge(DSO), RelationshipMapper, aggregate-commit store
├── charter-api/                         # OpenAPI contract + generated DTOs (additive)
└── deployable/                          # composition root wiring
contracts/charter/                       # openapi.yaml + scenarios (SbE, authored first)
```

**Structure Decision**: single Java service, onion vertical slices (ADR-0006). The relationship/graph
core lands in the pure `kernel`; the walk driver and registration services in `packs.application`; the
REST surface and the `Relationship`↔`DrgEdge` mapping at the `packs.infra.*` edges. Each artifact kind
is a self-contained slice copied from the proven Tactic slice — no shared kind registry (#169 deferred).

## Implementation Concern Map

> Concerns, not work packages. `/spec-kitty.tasks` turns these into WPs.

### IC-01 — Kernel graph core
- **Purpose**: the shared, framework-free relationship vocabulary every slice depends on.
- **Relevant requirements**: FR-001, FR-002, FR-004 (walker type).
- **Affected surfaces**: `kernel/domain/{Relationship,RelationType,Directionality,GraphWalker,Descent}.java`, `DoctrineGraph`.
- **Sequencing/depends-on**: none (first).
- **Risks**: `Relationship` identity must `equals`/`hashCode` on the **unordered** pair while still storing direction (ADR-0027); keep the kernel pure; `propagation()` is deferred with `impacts` (ADR-0025 remediation), ship `directionality()`+`token()` only.

### IC-02 — DrgEdge-as-DSO and the relationship persistence mapping
- **Purpose**: demote `DrgEdge` to a storage DSO and round-trip `Relationship` ↔ `DrgEdge` through the DRG shard.
- **Relevant requirements**: FR-005, NFR-003, and PROJECT_002.
- **Affected surfaces**: `kernel/domain/DrgEdge.java` (move to `packs.infra.storage`), `GraphShardWriter`/shard reader, `RelationshipMapper`, `PackPolicy` edge handling.
- **Sequencing/depends-on**: IC-01.
- **Risks**: round-trip fidelity (`when`/`rationale` must survive); reconcile with `PackPolicy.edge()` which already binds `impacts`/`in_tension_with`; the interim typed→SUGGESTS / bare→REQUIRES mapping is unconfirmed (charter #167, reconciled upstream at spec-kitty/spec-kitty#6667); never rewrite a shard on read.

### IC-03 — relationships() + lazy repository-backed walk
- **Purpose**: a component reports its originating relationships; a walk resolves neighbours on demand.
- **Relevant requirements**: FR-003, FR-004, NFR-001.
- **Affected surfaces**: `CharterArtifact.relationships()`, `packs.domain/ComponentResolver.java`, `packs.application/GraphWalk.java`, per-kind repository `load` populating relationships.
- **Sequencing/depends-on**: IC-01, IC-02.
- **Risks**: effective-origin filter (from==self, or BIDIRECTIONAL with to==self); resolver returns empty and the walk continues for out-of-scope/unwired/external target kinds (procedure/asset/agent_profile); cycle guard; no reverse-walk needed given the unidirectional collapse.

### IC-04 — Directive read slice (reference slice, read half)
- **Purpose**: read a pack's directives (all, by id) with relationships populated — the copyable pattern.
- **Relevant requirements**: FR-006.
- **Affected surfaces**: `DirectiveReadService`, `DirectiveController` (GET), contract GET operations + scenarios.
- **Sequencing/depends-on**: IC-03. **Pattern = the existing Tactic slice, copied.**
- **Risks**: read content must match the Python oracle (conformance corpus).

### IC-05 — Registration write path (directive first)
- **Purpose**: register a directive (fields + relationships) and a relationship edge, via functional registration.
- **Relevant requirements**: FR-007, FR-008, FR-009, FR-010, FR-012, NFR-002.
- **Affected surfaces**: `DirectiveRegistrationService` (load→mutate→revalidate `Pack`), `DirectiveController` (POST/PUT), `RelationshipController` (`PUT /packs/{id}/relationships`), aggregate-commit store (stage + atomic directory swap), `RefusalHandler` + new `RefusalCode` tokens, ETag/If-Match on a pack/artifact revision.
- **Sequencing/depends-on**: IC-04.
- **Risks**: aggregate atomicity (stage+swap vs journal — stage+swap chosen, see research); `412/428` are `Problem`-only not `RefusalCode`s; `RefusalHandler` needs a real code→status map; the pack/artifact revision (digest) must be distinct from the read `ConsistentRead` fingerprint; **dual-write ownership** — a relationship may arrive in an artifact body or via the relationship endpoint; define which door owns a pair.

### IC-06 — Mutability-policy write gate
- **Purpose**: refuse writes beyond the owning pack's mutability level.
- **Relevant requirements**: FR-011.
- **Affected surfaces**: a net-new policy model (ADR-0012 levels are not modelled in Java yet — no policy field on `PackDescriptor`), the registration services, `governance_policy_violation` refusal.
- **Sequencing/depends-on**: IC-05.
- **Risks**: this is **new** work, not reuse; the level→operation mapping (open/extendable/locked → register/replace/relationship) must be defined.

### IC-07 — Replicate to styleguide and paradigm
- **Purpose**: styleguide and paradigm reach read + registration parity.
- **Relevant requirements**: FR-013, FR-014.
- **Affected surfaces**: per-kind read/registration services, controllers, DSOs, Pack collections — **copied** from the directive slice, kept independent (no shared registry).
- **Sequencing/depends-on**: IC-05.
- **Risks**: accepted near-duplication (operator decision); each kind evolves independently.

### IC-08 — Toolguide as a new kind
- **Purpose**: model and wire the net-new toolguide kind end-to-end.
- **Relevant requirements**: FR-015.
- **Affected surfaces**: `Toolguide` entity + `ToolguideIdentifier` + `ToolguideRepository` + `ToolguideDso` + `Pack` toolguides collection & invariants + read + registration, from the canonical `contracts/packs/schemas/toolguide.schema.json`.
- **Sequencing/depends-on**: IC-05 (pattern); independent of IC-07.
- **Risks**: it is the first kind added under the per-kind decision — proves the slice carries a brand-new kind.

### IC-09 — Contract and scenarios
- **Purpose**: describe the read + registration surface in the OpenAPI contract and BDD scenarios.
- **Relevant requirements**: NFR-004, and every FR's external shape.
- **Affected surfaces**: `contracts/charter/openapi.yaml` (+ paths/responses/schemas), `contracts/charter/scenarios/`, generated DTOs in `charter-api`.
- **Sequencing/depends-on**: spans IC-04..IC-08; scenarios authored **before** each slice (SbE).
- **Risks**: additive-only (ADR-0009); DTOs never expose storage DSOs (C-002).
