# Mission Specification: Module-local test gaps and promise-first scenario verdicts

**Mission Branch**: `test-module-gaps-and-promise-scenarios`
**Created**: 2026-11-05
**Status**: Draft
**Input**: Operator brief (2026-11-04): #370 (R4; W1, W2) and #374 (R8) from the
[test suite assessment](../../docs/plans/engineering-notes/test-suite-assessment-2026-11-03.md). Intent confirmed in
decision `01M6NDJ4TCVQC6S2CB83FR0XVV`.

## Intent Summary

- **Actors:** a contributor changing `kernel`, `profiles` or the guide fetcher, and a maintainer who reads the
  scenario files as the service's specification.
- **Trigger:** each module's own tests reach less than the aggregate coverage suggests. Code that other
  modules' tests execute without asserting on it can regress unseen. Separately, 27 promise-first scenarios run
  under no runner and can drift from the service unseen.
- **Outcome:** the five named classes, and the untouched behaviour PIT shows around them, have direct tests that
  fail on a real regression. Every one of the 27 scenarios has a recorded verdict (map, wire or retire), and none
  is left that no runner executes and nothing covers.
- **Invariant:** a new test exists only because it guards real behaviour and fails on a real regression, shown by
  a planted break (`DIRECTIVE_041`, `QA_003`). There is no coverage threshold; the goal is the gaps, not a number.
- **Boundary:** for #370, tests only. A test that exposes a defect stops the work for an operator decision. For
  #374, a scenario whose behaviour nothing covers is listed for the operator to rule test gap or product gap.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A regression in kernel validation fails a kernel test (Priority: P1)

A contributor changes `Constrained` or `Rationale` so that an invariant is no longer enforced. Today a test in
another module may run that code without noticing. After this mission, a kernel test fails.

**Why this priority**: `kernel.validation` is the fail-fast invariant code every module leans on, and it has
58.3 % branch coverage.

**Independent Test**: plant a break in each invariant; the kernel test goes red.

**Acceptance Scenarios**:

1. **Given** a value that breaks an invariant, **When** it is constructed or validated, **Then** the kernel test
   sees the refusal and its reason.
2. **Given** a valid value on the same fixture, **When** it is constructed, **Then** it is accepted (positive
   control).

### User Story 2 - A regression in profiles fails a profiles test (Priority: P1)

A contributor changes `SelfReviewStep`, `ModeDefault` or another profiles domain class that PIT reports as untouched
by profiles' own tests. A profiles test fails.

**Why this priority**: profiles reports 99 % aggregate line coverage, but its own tests leave 29 of 66 mutants
untouched (53 % mutation score).

**Independent Test**: PIT's no-coverage count for profiles falls; each new test has a planted break.

**Acceptance Scenarios**:

1. **Given** the profiles module, **When** `pitest` runs, **Then** fewer mutants have no coverage than the
   baseline of 29, and every surviving or uncovered mutant left is triaged (killed, equivalent with a reason, or
   accepted with a reason).

### User Story 3 - The guide fetcher's body cap fails closed (Priority: P1)

A contributor breaks `CappedBodySubscriber` so that a body over the cap is accepted, or a body at the cap is
refused. A fetch test fails.

**Why this priority**: it is a fail-closed network boundary with no test of its own (77 % line coverage in
`packs.infra.fetch`).

**Independent Test**: planted breaks for over-cap acceptance and at-cap refusal turn the tests red.

**Acceptance Scenarios**:

1. **Given** a body one byte over the cap, **When** it is received, **Then** it is refused.
2. **Given** a body exactly at the cap, **When** it is received, **Then** it is accepted, byte for byte.
3. **Given** the stream fails or is cancelled, **When** the subscriber sees it, **Then** it completes exceptionally
   and keeps no partial body.

### User Story 4 - No scenario drifts unseen (Priority: P2)

A maintainer reads a promise-first scenario as the service's specification. Each one is either executed by the
Cucumber runner, shown to be covered by a named contract scenario, or retired with its covering scenario named.

**Why this priority**: the files look like specification but run nowhere.

**Independent Test**: a verdict table covers all 27; no file remains that no runner executes and nothing covers;
the README no longer says "not yet wired".

**Acceptance Scenarios**:

