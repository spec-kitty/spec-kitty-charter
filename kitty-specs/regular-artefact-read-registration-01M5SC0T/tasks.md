# Tasks: Regular artefact read & registration

**Mission**: regular-artefact-read-registration-01M5SC0T
**Planning base / merge target**: `feat/regular-artefacts`
**Design sources**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md), [contracts/openapi-additions.yaml](./contracts/openapi-additions.yaml)

Decomposition is **skeleton-first and layered**, per the IC-01..IC-09 map. The relationship/graph
core lands first (WP01), then the domain relationships migration + lazy walk (WP03), then the storage
graph seam + relationship persistence (WP02), then the Pack aggregate + the net-new toolguide kind +
the write-path foundations (WP04). The **directive reference slice** (WP05) proves read + functional
registration end-to-end; the mutability gate (WP06) and the styleguide/paradigm/toolguide slices
(WP07/08/09) replicate it per kind (no shared registry, D5); WP10 consolidates the contract + wiring.

**Each source file is owned by exactly one WP** (finalize-tasks validates no `owned_files` overlap).
The hotspots are resolved by single-owner assignment: `Pack.java`→WP04; `CharterArtifact.java` + the
four entities + `ArtifactEdges.java`→WP03; `DrgEdge.java` + `DoctrineGraph.java` + `PackPolicy.java` +
`GraphShardWriter.java`→WP02; `RefusalHandler.java` + `RefusalCode.java`→WP04; the root
`contracts/charter/openapi.yaml`→WP10 (each slice owns its per-kind path/schema fragments). A later WP
that needs a hotspot's behaviour **calls into it**; it does not edit it.

