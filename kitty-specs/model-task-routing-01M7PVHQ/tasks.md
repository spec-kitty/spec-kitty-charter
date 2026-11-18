# Tasks: Model-task routing: the catalog kind and the advisory recommendation

**Mission**: `model-task-routing-01M7PVHQ` · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)
**Planning base / merge target**: `feat/108-model-task-routing`

No WP starts before #355 (PR #599) has merged and this branch is rebased onto that main (C-001, research R1). Every
WP first rechecks the surfaces it names against the merged main.

Shared registry files are owned by one WP each. A later WP that must add its rows to one of them does so as a
recorded edit outside its map, after the owner merged (its dependency):

- `contracts/charter/openapi.yaml`, `scenarios/switch-gate.yaml`, `every-operation.feature`,
  `unexpected-failure.feature` and `read-refusals.feature` belong to WP08;
- `schemas/CharterRefusalCode.yaml` and `errors.feature` belong to WP09.

## Subtask Index

| ID | Description | WP | Parallel |
| --- | --- | --- | --- |
| T001 | Create the `routing` and `routing-edge` modules and wire the build | WP01 | |
| T002 | Register the routing slice in `HexagonalArchitectureTest` | WP01 | |
| T003 | Amend ADR-0037 and ADR-0006 for `packs → routing` | WP01 | [P] |
| T004 | Add `ROUTING_CATALOG` to `ArtifactKind` and `NodeKind`, with the marker type | WP02 | |
| T005 | Add the kernel `CostTier` | WP02 | [P] |
| T006 | Exclude the kind from `ENHANCES` and `OVERRIDES`; amend ADR-0077 decision 5 | WP02 | |
| T007 | Check the cascade rules, and update the kernel tests that enumerate kinds | WP02 | |
| T008 | Add the kind to the contract enums and the activation schemas | WP03 | |
| T009 | Add the kind to the pack schemas and write `routing-catalog.schema.json` | WP03 | |
| T010 | Add the file rule, the classifier case and the packs CHANGELOG line | WP03 | [P] |
| T011 | Register the link relations in the lint ruleset and the hypermedia table | WP03 | [P] |
| T012 | Update the kind-taxonomy example and `offering.feature` | WP03 | |
| T013 | Add `model_tier` to the pack step schema, the DSO and the domain step | WP04 | |
| T014 | Add `modelTier` to the contract step and the shared `CostTier` schema | WP04 | |
| T015 | Map the step tier in the step reads, with null omission | WP04 | |
| T016 | Write the `routing` domain value types | WP05 | |
| T017 | Write the storage binding, DSO and YAML format | WP05 | |
| T018 | Write the content rules as a `PackContents` facts view and pack rulesets | WP05 | |
| T019 | Add the read-service registrations | WP05 | |
| T020 | Add the R7 catalogs to the example packs | WP06 | |
| T021 | Add a profile `model`, a step `model_tier`, presets and policy | WP06 | |
| T022 | Regenerate the manifests; fix the count-asserting scenarios; pass the round trip | WP06 | |
| T023 | Write the score and rank key in exact decimal | WP07 | |
| T024 | Write the tiers in play and the two top-ranked sets with ties | WP07 | |
| T025 | Write the flags and the order | WP07 | |
| T026 | Unit-test every R7 row, all-zero weights, and float against decimal | WP07 | |
| T027 | Write the contract for the four catalog reads | WP08 | |
| T028 | Write the pack reads in `artifacts-edge` | WP08 | |
| T029 | Write the effective twins and the activation link | WP08 | |
| T030 | Add the null-omission mix-ins and native hints | WP08 | [P] |
| T031 | Write the scenarios and the switch-gate entries | WP08 | |
| T032 | Write the contract for `getModelRecommendation` and the refusal codes | WP09 | |
| T033 | Write `EffectiveReadService.modelRecommendation` and its tag | WP09 | |
| T034 | Write the `routing-edge` use case, controller and JSON mix-in | WP09 | |
| T035 | Write `model-recommendation.feature`: R7 rows and refusals | WP09 | |
| T036 | Add rows to the shared scenario files and switch-gate entries | WP09 | |
| T037 | Write the contract changelog fragment (`### Breaking`) | WP10 | |
| T038 | Update the service contract: the R14 row, the operation rows and #94's table | WP10 | |
| T039 | Open the divergence register entries with their evidence | WP10 | [P] |
| T040 | Update the logical model and add a pack-author guide note | WP10 | [P] |

