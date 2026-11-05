# Mission Specification: Nightly SonarCloud and per-PR complexity gate

**Mission Branch**: `ci-sonar-nightly-checkstyle-complexity`
**Created**: 2026-11-03
**Status**: Draft
**Input**: Operator ruling of 2026-11-03 on the test suite assessment
(`docs/plans/engineering-notes/test-suite-assessment-2026-11-03.md`, recommendations R5 and R6, PR #366),
recorded as Proposed ADR-0056: "Nightly Sonar, per PR CheckStyle"; new-code coverage reported through
SonarCloud instead of a coverage threshold.

> **Superseded in part at landing (2026-11-05).** The operator ruled that there is no grandfathering
> (ADR-0058), so the complexity half of this mission did not land: User Story 1, FR-001, FR-002, SC-001,
> SC-002, NFR-001 and the limits part of FR-006. No limit, register entry or stale check ships. The limits
> turn on through #371, once #367 has refactored the methods over them. The nightly SonarCloud analysis
> (User Story 2, FR-003 to FR-005) landed, with the landing's fixes to its guard and classpath. Six methods
> were also refactored, which this spec does not ask for. "Proposed ADR-0056" in this mission's files is
> today's Accepted ADR-0058; today's ADR-0056 is a different decision.

## Intent Summary (confirmed 2026-11-03)

- **Primary actors:** a contributor who opens a pull request, and the operator, who reads the nightly
  quality report.
- **Trigger and outcome:** a pull request that adds an overly complex or overly long method fails the
  required gate it already runs. Every night the merged code on `main` is analysed by SonarCloud, and the
  result is visible without ever blocking anyone.
- **Invariant:** the nightly analysis is reported only. It is never a required check, never runs on a
  pull request or in the merge queue, and never fails the night because the SonarCloud project or its
  token does not exist yet.
- **Decisions:** the limits are core-aligned (decision `01M6HQ9K8ZPB1W0PS5CNDEE9WR`), and the scope is
  as listed below (decision `01M6JHD5KXEKETR34N646Q79SK`).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A too-complex method fails the pull request (Priority: P1)

A contributor adds or changes a method so that its complexity, path count, length or nesting depth goes
past the agreed limit. The pull request's required gate fails, and the failure names the method and the
limit. The methods that already exceed a limit today are listed in the one exception register with a
reason, and that list can only shrink.

**Why this priority**: this is the blocking half of the ruling, and it needs no new tool, account or
secret.

**Independent Test**: plant a method that exceeds each limit and watch the build fail. Remove it and watch
the build pass. Add an entry to the register without a reason, or for a method that no longer exceeds the
limit, and watch the build fail.

**Acceptance Scenarios**:

1. **Given** the gate with the agreed limits, **When** a pull request adds a method of cyclomatic
   complexity 16, **Then** the build fails and names that method.
2. **Given** a method already listed in the register, **When** it is simplified below every limit,
   **Then** its register entry is stale and the build tells the contributor to remove it.
3. **Given** today's code, **When** the full build runs, **Then** it passes with only the listed
   exceptions.

---

### User Story 2 - The nightly SonarCloud report (Priority: P2)

Every night, and whenever the operator dispatches it by hand, the merged code on `main` is built and
analysed by SonarCloud, with coverage and new-code coverage. The quality-gate verdict appears in the run's
summary.

**Why this priority**: it covers what the gate cannot measure (duplication, security hotspots, new-code
coverage), but it depends on a project and a token the operator creates later.

**Independent Test**: dispatch the workflow without a token and see it finish green with a notice. Once
the operator has added the token, dispatch it again and see an analysis, with coverage, in SonarCloud.

**Acceptance Scenarios**:

1. **Given** no `SONAR_TOKEN` secret, **When** the nightly run starts, **Then** it finishes green with
   a notice that says the analysis was skipped and why.
2. **Given** a token and a project, **When** the nightly run finishes, **Then** SonarCloud shows the
   analysis with the service modules' coverage, and the run summary shows the quality-gate verdict.
3. **Given** a failing analysis or a failing quality gate, **When** the run ends, **Then** the failure is
   visible in the summary, and no required check and no pull request is affected.

### Edge Cases

- **A pull request or the merge queue:** the nightly analysis never runs there.
- **A fork:** the nightly runs only on this repository's `main`.
- **A build failure before the scan:** the run reports it in the summary and does not fail
  the night.
- **Generated code:** the generated `charter-api` sources are not analysed.
- **A method between today's register and the limit:** a new method may not take an
  exception; only a listed method may exceed the limit.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Complexity limits in the per-PR gate | As a contributor, I want a method over cyclomatic complexity 15, path count 1000, 60 lines or nested-if depth 1 to fail the required gate, so that complexity cannot grow unseen. Proved by a planted over-limit method that fails the build. | High | Open | build | no |
| FR-002 | Shrink-only exception register | As a maintainer, I want every method that exceeds a limit today to be listed once in the existing single exception register, in its convention (rule, file, reason, sunset, and the Size count), and a stale entry, an entry missing a field, or a wrong Size to fail the build, so that the debt can only go down. | High | Open | build | no |
| FR-003 | Nightly SonarCloud analysis of `main` | As the operator, I want a scheduled and manually dispatchable run that builds trusted code from `main` and analyses it with SonarCloud, with the service modules' compiled classes and the aggregated coverage report, and with generated sources excluded, so that duplication, security hotspots and new-code coverage are reported. | High | Open | build | no |
| FR-004 | Skip green without a token | As the operator, I want the nightly run to finish green with a notice when the token is absent, so that the workflow can land before the SonarCloud project exists. Proved by a dispatched run without the secret. | High | Open | build | no |
| FR-005 | Reported only, never required | As a contributor, I want the nightly analysis never to be part of the required gate, never to run on pull requests or in the merge queue, and never to fail the night, with its quality-gate verdict in the run summary. A guard checks the workflow triggers and the gate's needs. | High | Open | build | no |
| FR-006 | Documentation | As a contributor, I want the build guide to say which complexity limits apply and where the nightly report is, and a changelog entry to record the change. ADR-0056 stays Proposed for the operator to accept. | Medium | Open | build | yes |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Per-PR cost | The complexity checks add no new job, and add under 30 s to `verifyAll` on a CI runner. | Performance | High | Open |
| NFR-002 | Nightly cost | The nightly run completes in under 20 minutes, with a hard timeout of 30 minutes. | Performance | Medium | Open |
| NFR-003 | Token isolation | No pull-request-authored code is ever built or run in a job that can read `SONAR_TOKEN`: 0 jobs with a `pull_request`, `pull_request_target` or `merge_group` trigger in the workflow. | Security | High | Open |
| NFR-004 | Pinned actions | 100 % of third-party actions in the new workflow are pinned to a full commit SHA, with the version in a comment. | Security | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | No new build plugin | No new Gradle plugin and no new library: the gate uses the Checkstyle the build declares, and the analysis uses the generic scan action. | Technical | High | Open |
| C-002 | Required check unchanged | `CI gate / gate` stays the one required check (ADR-0023); workflows that produce no required check stay off `merge_group` (ADR-0047). | Technical | High | Open |
| C-003 | One exception register | `config/checkstyle/suppressions.xml` stays the single register for every gate's exceptions. | Technical | High | Open |
| C-004 | Out of scope | Not in scope: the #239 mutation floors, a per-PR SonarCloud analysis, and creating the SonarCloud project or token. | Business | High | Open |
| C-005 | Commit and docs rules | Commits carry `Co-Authored-By: Stijn Dejongh <stijn.dejongh@sddevelopment.be>` and no model identifier; the docs gate stays clean. | Business | High | Open |

### Key Entities

- **Complexity limit**: one measured property of a method (cyclomatic complexity, path count, length,
  nested-if depth) and its maximum.
- **Register entry**: one method allowed to exceed one limit, with the reason and the shrink-only rule.
- **Nightly analysis**: one run over `main`, its coverage input, and its reported quality-gate verdict.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A pull request that adds a method over any of the four limits fails the required gate, as
  shown by four planted methods, one per limit. — build · no-op passable: no
- **SC-002**: The register holds at most 15 entries when the mission lands, and none is stale. — build ·
  no-op passable: no
- **SC-003**: A dispatched nightly run without a token finishes green with a skip notice. — build ·
  no-op passable: no
- **SC-004**: Zero required checks change, and zero pull-request or merge-queue runs start the nightly
  analysis. — ratchet · no-op passable: yes, so paired with SC-003 as the positive control

## Assumptions

- Core's pinned `SonarSource/sonarqube-scan-action` and `sonarqube-quality-gate-action` versions are
  suitable here; the pins are copied, not re-chosen.
- The SonarCloud project key will be `spec-kitty_spec-kitty-charter` in the `spec-kitty` organization,
  following core's naming. The operator confirms it when creating the project.
- A limit of 1 for nested-if depth is Checkstyle's default and today has 4 exceptions.
