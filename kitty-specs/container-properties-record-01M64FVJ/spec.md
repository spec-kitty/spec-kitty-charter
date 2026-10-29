# Mission Specification: Container profile properties record

**Mission Branch**: `feat/228-container-config-properties`
**Created**: 2026-10-29
**Status**: Draft
**Input**: Issue #228 (epic #226, ADR-0041): container-profile validated properties record `charter.container.*`
(allowed-hosts), bean validation, fail-closed startup, tests per failure; local path unchanged.

## Intent Summary

- **Primary actor:** an operator who starts the charter service in a container with the `container` profile
  (ADR-0041 decision 1).
- **Trigger:** the service starts with `container` active.
- **Outcome:** the settings in the `charter.container.*` namespace are bound into one typed, validated record, and
  the profile's rules on keys outside it (a fixed port, an existing configuration root, no development flag and no
  worktree configuration directory) are checked. A missing or invalid value stops the start, and the error names the
  property.
- **Invariant:** without `container`, nothing is bound or validated at `charter.container.*`, and the local daemon
  starts exactly as before.
- **Canonical terms:** "container profile" names the Spring profile `container`. "Hosted" names the ADR-0034
  deployment option, not the profile. The issue's body predates ADR-0041 and says `hosted`; this mission follows
  ADR-0041.

Confirmation: the session runs without an interactive user. The parent session's brief, issue #228 and ADR-0041
stand in for the confirmed intent. The operator settled the scope on #228 (issuecomment-6506747587): the start-up
rules of ADR-0041 decisions 4 to 6 are part of this mission, under the `container` profile only.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A misconfigured container fails at start (Priority: P1)

An operator starts the service with the `container` profile and forgets the allowed host names, or gives a blank
one. The service does not start. The error names `charter.container.allowed-hosts`.

**Why this priority**: ADR-0041 decision 2. Without it a misconfigured container could start and serve nothing, or
serve anyone.

**Independent Test**: start the context with the profile active and the key missing, empty or with a blank entry;
the context fails and the failure names the key.

**Acceptance Scenarios**:

1. **Given** the `container` profile, **When** `charter.container.allowed-hosts` is absent, **Then** the start
   fails, naming `charter.container.allowed-hosts`.
2. **Given** the `container` profile, **When** the list is present but has no entries, **Then** the start fails,
   naming the key.
3. **Given** the `container` profile, **When** one entry is blank, **Then** the start fails, naming the key and the
   entry's index.
4. **Given** the `container` profile, **When** the list holds non-blank host names, **Then** the record binds them
   in order and the context starts.

### User Story 2 - The local daemon is unchanged (Priority: P1)

A developer starts the service without any profile, as the launcher does.

**Why this priority**: ADR-0041 decision 1 and its positive consequence: the local daemon is unchanged.

**Independent Test**: start the context without the profile and without any `charter.container.*` key; it starts,
and no container record bean exists.

**Acceptance Scenarios**:

1. **Given** no `container` profile and no `charter.container.*` keys, **When** the service starts, **Then** it
   starts and no container record is bound.
2. **Given** no `container` profile, **When** an invalid `charter.container.*` value is present, **Then** the start
   is not affected: the namespace is not bound outside the profile.

### Edge Cases

- A host entry that is only whitespace counts as blank.
- The failure message names the key in its canonical form (`charter.container.allowed-hosts`); Spring's own report
  adds the Java field name and the rejected value. No value in this namespace is a secret (ADR-0041 decision 8), so the
  rejected value may appear; a later secret never enters this record.
