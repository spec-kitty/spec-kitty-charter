# Mission Specification: Container guard, actuator and single-replica ruling

**Mission Branch**: `feat/container-guard-and-actuator`
**Created**: 2026-11-05
**Status**: Draft
**Input**: Issues #233, #268, #231 and #232 under epic #226. The operator's rulings apply: #226 (2026-10-27, the
later comment is in force), #233 (2026-11-05, one replica for now) and ADR-0041 (accepted 2026-10-29, clarified
2026-10-30). The pre-spec research squad (architecture, security and build lenses, 2026-11-05) is recorded in
[research.md](research.md).

## Intent Summary

- **Primary actor:** an operator who runs the charter service in a container under the `container` profile and
  reaches it by a real host name (for example `charter.internal`), behind docker compose or another platform.
- **Secondary actors:**
  - the platform's health probe, which must learn "up or down" without holding the token;
  - the local launcher, whose per-worktree daemon must stay loopback-only.
- **Trigger:** a request reaches the container with `Host: charter.internal`, or a platform probes
  `GET /actuator/health`.
- **Outcome:**
  - A request whose `Host` is in `charter.container.allowed-hosts` passes the host check under the container
    profile; any other `Host` is still refused with 403.
  - Exactly `GET /actuator/health` answers up or down without a token under the container profile, and nothing
    else about the configuration is exposed.
  - An ADR records that a deployment runs exactly one replica for now.
- **Invariants:**
  - Without the container profile, the guard behaves exactly as today. Health is reachable only with the token,
    and the service never starts bound beyond loopback.
  - DNS rebinding stays stopped. A browser request is still refused with 403.
  - A secret value never appears in a response, a refusal message or a log line.
  - A misconfiguration that would widen exposure fails the start and names the property.
