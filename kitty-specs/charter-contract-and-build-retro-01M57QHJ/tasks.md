---
description: "Work packages for the retroactive charter contract and build mission"
---

# Work Packages: Charter contract skeleton and Gradle build (retroactive)

**Inputs**: `kitty-specs/charter-contract-and-build-retro-01M57QHJ/` (spec.md, plan.md, research.md, quickstart.md, decisions/)
**Tests**: required by the charter (test first, scenarios first, planted-violation proofs for every gate).

Subtasks are reference rows: record completion with
`spec-kitty agent tasks mark-status <Txxx> --status done`.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Contract scenarios first: snake_case codes, tags, sorted order, code-plus-pack assertions, new scenarios | WP01 | |
| T002 | Contract documents: 1.0.0-SNAPSHOT, snake_case enums, x-provisional, `via`, descriptions, citations, changelog, scenarios README | WP01 | |
| T003 | The charter's lint ruleset and planted fixtures | WP01 | [P] |
| T032 | The contract-checks Gradle project (pinned vacuum/oasdiff; lint, bundle, compatibility) | WP01 | |
| T033 | The recursive response-closure checker in Java (replaces the Spectral rule) | WP01 | |
| T004 | Domain and REST: wire tokens and the enum parity test | WP01 | |
| T005 | ActionContextQuery: core's sort, comments, dangling-target pin | WP01 | |
| T006 | Open-packs round-trip asserts per-pack diagnostics | WP01 | [P] |
| T007 | Cucumber suite selects `@contract`; glue for any new step | WP01 | |
| T008 | Verify: verifyAll on Java 25 with the contract checks | WP01 | |
| T009 | Split conventions: `charter.java-base` and opt-in `charter.hand-written`; narrow the scenario classpath | WP02 | |
| T010 | Catalog, plugins and verification metadata for the vetted tools | WP02 | |
| T011 | Formatting-only commit with palantir-java-format | WP02 | |
| T012 | Checkstyle configuration and the suppressions register | WP02 | |
| T013 | Error Prone and NullAway in JSpecify mode; `@NullMarked` packages; nullness fixes | WP02 | |
| T014 | Javadoc with doclint as errors, wired into `check`; document every public member | WP02 | |
| T015 | JaCoCo per module, PIT configured outside `check`, Mockito with one real use | WP02 | |
| T016 | Planted-violation proofs and a green verifyAll | WP02 | |
| T017 | Reports project: aggregated Javadoc and JaCoCo report | WP03 | |
| T018 | Javadoc stylesheet mapped to the docsite tokens; back link | WP03 | |
| T019 | Docsite page for the API reference; site staging and ignore | WP03 | |
| T020 | `check_docs.py` allow-prefix for `/javadoc/` with a unit test and doc rule | WP03 | [P] |
| T021 | Local proof of the staged site and the docs checks | WP03 | |
| T022 | service.yml: runner pin, `workflow_call`, Javadoc and coverage artifacts | WP04 | |
| T023 | Contract bundle artifact | WP04 | |
| T024 | docs-site.yml publishes the Javadoc from the service build | WP04 | |
| T025 | Nightly mutation workflow | WP04 | [P] |
| T026 | Workflow validation: actionlint, pinned action SHAs | WP04 | |
| T027 | Build and contributor guides and README | WP05 | |
| T028 | Open questions and roadmap | WP05 | |
| T029 | Architecture and API docs follow the rulings | WP05 | |
| T030 | Charter companion, changelog entry, spelling words | WP05 | |
| T031 | Docs checks green | WP05 | |

---

## Work Package WP01: Contract to 1.0.0-SNAPSHOT, scenarios first (Priority: P1)

**Goal**: Carry the operator's rulings into the charter contract and the service and give the charter its own contract checks (lint, bundle and breaking-change checks.
**Independent Test**: `./gradlew verifyAll` green on Java 25; the charter's contract checks pass, and each lint rule fails them.
**Prompt**: `tasks/WP01-contract-scenarios-first.md`
**Requirement Refs**: FR-003, FR-004, C-001, C-002

### Included Subtasks

