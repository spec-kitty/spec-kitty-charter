# Tasks: Nightly SonarCloud and per-PR complexity gate

**Mission**: `ci-sonar-nightly-checkstyle-complexity-01M6HQ46`
**Branch**: planned on `ci-sonar-nightly-checkstyle-complexity`; completed work merges into
`ci-sonar-nightly-checkstyle-complexity`, which reaches `main` by pull request.
**Inputs**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md),
[data-model.md](data-model.md), [quickstart.md](quickstart.md)

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Add the four complexity modules and `SuppressionXpathFilter` to `checkstyle.xml`; switch the register to the XPath DTD | WP01 | |
| T002 | Measure every (rule, method) pair over the limits on main and test | WP01 | |
| T003 | Write one `suppress-xpath` entry per pair, with rule, file, reason and sunset, and the Size count | WP01 | |
| T004 | Prove each limit bites with a planted method, then remove it; record the evidence | WP01 | |
| T005 | Pure register parser (entries, comment fields, Size) | WP02 | [P] |
| T006 | Pure comparator: probe report and sources against the register (stale, missing field, wrong Size) | WP02 | [P] |
| T007 | Unit tests for the parser and the comparator | WP02 | |
| T008 | Gradle wiring: probe config, probe Checkstyle tasks over the register's files, verify task in `check` | WP02 | |
| T009 | End-to-end proof: a planted stale entry fails `verifyAll`, and its removal passes | WP02 | |
| T010 | `.github/workflows/sonar.yml`: triggers, repository and branch guard, token probe that skips green | WP03 | [P] |
| T011 | Build and scan steps: syncPacks, verifyAll, scan action with binaries, coverage and exclusions; quality gate in the summary | WP03 | |
| T012 | Guard test: no PR or merge-queue triggers, not called by `ci.yml`, not in the gate's `needs`, actions SHA-pinned | WP03 | |
| T013 | Validate the workflow (YAML parse, pin check) and record the skip-green dry reasoning | WP03 | |
| T014 | `docs/guides/service-build.md`: the limits, the register and its stale check, the nightly report | WP04 | |
| T015 | Changelog entry | WP04 | [P] |
| T016 | Docs gate clean; spelling words added in sorted position | WP04 | |

## Phase 1 — Per-PR complexity gate

### WP01 — Complexity limits and the method-scoped register (P1, MVP)

- **Goal:** the four limits fail the gate on new code; today's violators are listed method by method.
- **Prompt:** [tasks/WP01-complexity-limits-and-register.md](tasks/WP01-complexity-limits-and-register.md)
- **Independent test:** `verifyAll` passes; a planted over-limit method fails it (FR-001, SC-001).
- **Requirements:** FR-001, FR-002, NFR-001, C-001, C-003, SC-001, SC-002
- **Dependencies:** none

T001 Add the four complexity modules and the XPath filter (WP01)
T002 Measure every (rule, method) pair over the limits (WP01)
T003 Write the register entries and the Size count (WP01)
T004 Prove each limit bites with a planted method (WP01)

- **Risks:**
  - An XPath that matches nothing exempts nothing, so the build fails visibly.
  - Overloaded methods need a parameter-aware query, or a shared entry with the reason stated.

### WP02 — Stale-entry and register-format check (P1)

- **Goal:** the register can only shrink. A stale entry, a missing field or a wrong Size fails `check`.
- **Prompt:** [tasks/WP02-register-stale-check.md](tasks/WP02-register-stale-check.md)
- **Independent test:** the unit tests, plus a planted stale entry that fails `verifyAll` (FR-002, SC-002).
- **Requirements:** FR-002, NFR-001, C-001, C-003, SC-002
- **Dependencies:** WP01

T005 Pure register parser (WP02)
T006 Pure comparator (WP02)
T007 Unit tests (WP02)
T008 Gradle wiring into `check` (WP02)
T009 End-to-end planted-stale proof (WP02)

- **Risks:** configuration-cache safety, and the probe's cost (it runs only over the register's files).

## Phase 2 — Nightly SonarCloud

### WP03 — Nightly reported-only SonarCloud workflow (P2)

- **Goal:** a scheduled, never-required analysis of `main` that skips green without the token.
- **Prompt:** [tasks/WP03-nightly-sonar-workflow.md](tasks/WP03-nightly-sonar-workflow.md)
- **Independent test:** the guard test passes; the workflow parses; the token probe skips green
  (FR-003 to FR-005, SC-003, SC-004).
- **Requirements:** FR-003, FR-004, FR-005, NFR-002, NFR-003, NFR-004, C-001, C-002, C-004, SC-003, SC-004
- **Dependencies:** none (parallel with WP01 and WP02)

T010 Workflow triggers, guard and token probe (WP03)
T011 Build, scan and quality-gate steps (WP03)
T012 Guard test (WP03)
T013 Validate the workflow (WP03)

- **Risks:** the analysis path is untested until the token exists, and the project key is an
  assumption.

## Phase 3 — Polish

### WP04 — Documentation (P3)

- **Goal:** contributors know the limits, how to shrink the register, and where the nightly report is.
- **Prompt:** [tasks/WP04-documentation.md](tasks/WP04-documentation.md)
- **Requirements:** FR-006, C-005
- **Dependencies:** WP01, WP02, WP03

T014 Service-build guide section (WP04)
T015 Changelog entry (WP04)
T016 Docs gate and spelling (WP04)

## Parallelization

WP03 is independent of WP01 and WP02 and can run in parallel with them. WP02 needs WP01's register
format. WP04 comes last.

## MVP

WP01 alone delivers the blocking complexity gate: the operator's per-PR ruling.