## Phase 1 — Foundations

### WP01 — The routing slice and its governance

**Prompt**: [tasks/WP01-routing-slice-governance.md](tasks/WP01-routing-slice-governance.md) · Priority P1 ·
Dependencies: none.

- **Goal:** two empty but governed modules, `routing` and `routing-edge`, that ArchUnit checks as a slice.
- **Subtasks:**
  - T001 Create the `routing` and `routing-edge` modules and wire the build (WP01)
  - T002 Register the routing slice in `HexagonalArchitectureTest` (WP01)
  - T003 Amend ADR-0037 and ADR-0006 for `packs → routing` (WP01)
- **Independent test:** `./gradlew :deployable:test --tests '*HexagonalArchitectureTest*'` passes, and the bite
  control of the new purity rule fails when it should.

### WP02 — The kernel kind and cost tier

**Prompt**: [tasks/WP02-kernel-kind-and-cost-tier.md](tasks/WP02-kernel-kind-and-cost-tier.md) · Priority P1 ·
Dependencies: none.

- **Goal:** `ROUTING_CATALOG` in the kernel, appended last, activatable, and neither declarable nor an edge target.
  `CostTier` with its order. No relation admits a catalog.
- **Subtasks:**
  - T004 Add `ROUTING_CATALOG` to `ArtifactKind` and `NodeKind`, with the marker type (WP02)
  - T005 Add the kernel `CostTier` (WP02)
  - T006 Exclude the kind from `ENHANCES` and `OVERRIDES`; amend ADR-0077 decision 5 (WP02)
  - T007 Check the cascade rules, and update the kernel tests that enumerate kinds (WP02)
- **Independent test:** the kernel tests pass, and `EndpointTableAdrTest` holds the ADR and the code equal.

### WP03 — Schemas, file rule and link relations

**Prompt**: [tasks/WP03-schemas-file-rule-link-relations.md](tasks/WP03-schemas-file-rule-link-relations.md) ·
Priority P1 · Dependencies: WP02.

- **Goal:** every enum of kinds lists `routing_catalog`. The pack schema and the file rule exist. The four link
  relations are registered.
- **Subtasks:**
  - T008 Add the kind to the contract enums and the activation schemas (WP03)
  - T009 Add the kind to the pack schemas and write `routing-catalog.schema.json` (WP03)
  - T010 Add the file rule, the classifier case and the packs CHANGELOG line (WP03)
  - T011 Register the link relations in the lint ruleset and the hypermedia table (WP03)
  - T012 Update the kind-taxonomy example and `offering.feature` (WP03)
- **Independent test:** `./gradlew :contracts:check` passes, and so do the parity tests that pin enum order.

### WP04 — The step's model tier

**Prompt**: [tasks/WP04-step-model-tier.md](tasks/WP04-step-model-tier.md) · Priority P2 · Dependencies: WP02.

- **Goal:** a mission step may declare `model_tier`. It is stored, round-tripped and served as `modelTier`.
- **Subtasks:**
  - T013 Add `model_tier` to the pack step schema, the DSO and the domain step (WP04)
  - T014 Add `modelTier` to the contract step and the shared `CostTier` schema (WP04)
  - T015 Map the step tier in the step reads, with null omission (WP04)
- **Independent test:** a step read answers `modelTier` for a step that declares one and omits it otherwise.

## Phase 2 — The kind

### WP05 — Catalog domain, binding and content rules

**Prompt**: [tasks/WP05-catalog-domain-binding-rules.md](tasks/WP05-catalog-domain-binding-rules.md) · Priority P1 ·
Dependencies: WP01, WP02, WP03.

- **Goal:** the store reads, writes and validates a catalog, and registration refuses inconsistent content.
- **Subtasks:**
  - T016 Write the `routing` domain value types (WP05)
  - T017 Write the storage binding, DSO and YAML format (WP05)
  - T018 Write the content rules as a `PackContents` facts view and pack rulesets (WP05)
  - T019 Add the read-service registrations (WP05)
