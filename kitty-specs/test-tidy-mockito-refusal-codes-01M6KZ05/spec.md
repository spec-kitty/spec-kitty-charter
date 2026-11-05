# Mission Specification: Test-suite tidy: drop Mockito, pin refusal codes

**Mission Branch**: `test-tidy-mockito-refusal-codes`
**Created**: 2026-11-04
**Status**: Draft
**Input**: Operator brief (2026-11-04): pick up #373 (R7) and #369 (R3) from the
[test suite assessment of 2026-11-03](../../docs/plans/engineering-notes/test-suite-assessment-2026-11-03.md),
tidy-first, on a topic branch. Intent summary confirmed in decision `01M6KZ0NR4CPPMNT9FH075Z7C8`.

## Intent Summary

- **Actor:** a charter contributor (or CI) running the hand-written modules' tests.
- **Trigger:** the assessment found a test dependency nothing uses, and 15 storage-layer refusal
  checks that accept any refusal.
- **Outcome:** the mocking library the build declares runs at the version the Spring stack
  provisions, the reason it is kept is recorded, the verified dependency list holds no stale
  checksum, and each of the 15 checks fails when the code under test refuses with a different
  refusal code than intended.
- **Amended in plan (decisions `01M6KZNKE4QW577JQ5MFXVVPPQ`, `01M6KZNNTG47M33595KY54671S`):** the
  operator ruled to keep Mockito. `spring-boot-starter-test` brings it to the `deployable` and `packs`
  tests anyway, so the explicit dependency and the Java agent stay, aligned to the version Spring
  Boot manages. #373 is closed by recording why they are kept. The mission's creation title ("drop
  Mockito") predates this ruling and is immutable.
- **Invariant:** a refusal check names the refusal it expects; nothing is added that tests the
  tests (no counting or shape guard, `DIRECTIVE_041`).
- **Boundary:** only the 15 `CharterRefusal` sites listed in #369. The other type-only exception
  assertions stay with review and the test-cleanup FIX verdicts.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The mocking library is kept deliberately, at Spring's version (Priority: P1)

A contributor reads the build. Mockito is declared and attached as an agent on purpose: Spring's
test starter brings it to two modules anyway, and the agent avoids the JDK's self-attach refusal
the day a test uses it. The declared version is the one the Spring stack provisions, so no second
version is resolved, and the verified dependency set lists only what the build resolves.

**Why this priority**: tidy-first. It is the smallest, independent change and simplifies the
build files the later mutation-testing work (#239) edits.

**Independent Test**: the full per-PR gate is green under strict dependency verification, every
module resolves one Mockito version equal to the Spring-managed one, and the reason is written
beside the declaration.

**Acceptance Scenarios**:

1. **Given** the build after this change, **When** the test runtime classpaths are resolved,
   **Then** each holds exactly one Mockito version, the one Spring Boot manages.
2. **Given** the verified dependency list, **When** it is regenerated from scratch over every task
   family, **Then** it holds no checksum for an artifact no task resolves, and `verifyAll` is green
   with strict verification.

### User Story 2 - A wrong refusal code fails the refusal checks (Priority: P1)

A contributor changes storage or registration code so that it refuses with the wrong refusal
code. Today 15 tests still pass. After this change, the check that covers that path fails and
names the expected code.

**Why this priority**: it is the defect the assessment ranks W4; storage-layer refusals are not
seen by the HTTP contract scenarios.

**Independent Test**: plant a different refusal code in the code under a sample of the checks
and watch them go red; revert and watch them go green.

**Acceptance Scenarios**:

1. **Given** one of the 15 checks, **When** the code under test raises the intended refusal,
   **Then** the check passes as before (positive control: the existing behavior).
2. **Given** the same fixture, **When** the code under test raises a different refusal code,
   **Then** the check fails.

### Edge Cases

- A site whose refusal code differs between platforms or file systems: the check pins the code
  the production path raises on the CI platform, or the plan records why the site cannot.
- A site that already asserts post-conditions (bytes unchanged, listing unchanged) keeps those
  assertions; the code assertion is added, not substituted.
- Two checks in one statement chain (`PackWriteSafetyTest` 106 and 108): each is pinned.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Mockito kept on purpose, at Spring's version (#373) | As a contributor, I want the declared Mockito version aligned to the one the Spring stack provisions (the highest it provisions), and the reason for keeping the dependency and its Java agent recorded beside them, so that one Mockito version is resolved and the choice is explicit. | High | Open | [build] | no — the dependency report shows a single version equal to the Spring-managed one; today it shows 5.23.0 -> 5.24.0 |
| FR-001a | Verified dependency list regenerated from scratch (#373) | As a contributor, I want `verification-metadata.xml` regenerated over every task family, so that it lists no checksum for an artifact the build no longer resolves (for example `mockito-core` 5.24.0 after the alignment). | High | Open | [build] | no — strict `verifyAll` fails if a needed entry is missing; the diff shows the stale entries gone |
| FR-002 | Refusal code pinned on the 15 type-only checks (#369) | As a contributor, I want each of the 15 `CharterRefusal` checks listed in #369 to also assert the refusal code it expects, strengthened in place with no new test method, so that a wrong refusal code fails a test. | High | Open | [ratchet] | yes — paired with FR-003's planted break on the same fixtures |
| FR-003 | Planted-break proof (#369 AC) | As a reviewer, I want a planted wrong refusal code to turn the changed checks red, on a sample of at least one check per distinct expected code, with the result recorded in the PR, so that the strengthened checks are shown not to be vacuous. | High | Open | [build] | no — the record shows a red run per sampled code |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Gate stays green | `./gradlew verifyAll` is green on JDK 25 with strict SHA-256 verification; the `:packs:test` executions are unchanged in count (no test added or removed). | Reliability | High | Open |
| NFR-002 | No suppressions | 0 new Checkstyle suppressions, 0 `@Disabled`, 0 new `@SuppressWarnings`. | Maintainability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | No guard test about tests | No test that counts or shapes assertions is added (`DIRECTIVE_041`, R3 ruling of 2026-11-04). | Technical | High | Open |
| C-002 | Tidy-first ordering | The Mockito removal lands first, as its own commit, before the assertion changes. | Technical | High | Open |
| C-003 | Minimal overlap with #368 | Draft PR #368 also edits `charter.hand-written.gradle.kts`; this mission changes that file at most in the Mockito comment, so a rebase on either side stays trivial. | Technical | Medium | Open |
| C-005 | Never edit the metadata by hand | `verification-metadata.xml` changes only through `--write-verification-metadata sha256` (service build guide). | Technical | High | Open |
| C-004 | Scope | The other type-only `assertThatThrownBy` checks are out of scope. | Business | Medium | Open |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: every module's test runtime classpath resolves exactly 1 Mockito version, equal to the Spring-managed one; the verified dependency list holds 0 Mockito versions that no task resolves. — [build] · no-op passable: no
- **SC-002**: 15 of 15 listed checks assert a refusal code; 0 test methods added. — [ratchet] · no-op passable: yes (paired with SC-003)
- **SC-003**: every sampled planted break turns its check red (100 % of the sample). — [build] · no-op passable: no

## Assumptions

- The refusal code each site expects is the one the production path raises today; the mission
  pins current behavior and changes no main code.
- A sample of one check per distinct expected code satisfies #369's "checked on a sample".
