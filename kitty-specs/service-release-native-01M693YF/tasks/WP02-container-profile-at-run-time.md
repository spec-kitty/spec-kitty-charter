---
work_package_id: WP02
title: The container profile is decided at run time (D4a)
dependencies:
- WP01
requirement_refs:
- FR-008
planning_base_branch: issue-241-71-service-release-native
merge_target_branch: issue-241-71-service-release-native
branch_strategy: Planning artifacts for this mission were generated on issue-241-71-service-release-native. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-241-71-service-release-native unless the human explicitly redirects the landing branch.
subtasks:
- T005
- T006
- T007
- T008
history: []
agent_profile: java-jenny
authoritative_surface: service/deployable/src/
create_intent:
- service/deployable/src/test/java/ai/speckitty/charter/deployable/NoBuildTimeProfilesTest.java
execution_mode: code_change
model: ''
owned_files:
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ContainerStartupCheck.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ContainerWiring.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ContainerProperties.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ContainerStartupCheckTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ContainerPropertiesTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/NoBuildTimeProfilesTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP02: The container profile is decided at run time

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Make ADR-0041's container start-up rules hold in a native executable. The profile's check and the
validation of `charter.container.*` must run whenever the `container` profile is active at run time, not
only when it was active when the AOT image was built (research N1, ruling D4a, FR-008).

Implementation command:
`spec-kitty agent action implement WP02 --agent claude --mission service-release-native-01M693YF`

## Context

- `ContainerWiring` (`@Profile("container")`, with `@EnableConfigurationProperties(ContainerProperties)`)
  and `ContainerStartupCheck` (`@Profile("container")`, an `InitializingBean`) are profile-gated
  configuration classes.
- Spring AOT evaluates `@Profile` when the image is built. A native image built without the profile drops
  them. Started with `SPRING_PROFILES_ACTIVE=container`, it would skip the check: it **fails open**.
- `GuardWiring` already checks the profile at run time (`environment.matchesProfiles(PROFILE)`, line 157).
  Follow that pattern.
- Keep behavior identical on the JVM:
  - every existing `ContainerStartupCheckTest` and `ContainerPropertiesTest` case still holds;
  - messages name the property, never the value;
  - lazy initialization never skips the check.

### Subtask T005: Red: no main bean is gated by `@Profile`

Add `NoBuildTimeProfilesTest`, an ArchUnit test like `HexagonalArchitectureTest`, over
`ai.speckitty.charter..` main classes. The rule: no class or method is annotated with
`org.springframework.context.annotation.Profile`.

- The reason goes in its Javadoc: AOT fixes profiles at image build time.
- It fails today, on `ContainerWiring` and `ContainerStartupCheck`. Record the red run.

### Subtask T006: Red: the check and binding run under the profile, and only there

In `ContainerStartupCheckTest` and `ContainerPropertiesTest`, using the context runner over the
configuration that remains after T007:

- **Under the profile with `charter.container.allowed-hosts` missing, the start fails**, naming
  `charter.container.allowed-hosts`. This is the case a profile-less AOT image would lose.
- **Without the profile, the check is present and does nothing.** The context starts, and no
  `ContainerProperties` validation runs.

Keep the existing assertions. Where one asserts that the bean is absent without the profile, rewrite it to
assert the observable outcome (the start passes) instead.

### Subtask T007: Implement the run-time check

1. `ContainerStartupCheck`:
   - remove `@Profile`;
   - `afterPropertiesSet` returns at once unless `environment.matchesProfiles(ContainerProperties.PROFILE)`;
   - under the profile, it first binds and validates `ContainerProperties`. Use
     `Binder.get(environment).bindOrCreate("charter.container", Bindable.of(ContainerProperties.class),
     new ValidationBindHandler(validator))` with the context's
     `jakarta.validation.Validator` (inject `ObjectProvider<Validator>`), or Boot's
     `ConfigurationPropertiesBindingPostProcessor` equivalent. The constraint messages stay as they are.
2. Delete `ContainerWiring`. Move its Javadoc point into `ContainerStartupCheck`.
3. The binding hints for `ContainerProperties` are registered by WP03's `CharterRuntimeHints`
   (`BindableRuntimeHintsRegistrar.forTypes(ContainerProperties.class)`). Note it in the Javadoc.
4. Update `ContainerProperties`' Javadoc: "bound … only under that profile (`ContainerStartupCheck`)".

### Subtask T008: Green

Run:

```bash
JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew :deployable:check
./gradlew verifyAll
```

All the new tests are green. Zero new suppressions.

## Definition of Done

- The ArchUnit rule is in place and green.
- The container rules hold whatever profile the image was built with.
- The JVM behavior is unchanged, and the existing tests are kept or strengthened.
- `verifyAll` is green.

## Risks

- **Validation messages change** when binding manually, because Spring's binder wraps them. Mitigation:
  assert on the property name, as the tests already do.
- **Bean ordering:** the check must still run before the web server starts. As an `InitializingBean` on a
  configuration class it runs at refresh, as before.

## Reviewer Guidance

- Check that the guarantee is identical under the profile, and that nothing runs without it.
- Check that no secret value can reach an error message.
