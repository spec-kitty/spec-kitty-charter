---
work_package_id: WP03
title: Native build and runtime hints
dependencies:
- WP02
requirement_refs:
- FR-006
- FR-009
- NFR-003
planning_base_branch: issue-241-71-service-release-native
merge_target_branch: issue-241-71-service-release-native
branch_strategy: Planning artifacts for this mission were generated on issue-241-71-service-release-native. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-241-71-service-release-native unless the human explicitly redirects the landing branch.
subtasks:
- T009
- T010
- T011
- T012
- T013
history: []
agent_profile: java-jenny
authoritative_surface: service/deployable/
create_intent:
- service/deployable/src/main/java/ai/speckitty/charter/deployable/CharterRuntimeHints.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/CharterRuntimeHintsTest.java
execution_mode: code_change
model: ''
owned_files:
- gradle/libs.versions.toml
- gradle/verification-metadata.xml
- build-logic/build.gradle.kts
- service/deployable/build.gradle.kts
- service/deployable/src/main/java/ai/speckitty/charter/deployable/CharterServiceApplication.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/CharterRuntimeHints.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/CharterRuntimeHintsTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP03: Native build and runtime hints

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Make `:deployable:nativeCompile` produce `spec-kitty-charter-service[.exe]` from the same sources and
version as the jar (FR-006). Register every runtime hint the research found (N2, N3, N5, N8) and test each
one. Keep `verifyAll` green on Temurin 25, which has no GraalVM, with every new artifact verified (NFR-003).

Implementation command:
`spec-kitty agent action implement WP03 --agent claude --mission service-release-native-01M693YF`

## Context

- Spring Boot 4.1.1 manages native-build-tools `1.1.8` (`native-build-tools-plugin.version` in its BOM).
- When `org.graalvm.buildtools.native` is applied beside `org.springframework.boot`, Boot also does these:
  - registers `processAot` and `processTestAot`;
  - adds the AOT output to `bootJar`;
  - turns off toolchain detection.
- native-image is not installed locally, so `nativeCompile` runs only in CI (WP04).
  `processAot` and `bootJar` do run locally and must pass.
- Generated AOT sources pass through the `JavaCompile` conventions (Error Prone and NullAway, in
  `build-logic/src/main/kotlin/charter.java-base.gradle.kts`). See how generated openapi sources are treated
  there, and apply the same treatment to the `aot` and `aotTest` source sets. It must not be a blanket
  suppression of hand-written code.

### Subtask T009: The plugin

1. `gradle/libs.versions.toml`:
   - `native-build-tools = "1.1.8"`;
   - `gradle-plugin-graalvm-native = { module = "org.graalvm.buildtools:native-gradle-plugin", version.ref = "native-build-tools" }`.
2. `build-logic/build.gradle.kts`: add `implementation(libs.gradle.plugin.graalvm.native)`.
3. `service/deployable/build.gradle.kts`: apply `id("org.graalvm.buildtools.native")`, then:

   ```kotlin
   graalvmNative {
       toolchainDetection = false
       metadataRepository { enabled = true }
       binaries.named("main") {
           imageName = "spec-kitty-charter-service"
           buildArgs.add("--no-fallback")
       }
   }
   ```

   Add a comment citing ADR-0046.

### Subtask T010: Verification metadata

Run:

```bash
./gradlew --write-verification-metadata sha256 :deployable:processAot :deployable:bootJar help
```

with `JAVA_HOME` set to Temurin 25.

- Review the diff of `gradle/verification-metadata.xml`. Only the plugin, its dependencies and the
  reachability-metadata artifact may be added. No `<trust>` entries are added.
- Check that every added component is one the plugin needs.
- The reachability-metadata zip is resolved lazily by `nativeCompile`. If CI resolves an artifact that is
  not yet verified, add it the same way.

### Subtask T011: Red: `CharterRuntimeHintsTest`

Using `RuntimeHintsPredicates` on a `RuntimeHints` filled by `new CharterRuntimeHints().registerHints(...)`,
assert:

- **Resources:**
  - `packs/file-kinds.yaml`
  - a sample `packs/schemas/<one>.schema.json`; take a real file name from the packs build
  - `ai/speckitty/charter/packs/infra/api/service-version.properties`
  - `ai/speckitty/charter/packs/infra/api/contract-version.properties`
  - `ai/speckitty/charter/deployable/charter-openapi.yaml`
  - networknt's `draft/2020-12/schema` and one vocabulary file
  - the `jsv-messages` resource bundle
- **Reflection:**
  - `ai.speckitty.charter.api.model.Problem` and `CharterRefusal` (binding)
  - `ai.speckitty.charter.packs.infra.api.ProblemJson$OmitAbsentMembers` (declared annotations)
  - `ProblemErrorReportValve` (constructor)
  - `ContainerProperties` (binding)
- **The service loader:** the resource `META-INF/services/java.net.spi.InetAddressResolverProvider`, and the
  public constructor of `GlobalAddressResolverProvider`.
- Also assert that `CharterServiceApplication` carries `@ImportRuntimeHints(CharterRuntimeHints.class)`.

Red: the class does not exist yet.

### Subtask T012: `CharterRuntimeHints`

- A `RuntimeHintsRegistrar` in `ai.speckitty.charter.deployable`, with one Javadoc line per hint saying why
  (the research N number).
- Use `BindingReflectionHintsRegistrar` for the binding types, and
  `BindableRuntimeHintsRegistrar.forTypes(ContainerProperties.class)`.
- Name classes in other modules through `TypeReference.of("…")` only where they are not public. Respect the
  ArchUnit layer rules.
- Add `@ImportRuntimeHints(CharterRuntimeHints.class)` to `CharterServiceApplication`.

### Subtask T013: Green, and the JVM build with AOT

Run:

```bash
./gradlew :deployable:check :deployable:processAot :deployable:bootJar verifyAll
```

- Run the jar normally, and with `-Dspring.aot.enabled=true`.
- Both must start with `CHARTER_WORKTREE_TOKEN` (40 characters) and `--server.port=18080`, and answer
  `GET /api/v1/info`.
- Record the commands and counts for the "Tests run" section.

## Definition of Done

- `processAot` and `bootJar` pass. The jar starts in both JVM and AOT modes.
- `CharterRuntimeHintsTest` is green, and `verifyAll` is green with strict verification.
- No new suppressions; AOT-generated code is handled like the existing generated sources.

## Risks

- **`processAot` fails**, for example a bean that needs the environment at build time. The fix belongs in
  the bean; record it as a further N item.
- **`processTestAot` gets pulled into `check`.** If so, keep the cucumber and mock contexts out of it, or
  disable the task with a reason. Prefer not to.
- **Configuration-cache incompatibility of the plugin.** Check that `--configuration-cache` stays clean.

## Reviewer Guidance

- Every hint has a reason and a test. No speculative blanket `**/*` resource patterns.
- `verification-metadata.xml` additions are plausible and scoped.
