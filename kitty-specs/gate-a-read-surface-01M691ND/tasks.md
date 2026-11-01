# Tasks: Gate-A read surface: API root, offering, generic artifact routes and links

**Mission**: `gate-a-read-surface-01M691ND` · **Branch**: `issue-126-offering-reads` (planning base and
merge target) · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Research**:
[research.md](research.md)

- **One lane, seven WPs in a dependency chain.** WP01 → WP02 → … → WP07.
  - Every WP edits the shared contract files (`openapi.yaml`, the CHANGELOG, the scenarios). Parallel
    lanes would only conflict, so `owned_files` may overlap along the chain: they are sequential pairs.
  - Each WP leaves `./gradlew verifyAll` green.
- **Red first (C-007).** Inside each WP, the scenarios, fixtures and tests come first, and are seen
  failing before the contract or service change turns them green.
- **Squad folds.** The plan-point squad's findings (research.md, "Plan-point adversarial squad", and
  R-13 to R-24) are folded into the subtasks below.
- **CHANGELOG.** Each code WP appends its own lines to `contracts/charter/CHANGELOG.md` under
  `## 0.1.0-SNAPSHOT` → `### Breaking`, in a block headed for this mission. WP07 consolidates the block.

## Subtask Index

| ID | Description | WP | Parallel |
| --- | --- | --- | --- |
| T001 | Red lint fixtures for the fingerprint rules; replace the orphaned `.missing-fingerprint` fixture | WP01 | |
| T002 | Ruleset: narrow `charter-headers-on-every-response`; add `fingerprint-on-verified-reads` and `fingerprint-only-on-verified-reads`; lint README | WP01 | |
| T003 | Contract: fingerprint `required: true` on verified read 200s; removed from refusals, writes, `/openapi.yaml`, the shared Problem; `VENDORED.md` | WP01 | |
| T004 | #176: `OfferingUnreadable` examples and description; raised-code check over refusal examples, with a planted fixture | WP01 | [P] |
| T005 | Keep the service and harness tests green; CHANGELOG lines | WP01 | |
| T006 | `Link` and `Links` schemas (R-15), cited, unreferenced | WP02 | |
| T007 | ClosureChecker structural `_links` exemption (R-02 revised), with four fixtures | WP02 | |
| T008 | `ContractConformance` validates schema-valued `additionalProperties` (R-14), with red fixtures | WP02 | [P] |
| T009 | `ContractConformance` matches on path and method (R-14), with a fixture | WP02 | [P] |
| T010 | Service `Relation` constants and `Hypermedia` link builder, with unit tests | WP02 | [P] |
| T011 | Red scenarios `artifacts.feature` and new steps (R-21) | WP03 | |
| T012 | Contract: generic read operations, schemas, parameters, `ArtifactNotFound`; `PageInfo` citation; generator spike | WP03 | |
| T013 | `kind_not_found`: contract code, kernel `RefusalCode`, `notFound()`, `RefusalHandler`, parity tests | WP03 | [P] |
| T014 | Application `ArtifactReadService` (R-19) and the position value, with unit tests | WP03 | |
| T015 | Adapter `ArtifactController`: converter, cursor codec, 400s, envelope, ETag, fingerprint; `ArtifactControllerTest` | WP03 | |
| T016 | Verify, and CHANGELOG lines | WP03 | |
| T017 | Baseline the scenario count; red removed-route outline and 405 cases; migrate the kind features and journeys | WP04 | |
| T018 | Contract: the 8 writes moved, answering `Artifact`; every per-kind path, tag and orphaned file removed | WP04 | |
| T019 | Service: the per-kind controllers keep only the writes and answer the envelope; reads and `TacticController` removed; tests migrated | WP04 | |
| T020 | #176: scenario pinning `pack_unreadable` on a tactic item read | WP04 | [P] |
| T021 | Scenario count check and the removed-to-new mapping in the CHANGELOG | WP04 | |
| T022 | Red scenarios `root.feature` and `offering.feature`; `service.feature` updates | WP05 | |
| T023 | Contract: `getApiRoot`, `getOffering`, `ApiRoot`, `Offering`, `OfferingPack`, `ServiceInfo._links`, `Link` header | WP05 | |
| T024 | Domain port `PackCatalogRepository` and `ConfiguredPack`; storage implementation; optional descriptor lookup | WP05 | |
| T025 | Application `OfferingReadService`, with unit tests | WP05 | |
| T026 | `OfferingController`, `getApiRoot`, `ServiceInfo` links, `Link` header; fingerprint hook exempts `/` | WP05 | |
| T027 | `RelationshipRegistered` (R-13): contract, controller, scenarios | WP06 | |
| T028 | Lint rules `success-schemas-carry-links`, `link-relations-are-known`, `success-responses-have-link-examples`, with fixtures; `_links` examples | WP06 | |
| T029 | Guide relation table updated; parity tests (guide vs ruleset, guide vs service `Relation`) | WP06 | |
| T030 | Harness hooks: `self`, link resolution, `Location`, relation keys; `@AfterAll` operation coverage | WP06 | |
| T031 | Full `verifyAll`; CHANGELOG lines | WP06 | |
| T032 | CHANGELOG `0.1.0-SNAPSHOT` `### Breaking`: consolidate the mission block | WP07 | |
| T033 | ADR-0019 implementation note; ADR-0026 D1.2 amendment note (paths only) | WP07 | [P] |
| T034 | Docs: service-contract, scenarios README, architecture, roadmap, docs changelog, glossary check | WP07 | [P] |
| T035 | Docs checks, and a grep for the removed routes over the records | WP07 | |

