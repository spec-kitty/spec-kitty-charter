# Mission Specification: Charter contract skeleton and Gradle build (retroactive)

**Mission Branch**: `ccr-9654c444-x7y94q`  
**Created**: 2026-10-18  
**Status**: Draft  
**Input**: User description: "Retroactive mission: roadmap steps 1-2 (contract skeleton and Gradle build), already landed on branch ccr-9654c444-x7y94q without the mission workflow. Spec from the operator decisions of 2026-10-18 (ADR-0002 accepted; rewrite and replace, ADR-0008; core ADR 2026-10-03-1 accepted with Amendment 2026-10-18). Open items: run core's contracts/tools layout, lint and bundle checks here; the first Java 25 CI run; review of calls made without review (PACK_LEVEL_MISSING as a warning matching core's deprecation window, provisional action-context rules, the scenarios directory rename)."

## Intent Summary

Roadmap steps 1 (contract skeleton) and 2 (Gradle build) were built in an earlier session
and landed on `ccr-9654c444-x7y94q` without specify, plan or review. The **operator** wants
them brought under governance: the decisions and what landed are recorded, the calls made
without review are reviewed and ruled on, and the open items are closed **inside this
mission**, so steps 1 and 2 can be marked done. During the interview the operator added
to the build: Javadoc published as part of the docsite, and a vetted set of style, static
analysis and test-quality tools.

**Core maintainers** are the downstream consumers: core vendors the charter contract and
generates its DTOs from it. The charter owns its contract and its contract checks; it is a
redesign of the charter offering, not a port of core's conventions. Where core's tooling or
conventions differ, the integration seam (the anti-corruption layer in core) adapts, and
core changes later (operator, 2026-10-18).

**Invariant.** Nothing that landed changes without an operator ruling. Two things are
never traded away: the service's governance content matches core's Python charter (the
oracle) closely enough that core does not break, and the service honours the OpenAPI
contract.

### Decisions this mission builds on (operator, 2026-10-18)

1. ADR-0002 accepted: the charter contract is authored in this repository under
   `contracts/charter/`.
