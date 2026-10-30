# Work Packages: Container profile file and secret import

**Inputs**: Design documents from `kitty-specs/container-profile-secrets-01M65MC5/`
**Prerequisites**: plan.md, spec.md, research.md (R1 to R10), data-model.md, quickstart.md

**Tests**: Required by the spec (every FR row has a test); test first, red before the change.

**Organization**: Subtasks (`Txxx`) roll up into work packages (`WPxx`). Record completion with
`spec-kitty agent tasks mark-status <Txxx> --status done`; there are no checkboxes.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | `ContainerSecretsTest` (red): shipped-profile starts from a temp secrets dir and from env vars, missing dir, env-style file name, empty env token, defaults 8080 / 0.0.0.0 | WP01 | |
| T002 | `application-container.yaml`: required `configtree:` import with the secrets-dir placeholder, `server.address`, `server.port` | WP01 | |
| T003 | `ContainerPropertiesTest`: shipped starts get a secrets dir; the port-0 refusal passes `--server.port=0` explicitly | WP01 | |
| T004 | Non-vacuity check and `:deployable:check` | WP01 | |
| T005 | `GuardWiringTest` (red): container-profile refusals name both sources and the resolved dir; no development flag offered; empty-env hint; local messages unchanged | WP02 | [P] |
| T006 | `GuardWiring`: profile-aware messages through `Environment.matchesProfiles`, the token never in a message | WP02 | [P] |
| T007 | Non-vacuity check and `:deployable:check` | WP02 | |
| T008 | `ContainerStartupCheckTest` (red): a planted config-tree file outside the allowlist; blank, relative, root and others-writable secrets dirs; allowed files pass; nothing without the profile | WP03 | [P] |
| T009 | `ContainerStartupCheck`: config-tree key allowlist and safe secrets directory | WP03 | [P] |
| T010 | Non-vacuity check and `:deployable:check` | WP03 | |
| T011 | `deploy/compose/compose.yaml` and `deploy/compose/.env.example` | WP04 | |
| T012 | `.gitignore`: `.env`, `.env.*`, `!.env.example`, `deploy/compose/secrets/` | WP04 | |
| T013 | Deployable build: hand the repository root, the compose dir and `.gitignore` to the test JVM as inputs | WP04 | |
| T014 | `ShippedConfigurationTest`: local `application.yaml` pinned, profile-file keys, compose shape, env template, `git check-ignore`, no-secret scan with positive controls | WP04 | |
| T015 | `docker compose config` against the example, non-vacuity and `:deployable:check` | WP04 | |
| T016 | ADR-0041 decision 3: dated clarification note | WP05 | [P] |
| T017 | `docs/architecture/service-lifecycle.md`: accepted status, the container profile and its secrets | WP05 | [P] |
| T018 | `docs/guides/run-in-a-container.md` and the guides hub link | WP05 | [P] |
| T019 | Changelog entry and index line, with the follow-ups | WP05 | |
| T020 | Docs checks: `check_docs.py all`, pytest, cspell, markdownlint | WP05 | |

---

## Work Package WP01: Profile file and required secrets import (Priority: P1)

**Goal**: Under the `container` profile, the shipped application loads `application-container.yaml`. Its defaults are
8080 and `0.0.0.0`. It imports the secrets directory as a required config tree, so values bind from files and from env
vars, and a missing directory fails the start, naming it.
**Independent Test**: `./gradlew :deployable:test --tests '*ContainerSecretsTest' --tests '*ContainerPropertiesTest'`
passes, and `ContainerSecretsTest` is red without the profile file.
**Prompt**: `tasks/WP01-profile-file-and-secrets-import.md`
**Requirement Refs**: FR-001, FR-002, FR-003, FR-004, NFR-004, C-001, C-003

### Included Subtasks

T001 `ContainerSecretsTest` written first, red (WP01)
T002 `application-container.yaml` (WP01)
T003 `ContainerPropertiesTest` shipped starts adapted (WP01)
T004 Non-vacuity and module check (WP01)

### Implementation Notes

Test first. Use the `SpringApplicationBuilder` start of `CharterServiceApplication`, because `ApplicationContextRunner`
does no config-data processing. Simulate the environment with a `StandardEnvironment` subclass that overrides
`getSystemEnvironment()`.