## Phase 1 — Foundation

### WP01 — Fingerprint and refusal declarations (#175, #176)

- **Goal**: the contract declares `Charter-Source-Fingerprint` exactly where the service sends it, and
  the lint enforces both directions. `OfferingUnreadable`'s examples name only raised codes, and a check
  keeps it that way.
- **Priority**: P1. **Prompt**: [tasks/WP01-fingerprint-and-refusal-declarations.md](tasks/WP01-fingerprint-and-refusal-declarations.md)
- **Independent test**: `./gradlew :contracts:check :contracts:test :deployable:test`.

T001 Red lint fixtures for the fingerprint rules (WP01)
T002 Ruleset changes and lint README (WP01)
T003 Contract fingerprint declarations and `VENDORED.md` (WP01)
T004 #176 examples and the raised-code check (WP01)
T005 Service and harness tests green; CHANGELOG lines (WP01)

**Sketch.** Fixtures and the red check (T001, T004 test) → rules (T002) → contract (T003, T004) →
keep the suites green (T005). **Dependencies**: none. **Risks**: the vacuum JSONPath filter syntax;
the rule-count check in `lintContract`; every existing fixture carries the YAML anchor
`&charter-headers`.

### WP02 — Hypermedia components and conformance harness

- **Goal**: `Link`/`Links` exist; the closure checker accepts `_links` alone; the scenario harness can
  validate maps and tell a literal PUT path from a templated GET; the service has one link builder.
- **Priority**: P1. **Prompt**: [tasks/WP02-hypermedia-components-and-harness.md](tasks/WP02-hypermedia-components-and-harness.md)
- **Independent test**: `./gradlew :contracts:test :packs:test :deployable:test`.

T006 `Link` and `Links` schemas (WP02)
T007 Closure exemption and fixtures (WP02)
T008 Conformance validates maps (WP02)
T009 Method-aware path matching (WP02)
T010 `Relation` and `Hypermedia` (WP02)

**Dependencies**: WP01. **Risks**: `ContractConformance` vets every schema file, so `Links.yaml` must
land with T008 in the same commit, or the suite goes red.

## Phase 2 — The resource tree

### WP03 — Generic artifact reads (added beside the per-kind routes)

- **Goal**: `GET /packs/{packId}/artifacts` (paged, `?kind=`) and
  `GET /packs/{packId}/artifacts/{kind}/{artifactId}` answer the `ArtifactPage` and the `Artifact`
  envelope, with `kind_not_found`. The per-kind routes still exist.