2. Rewrite and replace (ADR-0008, numbered 0005 before the rebase onto #43): the Java
   service is written fresh against the BDD scenarios and the OpenAPI document; the
   Python charter stays in core as the reference implementation; every
   spec-kitty-open-packs YAML file round-trips through the file-based layer.
3. Core ADR `2026-10-03-1` accepted with Amendment 2026-10-18 (the amendment is on branch
   `ccr-9654c444-x7y94q` of spec-kitty/spec-kitty).

### What landed (the baseline this mission reviews)

- The charter contract at `0.1.0-SNAPSHOT`: two read operations (the pack listing and the
  action-scoped context), the vendored shared vocabulary, and 8 scenarios in
  `contracts/charter/scenarios/`.
- The build: convention plugins, six service modules, a version catalog, strict SHA-256
  dependency verification, the Gradle 9.8.1 wrapper and a Java 25 toolchain.
- Tests: unit tests, the architecture gate, the scenario suite and the open-packs
  round-trip (520 checks). 548 tests in total, all green on JDK 25 locally.
- CI for the service build, plus the docs that describe it.

### Interview decisions (this mission)

| Decision | Ruling |
|---|---|
| Scope | Record, review **and close** the open items in this mission |
| Unreviewed calls | The adversarial squad reviews each call at the plan point; the operator then ratifies, amends or reverts it |
| BDD layout | Contract scenarios stay in `contracts/charter/scenarios/`; #43's suites move to `contracts/charter/scenarios/<cluster>/` when their harness is bound |
| Javadoc | Built with doclint, warnings fail; the site is packaged by the main build workflow and published inside the docsite |
| Style | Checkstyle, and Spotless with palantir-java-format |
| Static analysis | Error Prone, with NullAway and JSpecify |
| Test quality | JaCoCo, PIT and Mockito (AssertJ is already present) |
| PIT cadence | Nightly, as an experiment; never on every pull request, never a merge gate |
| jqwik | Rejected: it carries an AI output clause; an advisory issue goes to spec-kitty-open-packs |
| OpenRewrite | Not enabled (plan-point ruling; research §11) |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The operator rules on the calls made without review (Priority: P1)

Three calls were made without review: `PACK_LEVEL_MISSING` is a non-blocking diagnostic
matching core's deprecation window; the action-scoped context rules are provisional
(scope edges of the action node, then of the mission-type node, over the merged offering,
with no activation filtering); and the scenario directory is named `scenarios/` because
the terms guard rejects the old name. The squad assesses each one against core's Python
charter and the contract, and the operator rules on it.

**Why this priority**: an unratified call inside the contract is exactly the
oracle-divergence and contract risk the charter ranks highest.

**Independent Test**: each call has a recorded ruling (ratified, amended or reverted) with
the squad's evidence, findable from the open questions.

**Acceptance Scenarios**:

1. **Given** the squad's assessment of a call, **When** the operator ratifies it, **Then**
   the ruling is recorded with its date and the behavior stays as it is.
2. **Given** the squad's assessment of a call, **When** the operator amends or reverts
   it, **Then** the scenarios change first and the service follows, and the contract
   changelog records the change.

---

### User Story 2 - The charter's own contract checks gate every change (Priority: P1)

The charter's own contract checks run in the build: a lint ruleset the charter authors for
its conventions, a bundle of `contracts/charter/` at `1.0.0-SNAPSHOT`, and a breaking-change
check against the charter's own released versions. Core's contract tools do not run here
(operator, 2026-10-18).

**Why this priority**: roadmap step 1 is not done until the contract is checked by rules
the charter owns, and core vendors the bundle the charter publishes.

**Independent Test**: the checks pass on the committed contract and yield a bundle; each
lint rule fails on its planted fixture.

**Acceptance Scenarios**:

1. **Given** the contract as committed, **When** the build runs, **Then** the checks pass and a
   bundle is produced and kept as a build artifact.
2. **Given** a planted violation of a charter lint rule, **When** the checks run, **Then** they
   fail and name the rule and location.

---

### User Story 3 - The service build runs green in CI on Java 25 with the full quality bar (Priority: P1)

The service workflow runs on Java 25 and passes: contract validation, unit tests, the
architecture gate, the scenarios, the open-packs round-trip, the style checks, static
analysis and coverage, with strict dependency verification.

**Why this priority**: the gates are the charter's merge bar, and no CI run has happened
yet.

**Independent Test**: a CI run on the mission's head is green; a planted style violation,
a planted nullness error and a planted architecture violation each fail it.

**Acceptance Scenarios**:

1. **Given** the branch head, **When** the service workflow runs, **Then** every gate
   passes on Java 25.
2. **Given** unformatted or rule-breaking hand-written code, **When** the build runs,
   **Then** it fails and names the file and rule.

---

### User Story 4 - A reader browses the service's API documentation from the docsite (Priority: P2)

The Javadoc of the hand-written modules is built by the main build workflow and published
as a dedicated set of pages inside the docsite, reached from the sidebar and styled like
the rest of the site.

**Why this priority**: valuable for contributors, but it gates nothing in steps 1–2.

**Independent Test**: the published docsite has an API documentation entry in its sidebar
that opens the Javadoc for each hand-written module, with the docsite's look.

**Acceptance Scenarios**:

1. **Given** a published docsite, **When** a reader opens the API documentation entry,
   **Then** they see the Javadoc of each hand-written module, styled like the docsite.
2. **Given** a public type with a malformed Javadoc comment, **When** the build runs,
   **Then** it fails.

---

### User Story 5 - Mutation testing reports test strength nightly (Priority: P3)

An experiment: mutation testing runs nightly on the domain and read-application modules,
as its own build task, and reports a mutation score. It never runs on pull requests.

**Why this priority**: a test-quality signal, not a merge gate.

**Independent Test**: the nightly run produces a report per module; a pull request run
does not invoke the mutation task.

**Acceptance Scenarios**:

1. **Given** the nightly schedule, **When** it fires, **Then** the mutation report lists
   killed and surviving mutants for each covered module.
2. **Given** a pull request, **When** CI runs, **Then** the mutation task is not run.

---

### Edge Cases

- A ruling reverts a call that core already relies on: the contract version changes and
  core is told before the change lands.
- The lint and breaking-change tools are binaries: each is pinned by version and SHA-256 and
  verified before it runs, like every other dependency.
- No released contract version exists yet: the breaking-change check reports that and passes
  until the first tag.
- A new tool flags existing landed code: the finding is fixed in this mission, or recorded
  as a reviewed exception with scope and sunset, never suppressed in bulk.
- The rate limit on the dependency mirror interrupts a CI run: the run is retried once;
  a second failure is investigated, not re-run.
- The generated contract sources are not held to the hand-written lint, style, nullness or
  Javadoc bar, but they still compile.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Record decisions and baseline | As the operator, I want the three 2026-10-18 decisions and what landed recorded in this mission so that steps 1–2 have a governed paper trail. | High | Open | [ratchet] | no: the baseline counts (2 operations, 8 scenarios, 548 tests, 520 round-trip checks) are asserted from a fresh build, not copied |
| FR-002 | Review the unreviewed calls | As the operator, I want the adversarial squad to assess each of the three unreviewed calls against the Python oracle and the contract so that I can rule on evidence. | High | Open | [build] | no: three named assessments must exist |
| FR-003 | Record the operator rulings | As the operator, I want each ruling (ratify, amend or revert) recorded with its date, and any amendment carried through scenario, contract changelog and service, so that no call stays implicit. | High | Open | [build] | no: three rulings must exist; an amendment needs a changed scenario |
| FR-004 | Charter contract checks pass | As a core maintainer, I want the charter's own contract checks (a charter-authored lint ruleset run by a pinned vacuum, a bundle, and a breaking-change check against the charter's own released versions) to run in the build and pass, producing a 1.0.0-SNAPSHOT bundle, so that core vendors a contract checked by its owner. | High | Open | [build] | no: every lint rule has a planted fixture that must fail it |
| FR-005 | First green Java 25 CI run | As the operator, I want the service workflow to run green on Java 25 on the mission head so that the merge bar is proven in CI, not only locally. | High | Open | [build] | no: a CI run id on the head commit is required |
| FR-006 | Style checks gate the build | As the operator, I want Checkstyle and a palantir-java-format formatting check to run on hand-written sources in the full verification and fail it on violation. | High | Open | [build] | no: paired with a planted violation that must fail |
| FR-007 | Static analysis gates the build | As the operator, I want Error Prone and NullAway with JSpecify annotations to run on hand-written sources at compile time and fail it on an error. | High | Open | [build] | no: paired with a planted null dereference that must fail |
| FR-008 | Coverage reported | As the operator, I want JaCoCo coverage reported per module and aggregated in the full verification. | Medium | Open | [build] | no: the report must list each hand-written module |
| FR-009 | Nightly mutation testing | As the operator, I want PIT mutation testing on the domain and read-application modules as its own task, run by a nightly workflow and never on pull requests, with a reported score. | Low | Open | [build] | no: the nightly report must list killed and surviving mutants, and the pull-request workflow must not invoke the task |
| FR-010 | Mockito available | As a contributor, I want Mockito on the test classpath through the version catalog and dependency verification so that it is available where a hand-written fake is not worth it. | Low | Open | [build] | yes: availability alone is passable, so it is checked by one test that uses it |
| FR-011 | Javadoc built and linted | As the operator, I want Javadoc built for every hand-written module with doclint on and warnings failing the build. | Medium | Open | [build] | no: paired with a planted malformed comment that must fail |
| FR-012 | Javadoc published in the docsite | As a reader, I want the Javadoc packaged by the main build workflow and published inside the docsite as a dedicated set of pages reached from the sidebar, styled like the docsite. | Medium | Open | [build] | no: the built site must contain the pages and the sidebar entry |
| FR-013 | BDD layout decision recorded | As the operator, I want the decision that contract scenarios stay beside the contract, and that #43's suites join them when bound, recorded in the open questions. | Medium | Open | [build] | no: a dated row must exist |
| FR-014 | Vendored pack defects logged | As the operator, I want the vendored-pack defects logged with an owner: the qa pack has no pack.yaml, and the internal pack requires glossary id spk-internal-glossary while its file stem is spk-internal. | Medium | Open | [build] | no: each defect needs a named owner |
| FR-015 | Plans and changelog updated | As the operator, I want the roadmap to mark steps 1–2 done, the open questions updated, and a changelog entry written when this mission is accepted. | Medium | Open | [build] | no: steps 1–2 read done only after FR-004 and FR-005 pass |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | No skipped or suppressed checks | 0 skipped tests, 0 quarantined tests and 0 blanket suppressions in the green run; each targeted suppression names its rule and reason. | Quality | High | Open |
| NFR-002 | Build time stays usable | The full verification, excluding the mutation task, completes within 10 minutes on a CI runner with a warm dependency cache. | Performance | Medium | Open |
| NFR-003 | Supply chain stays strict | 100% of new artifacts, tool plugins included, carry SHA-256 entries in the verification metadata; no artifact is admitted unverified. | Security | High | Open |
| NFR-004 | Docs stay clean | All eight docs checks, markdownlint and cspell report 0 errors on the mission head. | Quality | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | No change without a ruling | Landed behavior changes only through an operator ruling (FR-003). | Governance | High | Open |
| C-002 | Oracle parity and contract compliance | Never traded for any other requirement in this mission. | Business | High | Open |
| C-003 | Java 25 in CI | CI never builds with `-Pcharter.javaVersion=21`. | Technical | High | Open |
| C-004 | Operator-vetted tools only | The tools are the ones the operator vetted on 2026-10-18; adding another needs a new ruling. | Technical | Medium | Open |
| C-005 | Generated sources exempt | Generated contract sources are exempt from the hand-written style, analysis and Javadoc bars, but still compile. | Technical | Medium | Open |
| C-006 | Out of scope | Roadmap step 3 (the conformance corpus) and the ADR-0006 slice restructure are out of scope. | Scope | High | Open |
| C-007 | Local-only mirror script | A local mirror init script used against rate limiting is never committed. | Technical | Medium | Open |
| C-008 | jqwik excluded | jqwik is not added to the build or the version catalog. | Technical | Medium | Open |

