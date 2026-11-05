# Mission Specification: Mutation testing floors, and PIT on packs

**Mission Branch**: `test-mutation-floors`
**Created**: 2026-11-05
**Status**: Draft
**Input**: Operator brief (2026-11-04): #239 adopted as designed (the assessment's R1, W1), with the operator's
rulings of 2026-11-04 and 2026-11-05. Intent confirmed in decision `01M6NSEB00FQ43TR39ES2BPVKE`.

## Intent Summary

- **Actor:** the operator and contributors, who rely on the nightly mutation run to say whether the tests still
  notice a broken behaviour.
- **Trigger:** today the nightly runs PIT on kernel, missions and profiles only, gates nothing, keeps only
  kernel's report, and never mutates `packs`, which holds about 90 % of the code.
- **Outcome:**
  - each mutated module has a fixed floor, and a score below it fails that module's mutation task, naming the
    module;
  - `packs` domain, application and storage code are mutated, with their own floors;
  - the nightly reports every module and opens an issue when it goes red;
  - an ADR ends the "experiment" status.
- **Invariant:** mutation testing never runs on a pull request or in the required check; it stays a nightly. A
  floor is raised by a PR when the score clears it by 5 points, and never lowered without an ADR amendment. A
  surviving mutant is never killed by a test whose only purpose is killing it.
- **Rulings:**
  - Floors: kernel 65, missions 70, profiles 50.
  - Packs floors: `floor5(score − 3)` from the first measured run, reported to the operator before they are
    written.
  - `packs.infra.storage` is in scope with its own floor, in the same `:packs:pitest` run as
    domain+application.
  - Ship the ruled floors now; raise kernel and profiles after #384 merges.
- **Amendment (2026-11-05)**: operator ruling at the pre-PR squad. `packs.infra.fetch.*` is in scope too, as a third
  packs group `infra.fetch` in the same `:packs:pitest` run, without `infra.fetch.config` (the Spring wiring) and with
  its unit tests in `targetTests`. Its first run scored 77.3 % (208 of 269), so its floor is `floor5(score − 3)` = 70.
  The packs `test` and `pitest` tasks now share one definition of the excluded tags, system properties and repository
  inputs. Pull requests that touch `scripts/ci/` or `mutation-nightly.yml` run the summary script's unit tests
  (`docs-cleanliness.yml`, through `ci.yml`), never PIT. FR-002 and FR-003 stand as first written.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A falling mutation score turns the nightly red (Priority: P1)

A contributor weakens an assertion, and a module's mutation score drops below its floor. The next nightly fails
that module's mutation task with a message that names the module (and, for `packs`, the package group), its score
and its floor. The run opens one `mutation-nightly` issue, or comments on the open one.

**Why this priority**: it is the gap W1 names: today a falling score goes unnoticed.

**Independent Test**: lower a test's assertion locally (or raise a floor above the score) and run `./gradlew pitest`.
The module's task fails and names the module.

**Acceptance Scenarios**:

1. **Given** a module scoring below its floor, **When** `./gradlew pitest --continue` runs, **Then** that module's
   mutation task fails naming module, score and floor, and the other modules still run.
2. **Given** every module at or above its floor, **When** it runs, **Then** it is green and reports each score.

### User Story 2 - The packs logic is mutated (Priority: P1)

The nightly mutates `packs.domain`, `packs.application` and `packs.infra.storage` with their own unit tests and
reports their scores against their floors.

**Why this priority**: the logic lives there.

**Independent Test**: `./gradlew :packs:pitest` produces a report covering those packages and no others.

**Acceptance Scenarios**:

1. **Given** the packs module, **When** its mutation task runs, **Then** only classes under the three target
   packages are mutated, excluding `application.config.*` and `infra.api.*`, and Spring-wired tests are not run.
2. **Given** strict dependency verification, **When** the plugin is applied to packs, **Then** the build still
   verifies every artifact.

### User Story 3 - The nightly reports every module (Priority: P2)