### Dependencies

None.

### Risks

- A token in the builder's default properties would hide the token file. The helper is token-free.
- Every failure asserts the exception type and the path or key it names.

---

## Work Package WP02: Container-profile token messages (Priority: P2)

**Goal**: Under the container profile, the two token refusals name both `CHARTER_WORKTREE_TOKEN` and
`<secrets-dir>/charter.worktree-token`, and do not offer the development flag. The blank refusal says that an empty
env var hides the file. Without the profile, the messages are unchanged.
**Independent Test**: `./gradlew :deployable:test --tests '*GuardWiringTest'` passes, and the new tests are red on
`origin/main`'s `GuardWiring`.
**Prompt**: `tasks/WP02-container-token-messages.md`
**Requirement Refs**: FR-005, NFR-001, NFR-002, NFR-004

### Included Subtasks

T005 `GuardWiringTest` container cases, red first (WP02)
T006 `GuardWiring` profile-aware messages (WP02)
T007 Non-vacuity and module check (WP02)

### Dependencies

None. It can run in parallel with WP01 and WP03.

### Risks

- The messages must not echo the token.
- The local message text is pinned by existing tests; leave those tests unchanged.

---

## Work Package WP03: The config tree sets only its own keys (Priority: P1)

**Goal**: Under the container profile, a config-tree file that names a property outside `charter.worktree-token` and
`charter.container.*` fails the start, naming the file. So does a secrets directory that is blank, relative, the root
or writable by others, naming the directory.
**Independent Test**: `./gradlew :deployable:test --tests '*ContainerStartupCheckTest'` passes, and the new tests are
red without the change.
**Prompt**: `tasks/WP03-config-tree-allowlist.md`
**Requirement Refs**: FR-010, NFR-001, NFR-002, NFR-004

### Included Subtasks

T008 `ContainerStartupCheckTest` new cases, red first (WP03)
T009 `ContainerStartupCheck` allowlist and directory rules (WP03)
T010 Non-vacuity and module check (WP03)

### Dependencies

None. It can run in parallel with WP01 and WP02.

### Risks

- Find config-tree sources by type (`ConfigTreePropertySource`).
- Name files, never their content.
- Keep the complexity of each method at 15 or below.

---

## Work Package WP04: Compose example, env template, ignore rules and the shipped-files check (Priority: P1)

**Goal**: A committed compose example and `.env.example` that hold no secret; `.env` and the secrets directory ignored;
a test that pins the shipped files and catches a committed secret.
**Independent Test**: `./gradlew :deployable:test --tests '*ShippedConfigurationTest'` passes. Each positive control
fires. `docker compose config` accepts the example.
**Prompt**: `tasks/WP04-compose-example-and-shipped-files.md`
**Requirement Refs**: FR-006, FR-007, FR-008, FR-009, NFR-003

### Included Subtasks

T011 Compose example and env template (WP04)
T012 `.gitignore` rules (WP04)
T013 Test JVM inputs in the deployable build (WP04)
T014 `ShippedConfigurationTest` (WP04)
T015 `docker compose config`, non-vacuity and module check (WP04)

### Dependencies

WP01: the test reads `application-container.yaml`.

### Risks

- CI's change routing does not cover `deploy/`. The files are inputs of the deployable test task.
- The test needs `.git` and fails loudly without it.

---

## Work Package WP05: Living docs (Priority: P2)

**Goal**: Bring the docs current:

- ADR-0041 decision 3 clarification note;
- the service-lifecycle page, current;
- a compose how-to;
- a changelog entry naming the follow-ups.

**Independent Test**: `python3 scripts/docs/check_docs.py all`, the docs pytest suite and cspell pass.
**Prompt**: `tasks/WP05-living-docs.md`
**Requirement Refs**: SC-005, C-002

### Included Subtasks

T016 ADR-0041 note (WP05)
T017 Service lifecycle page (WP05)
T018 How-to and hub link (WP05)
T019 Changelog (WP05)
T020 Docs checks (WP05)

### Dependencies

WP01, WP02, WP03 and WP04: the docs describe what landed.

### Risks

- "feature" is a forbidden term.
- Descriptions must be 50 to 180 characters.
- `related:` slugs must resolve.
- Put `configtree` in code spans.
