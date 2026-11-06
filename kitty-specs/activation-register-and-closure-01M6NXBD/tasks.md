# Tasks: Activation register and activated-pack closure

**Mission**: `activation-register-and-closure-01M6NXBD`
**Branch**: planned on `issue-360-activated-closure`, which is rebased onto `main`. `main` now carries PR #368 and
PR #376 (ADR-0059). Completed work merges into `issue-360-activated-closure`, which reaches `main` as two stacked pull
requests.
**Inputs**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md),
[contracts/contract-delta.md](contracts/contract-delta.md), [decisions/](decisions/)

**Rulings that govern:** ADR-0020 D2–D9 and its amendment; ADR-0013 D2–D5; ADR-0059 (on `main`) as revised on
2026-11-05. They supersede the 13:15 discovery decisions on the pull and on lineage.

- **D2.2:** `enhances` is pulled at read, both ways and transitively, and is never written into `selected`. It is
  used at write time in only two places:
  - `includeDeactivated` clears every deny entry the pull would reach;
  - a cascading deactivate counts an active enhancer as a reference in its shared check.
- **D2.4:** lineage passes `requires` and `scope` only, up the whole ancestor chain.

**Complexity limits:** they are still off on `main`, held until the methods over them are refactored. Every WP holds
them by construction (CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1), and the reviewer measures new and
changed methods by hand.

## Two pull requests, and the cut (P1)

- **PR A = WP01–WP10, plus WP16 and WP17.**
  - Code: shared plumbing, the rooted compose and the closure read, the policy file (ADR-0061), the floor, the slice
    and its storage, contract A and its red scenarios (WP08), and CRUD (WP09).
  - The post-PR-A squad fixes: the shared web-transport module (WP16) and the correctness fixes (WP17).
  - Docs: WP10.
- **PR B = WP11–WP15.**
  - Code: typed edges and reach, the cascade rules, and the five per-artifact and preset writes, with contract B.
  - Docs: WP15.
  - PR B's first code WP (WP11) depends on the last PR A code WP (WP09). Every other PR B WP depends on WP11,
    directly or transitively.
- **The cut at closeout.** WP01–WP10, WP16 and WP17 form a dependency-closed prefix: no PR A WP depends on a PR B WP.
  - PR A is built by replaying WP01–WP10, WP16 and WP17's commits onto `main`.
  - PR B stacks WP11–WP15's commits on top of PR A.
  - WP15 touches no file WP10 owns, except through the out-of-map edits it lists. No PR B WP edits a PR A file except
    through a listed out-of-map edit, so the replay never carries PR B content.
- **Red by design inside PR A.** WP08 lands contract A with its new scenarios failing. WP09, in the same lane, turns them
  green. PR A is cut only after WP10, so `main` never sees the red state.
- **Open PRs to rebase over:**
  - #377 (test-tidy-mockito-refusal-codes, ready);
  - #379, #384 and #390;
  - #391 (settings and conventions);
  - #392 (ADR-0060);
  - #393 (`ProblemHandler`, draft);
  - #394 (mutation floors per module, draft: the new `:activations` module may need a floor).
- **The #377 gate (P3):** WP01, WP03 and WP11 start only after #377 merges, or after rebasing over its head. Each
  WP rebases onto its lane base before review. The reviewer is `reviewer-renata`.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | `@ReservedOperation` in `kernel.transport`, TYPE and METHOD targets | WP01 |  |
