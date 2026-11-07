---
work_package_id: WP04
title: Exposure start-up check and profile guard tests
dependencies: []
requirement_refs:
- FR-011
- FR-012
- FR-013
- FR-014
- FR-015
- C-002
planning_base_branch: feat/container-guard-and-actuator
merge_target_branch: feat/container-guard-and-actuator
branch_strategy: Planning artifacts for this mission were generated on feat/container-guard-and-actuator. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/container-guard-and-actuator unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-container-guard-and-actuator-01M6P99A
base_commit: b96e97ec1158beefa971c885e45e2e063053d88f
created_at: '2026-11-05T16:55:46.197732+00:00'
subtasks:
- T013
- T014
- T015
- T016
history: []
agent_profile: java-jenny
authoritative_surface: service/deployable/src/main/java/ai/speckitty/charter/deployable/ExposureStartupCheck.java
create_intent:
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ExposureStartupCheck.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ExposureStartupCheckTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ProfileDefaultsTest.java
execution_mode: code_change
model: ''
owned_files:
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ExposureStartupCheck.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ExposureStartupCheckTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ProfileDefaultsTest.java
role: implementer
tags: []
tracker_refs: []
---
# WP04: Exposure start-up check and profile guard tests

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Branch Strategy

Planning branch and final merge target: `feat/container-guard-and-actuator`. Execution worktrees are allocated per computed lane from
`lanes.json`; do not pick a base by hand.

Implementation command:
`spec-kitty agent action implement WP04 --agent claude --mission container-guard-and-actuator-01M6P99A`

## Objective

An always-on start-up check holds the rules on what the process exposes (FR-011 to FR-013, FR-016's logging
half). Shipped-configuration tests pin that the container profile is never a default and that a missing secret
fails the start (FR-014, FR-015). This closes #231.

## Subtasks

### T013 — Local loopback bind

The check is `ExposureStartupCheck`, a `@Configuration(proxyBeanMethods = false)` that `implements
InitializingBean`. It has a static `LazyInitializationExcludeFilter` bean, copying the pattern of
`ContainerStartupCheck`. Read the profile with `environment.matchesProfiles(ContainerProperties.PROFILE)`;
never use `@Profile` (`NoBuildTimeProfilesTest`).

Without the profile:
- `server.address` must be present and non-blank.
- `InetAddress.getByName(value).isLoopbackAddress()` must be true.
- Otherwise fail: `server.address must be a loopback address without the container profile: the local
  service never listens beyond this machine (ADR-0041 decision 4)`.
- An `UnknownHostException` is refused the same way, never echoing the value.

Under the container profile there is no bind rule (FR-013); port 0 stays with `ContainerStartupCheck`.

### T014 — Management and logging rules (both profiles unless stated)

- `management.server.port` and `management.server.address` must not be set: a management server runs outside
  the request guard.
- `management.endpoints.web.base-path`, when set, must equal `/actuator`.
- `management.endpoints.web.exposure.include`, when set, must equal `health` after trim and lower case. Unset is
  allowed: Boot's default is health.
- `management.endpoint.health.show-details` and `management.endpoint.health.show-components`, when set, must
  equal `never` (case-insensitive).
- `management.endpoint.env.show-values` and `management.endpoint.configprops.show-values`, when set, must equal
  `never`.
- Under container only: `spring.mvc.log-request-details` must not be `true`.

Each message names the property, never a value. This needs no actuator dependency: it reads the environment.

### T015 — `ExposureStartupCheckTest` (`ApplicationContextRunner`)

- **Local:**
  - `127.0.0.1`, `127.0.0.2`, `::1`, `[::1]`? (check what `InetAddress` accepts; drop it if unsupported) and
    `localhost` start;
  - `0.0.0.0`, `::`, blank, absent, `192.0.2.10` and `::ffff:10.0.0.1` fail, naming `server.address`.
- **Container:** `0.0.0.0`, absent and `127.0.0.1` start.
- **Management:** each rule fails under both profiles, with a positive control; `health` and `Health ` are
  allowed.
- **Container logging:** `spring.mvc.log-request-details=true` fails under container and starts locally.
- **Lazy initialization:** does not skip the check.

### T016 — `ProfileDefaultsTest` (shipped configuration)

- **FR-014:** the shipped configuration has no `spring.profiles.default`, `spring.profiles.active` or
  `spring.profiles.include`. Load `application.yaml` and `application-container.yaml` with the SnakeYAML engine
  (test dependency) and assert the keys are absent. Also start the real application with no profile, as
  `ContainerPropertiesTest`'s "as it ships" tests do, and assert
  `environment.matchesProfiles("container")` is false.
- **FR-015:**
  - Start the real application under `container` with an empty `@TempDir` secrets directory
    (`charter.container.secrets-dir`), allowed hosts and an existing config root, and no token.
  - The start fails with a message naming `CHARTER_WORKTREE_TOKEN` and `<dir>/charter.worktree-token`.
  - Positive control: the same start with the token file present succeeds.
  - Reuse helpers from `ContainerSecretsTest` where you can; do not edit that file (WP05 owns it).

## Definition of Done

`./gradlew :deployable:test --tests '*ExposureStartupCheckTest' --tests '*ProfileDefaultsTest'` passes, and the
whole deployable suite stays green (the local tests bind `127.0.0.1`).

## Risks

Some existing tests may set `server.address`. Run the full deployable suite.