**Sequencing correction (grounded in the code):** no production code calls `CharterArtifact.edges()`,
so the domain `relationships()` migration (WP03) lands **before** the `DrgEdge` kernel→storage move
(WP02); this keeps every file single-owned and every lane compilable. Every code WP authors **BDD /
specification-by-example scenarios first and is test-first** (charter-mandated; DIRECTIVE_034/041) —
tests are required, not optional. All WPs are `execution_mode: code_change`, `agent_profile:
java-jenny`, Java 25.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | `Directivity` enum DIRECTED/BIDIRECTIONAL (WP01) | WP01 | [P] |
| T002 | `RelationType` behaviour-carrying enum (directionality()+token(); propagation deferred) (WP01) | WP01 | |
| T003 | `Relationship` VO — unordered-pair identity, when/rationale (WP01) | WP01 | |
| T004 | `GraphWalker` visitor + `Descent` enum (WP01) | WP01 | [P] |
| T005 | Kernel graph-core unit tests (unordered-pair identity control, catalogue parity) (WP01) | WP01 | |
| T006 | Move `DrgEdge` kernel→`packs.infra.storage` as flat DSO (WP02) | WP02 | |
| T007 | Rewrite `DoctrineGraph` (kernel) to hold `Relationship` (WP02) | WP02 | |
| T008 | `RelationshipMapper` — Relationship↔DrgEdge round-trip (WP02) | WP02 | |
| T009 | Rewrite `GraphShardWriter` to serialise a `Relationship` graph (WP02) | WP02 | |
| T010 | Rewrite `PackPolicy` edge binding to produce `Relationship`s (WP02) | WP02 | |
| T011 | `RelationshipShard` — read/upsert-on-pair/serialise (last-write-wins) (WP02) | WP02 | |
| T012 | Storage graph-seam round-trip tests (mapper, shard, legacy read green) (WP02) | WP02 | |
| T013 | `CharterArtifact.relationships()` supersedes `edges()` (WP03) | WP03 | |
| T014 | Rewrite `ArtifactEdges` to build `Relationship`s (WP03) | WP03 | |
| T015 | Migrate the four entities' `relationships()` (WP03) | WP03 | |
| T016 | `ComponentResolver` port (D6 empty-on-unwired) (WP03) | WP03 | [P] |
| T017 | `GraphWalk` driver — lazy, cycle-guarded, bounded (WP03) | WP03 | |
| T018 | relationships + walk tests; update legacy family/entity tests (WP03) | WP03 | |
| T019 | `Toolguide` entity + `ToolguideIdentifier` + `ToolguideRepository` (WP04) | WP04 | |
| T020 | `ToolguideDso` + enumerated toolguide storage seam (WP04) | WP04 | |
| T021 | `Pack` toolguides collection + invariants + builder adder (WP04) | WP04 | |
| T022 | `PackCommitStore` — stage whole pack + atomic directory swap (WP04) | WP04 | |
| T023 | New `RefusalCode` validation(422)/conflict(409) tokens + contract enum (WP04) | WP04 | |
| T024 | `RefusalHandler` code→status map (422/409/412/428) (WP04) | WP04 | |
| T025 | `PackRevision` ETag digest (distinct from read fingerprint) (WP04) | WP04 | |
| T026 | Pack/toolguide/commit/refusal/revision tests (WP04) | WP04 | |
| T027 | `WritePolicyGate` port + permissive default (foundation for WP06) (WP04) | WP04 | |
| T028 | `directives.feature` BDD scenarios (authored first) (WP05) | WP05 | |
| T029 | `DirectiveReadService` (copy Tactic read) (WP05) | WP05 | |
| T030 | `DirectiveRegistrationService` (load→mutate→revalidate→commit + gate) (WP05) | WP05 | |
| T031 | `DirectiveController` (GET + POST/PUT) implements `DirectivesApi` (WP05) | WP05 | |
| T032 | `RelationshipController` (PUT /relationships, idempotent on pair) (WP05) | WP05 | |
| T033 | Directive + relationship contract fragment files (WP05) | WP05 | |
| T034 | Directive read/registration/walk/controller tests (WP05) | WP05 | |
| T035 | `MutabilityLevel` enum + operation sets (ADR-0012) (WP06) | WP06 | |
| T036 | `MutabilityPolicySource` + `PackDescriptor` policy field (WP06) | WP06 | |
| T037 | `PackMutabilityPolicy implements WritePolicyGate` (@Primary) (WP06) | WP06 | |
| T038 | Mutability gate tests (within-level control + over-level refusal) (WP06) | WP06 | |
| T039 | `styleguides.feature` scenarios (WP07) | WP07 | |
| T040 | `StyleguideReadService` (+ test) (WP07) | WP07 | |
| T041 | `StyleguideRegistrationService` (+ test) (WP07) | WP07 | |
| T042 | `StyleguideController` implements `StyleguidesApi` (+ test) (WP07) | WP07 | |
| T043 | Styleguide contract fragment files (WP07) | WP07 | |
| T044 | `paradigms.feature` scenarios (WP08) | WP08 | |
| T045 | `ParadigmReadService` (+ test) (WP08) | WP08 | |
| T046 | `ParadigmRegistrationService` (+ test) (WP08) | WP08 | |
| T047 | `ParadigmController` + `ParadigmWalkSliceTest` (WP08) | WP08 | |
| T048 | Paradigm contract fragment files (WP08) | WP08 | |
| T049 | `toolguides.feature` scenarios (WP09) | WP09 | |
| T050 | `ToolguideReadService` (+ test) (WP09) | WP09 | |
| T051 | `ToolguideRegistrationService` (+ test; duplicate-id control) (WP09) | WP09 | |
| T052 | `ToolguideController` implements `ToolguidesApi` (+ test) (WP09) | WP09 | |
| T053 | Toolguide contract fragment files (WP09) | WP09 | |
| T054 | Root `openapi.yaml`: register all new paths + tags (WP10) | WP10 | |
| T055 | Regenerate + reconcile `*Api`/DTOs across slices (WP10) | WP10 | |
| T056 | `CompositeComponentResolver` (@Service, kind-dispatch, D6) (WP10) | WP10 | |
| T057 | Deployable wiring verification + `RegularArtefactWiringTest` (WP10) | WP10 | |
| T058 | Full `@contract` suite + `:contracts:check` green across all kinds (WP10) | WP10 | |
| T059 | Living-docs sync + NFR-004/C-001/C-002/SC-004 verification (WP10) | WP10 | |

