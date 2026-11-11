# Tasks: Charter example packs replace the open-packs corpus

**Mission**: charter-example-packs-01M74GCF · **Branch**: `claude/adr-open-packs-456-458-adf2fo` (single branch, PR #474)

## Subtask Index

| ID | Description | WP | Parallel |
| --- | --- | --- | --- |
| T001 | Add the `java-test-fixtures` plugin to `:packs` and `testImplementation(testFixtures(project(":packs")))` to `:deployable` | WP01 |  |
| T002 | Author the `built-in` example pack | WP01 |  |
| T003 | Author the `org-base` example pack | WP01 |  |
| T004 | Author the `project` example pack | WP01 |  |
| T005 | Write `ExamplePacks` and its self-test | WP01 |  |
| T006 | Point `WholePackRoundTripTest` at `ExamplePacks.pack("built-in")` | WP02 |  |
| T007 | Point `WholePackOpenPacksRoundTripTest` at all three example packs | WP02 |  |
| T008 | Empty the expectation lists | WP02 |  |
| T009 | Keep the `whole-pack` tag and the `wholePackRoundTrip` task | WP02 |  |
| T010 | Schema-property coverage | WP03 | [P] |
| T011 | Artifact-kind coverage | WP03 | [P] |
| T012 | Refusal-code coverage | WP03 | [P] |
| T013 | Artifact round-trip tests | WP04 | [P] |
| T014 | Mission-type and step-contract tests | WP04 | [P] |
| T015 | Prompt-slot tests | WP04 | [P] |
| T016 | Remove the `open-packs` tag from these classes | WP04 | [P] |
| T017 | Composition test | WP05 | [P] |
| T018 | Companion and binary-payload tests | WP05 | [P] |
| T019 | Digest and manifest tests | WP05 | [P] |
| T020 | File-kind, inspector, level-schema and governance tests | WP05 | [P] |
| T021 | `ProfileCorpusTest` over the example packs | WP06 | [P] |
| T022 | `ProfileExamplesEdgeTest` and `PackGraphEdges` | WP06 | [P] |
| T023 | Contract examples | WP06 | [P] |
| T024 | Remove the corpus tasks and inputs | WP07 |  |
| T025 | Workflows | WP07 |  |
| T026 | Nightly drift watch | WP07 |  |
| T027 | Prove green without open-packs | WP07 |  |
| T028 | Amend the charter companion | WP08 |  |
| T029 | Amend PROJECT_002 | WP08 |  |
| T030 | File the open-packs validation-job issue | WP08 |  |

## WP01 — Example packs and the ExamplePacks locator

**Prompt**: `tasks/WP01-example-packs-and-locator.md` · **Dependencies**: none · **Requirements**: FR-001, NFR-002, C-002

Included subtasks:

T001 Add the `java-test-fixtures` plugin to `:packs` and `testImplementation(testFixtures(project(":packs")))` to `:deployable` (WP01)
T002 Author the `built-in` example pack (WP01)
T003 Author the `org-base` example pack (WP01)
T004 Author the `project` example pack (WP01)
T005 Write `ExamplePacks` and its self-test (WP01)

## WP02 — Whole-pack round trip over the example packs

**Prompt**: `tasks/WP02-round-trip-over-examples.md` · **Dependencies**: WP01 · **Requirements**: FR-002

Included subtasks:

T006 Point `WholePackRoundTripTest` at `ExamplePacks.pack("built-in")` (WP02)
T007 Point `WholePackOpenPacksRoundTripTest` at all three example packs (WP02)
T008 Empty the expectation lists (WP02)
T009 Keep the `whole-pack` tag and the `wholePackRoundTrip` task (WP02)

## WP03 — Coverage check over the example packs

**Prompt**: `tasks/WP03-coverage-check.md` · **Dependencies**: WP01 · **Requirements**: FR-003, SC-003

Included subtasks:

T010 Schema-property coverage (WP03)
T011 Artifact-kind coverage (WP03)
T012 Refusal-code coverage (WP03)

## WP04 — Re-point the artifact, mission-type and prompt-slot tests

**Prompt**: `tasks/WP04-artifact-and-mission-corpus-tests.md` · **Dependencies**: WP01 · **Requirements**: FR-004

Included subtasks:

T013 Artifact round-trip tests (WP04)
T014 Mission-type and step-contract tests (WP04)
T015 Prompt-slot tests (WP04)
T016 Remove the `open-packs` tag from these classes (WP04)

## WP05 — Re-point the composition, file and digest tests

**Prompt**: `tasks/WP05-composition-files-and-digests-tests.md` · **Dependencies**: WP01 · **Requirements**: FR-004

Included subtasks:

T017 Composition test (WP05)
T018 Companion and binary-payload tests (WP05)
T019 Digest and manifest tests (WP05)
T020 File-kind, inspector, level-schema and governance tests (WP05)

## WP06 — Re-point the profile tests in deployable

**Prompt**: `tasks/WP06-profile-tests-in-deployable.md` · **Dependencies**: WP01 · **Requirements**: FR-004

Included subtasks:

T021 `ProfileCorpusTest` over the example packs (WP06)
T022 `ProfileExamplesEdgeTest` and `PackGraphEdges` (WP06)
T023 Contract examples (WP06)

## WP07 — Build and CI without open-packs; nightly drift watch

**Prompt**: `tasks/WP07-build-and-ci-without-open-packs.md` · **Dependencies**: WP02, WP03, WP04, WP05, WP06 · **Requirements**: FR-005, FR-006, FR-007, NFR-001, C-003

Included subtasks:

T024 Remove the corpus tasks and inputs (WP07)
T025 Workflows (WP07)
T026 Nightly drift watch (WP07)
T027 Prove green without open-packs (WP07)

## WP08 — Governance amendments and the open-packs follow-up

**Prompt**: `tasks/WP08-governance-amendments.md` · **Dependencies**: none · **Requirements**: FR-008

Included subtasks:

T028 Amend the charter companion (WP08)
T029 Amend PROJECT_002 (WP08)
T030 File the open-packs validation-job issue (WP08)

## Parallelism

WP01 and WP08 start at once. WP02 to WP06 run in parallel after WP01. WP07 closes after WP02 to WP06.

## MVP

WP01 + WP02: the round-trip gate over charter-owned packs.
