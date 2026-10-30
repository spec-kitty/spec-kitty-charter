---
work_package_id: WP04
title: Compose example, env template, ignore rules and the shipped-files check
dependencies:
- WP01
requirement_refs:
- FR-006
- FR-007
- FR-008
- FR-009
- NFR-003
planning_base_branch: issue-229-hosted-config-secrets
merge_target_branch: issue-229-hosted-config-secrets
branch_strategy: Planning artifacts for this mission were generated on issue-229-hosted-config-secrets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-229-hosted-config-secrets unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-container-profile-secrets-01M65MC5
base_commit: e13a31827ddbc09d6e22f986e2c7c081006a68c6
created_at: '2026-10-30T06:18:28.327339+00:00'
subtasks:
- T011
- T012
- T013
- T014
- T015
phase: Phase 1 - Implementation
history:
- at: '2026-10-30T07:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: deploy/compose/
create_intent:
- deploy/compose/compose.yaml
- deploy/compose/.env.example
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ShippedConfigurationTest.java
execution_mode: code_change
model: ''
owned_files:
- deploy/compose/compose.yaml
- deploy/compose/.env.example
- .gitignore
- service/deployable/build.gradle.kts
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ShippedConfigurationTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP04 – Compose example, env template, ignore rules and the shipped-files check

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

Issue #229 scope: a docker compose example that uses an env file; only `.env.example` committed, holding placeholders;
`.env` gitignored; the local `application.yaml` unchanged and asserted. Acceptance: no secret value is committed. The
plan-point squad made the check concrete (research R10, rows 1, 2, 4 and 12).

Done means:

- `deploy/compose/compose.yaml`:
  - runs the image `${CHARTER_IMAGE:?…}` under `SPRING_PROFILES_ACTIVE=container`;
  - reads `env_file: .env`;
  - bind-mounts `./secrets` at `/run/secrets` read-only, with `create_host_path: false`;
  - mounts a named volume for the configuration root and sets `CHARTER_CONFIGROOT` to it;
  - publishes 8080.

  `docker compose config` accepts it.
- `deploy/compose/.env.example`:
  - `CHARTER_IMAGE=` is the only live key, with an empty value;
  - `CHARTER_WORKTREE_TOKEN` and `CHARTER_CONTAINER_ALLOWEDHOSTS` appear only as comments, with the file form preferred;
  - `CHARTER_CONTAINER_SECRETS_DIR` does not appear.
- `.gitignore` ignores:
  - `.env` and `.env.*` everywhere, except `.env.example`;
  - `deploy/compose/secrets/`.
- `ShippedConfigurationTest` pins all of the above, scans for committed secrets with positive controls, and pins the
  local `application.yaml`.

## Context & Constraints

- **Where the test reads files from.** Deployable tests run with the module directory as their working directory. Pass
  paths explicitly, as the build already does for the contract bundle (`ContractBundleArguments` in
  `service/deployable/build.gradle.kts`): add a `CommandLineArgumentProvider` with `@InputFiles` over
  `deploy/compose/compose.yaml`, `deploy/compose/.env.example` and `.gitignore`. It emits
  `-Dcharter.test.repo-root=<root>`. The task then reruns when they change, which matters because CI's change routing
  does not cover `deploy/` (research R7).
- **Configuration cache.** Use `rootProject.layout.projectDirectory` inside the provider's configuration, as the
  existing code does with `contracts`.
- **YAML parsing.** Use `snakeyaml-engine` (`org.snakeyaml.engine.v2.api.Load` with `LoadSettings.builder().build()`),
  already a test dependency. No new dependency (NFR-003).
- **`git check-ignore`.** Run it with `ProcessBuilder("git", "-C", root, "check-ignore", "-q", "--no-index", path)`.
  Exit 0 means ignored and 1 means not ignored. Fail loudly (`IllegalStateException`) when `<root>/.git` is absent:
  never skip.
- **`git ls-files`.** Use the same `ProcessBuilder` approach for the tracked-file scan.

## Subtasks & Detailed Guidance

### Subtask T011 – Compose example and env template

`deploy/compose/compose.yaml`:

```yaml
# An example: the charter service in a container under the container profile (ADR-0041). Copy .env.example to .env,
# put each secret in ./secrets as a file named after its property (./secrets/charter.worktree-token), then
# `docker compose up`. No image is published yet (#234): set CHARTER_IMAGE to one you built.
name: charter
services:
  charter:
    image: ${CHARTER_IMAGE:?set CHARTER_IMAGE in .env to a charter service image}
    env_file: .env
    environment:
      SPRING_PROFILES_ACTIVE: container
      # Local to this example until the image fixes its mount path (#234).
      CHARTER_CONFIGROOT: /var/lib/spec-kitty/charter
    ports:
      - "8080:8080"
    volumes:
      # Required: the service does not start without it. Not created when missing, so a forgotten directory fails.
      - type: bind
        source: ./secrets
        target: /run/secrets
        read_only: true
        bind:
          create_host_path: false
      - type: volume
        source: charter-config
        target: /var/lib/spec-kitty/charter
volumes:
  charter-config:
```

`deploy/compose/.env.example`:

```dotenv
# Copy to .env (ignored by git) and fill in. Never commit .env.
# The image to run; no image is published yet (#234).
CHARTER_IMAGE=
# Secrets: prefer files in ./secrets, named after the property (./secrets/charter.worktree-token). An env var outranks
# the file, and an empty one hides it, so uncomment a line below only to use the env var instead.
# CHARTER_WORKTREE_TOKEN=
# The host names a request may carry (comma-separated); or the file ./secrets/charter.container.allowed-hosts.
# CHARTER_CONTAINER_ALLOWEDHOSTS=
```