1. **Given** each of the 27 scenarios, **When** the gap analysis runs, **Then** it records one verdict with its
   evidence: the covering contract scenario, the runner that now executes it, or the reason to retire it.
2. **Given** a scenario whose behaviour nothing covers, **When** the analysis finds it, **Then** it goes on one
   list that the operator rules on: test gap (wire or write it here if small) or product gap (follow-up issue).

### Edge Cases

- A new test that reveals a defect: stop, report, and ask the operator before changing main code.
- A scenario that is partly covered: the verdict names what is covered and lists the rest as a gap.
- A scenario that describes behaviour the service deliberately does not offer (for example a planned-later surface):
  retire it, naming the decision record.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Direct kernel.validation tests | As a contributor, I want `Constrained` and `Rationale` (and the other kernel.validation behaviour PIT shows untouched) tested directly through their entry points, so that a broken invariant fails a kernel test. | High | Open | [build] | no — each new test has a planted break; PIT no-coverage for kernel.validation falls |
| FR-002 | Direct profiles tests | As a contributor, I want `SelfReviewStep`, `ModeDefault` and the other profiles behaviour PIT reports untouched tested directly, so that a broken profile rule fails a profiles test. | High | Open | [build] | no — PIT no-coverage for profiles falls from 29; planted breaks |
| FR-003 | Direct CappedBodySubscriber tests | As a contributor, I want the fetcher's byte cap tested at, over and under the limit and on stream failure, so that the fail-closed boundary is pinned. | High | Open | [build] | no — planted breaks for over-cap acceptance and at-cap refusal |
| FR-004 | Planted-break proof | As a reviewer, I want every new or strengthened test proved by a planted break recorded in the PR, so that no new test is vacuous. | High | Open | [build] | no — the record shows a red run per test |
| FR-005 | A verdict for every promise-first scenario | As a maintainer, I want each of the 27 scenarios mapped, wired or retired with evidence, the files changed accordingly and the README no longer saying "not yet wired", so that no scenario runs nowhere and drifts unseen. | Medium | Open | [build] | no — the verdict table counts 27; a file grep finds no unaccounted scenario |
| FR-006 | Gaps ruled by the operator | As the operator, I want the scenarios whose behaviour nothing covers listed once, each with a recommendation, so that I rule test gap or product gap; test gaps are wired or written here if small, product gaps become follow-up issues. | Medium | Open | [build] | no — each listed gap carries a ruling and its outcome |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Gate green | `verifyAll -PrequireSymlinks=true` and `pitest --continue` are green; 0 `@Disabled`, 0 suppressions, 0 `timing` tags added. | Reliability | High | Open |
| NFR-002 | Tests stay cheap | The new tests add at most 5 s to the summed test time of their modules. | Performance | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Tests as scaffold | Strengthen an existing test before adding one; add a test only if it guards real behaviour (`DIRECTIVE_041`). No test whose only purpose is killing a mutant. | Technical | High | Open |
| C-002 | No main-code change without a ruling | #370 is tests only; a defect a test exposes is reported to the operator before main code changes. | Technical | High | Open |
| C-003 | No coverage threshold | No JaCoCo or PIT threshold is added here (operator ruling of 2026-11-03; the floors belong to #239). | Business | High | Open |
| C-004 | Contract scenarios are the authority | A wired scenario joins the existing `CharterContractSuite`; no second runner, and no change to the `contracts/charter/scenarios/` copy rule without the plan naming it. | Technical | High | Open |
| C-005 | Coordinate with the parallel session | The activation and effective-read work in another session touches `packs` and `profiles`; check its open branches before the PR. | Technical | Medium | Open |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: PIT no-coverage counts fall for kernel.validation and for profiles (baseline: kernel 76 of 378 mutants overall, profiles 29 of 66). — [build] · no-op passable: no
- **SC-002**: 5 of 5 named classes have a direct test; 100 % of new tests have a recorded planted break. — [build] · no-op passable: no
- **SC-003**: 27 of 27 promise-first scenarios have a verdict; 0 scenario files run nowhere unaccounted. — [build] · no-op passable: no

## Assumptions

- The QA pack's `qa-coverage-gap-analysis` procedure is available from the synced `packs/qa`, and its method applies
  to Gherkin scenarios as written.
- The PIT baseline is the one measured on `4b52bcb` (Phase 0 research of the programme).