| T002 | `PreconditionException` and `EntityTags` (strong `If-Match`, weak `If-None-Match`) in the kernel; packs callers updated | WP01 |  |
| T003 | `ReservedOperationInterceptor` in `deployable`, method-aware, with its unit test | WP01 |  |
| T004 | Kernel `CharterRefusal` gains `violations` and `requiredBy`; `RefusalHandler` renders them | WP01 | [P] |
| T005 | Share the body guard: kernel `BodyLimit`, public `JsonBodyGuard`; registration advices use it | WP01 | [P] |
| T006 | `HexagonalArchitectureTest` and the bad fixture follow the moves | WP01 |  |
| T007 | No-behaviour-change evidence and module gates | WP01 |  |
| T008 | Red tests for the rooted closure (`PackCompositionResolverTest`) | WP02 |  |
| T009 | Public `compositionClosure` (`depends_on` + `parent_pack`, tolerant); `checkReferences` keeps the `depends_on`-only closure | WP02 |  |
| T010 | `resolveRooted(available, roots)`: closure first, then the existing checks | WP02 |  |
| T011 | `OfferingReadService.composed(roots)`: tolerant descriptors, facts only for the closure; verified `closure(roots)` | WP02 |  |
| T012 | The offering ratchet: `composed()` equals `composed(all)`; offering and composition scenarios unchanged | WP02 |  |
| T013 | `policy.schema.json`, descriptor `mutability_policy`, the `file-kinds.yaml` rule | WP03 | [P] |
| T014 | `PackPaths.mutabilityPolicy` and the descriptor reader | WP03 | [P] |
| T015 | `PolicyYaml` reader into `MutabilityPolicySource` | WP03 |  |
| T016 | Read the policy per pack on the facts; a missing or invalid file is `pack_unreadable`; `MutabilityPolicyRepository` port | WP03 |  |
| T017 | `PerPackPolicy.levelIn(closure, urn)`: the highest level and the setting pack | WP03 | [P] |
| T018 | `PackMutabilityPolicy` write gate shares `levelIn` over the pack's closure | WP03 |  |
| T019 | ADR-0061 and the open-packs note | WP03 | [P] |
| T020 | Presets on `PackCompositionFacts` from the already-assembled pack | WP04 |  |
| T021 | `RequiredFloor` (pure domain): governing presets' must-have items with the requiring pack | WP04 | [P] |
| T022 | `ClosureView`: packs, held ids, holders, mission types, floor, locks, presets, fingerprint | WP04 |  |
| T023 | `ClosureQueries.snapshot(roots)` in one `ConsistentRead` | WP04 |  |
| T024 | One read per request: stale answers `stale_input`; facts read once per pack | WP04 |  |
| T025 | Wiring and gates | WP04 |  |
| T026 | The `service/activations` Gradle module and package skeleton | WP05 |  |
| T027 | `charter-reports` and `sonar.yml` module lists | WP05 | [P] |
| T028 | Move `ActivationController` (method-level reserved marks) and `ActivationJson` to `activations.infra.api`; deployable scans | WP05 |  |
| T029 | `AvailablePacks` port (no method yet) and `PacksAvailability` adapter stub | WP05 | [P] |
| T030 | `ActivationStorageRoot` in `deployable` from `ConfigRoot.activations()`, in-memory when none | WP05 | [P] |
| T031 | Generalise `HexagonalArchitectureTest` (#140): slices, per-slice rules, generated types, adapter-only `packs`, method-scoped reserved rule | WP05 |  |
| T032 | Bad fixtures for the activations slice, each with `assertBites` | WP05 |  |
| T033 | `hexagonal-service.md`: the cross-slice adapter rule | WP05 | [P] |
| T034 | Kernel `ArtifactKind.InForceWhenAbsent` → `NONE`, all users updated | WP06 | [P] |
| T035 | `ActivationId`, `PackRef`, `Revision` | WP06 |  |
| T036 | `Jcs` canonicaliser (RFC 8785 subset) over the domain value tree, with pinned vectors | WP06 | [P] |
| T037 | `ActivationDocument`: three-state lists, canonical form, revision | WP06 |  |
| T038 | `ClosureSnapshot` value and the `ClosureSnapshots` test builder | WP06 |  |
| T039 | `DocumentRules`: 422 violations and 409 floor refusals | WP06 |  |
| T040 | Purity and gates | WP06 |  |
| T041 | `ActivationRepository` port and `StoredActivation` | WP07 |  |
| T042 | Shared repository contract test; `InMemoryActivationRepository` | WP07 |  |
| T043 | `FileActivationRepository`: temp + fsync + `ATOMIC_MOVE`, per-id lock, `Rename` seam; `ActivationFileCodec` | WP07 |  |
| T044 | 8-writer concurrency, 100 of 100 (NFR-004) | WP07 | [P] |
| T045 | Injected failure and context restart, byte-identical (NFR-005, SC-003) | WP07 | [P] |
| T046 | Adapter choice wired in `deployable` | WP07 |  |
| T047 | Contract A: `listActivationPacks` (+ reserved stub so the generated interface compiles), `ActivationPackList`, `summary` and fingerprint moves, `304`, absent-means-none texts, `x-reserved` off CRUD, fragment | WP08 |  |
| T048 | Red scenarios `activation-register.feature` (US1, US2, US6, R4/R7/J10 additions), `ActivationSteps`, the `@WIP` composition rewrite; failing names recorded | WP08 |  |
| T049 | Reserved pins 13 → 9; the binding pin moves to `selectActivationArtifact`; coverage scans `@reserved` blocks only | WP08 |  |
| T050 | `PacksAvailability` over `ClosureQueries`; unknown pack: 422 on write, 500 `pack_unreadable` on the closure read | WP09 |  |
| T051 | `ActivationService`: create, put (`If-None-Match: *` / non-`*` 400, `If-Match`, LWW, both validators 400), get (`304`), delete, packs; one snapshot per request asserted | WP09 |  |
| T052 | Controller methods, `ActivationMapper`, conditional-request test (controller 304 vs Spring weak compare) | WP09 |  |
| T053 | #125 build notes: `ActivationBodyAdvice` and `ActivationProblems` (422 `activation_invalid`); kind parsing | WP09 |  |
| T054 | Round-trip property test over ≥ 1,000 documents (NFR-003) | WP09 | [P] |
| T055 | Closure read and US6 end to end | WP09 |  |
| T056 | Raised flags for the four activation codes, `ReservedRefusalCodesTest`, all green against WP08's red list | WP09 |  |
| T057 | Divergences DIV-203 to DIV-205 in both register forms | WP10 | [P] |
| T058 | Amendment notes: ADR-0020 D6, ADR-0013 D2, ADR-0012 D2 → ADR-0061 | WP10 | [P] |
| T059 | `offering-and-activation.md`, `logical-model.md`, `resolution-model.md` | WP10 |  |
| T060 | PR A docs changelog fragment with the #360 re-point | WP10 |  |
| T061 | Docs gates | WP10 |  |
| T062 | Typed edges (`Relationship`) on `PackCompositionFacts` from graphs, components and shards | WP11 |  |
| T063 | `EdgeIndex`: an edge-backed resolver over the closure, with `<pack>::` through the compose rule | WP11 |  |
| T064 | `Reachability.reach` on `GraphWalk`: reached and stopped with every `Via`, behind-stopped, lineage | WP11 |  |
| T065 | `enhancesPull` (both ways, transitive over `enhances`) and `enhancersOf` (one hop, reverse) | WP11 | [P] |
| T066 | Expose `reach`, `enhancesPull` and `enhancersOf` on `ClosureView`, with no extra read | WP11 |  |
| T067 | The 500-artifact timing, report-only (NFR-006) | WP11 | [P] |
| T068 | Domain values: `ArtifactRef`, `Reach`, `ReachPlan`, `SkippedReport`, `ActivationChange` | WP12 |  |
| T069 | `CascadeScope` and the `["all"]`-mix refusal | WP12 | [P] |
| T070 | Select delta and report: scope, stop at deny (`viaRequires`, `requiredBy`) | WP12 |  |
| T071 | `unreachedBehindDeactivated` | WP12 |  |
| T072 | Lineage: `requires` and `scope` only, transitive, a denied ancestor stops it | WP12 |  |
| T073 | `includeDeactivated` clearing: walk-reached and `enhances`-pull-reached deny entries (both ways, transitive); never selects a pulled artifact | WP12 |  |
| T074 | Deactivate `skippedShared`: the active set is selected ∪ floor; active enhancers count | WP12 |  |
| T075 | Contract B part one: selections and exclusions built; two codes raised; fragment | WP13 |  |
| T076 | Red scenarios `activation-selections.feature` (US3, US4) | WP13 |  |
| T077 | Reserved pins 9 → 5; the binding pin moves to the preset operation | WP13 |  |
| T078 | `PacksSession`: one closure view per request for snapshot, reach, `enhancesPull` and `enhancersOf` | WP13 |  |
| T079 | `ActivationSelectionService` (select, unselect, `mission_type` → `missionTypes`) | WP13 |  |
| T080 | `ActivationExclusionService` (deactivate with the floor 409, reactivate) | WP13 |  |
| T081 | Controller methods and `ActivationChangeMapper` | WP13 |  |
| T082 | Refusal flags and operation coverage | WP13 |  |
| T083 | All green, and evidence | WP13 |  |
| T084 | Contract B part two: presets built; `preset_not_found` raised; closing fragment | WP14 |  |
| T085 | Red scenarios `activation-presets.feature` (US5) | WP14 |  |
| T086 | Retire the binding pin; 5 → 4; `noReservedOperationTakesABody` | WP14 |  |
| T087 | `PresetRules`: replace listed keys, `force` clears | WP14 |  |
| T088 | `ActivationPresetService` and the controller | WP14 |  |
| T089 | All green, and evidence | WP14 |  |
| T090 | Divergences DIV-206 (lineage) and DIV-207 (`enhances` at write) | WP15 | [P] |
| T091 | Architecture docs for the cascade | WP15 |  |
| T092 | PR B docs changelog fragment | WP15 | [P] |
| T093 | #291 and #360 PR-description notes; docs gates | WP15 |  |
| T094 | Contract changelog fragment for the pack-schema change (`mutability_policy`, `policy.schema.json`) | WP03 | [P] |
| T095 | Digest `policy.yaml` in `PackFileSet.packLevelTail`; a policy edit moves the fingerprint | WP03 | |
| T096 | `ActivationRuntimeHints` for the moved mix-ins, with its hints test | WP05 | [P] |
| T097 | The `web-transport` module: build file, settings, charter-reports, `sonar.yml`, the dependent builds, config scan | WP16 | |
| T098 | Move `Problems`, `ProblemHandler` and the `Problem` JSON and hints; split `ArtifactJson`; one `Link` mix-in | WP16 | |
| T099 | Move `RefusalHandler` and `JsonBodyGuard`; `ReservedRefusalCodesTest` `HANDLER_CLASS` | WP16 | |
| T100 | Move `ActivationBodyAdvice` and `ActivationProblems` from `deployable` into `activations.infra.api` | WP16 | |
| T101 | Behaviour preservation: problem bodies and statuses byte-identical | WP16 | |
| T102 | ArchUnit: no `DEPLOYABLE` exemption; the web ring is an adapter ring; `WEB_NAMES_NO_SLICE`; no domain names `kernel.transport` | WP16 | |
| T103 | Bad fixtures for the new rules | WP16 | [P] |
| T104 | Evidence; the `offering-and-activation.md` flowchart and table (out-of-map, WP10) | WP16 | |
| T105 | Preconditions before content (412 before 422/409, no snapshot); the docs note (out-of-map, WP10) | WP17 | |
| T106 | Reversible directory-name encoding; `ABC` and `abc` never alias | WP17 | |
| T107 | Striped locks, bounded | WP17 | |
| T108 | Typed `UnknownRootException`; `PacksAvailability` catches only it | WP17 | [P] |
| T109 | One de-duplicated preset list for the presets map and `RequiredFloor` | WP17 | [P] |
| T110 | The registration gate reads composition and policy in one `composedWithFacts` | WP17 | [P] |
| T111 | Service-level races (8 writers through `put`; create against create) and a non-fakeable atomicity test | WP17 | |
| T112 | `activation-register-guards.feature`: 401/403 and the precondition-order scenarios | WP17 | |
| T113 | `RequiringPack.via` from the `parent_pack` chain (or documented), with a contract fragment | WP17 | |
| T114 | 422 bodies: never empty `violations`; report every unknown member | WP17 | |

T094–T096 were added by the post-tasks squad fold, and T097–T114 by the post-PR-A squad: WP16 (T097–T104) and WP17
(T105–T114). They are appended after T093 so the existing ids stay stable,
and they belong to WP03 (T094, T095) and WP05 (T096); the per-WP lists below place them in their WPs.

## PR A

### WP01 — Shared transport plumbing (IC-01)

- **Goal:** move the reserved marker, the preconditions, the refusal body members and the body guard (typed
  `JsonBodyGuard` API) to homes a second HTTP slice can reach, with no behaviour change.
- **Prompt:** [tasks/WP01-shared-transport-plumbing.md](tasks/WP01-shared-transport-plumbing.md)
- **Requirements:** C-002, C-005, FR-006, FR-007, NFR-001
- **Dependencies:** none. Gated on #377 (P3); `packs` edits are limited to imports and call sites.

T001 `@ReservedOperation` in the kernel, method- and type-level (WP01)
T002 `PreconditionException` and `EntityTags` in the kernel (WP01)
T003 Method-aware interceptor in `deployable` (WP01)
T004 Refusal body members `violations` and `requiredBy` (WP01)
T005 Share the body guard (WP01)
T006 The architecture test follows the moves (WP01)
T007 No-behaviour-change evidence (WP01)

- **Risks:**
  - a silent 501 regression;
  - `If-Match` parsing drift;
  - a clash between the kernel and generated DTO simple names in `RefusalHandler` (J8).

### WP02 — packs rooted compose (IC-02)

- **Goal:** compose only the closure of an activation's packs; `/offering` is unchanged.
- **Prompt:** [tasks/WP02-packs-rooted-compose.md](tasks/WP02-packs-rooted-compose.md)
- **Requirements:** FR-009, FR-020, SC-004, NFR-001, C-002
- **Dependencies:** none (parallel with WP01)

T008 Red tests for the rooted closure (WP02)
T009 Public `compositionClosure` (WP02)
T010 `resolveRooted` (WP02)
T011 `composed(roots)`, with facts only for the closure and duplicates kept (WP02)
T012 The offering ratchet (WP02)

- **Risks:**
  - the parent edge changing the offering order;
  - refusal-detail drift;
  - duplicate ids lost in the tolerant read (J7).

### WP03 — packs mutability policy file (IC-03a)

- **Goal:** packs declare locks in `policy.yaml`; the highest level along the closure wins; ADR-0061; a contract
  fragment; the file is digested.
- **Prompt:** [tasks/WP03-packs-mutability-policy-file.md](tasks/WP03-packs-mutability-policy-file.md)
- **Requirements:** FR-022, FR-007, C-005, NFR-001
- **Dependencies:** depends on WP02. Gated on #377 (P3).

T013 Schemas and the file-kind rule (WP03)
T014 The descriptor path and the reader (WP03)
T015 `PolicyYaml` (WP03)
T016 Per-pack read; fail closed (WP03)
T017 `PerPackPolicy.levelIn` (WP03)
T018 The write gate shares it (WP03)
T019 ADR-0061 (WP03)
T094 Contract fragment for the pack-schema change (WP03)
T095 Digest `policy.yaml` (WP03)

- **Risks:**
  - the `const: policy.yaml` decision;
  - one compose per registration;
  - churn from #377 and #390.

### WP04 — packs ClosureQueries snapshot (IC-03b)

- **Goal:** everything activations may ask about packs, in one verified read.
- **Prompt:** [tasks/WP04-packs-closure-queries.md](tasks/WP04-packs-closure-queries.md)
- **Requirements:** FR-007, FR-009, FR-010, FR-023, NFR-007, NFR-001
- **Dependencies:** depends on WP02 and WP03

T020 Presets on the facts (WP04)
T021 `RequiredFloor` (WP04)
T022 `ClosureView` (WP04)
T023 `ClosureQueries.snapshot` (WP04)
T024 One read per request (WP04)
T025 Wiring and gates (WP04). It runs the wiring tests and does not edit them; WP07 owns them (P5).

- **Risks:**
  - preset-id collisions across packs;
  - the floor is an assumed default.

### WP05 — activations module skeleton and architecture (IC-04)

- **Goal:**
  - the `activations` module;
  - the moved controller (all operations still reserved), with runtime hints for its mix-ins;
  - the storage **root** (the adapter wiring is WP07's);
  - the ArchUnit gate, generalised with the old rules kept verbatim (P4).
- **Prompt:** [tasks/WP05-activations-module-and-architecture.md](tasks/WP05-activations-module-and-architecture.md)
- **Requirements:** C-002, C-005, FR-019, NFR-001
- **Dependencies:** depends on WP01

T026 The Gradle module (WP05)
T027 Reports and CI lists (WP05)
T028 Move the controller and the JSON mix-in (WP05)
T029 `AvailablePacks` port and adapter stub (WP05)
T030 The storage root from the composition root (WP05)
T031 Generalise `HexagonalArchitectureTest`, `TRANSPORT_STAYS_IN_ADAPTER` widened to `deployable` (WP05)
T032 Bad fixtures (WP05)
T033 The cross-slice adapter rule in the docs (WP05)
T096 `ActivationRuntimeHints` (WP05)

- **Risks:**
  - weakening an old rule;
  - native-image scanning;
  - #391 churn;
  - a #394 mutation floor for the new module.

### WP06 — activation document domain (IC-05)

- **Goal:** the pure document, the revision, the canonicaliser, the snapshot value and the write rules; absent means
  none.
- **Prompt:** [tasks/WP06-activation-document-domain.md](tasks/WP06-activation-document-domain.md)
- **Requirements:** FR-005, FR-006, FR-007, FR-008, FR-010, FR-017, C-002, NFR-001
- **Dependencies:** depends on WP05

T034 Absent means none in the kernel (WP06)
T035 `ActivationId`, `PackRef`, `Revision` (WP06)
T036 `Jcs`, with the §3.2.2.2 vectors, lone surrogates refused and the 2^53 bound (WP06)
T037 `ActivationDocument` (WP06)
T038 `ClosureSnapshot` (WP06)
T039 `DocumentRules` (WP06)
T040 Purity and gates (WP06)

- **Risks:**
  - JCS ordering and escapes;
  - a new violation-code vocabulary;
  - the pin rule is an open item.

### WP07 — storage adapters (IC-06a)

- **Goal:** compare-and-set storage, in memory and on disk, proved concurrent and durable; the adapter wiring in
  `deployable`.
- **Prompt:** [tasks/WP07-activation-storage-adapters.md](tasks/WP07-activation-storage-adapters.md)
- **Requirements:** FR-019, FR-003, NFR-004, NFR-005, SC-003, NFR-001, C-005
- **Dependencies:** depends on WP04 and WP06. WP07 is the single owner of `ProductionWiringTest` and
  `WiringRoundTripTest` (P5).

T041 The port and the record (WP07)
T042 The contract test and the in-memory adapter (WP07)
T043 The file adapter: under Windows, reads take the lock and moves are retried a bounded number of times (WP07)
T044 Concurrency (WP07)
T045 Durability (WP07)
T046 Wiring (WP07)

- **Risks:**
  - `ATOMIC_MOVE` denied on Windows (J6);
  - directory fsync is best-effort;
  - the restart test is costly.

### WP08 — contract A and the red scenarios (IC-06b, contract half)

- **Goal:**
  - contract A, plus a reserved stub so the generated interface compiles;
  - the US1, US2 and US6 scenarios and the edge-case scenarios, failing by design, with their names recorded;
  - the reserved count 13 → 9.
- **Prompt:** [tasks/WP08-contract-a-and-red-scenarios.md](tasks/WP08-contract-a-and-red-scenarios.md)
- **Requirements:** C-001, C-004, FR-008, FR-009, FR-018, FR-021, FR-022, NFR-001, NFR-002, SC-002
- **Dependencies:** depends on WP05 and WP07

T047 Contract A and the stub (WP08)
T048 Red scenarios and the `@WIP` rewrite (WP08)
T049 The reserved pins, 13 → 9 (WP08)

- **Risks:**
  - the schema parity test;
  - scenario state leaking without the clear hook.

### WP09 — CRUD service, controller, body advice and the closure read (IC-06b, service half)

- **Goal:**
  - turn WP08's red list green;
  - one snapshot per request, asserted;
  - the 422 body rules;
  - the 304 path agreeing with Spring's weak comparison;
  - the raised flags;
  - the round-trip property test.
- **Prompt:** [tasks/WP09-activation-crud-service-and-closure-read.md](tasks/WP09-activation-crud-service-and-closure-read.md)
- **Requirements:** FR-001 to FR-007, FR-009, FR-010, FR-017, FR-022, NFR-001, NFR-003, NFR-007, SC-001, SC-002, SC-004
- **Dependencies:** depends on WP08 and WP04. This is PR A's last code WP.

T050 `PacksAvailability` over `ClosureQueries` (WP09)
T051 `ActivationService` (WP09)
T052 The controller, the mapper and conditional requests (WP09)
T053 The #125 build notes (WP09)
T054 The round-trip property test (WP09)
T055 The closure read and US6 end to end (WP09)
T056 Raised flags, all green, evidence (WP09)

- **Risks:**
  - a double 304 path;
  - the cost of the strict re-read for unknown members.

### WP10 — docs A (IC-10, A half)

- **Goal:** the divergences, the ADR amendment notes, the architecture docs and the changelog for PR A.
- **Prompt:** [tasks/WP10-docs-pr-a.md](tasks/WP10-docs-pr-a.md)
- **Requirements:** C-006, C-003, FR-008, FR-007, FR-023, C-001
- **Dependencies:** depends on WP09

T057 DIV-203 to DIV-205 (WP10)
T058 ADR amendment notes (WP10)
T059 Architecture docs (WP10)
T060 Changelog fragment and the #360 re-point (WP10)
T061 Docs gates (WP10)

- **Risks:** overclaiming the effective reads.

### WP16 — Shared web transport and bounded architecture (post-PR-A squad)

- **Goal:**
  - move the service-wide Spring and Jackson transport out of `packs.infra.api` into `service/web-transport`
    (`ai.speckitty.charter.web`);
  - move the activation advices into `activations.infra.api`;
  - drop `TRANSPORT_STAYS_IN_ADAPTER`'s `DEPLOYABLE` exemption;
  - bound the web ring with ArchUnit, and forbid `..domain..` → `kernel.transport..`.

  It is behaviour-preserving.
- **Prompt:** [tasks/WP16-shared-web-transport.md](tasks/WP16-shared-web-transport.md)
- **Requirements:** C-002, C-005, FR-006, NFR-001
- **Dependencies:** depends on WP09

T097 The module (WP16)
T098 `Problems`, `ProblemHandler`, the `Problem` JSON and hints (WP16)
T099 `RefusalHandler` and `JsonBodyGuard` (WP16)
T100 The activation advices move into the slice (WP16)
T101 Behaviour preservation (WP16)
T102 The architecture rules (WP16)
T103 Bad fixtures (WP16)
T104 Evidence and the architecture doc (WP16)

- **Risks:**
  - advice ordering;
  - native hints;
  - #393 and #394 churn.

### WP17 — Activation register correctness fixes (post-PR-A squad)

- **Goal:**
  - preconditions before content;
  - case-safe directory names, and bounded striped locks;
  - a typed unknown root;
  - one preset list for the presets map and the floor;
  - a verified registration gate;
  - service-level races and a non-fakeable atomicity test;
  - the 401/403 scenarios;
  - `RequiringPack.via`;
  - complete 422 bodies.
- **Prompt:** [tasks/WP17-activation-register-correctness-fixes.md](tasks/WP17-activation-register-correctness-fixes.md)
- **Requirements:** FR-001, FR-003, FR-006, FR-007, FR-019, FR-022, NFR-001, NFR-004, NFR-005, NFR-007, C-001, C-002
- **Dependencies:** depends on WP16. WP13 (PR B) depends on it.

T105 Preconditions before content (WP17)
T106 Case-insensitive file systems (WP17)
T107 Bounded locks (WP17)
T108 `UnknownRootException` (WP17)
T109 One preset list (WP17)
T110 The registration gate's verified read (WP17)
T111 Races and atomicity (WP17)
T112 The guards feature (WP17)
T113 `RequiringPack.via` (WP17)
T114 The 422 body NOTEs (WP17)

- **Risks:**
  - the storage layout change;
  - the `via` endpoint semantics;
  - one extra file read per conditional write.

## PR B

### WP11 — packs typed edges and reach (IC-07)

- **Goal:**
  - one traversal (`GraphWalk`) over typed edges, with `<pack>::` followed;
  - lineage as a separate walk;
  - the both-ways, transitive `enhances` pull (`enhancesPull`), and the one-hop `enhancersOf`.
- **Prompt:** [tasks/WP11-packs-typed-edges-and-reach.md](tasks/WP11-packs-typed-edges-and-reach.md)
- **Requirements:** FR-012 to FR-015, NFR-006, NFR-007, C-002, NFR-001
- **Dependencies:** depends on WP09 (PR A's last code WP). Gated on #377 (P3).

T062 Typed edges on the facts (WP11)
T063 `EdgeIndex` (WP11)
T064 `Reachability.reach`: lineage walk, and visits keyed by (pack, URN) (WP11)
T065 `enhancesPull` and `enhancersOf` (WP11)
T066 Expose on the view (WP11)
T067 The 500-artifact timing (WP11)

- **Risks:**
  - multiple predecessors;
  - an ancestor that is also a direct target (J4);
  - any edit to `GraphWalk`.

### WP12 — cascade rules (IC-08), domain only

- **Goal:** the policy on top of a reach result: scope, stop at deny, behind-deny, lineage, clearing (walk ∪ pull)
  and shared.
- **Prompt:** [tasks/WP12-cascade-rules-domain.md](tasks/WP12-cascade-rules-domain.md)
- **Requirements:** FR-012 to FR-015, C-002, NFR-001
- **Dependencies:** depends on WP11

T068 Domain values (WP12)
T069 `CascadeScope` (WP12)
T070 The select delta and report (WP12)
T071 `unreachedBehindDeactivated` (WP12)
T072 Lineage (WP12)
T073 `includeDeactivated` clearing, with the E1 → E2 → T chain and a selected enhancer of a denied target (WP12)
T074 Deactivate `skippedShared`, where E2 is the active enhancer (WP12)

- **Risks:** building to the superseded 13:15 decisions.

### WP13 — select, unselect, deactivate, reactivate (IC-09a)

- **Goal:** the four per-artifact writes, scenarios first; 9 → 5 reserved; write-time `skipped` only (R5).
- **Prompt:** [tasks/WP13-select-and-deactivate-writes.md](tasks/WP13-select-and-deactivate-writes.md)
- **Requirements:** FR-007, FR-010 to FR-015, C-001, NFR-001, NFR-002, NFR-007, SC-001, SC-002
- **Dependencies:** depends on WP12 and WP17 (the post-PR-A correctness fixes)

T075 Contract B, part one; the R5 deferral recorded (WP13)
T076 Red scenarios: US3 AS3 with E denied, US4 AS2 with E2 selected, the cross-pack follow (WP13)
T077 The reserved pins, 9 → 5 (WP13)
T078 The session port (WP13)
T079 `ActivationSelectionService` (WP13)
T080 `ActivationExclusionService` (WP13)
T081 The controller and the change mapper (WP13)
T082 Refusal flags and coverage (WP13)
T083 Evidence (WP13)

- **Risks:**
  - fixture weight;
  - the no-op check.

### WP14 — apply a preset (IC-09b)

- **Goal:** the preset write, scenarios first, including the `missionTypes` replacement; 5 → 4 reserved; the binding
  pin retired.
- **Prompt:** [tasks/WP14-apply-preset.md](tasks/WP14-apply-preset.md)
- **Requirements:** FR-016, FR-010, C-001, NFR-001, NFR-002, NFR-007, SC-001, SC-002
- **Dependencies:** depends on WP13

T084 Contract B, part two (WP14)
T085 Red scenarios (WP14)
T086 Retire the binding pin (WP14)
T087 `PresetRules` (WP14)
T088 `ActivationPresetService` and the controller (WP14)
T089 Evidence (WP14)

- **Risks:** a preset's absent versus empty key (it may need a ruling).

### WP15 — docs B (IC-10, B half)

- **Goal:** the divergences and architecture docs for the cascade, the changelog, and the PR B notes.
- **Prompt:** [tasks/WP15-docs-pr-b.md](tasks/WP15-docs-pr-b.md)
- **Requirements:** C-006, FR-013, FR-014, FR-015
- **Dependencies:** depends on WP14 and WP10 (A2: it edits WP10-owned files out of map). It touches no WP10-owned
  file except its listed out-of-map edits (P1).

T090 DIV-206 and DIV-207 (WP15)
T091 Architecture docs for the cascade (WP15)
T092 Changelog fragment (WP15)
T093 PR description notes and docs gates (WP15)

- **Risks:** describing the superseded decisions.

## Orchestrator closeout (not a WP)

These steps are the orchestrator's; no WP owns them:

1. **Squads after each phase.** Run the post-phase adversarial squad after each PR's WPs are approved (PR A after
   WP10, PR B after WP15), and fold its findings into the artifacts.
2. **Pre-PR squad.** Run one before each pull request opens.
3. **Per-PR gate.** Run `./gradlew verifyAll` once per PR, on the replayed branch. No WP runs it
   (NO_FULL_HEAVY_SUITES_IN_MISSION).
4. **History replay.** Build PR A by replaying WP01–WP10, WP16 and WP17's commits onto `main`. Build PR B by stacking WP11–WP15's
   commits on PR A (see "the cut").
5. **Operator approval.** Each PR opens only with the operator's approval. Flag the open review items in each PR
   description: the closure-scoped floor; refusing a differing pin; ADR-0059 open questions 1 and 4; and the
   preset "listed key" rule.

## Parallelization

- **In parallel:** WP01 and WP02.
- **Joins:**
  - WP03 follows WP02, and WP05 follows WP01;
  - WP04 joins WP02 and WP03;
  - WP06 follows WP05, and WP07 joins WP04 and WP06;
  - WP08 joins WP05 and WP07, and WP09 follows WP08.
- **After WP09:**
  - WP10 (docs), then WP16, then WP17, all in PR A;
  - PR B's WP11 and WP12 hang off WP09, so they can run beside WP16 and WP17;
  - WP13 joins WP12 and WP17, then WP14 and WP15 follow.

## Shared files (one owner each)

The first WP to touch a shared file owns it. Later WPs list their edits under "Out-of-map edits (sequenced by
dependency)".

| File | Owner | Later edits |
|---|---|---|
| `HexagonalArchitectureTest.java` | WP01 | WP05, WP16 |
| `hexagonal-service.md` | WP05 | WP16 (the web ring; a recorded decision, no ADR) |
| `activations/infra/api/ActivationController.java` | WP05 | WP08 (stub), WP09, WP16 (imports), WP13, WP14 |
| `AvailablePacks.java`, `PacksAvailability.java` | WP05 | WP09, WP17, WP13 |
| `PackCompositionFacts.java`, `DomainRepositoryService.java`, `DomainRepositoryServiceTest.java` | WP03 | WP04, WP11 |
| `ClosureView.java`, `ClosureQueries.java`, `ClosureQueriesTest.java` | WP04 | WP17, then WP11 (A4: WP17 lands first; WP11's review waits for WP17's approval and rebases over it; WP11's commits on these files go only into PR B) |
| `OfferingReadService.java`, `OfferingReadServiceTest.java` | WP02 | WP04 |
| `ProductionWiringTest.java`, `WiringRoundTripTest.java` | WP07 | none (WP04 only runs them) |
| `ClosureSnapshot.java`, `DocumentRules.java`, `DocumentRulesTest.java` | WP06 | WP09, WP17 |
| `activations.feature`, `ReservedOperationCoverageTest.java`, `ActivationSteps.java`, the selections and exclusions path files | WP08 | WP13, WP14 |
| `CharterRefusalCode.yaml`, `ReservedRefusalCodesTest.java`, `FakeAvailablePacks.java`, `PacksAvailabilityTest.java` | WP09 | WP16 (`HANDLER_CLASS`), WP17, WP13, WP14 |
| `ActivationService.java`, `ActivationServiceTest.java` | WP09 | WP17 |
| `deployable/ActivationBodyAdvice.java`, `ActivationProblems.java`, `ActivationBodyAdviceTest.java` | WP09 | WP16 (moved to `activations.infra.api`, where WP16 owns them; WP17 edits) |
| `packs/infra/api/RefusalHandler.java`, `JsonBodyGuard.java`, `RefusalHandlerTest.java`, `RegistrationListsTest.java` | WP01 | WP16 (moved to `ai.speckitty.charter.web`) |
| `settings.gradle.kts`, `charter-reports/build.gradle.kts`, `sonar.yml`, `activations/build.gradle.kts`, `deployable/build.gradle.kts`, `DeployableConfiguration.java`, `ActivationJson.java` | WP05 | WP16 |
| `FileActivationRepository.java`, `FileActivationRepositoryConcurrencyTest.java`, `FileActivationRepositoryTest.java` | WP07 | WP17 |
| `PackMutabilityPolicy.java`, `PackMutabilityPolicyTest.java` (on lane-i under `application/`) | WP03 | WP17 |
| `ActivationWrites.java` | WP13 | WP14 |
| `divergence-register.md`, `divergences.yaml`, `resolution-model.md`, `0013-persistent-deactivation.md` | WP10 | WP15 |
| `offering-and-activation.md` | WP10 | WP16 (flowchart and module table), WP17 (precondition order, storage layout), WP15 |

## MVP

PR A alone (WP01 to WP10) lets core store, read, replace and delete a project's activation and read its closure
(US1, US2, US6).

## Squad dispositions (post-tasks)

Every finding has exactly one disposition: **accepted** (the artifact changed, and the location is given),
**changed** (the concern was resolved another way) or **deferred** (valid, with a reason).

| # | Finding | Disposition | Where |
|---|---|---|---|
| P1 | State the PR A/B cut; WP15 must not touch WP10's files | accepted | tasks.md "Two pull requests, and the cut"; WP15 "Out-of-map edits" |
| P2 | Split the old WP08 into contract + red (WP08) and service + green (WP09); renumber the rest +1 | accepted | WP08, WP09 files; old WP09–WP14 renamed WP10–WP15; ids T001–T093 kept; dependencies updated (WP10 → WP09, WP11 → WP09) |
| P3 | #377 gate for WP01, WP03 and the edges WP; `packs` edits import and call site only; evidence | accepted | WP01 Context, Branch Strategy, Evidence; WP03 Collisions; WP11 Collisions |
| P4 | Split the ArchUnit generalisation out of WP05 | deferred (the split): WP05 has 9 subtasks, an acceptable size. Changed: old rules kept verbatim, plus `assertBites` on the existing fixtures | WP05 T031 "Preservation rule", Reviewer guidance |
| P5 | One owner for the wiring tests | accepted | WP07 owns both; WP07 depends on WP04; WP04 T025 only runs them |
| P6 | WP05's storage wiring wording | accepted | WP05 Objective, T028 step 5, T030 step 3 |
| R1 | `includeDeactivated` clears what the pull would reach: both ways, transitive | accepted | spec.md FR-013; WP11 T065 (`enhancesPull`); WP12 Objective, T070 API, T073 cases 3a–3c; WP13 T078/T079; WP15 table and DIV-207 |
| R2 | US3 AS3 fixture denies E | accepted | WP13 T076 |
| R3 | A contract fragment for the pack-schema change | accepted | WP03 T094 (`contracts/charter/changes/`; the pack-schema history lives in `contracts/charter/CHANGELOG.md`) |
| R4 | One rule each for an unknown pack: on write 422, on the closure read 500 | accepted | WP09 T050; WP08 T048 scenarios |
| R5 | `ActivationChange.skipped` versus the read-time pull | deferred (read-time reporting belongs to the effective-reads mission); recorded | WP13 T075 (fragment, and the `ActivationChange.yaml` description) |
| R6 | Fixture naming E and E2; E2 selected in US4 AS2 | accepted | WP12 T074 case 2; WP13 T076 and Reviewer guidance |
| R7 | Missing scenarios | accepted | WP08 T048 (stored deny now locked; outside-closure `<pack>::` 500; GET unknown 404; schema-invalid policy); WP13 T076 (cross-pack follow); WP14 T085 (`missionTypes` replacement) |
| R8 | The fake counts snapshot calls; one per request asserted | accepted | WP09 T051 |
| R9 | `requirement_refs` gaps | accepted | C-004 in WP08; C-003 in WP10; NFR-001 added to WP08, WP09, WP13 and WP14 |
| J1 | Typed `JsonBodyGuard` API with a refusal factory; public `NullValue` | accepted | WP01 T005; WP09 T053 (`body_too_large`, `null_value`) |
| J2 | `TRANSPORT_STAYS_IN_ADAPTER` allows `deployable` | accepted | WP05 T031 step 3; fixture `ApplicationNamesGeneratedApi` (T032) |
| J3 | Digest `policy.yaml` in `packLevelTail` | accepted | WP03 T095 |
| J4 | Lineage as a separate walk; the ancestor-and-direct-target test; per-pack identity | accepted | WP11 T064 (cases 9–10, Implementation) |
| J5 | `ActivationRuntimeHints` | accepted | WP05 T096 |
| J6 | Windows `ATOMIC_MOVE` | accepted | WP07 T043 (reads under the lock, bounded retry, best-effort directory fsync) |
| J7 | Duplicates kept in the tolerant read | accepted | WP02 T011 |
| J8 | Kernel and generated DTO name clash in `RefusalHandler` | accepted | WP01 T004 |
| J9 | JCS vectors from §3.2.2.2; lone surrogates and the 2^53 bound | accepted | WP06 T036, Reviewer guidance, DoD |
| J10 | Controller 304 versus Spring's weak compare; a non-`*` `If-None-Match` on PUT | accepted | WP08 T047 step 6 and T048; WP09 T051, T052 (`ActivationConditionalRequestTest`) |

## Analyze dispositions

| # | Finding | Disposition | Where |
|---|---|---|---|
| A1 | `applied` follows the contract: every id added to or removed from `selected`, `deactivated` or `missionTypes`; `cleared` lists the removed deny entries; kinds never appear; a no-op has both empty | accepted | spec US3 AS5, US4 AS1, US4 AS4, US5 AS1, FR-011, FR-015, FR-016; WP12 T068 and T070 (one `ActivationChange.of(before, after, …)`), T074; WP13 T076, T079, T080; WP14 Objective, T085, T087 |
| A2 | WP15 must depend on WP10 | accepted | WP15 frontmatter and Branch Strategy; tasks.md WP15 entry |
| A3 | The base is `main` (#368 and #376 merged); rebase over the open PRs | accepted | spec C-004, NFR-001 and Context; plan header, Constraints and Rebase order |
| A5 | An orchestrator closeout note | accepted | tasks.md "Orchestrator closeout (not a WP)" |
| A6 | The superseded 13:15 decisions are recorded in research | changed (a disposition row, B1b) | research.md "Squad dispositions" |
| A7 | A preset key with an empty list counts as not listed | accepted (an assumed default, logged) | spec FR-016; research.md open review item 4; WP14 Grounding, T085, T087, DoD |
| Low | Research R9 count; "14 pins" wording; name drift (spec US4 AS2 E → E2) | accepted | research R9 (13 → 9 → 5 → 4); WP01 and WP05 ("13 operations plus the binding pin"); spec US4 AS2 |

## Analyze dispositions (second pass)

| # | Finding | Disposition | Where |
|---|---|---|---|
| A1 | A cleared enhancer is in `cleared` **and** `applied` (removed from `deactivated`), never in `selected` | accepted | spec US3 AS3; WP12 T073 cases 2, 3a, 3b; WP13 T075 fragment, T076, Reviewer guidance |
| A2 | `data-model.md` `applied` follows the contract rule | accepted | data-model.md ActivationChange |
| A3 | Contract A raises four codes | accepted | plan.md IC-06 |
| A4 | Pin counts: 15 `@reserved` scenarios today (`select` pinned twice, plus the binding pin). Operations go 13 → 9 → 5 → 4; scenarios go 15 → 11 → 6 → 4 | accepted | WP01, WP05, WP08 T049 and Grounding, WP13 T077 and out-of-map, WP14 T086 |
| A5 | `closureOf` renamed `compositionClosure`; WP14's conditional "listed" flag edit dropped | accepted | data-model.md, plan.md, research.md R2; WP14 out-of-map; the shared-files table |
| A6 | "Nine writes" becomes "the nine operations #125 reserved (eight writes and `getActivation`)" | accepted | spec Input, Context, NFR-002; WP15 T093 |
| A7 | The policy path is pack-relative, constrained to `policy.yaml` for now | accepted | spec FR-022; research R6 |
| A8 | 428 and the path-kind 422 in the requirements | accepted | spec FR-010 |
| A9 | FR-020 is paired with US2 AS1 and AS2 | accepted | spec FR-020 |
| A10 | `enhances` at write in C-006 | accepted | spec C-006 |
| A11 | Check the free ADR number at write time | accepted | WP03 T019 step 1 |
| A12 | The foreign NFR-002 id is named as such | accepted | WP03 T095 |
| A13 | Index formatting and a note for T094–T096 | accepted | tasks.md Subtask Index |
| A14 | Deny entries survive every write that does not explicitly clear them | accepted | spec US4 "Why this priority" |

## PR A squad dispositions

The post-PR-A squad ran on lane-i (WP01–WP09 combined). Every finding has one disposition.

| Delegate | Finding | Disposition | Where |
|---|---|---|---|
| alphonso | MAJOR: the shared transport is owned by `packs.infra.api`, and `deployable` holds activation transport (the exemption) | accepted | WP16 (T097–T104) |
| alphonso | MINOR: `PacksAvailability` catches every `IllegalArgumentException` | accepted | WP17 T108 (`UnknownRootException`) |
| alphonso | MINOR: presets split-brain (the floor uses every copy, the map the last) | accepted | WP17 T109 |
| alphonso | MINOR: the policy gate reads outside a verified compose | accepted | WP17 T110 (`composedWithFacts`) |
| alphonso | MINOR: no rule keeps `kernel.transport` out of the domain | accepted | WP16 T102 step 6, T103 (`DomainNamesKernelTransport`) |
| alphonso | NOTE: `RequiringPack.via` is always empty | accepted | WP17 T113 (from the `parent_pack` chain, or documented, with a fragment) |
| alphonso | NOTE: presets are copied per pack and per map | accepted | WP17 T109 (one de-duplicated list; the map is a view) |
| renata | MINOR: preconditions are judged after content (422 instead of 412) | accepted | WP17 T105, T112; docs out-of-map in `offering-and-activation.md` |
| renata | MINOR: case-insensitive file systems alias ids | accepted | WP17 T106 (reversible encoding) |
| renata | MINOR: the lock map grows unbounded | accepted | WP17 T107 (striped) |
| renata | MINOR: `via` is not populated | accepted | WP17 T113 |
| renata | MINOR: `PacksAvailability` catch is too broad | accepted | WP17 T108 |
| renata | MINOR: fakeable readers-racing-writers test; no service-level races | accepted | WP17 T111 |
| renata | NOTE: a 422 can carry empty `violations` | accepted | WP17 T114 step 1 |
| renata | NOTE: only the first unknown member is reported | accepted (report all; documenting "first only" is the fallback) | WP17 T114 step 2 |
| renata | NOTE: no 401/403 scenario for `/activations` | accepted | WP17 T112 |

## Analyze dispositions (fourth pass)

| # | Finding | Disposition | Where |
|---|---|---|---|
| A1 | Every PR A replay or prefix statement names WP01–WP10, WP16 and WP17 | accepted | tasks.md "The cut", Orchestrator closeout step 4; WP15 P1 note, Branch Strategy |
| A2 | plan.md: the `web-transport` module, two new modules, the IC-01 note, IC-11 and IC-12, the Revision line | accepted | plan.md |
| A3 | Encoded, case-safe storage path | accepted | spec FR-019; plan Storage; research R4 |
| A4 | WP17 and WP11 ordering on `ClosureQueries`/`ClosureView`/`ClosureQueriesTest` (option b) | accepted | tasks.md shared-files table; WP11 and WP17 out-of-map sections |
| A5 | `hexagonal-service.md` records the web ring (no ADR) | accepted | WP16 out-of-map, T104 step 5 |
| A6 | WP16 depends on WP10 | accepted | WP16 frontmatter, Branch Strategy |
| A7 | `ActivationBodyAdvice` location in WP13 and WP14 | accepted | WP13 Grounding; WP14 Grounding |
| A8 | Preconditions before content in the requirements | accepted | spec FR-010 |
| A9 | `RequiringPack.via` in the contract delta | accepted | contracts/contract-delta.md PR A |
| A10 | A duplicated preset id: the last holder wins and the floor follows it | accepted | spec Assumptions; WP04 T021 annotation |
| A11 | The index note names WP16 and WP17 ranges | accepted | tasks.md Subtask Index note |
| A12 | `const: policy.yaml` as implemented; the stray `)` in FR-022 | accepted | WP03 T013 step 4, T095 step 3; spec FR-022 |