T001 Contract scenarios first (WP01)
T002 Contract documents (WP01)
T003 The charter's lint ruleset and planted fixtures (WP01)
T032 The contract-checks Gradle project (WP01)
T033 The recursive closure checker in Java (WP01)
T004 Domain and REST wire tokens; enum parity test (WP01)
T005 ActionContextQuery sort, comments, dangling-target pin (WP01)
T006 Open-packs round-trip asserts per-pack diagnostics (WP01)
T007 Cucumber suite selects `@contract` (WP01)
T008 Verify (WP01)

### Dependencies

- None.

### Risks & Mitigations

- The ratified pack-level behavior must not change: only the code spellings move.
- Case mapping drift: the parity test pins every Java enum against its contract enum.

---

## Work Package WP02: Quality gates behind one opt-in plugin (Priority: P1)

**Goal**: Add the operator-vetted gates so generated code is exempt by construction and the hand-written code passes them with no suppression debt.
**Independent Test**: verifyAll green; each gate fails on its planted violation.
**Prompt**: `tasks/WP02-quality-gates.md`
**Requirement Refs**: FR-006, FR-007, FR-008, FR-009, FR-010, FR-011, NFR-001, NFR-003, C-003, C-004, C-005, C-008

### Included Subtasks

T009 Split conventions; narrow the scenario classpath (WP02)
T010 Catalog, plugins and verification metadata (WP02)
T011 Formatting-only commit (WP02)
T012 Checkstyle and the suppressions register (WP02)
T013 Error Prone and NullAway (WP02)
T014 Javadoc with doclint as errors (WP02)
T015 JaCoCo, PIT outside `check`, Mockito (WP02)
T016 Planted-violation proofs; green verifyAll (WP02)

### Dependencies

- Depends on WP01.

### Risks & Mitigations

- Error Prone on JDK 25 needs `--add-exports`/`--add-opens` and a supporting release.
- Formatting churn: one formatting-only commit, nothing else in it.

---

## Work Package WP03: Javadoc site and docsite page (Priority: P2)

**Goal**: One aggregated, searchable Javadoc for the hand-written modules, styled like the docsite and reachable from its sidebar.
**Independent Test**: the staged site contains the Javadoc and the API page links to it; `check:built-links` and `check_docs.py links` pass.
**Prompt**: `tasks/WP03-javadoc-site.md`
**Requirement Refs**: FR-011, FR-012

### Included Subtasks

T017 Reports project (WP03)
T018 Javadoc stylesheet; back link (WP03)
T019 Docsite page; staging and ignore (WP03)
T020 `check_docs.py` allow-prefix (WP03)
T021 Local proof (WP03)

### Dependencies

- Depends on WP02.

---

## Work Package WP04: CI workflows (Priority: P1)

**Goal**: The main build workflow runs every gate, the charter's own contract checks included, on Java 25 and packages Javadoc, coverage and the bundle; the docsite publishes the Javadoc; mutation testing runs nightly.
**Independent Test**: workflows pass actionlint; every action pinned to a real release SHA; the first CI run on the pull request is green (checked at closeout).
**Prompt**: `tasks/WP04-ci-workflows.md`
**Requirement Refs**: FR-004, FR-005, FR-009, FR-012, NFR-002, C-003

### Included Subtasks

T022 service.yml changes (WP04)
T023 Contracts job (WP04)
T024 docs-site.yml (WP04)
T025 Nightly mutation workflow (WP04)
T026 Workflow validation (WP04)

### Dependencies

- Depends on WP01, WP02, WP03.

---

## Work Package WP05: Docs, plans and records (Priority: P2)

**Goal**: Every doc tells the truth about the build, the rulings and the scenario homes; the mission is recorded.
**Independent Test**: all eight docs checks, markdownlint and cspell report 0 errors.
**Prompt**: `tasks/WP05-docs-and-records.md`
**Requirement Refs**: FR-001, FR-002, FR-013, FR-014, FR-015, NFR-004

### Included Subtasks

T027 Guides and README (WP05)
T028 Open questions and roadmap (WP05)
T029 Architecture and API docs (WP05)
T030 Charter companion, changelog, spelling (WP05)
T031 Docs checks green (WP05)

### Dependencies

- Depends on WP01, WP02, WP03, WP04.

---

## Parallelization

The chain is sequential (WP01 → WP02 → WP03 → WP04 → WP05) because WP02's compliance
edits touch WP01's sources and every later package describes or wires the earlier ones.
Inside WP01, T003 and T006 can run beside the scenario work.