The operator opens the nightly run and sees, per module, the score, test strength, no-coverage count and top
surviving classes in the job summary. Every module's report is in the artifact.

**Independent Test**: a `workflow_dispatch` run shows the summary for all four modules and one artifact with
four reports.

### Edge Cases

- PIT's `mutations.xml` contains raw `<` in mutator descriptions. The summary and the floor check must not parse
  it strictly.
- `--continue` must let every module report, even after one fails.
- A forced failure under `workflow_dispatch` opens or updates exactly one `mutation-nightly` issue. A scheduled
  run that is green does not touch the issue.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | One mutation convention | As a contributor, I want PIT's versions, output formats, `timestampedReports = false`, mutators and threads in a `charter.mutation-conventions` plugin that `charter.domain-conventions` and `packs` apply, so that every mutated module is configured alike. | High | Open | [build] | no — kernel, missions and profiles produce the same report shape as today; packs gains one |
| FR-002 | PIT on packs | As the operator, I want `packs.domain.*`, `packs.application.*` and `packs.infra.storage.*` mutated with their unit tests (excluding `application.config.*`, `infra.api.*` and Spring-wired tests), the dependency-verification entries added deliberately, and `pit-targets.init.gradle` reconciled, so that the logic is mutated. | High | Open | [build] | no — the packs report lists only the three groups |
| FR-003 | Fixed floors | As the operator, I want a fixed floor per module (kernel 65, missions 70, profiles 50; packs domain+application and packs infra.storage at `floor5(score − 3)` of their first run), checked after each mutation task, failing it with module or group, score and floor named, so that a falling score is red. | High | Open | [build] | no — a floor above the score fails the task; at or below it, it passes |
| FR-004 | Nightly reports and alerts | As the operator, I want the nightly to run `pitest --continue`, write a job summary per module without strict XML parsing, upload every module's report, and on a failed scheduled run open or update one `mutation-nightly` issue, so that a red run is visible. It stays nightly and manual only. | High | Open | [build] | no — a dispatch run with a forced failure opens or updates exactly one issue |
| FR-005 | ADR and docs | As a maintainer, I want an ADR "Nightly mutation floor" that ends the experiment status (DM-01M57RMMBAS7275PXGNSYJZBQY), and the service build guide, the workflow header and the FR-009 trace to match what runs. | Medium | Open | [build] | no — the docs checks and a reader comparing docs to the build agree |
| FR-006 | Kernel survivors triaged | As a contributor, I want each surviving kernel mutant triaged: killed by strengthening an existing test, marked equivalent with a reason, or accepted with an owner, so that the score is honest. | Medium | Open | [build] | no — the triage table covers every survivor named in the PR |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Never on a PR | `./gradlew verifyAll --dry-run` lists no pitest or floor-check task; the nightly workflow has no `pull_request` or `merge_group` trigger. | Reliability | High | Open |
| NFR-002 | Nightly duration | The nightly mutation job finishes within its `timeout-minutes` with at least 25 % headroom on the measured run. | Performance | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Floors as ruled | Kernel 65, missions 70, profiles 50; packs floors are reported to the operator before they are written. | Business | High | Open |
| C-002 | No test only for a mutant | A surviving mutant is never killed by a new test whose only purpose is to kill it (`DIRECTIVE_041`). | Technical | High | Open |
| C-003 | Strict verification stays | New artifacts get their checksums through `--write-verification-metadata` additively; no hand edits. | Technical | High | Open |
| C-004 | Overlap | #377 regenerated the metadata from scratch; #368 edits `charter.hand-written.gradle.kts`. Keep hunks minimal and rebase on whichever lands first, regenerating additively. | Technical | Medium | Open |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 4 of 4 mutated modules have a floor that fails their task when the score drops below it. — [build] · no-op passable: no
- **SC-002**: the nightly summary shows 4 modules (5 rows, with packs split in two) and the artifact holds 4 reports. — [build] · no-op passable: no
- **SC-003**: 0 pitest tasks in `verifyAll`'s task graph. — [ratchet] · no-op passable: yes (paired with SC-001)
