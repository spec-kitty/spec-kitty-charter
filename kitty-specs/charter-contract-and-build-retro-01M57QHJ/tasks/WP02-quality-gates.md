---
work_package_id: WP02
title: Quality gates behind one opt-in plugin
dependencies:
- WP01
requirement_refs:
- FR-006
- FR-007
- FR-008
- FR-009
- FR-010
- FR-011
- NFR-001
- NFR-003
- C-003
- C-004
- C-005
- C-008
planning_base_branch: ccr-9654c444-x7y94q
merge_target_branch: ccr-9654c444-x7y94q
branch_strategy: Planning artifacts for this mission were generated on ccr-9654c444-x7y94q. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into ccr-9654c444-x7y94q unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-charter-contract-and-build-retro-01M57QHJ
base_commit: 15d4ddf1a812d81032c9d78450f8e85d406b07cc
created_at: '2026-10-18T19:24:35.824290+00:00'
subtasks:
- T009
- T010
- T011
- T012
- T013
- T014
- T015
- T016
phase: Phase 2 - Build
history:
- at: '2026-10-18T15:42:56Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: build-logic/
create_intent:
- build-logic/src/main/kotlin/charter.java-base.gradle.kts
- build-logic/src/main/kotlin/charter.hand-written.gradle.kts
- config/checkstyle/checkstyle.xml
- config/checkstyle/suppressions.xml
- service/charter-service/src/main/java/ai/speckitty/charter/service/package-info.java
execution_mode: code_change
model: ''
owned_files:
- build-logic/**
- gradle/libs.versions.toml
- gradle/verification-metadata.xml
- config/**
- service/charter-domain/build.gradle.kts
- service/charter-read-app/build.gradle.kts
- service/charter-api/build.gradle.kts
- service/adapter-in-rest/build.gradle.kts
- service/adapter-out-yaml/build.gradle.kts
- service/charter-service/build.gradle.kts
- service/adapter-out-yaml/src/main/**
- service/charter-service/src/main/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02 – Quality gates behind one opt-in plugin

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ IMPORTANT: Review Feedback

- **Has review feedback?**: check the `review_ref` field in the event log (`spec-kitty agent tasks status`).
- Address every feedback item; record each with `spec-kitty agent tasks add-history WP02 --note "..."`.

## Review Feedback

*[Filled from the status event log when this WP returns from review.]*

---

## Objectives & Success Criteria

Add the operator-vetted gates (ruling 2026-10-18) so they fail the build, exempt generated
code by construction, and leave no suppression debt:

- Checkstyle and Spotless with palantir-java-format on main and test.
- Error Prone on main and test; NullAway in JSpecify `OnlyNullMarked` mode on main only.
- Javadoc with `-Xdoclint:all` and `-Werror` on main, part of `check`; every public type and
  member documented.
- JaCoCo per module; PIT configured for charter-domain and charter-read-app and **not** part
  of `check`; Mockito available, with one test that genuinely uses it.
- jqwik is **not** added (C-008); OpenRewrite is **not** added.

Done when `./gradlew verifyAll` is green on Java 25 and each gate fails on a planted violation.

## Context & Constraints

- Read first: `.kittify/charter/charter.md`; `spec.md` FR-006–FR-011, NFR-001, NFR-003,
  C-003–C-005, C-008; `plan.md` IC-01, IC-02, Charter Check (supply-chain controls);
  `research.md` §6 and §10 rows G1–G4.
- **Generated code is exempt by construction, not by subtraction (G1).** Today
  `charter.contract-api` strips `-Werror`/`-Xlint:all` from a flag list it inherited. Replace
  that with: `charter.java-base` (toolchain, compile, JUnit, BOMs) applied everywhere, and
  `charter.hand-written` (every gate) applied to every module except `charter-api`.
  Aggregations and PIT targets select projects by `plugins.hasPlugin("charter.hand-written")`,
  never a hard-coded module list.
- **No suppression debt (NFR-001).** No blanket suppressions, no inline `@SuppressWarnings`
  without the register's fields; per-call-site suppression of NullAway is forbidden (G2/G3).
- **Out-of-map edits, recorded.** This WP depends on WP01 and must bring WP01's sources
  (`service/charter-domain/src`, `charter-read-app/src`, `adapter-in-rest/src`,
  `adapter-out-yaml/src/test`, `charter-service/src/test`) into compliance. Those edits are
  limited to formatting (T011), nullness annotations and their direct fixes (T013), and
  Javadoc (T014): no behavior change. Record that rationale once with `add-history`.
- Strict dependency verification stays on; the configuration cache stays on.

## Branch Strategy

- **Strategy**: set by finalize-tasks
- **Planning base branch**: `ccr-9654c444-x7y94q`
- **Merge target branch**: `ccr-9654c444-x7y94q`

```bash
spec-kitty agent action implement WP02 --agent claude --mission charter-contract-and-build-retro-01M57QHJ
```

## Subtasks & Detailed Guidance

### Subtask T009 – Split the conventions; narrow the scenario classpath

- **Steps**:
  1. Create `charter.java-base.gradle.kts` from the toolchain, compile options (`-Xlint:all`, `-Werror`, `-parameters` for hand-written code), JUnit and BOM parts of `charter.java-conventions`; create `charter.hand-written.gradle.kts` for the gates. Decide the compile-flag split carefully: the generated module must compile without `-Werror`; the cleanest shape is that `-Xlint:all -Werror` lives in `charter.hand-written` and `charter.java-base` sets only `-parameters` and the encoding.
  2. Re-base `charter.domain-conventions`, `charter.spring-conventions`, `charter.service-conventions` and `charter.contract-api` on the base; remove the `removeAll` subtraction in `charter.contract-api`.
  3. Apply `charter.hand-written` in the five hand-written module build files. Narrow `charter.contract-api`'s `generateContract` inputs to `contracts/charter/**` (today all of `contracts/` is an input, so a checker edit re-runs code generation; found in WP01 cycle 6). Also in `contracts/build.gradle.kts`: WP01's closure checker (T033) is hand-written Java and gets every gate (a one-line out-of-map edit of WP01's file; record it with `add-history`). It is excluded from the aggregated Javadoc and JaCoCo of WP03 only if those select service modules; say which in the history note.
  4. `service/charter-service/build.gradle.kts`: put only `scenarios/**` of `contracts/charter/` on the test resource path (`include("scenarios/**")`), keeping the classpath resource name `scenarios` the suite selects (D1).
- **Check**: `./gradlew verifyAll` still green before any gate is added.

### Subtask T010 – Catalog, plugins and verification metadata

- **Steps**: add to `gradle/libs.versions.toml` (exact versions, each released at least two weeks ago, current enough for JDK 25): the Spotless Gradle plugin and palantir-java-format; Checkstyle (tool version); the `net.ltgt.errorprone` Gradle plugin, `error_prone_core`, NullAway, `org.jspecify:jspecify`; the gradle-pitest plugin and `pitest-junit5-plugin`; Mockito (`mockito-core`, `mockito-junit-jupiter`). Plugins go on `build-logic` as `implementation` dependencies like the existing ones. Regenerate the verification metadata with `./gradlew --write-verification-metadata sha256 verifyAll`, then **review the diff**: only the expected groups; no changed existing checksum.
- **Supply chain (DIRECTIVE_051)**: record each artifact's licence and source in `add-history`; reject anything with install-time scripts or AI-use terms.

### Subtask T011 – Formatting-only commit

- **Steps**: configure Spotless (`palantirJavaFormat()`, `removeUnusedImports`, `formatAnnotations` as palantir recommends) for `src/main/java` and `src/test/java` of hand-written modules; run `spotlessApply`; commit **only** the formatting change, message `style: apply palantir-java-format`; then wire `spotlessCheck` into `check` (G4).

### Subtask T012 – Checkstyle

- **Steps**: `config/checkstyle/checkstyle.xml`: a ruleset that does not fight palantir (no whitespace/indent/line-length rules; formatting is Spotless's job). Include naming, imports (no star imports), `FinalClass`, `HideUtilityClassConstructor`, `MissingSwitchDefault`, `EqualsHashCode`, `ModifierOrder`, and similar semantic checks. `config/checkstyle/suppressions.xml` is the **one** register for any exception: each entry carries the rule, the file, a reason comment and a sunset date. Start it empty if you can. Checkstyle failures fail the build (`isIgnoreFailures = false`, `maxWarnings = 0`).

### Subtask T013 – Error Prone and NullAway

- **Steps**:
  1. Apply Error Prone to `compileJava` and `compileTestJava` of hand-written modules; add the JDK 25 `--add-exports`/`--add-opens` the Error Prone docs list for javac (on the compile task's fork options or `options.compilerArgs` as the plugin documents). Errors fail the compile.
  2. NullAway on main only: `NullAway:OnlyNullMarked=true`, JSpecify mode (`NullAway:JSpecifyMode=true`), severity ERROR. `@NullMarked` in every hand-written `package-info.java`; create `service/charter-service/src/main/java/ai/speckitty/charter/service/package-info.java`.
  3. Fix every finding at the source: annotate genuinely nullable values `@Nullable`; at the `orElse(null)` sites that feed generated DTOs (`PacksController.java`, `ContextController.java`), make the nullable explicit (a `@Nullable` local or a mapping helper), never a suppression. Also `PackDescriptorReader`, `ScalarWriter`, `CharterProperties` (Spring binding), `DoctrineGraph.equals`.

### Subtask T014 – Javadoc with doclint as errors

- **Steps**: in `charter.hand-written`, `tasks.withType<Javadoc>` with `addBooleanOption("Xdoclint:all", true)`, `addBooleanOption("Werror", true)`, `-notimestamp`; make `check` depend on `javadoc`. Document **every** public type, member, constructor and nested record (operator ruling: document everything), including `YamlDocument.Trivia` and `TokenText`. Write useful sentences: what it is, units and nullability, what it refuses. The generated module has no Javadoc task in the gate (C-005).

### Subtask T015 – JaCoCo, PIT, Mockito

- **Steps**:
  1. JaCoCo on every hand-written module; `jacocoTestReport` (XML + HTML) after `test`, part of `check`. No threshold in this mission (FR-008 asks for a report); the aggregate report is WP03's.
  2. PIT via the gradle-pitest plugin on charter-domain and charter-read-app only: `junit5PluginVersion`, `targetClasses = ["ai.speckitty.charter.domain.*"]` etc., `outputFormats = ["HTML","XML"]`, `timestampedReports = false`. **Not** part of `check` or `verifyAll`; it runs as `./gradlew pitest` (the nightly workflow calls it, WP04).
  3. Mockito on the test classpath of hand-written modules through the catalog; add one test where a mock is genuinely the clearest choice (for example verifying `PackListingQuery` calls the repository port once and maps its refusal), keeping hand-written fakes elsewhere.

### Subtask T016 – Planted-violation proofs and a green build

- **Steps**: for each gate, plant one violation, run the build, see it fail with the rule named, revert: a misformatted line (Spotless), a star import (Checkstyle), a reference-equality string compare (Error Prone `StringEquality` or similar), a `null` returned from a `@NullMarked` method (NullAway), a malformed `@param` (doclint). Then `./gradlew verifyAll -PopenPacksDir=...` green, and `./gradlew pitest` runs and writes its report. Record commands, counts and the suppressions-register size (target 0) with `add-history`.

## Test Strategy

The gates are the tests; each needs its planted-violation proof (non-vacuity). The build
runs `-Werror`: compiler diagnostics count as failures.

## Risks & Mitigations

- Error Prone and JDK 25 compatibility: pick an Error Prone release that supports JDK 25; if none does, stop and escalate (do not drop the gate silently).
- Configuration cache: Spotless, Checkstyle, Error Prone and PIT plugins must not break it; verify with a second run (`Configuration cache entry reused`).
- Build time (NFR-002): note the verifyAll wall time before and after.

## Review Guidance

- `charter-api` applies no gate and needs no subtraction anywhere.
- No suppressions outside the register; the register is empty or every entry has rule, reason and sunset.
- Out-of-map edits are formatting, nullness and Javadoc only.
- Five planted-violation proofs recorded.

## Progress & Status

`spec-kitty agent tasks add-history WP02 --note "..."`, `spec-kitty agent tasks mark-status T009 --status done`, `spec-kitty agent tasks status`.
