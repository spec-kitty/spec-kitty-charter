---
work_package_id: WP05
title: Actuator health, end to end
dependencies:
- WP03
- WP04
requirement_refs:
- FR-008
- FR-016
- FR-017
- NFR-001
- NFR-002
- NFR-003
planning_base_branch: feat/container-guard-and-actuator
merge_target_branch: feat/container-guard-and-actuator
branch_strategy: Planning artifacts for this mission were generated on feat/container-guard-and-actuator. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/container-guard-and-actuator unless the human explicitly redirects the landing branch.
subtasks:
- T017
- T018
- T019
- T020
history: []
agent_profile: java-jenny
authoritative_surface: service/deployable/
create_intent:
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ContainerHealthTest.java
execution_mode: code_change
model: ''
owned_files:
- service/deployable/build.gradle.kts
- gradle/libs.versions.toml
- gradle/verification-metadata.xml
- service/deployable/src/main/resources/application.yaml
- service/deployable/src/main/resources/application-container.yaml
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ContainerHealthTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ContainerSecretsTest.java
role: implementer
tags: []
tracker_refs: []
---
# WP05: Actuator health, end to end

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
`spec-kitty agent action implement WP05 --agent claude --mission container-guard-and-actuator-01M6P99A`

## Objective

Add actuator with `health` as its only HTTP endpoint (FR-008). Under the container profile the guard lets
exactly `GET /actuator/health` through without a token. Prove the whole surface end to end on a real port under
both profiles (FR-009 to FR-011, FR-016, FR-017, NFR-001 to NFR-003). This closes #232.

## Subtasks

### T017 — Dependency (DIRECTIVE_051)

- In `gradle/libs.versions.toml`, add `spring-boot-starter-actuator = { module = "org.springframework.boot:spring-boot-starter-actuator" }`
  beside the validation starter.
- In `service/deployable/build.gradle.kts`, add `implementation(catalog.lib("spring-boot-starter-actuator"))`.
- Run `./gradlew --write-verification-metadata sha256 :deployable:compileJava` (or `verifyAll`, per
  `docs/guides/service-build.md` "Change a dependency"), with the Maven Central mirror init script in place.
- Review the `gradle/verification-metadata.xml` diff: only new `component` entries with SHA-256, no
  `trusted-artifacts` change and no removals.

### T018 — Configuration

`application.yaml` (both profiles):

```yaml
spring:
  jmx:
    enabled: false
management:
  endpoints:
    access:
      default: none
    web:
      exposure:
        include: health
      discovery:
        enabled: false
    jmx:
      exposure:
        exclude: "*"
  endpoint:
    health:
      access: read-only
      show-details: never
      show-components: never
    env:
      show-values: never
    configprops:
      show-values: never
```

Merge into the existing `spring:` block; do not duplicate the key. Comment each with ADR-0041 D7.

`application-container.yaml`: `server.forward-headers-strategy: none`, commented "until a proxy is decided;
`X-Forwarded-Host` never changes the judged name".

### T019 — `ContainerHealthTest` (new, real server on a random port)

- **Container profile.** Start the real application with:
  - a temp config root;
  - a temp secrets dir holding the token;
  - `charter.container.allowed-hosts=localhost`;
  - `server.port=0`. `ContainerStartupCheck` refuses port 0, so either pick a free port first
    (`ServerSocket(0)`) or bind loopback with a fixed free port.

  Use `java.net.http.HttpClient` with an explicit `Host` header where needed: the JDK client restricts `Host`,
  so use a raw `Socket` for the custom-Host cases.
- **Container scenarios:**
  - `GET /actuator/health`, no token: 200, body exactly `{"status":"UP"}`, content type
    `application/vnd.spring-boot.actuator.v3+json` or JSON.
  - With `Host: attacker.example`: 403.
  - With `Origin`: 403.
  - HEAD, a trailing slash, and `/actuator/health/liveness` without a token: 401.
  - `/actuator/{env,configprops,beans,loggers,heapdump,threaddump,mappings}` with the token: 404.
  - `/actuator` with the token: 404 (discovery off).
  - `X-Forwarded-Host: attacker.example` with `Host: localhost` and `spring.main.cloud-platform=kubernetes`:
    200 on health, the same outcome.
- **Local profile:** `GET /actuator/health` without a token is 401; with the token it is 200
  `{"status":"UP"}`; `/actuator/env` with the token is 404.
- The context holds no `JmxEndpointExporter` bean.
- A widening env property fails the start, e.g. `management.endpoints.web.exposure.include=*`. One case is
  enough here; WP04 covers the matrix.

### T020 — Secrets never logged (FR-017)

Extend `ContainerSecretsTest` (or `ContainerHealthTest` with `OutputCaptureExtension`). Run a container start
with:
- `debug=true` and `logging.level.root=TRACE` (or DEBUG if TRACE is too slow);
- the token from a config-tree file;
- health requests and a 401.

Assert that the token text never appears in the captured output, using the existing `assertNoToken` helper.

## Definition of Done

- `./gradlew :deployable:test` passes, and `verifyAll` passes on Java 25.
- The verification metadata is updated.
- The native job is watched in CI (NFR-002).

## Risks

- A test that enumerates the served paths, or the OpenAPI document, may flag the actuator path. Carve it out
  with a comment citing ADR-0041 D7.
- On Boot 4, the module names may differ (`spring-boot-health`). Rely on the starter.
