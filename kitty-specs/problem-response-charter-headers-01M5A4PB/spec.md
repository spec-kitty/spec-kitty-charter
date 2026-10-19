# Mission Specification: Charter headers on the default Problem response

**Mission Branch**: `fix/problem-response-charter-headers`
**Created**: 2026-10-19
**Status**: Draft
**Input**: Issue [#90](https://github.com/spec-kitty/spec-kitty-charter/issues/90): "Contract: `_shared/responses/Problem.yaml` does not declare the Charter-* response headers."

## Intent Summary (confirmed)

The charter service already sends `Charter-Contract-Version`, `Charter-Worktree` and, when the
packs can be read, `Charter-Source-Fingerprint` on every response, including the generic
`Problem` answer that every operation can return (for example the 400 for an out-of-range
`depth`). The contract does not say so: the shared `default` response,
`contracts/_shared/responses/Problem.yaml`, declares no headers. A consumer such as core reads
the contract to learn the version handshake of ADR-0011 and is not told that the headers are on
this response.

**Operator ruling (2026-10-19, Decision Moment `01M5A4PP44NQ3KWG4APT0PEJCT`):** edit
`contracts/_shared/responses/Problem.yaml` itself, as the issue says, rather than adding a
charter-local response. `contracts/_shared/` is vendored byte for byte from core
(`VENDORED.md`, ADR-0002 decision 2), so the local divergence is recorded in `VENDORED.md`.

**Primary actor:** a contract consumer (core's integration seam, or anyone generating a client
from the bundle). **Trigger:** the consumer reads the contract for a response a charter operation
can return. **Outcome:** every response of every charter operation, the `default` `Problem`
included, declares the three `Charter-*` headers with the same required-ness as the other
responses. **Invariant:** the contract never states fewer headers than the service sends;
`Charter-Source-Fingerprint` is declared as not required because it is absent when a pack file
cannot be read (ADR-0011, fail closed).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The contract tells a consumer the headers are on every error answer (Priority: P1)

A consumer reads the bundled contract for any charter operation and sees that the `default`
`Problem` response carries `Charter-Contract-Version`, `Charter-Worktree` and
`Charter-Source-Fingerprint`, defined the same way as on the success and refusal responses.

**Why this priority**: This is the whole of issue #90: the contract under-documents ADR-0011's
"headers on every response" rule on the one response every operation can return.

**Independent Test**: A contract check walks every response of every operation in the bundle
and fails if any lacks one of the three headers. Run against today's contract it fails on the
`default` responses; after the change it passes.

**Acceptance Scenarios**:

1. **Given** the bundled charter contract, **When** a consumer reads the `default` response of
   `listPacks`, the action-context operation or the info operation, **Then** it declares the
   three `Charter-*` headers, with `Charter-Contract-Version` and `Charter-Worktree` required and
   `Charter-Source-Fingerprint` not required.
2. **Given** a running service, **When** a client asks for the action context with
   `depth=2`, **Then** the answer is a 400 `Problem`, and it carries `Charter-Contract-Version`,
   `Charter-Worktree` and `Charter-Source-Fingerprint`, and it conforms to the contract,
   headers included.

### Edge Cases

- A pack file cannot be read: the service omits `Charter-Source-Fingerprint`. The contract
  declares that header as not required on every response, so the answer still conforms.
- A future operation added without the headers on one of its responses: the contract check
  fails the build.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Shared Problem response declares the headers | As a contract consumer, I want `contracts/_shared/responses/Problem.yaml` to declare `Charter-Contract-Version`, `Charter-Worktree` and `Charter-Source-Fingerprint`, defined exactly as the charter's other responses define them, so that the `default` answer of every operation states the headers it carries. | High | Open | [build] | no — the FR-002 check fails on today's contract |
| FR-002 | Every response declares the headers | As a contract maintainer, I want a contract check in the build that fails when any response of any charter operation (the `default` included) lacks one of the three headers, so that the ADR-0011 rule cannot regress. | High | Open | [build] | no — it must be red on the current contract before FR-001 lands (red-first proof), and green after |
| FR-003 | The 400 Problem carries and conforms | As a contract consumer, I want a contract scenario showing that the 400 `Problem` for an out-of-range `depth` carries the three headers and conforms to the contract, so that the runtime behavior and the declaration are pinned together. | Medium | Open | [ratchet] | yes — the runtime already sends the headers; paired with FR-002's red-first check, which fails without FR-001 on the same response |
| FR-004 | The change from the vendored origin is recorded | As a maintainer, I want `contracts/_shared/VENDORED.md` to state that this repository's copy is canonical (operator general ruling, 2026-10-19: consumers, core included, adapt) and to list `responses/Problem.yaml` as changed from its origin, why (issue #90) and how, so that nobody reverts it to core's copy. | Medium | Open | [build] | no — absent today |
| FR-005 | Contract CHANGELOG records the change | As a contract consumer, I want `contracts/charter/CHANGELOG.md` under `1.0.0-SNAPSHOT` to record that the `default` `Problem` response declares the three headers, so that the change is visible to vendoring consumers. | Low | Open | [build] | no — absent today |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Full gate green | `./gradlew verifyAll` on Java 25 passes with 0 failures, including the contract lint, closure check, compatibility check, contract scenarios and the open-packs round-trip. | Reliability | High | Open |
| NFR-002 | Docs gates green | `check_docs.py all`, markdownlint-cli2 0.18.1 and cspell report 0 errors on the changed docs. | Quality | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | No runtime behavior change | The service's responses do not change; this mission changes the contract and its checks only. | Technical | High | Open |
| C-002 | One header definition | The header definitions stay in one place (`contracts/charter/headers/`); the shared response references them rather than restating them. | Technical | Medium | Open |
| C-003 | Unreleased contract only | The change lands in `1.0.0-SNAPSHOT`, before release, so declaring headers on an existing response is not a breaking change under the release gate. | Technical | High | Open |
| C-004 | Operator ruling on the vendored file | The vendored `_shared/responses/Problem.yaml` is edited in place by operator ruling; no other `_shared/` file changes. | Business | High | Open |

### Key Entities

- **Default `Problem` response**: the generic error answer every charter operation can return.
- **`Charter-*` headers**: `Charter-Contract-Version`, `Charter-Worktree`, `Charter-Source-Fingerprint` (ADR-0009, ADR-0011).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the responses of the charter's operations in the bundled contract declare all three `Charter-*` headers (today: the three `default` responses do not). — [build] · no-op passable: no
- **SC-002**: The new contract check fails on the pre-change contract and passes after it, both shown in the mission's red-first evidence. — [build] · no-op passable: no
- **SC-003**: The full build and the docs gates report 0 failures. — [ratchet] · no-op passable: yes — paired with SC-002

## Assumptions

- Only the charter contract consumes `contracts/_shared/` in this repository, so the edit has no
  other in-repo consumer (AC3 of #90). Core's own copy is not changed by this mission.
- Declaring headers on a response is additive and allowed before release (C-003).