### Key Entities

- **Unreviewed call**: a design choice that landed without review, with its assessment
  and the operator's ruling.
- **Contract bundle**: the single-file artifact the charter's build produces from
  `contracts/charter/` at a contract version; what core vendors.
- **Quality gate**: a check that fails the build when violated, with the planted
  violation that proves it can fail.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 3 unreviewed calls carry an operator ruling backed by a squad assessment. — [build] · no-op passable: no
- **SC-002**: The charter's own contract checks pass on the contract and 1 `1.0.0-SNAPSHOT` bundle is produced; every lint rule fails on its planted fixture. — [build] · no-op passable: no
- **SC-003**: The first CI run on the mission head is green on Java 25 with every gate enabled, and each new gate fails on its planted violation. — [build] · no-op passable: no
- **SC-004**: A reader reaches the API documentation of every hand-written module from the docsite sidebar in one click. — [build] · no-op passable: no
- **SC-005**: The roadmap reads steps 1 and 2 as done, with no open item left untracked. — [build] · no-op passable: no
- **SC-006**: The nightly mutation run reports a score for each covered module, and no pull-request run takes longer because of it. — [build] · no-op passable: no

## Assumptions

- The charter's contract conventions may differ from core's (snake_case codes, provenance
  citations, provisional markers are the charter's own choices); the integration seam in core
  absorbs differences.
- The six landed modules are kept as they are; the slice restructure belongs to ADR-0006's
  implementation mission.
- Publishing the docsite continues to use the existing Pages workflow; the main build
  workflow produces the Javadoc artifact it consumes.