The guard still checks for a loopback `Host` until it reads `allowed-hosts` (a follow-up). Requests to
`localhost:8080` pass; real host names get a 403. WP05 documents this.

### Subtask T012 – `.gitignore`

Add a commented section **outside** the Spec Kitty auto-managed block at the top, near `# Gradle`:

```gitignore
# Local env files and mounted secrets (ADR-0041 decision 3): only the template is committed.
.env
.env.*
!.env.example
deploy/compose/secrets/
```

### Subtask T013 – Test JVM inputs

Add to `service/deployable/build.gradle.kts`:

```kotlin
/** Hands the repository root to the tests that check the shipped configuration files, with those files as inputs. */
abstract class ShippedConfigurationArguments : CommandLineArgumentProvider {
    @get:Internal abstract val root: DirectoryProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val files: ConfigurableFileCollection
    override fun asArguments() = listOf("-Dcharter.test.repo-root=${root.get().asFile.absolutePath}")
}
tasks.test {
    jvmArgumentProviders.add(objects.newInstance<ShippedConfigurationArguments>().apply {
        root.set(rootProject.layout.projectDirectory)
        files.from(rootProject.layout.projectDirectory.files("deploy/compose/compose.yaml", "deploy/compose/.env.example", ".gitignore"))
    })
}
```

Verify that `./gradlew :deployable:test --configuration-cache` still works.

### Subtask T014 – `ShippedConfigurationTest`

Create `service/deployable/src/test/java/ai/speckitty/charter/deployable/ShippedConfigurationTest.java`. Read
`application*.yaml` from `<root>/service/deployable/src/main/resources/` by listing the directory, not from a fixed list.

Tests:

1. **The local file is unchanged.** `application.yaml` has `server.address` `127.0.0.1` and `server.port` `0`, and no
   `spring.config.import` or `spring.profiles` key.
2. **The profile file holds only its defaults.** The flattened keys of `application-container.yaml` are exactly
   `{spring.config.import, server.address, server.port}`. The import equals
   `configtree:${charter.container.secrets-dir:/run/secrets}/`; it does not start with `optional:`. The address is
   `0.0.0.0` and the port is `8080`.
3. **The compose shape.** The `charter` service has:
   - an image starting with `${CHARTER_IMAGE:?`;
   - `env_file` including `.env`;
   - `environment.SPRING_PROFILES_ACTIVE` equal to `container`;
   - a bind volume with target `/run/secrets`, `read_only: true` and `bind.create_host_path: false`.
4. **The env template.** Its live keys (lines without `#`, split on the first `=`) are exactly `{CHARTER_IMAGE}`, each
   with an empty value. No live key's normalized name is the token's or `charter.container.secrets-dir`'s.
5. **Ignored files.** `git check-ignore` says `deploy/compose/.env`, `deploy/compose/.env.local`, `.env` and
   `deploy/compose/secrets/charter.worktree-token` are ignored, and `deploy/compose/.env.example` is not.
6. **No secret committed.** Write a small checker, `secretFindings(Map<String, String> flattened)`, returning
   `List<String>` of keys only, never values. It flags:
   - a key whose normalized name (lowercase, non-alphanumerics removed) is `charterworktreetoken`, with a non-empty
     value that is not exactly a `${…}` reference;
   - any value matching `[A-Za-z0-9+/=_-]{32,}` that is not a `${…}` reference.

   Feed it:
   - every flattened `application*.yaml`;
   - the compose file, with each service's `environment` flattened from both map and list (`KEY=value`) forms;
   - the env template.

   Assert that there are no findings. Separately, assert from `git ls-files` that no tracked path matches `.env*`
   other than `.env.example`, and none lies under a `secrets/` directory.
7. **Positive controls for 6.** Feed the checker planted inputs and assert that each is flagged:
   - nested YAML `charter: {worktree-token: abcdef…}`;
   - the compose list form `- CHARTER_WORKTREE_TOKEN=abc`;
   - `export CHARTER_WORKTREE_TOKEN=abc` (the env parser strips `export `);
   - a 40-character hex value under an innocent key.

   Assert that `KEY=` and `${CHARTER_WORKTREE_TOKEN}` pass. For the tracked-file rule, feed the path filter a planted
   list (`deploy/compose/.env`, `x/secrets/y`) and assert that both are flagged.

### Subtask T015 – Compose validation, non-vacuity and module check

- Run `CHARTER_IMAGE=example.invalid/charter:dev docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env.example config`.
  Docker is available in the session. Do **not** run it with a real `.env`. Record the result. If compose refuses
  `create_host_path` (older engines), report it rather than dropping it.
- Non-vacuity: plant `CHARTER_WORKTREE_TOKEN=0123…` (live) in a scratch copy of `.env.example`, point the checker at it
  from a scratch test run, see it red, then discard. Remove `.env` from `.gitignore` locally and see test 5 red, then
  restore it.
- Run `./gradlew :deployable:spotlessApply :deployable:check`.

## Definition of Done

- [ ] The files are committed as specified, and `docker compose config` passes.
- [ ] `ShippedConfigurationTest` passes, and each positive control fires.
- [ ] `:deployable:check` is green, with no new dependency and no suppressions.

## Risks & Reviewer Guidance

- Reviewer: run `git check-ignore -v deploy/compose/.env` yourself.
- Reviewer: confirm the checker never prints values (its findings are keys).
- Reviewer: confirm the build provider declares the files as inputs.

## Implementation command

```bash
spec-kitty agent action implement WP04 --agent claude --mission container-profile-secrets-01M65MC5
```