## Work Packages

### WP01 — Kernel graph core
Goal: the pure relationship/graph vocabulary (`Relationship` unordered-pair identity, `RelationType`,
`Directionality`, `GraphWalker`, `Descent`) every slice depends on (FR-001, FR-002). Priority: P1
(foundational). Independent test: unordered-pair identity (reverse collapses in a `Set`); RelationType
catalogue mirrors `Relation` token-for-token; walker/descent contract. Subtasks: T001–T005.
Dependencies: none. Prompt: `tasks/WP01-kernel-graph-core.md`.

### WP03 — relationships() migration + ComponentResolver + GraphWalk
Goal: migrate the `CharterArtifact` family from `edges()` to `relationships()`; add the
`ComponentResolver` port + the lazy, cycle-guarded, bounded `GraphWalk` driver (FR-003, FR-004,
NFR-001). Priority: P1. Independent test: effective-origin paired control; on-demand bounded walk
with D6 empty-on-unwired. Subtasks: T013–T018. Dependencies: WP01. Prompt:
`tasks/WP03-relationships-and-walk.md`. (Lands **before** WP02 — no production `edges()` caller.)

### WP02 — DrgEdge→storage DSO + relationship persistence
Goal: demote `DrgEdge` to a storage DSO, make `DoctrineGraph` hold `Relationship`, add the
`RelationshipMapper` + `RelationshipShard` (upsert-on-pair, last-write-wins) + rewrite `GraphShardWriter`
and `PackPolicy` edge binding — lossless round-trip of `when`/`rationale` (FR-005, NFR-003,
PROJECT_002). Priority: P1. Independent test: mapper + shard round-trip with present-vs-absent
controls; legacy DRG read path stays green. Subtasks: T006–T012. Dependencies: WP01, WP03. Prompt:
`tasks/WP02-drgedge-dso-and-relationship-persistence.md`.

### WP04 — Pack aggregate + Toolguide + registration foundations
Goal: the single owner of `Pack.java` — toolguides collection + invariants (FR-008); the net-new
`Toolguide` kind end-to-end in domain + the enumerated storage seam (FR-015); the aggregate-commit
store (stage + atomic directory swap, NFR-002); the `RefusalHandler` code→status map + new
`RefusalCode` tokens; the `PackRevision` ETag; and the `WritePolicyGate` port + permissive default
(foundation for WP06). Priority: P1 (the write-path hotspot). Independent test: aggregate invariants
with duplicate-id control; commit-store pre-swap-failure = zero change; refusal map; toolguide
round-trip. Subtasks: T019–T027. Dependencies: WP02, WP03. Prompt:
`tasks/WP04-pack-aggregate-toolguide-registration-foundations.md`.

### WP05 — Directive slice (the reference slice)
Goal: directive read + functional registration end-to-end (FR-006/007/009/010/012; SC-001/002/003/004)
— read value-equal with relationships, register with aggregate revalidation + atomic commit, the
shared relationship endpoint, optimistic concurrency, typed refusals, and the on-demand walk. The
copyable pattern. Priority: P1 (MVP skeleton). Independent test: register→read-back value-equal;
duplicate/malformed refused with a valid control, pack unchanged; walk resolves on demand reading
fewer than the pack total. Subtasks: T028–T034. Dependencies: WP04. Prompt: `tasks/WP05-directive-slice.md`.

### WP06 — Mutability-policy write gate
Goal: refuse a write beyond the owning pack's mutability level (FR-011) — model ADR-0012's
open/extendable/locked, map it onto REGISTER/REPLACE/RELATIONSHIP, and implement the real
`WritePolicyGate` (@Primary) raising `governance_policy_violation`. Enforced only through the WP04
port seam (no registration-service edit). Priority: P2. Independent test: level→operation matrix;
over-level refused with a within-level control. Subtasks: T035–T038. Dependencies: WP05. Prompt:
`tasks/WP06-mutability-policy-gate.md`.

