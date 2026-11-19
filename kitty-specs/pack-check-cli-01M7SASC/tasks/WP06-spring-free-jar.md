---
work_package_id: WP06
title: The Spring-free jar and its proof
dependencies:
- WP04
requirement_refs:
- NFR-001
- NFR-004
- SC-001
- NFR-005
planning_base_branch: feat/478-pack-check
merge_target_branch: feat/478-pack-check
branch_strategy: Planning artifacts for this mission were generated on feat/478-pack-check. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/478-pack-check unless the human explicitly redirects the landing branch.
subtasks:
- T023
- T024
- T025
- T026
phase: Phase 3 - CLI
history:
- at: '2026-11-19T08:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: build-logic/src/main/kotlin/
create_intent:
- build-logic/src/main/kotlin/charter.pack-check-jar.gradle.kts
- build-logic/src/main/kotlin/PackCheckLicences.kt
- service/pack-check/src/test/java/ai/speckitty/charter/packcheck/jar/PackCheckJarTest.java
execution_mode: code_change
owned_files:
- build-logic/src/main/kotlin/charter.pack-check-jar.gradle.kts
- build-logic/src/main/kotlin/ModuleDependencyAllowlist.kt
- build-logic/src/main/kotlin/PackCheckLicences.kt
- service/pack-check/licenses/**
- build-logic/src/test/kotlin/ModuleDependencyAllowlistTest.kt
- service/pack-check/src/test/java/ai/speckitty/charter/packcheck/jar/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP06 — The Spring-free jar and its proof

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Build `pack-check-<version>.jar`: one runnable file, Spring-free (NFR-001), under 40 MB (NFR-004). Prove it works on
every path in a child JVM, inside `check` (so in `verifyAll`).

## Context

- Spec: NFR-001, NFR-002, NFR-004, User Story 4, SC-001. research.md R1 to R3 and squad J1, J3 to J10 and J12.
- **The lean classpath** (prototype R1) is 20 jars, 8.8 MB: the project jars of `pack-validation`, `packs`,
  `profiles`, `missions`, `routing` and `kernel`, plus `snakeyaml-engine`, `json-schema-validator`, `re2j`, Jackson 3
  core and databind, `jackson-annotations`, `commons-compress`, `slf4j-api`, `commons-codec`, `commons-lang3`,
  `commons-logging`, `commons-io`, `jspecify` and `itu`. Everything Spring, Tomcat, Jakarta, Micrometer, Logback,
  log4j-to-slf4j, `:web-transport` and `:charter-api` is excluded.
- **J5:** do not exclude `org.springframework.boot` by group. `jackson-databind` and `slf4j-api` take their versions
  from `spring-boot-dependencies`, so a group exclude drops the BOM. Exclude by module: `spring-boot-starter-webmvc`,
  `:web-transport`, `:charter-api`. Keep the resolved versions equal to `runtimeClasspath`'s.
- **J6:** `ModuleDependencyAllowlist` judges only `compileClasspath`/`runtimeClasspath`
  (`build-logic/src/main/kotlin/ModuleDependencyAllowlist.kt`, `CLASSPATHS`).
- **J1:** never put the SLF4J service file in `src/main/resources`. Deployable's tests have `:pack-check` beside
  Logback, and a second provider breaks Spring Boot's logging there.
- The configuration cache is on (`gradle.properties`). Gradle 9.8.1 archives are reproducible by default.

## Branch Strategy

Planning base `feat/478-pack-check`, merge target `feat/478-pack-check`; worktrees per lane from `lanes.json`. Run
`spec-kitty agent action implement WP06 --agent <name> --mission pack-check-cli-01M7SASC`.

## Subtasks

### T023 — The jar configuration

- Put the jar logic in a build-logic convention plugin, `charter.pack-check-jar` (new
  `build-logic/src/main/kotlin/charter.pack-check-jar.gradle.kts`). `service/pack-check/build.gradle.kts` (WP04's)
  gains only `id("charter.pack-check-jar")`: a one-line out-of-map edit, noted in the commit.
- Create a resolvable configuration, `packCheckJarClasspath`:
  - it extends `runtimeClasspath`'s dependencies;
  - it carries the module excludes above;
  - `shouldResolveConsistentlyWith(runtimeClasspath)`, as `mockitoAgent` does in `charter.hand-written`.
- Make the allowlist judge it: extend `ModuleDependencyAllowlist` with a way to enforce on an extra configuration name,
  and call it from the plugin. Its existing tests in build-logic must still pass; add one for the extra configuration.
- **Test** (in `jarTest`, T026): the library versions inside the jar equal `runtimeClasspath`'s for the same modules.
  Write the expected list from `runtimeClasspath` into a generated resource or a system property.

### T024 — `packCheckJar`

- `tasks.register<Jar>("packCheckJar")`:
  - `archiveBaseName = "pack-check"` and `destinationDirectory = layout.buildDirectory.dir("pack-check")` (J7: never
    `build/libs`);
  - manifest `Main-Class: ai.speckitty.charter.packcheck.PackCheckMain`, `Multi-Release: true`, and
    `Implementation-Version`;
  - `from(main output)` plus `from(packCheckJarClasspath.elements.map { zipTree })`, configuration-cache safe;
  - excluded:
    - `module-info.class` (any depth, `META-INF/versions/**/module-info.class` too);
    - the source jars' `META-INF/MANIFEST.MF`;
    - `META-INF/services/java.net.spi.InetAddressResolverProvider` (`:packs`' DNS guard; the CLI does no network);
    - signature files, defensively;
  - `duplicatesStrategy = DuplicatesStrategy.FAIL`. Licence files are moved by T025 before the merge, so they never
    collide.
- **The SLF4J service file:** a task writes `META-INF/services/org.slf4j.spi.SLF4JServiceProvider` containing
  `org.slf4j.helpers.NOP_FallbackServiceProvider` into `build/generated/pack-check-jar/`. Only `packCheckJar` includes
  it.
- `assemble` depends on `packCheckJar`.

### T025 — Licences

- Each library's licence and notice files (`META-INF/LICENSE*`, `META-INF/NOTICE*`, a root `LICENSE`, jackson-core's
  `FastDoubleParser-*` and `Schubfach-LICENSE`) go under `META-INF/licenses/<group>-<module>/`. Use a
  configuration-cache-safe task over the configuration's `ArtifactCollection` (input), which unpacks and renames.
- Check in the licence texts of the four jars that ship none: `re2j` (BSD-style), `json-schema-validator`
  (Apache-2.0), `itu` (Apache-2.0) and `snakeyaml-engine` (Apache-2.0). Put them at
  `service/pack-check/licenses/<group>-<module>/LICENSE`, taken verbatim from each project's repository at the pinned
  version, with the source URL in a sibling `SOURCE` file.
- The task fails when an artifact of the configuration ends up with no licence file.
- Add the project's own `LICENSE` as `META-INF/LICENSE`.

### T026 — `jarTest`

- A tagged `Test` task, `jarTest`, over the `test` source set (`includeTags("jar")`; the `test` task excludes the
  tag), as `wholePackRoundTrip` is in `pack-validation`.
  - `check` depends on it; PIT never runs it.
  - Inputs: the jar file (`inputs.file`), and its path as a system property.
  - The test class lives under `service/pack-check/src/test/java/ai/speckitty/charter/packcheck/jar/`.
- Launch with the toolchain's `javaLauncher` (pass `launcher.executablePath` as a system property), never `java` from
  `PATH`, in a clean environment: remove `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, `_JAVA_OPTIONS` and `CLASSPATH` from
  the `ProcessBuilder` environment, and use a temporary working directory.