- **Boundaries:**
  - ADR-0020 D12, the reshape that moves browser refusal to the local profile and adds a CORS policy, is not part
    of this mission.
  - Shared state across replicas, sticky routing, probes beyond `health`, and the container image (#234) are
    out of scope.
  - The login flow and token format (ADR-0034 D5) stay open.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reach a container by its real host name (Priority: P1)

The operator lists the names the container answers to in `charter.container.allowed-hosts`. A request with
one of those names in `Host`, at any port, passes the host check and is then judged on its token, as today.

**Why this priority**: today a container reached by a real host name refuses every request. This is the
blocker for any hosted use (#268).

**Independent Test**: start the guard with a configured list and send requests with listed and unlisted
`Host` values.

**Acceptance Scenarios**:

1. **Given** the container profile with `allowed-hosts=charter.internal`, **When** a request carries
   `Host: charter.internal:8080` and the right token, **Then** it passes.
2. **Given** the same list, **When** `Host` is `CHARTER.INTERNAL`, **Then** it passes: host names are
   compared without regard to case.
3. **Given** the same list, **When** `Host` is `localhost` or `attacker.example`, **Then** it is 403 whatever
   token it carries.
4. **Given** the same list, **When** a request carries no `Host` header, **Then** it is 403.
5. **Given** the same list, **When** a request carries a listed `Host` and an `Origin` header, **Then** it is
   403.
6. **Given** no container profile, **When** a request carries `Host: charter.internal`, **Then** it is 403,
   exactly as today.
7. **Given** the container profile, **When** an entry in `allowed-hosts` is `*`, `*.example.com`,
   `host:8080`, `http://host`, `0.0.0.0`, `[::]`, a name with a trailing dot or a non-ASCII name, **Then**
   the start fails, naming the property and never a secret.

### User Story 2 - Probe health without the token (Priority: P1)

A platform or compose health check sends `GET /actuator/health` and learns whether the service is up. It
learns nothing else: no components, no details and no configuration.

**Why this priority**: without a probe, compose and every platform can only see that the process runs
(#232, ADR-0041 D7).

**Independent Test**: start the service under each profile and request the health path and its near misses,
with and without the token.

**Acceptance Scenarios**:

1. **Given** the container profile, **When** `GET /actuator/health` arrives with a listed `Host` and no token,
   **Then** the answer is 200 with the body `{"status":"UP"}` and nothing more.
2. **Given** the container profile, **When** `GET /actuator/health` arrives with an unlisted `Host` or a
   browser header, **Then** it is 403.
3. **Given** the container profile, **When** the request is `HEAD /actuator/health`, `POST /actuator/health`,
   `/actuator/health/`, `/actuator/health;x=y`, `/actuator/health/liveness`, `/actuator/%68ealth`,
   `/ACTUATOR/health`, `/actuator/healthz`, `/actuator` or `/actuator/env`, without a token, **Then** it is
   401.
4. **Given** either profile and the right token, **When** `/actuator/env`, `/actuator/configprops`,
   `/actuator/beans`, `/actuator/loggers`, `/actuator/heapdump`, `/actuator/threaddump` or
   `/actuator/mappings` is requested, **Then** the answer is a 404.
5. **Given** no container profile, **When** `GET /actuator/health` arrives without a token, **Then** it is
   401; with the token it is 200 `{"status":"UP"}`.
6. **Given** either profile, **When** the environment widens the exposure, the health details, a sanitized
   value or the management port (for example `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=*`), **Then** the
   start fails, naming the property.

### User Story 3 - The start refuses an unsafe profile configuration (Priority: P2)

The local daemon never binds beyond loopback, and a container never starts half-configured (#231,
ADR-0041 D4).

**Independent Test**: start the application with each configuration and observe whether it starts.

**Acceptance Scenarios**:

1. **Given** no profile and the shipped configuration, **When** the service starts, **Then** it binds
   `127.0.0.1` on a random port, and the container profile is not active.
2. **Given** no container profile, **When** `server.address` is `0.0.0.0`, `::`, a non-loopback address or
   blank, **Then** the start fails, naming `server.address`.
3. **Given** no container profile, **When** `server.address` is `127.0.0.2`, `::1` or `localhost`, **Then** the
   service starts.
4. **Given** the container profile, **When** `server.address` is unset, `0.0.0.0` or `127.0.0.1`, **Then** the
   bind rule does not fail the start.
5. **Given** the container profile and an empty secrets directory, **When** no token is set as a file or an env
   var, **Then** the start fails with a message that names `CHARTER_WORKTREE_TOKEN` and the token file.

### User Story 4 - A malformed parameter is a 400 (Priority: P3)

A request parameter that breaks a bound the contract declares is answered as a malformed request (400, the
plain Problem without a code) by whichever controller receives it, not as a 500 (#268 note).

**Acceptance Scenarios**:

1. **Given** any controller whose interface is validated, **When** a parameter breaks its bound, **Then** the
   answer is a 400 Problem without a code or a detail.
2. **Given** a violation that is not on a request parameter (for example, a return value), **When** it is
   raised, **Then** it stays a 500 `internal_error`.

### User Story 5 - The replica model is recorded (Priority: P2)

A reader of the ADRs and the container guide learns that a deployment runs exactly one replica, and why
(#233).

**Acceptance Scenarios**:

1. **Given** the ADR index, **When** a reader looks for the replica model, **Then** an accepted ADR states
   "one replica per deployment" and names the in-memory state that requires it.
2. **Given** the container guide, **When** a reader looks for its limits, **Then** it says "single replica".

### Edge Cases

- A `Host` with a trailing dot (`charter.internal.`) does not match a listed name and is refused. This is
  documented, not normalized.
- A Kubernetes `httpGet` probe sends the pod IP as `Host`. The guide tells operators to set the probe's `Host`
  header to a listed name rather than list a wildcard.
- `X-Forwarded-Host` never changes the name judged under the container profile.
- Two `Host` headers are refused unless each is listed. The servlet container normally refuses them first.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status | Delivery | No-op passable? |
|---|---|---|---|---|
| FR-001 | Under the container profile, the request guard passes the host check for a request whose server name and every `Host` value, port removed and compared without regard to case, are in `charter.container.allowed-hosts`. Any other request is 403. | Proposed | [build] | no |
| FR-002 | Under the container profile, a request with no `Host` header is 403. | Proposed | [build] | no |
| FR-003 | Without the container profile, the guard's host check accepts exactly the loopback names it accepts today, with the same refusals. | Proposed | [ratchet] | yes — paired with FR-001 on the same guard fixture with a configured list |
| FR-004 | The request-guard component receives its accepted host names and its token-exempt path from the composing module. It holds no knowledge of profiles. | Proposed | [build] | no |
| FR-005 | Under the container profile, an `allowed-hosts` entry that is not a DNS name, an IPv4 literal or a bracketed IPv6 literal fails the start, naming the property. Refused entries: a wildcard, a port, a scheme, a path, userinfo, a trailing dot, non-ASCII text, or the any-address `0.0.0.0` or `[::]`. | Proposed | [build] | no |
| FR-006 | Browser-header refusal (`Origin`, `Sec-Fetch-Site`, `Sec-Fetch-Mode`) is unchanged under both profiles. | Proposed | [ratchet] | yes — paired with FR-001 on the same fixture |
| FR-007 | The contract's description of the 403 says that the host check compares against loopback names, or under a containerized deployment against its configured list. The change is recorded as a contract changelog fragment. | Proposed | [build] | no |
| FR-008 | The service exposes exactly one management endpoint over HTTP, `health`. It answers only up or down, never components, details or values, and never over JMX. | Proposed | [build] | no |
| FR-009 | Under the container profile, exactly `GET /actuator/health` (raw request path, no context path) passes without a token. The host and browser checks still apply. Every other method and path, near misses included, still requires the token. | Proposed | [build] | no |
| FR-010 | Without the container profile, `/actuator/health` requires the token like every other path. | Proposed | [ratchet] | yes — paired with FR-009 on the same application fixture |
| FR-011 | Under either profile, the start fails when: the web exposure is anything other than `health`; health details, components or values are not `never`; the management server port or address is set; or the management base path is changed. The message names the property. | Proposed | [build] | no |
| FR-012 | Without the container profile, the start fails unless `server.address` names a loopback address. Blank, unset, any-address and non-loopback values are refused, naming `server.address`. | Proposed | [build] | no |
| FR-013 | Under the container profile, the bind rule accepts any address, loopback included (ADR-0041 D4). Port 0 stays refused by the existing check. | Proposed | [ratchet] | yes — paired with FR-012 on the same fixture |
| FR-014 | The container profile is never active by default. The shipped configuration names no default or active profile. | Proposed | [ratchet] | yes — the shipped configuration must hold no profile default, checked from the file |
| FR-015 | Under the container profile, a start with an empty secrets directory and no token env var fails with a message naming both accepted forms, and never a value. | Proposed | [ratchet] | yes — paired with a start that has the token file on the same fixture |
| FR-016 | Under the container profile, request-detail logging is refused at start, and forwarded headers are ignored (`X-Forwarded-Host` changes no judged name). | Proposed | [build] | no |
| FR-017 | Across the scenarios above, no response, refusal message or captured log line contains the token. This includes a start with debug logging on. | Proposed | [ratchet] | yes — the existing token-never-logged test is extended to the new paths |
| FR-018 | A method-validation violation on a request parameter of any controller answers 400 with the plain Problem (no code, no detail). Other violations stay 500 `internal_error`. | Proposed | [build] | no |
| FR-019 | An accepted ADR records one replica per deployment. It names the in-memory per-project state (the write-through activation copy of ADR-0033, the ETags of ADR-0018) and says when this is revisited. The container guide says "single replica". | Proposed | [build] | no |
| FR-020 | The container guide and the compose example describe the allowed hosts, the health check and its probe `Host` header, and the single replica, and drop the "limits" this mission lifts. | Proposed | [build] | no |

### Non-Functional Requirements

| ID | Requirement | Threshold | Status |
|---|---|---|---|
| NFR-001 | Dependency verification stays strict. | Every new artifact has a SHA-256 entry; zero new exemptions | Proposed |
| NFR-002 | The native executable still builds and answers. | The PR's native job and smoke test pass on every target CI runs | Proposed |
| NFR-003 | The health answer reveals nothing but status. | The body is exactly `{"status":"UP"}` (or DOWN); zero other members | Proposed |
| NFR-004 | The full gate passes. | `./gradlew verifyAll` on Java 25 green; docs checks (all eight), markdownlint, cspell and the site build green | Proposed |

### Constraints

| ID | Constraint | Status |
|---|---|---|
| C-001 | The packs module does not learn the profile; the deployable composes the guard (ADR-0041 D2). | Accepted |
| C-002 | No `@Profile` gating; the profile is read at run time (ADR-0046 D5, `NoBuildTimeProfilesTest`). | Accepted |
| C-003 | The contract edit is limited to the 403 description and one changelog fragment. Sibling session B owns the registration bodies. The contract is unreleased 0.x; no version bump and no tag. | Accepted |
| C-004 | No push to `main`, no release cut, no contract tag. The operator merges. | Accepted |
| C-005 | Designed against this repository's model and contract, as if core did not exist (charter, 2026-10-28). | Accepted |
| C-006 | ADR number chosen at writing time against `main` and the open PRs. | Accepted |

### Key Entities

- **Allowed hosts:** the host names a request may give under the container profile. Under the local profile
  this is the loopback names.
- **Token-exempt path:** the single path a request may reach without the token. It is `GET /actuator/health`
  under the container profile only.
- **Replica:** one running service process of a deployment.

## Domain Language

| Canonical term | Meaning | Avoid |
|---|---|---|
| container profile | The `container` Spring profile (ADR-0041 D1) | "hosted profile" |
| hosted | The deployment option of ADR-0034 that the container profile serves | — |
| allowed hosts | `charter.container.allowed-hosts` | "whitelist", "trusted hosts" |
| health check | `GET /actuator/health`, answering up or down | "readiness", "liveness" (not exposed) |

## Success Criteria *(mandatory)*

- **SC-001:** Of the host values in User Story 1, 100% of listed values pass and 100% of the others are 403
  under the container profile. Without the profile, the existing guard tests pass unchanged. — [build] ·
  no-op passable: no
- **SC-002:** Every one of the 10 near-miss health requests in User Story 2, scenario 3, is refused, and the
  health body has exactly one member. — [build] · no-op passable: no
- **SC-003:** Each unsafe configuration named in User Stories 1, 2 and 3 fails the start with the property
  named. — [build] · no-op passable: no
- **SC-004:** A reader finds the one-replica rule in one accepted ADR and in the container guide. —
  [build] · no-op passable: no

## Assumptions

- Spring Boot 4.1's actuator needs no project runtime hints for the native image. The native smoke run
  confirms this.
- A trailing-dot host name and Unicode host names are refused rather than normalized. Operators list ASCII
  (punycode) names.
- Health is reachable locally, behind the token, so the local daemon and the container expose the same
  surface.
