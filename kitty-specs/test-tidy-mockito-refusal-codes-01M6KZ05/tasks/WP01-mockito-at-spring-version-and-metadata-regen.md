---
work_package_id: WP01
title: Mockito at Spring's version, verified metadata regenerated
dependencies: []
requirement_refs:
- FR-001
- FR-001a
- SC-001
planning_base_branch: test-tidy-mockito-refusal-codes
merge_target_branch: test-tidy-mockito-refusal-codes
branch_strategy: Planning artifacts for this mission were generated on test-tidy-mockito-refusal-codes. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test-tidy-mockito-refusal-codes unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-test-tidy-mockito-refusal-codes-01M6KZ05
base_commit: 75b7b67213304ad8d5a9465f2abacb04a0dbf0e0
created_at: '2026-11-04T19:25:09.775999+00:00'
subtasks:
- T001
- T002
- T003
- T004
phase: Phase 1 - Tidy first
agent: claude
history:
- at: '2026-11-04T19:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: gradle/
execution_mode: code_change
owned_files:
- gradle/libs.versions.toml
- gradle/verification-metadata.xml
- build-logic/src/main/kotlin/charter.hand-written.gradle.kts
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP01 — Mockito at Spring's version, verified metadata regenerated

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Close #373 the way the operator ruled (decisions `01M6KZNKE4QW577JQ5MFXVVPPQ`, `01M6KZNNTG47M33595KY54671S`):
**keep** Mockito and its Java agent, align the declared version to the one the Spring stack provisions, record why
it is kept, and regenerate `gradle/verification-metadata.xml` from scratch so it lists no stale checksum
(spec FR-001, FR-001a; research R-03, R-04).

## Branch Strategy

Planning base and final merge target: `test-tidy-mockito-refusal-codes`. The execution worktree is allocated per
computed lane from `lanes.json`; do not pick a base branch by hand.

```bash
spec-kitty agent action implement WP01 --agent claude --mission test-tidy-mockito-refusal-codes-01M6KZ05
```

## Context

- Today `gradle/libs.versions.toml:29` pins `mockito = "5.24.0"`. `spring-boot-dependencies` 4.1.1 manages
  `<mockito.version>5.23.0</mockito.version>` and `spring-boot-starter-test` 4.1.1 requires 5.23.0 for
  `mockito-core` and `mockito-junit-jupiter`. The dependency report of `:deployable` shows
  `org.mockito:mockito-core:5.23.0 -> 5.24.0`: our pin overrides Spring's.
- No test uses Mockito (0 `org.mockito` imports). It stays because the starter brings it to `:deployable` and
  `:packs` anyway, and the agent avoids the JDK's dynamic self-attach refusal the day a test uses it.
- Gradle's `--write-verification-metadata` only **adds** entries; it never prunes. The file holds 578 components,
  including stale ones (`mockito-core` 5.23.0 and 5.24.0; `byte-buddy` 1.17.7, 1.18.3, 1.18.11).
- `docs/guides/service-build.md` ("Change a dependency"): never edit the metadata by hand; review its diff.
- Draft PR #368 also edits `charter.hand-written.gradle.kts` (Checkstyle register). Touch that file **only** in
  the comment above the Mockito block (C-003).

## Subtask T001 — Align the Mockito version to Spring's

**Purpose**: one Mockito version in every test JVM, equal to the one Spring Boot manages.

**Steps**:
1. Confirm the managed version from the resolved BOM, not from memory:
   `find ~/.gradle/caches/modules-2 -name 'spring-boot-dependencies-<spring-boot version>.pom'` and read
   `<mockito.version>`. With `spring-boot = "4.1.1"` it is 5.23.0.
2. Set `mockito = "5.23.0"` in `gradle/libs.versions.toml`, with a one-line comment above it:
   it tracks the version `spring-boot-dependencies` manages, and moves with `spring-boot`.