- **Priority**: P1. **Prompt**: [tasks/WP03-generic-artifact-reads.md](tasks/WP03-generic-artifact-reads.md)
- **Independent test**: the `artifacts.feature` scenarios, plus `./gradlew verifyAll`.

T011 Red scenarios and steps (WP03)
T012 Contract for the generic reads (WP03)
T013 `kind_not_found` (WP03)
T014 `ArtifactReadService` (WP03)
T015 `ArtifactController` (WP03)
T016 Verify and CHANGELOG (WP03)

**Dependencies**: WP02. **Risks**: the generator's typing of `PageInfo.nextPageCursor` and of the
`Artifact` `oneOf`; Spring enum conversion (R-17); complexity ≤ 15 in the cursor decoding.

### WP04 — Writes moved; every per-kind route removed

- **Goal**: the 8 writes answer under `/packs/{packId}/artifacts/<kind>…` with the envelope. Every
  per-kind route is gone with no alias, and every behaviour its scenarios pinned is pinned again.
- **Priority**: P1. **Prompt**: [tasks/WP04-writes-moved-per-kind-routes-removed.md](tasks/WP04-writes-moved-per-kind-routes-removed.md)
- **Independent test**: the migrated kind features, the journeys, and the removed-route outline.

T017 Baseline, red route scenarios, migrated features (WP04)
T018 Contract write moves and removals (WP04)
T019 Service write controllers and removals (WP04)
T020 #176 item scenario (WP04)
T021 Count check and mapping (WP04)

**Dependencies**: WP03. **Risks**: the largest diff, in the exact-body scenarios and journeys; losing
coverage silently (guarded by T017 and T021).

### WP05 — API root and offering

- **Goal**: `GET /` and `GET /offering` answer from the canonical model, `ServiceInfo` links to the
  contract document, and every link target exists.
- **Priority**: P2. **Prompt**: [tasks/WP05-api-root-and-offering.md](tasks/WP05-api-root-and-offering.md)
- **Independent test**: `root.feature`, `offering.feature`, `service.feature`.

T022 Red scenarios (WP05)
T023 Contract (WP05)
T024 `PackCatalogRepository` (WP05)
T025 `OfferingReadService` (WP05)
T026 Controllers, links, `Link` header, hook (WP05)

**Dependencies**: WP04. **Risks**: the configured level vs the declared level (R-18); a pack without
`pack.yaml`.

## Phase 3 — Enforcement and records

### WP06 — Every answer linked and checked

- **Goal**: every JSON success answer carries `_links`, with `self` where its URI can be read. Links are
  resolved statically against the contract, relation keys match the guide's table, and every
  (operation, 2xx) pair is exercised.
- **Priority**: P1. **Prompt**: [tasks/WP06-every-answer-linked-and-checked.md](tasks/WP06-every-answer-linked-and-checked.md)
- **Independent test**: `./gradlew verifyAll`.

T027 `RelationshipRegistered` (WP06)
T028 Link lint rules and fixtures (WP06)
T029 Relation table and parity tests (WP06)
T030 Harness hooks and coverage (WP06)
T031 Verify and CHANGELOG (WP06)

**Dependencies**: WP05. **Risks**: every existing lint fixture needs `_links` so that it still fires
only its own rule; the coverage check can expose operations no scenario exercises.

### WP07 — Records

- **Goal**: the CHANGELOG, ADR notes and docs describe the new surface, and the docs checks pass.
- **Priority**: P2. **Prompt**: [tasks/WP07-records.md](tasks/WP07-records.md)
- **Independent test**: `python3 scripts/docs/check_docs.py all`, markdownlint-cli2, cspell.

T032 CHANGELOG consolidation (WP07)
T033 ADR notes (WP07)
T034 Docs (WP07)
T035 Docs checks and a removed-route grep (WP07)

**Dependencies**: WP06. **Risks**: the docsite's stricter `related:` slugs; cspell on new terms.

## Closeout (not a WP)

- Update the #126 issue body with the offering reads chosen for this minor (`GET /offering` only; the
  deferrals per ruling 3).
- Pre-PR adversarial squad with a contract/API lens; rebase onto `origin/main`; draft PR.
