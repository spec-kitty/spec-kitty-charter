---
work_package_id: WP03
title: The config tree sets only its own keys
dependencies: []
requirement_refs:
- FR-010
- NFR-001
- NFR-002
- NFR-004
planning_base_branch: issue-229-hosted-config-secrets
merge_target_branch: issue-229-hosted-config-secrets
branch_strategy: Planning artifacts for this mission were generated on issue-229-hosted-config-secrets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-229-hosted-config-secrets unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-container-profile-secrets-01M65MC5
base_commit: cf0f99db558ab0cbb0c82ce11c17db83b41be78f
created_at: '2026-10-30T06:14:14.385806+00:00'
subtasks:
- T008
- T009
- T010
phase: Phase 1 - Implementation
history:
- at: '2026-10-30T07:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/deployable/src/main/java/ai/speckitty/charter/deployable/ContainerStartupCheck.java
create_intent: []
execution_mode: code_change
model: ''
owned_files:
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ContainerStartupCheck.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ContainerStartupCheckTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP03 – The config tree sets only its own keys

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

The plan-point security lens found that any file in the secrets directory sets any property (research R10, rows 3 and
7). A shared or misplaced mount could set:

- `server.address`;
- `charter.config-root`;
- `logging.*`;
- `spring.config.import`.

Under the `container` profile, `ContainerStartupCheck` gains two rules:

1. **Config-tree keys.**
   - Every property name of every `ConfigTreePropertySource` in the environment must be `charter.worktree-token` or
     start with `charter.container.`. Otherwise the start fails with a message that names the file (the property name)
     and the directory, and never the content.
   - An empty directory passes.
   - Several offending files are reported together, in sorted order.
2. **Secrets directory.** `charter.container.secrets-dir`, defaulting to `/run/secrets`, must:
   - be non-blank;
   - be absolute;
   - not be the filesystem root;
   - not be writable by others, when the filesystem reports POSIX permissions.

   A breach fails the start, naming the property and the directory. A path is not a secret, so naming it is fine.

Without the profile, nothing here runs (the class is `@Profile("container")`).

This allowlist is narrower than ADR-0041 decision 3's text. It is logged as an open review item for the operator (R10
row 3). Implement it as specified.

## Context & Constraints

- `ContainerStartupCheck` (`service/deployable/.../ContainerStartupCheck.java`) is an `InitializingBean` reading an
  `Environment`. Change its constructor parameter to `ConfigurableEnvironment` if you need the property sources, and
  check that Spring still injects it (it does for `ConfigurableEnvironment`).
- `ConfigTreePropertySource` is `org.springframework.boot.env.ConfigTreePropertySource`, an
  `EnumerablePropertySource<Path>`.
  - `getSource()` is the directory.
  - **Never** call `getProperty(...)` on it in a message: its value's `toString()` is the secret.
- The secrets directory check runs after the import (research R10 row 7, residual). A `configtree:/` import is read
  before the check refuses it. That is accepted and documented in WP05.
- `ContainerStartupCheckTest` uses `ApplicationContextRunner(ContainerStartupCheck)` with a `container()` helper. Add
  config-tree sources through `withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addLast(new
  ConfigTreePropertySource("Config tree test", dir)))`.
- Keep complexity at 15 or below per method.
- `-Xlint:all -Werror`, Error Prone and NullAway apply to main code.

## Subtasks & Detailed Guidance

### Subtask T008 – `ContainerStartupCheckTest` new cases (red first)

Add these tests, each with a valid base (fixed port, existing root) from the existing helper:

1. A config tree holding `charter.worktree-token` and `charter.container.allowed-hosts` passes (positive control).
2. A config tree holding `server.address` fails. The message contains `server.address` and the directory, and does not
   contain the file's content. Plant a distinctive content, such as `planted-value-0123456789`, and assert it is absent.
3. A config tree holding `logging.level.root` and `spring.config.import` fails, naming both.
4. An empty config tree passes.
5. `charter.container.secrets-dir=` (blank), `relative/dir` and `/` each fail, naming `charter.container.secrets-dir`.
   Use one `@ParameterizedTest` if that reads cleanly.
6. A secrets directory with `OTHERS_WRITE` set (`Files.setPosixFilePermissions` on a `@TempDir` subdirectory) fails,
   naming the directory. Guard with `assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))`:
   that is a platform capability assumption, not a quarantine.
7. The secrets dir unset, so the `/run/secrets` default applies: the directory rule does not fail on the default path
   even when it does not exist. The import itself refuses a missing directory (WP01). The check here is only about
   writability when the directory exists.
8. Without the profile, a config tree holding `server.address` does not fail the start. Use an
   `ApplicationContextRunner` without the profile; the class is not loaded.

Run before T009. Cases 2, 3, 5 and 6 fail. Record the red run.

### Subtask T009 – The rules in `ContainerStartupCheck`

Suggested shape:

```java
static final String SECRETS_DIR_PROPERTY = "charter.container.secrets-dir";
static final String TOKEN_PROPERTY = "charter.worktree-token"; // or reuse GuardWiring.TOKEN_PROPERTY
private static final String CONTAINER_PREFIX = "charter.container.";

private static void requireOwnKeysInConfigTrees(ConfigurableEnvironment environment) {
    for (PropertySource<?> source : environment.getPropertySources()) {
        if (source instanceof ConfigTreePropertySource tree) {
            List<String> foreign = Arrays.stream(tree.getPropertyNames())
                    .filter(name -> !TOKEN_PROPERTY.equals(name) && !name.startsWith(CONTAINER_PREFIX))
                    .sorted().toList();
            if (!foreign.isEmpty()) {
                throw new IllegalStateException("the secrets directory " + tree.getSource() + " may set only "
                        + TOKEN_PROPERTY + " and " + CONTAINER_PREFIX + "* under the container profile; remove "
                        + String.join(", ", foreign));
            }
        }
    }
}
```

`requireSafeSecretsDir(environment)`:

- read the property with the default `/run/secrets`;
- blank, or `!Path.of(v).isAbsolute()`, or `path.getParent() == null` (the root): fail;
- if `Files.isDirectory(path)` and POSIX permissions are supported and contain `OTHERS_WRITE`: fail;
- catch `InvalidPathException` and fail with the property named.

Call both from `afterPropertiesSet` after the existing rules. Update the class javadoc to list the two new rules, with
ADR-0041 decision 3 and research R10.

### Subtask T010 – Non-vacuity and module check

- Remove the two calls locally and confirm that the failing cases are red. Restore them.
- Run `./gradlew :deployable:spotlessApply :deployable:check`.

## Definition of Done

- [ ] The new cases pass, and were red without the rules.
- [ ] The existing `ContainerStartupCheckTest` cases are unchanged and pass.
- [ ] No message holds a config-tree file's content.
- [ ] `:deployable:check` is green, with no new suppressions.

## Risks & Reviewer Guidance

- Reviewer: confirm the property name in the message comes from `getPropertyNames()`, never from a value.
- Reviewer: confirm the allowlist is exact. `charter.container` without the dot must not pass.
- Reviewer: confirm the POSIX assumption is a capability guard only.

## Implementation command

```bash
spec-kitty agent action implement WP03 --agent claude --mission container-profile-secrets-01M65MC5
```
