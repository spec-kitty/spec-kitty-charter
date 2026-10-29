# Work Packages: Container profile properties record

**Inputs**: Design documents from `kitty-specs/container-properties-record-01M64FVJ/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Required by the spec (FR-002, FR-003, FR-004, SC-001); WP01 writes them.

**Organization**: Subtasks (`Txxx`) roll up into work packages (`WPxx`). Record completion with
`spec-kitty agent tasks mark-status <Txxx> --status done`; there are no checkboxes.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Add `spring-boot-starter-validation` to the catalog and the deployable; regenerate verification metadata | WP01 | |
| T002 | `ContainerProperties` record at `charter.container.*` with bean-validation constraints naming the canonical key | WP01 | |
| T003 | `ContainerWiring`: enable the record under the `container` profile only | WP01 | |
| T004 | `ContainerPropertiesTest`: binding, each failure, profile off, another profile, the shipped application | WP01 | |
| T005 | Pin the validation side effect (400, not 500) in `ProblemResponsesTest` | WP01 | |
| T006 | Run `:deployable:check` and the full `verifyAll`; non-vacuity check without `@Validated` | WP01 | |
| T007 | Changelog entry and index line | WP02 | |
| T008 | Docs checks: `check_docs.py`, markdownlint-cli2, cspell | WP02 | |
| T009 | `ContainerStartupCheck`: port, config root, development flag, project configuration directory under `container` | WP03 | |
| T010 | `ContainerStartupCheckTest`: each breach, a valid start, the unset port, the profile off | WP03 | |
| T011 | Shipped-application tests pass the port and root as arguments; pin the local port 0 refused under `container` | WP03 | |
| T012 | Gates, non-vacuity check and the changelog update | WP03 | |

---

## Work Package WP01: Validated container record (Priority: P1)

**Goal**: Under the `container` profile, `charter.container.allowed-hosts` binds into a validated record and a missing,
empty or blank value fails the start naming the key; without the profile nothing changes.
**Independent Test**: `./gradlew :deployable:test --tests '*ContainerPropertiesTest*' --tests '*ProblemResponsesTest*'`
passes, and the failure tests go red without `@Validated`.
**Prompt**: `tasks/WP01-validated-container-record.md`
**Requirement Refs**: FR-001, FR-002, FR-003, FR-004, NFR-001, NFR-002, NFR-003, C-001, C-002, C-003, C-004

### Included Subtasks

T001 Add the validation starter and regenerate verification metadata (WP01)
T002 `ContainerProperties` record (WP01)
T003 `ContainerWiring` under the `container` profile (WP01)
T004 `ContainerPropertiesTest` (WP01)
T005 Pin the 400 side effect in `ProblemResponsesTest` (WP01)
T006 Gates and non-vacuity check (WP01)

**Dependencies**: none.
**Risks**: the HTTP side effect (research.md R2) is open on #228; the start-up validator scope (R1) is deferred.

---

## Work Package WP02: Changelog (Priority: P2)

**Goal**: The changelog announces the record, the new dependency and its HTTP side effect, and names the follow-ups.
**Independent Test**: the docs checks pass.
**Prompt**: `tasks/WP02-changelog.md`
**Requirement Refs**: SC-003

### Included Subtasks

T007 Changelog entry and index line (WP02)
T008 Docs checks (WP02)

**Dependencies**: Depends on WP01.

---

## Work Package WP03: Start-up check on keys outside the namespace (Priority: P1)

**Goal**: Under the `container` profile, port 0, an unusable configuration root, the development flag or a project
configuration directory fails the start naming the property (operator ruling on #228, ADR-0041 decisions 4 to 6).
**Independent Test**: `./gradlew :deployable:test --tests '*ContainerStartupCheckTest*' --tests '*ContainerPropertiesTest*'`
passes, and the eight breach tests go red when the check does nothing.
**Prompt**: `tasks/WP03-container-startup-check.md`
**Requirement Refs**: FR-005, FR-006, FR-007, FR-008, SC-004

### Included Subtasks

T009 `ContainerStartupCheck` (WP03)
T010 `ContainerStartupCheckTest` (WP03)
T011 Shipped-application tests (WP03)
T012 Gates, non-vacuity and changelog (WP03)

**Dependencies**: Depends on WP01.