### WP07 — Styleguide slice
Goal: styleguide read + registration parity (FR-013), copied from the directive slice; empty
relationships; structured closed response schema. Priority: P2. Independent test: register→read-back
value-equal; duplicate/malformed refused with a valid control. Subtasks: T039–T043. Dependencies: WP05.
Prompt: `tasks/WP07-styleguide-slice.md`.

### WP08 — Paradigm slice
Goal: paradigm read + registration parity (FR-014), copied from the directive slice; directive-ref
relationships + a walk. Priority: P2. Independent test: register→read-back value-equal; walk resolves
referenced directives on demand (SC-002). Subtasks: T044–T048. Dependencies: WP05. Prompt:
`tasks/WP08-paradigm-slice.md`.

### WP09 — Toolguide slice
Goal: expose the net-new toolguide kind through read + registration (FR-015); entity/DSO/repo/Pack
collection already exist from WP04 — services/controllers/contract only; duplicate-id refusal via
aggregate revalidation (US3/SC-003). Priority: P3. Independent test: register→read-back value-equal;
same-id refused with a distinct-id control. Subtasks: T049–T053. Dependencies: WP04, WP05. Prompt:
`tasks/WP09-toolguide-slice.md`.

### WP10 — Contract + wiring consolidation
Goal: register every new path in the root `openapi.yaml` (triggering DTO/`*Api` regeneration), provide
the production `CompositeComponentResolver`, verify the deployable composition root, and prove the
whole surface through the `@contract` Cucumber suite + `:contracts:check` (NFR-004, C-001, C-002,
SC-004). Priority: P1 (consolidation gate). Independent test: this WP *is* the consolidation —
additive contract (oasdiff clean), full context wires, `@contract` suite green across all kinds.
Subtasks: T054–T059. Dependencies: WP05, WP06, WP07, WP08, WP09. Prompt:
`tasks/WP10-contract-and-wiring-consolidation.md`.

## Sequencing & parallelism

- **Lane A (foundational):** WP01.
- **Lane B (after WP01):** WP03 (domain relationships + walk) — then WP02 (storage graph seam), which
  depends on WP03 so the `DrgEdge` move is safe.
- **Lane C (after WP02+WP03):** WP04 (Pack aggregate + toolguide + write foundations) — the write-path
  hotspot.
- **Lane D (after WP04):** WP05 (directive reference slice) — the MVP skeleton.
- **Lane E (after WP05, parallelisable — disjoint files):** WP06 (gate) ∥ WP07 (styleguide) ∥ WP08
  (paradigm); WP09 (toolguide) after WP04+WP05.
- **Lane F (after all slices + WP06):** WP10 (consolidation).

**MVP**: WP01 → WP03 → WP02 → WP04 → WP05 (+WP10's directive-path wiring) delivers a fully
round-tripping, registerable, walkable **directive** — the highest-value slice — before styleguide/
paradigm/toolguide replicate it.

## Risks (cross-WP)

- **WP02 blast radius**: `DoctrineGraph`/`PackPolicy` sit on the read path; keep the oracle
  non-divergence (PROJECT_002) by keeping the existing DRG read tests green while only changing the
  edge representation.
- **No existing DRG-shard writer / aggregate commit**: WP02's relationship persistence and WP04's
  stage+swap commit are net-new; the registered-relationship shard path must be fingerprint-covered
  and round-trip-safe (never a read-time rewrite, ADR-0027).
- **Contract compile-order**: slice controllers compile against generated `*Api` interfaces that come
  online only when WP10 wires the root — slices author against the agreed names; WP10 is the green
  gate. Keep the root single-owned (WP10).
- **Mutability gate seam**: enforced only through WP04's `WritePolicyGate` port so WP06 touches no
  registration service (single-ownership preserved).
