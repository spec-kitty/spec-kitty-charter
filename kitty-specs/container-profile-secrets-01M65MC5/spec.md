# Mission Specification: Container profile file and secret import

**Mission Branch**: `issue-229-hosted-config-secrets`
**Created**: 2026-10-30
**Status**: Draft
**Input**: Issue #229 (epic #226, ADR-0041): the profile file with non-secret defaults only, a required import of mounted
secrets, env-var binding for the same keys, a docker compose example with an env file, `.env.example` committed and `.env`
ignored, and the local `application.yaml` unchanged and asserted.

## Intent Summary

- **Primary actor:** an operator who runs the charter service in a container under the `container` profile
  (`SPRING_PROFILES_ACTIVE=container`, ADR-0041 decision 1).
- **Trigger:** the service starts under that profile, from docker compose or any other container platform.
- **Outcome:** the profile's own file supplies the non-secret defaults: listen on all interfaces, on port 8080. The
  secrets come only from files mounted under `/run/secrets/` or from env vars. If the secrets mount is missing, the
  service refuses to start. A compose example and an `.env.example` show the setup without committing any secret.
- **Invariant:** no committed file holds a secret value. Without the profile, the local daemon starts exactly as before:
  `application.yaml` is unchanged and still binds `127.0.0.1` on port 0.
- **Canonical terms:**
  - "Container profile" is the Spring profile `container`.
  - "Hosted" is the ADR-0034 deployment option, not a profile name.
  - "Secrets directory" is the directory the service reads mounted secret files from (default `/run/secrets/`).
  - "Config tree" is Spring's name for such a directory: one file per property.
  - The issue body predates ADR-0041 and says `hosted` and `charter.hosted.*`. This mission follows ADR-0041 and PR #266
    (research R1).

Confirmation:

- The operator confirmed this intent on 2026-10-30.
- The operator also chose how to resolve the secrets directory (decision `01M65MGKX68G0EGHV2AETWWKNX`). The import reads
  `configtree:${charter.container.secrets-dir:/run/secrets/}`, with no `optional:` prefix. The directory stays required,
  and the default is ADR-0041's path. This is recorded as a clarification of ADR-0041 decision 3.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Secrets come from the mounted directory or the environment (Priority: P1)

An operator mounts the token as `/run/secrets/charter.worktree-token`, or passes it as `CHARTER_WORKTREE_TOKEN` from an
env file. Either way, the containerized service starts with that token. The other container settings, such as
`charter.container.allowed-hosts`, can come the same two ways.

**Why this priority**: this is ADR-0041 decision 3 and the issue's third acceptance criterion. A container must be
configurable without putting a secret into a committed file or onto the command line.

**Independent Test**: start the shipped application under the `container` profile with a temporary secrets directory.
Put the values in files in that directory in one start, and in env vars in another. Then read the bound values back.

**Acceptance Scenarios**:

1. **Given** the container profile and a secrets directory holding `charter.worktree-token` and
   `charter.container.allowed-hosts`, **When** the service starts, **Then** the guard enforces that token and the
   container record holds those hosts.
2. **Given** the container profile, an empty secrets directory, and the same values as `CHARTER_WORKTREE_TOKEN` and
   `CHARTER_CONTAINER_ALLOWEDHOSTS` in the environment, **When** the service starts, **Then** the same values bind.
3. **Given** a secret file whose content ends in a newline, **When** the service starts, **Then** the bound value has
   no trailing newline.
4. **Given** a file named in env-var style (`CHARTER_WORKTREE_TOKEN`) in the secrets directory, **When** the service
   starts, **Then** no property is set from it, and the start fails for want of a token.

### User Story 2 - A container without its secrets mount does not start (Priority: P1)

An operator forgets to mount the secrets directory. The service does not start. The failure names the directory it
expected.

**Why this priority**: this is ADR-0041 decision 3 and the issue's second acceptance criterion. A required import
catches a missing mount before any property is read (ADR-0041, alternatives considered).

**Independent Test**: start the shipped application under the container profile with the secrets directory pointing
at a path that does not exist, and with no override at all. Both starts fail, naming the path.

**Acceptance Scenarios**:

1. **Given** the container profile and no secrets-directory override, **When** `/run/secrets/` does not exist,
   **Then** the start fails, naming `/run/secrets/`.
2. **Given** the container profile and an override that names a missing directory, **When** the service starts,
   **Then** the start fails, naming that directory.
3. **Given** the container profile and an existing empty secrets directory, with every secret supplied as an env var,
   **When** the service starts, **Then** it starts.

