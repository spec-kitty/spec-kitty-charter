# Work Packages: Whole-pack gate known-failure list

**Inputs**: Design documents from `kitty-specs/whole-pack-known-failures-01M5Z72Y/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Required by the spec (NFR-001, NFR-002, SC-001); WP01 writes them first.

**Organization**: Subtasks (`Txxx`) roll up into work packages (`WPxx`). Record completion with
`spec-kitty agent tasks mark-status <Txxx> --status done`; there are no checkboxes.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Ship `known-failures.yaml` (empty) with its header comment | WP01 | |
| T002 | Write the failing tests: four acceptance cases, moved difference, refusal match | WP01 | |
| T003 | Write the failing tests: load refusals, stale/unmodelled/ambiguous entries, real-list load | WP01 | |
| T004 | `KnownFailure` record, 3-arg `load`, issue-format check, `fromClasspath` reads three files | WP01 | |
| T005 | `Status.KNOWN_FAILURE` and its judgement in `evaluate`, with the whole-list checks | WP01 | |
| T006 | Report: label, totals, last per-kind column; report tests | WP01 | |
| T007 | Run `:packs:test`, `:packs:wholePackRoundTrip` and the style gates; record counts | WP01 | |
| T008 | Gate docs: the known-failure list and its workflow in `docs/guides/service-build.md` | WP02 | [P] |
| T009 | Changelog entry and index line | WP02 | [P] |
| T010 | Docs checks: `check_docs.py`, markdownlint-cli2, cspell | WP02 | |

---

## Work Package WP01: Known-failure list, judgement and report (Priority: P1)

**Goal**: The gate loads `whole-pack/known-failures.yaml`, judges listed files as known failures, expires them when they pass, refuses malformed or misplaced entries, and counts them in the report.
**Independent Test**: `./gradlew :packs:test --tests '*WholePackExpectationTest*'` passes, and the four #247 acceptance cases fail when the new code is removed.
**Prompt**: `tasks/WP01-known-failure-list-judgement-and-report.md`
**Requirement Refs**: FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, NFR-001, NFR-002, NFR-003, C-001, C-002, C-003

### Included Subtasks

T001 Ship `known-failures.yaml` (empty) with its header comment (WP01)
T002 Write the failing tests: four acceptance cases, moved difference, refusal match (WP01)
T003 Write the failing tests: load refusals, stale/unmodelled/ambiguous entries, real-list load (WP01)
T004 `KnownFailure` record, 3-arg `load`, issue-format check, `fromClasspath` reads three files (WP01)
T005 `Status.KNOWN_FAILURE` and its judgement in `evaluate`, with the whole-list checks (WP01)
T006 Report: label, totals, last per-kind column; report tests (WP01)
T007 Run `:packs:test`, `:packs:wholePackRoundTrip` and the style gates; record counts (WP01)

### Implementation Notes

- Test first: T002 and T003 go red before T004–T006 turn them green.
- Additive only: a new nested test class, a delegating 2-arg `load`, the enum value and column appended last.

### Parallel Opportunities

- None inside the WP (one class and its test).

### Dependencies

- None.

### Risks & Mitigations

- Sibling Wave 1 missions edit `expected-red.yaml` and may add tests: never touch `expected-red.yaml`; rebase on `main` before review.
- The `Status` switch in `WholePackReport.label` is exhaustive: the enum value and the label must land together.

---

## Work Package WP02: Known-failure workflow docs and changelog (Priority: P2)

**Goal**: Contributors can read when to use the list and how (file the open-packs issue, add the entry, remove it with the pin bump), and the change is announced.
**Independent Test**: The docs checks pass and the gate section names every new problem code WP01 introduced.
**Prompt**: `tasks/WP02-known-failure-workflow-docs-and-changelog.md`
**Requirement Refs**: FR-009

### Included Subtasks

T008 Gate docs: the known-failure list and its workflow in `docs/guides/service-build.md` (WP02)
T009 Changelog entry and index line (WP02)
T010 Docs checks: `check_docs.py`, markdownlint-cli2, cspell (WP02)

### Implementation Notes

- Write after WP01 is approved so the docs quote the real problem codes and report labels.

### Parallel Opportunities

- T008 and T009 touch different files.

### Dependencies

- Depends on WP01.

### Risks & Mitigations

- The changelog index is shared with sibling missions: add one line at the top, never reorder others.