- **Independent test:** the binding round-trips a catalog, and each FR-002 rule refuses its invalid fixture.

### WP06 — Example packs

**Prompt**: [tasks/WP06-example-packs.md](tasks/WP06-example-packs.md) · Priority P1 · Dependencies: WP04, WP05.

- **Goal:** charter's example packs carry the research R7 matrix, and the whole-pack round trip passes over them.
- **Subtasks:**
  - T020 Add the R7 catalogs to the example packs (WP06)
  - T021 Add a profile `model`, a step `model_tier`, presets and policy (WP06)
  - T022 Regenerate the manifests; fix the count-asserting scenarios; pass the round trip (WP06)
- **Independent test:** `:pack-validation:wholePackRoundTrip` passes, along with `ExamplePackCoverageTest`.

## Phase 3 — Reads

### WP07 — The ranker

**Prompt**: [tasks/WP07-ranker.md](tasks/WP07-ranker.md) · Priority P1 · Dependencies: WP05.

- **Goal:** the pure ranking of ADR-0080 decisions 10 to 12, as `data-model.md` states it.
- **Subtasks:**
  - T023 Write the score and rank key in exact decimal (WP07)
  - T024 Write the tiers in play and the two top-ranked sets with ties (WP07)
  - T025 Write the flags and the order (WP07)
  - T026 Unit-test every R7 row, all-zero weights, and float against decimal (WP07)
- **Independent test:** the unit tests, each of which fails when its rule is removed.

### WP08 — The catalog reads

**Prompt**: [tasks/WP08-catalog-reads.md](tasks/WP08-catalog-reads.md) · Priority P1 · Dependencies: WP06.

- **Goal:** the four catalog reads, `ready` in the switch gate.
- **Subtasks:**
  - T027 Write the contract for the four catalog reads (WP08)
  - T028 Write the pack reads in `artifacts-edge` (WP08)
  - T029 Write the effective twins and the activation link (WP08)
  - T030 Add the null-omission mix-ins and native hints (WP08)
  - T031 Write the scenarios and the switch-gate entries (WP08)
- **Independent test:** `./gradlew verifyAll`, with the four operations `ready`.

### WP09 — The recommendation read

**Prompt**: [tasks/WP09-recommendation-read.md](tasks/WP09-recommendation-read.md) · Priority P1 ·
Dependencies: WP04, WP07, WP08.

- **Goal:** `getModelRecommendation`, `ready`, with one scenario per R7 row and per refusal.
- **Subtasks:**
  - T032 Write the contract for `getModelRecommendation` and the refusal codes (WP09)
  - T033 Write `EffectiveReadService.modelRecommendation` and its tag (WP09)
  - T034 Write the `routing-edge` use case, controller and JSON mix-in (WP09)
  - T035 Write `model-recommendation.feature`: R7 rows and refusals (WP09)
  - T036 Add rows to the shared scenario files and switch-gate entries (WP09)
- **Independent test:** `./gradlew verifyAll`, with `getModelRecommendation` `ready`.

## Phase 4 — Records

### WP10 — Records and closeout docs

**Prompt**: [tasks/WP10-records.md](tasks/WP10-records.md) · Priority P2 · Dependencies: WP08, WP09.

- **Goal:** the decision is visible where clients look.
- **Subtasks:**
  - T037 Write the contract changelog fragment (`### Breaking`) (WP10)
  - T038 Update the service contract: the R14 row, the operation rows and #94's table (WP10)
  - T039 Open the divergence register entries with their evidence (WP10)
  - T040 Update the logical model and add a pack-author guide note (WP10)
- **Independent test:** `ChangelogFragmentsTest` and `DivergenceRegisterTest` pass, along with the eight
  `check_docs.py` checks.

## Parallelism

- WP01 and WP02 can run at once.
- After WP02, WP03 and WP04 can run at once.
- WP07 can run beside WP06 once WP05 is done.

MVP: WP01 to WP08 serve the kind. WP09 is the read core's dispatch needs.
