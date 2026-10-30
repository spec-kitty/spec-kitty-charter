---
work_package_id: WP01
title: Profile file and required secrets import
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- NFR-004
planning_base_branch: issue-229-hosted-config-secrets
merge_target_branch: issue-229-hosted-config-secrets
branch_strategy: Planning artifacts for this mission were generated on issue-229-hosted-config-secrets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-229-hosted-config-secrets unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-container-profile-secrets-01M65MC5
base_commit: d1d684c9e7af68cdc8c9b3af2426560471905f16
created_at: '2026-10-30T06:05:05.030466+00:00'
subtasks:
- T001
- T002
- T003
- T004
phase: Phase 1 - Implementation
history:
- at: '2026-10-30T07:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/deployable/src/main/resources/application-container.yaml
create_intent:
- service/deployable/src/main/resources/application-container.yaml
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ContainerSecretsTest.java
execution_mode: code_change
model: ''
owned_files:
- service/deployable/src/main/resources/application-container.yaml
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ContainerSecretsTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ContainerPropertiesTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 – Profile file and required secrets import

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter (or any user-defined
profile), and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Review Feedback

*[If this WP was returned from review, the reviewer feedback reference appears in the status event log.]*

---

## Objectives & Success Criteria

ADR-0041 decision 3: under the `container` profile, secrets come only from mounted files or env vars, and the import
of the mounted directory is required. The operator chose a placeholder for the directory (decision
`01M65MGKX68G0EGHV2AETWWKNX`):

```yaml
spring:
  config:
    import: configtree:${charter.container.secrets-dir:/run/secrets}/
```

Done means:

- Under the profile, the shipped `CharterServiceApplication`:
  - defaults to `server.port` 8080 and `server.address` `0.0.0.0`;
  - binds `charter.worktree-token` and `charter.container.allowed-hosts` from files in the secrets directory, and
    separately from env vars;
  - fails the start with `ConfigDataResourceNotFoundException` naming a missing directory.
- A file named in env-var style binds nothing.
- An empty `CHARTER_WORKTREE_TOKEN` hides a token file, and the start fails as blank. That message is WP02's;
  here only the failure is pinned.
- No test output or message holds the test token (NFR-004).
- Without the profile nothing changes.

## Context & Constraints

- Spec: User Stories 1, 2 and 4 and the edge cases. Research: R3, R5 and R10, rows 1, 5, 6 and 10.
- `ApplicationContextRunner` does **no** config-data processing. Use
  `new SpringApplicationBuilder(CharterServiceApplication.class).web(WebApplicationType.NONE).profiles("container")`,
  as `ContainerPropertiesTest.shippedService` does.
- Builder `.properties(...)` are *default properties*. They rank below `application.yaml` but are visible to the import
  placeholder. Pass `charter.container.secrets-dir`, the port and the root as command-line arguments
  (`--key=value`), which outrank both. **Never** pass the token on the command line: `GuardWiring` refuses it.
- To simulate env vars, pass `.environment(env)`, where `env` is a `StandardEnvironment` subclass overriding
  `getSystemEnvironment()` to return a fixed map. Boot then wraps it as the real `systemEnvironment` source, so
  relaxed names (`CHARTER_WORKTREE_TOKEN`, `CHARTER_CONTAINER_ALLOWEDHOSTS`) and the placeholder's
  `CHARTER_CONTAINER_SECRETS_DIR` are exercised exactly as in a container. Check that
  `SpringApplicationBuilder.environment(ConfigurableEnvironment)` exists in Boot 4.1.1. If it doesn't, use
  `SpringApplication#setEnvironment`.
