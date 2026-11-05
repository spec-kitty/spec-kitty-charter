# Mission Specification: A generated Java client and a "generate your own client" how-to

**Mission Branch**: `claude/client-java-218-224`
**Created**: 2026-11-05
**Status**: Draft
**Input**: Issues #218 and #224, under epic #217. The operator's rulings on #217 apply: open-core;
`okhttp-gson`; consumers on Java 21 and 25; MIT; a hand-written Problem helper inside the client; the client
stays 0.x; no published Python client, a Python section in #224 instead (2026-10-28); and, on 2026-11-05,
"start #218 and #224 now against `main`'s snapshot contract; #219–#223 and #225 wait for 0.1.0".
The coordinates are recorded as decision `01M6P9CHWRCVGDZN6CA7GG191W`.

## Intent Summary

- **Primary actor:** a Java developer who calls the charter service from a Java 21 or 25 program.
- **Secondary actor:** a developer in any other language, Python included, who generates their own client
  from the published contract.
- **Trigger:** the build of this repository (for the Java client); a reader opening the docsite how-to
  (for everyone else).
- **Outcome:**
  - the build generates a Java client from the same single-file contract that is published, compiles it for
    Java 21, and checks it on every change;
  - a small hand-written helper turns an error response into the contract's Problem model;
  - a how-to lets a reader fetch the contract, verify it, and generate and compile a client in Java or
    Python, with the generator version and options this repository uses.
- **Invariants:**
  - The client is generated from the bundle, never from the split source tree, so it matches what is
    published byte for byte.
  - A client generated today keeps working when a later 0.x minor adds an enum value or a response field.
  - Generated code is never edited by hand; a contract change is a regeneration.
