---
work_package_id: WP03
title: Container composition, host entries and the 403 text
dependencies:
- WP02
requirement_refs:
- FR-005
- FR-007
- FR-010
- C-003
- SC-003
planning_base_branch: feat/container-guard-and-actuator
merge_target_branch: feat/container-guard-and-actuator
branch_strategy: Planning artifacts for this mission were generated on feat/container-guard-and-actuator. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/container-guard-and-actuator unless the human explicitly redirects the landing branch.
subtasks:
- T009
- T010
- T011
- T012
history: []
agent_profile: java-jenny
authoritative_surface: service/deployable/src/main/java/ai/speckitty/charter/deployable/
create_intent:
- contracts/charter/changes/2026-11-05-forbidden-configured-hosts.md
execution_mode: code_change
model: ''
owned_files:
- service/deployable/src/main/java/ai/speckitty/charter/deployable/GuardWiring.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ContainerProperties.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/GuardWiringTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ContainerPropertiesTest.java
- contracts/charter/responses/Forbidden.yaml
- contracts/charter/openapi.yaml
- contracts/charter/changes/2026-11-05-forbidden-configured-hosts.md
role: implementer
tags: []
tracker_refs: []
---
# WP03: Container composition, host entries and the 403 text

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
`spec-kitty agent action implement WP03 --agent claude --mission container-guard-and-actuator-01M6P99A`

## Objective

Under the `container` profile, `GuardWiring` hands the guard `GuardPolicy.named(allowedHosts)` with the health
exemption. Without the profile it hands over `GuardPolicy.loopback()`. Malformed `allowed-hosts` entries fail
the start (FR-005), and the contract describes the configured list (FR-007).

## Subtasks

### T009 — Entry grammar (`ContainerProperties`)

Add an element `@Pattern` beside `@NotBlank`, case-insensitive (`flags = Pattern.Flag.CASE_INSENSITIVE`). It
accepts:
- an LDH DNS name: labels `[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?` separated by dots, total ≤ 253, no trailing dot;
- a dotted IPv4 literal;
- `\[[0-9a-f:.]+\]`.

Negative lookaheads reject `0.0.0.0` and the bracketed any-address forms `[::]` and `[0:0:0:0:0:0:0:0]`.

The message is `charter.container.allowed-hosts entries must be a host name, an IPv4 address or a bracketed
IPv6 address, without a port, scheme, path, wildcard or trailing dot, and not the any-address`.

Keep the record's Javadoc in step. Watch the native image: `CharterRuntimeHints` already registers the
record's binding.

### T010 — `GuardWiring`

- `worktreeGuardRegistration` gains a `ContainerWiring containerWiring` parameter (it is a bean; it binds in
  `afterPropertiesSet`).
- The policy is `containerWiring.properties().map(p -> GuardPolicy.named(p.allowedHosts()).withTokenExemptGet("/actuator/health")).orElse(GuardPolicy.loopback())`.
  Keep `/actuator/health` as a named constant, documented with ADR-0041 D7.
- Update the class Javadoc list.
- Make sure the binding has happened before the registration bean is built: inject the bean so Spring
  initializes it first. Verify with the existing `theContainerWiringRegistersTheSameBeansWithAndWithoutTheProfile`.

### T011 — Tests

`ContainerPropertiesTest`: a parameterized refusal under the container profile for each of `*`,
`*.example.com`, `.example.com`, `host:8080`, `http://host`, `host/path`, `a@b`, `charter.internal.`,
`bücher.de`, `0.0.0.0`, `[::]`, `::1` (unbracketed) and `[fe80::1%25eth0]`. Each must fail the start with a
message naming `charter.container.allowed-hosts`. Acceptance cases: `charter.internal`, `LOCALHOST`,
`10.0.0.5` and `[2001:db8::1]`.

`GuardWiringTest`:
- Under the container profile, with allowed hosts `charter.internal`: a request with `Host: charter.internal`
  and the token passes; `Host: localhost` is 403; `GET /actuator/health` without a token passes the guard.
- Without the profile: `Host: charter.internal` is 403, and `GET /actuator/health` without a token is 401.

Drive the registered filter with mock requests, as the existing tests do.

### T012 — Contract (limited to the 403 text, C-003)

- In `contracts/charter/responses/Forbidden.yaml` and in the `charterWorktreeToken` description in
  `openapi.yaml`, extend the host sentence: under a containerized deployment the host name is compared with the
  deployment's configured list instead of the loopback names; the port is still not judged.
- Do not mention actuator: it is not a contract operation.
- Add the fragment `contracts/charter/changes/2026-11-05-forbidden-configured-hosts.md` with one `### Changed`
  block. Say that core's seam is unaffected (a local daemon is unchanged).
- Run `./gradlew :contracts:check`.

## Definition of Done

`./gradlew :deployable:test --tests '*GuardWiringTest' --tests '*ContainerProperties*'` and `:contracts:check`
pass. No other contract file is touched.
