# Mission Specification: Serve the contract document

**Mission Branch**: `feat/209-serve-openapi-document`
**Created**: 2026-10-29
**Status**: Draft
**Input**: Issue #209 (part of epic #207): package the validated contract bundle in the deployable and serve it
from `GET /openapi.yaml` through a guarded controller with an ETag and `304`.

## Intent Summary

A client of the charter service (a developer, a tool, an HTTP Client example run) fetches the contract the
running service was built against from the service itself, locally and hosted. The answer is the bundled
contract, byte for byte the file a contract release publishes, as `application/yaml`, with a strong ETag
(the SHA-256 of those bytes). A client that already holds that document sends the ETag back in
`If-None-Match` and gets `304 Not Modified` with no body. The request is guarded like every operation: no
token is a `401` Problem, a browser request is a `403` Problem, and the Charter headers stay on every answer.
The document is fixed when the service is built; it is never a hand copy.

The contract already declares the operation (#208, merged; no version bump; the `service-desc` link waits for
#126). Today the service answers `501` for it. This mission replaces that interim answer. No contract change.

Confirmed by the orchestrator's brief (non-interactive session); the one scope call is recorded as Decision
Moment `01M64FWM9F2AGBZK6XV1W3R1FC`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Fetch the contract (Priority: P1)

A developer with a valid worktree token fetches `/openapi.yaml` and receives the exact contract bundle the
service was built from, with its ETag.

**Why this priority**: It is the point of #207: read or try the latest contract without a separate download.

**Independent Test**: Request the document with a valid token and compare the body and ETag with the bundle
the build produced and its checksum.

**Acceptance Scenarios**:

1. **Given** a running service and a valid token, **When** the client sends `GET /openapi.yaml`, **Then** the
   answer is `200`, `Content-Type: application/yaml`, the body equals the bundle build output byte for byte,
   and `ETag` is `"sha256:<digest of those bytes>"`.
2. **Given** the same request, **Then** the answer carries `Charter-Contract-Version` (equal to the document's
   `info.version`) and `Charter-Worktree`, and no `Charter-Source-Fingerprint`.

---

### User Story 2 - Revalidate a held copy (Priority: P2)

A client that already holds the document revalidates it cheaply.

**Why this priority**: Tools poll the contract; a `304` saves the transfer and tells them nothing changed.

**Independent Test**: Send the ETag from a first answer in `If-None-Match` and check for a bodyless `304`.

**Acceptance Scenarios**:

1. **Given** the ETag of the current document, **When** the client sends it in `If-None-Match`, **Then** the
   answer is `304` with no body, the same `ETag`, and the Charter headers.
2. **Given** `If-None-Match: *`, or a list in which one tag matches (compared weakly, so `W/"sha256:…"`
   matches too), **Then** the answer is `304`.
3. **Given** a tag that does not match, or a value that is not an entity-tag list, **Then** the answer is
   `200` with the document.

---

### User Story 3 - Refused like every operation (Priority: P1)

The document is only answered to a client the service would answer anything to.

**Why this priority**: Hosted mode must not leak more than any other operation does (default ruling on #207:
token required).

**Independent Test**: Send the request without a token, and as a browser, and check the Problem answers.

**Acceptance Scenarios**:

1. **Given** no token (or a wrong one), **When** the client sends `GET /openapi.yaml`, **Then** the answer is
   `401` `application/problem+json` with `WWW-Authenticate: Bearer`, even with a matching `If-None-Match`.
2. **Given** a browser request (as the guard defines it), **Then** the answer is `403` Problem, even with a
   valid token or a matching `If-None-Match`.

### Edge Cases

- The bundle resource is missing from the deployable: the service fails to start; it never answers an empty
  body, a stale copy or `501`.
- An `Accept` that excludes `application/yaml`: a `406` Problem, as for any operation.
- `If-None-Match` with surrounding whitespace, several tags, `*`, or a weak tag: handled as the contract's
  weak comparison says; anything unparseable matches nothing.
- A gateway in front of the hosted service could strip the `ETag` (risk named on #209): recorded as a hosted
  check, not built around.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Bundle packaged from the build | As a maintainer, I want the deployable to carry the validated contract bundle produced by the contract build as a resource, wired from that build step's output, so that the served document can never be a hand copy or go stale. | High | Open | [build] | no — today no bundle is packaged |
| FR-002 | Document served byte for byte | As a client with a valid token, I want `GET /openapi.yaml` to answer `200` with the packaged bundle's exact bytes as `application/yaml`, so that I read the contract the service answers to. | High | Open | [build] | no — today it answers 501 |
| FR-003 | Strong ETag fixed at startup | As a client, I want every `200` and `304` to carry `ETag: "sha256:<hex>"`, the SHA-256 of the served bytes, computed once when the service starts. | High | Open | [build] | no |
| FR-004 | 304 on a matching If-None-Match | As a client holding the document, I want a weakly matching `If-None-Match` (or `*`) to answer `304` with no body, the ETag and the Charter headers; a non-matching or unparseable value answers `200`. | High | Open | [build] | no — paired with the 200 case on the same request |
| FR-005 | Guards and headers still apply | As an operator, I want the token check (`401` Problem), the browser refusal (`403` Problem), the Charter headers and the shared Problem rendering to apply to this operation exactly as to every other, checked before `If-None-Match` is read. | High | Open | [ratchet] | yes — paired with FR-002/FR-004 on the same fixture |
| FR-006 | Fail closed on a missing bundle | As an operator, I want the service to refuse to start when the bundle resource is absent or unreadable, so that it never answers a partial document. | Medium | Open | [build] | no |
| FR-007 | Interim wording retired | As a reader of the docs and contract changelog, I want the "501 until #209" wording replaced by what is now true, and a docs changelog entry announcing the served document. | Medium | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Exact assertions | Each acceptance case (200 bytes and ETag, 401, 403, 304) has a test asserting the exact status, media type and body (0 differing bytes against the build output; empty body for 304), never only a status. | Test quality | High | Open |
| NFR-002 | Digest once | The digest is computed 1 time per service start, not per request. | Performance | Medium | Open |
| NFR-003 | Gate green | `./gradlew verifyAll` passes on Java 25 with strict dependency verification and the style gates. | Reliability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | No contract change | 0 changes under `contracts/charter/` except the contract CHANGELOG note; no version bump, no contract tag, no release cut. | Contract | High | Open |
| C-002 | Scope fence | Contract scenarios for this operation are #210; `.http` examples are #211; the `service-desc` link is #126. Container properties (#228) and packs (#194) are parallel work: do not touch `CharterProperties` or pack code beyond what serving needs. | Process | High | Open |
| C-003 | Hexagonal rules | The ArchUnit gate stays green with no new exemption. | Architectural | High | Open |
| C-004 | Land by PR | Draft PR to `main` with "Closes #209"; never push to `main`; the operator merges. | Process | High | Open |

### Key Entities

- **Contract document**: the bundled charter OpenAPI file the contract build produces and validates; its bytes
  and their SHA-256.
- **Entity tag**: `"sha256:<64 lowercase hex>"`, strong; compared weakly against `If-None-Match`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A request with a valid token gets 0 differing bytes against the bundle build output, and an ETag equal to its checksum. — [build] · no-op passable: no
- **SC-002**: The four acceptance cases of #209 (200, 401, 403, 304) are covered by tests that fail when the serving is removed. — [build] · no-op passable: no
- **SC-003**: `./gradlew verifyAll` passes on Java 25 with 0 new architecture exemptions. — [ratchet] · no-op passable: yes — paired with SC-002

## Assumptions

- The default ruling on #207 holds: no anonymous fetch in hosted mode.
- The browser and token checks are those GuardWiring applies today; this mission does not redefine them.
- If-None-Match edge cases beyond the contract text are pinned by #210's scenarios.