### User Story 3 - A refused token says where a container's token goes (Priority: P2)

An operator starts a container with no token. The refusal names both places a container takes the token from: the env
var and the mounted file. The local daemon's refusal is unchanged.

**Why this priority**: ADR-0041 decision 3 changes this message. The #228 mission handed the change to this one
(research R2).

**Independent Test**: start the guard wiring without a token, once with the container profile and once without it.
Then compare the two refusals.

**Acceptance Scenarios**:

1. **Given** the container profile and no token, **When** the service starts, **Then** the refusal names
   `CHARTER_WORKTREE_TOKEN` and `/run/secrets/charter.worktree-token`, and does not offer the development flag, which
   the container profile refuses.
2. **Given** the container profile and the token on the command line, **When** the service starts, **Then** the
   refusal names both accepted forms.
3. **Given** no profile and no token, **When** the service starts, **Then** the refusal is the local one, as before.

### User Story 4 - The profile's defaults ship, and nothing secret is committed (Priority: P1)

An operator starts the container with only its secrets and its deployment-specific values. The service listens on all
interfaces on port 8080. A reviewer can check that no committed configuration file, compose file or env template holds
a secret.

**Why this priority**: these are the issue's first acceptance criterion and its scope items (profile file, compose
example, `.env.example`, `.gitignore`).

**Independent Test**: read the committed files and assert their content. Separately, start the shipped application
under the profile without setting a port and read back the address and port it binds.

**Acceptance Scenarios**:

1. **Given** the container profile and no port setting, **When** the service starts, **Then** it uses port 8080 on
   address `0.0.0.0`.
