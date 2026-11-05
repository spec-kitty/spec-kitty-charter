# Tasks: Activation register and activated-pack closure

**Mission**: `activation-register-and-closure-01M6NXBD`
**Branch**: planned on `issue-360-activated-closure` (cut from PR #368's branch); completed work merges into
`issue-360-activated-closure`, which reaches `main` as two stacked pull requests.
**Inputs**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md),
[contracts/contract-delta.md](contracts/contract-delta.md), [decisions/](decisions/)

**Rulings that govern:** ADR-0020 D2–D9 and its amendment; ADR-0013 D2–D5; ADR-0059 as revised on PR #376 on
2026-11-05. D2.2: `enhances` is pulled at read, both ways, and never written into `selected`; the only write-time uses
are `includeDeactivated` deny-clearing and deactivate's shared check. D2.4: lineage passes `requires` and `scope`
only. These supersede the 13:15 discovery decisions on the pull and on lineage.

## Two pull requests

- **PR A = WP01–WP09.** Shared plumbing, the rooted compose and the closure read, the policy file (ADR-0061), the
  floor, the slice and its storage, CRUD, contract A, and the docs for A. It names PR #376 (ADR-0059) as a
  merge-before dependency.
- **PR B = WP10–WP14.** Typed edges and reach, the cascade rules, the five per-artifact and preset writes, contract B,
  and the docs for B. Every PR B code WP depends on WP08 (PR A's last code WP), directly or transitively, so PR B starts only once PR A's code work
  is complete.
- **Rebase order:** PR #368 (base) → #376 → #377 / #390 / #391 / #393 as they merge → PR A → PR B. Each WP rebases
  onto its lane base before review. The reviewer is `reviewer-renata`.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | `@ReservedOperation` in `kernel.transport`, TYPE and METHOD targets | WP01 | |
| T002 | `PreconditionException` and `EntityTags` (strong `If-Match`, weak `If-None-Match`) in the kernel; packs callers updated | WP01 | |
| T003 | `ReservedOperationInterceptor` in `deployable`, method-aware, with its unit test | WP01 | |
| T004 | Kernel `CharterRefusal` gains `violations` and `requiredBy`; `RefusalHandler` renders them | WP01 | [P] |
| T005 | Share the body guard: kernel `BodyLimit`, public `JsonBodyGuard`; registration advices use it | WP01 | [P] |
| T006 | `HexagonalArchitectureTest` and the bad fixture follow the moves | WP01 | |
| T007 | No-behaviour-change evidence and module gates | WP01 | |
| T008 | Red tests for the rooted closure (`PackCompositionResolverTest`) | WP02 | |
| T009 | Public `compositionClosure` (`depends_on` + `parent_pack`, tolerant); `checkReferences` keeps the `depends_on`-only closure | WP02 | |
| T010 | `resolveRooted(available, roots)`: closure first, then the existing checks | WP02 | |
| T011 | `OfferingReadService.composed(roots)`: tolerant descriptors, facts only for the closure; verified `closure(roots)` | WP02 | |
| T012 | The offering ratchet: `composed()` equals `composed(all)`; offering and composition scenarios unchanged | WP02 | |
| T013 | `policy.schema.json`, descriptor `mutability_policy`, the `file-kinds.yaml` rule | WP03 | [P] |
| T014 | `PackPaths.mutabilityPolicy` and the descriptor reader | WP03 | [P] |
| T015 | `PolicyYaml` reader into `MutabilityPolicySource` | WP03 | |
| T016 | Read the policy per pack on the facts; a missing or invalid file is `pack_unreadable`; `MutabilityPolicyRepository` port | WP03 | |
| T017 | `PerPackPolicy.levelIn(closure, urn)`: the highest level and the setting pack | WP03 | [P] |
| T018 | `PackMutabilityPolicy` write gate shares `levelIn` over the pack's closure | WP03 | |
| T019 | ADR-0061 and the open-packs note | WP03 | [P] |
| T020 | Presets on `PackCompositionFacts` from the already-assembled pack | WP04 | |
| T021 | `RequiredFloor` (pure domain): governing presets' must-have items with the requiring pack | WP04 | [P] |
| T022 | `ClosureView`: packs, held ids, holders, mission types, floor, locks, presets, fingerprint | WP04 | |
| T023 | `ClosureQueries.snapshot(roots)` in one `ConsistentRead` | WP04 | |
| T024 | One read per request: stale answers `stale_input`; facts read once per pack | WP04 | |
| T025 | Wiring and gates | WP04 | |
| T026 | The `service/activations` Gradle module and package skeleton | WP05 | |
| T027 | `charter-reports` and `sonar.yml` module lists | WP05 | [P] |
| T028 | Move `ActivationController` (method-level reserved marks) and `ActivationJson` to `activations.infra.api`; deployable scans | WP05 | |
| T029 | `AvailablePacks` port (no method yet) and `PacksAvailability` adapter stub | WP05 | [P] |
| T030 | `ActivationStorageRoot` in `deployable` from `ConfigRoot.activations()`, in-memory when none | WP05 | [P] |
| T031 | Generalise `HexagonalArchitectureTest` (#140): slices, per-slice rules, generated types, adapter-only `packs`, method-scoped reserved rule | WP05 | |
| T032 | Bad fixtures for the activations slice, each with `assertBites` | WP05 | |
| T033 | `hexagonal-service.md`: the cross-slice adapter rule | WP05 | [P] |
| T034 | Kernel `ArtifactKind.InForceWhenAbsent` → `NONE`, all users updated | WP06 | [P] |
| T035 | `ActivationId`, `PackRef`, `Revision` | WP06 | |
| T036 | `Jcs` canonicaliser (RFC 8785 subset) over the domain value tree, with pinned vectors | WP06 | [P] |
| T037 | `ActivationDocument`: three-state lists, canonical form, revision | WP06 | |
| T038 | `ClosureSnapshot` value and the `ClosureSnapshots` test builder | WP06 | |
| T039 | `DocumentRules`: 422 violations and 409 floor refusals | WP06 | |
| T040 | Purity and gates | WP06 | |
| T041 | `ActivationRepository` port and `StoredActivation` | WP07 | |
| T042 | Shared repository contract test; `InMemoryActivationRepository` | WP07 | |
| T043 | `FileActivationRepository`: temp + fsync + `ATOMIC_MOVE`, per-id lock, `Rename` seam; `ActivationFileCodec` | WP07 | |
| T044 | 8-writer concurrency, 100 of 100 (NFR-004) | WP07 | [P] |
| T045 | Injected failure and context restart, byte-identical (NFR-005, SC-003) | WP07 | [P] |
| T046 | Adapter choice wired in `deployable` | WP07 | |
| T047 | Contract A: `listActivationPacks`, `ActivationPackList`, `summary` and fingerprint moves, `304`, absent-means-none texts, `x-reserved` off CRUD, raised flags, fragment | WP08 | |
| T048 | Red scenarios `activation-register.feature` (US1, US2, US6), `ActivationSteps`, the `@WIP` composition rewrite | WP08 | |
| T049 | Reserved pins 13 → 9; the binding pin moves to `selectActivationArtifact`; coverage scans `@reserved` blocks only; refusal-code test | WP08 | |
| T050 | `PacksAvailability` over `ClosureQueries` | WP08 | |
| T051 | `ActivationService`: create, put (`If-None-Match: *`, `If-Match`, LWW, both validators 400), get (`304`), delete, packs | WP08 | |
| T052 | Controller methods and `ActivationMapper` | WP08 | |
| T053 | #125 build notes: `ActivationBodyAdvice` and `ActivationProblems` (422 `activation_invalid`); kind parsing | WP08 | |
| T054 | Round-trip property test over ≥ 1,000 documents (NFR-003) | WP08 | [P] |
| T055 | Closure read and US6 end to end | WP08 | |
| T056 | All green, and red-to-green evidence | WP08 | |
| T057 | Divergences DIV-203 to DIV-205 in both register forms | WP09 | [P] |
| T058 | Amendment notes: ADR-0020 D6, ADR-0013 D2, ADR-0012 D2 → ADR-0061 | WP09 | [P] |
| T059 | `offering-and-activation.md`, `logical-model.md`, `resolution-model.md` | WP09 | |
| T060 | PR A docs changelog fragment with the #360 re-point | WP09 | |
| T061 | Docs gates | WP09 | |
| T062 | Typed edges (`Relationship`) on `PackCompositionFacts` from graphs, components and shards | WP10 | |
| T063 | `EdgeIndex`: an edge-backed resolver over the closure, with `<pack>::` through the compose rule | WP10 | |
| T064 | `Reachability.reach` on `GraphWalk`: reached and stopped with every `Via`, behind-stopped, lineage | WP10 | |
| T065 | `enhancersOf` (reverse `enhances`) | WP10 | [P] |
| T066 | Expose `reach` and `enhancersOf` on `ClosureView`, with no extra read | WP10 | |
| T067 | The 500-artifact timing, report-only (NFR-006) | WP10 | [P] |
| T068 | Domain values: `ArtifactRef`, `Reach`, `ReachPlan`, `SkippedReport`, `ActivationChange` | WP11 | |
| T069 | `CascadeScope` and the `["all"]`-mix refusal | WP11 | [P] |
| T070 | Select delta and report: scope, stop at deny (`viaRequires`, `requiredBy`) | WP11 | |
| T071 | `unreachedBehindDeactivated` | WP11 | |
| T072 | Lineage: `requires` and `scope` only, transitive, a denied ancestor stops it | WP11 | |
| T073 | `includeDeactivated` clearing, including enhancers' deny entries; never selects an enhancer | WP11 | |
| T074 | Deactivate `skippedShared`: the active set is selected ∪ floor; active enhancers count | WP11 | |
| T075 | Contract B part one: selections and exclusions built; two codes raised; fragment | WP12 | |
| T076 | Red scenarios `activation-selections.feature` (US3, US4) | WP12 | |
| T077 | Reserved pins 9 → 5; the binding pin moves to the preset operation | WP12 | |
| T078 | `PacksSession`: one closure view per request for snapshot, reach and enhancers | WP12 | |
| T079 | `ActivationSelectionService` (select, unselect, `mission_type` → `missionTypes`) | WP12 | |
| T080 | `ActivationExclusionService` (deactivate with the floor 409, reactivate) | WP12 | |
| T081 | Controller methods and `ActivationChangeMapper` | WP12 | |
| T082 | Refusal flags and operation coverage | WP12 | |
| T083 | All green, and evidence | WP12 | |
| T084 | Contract B part two: presets built; `preset_not_found` raised; closing fragment | WP13 | |
| T085 | Red scenarios `activation-presets.feature` (US5) | WP13 | |
| T086 | Retire the binding pin; 5 → 4; `noReservedOperationTakesABody` | WP13 | |
| T087 | `PresetRules`: replace listed keys, `force` clears | WP13 | |
| T088 | `ActivationPresetService` and the controller | WP13 | |
| T089 | All green, and evidence | WP13 | |
| T090 | Divergences DIV-206 (lineage) and DIV-207 (`enhances` at write) | WP14 | [P] |
| T091 | Architecture docs for the cascade | WP14 | |
| T092 | PR B docs changelog fragment | WP14 | [P] |
| T093 | #291 and #360 PR-description notes; docs gates | WP14 | |

## PR A

### WP01 — Shared transport plumbing (IC-01)

- **Goal:** move the reserved marker, the preconditions, the refusal body members and the body guard to homes a second
  HTTP slice can reach, with no behaviour change.
- **Prompt:** [tasks/WP01-shared-transport-plumbing.md](tasks/WP01-shared-transport-plumbing.md)
- **Requirements:** C-002, C-005, FR-006, FR-007, NFR-001
- **Dependencies:** none

T001 `@ReservedOperation` in the kernel, method- and type-level (WP01)
T002 `PreconditionException` and `EntityTags` in the kernel (WP01)
T003 Method-aware interceptor in `deployable` (WP01)
T004 Refusal body members `violations` and `requiredBy` (WP01)
T005 Share the body guard (WP01)
T006 The architecture test follows the moves (WP01)
T007 No-behaviour-change evidence (WP01)

- **Risks:** a silent 501 regression if the interceptor is not scanned; `If-Match` parsing drift; a rebase with #377
  on `PackCommitStore`.

### WP02 — packs rooted compose (IC-02)

- **Goal:** compose only the closure of an activation's packs; `/offering` unchanged.
- **Prompt:** [tasks/WP02-packs-rooted-compose.md](tasks/WP02-packs-rooted-compose.md)
- **Requirements:** FR-009, FR-020, SC-004, NFR-001, C-002
- **Dependencies:** none (parallel with WP01)

T008 Red tests for the rooted closure (WP02)
T009 Public `compositionClosure` (WP02)
T010 `resolveRooted` (WP02)
T011 `composed(roots)`, with facts only for the closure (WP02)
T012 The offering ratchet (WP02)

- **Risks:** the parent edge changing the offering order; refusal-detail drift between `resolve` and `resolveRooted`.

### WP03 — packs mutability policy file (IC-03a)

- **Goal:** packs declare locks in `policy.yaml`; the highest level along the closure wins; ADR-0061.
- **Prompt:** [tasks/WP03-packs-mutability-policy-file.md](tasks/WP03-packs-mutability-policy-file.md)
- **Requirements:** FR-022, FR-007, C-005, NFR-001
- **Dependencies:** Depends on WP02

T013 Schemas and the file-kind rule (WP03)
T014 The descriptor path and the reader (WP03)
T015 `PolicyYaml` (WP03)
T016 Per-pack read; fail closed (WP03)
T017 `PerPackPolicy.levelIn` (WP03)
T018 The write gate shares it (WP03)
T019 ADR-0061 (WP03)

- **Risks:** the whole-pack gate and the manifest classification of `policy.yaml` (the `const` decision); one compose
  per registration; churn from #377 and #390.

### WP04 — packs ClosureQueries snapshot (IC-03b)

- **Goal:** everything activations may ask about packs, in one verified read.
- **Prompt:** [tasks/WP04-packs-closure-queries.md](tasks/WP04-packs-closure-queries.md)
- **Requirements:** FR-007, FR-009, FR-010, FR-023, NFR-007, NFR-001
- **Dependencies:** Depends on WP02, WP03

T020 Presets on the facts (WP04)
T021 `RequiredFloor` (WP04)
T022 `ClosureView` (WP04)
T023 `ClosureQueries.snapshot` (WP04)
T024 One read per request (WP04)
T025 Wiring and gates (WP04)

- **Risks:** preset-id collisions across packs (merge order decides); the floor is an assumed default kept in one
  function.

### WP05 — activations module skeleton and architecture (IC-04)

- **Goal:** the `activations` module, the moved controller (all operations still reserved) and the generalised ArchUnit
  gate.
- **Prompt:** [tasks/WP05-activations-module-and-architecture.md](tasks/WP05-activations-module-and-architecture.md)
- **Requirements:** C-002, C-005, FR-019, NFR-001
- **Dependencies:** Depends on WP01

T026 The Gradle module (WP05)
T027 Reports and CI lists (WP05)
T028 Move the controller and the JSON mix-in (WP05)
T029 `AvailablePacks` port and adapter stub (WP05)
T030 The storage root from the composition root (WP05)
T031 Generalise `HexagonalArchitectureTest` (WP05)
T032 Bad fixtures (WP05)
T033 The cross-slice adapter rule in the docs (WP05)

- **Risks:** weakening an old rule while generalising; native-image scanning; churn from #391 and #390.

### WP06 — activation document domain (IC-05)

- **Goal:** the pure document, the revision, the canonicaliser, the snapshot value and the write rules; absent means
  none.
- **Prompt:** [tasks/WP06-activation-document-domain.md](tasks/WP06-activation-document-domain.md)
- **Requirements:** FR-005, FR-006, FR-007, FR-008, FR-010, FR-017, C-002, NFR-001
- **Dependencies:** Depends on WP05

T034 Absent means none in the kernel (WP06)
T035 `ActivationId`, `PackRef`, `Revision` (WP06)
T036 `Jcs` (WP06)
T037 `ActivationDocument` (WP06)
T038 `ClosureSnapshot` (WP06)
T039 `DocumentRules` (WP06)
T040 Purity and gates (WP06)

- **Risks:** JCS key ordering (UTF-16, not code points); a new violation-code vocabulary; the pin rule is an open item.

### WP07 — storage adapters (IC-06a)

- **Goal:** compare-and-set storage, in memory and on disk, proved concurrent and durable.
- **Prompt:** [tasks/WP07-activation-storage-adapters.md](tasks/WP07-activation-storage-adapters.md)
- **Requirements:** FR-019, FR-003, NFR-004, NFR-005, SC-003, NFR-001, C-005
- **Dependencies:** Depends on WP06

T041 The port and the record (WP07)
T042 The contract test and the in-memory adapter (WP07)
T043 The file adapter (WP07)
T044 Concurrency (WP07)
T045 Durability (WP07)
T046 Wiring (WP07)

- **Risks:** directory fsync is not portable; lock-map growth; the restart test boots Spring twice.

### WP08 — contract A, CRUD and the closure read (IC-06b)

- **Goal:** create, read, replace and delete, and the closure read, served from contract A, scenarios first; 13 → 9
  reserved.
- **Prompt:** [tasks/WP08-contract-a-crud-and-closure-read.md](tasks/WP08-contract-a-crud-and-closure-read.md)
- **Requirements:** FR-001 to FR-010, FR-017, FR-018, FR-021, FR-022, C-001, NFR-002, NFR-003, NFR-007, SC-001,
  SC-002, SC-004
- **Dependencies:** Depends on WP04, WP07

T047 Contract A (WP08)
T048 Red scenarios and the `@WIP` rewrite (WP08)
T049 The reserved pins, 13 → 9 (WP08)
T050 `PacksAvailability` over `ClosureQueries` (WP08)
T051 `ActivationService` (WP08)
T052 The controller and the mapper (WP08)
T053 The #125 build notes (WP08)
T054 The round-trip property test (WP08)
T055 The closure read and US6 end to end (WP08)
T056 Evidence (WP08)

- **Risks:** the contract change breaks compilation until the controller implements `listActivationPacks`; the schema
  parity test; scenario state leaking without the per-scenario clear.

### WP09 — docs A (IC-10, A half)

- **Goal:** the divergences, the ADR amendment notes, the architecture docs and the changelog for PR A.
- **Prompt:** [tasks/WP09-docs-pr-a.md](tasks/WP09-docs-pr-a.md)
- **Requirements:** C-006, FR-008, FR-007, FR-023, C-001
- **Dependencies:** Depends on WP08

T057 DIV-203 to DIV-205 (WP09)
T058 ADR amendment notes (WP09)
T059 Architecture docs (WP09)
T060 Changelog fragment and the #360 re-point (WP09)
T061 Docs gates (WP09)

- **Risks:** overclaiming the effective reads; ADR-0059 links before #376 merges.

## PR B

### WP10 — packs typed edges and reach (IC-07)

- **Goal:** one traversal (`GraphWalk`) over typed edges of every graph-authored kind, with `<pack>::`, lineage and
  reverse `enhances`.
- **Prompt:** [tasks/WP10-packs-typed-edges-and-reach.md](tasks/WP10-packs-typed-edges-and-reach.md)
- **Requirements:** FR-012 to FR-015, NFR-006, NFR-007, C-002, NFR-001
- **Dependencies:** Depends on WP08

T062 Typed edges on the facts (WP10)
T063 `EdgeIndex` (WP10)
T064 `Reachability.reach` (WP10)
T065 `enhancersOf` (WP10)
T066 Expose on the view (WP10)
T067 The 500-artifact timing (WP10)

- **Risks:** multiple predecessors versus the visited guard; edge duplication; any edit to `GraphWalk`.

### WP11 — cascade rules (IC-08), domain only

- **Goal:** the policy on top of a reach result: scope, stop at deny, behind-deny, lineage, clearing and shared.
- **Prompt:** [tasks/WP11-cascade-rules-domain.md](tasks/WP11-cascade-rules-domain.md)
- **Requirements:** FR-012 to FR-015, C-002, NFR-001
- **Dependencies:** Depends on WP10

T068 Domain values (WP11)
T069 `CascadeScope` (WP11)
T070 The select delta and report (WP11)
T071 `unreachedBehindDeactivated` (WP11)
T072 Lineage (WP11)
T073 `includeDeactivated` clearing (WP11)
T074 Deactivate `skippedShared` (WP11)

- **Risks:** building to the superseded 13:15 decisions; report ordering.

### WP12 — select, unselect, deactivate, reactivate (IC-09a)

- **Goal:** the four per-artifact writes, scenarios first; 9 → 5 reserved.
- **Prompt:** [tasks/WP12-select-and-deactivate-writes.md](tasks/WP12-select-and-deactivate-writes.md)
- **Requirements:** FR-007, FR-010 to FR-015, C-001, NFR-002, NFR-007, SC-001, SC-002
- **Dependencies:** Depends on WP11

T075 Contract B, part one (WP12)
T076 Red scenarios (WP12)
T077 The reserved pins, 9 → 5 (WP12)
T078 The session port (WP12)
T079 `ActivationSelectionService` (WP12)
T080 `ActivationExclusionService` (WP12)
T081 The controller and the change mapper (WP12)
T082 Refusal flags and coverage (WP12)
T083 Evidence (WP12)

- **Risks:** fixture weight; the no-op check; a floor change mid-request (answers `stale_input`).

### WP13 — apply a preset (IC-09b)

- **Goal:** the preset write, scenarios first; 5 → 4 reserved; the binding pin retired.
- **Prompt:** [tasks/WP13-apply-preset.md](tasks/WP13-apply-preset.md)
- **Requirements:** FR-016, FR-010, C-001, NFR-002, NFR-007, SC-001, SC-002
- **Dependencies:** Depends on WP12

T084 Contract B, part two (WP13)
T085 Red scenarios (WP13)
T086 Retire the binding pin (WP13)
T087 `PresetRules` (WP13)
T088 `ActivationPresetService` and the controller (WP13)
T089 Evidence (WP13)

- **Risks:** a preset's absent versus empty key (may need an operator ruling); preset-id collisions.

### WP14 — docs B (IC-10, B half)

- **Goal:** the divergences and architecture docs for the cascade, the changelog, and the PR B notes.
- **Prompt:** [tasks/WP14-docs-pr-b.md](tasks/WP14-docs-pr-b.md)
- **Requirements:** C-006, FR-013, FR-014, FR-015
- **Dependencies:** Depends on WP13

T090 DIV-206 and DIV-207 (WP14)
T091 Architecture docs for the cascade (WP14)
T092 Changelog fragment (WP14)
T093 PR description notes and docs gates (WP14)

- **Risks:** describing the superseded decisions; overclaiming the effective reads.

## Parallelization

WP01 and WP02 have no dependency and can run in parallel. WP03 follows WP02, and WP05 follows WP01. WP04 joins
WP02 and WP03; WP06 and WP07 follow WP05 in sequence. WP08 joins WP04 and WP07. Everything after WP08 is a chain:
WP09, then PR B's WP10 to WP14.

## Shared files (one owner each)

Several files are edited by more than one WP in sequence. Each has one owner (the first WP to touch it), and the later
WPs list the edit under "Out-of-map edits (sequenced by dependency)":

| File | Owner | Later edits |
|---|---|---|
| `HexagonalArchitectureTest.java` | WP01 | WP05 |
| `activations/infra/api/ActivationController.java` | WP05 | WP08, WP12, WP13 |
| `AvailablePacks.java`, `PacksAvailability.java` | WP05 | WP08, WP12 |
| `PackCompositionFacts.java`, `DomainRepositoryService.java` | WP03 | WP04, WP10 |
| `ClosureView.java`, `ClosureQueries.java` | WP04 | WP10 |
| `OfferingReadService.java` | WP02 | WP04 |
| `activations.feature`, `ReservedOperationCoverageTest.java`, `CharterRefusalCode.yaml`, `ReservedRefusalCodesTest.java`, `ActivationSteps.java`, selections and exclusions path files | WP08 | WP12, WP13 |
| `divergence-register.md`, `divergences.yaml`, `offering-and-activation.md`, `resolution-model.md` | WP09 | WP14 |

## MVP

PR A alone (WP01 to WP09) lets core store, read, replace and delete a project's activation and read its closure
(US1, US2, US6).
