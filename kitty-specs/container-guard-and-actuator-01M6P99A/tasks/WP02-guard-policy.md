---
work_package_id: WP02
title: Guard policy in the request guard
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-006
- FR-009
- SC-001
- SC-002
- C-001
planning_base_branch: feat/container-guard-and-actuator
merge_target_branch: feat/container-guard-and-actuator
branch_strategy: Planning artifacts for this mission were generated on feat/container-guard-and-actuator. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/container-guard-and-actuator unless the human explicitly redirects the landing branch.
subtasks:
- T005
- T006
- T007
- T008
history: []
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/GuardPolicy.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/GuardPolicyTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/GuardPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/WorktreeGuard.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/GuardPolicyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/WorktreeGuardTest.java
role: implementer
tags: []
tracker_refs: []
---
# WP02: Guard policy in the request guard

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
`spec-kitty agent action implement WP02 --agent claude --mission container-guard-and-actuator-01M6P99A`

## Objective

Teach `WorktreeGuard` to judge the request's host names against a **passed-in** policy, and to let exactly one
raw `GET` path skip the token check (FR-001 to FR-004, FR-006, FR-009). The packs module must not learn about
profiles; the deployable chooses the policy (WP03).

## Subtasks

### T005 — `GuardPolicy` (new, `service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/GuardPolicy.java`)

A final class or record:

- `Set<String> hosts`: lower case (`Locale.ROOT`) and stripped; non-empty.
- `boolean hostHeaderRequired`.
- `Set<String> tokenExemptGets`: each must start with `/`.
- **Factories:**
  - `static GuardPolicy loopback()`: hosts `localhost`, `127.0.0.1`, `[::1]` and `::1` (the bare `::1` is
    today's server-name form); `hostHeaderRequired=false`; no exempt path. This must reproduce today's
    behaviour exactly.
  - `static GuardPolicy named(Collection<String> hosts)`: `hostHeaderRequired=true`; refuses an empty
    collection or a blank entry with `IllegalArgumentException`. The deployable validates the entries first,
    so this is defence in depth.
  - `GuardPolicy withTokenExemptGet(String path)`: returns a copy with the path added.
- **Methods:** `boolean acceptsHost(String name)` (lower case; the caller has already stripped the port) and
  `boolean exemptsFromToken(HttpServletRequest r)`:
  - `"GET".equals(r.getMethod())`;
  - `r.getContextPath().isEmpty()`;
  - `tokenExemptGets.contains(r.getRequestURI())`, with no decoding and no normalization.
- Javadoc in the house style. Every public member is documented (doclint is on), and NullAway applies.

### T006 — `WorktreeGuard`

- Add a constructor
  `WorktreeGuard(@Nullable WorktreeToken token, GuardPolicy policy, ServiceInfoQuery info, Supplier<ObjectMapper> mapper)`.
  Keep the existing three-argument constructor, delegating with `GuardPolicy.loopback()`.
- `fromLoopbackName` becomes `fromAcceptedName`:
  - the server name and every `Host` value (stripped, port removed by the existing `hostName`) must be
    accepted by the policy;
  - when `policy.hostHeaderRequired()` and there is no `Host` header, refuse.
- The filter order stays: the host check, then the browser check (both 403); then, unless
  `policy.exemptsFromToken(request)`, the token check (401).
- Update the class Javadoc: the guard judges against its policy; under the loopback policy it is the
  ADR-0018 D9 check; under a named list it is ADR-0041 D6.

### T007 — Tests

`GuardPolicyTest`:
- the factories;
- refusal of empty and blank entries;
- lower-casing;
- the exempt rule's method, context-path and exact-URI cases.

`WorktreeGuardTest`: add a second guard built with `GuardPolicy.named(List.of("charter.internal")).withTokenExemptGet("/actuator/health")`:
- `charter.internal`, `CHARTER.internal:8080` and `charter.internal:` pass with the token;
- `localhost`, `attacker.example`, `charter.internal.` and `charter.internal.attacker` are 403 even with the
  token;
- no `Host` header is 403;
- a listed host plus `Origin` is 403;
- a server name that is not listed is 403 (keep the server-name rule).

Keep every existing test green, unchanged: that is the FR-003 ratchet.

### T008 — Near-miss exemption tests (FR-009, FR-010, SC-002)

With the named policy and no `Authorization`:
- `GET /actuator/health` passes and the chain runs.
- Each of the following is 401:
  - `HEAD`, `POST`, `PUT` or `DELETE` on `/actuator/health`;
  - `GET` on `/actuator/health/`, `/actuator/health;x=y`, `/actuator/health/liveness`,
    `/actuator/health/../env`, `/actuator/%68ealth`, `/ACTUATOR/health`, `/actuator/healthz`, `/actuator`,
    `/actuator/env` or `//actuator/health`;
  - the right URI with a non-empty context path.

  Set `requestURI` explicitly on the mock.
- `GET /actuator/health` with an unlisted Host, or with `Origin`, is 403.
- With the loopback policy, `GET /actuator/health` without a token is 401.

## Definition of Done

- `./gradlew :packs:test` passes, and so do `spotlessCheck` and `checkstyleMain`/`checkstyleTest` for packs.
- No profile name appears in the packs module.

## Risks

`MockHttpServletRequest` derives `requestURI` from the constructor path; use `setRequestURI` for the
encoded forms.