3. Do not add a Spring BOM platform to `kernel`, `missions` or `profiles` to achieve this: those modules stay
   Spring-free.

**Validation**:
- `./gradlew -q :deployable:dependencies --configuration testRuntimeClasspath | grep mockito-core` shows
  `5.23.0` with no `->` arrow to another version; same for `:packs`, `:kernel`, `:missions`, `:profiles`.

## Subtask T002 — Record why Mockito and its agent are kept

**Purpose**: #373's acceptance allows "record why they are kept"; the reason lives next to the declaration.

**Steps**:
1. Replace the comment above the `mockitoAgent` block in
   `build-logic/src/main/kotlin/charter.hand-written.gradle.kts` (today: "Mockito where a hand-written fake is not
   worth it…") with a comment that says, in two or three lines: no test uses Mockito today; it is kept because
   `spring-boot-starter-test` brings it to the Spring modules regardless; the agent is attached so a future use
   does not hit the JDK's dynamic-attach refusal; the version follows Spring Boot's (see the catalog). Name #373.
2. Change nothing else in the file.

**Validation**: `git diff build-logic/` shows a comment-only change.

## Subtask T003 — Regenerate the verified metadata from scratch

**Purpose**: the file lists exactly what the build resolves (FR-001a).

**Steps**:
1. Keep `<configuration>` (and any trusted-artifacts or key blocks) verbatim. Empty the `<components>` element.
2. Regenerate over every task family CI runs, in one invocation per family, each with
   `--write-verification-metadata sha256` (Gradle merges into the file):
   - `./gradlew --write-verification-metadata sha256 help` (resolves every resolvable configuration);
   - `./gradlew --write-verification-metadata sha256 -Pcharter.native=true help` (the native plugin's configurations);
   - `./gradlew --write-verification-metadata sha256 verifyAll -PrequireSymlinks=true`;
   - `./gradlew --write-verification-metadata sha256 pitest`;
   - `./gradlew --write-verification-metadata sha256 :packs:wholePackRoundTrip :packs:timingBudgets :deployable:bootJar :charter-reports:aggregateJavadoc :contracts:bundleContract`;
   - `./gradlew --write-verification-metadata sha256 -p build-logic check`.
   A `wholePackRoundTrip` failure on its known expected-red list is fine here; only resolution matters.
3. Diff old vs new by component coordinates (a short throwaway script; do not commit it). Every **removed**
   component must be an artifact no task resolves (an older version superseded, or Mockito 5.24.0). Every
   **added** one must be explained (it was resolved but missing, which strict verification would have caught,
   so expect none). Put the removed/added lists in the WP activity log.

**Edge cases**:
- An entry used only by the release workflow (`checkServiceReleaseTag`) or native build: covered by the native
  `help` run; if unsure, keep it by running the task.

## Subtask T004 — Prove it from a cold cache

**Purpose**: a dropped entry would only show on a fresh dependency cache.

**Steps**:
1. Run with a fresh Gradle user home that reuses the Central mirror init script:
   `GRADLE_USER_HOME=$(mktemp -d)`; copy `~/.gradle/init.d` into it; then
   `./gradlew verifyAll -PrequireSymlinks=true` and `./gradlew pitest --continue`.
2. Both green under strict verification. Record the commands, durations and test counts.

## Definition of Done

- [ ] One Mockito version (5.23.0, Spring's) in every module's test runtime classpath.
- [ ] The keep-reason comment is in place; the `.gradle.kts` diff is comment-only.
- [ ] `verification-metadata.xml` regenerated from scratch; removed/added component lists recorded and justified.
- [ ] `verifyAll` and `pitest` green from a cold Gradle user home.
- [ ] One commit for this WP (tidy-first, C-002).

## Reviewer guidance

- Check the version claim against the actual BOM pom, not the prompt.
- Sample three removed metadata components and confirm no task resolves them (`dependencyInsight`).
- Confirm no other line in `charter.hand-written.gradle.kts` changed (the #368 overlap).