2. **Given** the container profile with an explicit `server.port=0`, **When** the service starts, **Then** the start
   is still refused (the #228 rule holds).
3. **Given** the committed profile file, **When** it is read, **Then**:
   - it holds no token, no allowed hosts, no config root, no development flag and no profile activation;
   - its import has no `optional:` prefix and defaults to `/run/secrets/`.
4. **Given** the committed `.env.example` and compose example, **When** they are read, **Then**:
   - every variable in `.env.example` has an empty value;
   - the compose file reads the env file, mounts a secrets directory at `/run/secrets`, activates the container
     profile and holds no secret value.
5. **Given** the repository's `.gitignore`, **When** git checks `.env` beside the compose example, **Then** it is
   ignored, and `.env.example` is not.

### User Story 5 - The local daemon is unchanged (Priority: P1)

A developer starts the service as the launcher does, with no profile.

**Why this priority**: this is ADR-0041 decision 1 and the issue's last scope item.

**Independent Test**: read the committed `application.yaml` and assert its loopback address, port 0 and the absence of
any import. Separately, start without the profile and with no `/run/secrets/`.

**Acceptance Scenarios**:

1. **Given** the committed `application.yaml`, **When** it is read, **Then** it binds `127.0.0.1` on port 0 and imports
   nothing.
2. **Given** no profile and no secrets directory, **When** the service starts, **Then** it starts as before.

### Edge Cases

- **Two secret sources:** a secret supplied both as a file and as an env var takes the env var, which is Spring's
  property-source order (environment above imported config data). This is documented, not changed.
- **Unused secret files:** a secrets directory may hold files for other services. Files that name no property bind
  nowhere.
- **Overriding the secrets directory:** `charter.container.secrets-dir` can be set from the environment or the command
  line. It is a path, not a secret, so it may appear in a message.
- **Command line refusal:** the token is still refused from the command line and from system properties under both
  profiles.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Container profile file | As an operator, I want a profile file loaded only under the `container` profile that sets non-secret defaults (all interfaces, port 8080) and nothing deployment-specific or secret, so that a container starts with the right bind without committing a secret (ADR-0041 decisions 1, 3 and 4). | High | Open | [build] | no — the start reads 8080 back where the local file gives 0 |
| FR-002 | Required secrets import | As an operator, I want the container profile to import the secrets directory (default `/run/secrets/`, overridable by `charter.container.secrets-dir`) with no `optional:` prefix, so that a container started without the mount fails the start, naming the directory (ADR-0041 decision 3; operator decision `01M65MGKX68G0EGHV2AETWWKNX`). | High | Open | [build] | no — paired with a start on an existing directory |
| FR-003 | Mounted secrets bind | As an operator, I want a file in the secrets directory named after a property in dot form to set that property (token and allowed hosts included), with the trailing newline dropped, and a file named in env-var style to set nothing, so that Docker and Kubernetes secrets configure the service. | High | Open | [build] | no — the bound values are read back |
| FR-004 | Env vars bind the same keys | As an operator, I want the same keys supplied as env vars (`CHARTER_WORKTREE_TOKEN`, `CHARTER_CONTAINER_ALLOWEDHOSTS`) to bind under the container profile with an empty secrets directory, so that a deployed env file configures the service. | High | Open | [build] | no — the bound values are read back |
| FR-005 | Container token message | As an operator, I want the token refusals under the container profile to name both the env var and the mounted file, and not to offer the development flag, so that the message says where a container's token goes (ADR-0041 decision 3). | Medium | Open | [build] | no — the local message stays as it is on the same input |
| FR-006 | Compose example | As an operator, I want a committed docker compose example that runs the service under the container profile, reads an env file, mounts a secrets directory at `/run/secrets` and names the image through a required variable, so that the setup is shown end to end without inventing an image tag (#234). | Medium | Open | [build] | no — a test reads the file |
| FR-007 | Env template only | As a reviewer, I want only `.env.example` committed, with every variable named and none given a value, and `.env` ignored by git, so that no secret value can be committed by accident. | High | Open | [build] | no — `git check-ignore` decides |
| FR-008 | No secret in a committed config | As a reviewer, I want a test that fails when a committed `application*.yaml`, the compose example or `.env.example` holds a value for the token, so that the rule "no secret is committed" is checked, not only stated. | High | Open | [build] | no — a positive control shows the check catches a planted value |
| FR-009 | Local file asserted | As a developer, I want a test that pins `application.yaml` to `127.0.0.1`, port 0 and no import, so that the local daemon stays unchanged (ADR-0041 decision 1). | High | Open | [ratchet] | yes — it pins existing content, paired with FR-001's read-back of the container values |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | No new suppressions | Zero new entries in `config/checkstyle/suppressions.xml` and zero new `@SuppressWarnings`. javac `-Werror`, Error Prone/NullAway, doclint, Checkstyle and Spotless all stay at zero warnings. | Maintainability | High | Open |
| NFR-002 | Complexity | Every new or changed method has cyclomatic complexity of at most 15. | Maintainability | High | Open |
| NFR-003 | No new dependency | `gradle/verification-metadata.xml` and `gradle/libs.versions.toml` are unchanged. | Security | High | Open |
| NFR-004 | No secret in output | No message, log line or test output added by this mission holds a token value; at most 0 occurrences of a test token in failure messages. | Security | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | ADR-0041 governs | The profile is `container`, the file is `application-container.yaml`, and the namespace is `charter.container.*`. The packs module does not learn the profile. | Technical | High | Open |
| C-002 | Scope fence | Out of scope: the configuration processor and docsite table (#230); the local loopback-bind refusal and guard tests (#231); actuator and compose health check (#232); the image, its mount path and the config-root default (#234). The guard does not read `allowed-hosts` here (follow-up). | Technical | High | Open |
| C-003 | Local path unchanged | `application.yaml`, `CharterProperties` and the local start do not change. | Technical | High | Open |
| C-004 | Parallel work | Changes stay in the deployable's configuration, its tests, the compose example, `.gitignore` and docs. The packs surface (#260, #194 PR-B) is not touched. | Process | Medium | Open |

### Key Entities

- **Secrets directory**: a mounted directory with one file per secret property, named in dot form.
- **Env template**: the committed list of variable names an operator fills in a local, ignored `.env`.

## Assumptions

- No `charter.config-root` default is set in the profile file. The root inherited from `application.yaml` does not
  exist in a container, so the #228 start-up check refuses the start until a root is mounted (ADR-0041 decision 5). #234
  sets the image's default.
- The compose example names its image through `CHARTER_IMAGE`, which must be set. No image exists until #234.
- `charter.container.secrets-dir` is read by Spring's config import, not by the container record. It is not a record
  component, and #230's reference table lists it.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A container started without its secrets directory fails to start, naming the directory, in 2 of 2 cases
  (default path, overridden path). — [build] · no-op passable: no
- **SC-002**: The token and allowed hosts bind from mounted files and from env vars, in 2 of 2 sources. — [build] ·
  no-op passable: no
- **SC-003**: 0 committed configuration or env-template files hold a secret value, checked by a test with a positive
  control. — [build] · no-op passable: no
- **SC-004**: The local start, with no profile, behaves as before: the existing deployable tests pass, and
  `application.yaml` is byte-for-byte unchanged. — [ratchet] · no-op passable: yes — paired with SC-002
- **SC-005**: The full build with every check passes, with zero new suppressions. — [ratchet] · no-op passable: yes
