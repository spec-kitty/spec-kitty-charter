# Tasks: Mission-type consolidation into the sanitized model

**Mission**: `mission-type-consolidation-01M65MCK` · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)
**Planning branch / merge target**: `issue-194-mission-type-consolidation` (one PR to `main`, issue #194 PR-B)

Seven work packages, sequential: WP02 (kernel kind) runs first, then WP01, WP03 to WP07. Every WP leaves `check` green: the new `files` rules match no path of the old layout,
the old exclusions stay until the re-pin, the `retired` mechanism is proven on fixture tables, and the migration
harness lives in its own source set. WP06 is the one re-pin commit; it waits on the orchestrator landing the
open-packs change.

Shared files and their owning WP: `contracts/packs/file-kinds.yaml` (WP03 adds rules; WP06 swaps exclusions for
`retired`), `expected-red.yaml` and `REQUIRED_KINDS` (WP06), `KindBindings.java`/`YamlFormats.java` (WP03),
`PackFileKind.java`/`PackFileSet.java` (WP04), `HexagonalArchitectureTest.java` (WP01). Edits to files #260 also
touches stay in one block and leave #260's entries alone.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | `:missions` module: build file, settings, charter-reports, `:packs` dependency | WP01 | |
| T002 | Domain: `MissionType`, `MissionTypeIdentifier`, `MissionStep`, values, invariants | WP01 | |
| T003 | Domain tests: accepted/refused pairs per invariant | WP01 | |
| T004 | ArchUnit: `SLICES`, `DOMAIN_RINGS`, purity rule and bad fixture | WP01 | [P] |
| T005 | `ArtifactKind.MISSION_TYPE`, `NodeKind` backing, kernel tests | WP02 | |
| T006 | `mission-type.schema.json`; schema fixture tests | WP02 | |
| T007 | `file-kinds.yaml` `artifact_kinds`; pack-manifest constituent enum; `VocabularyEqualityTest`, inspector list | WP02 | |
| T008 | Companion structure schemas and `files` rules | WP03 | |
| T009 | `MissionTypeDso`, `MissionTypeYaml`, `MissionTypeBinding`, registry append | WP03 | |
| T010 | Placement check in `FileStems`; nested create in `FilesystemPackStore.newFile` | WP03 | |
| T011 | Fixture round trips, derived paths, template paths, committed positive controls | WP03 | |
| T012 | `GeneratedPackTree` mission-type shape; `PackFileKindTest` samples | WP03 | [P] |
| T013 | `Retired` classification; `PackFileSet` listing, digest tail | WP04 | |
| T014 | Store violation, validate-mode finding, `claimedByRule`, gate census bucket | WP04 | |
| T015 | Unknown top-level key refusal in `PackFileKind.parse` | WP04 | [P] |
| T016 | Retired fixtures (every family), fingerprint test, retired-name source scan | WP04 | |
| T017 | `migration` source set and task; legacy reader; mapper | WP05 | |
| T018 | Independent `FieldMappingVerifier` and its self-test | WP05 | |
| T019 | Run on the pin; report, diffs, patch bundle; open-packs issue drafts; send to the orchestrator | WP05 | |
| T020 | Re-pin, re-vendor `packs/qa`, `file-kinds` exclusions to `retired`, README exclusion | WP06 | |
| T021 | `expected-red` narrowed, `REQUIRED_KINDS`, `MissionTypeCorpusTest`, `MarkdownCorpusTest` | WP06 | |
| T022 | Harness, source set and task deleted; guard extended; gates and `verifyAll` | WP06 | |
| T023 | Divergence register, contract CHANGELOG and `/relationships` scenario | WP07 | |
| T024 | Architecture, glossary, context, contracts README, #186 follow-ups, changelog | WP07 | [P] |

## WP01 — `:missions` module and domain
Depends on: WP02. Requirements: FR-005, FR-006. Subtasks T001–T004.

## WP02 — Kernel kind, schema and vocabulary
Depends on: none. Requirements: FR-001, FR-004. Subtasks T005–T007.

## WP03 — Mission-type binding and companions
Depends on: WP01. Requirements: FR-002, FR-003, FR-007. Subtasks T008–T012.

## WP04 — Retired files are refused by name
Depends on: WP03. Requirements: FR-003, FR-008, NFR-003. Subtasks T013–T016.

## WP05 — Migration harness and open-packs bundle
Depends on: WP03 (the harness needs the shape, not the retired mechanism; it runs beside WP04). Requirements: FR-010, SC-004. Subtasks T017–T019.

## WP06 — Re-pin and gates
Depends on: WP04, WP05. Requirements: FR-009, FR-011, SC-001, SC-002, SC-003. Subtasks T020–T022.

## WP07 — Documentation
Depends on: WP06. Requirements: FR-012, NFR-001. Subtasks T023–T024.

## Closeout items

- History cleanup: move `api(project(":missions"))` in `service/packs/build.gradle.kts` from the WP02 fix commit into
  WP01's commit (WP02 review feedback 2).