- **Cases**, each asserting the exit code, the stdout JSON shape (parse it, check `checks` and the problem codes) and
  stderr:
  1. **Valid set:** the five example packs as `--config-root` with `--round-trip`. Exit 0, `checks` are
     `[PACK, CLOSURE, ROUND_TRIP]`, and stderr is empty or holds only `warning:` lines.
  2. **Pack fault:** a planted subject. Exit 1 with its codes.
  3. **Composition fault:** `project` alone. Exit 1, `pack_dependency_unknown`.
  4. **Planted link:** a link to a marker file holding a unique string. Exit 1, `pack_entry_linked`, and the marker in
     neither stdout nor stderr (squad S2).
  5. **Schema-invalid file:** a tactic with an unknown key. Exit 1, its schema code. This runs the networknt and
     commons paths.
  6. **Usage error:** no arguments. Exit 2, empty stdout.

  On top of the per-case checks, **stderr never contains** `SLF4J`, `Exception`, `NoClassDefFoundError` or
  `ClassNotFoundException`.
- **Jar entries:**
  - the jar refuses any entry under `org/springframework/`, `org/apache/tomcat/`, `jakarta/`, `ch/qos/logback/`,
    `ai/speckitty/charter/api/` and `ai/speckitty/charter/web/`, any `module-info.class`, and the resolver service;
  - the manifest has `Multi-Release: true` and the `Main-Class`;
  - the size is under 40 MB;
  - every `META-INF/licenses/*/` directory is non-empty.
- **Reproducible:** run `packCheckJar` twice into two directories (a second task with another destination, or a
  `jarTest` helper that rebuilds) and compare the bytes. If that is too costly inside `check`, run it in the CI jar job
  (WP07) and say so.
- Do not assert NFR-002's time here. WP07 times it in CI.

## Definition of Done

- `./gradlew verifyAll` builds the jar and runs `jarTest` green.
- `java -jar service/pack-check/build/pack-check/pack-check-<ver>.jar --config-root …` works on a machine with only
  Java 25.
- No Spring in the jar or on its classpath. Licences are present. Builds are byte-identical.

## Risks and reviewer guidance

- **A path not exercised.** The six cases are the guard; a reviewer checks each case's process actually ran (exit code
  asserted) and its stderr is checked.
- **Version shifts.** The version-equality test of T023 must fail when an exclude changes a resolved version.