- **Boundaries:** no release zip (#220), no JDK 21/25 test matrix or smoke test (#219), no quickstart project
  (#223), no forward-compatibility test suite (#221), no Maven publication (#225). No tag, no release. The
  contract is not changed.

## User Scenarios & Testing *(mandatory)*

### User Story 1: A Java developer has a client that compiles for Java 21 (Priority: P1)

The build generates the charter client from the bundled contract and compiles it so a Java 21 runtime loads it.

**Why this priority:** it is the deliverable of #218, and every later client task (#219–#223) builds on it.

**Independent Test:** run the client module's build. It generates the sources from the bundle, compiles them,
and every class file targets Java 21.

**Acceptance Scenarios:**

1. **Given** a clean checkout, **When** the client module is built, **Then** the client is generated from the
   bundle the contract build produces, compiled, and its class files carry the Java 21 class-file version.
2. **Given** the client has been generated once, **When** it is generated again from the same contract,
   **Then** the generated sources are byte-identical (no timestamps).
3. **Given** the contract changes, **When** the build runs, **Then** the client is regenerated from the new
   bundle without any hand edit.

### User Story 2: A Java developer reads an error as the contract's Problem (Priority: P1)

Every error the service answers is `application/problem+json`. The generated client reports it as an exception
whose body is text. The helper turns it into the generated Problem model.

**Why this priority:** without it every caller writes the same parsing code, and gets it wrong for a body that
is not a Problem.

**Independent Test:** the helper's own tests, with a Problem body, a refusal with a code, a body with an
unknown member, an empty body and a body that is not JSON.

**Acceptance Scenarios:**

1. **Given** an error whose body is a Problem with a `code`, **When** the helper reads it, **Then** the caller
   gets the title, status and code.
2. **Given** an error whose body is empty or not a Problem, **When** the helper reads it, **Then** the caller
   gets "no Problem" (an empty result), not an exception.
3. **Given** a Problem that carries a member this client does not know, **When** the helper reads it, **Then**
   the known members are read and the unknown member does not fail the read.

### User Story 3: A client generated today survives an additive 0.x minor (Priority: P1)

A later 0.x minor may add an enum value or a response member. A client generated from the current contract must
read such a response instead of throwing.

**Why this priority:** the contract's closed objects and closed enums would otherwise turn every additive minor
into a client break (#217, "contract facts").

**Independent Test:** decode a response that carries an unknown enum value and an unknown member with the
generated client's own JSON reader.

**Acceptance Scenarios:**

1. **Given** a response enum value the client does not know, **When** it is decoded, **Then** it decodes to
   the client's "unknown" value instead of failing.
2. **Given** a response object with a member the client does not know, **When** it is decoded, **Then** the
   known members decode and the decode does not fail.

### User Story 4: A developer in any language generates their own client (Priority: P2)

A reader follows one docsite page: fetch the contract (from the docsite, or from a running service), check its
SHA-256, generate a client with the pinned generator and its options, and compile it. The page has a Java and a
Python section and lists the caveats that hold for any language.

**Why this priority:** it is #224; core and other consumers generate their own client (ADR-0021), and the Python
section is the operator's answer to "no published Python client".

**Independent Test:** follow the page from a clean directory for Java and for Python; each ends with a compiled
(or imported) client.

**Acceptance Scenarios:**

1. **Given** the docsite contract and its checksum, **When** the reader runs the page's verify command,
   **Then** it passes for the published file and fails for an altered one.
2. **Given** the verified contract, **When** the reader runs the page's Java or Python command, **Then** a
   client is generated with the same generator version and the same tolerance options this repository uses.
3. **Given** the page, **When** a reader looks for the release status, **Then** it says the contract is
   unreleased (a snapshot) until 0.1.0, and where the release asset will be once it is.

### Edge Cases

- The bundle carries two files (the YAML and its `.sha256`); the generator must read only the YAML.
- The contract declares `additionalProperties: false` on every object; the generator's default for that is a
  strict check that throws on an unknown member.
- One open map (`Links`) maps relation names to `Link`; it must stay a map.
- `allOf` is used for single-reference descriptions (for example `ServiceInfo.sourceFingerprint`) and for
  `CharterRefusal` over the closed `Problem`; both must generate usable models.
- Nullable members are OAS 3.1 type arrays (`['string', 'null']`).
- The parser can emit duplicate models for one schema (`Problem1`, #119).
- The build tools that bundle the contract run on Linux and macOS only.
- The service answers `401`/`403` Problems for a missing token or a browser-like request; the how-to must say
  that `GET /api/v1/openapi.yaml` needs the worktree token.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status | Delivery | No-op passable? |
|---|---|---|---|---|
| FR-001 | A `:client-java` build module generates a Java client with the generator `java` and library `okhttp-gson`, at the generator version already pinned in the version catalog, adding no second pin. | Proposed | [build] | no |
| FR-002 | The generator's input is the single-file bundle published by the contract build, reached through its published variant, not the split tree under `contracts/charter/`. | Proposed | [build] | no |
| FR-003 | Generation options: unknown enum values map to a default "unknown" case; `openApiNullable=false`; `dateLibrary=java8`; `hideGenerationTimestamp=true`; the duplicate-model fix of #119 applies. | Proposed | [build] | no |
| FR-004 | The client tolerates unknown response members: no generated model rejects a member it does not declare. A test proves it with the generated client's own decoder. | Proposed | [build] | no |
| FR-005 | A test proves that an unknown enum value decodes to the "unknown" case. | Proposed | [build] | no |
| FR-006 | The client compiles with `--release 21` on the Java 25 toolchain; a check fails the build if any class file targets a later version than Java 21. | Proposed | [build] | no |
| FR-007 | Every generated source carries an `SPDX-License-Identifier: MIT` header and a note that it is generated from the charter contract at its version and must not be edited. | Proposed | [build] | no |
| FR-008 | The client's package is `ai.speckitty.charter.client` (models in `.model`); the module's Maven coordinates are recorded as group `ai.speckitty.charter`, artifact `charter-client`, version = the contract's `info.version`. | Proposed | [build] | no |
| FR-009 | A hand-written `ProblemDetails.from(ApiException)` returns the generated Problem model when the error body is a Problem, and an empty result otherwise; it never throws for a malformed body. | Proposed | [build] | no |
| FR-010 | The helper has unit tests for a Problem, a refusal with `code`, an unknown member, an empty body and a non-JSON body. | Proposed | [build] | no |
| FR-011 | The hand-written helper passes the same gates as other hand-written code (format, Checkstyle with the complexity limits, Error Prone, NullAway, doclint); generated code is not gated. | Proposed | [build] | no |
| FR-012 | Two generations from the same bundle give byte-identical sources; a check proves it. | Proposed | [build] | no |
| FR-013 | The client's build runs inside `verifyAll`, and CI's change detection runs the build when the client module changes. | Proposed | [build] | no |
| FR-014 | A docsite how-to, "Generate your own client", shows: fetching the contract from the docsite and from `GET /api/v1/openapi.yaml`; verifying it with `sha256sum -c`; the pinned generator version and options; a Java section; a Python section; and the caveats (unknown enums, closed objects not enforced on the client, nullability as type arrays, `application/problem+json` errors, the literal write paths). | Proposed | [build] | no |
| FR-015 | The how-to states that the contract is unreleased (a snapshot) until 0.1.0 and names where the release asset will be published. | Proposed | [build] | no |
| FR-016 | The build guide documents the client module, and a changelog entry records the change. | Proposed | [build] | no |

### Non-Functional Requirements

| ID | Requirement | Threshold | Status |
|---|---|---|---|
| NFR-001 | The build's dependency verification stays strict. | Every new dependency has a SHA-256 entry; zero changed checksums; zero trust or ignore entries | Proposed |
| NFR-002 | The client's runtime dependencies stay small. | At most 5 direct runtime dependencies, each pinned in the version catalog | Proposed |
| NFR-003 | Hand-written code meets the complexity gate. | Cyclomatic ≤ 15, NPath ≤ 1000, ≤ 60 lines per method, nested-if depth ≤ 1, zero new suppressions | Proposed |
| NFR-004 | Regeneration is deterministic. | 0 differing bytes between two generations from one bundle | Proposed |
| NFR-005 | The how-to passes the docs checks. | All `check_docs.py` checks, markdownlint and cspell pass; description ≤ 180 characters | Proposed |

### Constraints

| ID | Constraint | Status |
|---|---|---|
| C-001 | No release zip, no JDK matrix or smoke test, no quickstart, no Maven publication: #219–#223 and #225 wait for 0.1.0. | Accepted |
| C-002 | The contract (`contracts/charter/`, `contracts/_shared/`) is not changed. | Accepted |
| C-003 | Generated code is never edited by hand; tolerance is achieved through generator options, the normalizer and the header template only. | Accepted |
| C-004 | Designed without regard to core (charter, 2026-10-28); core is named only as one consumer that generates its own client. | Accepted |
| C-005 | No tag is pushed and no release is created; the operator merges. | Accepted |
| C-006 | `ci.yml` is edited by appending only (a parallel session also edits it). | Accepted |

### Key Entities

- **Contract bundle:** the single-file OpenAPI 3.1 document and its `.sha256`, produced by the contract build
  and published on the docsite (and, from 0.1.0, as a release asset).
- **Generated client:** the API classes, models and transport the generator writes from the bundle.
- **Problem:** the RFC 9457 error model of the contract; a refusal is a Problem with a `code`.
- **ProblemDetails helper:** the one hand-written class in the client.

## Success Criteria *(mandatory)*

- **SC-001:** A Java 21 program can load every class of the client (100% of class files at Java 21 or lower).
  — [build] · no-op passable: no
- **SC-002:** A response with one unknown enum value and one unknown member is read without error by a client
  generated from the current contract. — [build] · no-op passable: no
- **SC-003:** A reader who has never seen the repository generates and compiles a Java client, or imports a
  Python client, from the how-to alone, in under 15 minutes. — [build] · no-op passable: no
- **SC-004:** Zero hand edits to generated code are needed after a contract change. — [build] · no-op passable: no

## Assumptions

- The generator version is the one already pinned (7.26.0 at the time of writing; #217 named 7.25.0 before a
  bump). The how-to reads it from the catalog rather than repeating a stale number.
- Tolerating unknown members means the models keep them in their additional-properties map rather than
  dropping them; callers that ignore the map see no difference.
- The contract stays unreleased during this mission; regenerations after #125, #126 and #240 are expected.
