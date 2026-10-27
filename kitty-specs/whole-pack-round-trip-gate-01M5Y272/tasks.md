# Tasks: Whole-pack round-trip gate

**Mission**: whole-pack-round-trip-gate-01M5Y272
**Planning base / merge target**: `feat/whole-pack-round-trip-gate`
**Design sources**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md)

Three foundation WPs (comparator, round-trip seam + adapter, census/expectation/report) are independent and can
run in parallel; WP04 assembles the corpus gate, seeds the baseline from the first real run, and wires the build,
CI and docs. Every class is test-scope in package `ai.speckitty.charter.packs.infra.storage` of `service/packs`.
Tests are required (charter: test first, no fake green); every WP writes its unit tests before the code.
All WPs: `execution_mode: code_change`, `agent_profile: java-jenny`, Java 25. No edits to `Pack.java` or
`FilesystemPackStore` (C-001).

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Comparator specification by example: equal/unequal cases incl. key order, quoting, `[]` vs absent, list order (WP01) | WP01 | |
| T002 | `MeaningComparator` core: maps, lists, scalars, first difference with path (WP01) | WP01 | |
| T003 | Graph-shard policy: canonical edge set over all fields, nodes keyed by `urn`, duplicate info (WP01) | WP01 | |
| T004 | `WholePackRoundTrip` seam: outcome types, contract javadoc for #189 (WP02) | WP02 | [P] |
| T005 | Fixture packs for the adapter tests (WP02) | WP02 | [P] |
| T006 | `DomainRoundTrip`: two `DomainRepositoryService`s, descriptor + five kinds, pairing by (kind, id) (WP02) | WP02 | |
| T007 | `DomainRoundTrip`: graph shards via reader → `PackPolicy.graph` → writer; read-back violations → refused (WP02) | WP02 | |
| T008 | Adapter tests: happy path, unknown field refused, lossy-writer positive control (WP02) | WP02 | |
| T009 | `WholePackCensus`: list files, gate kind from `file-kinds.yaml` or `other:<segment>` (WP03) | WP03 | [P] |
| T010 | `WholePackExpectation`: load `expected-red.yaml` + `exclusions.yaml`, validate entries (WP03) | WP03 | |
| T011 | Ratchet evaluation: statuses + problems (unexpected red/green, stale, excluded-modelled, empty kind) (WP03) | WP03 | |
| T012 | `WholePackReport`: kind table + per-file rows, deterministic Markdown (WP03) | WP03 | [P] |
| T013 | Expectation/ratchet/report unit tests (WP03) | WP03 | |
| T014 | `WholePackRoundTripTest` (`@Tag("whole-pack")`): pin, run, report, vacuity, accounting, ratchet (WP04) | WP04 | |
| T015 | Gradle `wholePackRoundTrip` task; tag excluded from `test`/`openPacksRoundTrip`; not in `check`; fix stale comment (WP04) | WP04 | |
| T016 | First real run; triage every red into `expected-red.yaml` (reason + issue); write `exclusions.yaml` (WP04) | WP04 | |
| T017 | Non-required CI workflow running the gate and uploading the report (WP04) | WP04 | [P] |
| T018 | Docs: service-build guide section + changelog entry (WP04) | WP04 | [P] |

## Phase 1 — Foundations (parallel)

### WP01 — Meaning comparator

**Prompt**: [tasks/WP01-meaning-comparator.md](./tasks/WP01-meaning-comparator.md)
**Goal**: decide whether a written YAML document means the same as its source, per the provisional #188 policy.
**Priority**: P1 · **Independent test**: `MeaningComparatorTest` passes; each rule has an equal and an unequal case.
**Requirements**: FR-003, FR-004 · **Dependencies**: none

T001 Comparator specification by example (WP01)
T002 `MeaningComparator` core (WP01)
T003 Graph-shard policy (WP01)

Risks: over-lenient rules hide loss (every "equal" rule has an unequal twin); YAML type coercion (`'1.0'` vs `1.0`).

### WP02 — Round-trip seam and domain adapter

**Prompt**: [tasks/WP02-round-trip-seam-and-domain-adapter.md](./tasks/WP02-round-trip-seam-and-domain-adapter.md)
**Goal**: read a pack into the domain, write it to an empty directory through storage, read it back.
**Priority**: P1 · **Independent test**: `DomainRoundTripTest` over fixture packs.
**Requirements**: FR-002, FR-005, FR-011 · **Dependencies**: none

T004 `WholePackRoundTrip` seam (WP02)
T005 Fixture packs (WP02)
T006 `DomainRoundTrip` artefacts + descriptor (WP02)
T007 `DomainRoundTrip` graph shards + read-back (WP02)
T008 Adapter tests incl. positive controls (WP02)

Risks: written files land at `<id>.<kind>.yaml`, not the source path (pair by id); duplicate ids in a source pack.

### WP03 — Census, expectation, ratchet and report

**Prompt**: [tasks/WP03-census-expectation-and-report.md](./tasks/WP03-census-expectation-and-report.md)
**Goal**: account for every file once and judge the observed result against the checked-in expectation.
**Priority**: P1 · **Independent test**: `WholePackExpectationTest` (ratchet both ways, stale entries).
**Requirements**: FR-006, FR-007, FR-008, FR-009, FR-010 · **Dependencies**: none

T009 `WholePackCensus` (WP03)
T010 `WholePackExpectation` loading (WP03)
T011 Ratchet evaluation (WP03)
T012 `WholePackReport` (WP03)
T013 Unit tests (WP03)

Risks: ratchet that can be satisfied vacuously (empty kinds, zero files) — guarded by explicit problems.

## Phase 2 — Gate assembly

### WP04 — Corpus gate, baseline, build, CI and docs

**Prompt**: [tasks/WP04-corpus-gate-baseline-and-wiring.md](./tasks/WP04-corpus-gate-baseline-and-wiring.md)
**Goal**: the gate runs over the pinned built-in pack as its own task and passes against a triaged baseline.
**Priority**: P1 · **Independent test**: `./gradlew :packs:wholePackRoundTrip` green; `verifyAll` unchanged.
**Requirements**: FR-001, FR-012 · **Dependencies**: WP01, WP02, WP03

T014 `WholePackRoundTripTest` (WP04)
T015 Gradle task wiring (WP04)
T016 Baseline seed + exclusions (WP04)
T017 Non-required CI workflow (WP04)
T018 Docs + changelog (WP04)

Risks: baseline seeded without triage (every entry needs a specific reason + issue); wiring accidentally into `check`.

## MVP

WP01–WP04 together: the gate is only useful assembled. WP01–WP03 can land in any order.