- The minimum for a container start (from #228):
  - a non-empty `charter.container.allowed-hosts`;
  - a port other than 0;
  - an existing absolute `charter.config-root`;
  - a token of 32 or more characters from an accepted source;
  - neither `charter.insecure-no-token` nor `charter.project-config-dir`.
- Test token: a 36-character hex string, as the other tests use. Treat it as if it were a secret: never print it.

## Subtasks & Detailed Guidance

### Subtask T001 – `ContainerSecretsTest` (write first, run red)

**Purpose**: prove FR-001 to FR-004 against the shipped files.

**Steps**: create `service/deployable/src/test/java/ai/speckitty/charter/deployable/ContainerSecretsTest.java`. Javadoc
it in the style of the neighbouring tests. Add helpers:

- `container(Map<String,String> env)` returns a builder with `.profiles(ContainerProperties.PROFILE)`, `web(NONE)` and
  the custom environment. It sets no token or hosts.
- `arguments(Path secrets, Path root, String... more)` returns `--charter.container.secrets-dir=<secrets>`,
  `--configRootLocation=<root>`, plus `more`. Do not pass a port: the profile default must be what makes it start.
- `guardStatus(context, String bearer)`: get the `FilterRegistrationBean<?>` named `worktreeGuard`, run its filter on a
  `MockHttpServletRequest("GET", "/api/v1/info")` with `Host: localhost` and the bearer, and return the status. A
  passing request reaches the `MockFilterChain`: the status stays 200 and `chain.getRequest()` is non-null. Model it
  on `GuardWiringTest.statusOf`.

Test cases. Each test's name states its outcome.

1. **Files bind.** The secrets dir has two files:
   - `charter.worktree-token`, holding the token plus `"\n"`;
   - `charter.container.allowed-hosts`, holding `charter.example.org,localhost`.

   Assert that the context starts and `ContainerProperties.allowedHosts()` is `[charter.example.org, localhost]`. Assert
   that the guard passes the file's token (no trailing newline) and answers 401 to a different 36-character token.
2. **Env vars bind.** Empty secrets dir. The env map has `CHARTER_WORKTREE_TOKEN` and `CHARTER_CONTAINER_ALLOWEDHOSTS`.
   Assert the same outcomes.
3. **The secrets dir comes from the environment.** The env map has `CHARTER_CONTAINER_SECRETS_DIR=<dir>`, the dir holds
   the files, and no `--charter.container.secrets-dir` argument is passed. Assert that it starts and the hosts are bound.
4. **Missing dir.** `--charter.container.secrets-dir=<tmp>/absent`. Assert that `run` throws
   `ConfigDataResourceNotFoundException`, or one with it as a cause (`hasRootCauseInstanceOf` or a chain search), and
   that the message contains the absent path.
5. **Env-style file name binds nothing.** The dir has a file named `CHARTER_WORKTREE_TOKEN` and the hosts file. Assert
   that the start fails with a stack trace containing `charter.worktree-token` and `is required`.
6. **Empty env var hides the file.** The dir has the token file, and the env has `CHARTER_WORKTREE_TOKEN=""`. Assert
   that the start fails with `is blank`. WP02 adds the hint; assert only `is blank` here, so this test is independent
   of WP02.
7. **Profile defaults.** A valid start with no port argument. Assert that `environment.getProperty("server.port")` is
   `8080` and `server.address` is `0.0.0.0`.
8. **No token in output** (NFR-004). Use `@ExtendWith(OutputCaptureExtension.class)` on cases 5 and 6, and on one start
   that fails on a short token planted as a file. Assert that the captured output and the exception's full stack text
   do not contain the token value.
   - Positive control: a helper that asserts "does not contain" must fail on a string that does contain the token.
     Write it as a one-line `assertThatThrownBy` around the helper, inside the same test class.

**Red first**: run before T002. Cases 1 to 4 and 7 fail, because without the profile file nothing imports and the port
is 0. Record the red run (command and failing count) in the WP's activity log.

### Subtask T002 – `application-container.yaml`

**Purpose**: FR-001 and FR-002.

Create `service/deployable/src/main/resources/application-container.yaml`. Match the comment style of
`application.yaml`.

```yaml
# The container profile (ADR-0041): active only when SPRING_PROFILES_ACTIVE=container, never by default. Non-secret
# defaults only: no token, no allowed hosts, no configuration root (the image's mount path is #234's).
spring:
  config:
    # Required, no optional: prefix (ADR-0041 decision 3): a container started without its secrets directory fails the
    # start, naming it. One file per property, named in dot form (/run/secrets/charter.worktree-token); an env var
    # (CHARTER_WORKTREE_TOKEN) is the alternative and outranks the file. An empty directory is valid. Override the
    # directory with charter.container.secrets-dir (env CHARTER_CONTAINER_SECRETS_DIR): an absolute path, no slash at
    # the end.
    import: configtree:${charter.container.secrets-dir:/run/secrets}/
server:
  # All interfaces on a fixed port, so the port can be published (ADR-0041 decision 4).
  address: 0.0.0.0
  port: 8080
```

Do not add `charter.*` keys, `spring.profiles.*` or actuator settings.

### Subtask T003 – Adapt `ContainerPropertiesTest`

- The three `shippedService` tests now load the profile file, so they need a secrets directory. Add
  `--charter.container.secrets-dir=<@TempDir>` to `startArguments` and to the port test's arguments.
- `theServiceAsItShipsRefusesItsLocalRandomPortUnderTheContainerProfile` relies on the inherited port 0, which no longer
  holds. Rename it to describe the outcome (an explicit port 0 is still refused under the container profile) and pass
  `--server.port=0`. Do not delete it.
- The builder token in `shippedService` stays: those tests are about the record, not the token. Keep the edits minimal.

### Subtask T004 – Non-vacuity and module check

- Temporarily move `application-container.yaml` aside and confirm that cases 1 to 4 and 7 fail. Restore it. Note this in
  the activity log.
- Run `./gradlew :deployable:spotlessApply :deployable:check`. It must have zero warnings and no new suppressions.

## Definition of Done

- [ ] `ContainerSecretsTest` passes, and was red before T002.
- [ ] `ContainerPropertiesTest` passes, with the port-0 refusal now explicit.
- [ ] `:deployable:check` is green.
- [ ] `application.yaml` is untouched.

## Risks & Reviewer Guidance

- Reviewer: confirm the helper never sets a token in the builder.
- Reviewer: confirm the env simulation goes through `getSystemEnvironment()`, not a `MapPropertySource`.
- Reviewer: confirm the missing-dir test asserts the exception type and the path.
- A secret given both as a file and as an env var takes the env var. This is Spring's order and is documented in WP05.

## Implementation command

```bash
spec-kitty agent action implement WP01 --agent claude --mission container-profile-secrets-01M65MC5
```
