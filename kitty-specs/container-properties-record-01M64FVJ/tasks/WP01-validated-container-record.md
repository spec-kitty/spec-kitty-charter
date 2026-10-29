---
work_package_id: WP01
title: Validated container record
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
planning_base_branch: feat/228-container-config-properties
merge_target_branch: feat/228-container-config-properties
branch_strategy: Planning artifacts for this mission were generated on feat/228-container-config-properties. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/228-container-config-properties unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
phase: Phase 1 - Implementation
history:
- at: '2026-10-29T19:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/deployable/
create_intent:
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ContainerProperties.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ContainerWiring.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ContainerPropertiesTest.java
execution_mode: code_change
model: ''
owned_files:
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ContainerProperties.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ContainerWiring.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ContainerPropertiesTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ProblemResponsesTest.java
- service/deployable/build.gradle.kts
- gradle/libs.versions.toml
- gradle/verification-metadata.xml
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 – Validated container record

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter (or any user-defined profile), and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Review Feedback

*[If this WP was returned from review, the reviewer feedback reference appears in the status event log.]*

---

## Objectives & Success Criteria

ADR-0041 decision 2: a separate `@Validated` `@ConfigurationProperties` record at `charter.container.*`, in the
deployable beside `CharterProperties`, bound only under the `container` profile. Done when:

- `charter.container.allowed-hosts` (absent, empty, a blank entry) fails a `container`-profile start, and the failure
  names `charter.container.allowed-hosts` (FR-002);
- a valid list binds in order (FR-001);
- without the profile nothing at `charter.container.*` is bound, even an invalid value (FR-003);
- the shipped `CharterServiceApplication` binds the record under the profile (component scan);
- the 400 side effect of bean validation is pinned (FR-004, research.md R2);
- `./gradlew verifyAll` passes on Java 25 with zero new suppressions.

## Context & Constraints

- ADR-0041 decisions 1, 2, 6 and 8: profile `container`, never a default. The packs module does not learn the
  profile, and no secret goes in the record.
- Out of scope (C-002): `application-container.yaml` and the secret import (#229), the configuration processor (#230),
  guard changes and guard tests (#231), actuator (#232), and the start-up validator for keys outside the namespace
  (deferred, R1).
- Gates: javac `-Werror`, Error Prone and NullAway, doclint on main (Javadoc `@param` for each record component),
  Checkstyle (`HideUtilityClassConstructor`: a configuration class with only static members trips it), Spotless.

## Branch Strategy

- **Planning base branch**: `feat/228-container-config-properties`
- **Merge target branch**: `feat/228-container-config-properties` (the mission then lands on `main` by PR)

> Implement with `spec-kitty agent action implement WP01 --agent claude --mission container-properties-record-01M64FVJ`.

## Subtasks & Detailed Guidance

### T001 – The validation starter

- Add `spring-boot-starter-validation` (BOM-managed, no version) to `gradle/libs.versions.toml` and
  `implementation(catalog.lib("spring-boot-starter-validation"))` to `service/deployable/build.gradle.kts`.
- Regenerate with `./gradlew --write-verification-metadata sha256 verifyAll`. Never hand-edit the file. Review the
  diff: it should add only hibernate-validator, jboss-logging, classmate and the Boot validation modules.

### T002 – `ContainerProperties`

- `@ConfigurationProperties("charter.container") @Validated public record ContainerProperties(...)`, with
  `allowedHosts` as `@NotEmpty List<@NotBlank String>`.
- Spring reports the Java field name (`allowedHosts`), so each constraint's `message` names the canonical
  `charter.container.allowed-hosts`.
- Hold the profile name as a constant here (`PROFILE = "container"`), not on the wiring class (Checkstyle).

### T003 – `ContainerWiring`

- `@Configuration(proxyBeanMethods = false) @Profile(ContainerProperties.PROFILE)
  @EnableConfigurationProperties(ContainerProperties.class)`. The application's scan registers it, as it does
  `CharterWiring` and `GuardWiring`. Do not add the record to `CharterServiceApplication`'s enablement: that would
  bind it without the profile.

### T004 – `ContainerPropertiesTest`

- Use `ApplicationContextRunner` with `ContainerWiring` and `spring.profiles.active`. Cover a valid binding, an
  absent key, an empty list, a blank entry (with the index), no profile with an invalid value, another profile alone,
  and `container` beside another profile.
- The shipped application: `SpringApplicationBuilder(CharterServiceApplication.class)` with web `NONE` and the profile.
  Set the token and the config root as default properties, never as command-line arguments, which the guard refuses.
  It must fail without the key and bind with it. Use a fresh builder per run.

### T005 – The side effect

- In `ProblemResponsesTest`, a `POST /api/v1/packs/qa/directives` body without `title` answers
  `{"title":"Bad Request","status":400}` as `application/problem+json`. Without the starter it answered a 500
  `internal_error` (measured, R2).

### T006 – Gates

- `./gradlew :deployable:check`, then `./gradlew verifyAll` (Java 25).
- Non-vacuity: remove `@Validated` temporarily. The failure tests must go red; then restore it.

## Definition of Done

- All six subtasks are done; `verifyAll` is green; no new suppressions; complexity at most 15.

## Reviewer Guidance

- Check that the namespace is unbound without the profile, that the key is named in every failure, and that the
  verification-metadata diff matches the starter's tree.