- Another profile active alongside `container` (for example a test profile) still binds and validates the record.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Container record | As an operator, I want the settings at `charter.container.*` bound into one typed record in the deployable, beside `CharterProperties`, holding `allowed-hosts` as an ordered list of host names, so that the container profile has one validated home for its own keys (ADR-0041 decisions 2 and 6). | High | Open | [build] | no — the valid-binding test reads the bound list back |
| FR-002 | Fail closed on a missing or invalid value | As an operator, I want the start to fail when `charter.container.allowed-hosts` is absent, empty or has a blank entry under the `container` profile, the failure naming the property, so that a misconfigured container never serves. | High | Open | [build] | no — paired with the valid-binding test on the same runner |
| FR-003 | Profile-only binding | As a developer, I want the record bound and validated only when the `container` profile is active, so that the local daemon's start and bindings do not change. | High | Open | [build] | no — paired with the container-profile failure on the same invalid input |
| FR-004 | Validation side effect pinned | As a client of the registration endpoints, I want a body missing a field the contract requires refused as a 400 problem before the handler runs (the effect of putting bean validation on the classpath, ADR-0041 decision 2), never the 500 `internal_error` it was before, so that the HTTP change the dependency brings is deliberate and visible. Raised on #228 (issuecomment-6505750016). | Medium | Open | [build] | no — the same request answered 500 without the dependency (measured) |
| FR-005 | Fixed port in a container | As an operator, I want a container-profile start on port 0 refused, naming `server.port`, so that a container never listens on a port nobody can publish (ADR-0041 decision 4). | High | Open | [build] | no — paired with the fixed-port start on the same runner |
| FR-006 | Existing configuration root | As an operator, I want a container-profile start refused when `charter.config-root` is unset, relative, missing or not a directory, naming the property and not its value, so that a forgotten volume never gives a running, empty service (ADR-0041 decision 5). | High | Open | [build] | no — paired with an existing absolute root on the same runner |
| FR-007 | No development flag in a container | As an operator, I want a container-profile start refused when `charter.insecure-no-token` is set, whatever its value, so that a container never runs without the token (ADR-0041 decision 6). | High | Open | [build] | no |
| FR-008 | No worktree configuration in a container | As an operator, I want a container-profile start refused when `charter.project-config-dir` is set, so that a shared container never ties its fingerprint to one worktree (ADR-0041 decision 6). | High | Open | [build] | no |


### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | No new suppressions | Zero new entries in `config/checkstyle/suppressions.xml` and zero new `@SuppressWarnings`; every existing gate (javac `-Werror`, Error Prone/NullAway, doclint, Checkstyle, Spotless) stays at zero warnings. | Maintainability | High | Open |
| NFR-002 | Complexity | Every new method has cyclomatic complexity at most 15. | Maintainability | High | Open |
| NFR-003 | Verified dependencies | Every artifact the new validation dependency brings is in `gradle/verification-metadata.xml` with a SHA-256, regenerated by the documented command, never edited by hand. | Security | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | ADR-0041 governs | The profile is `container`, the namespace `charter.container.*`, and the record lives in the deployable module; the packs module does not learn the profile. | Technical | High | Open |
| C-002 | Scope fence | No `application-container.yaml` or secret import, and no token-message change (#229); no configuration processor or docsite table (#230); no guard changes, guard tests or local loopback rule (#231); no actuator (#232). The guard does not consume `allowed-hosts` in this mission (a follow-up issue). | Technical | High | Open |
| C-003 | Local path unchanged | `application.yaml`, `CharterProperties` and the local start behaviour do not change. | Technical | High | Open |
| C-004 | Parallel work | Changes stay in the deployable's configuration classes, their tests and the build files; no controller (#209) or packs (#194) change. | Process | Medium | Open |

### Key Entities

- **Container properties**: the settings of the `container` profile in its own namespace; for now, the host names a
  request's `Host` may carry.

## Assumptions

- The record holds only `allowed-hosts` for now: ADR-0041 names no other key in `charter.container.*`. Keys outside
  the namespace (`server.*`, `charter.config-root`, `charter.worktree-token`, `charter.insecure-no-token`,
  `charter.project-config-dir`) are not record components; the start-up check reads them.
- "Set" means present in any property source, whatever its value: `charter.insecure-no-token=false` also fails, and
  the message says to remove it.
- An unset `server.port` is Spring's fixed default (8080) and passes. The local `application.yaml` sets 0, so until
  `application-container.yaml` (#229) gives the container default, a container must set `SERVER_PORT`.
- Host entries get no format rule beyond non-blank. The guard that compares against them settles the format.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every invalid `allowed-hosts` case (absent, empty, a blank entry) stops a container-profile start
  with the key named in the failure, in 3 of 3 cases. — [build] · no-op passable: no
- **SC-004**: Every breach of the start-up rules (port 0; config root unset, relative, missing or a file; the
  development flag; a project configuration directory) stops a container-profile start naming its property, in 8 of
  8 cases, and none of them applies without the profile. — [build] · no-op passable: no
- **SC-002**: The local start, with no profile, behaves as before: the existing deployable test suite passes
  unchanged. — [ratchet] · no-op passable: yes — paired with SC-001 on the same inputs
- **SC-003**: The full build with every check passes, with zero new suppressions. — [ratchet] · no-op passable: yes
