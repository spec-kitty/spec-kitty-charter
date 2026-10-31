# Tasks: the activation set (#125)

Mission `activation-set-01M691HW`. Planning base and merge target: `issue-125-activation-set`.
Design authority: `spec.md`, `plan.md`, `research.md` (rulings, D-1 to D-15, squad S1 to S31),
`data-model.md`, `contracts/activation-operations.md`.

Work is red-first (ATDD): WP01 and WP02 commit failing lint fixtures and failing scenarios before
WP03 and WP04 make them pass.

## Subtask Index

| ID | Description | WP | Parallel |
|---|---|---|---|
| T001 | `clean.yaml` gains a reserved operation and a 204 DELETE (positive controls) | WP01 | |
| T002 | Planted fixtures for the three new rules and `no-content-has-no-body` (red) | WP01 | |
| T003 | Rules `x-reserved-shape`, `reserved-operation-declares-501`, `built-operation-declares-no-501` | WP01 | |
| T004 | Amend `error-responses-are-problems` for 204; add `no-content-has-no-body` | WP01 | |
| T005 | Lint README table and fixture notes | WP01 | |
| T006 | `ContractConformance` learns `minItems` (fixture test red first) | WP02 | [P] |
| T007 | Step `Then the operation is refused as not implemented` (declared 501 + `x-reserved`, no `default` fallback, headers, no `code`) | WP02 | |
| T008 | `activations.feature`: 13 `@contract @reserved` 501 pins | WP02 | |
| T009 | `errors.feature`: 6 declaration pins; reserved-code list | WP02 | |
| T010 | Scenarios README: `@reserved` admits the 501 form | WP02 | [P] |
| T011 | Test: `x-reserved` operations equal the operations the 501 scenarios call | WP02 | |
| T012 | `ActivationConfig.schema.json` (2020-12) and the corpus | WP03 | |
| T013 | OpenAPI mirror: `ActivationConfig` and its parts | WP03 | |
| T014 | `Activation`, `ActivationChange`, `SkippedReport` family, effective-read schemas, `Links`/`Link`, request bodies | WP03 | |
| T015 | Shared `Problem` gains `requiredBy`, `violations`; `RequiringPack`, `RefusalViolation`; VENDORED.md | WP03 | |
| T016 | ClosureChecker `_links` exemption with fixture | WP03 | |
| T017 | Structural parity test with negative control (`contracts/src/test`) | WP03 | |
| T018 | Corpus on networknt (JSON file and mirror) and kind alignment test (`service/packs`) | WP03 | |
| T019 | Generator `containerDefaultToNull`, `NON_NULL` mix-in, absent-vs-`[]` round trip | WP03 | |
| T020 | Parameters, headers and responses for the activation set | WP04 | |
| T021 | Nine path files, 13 operations, tag `Activation`, `x-reserved`, 501, 500 | WP04 | |
| T022 | Six refusal codes in the contract and in `RefusalCode`/`RefusalHandler`; descriptions rewritten | WP04 | |
| T023 | `@ReservedOperation`, the interceptor and `ActivationController` | WP04 | |
| T024 | ArchUnit: reserved codes raised by nothing (all six, bite probes); controller touches no storage | WP04 | |
| T025 | `ContractDescriptionsTest` pins; corpus through `ContractConformance` | WP04 | |
| T026 | Green run: `:contracts:check`, scenarios, `verifyAll` | WP04 | |
| T027 | CHANGELOG under `0.1.0-SNAPSHOT`, `### Breaking` and `### Added` | WP05 | |
| T028 | ADR-0020 amendment and ADR index row | WP05 | |
| T029 | Architecture, API and status-code docs | WP05 | |
| T030 | Release workflow ships `ActivationConfig.schema.json` | WP05 | |
| T031 | Draft issue for building the activation writes (`research/`) | WP05 | |
| T032 | Docs checks: `check_docs.py`, markdownlint, cspell | WP05 | |

## WP01 — Lint rules for the reserved shape (red first)

- **Goal**: a half-reserved or half-built operation fails lint; `204` is a success with no body.
- **Priority**: P1. **Independent test**: `./gradlew :contracts:lintFixtures :contracts:lintContract`.
- **Prompt**: `tasks/WP01-lint-rules-for-the-reserved-shape.md`
- **Dependencies**: none.

T001 `clean.yaml` positive controls (WP01)
T002 Planted fixtures, red (WP01)
T003 Three new rules (WP01)
T004 204 admitted; `no-content-has-no-body` (WP01)
T005 Lint README (WP01)

## WP02 — Scenarios first: a 501 pin per operation (red first)

- **Goal**: each of the 13 operations and the 6 codes is pinned by a scenario before anything is
  declared; the step and the hook cannot pass on a missing declaration.
- **Priority**: P1. **Independent test**: `./gradlew :deployable:test` (scenarios red until WP04).
- **Prompt**: `tasks/WP02-scenarios-first-a-501-pin-per-operation.md`
- **Dependencies**: none.

T006 `minItems` in `ContractConformance` (WP02)
T007 The 501 step (WP02)
T008 `activations.feature` (WP02)
T009 Declaration pins (WP02)
T010 Scenarios README (WP02)
T011 13 = 13 test (WP02)

## WP03 — The activation schemas and their parity

- **Goal**: every closed schema of `data-model.md`, the canonical JSON Schema, and three checks
  that keep the mirror equal to it.
- **Priority**: P1. **Independent test**: `:contracts:check` and `:packs:test` parity tests.
- **Prompt**: `tasks/WP03-the-activation-schemas-and-their-parity.md`
- **Dependencies**: none.

T012 JSON Schema and corpus (WP03)
T013 OpenAPI mirror (WP03)
T014 Envelope, change, report, effective reads, links, bodies (WP03)
T015 Shared `Problem` members (WP03)
T016 ClosureChecker `_links` exemption (WP03)
T017 Structural parity test (WP03)
T018 Corpus and kind alignment test (WP03)
T019 Generator null handling and round trip (WP03)

## WP04 — The reserved operations, declared and answering 501

- **Goal**: the 13 operations in the contract and a service that answers each with `501`.
- **Priority**: P1. **Independent test**: the WP02 scenarios turn green; `verifyAll`.
- **Prompt**: `tasks/WP04-the-reserved-operations-declared-and-answering-501.md`
- **Dependencies**: WP01, WP02, WP03.

T020 Parameters, headers, responses (WP04)
T021 Path files and operations (WP04)
T022 Refusal codes, contract and service (WP04)
T023 Interceptor and controller (WP04)
T024 ArchUnit pins (WP04)
T025 Description pins; corpus through `ContractConformance` (WP04)
T026 Green run (WP04)

## WP05 — Version, CHANGELOG, ADR-0020 and docs

- **Goal**: record the set, its breaks and the rulings; ship the JSON Schema with the release.
- **Priority**: P2. **Independent test**: release gate (`checkReleaseTag` logic via `:contracts:check`), docs checks.
- **Prompt**: `tasks/WP05-version-changelog-adr-0020-and-docs.md`
- **Dependencies**: WP04.

T027 CHANGELOG (WP05)
T028 ADR-0020 amendment (WP05)
T029 Docs (WP05)
T030 Release workflow (WP05)
T031 Draft build issue (WP05)
T032 Docs checks (WP05)

## Parallelism and MVP

WP01, WP02 and WP03 are independent and may run in parallel; WP04 joins them; WP05 closes.
MVP: WP01 to WP04 (the contract and the service agree, every operation answers 501).
