---
work_package_id: WP03
title: Start-up check on keys outside the namespace
dependencies:
- WP01
requirement_refs:
- FR-005
- FR-006
- FR-007
- FR-008
planning_base_branch: feat/228-container-config-properties
merge_target_branch: feat/228-container-config-properties
branch_strategy: Planning artifacts for this mission were generated on feat/228-container-config-properties. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/228-container-config-properties unless the human explicitly redirects the landing branch.
subtasks:
- T009
- T010
- T011
- T012
phase: Phase 1 - Implementation
history:
- at: '2026-10-29T19:55:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks after the operator's ruling on
agent_profile: java-jenny
authoritative_surface: service/deployable/src/main/java/ai/speckitty/charter/deployable/ContainerStartupCheck.java
create_intent:
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ContainerStartupCheck.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ContainerStartupCheckTest.java
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

# Work Package Prompt: WP03 – Start-up check on keys outside the namespace

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter (or any user-defined profile), and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Review Feedback

*[If this WP was returned from review, the reviewer feedback reference appears in the status event log.]*

---

## Objectives & Success Criteria

The operator ruled on #228 (issuecomment-6506747587) that the start-up check of ADR-0041 decision 2 belongs in this
mission. Under the `container` profile, the start fails, naming the property, when:

- `server.port` is 0 (FR-005);
- `charter.config-root` is unset, relative, missing or not a directory (FR-006). The message never echoes the value.
- `charter.insecure-no-token` is set, whatever its value (FR-007);
- `charter.project-config-dir` is set (FR-008).

Without the profile none of these rules applies.

## Context & Constraints

- Out of scope:
  - the local rule that a non-loopback bind fails (D4) belongs to #231;
  - the token message naming both forms (D3) belongs to #229;
  - handing `allowed-hosts` to the guard becomes a follow-up issue.
- The packs module does not learn the profile; the check lives in the deployable.
- The gates as in WP01 apply. A class with only static members and a public constructor trips Checkstyle's
  `HideUtilityClassConstructor`, so the check is an `InitializingBean`.

## Branch Strategy

- **Planning base branch**: `feat/228-container-config-properties`
- **Merge target branch**: `feat/228-container-config-properties`

> Implement with `spec-kitty agent action implement WP03 --agent claude --mission container-properties-record-01M64FVJ`.

## Subtasks & Detailed Guidance

### T009 – `ContainerStartupCheck`

- Make it a `@Configuration(proxyBeanMethods = false)` class with `@Profile(ContainerProperties.PROFILE)` that
  implements `InitializingBean` and takes the `Environment` through its constructor.
- An unset `server.port` reads as Spring's default, 8080.
- "Set" means `Environment.containsProperty`, so relaxed env-var names count.

### T010 – `ContainerStartupCheckTest`

- Use `ApplicationContextRunner` with the check only.
- Start from a valid baseline: the profile, port 8080 and a `@TempDir` root. Break one rule per test, and assert the
  message names the property.
- For the missing root, assert the message does not contain the path.
- Without the profile, every breach together still starts.

### T011 – The shipped application

- Builder properties rank below `application.yaml`, so pass `--server.port=8080` and `--configRootLocation=<root>` as
  arguments.
- Pin that the local `port: 0` is refused under `container` when no port is given.

### T012 – Gates and docs

- Run `verifyAll`.
- Non-vacuity: make `afterPropertiesSet` return early. The eight breach tests must go red. Restore it afterwards.
- Update the changelog entry: the new rules, and that a container needs `SERVER_PORT` until #229.

## Definition of Done

- All four subtasks are done, `verifyAll` is green and the changelog is updated.
