---
work_package_id: WP04
title: The worktree guard and refresh
dependencies:
- WP03
requirement_refs:
- FR-011
- FR-012
- NFR-001
- NFR-004
- NFR-005
- C-003
- C-005
planning_base_branch: feat/action-context-reserved-surface
merge_target_branch: feat/action-context-reserved-surface
branch_strategy: Planning artifacts for this mission were generated on feat/action-context-reserved-surface. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/action-context-reserved-surface unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-action-context-reserved-surface-01M5AFEK
base_commit: 2544540a0317efba1b754fc7013f3c7f17b56592
created_at: '2026-10-19T17:58:53.273430+00:00'
subtasks:
- T017
- T018
- T019
- T020
- T021
phase: Phase 2 - The reserved surface
history:
- at: '2026-10-19T17:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/adapter-in-rest/src/main/java/ai/speckitty/charter/rest/WorktreeGuard.java
create_intent:
- contracts/charter/responses/Unauthorized.yaml
- contracts/charter/responses/Forbidden.yaml
- contracts/charter/paths/refresh.yaml
- contracts/charter/scenarios/security.feature
- contracts/charter/scenarios/refresh.feature
- service/adapter-in-rest/src/main/java/ai/speckitty/charter/rest/WorktreeGuard.java
- service/adapter-in-rest/src/main/java/ai/speckitty/charter/rest/WorktreeToken.java
- service/adapter-in-rest/src/main/java/ai/speckitty/charter/rest/RefreshController.java
- service/adapter-in-rest/src/test/java/ai/speckitty/charter/rest/WorktreeGuardTest.java
- service/adapter-in-rest/src/test/java/ai/speckitty/charter/rest/WorktreeTokenTest.java
- service/charter-service/src/main/java/ai/speckitty/charter/service/GuardWiring.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/GuardWiringTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/SecuritySteps.java
execution_mode: code_change
model: sonnet
owned_files:
- contracts/charter/openapi.yaml
- contracts/charter/responses/Unauthorized.yaml
- contracts/charter/responses/Forbidden.yaml
- contracts/charter/responses/_index.yaml
- contracts/charter/paths/refresh.yaml
- contracts/charter/paths/info.yaml
- contracts/charter/paths/packs.yaml
- contracts/charter/scenarios/security.feature
- contracts/charter/scenarios/refresh.feature
- contracts/lint/**
- service/adapter-in-rest/src/main/java/ai/speckitty/charter/rest/WorktreeGuard.java
- service/adapter-in-rest/src/main/java/ai/speckitty/charter/rest/WorktreeToken.java
- service/adapter-in-rest/src/main/java/ai/speckitty/charter/rest/RefreshController.java
- service/adapter-in-rest/src/test/java/ai/speckitty/charter/rest/WorktreeGuardTest.java
- service/adapter-in-rest/src/test/java/ai/speckitty/charter/rest/WorktreeTokenTest.java
- service/charter-service/build.gradle.kts
- service/charter-service/src/main/java/ai/speckitty/charter/service/GuardWiring.java
- service/charter-service/src/main/resources/**
- service/charter-service/src/test/resources/**
- service/charter-service/src/test/java/ai/speckitty/charter/service/GuardWiringTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/ProductionWiringTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/ProblemResponsesTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/ConnectorErrorsTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/NullOmissionTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/SecuritySteps.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP04: The worktree guard and refresh

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ IMPORTANT: Review Feedback

- **Has review feedback?**: check the `review_ref` field in the event log (`spec-kitty agent tasks status`).
- Address every feedback item, and record each with `spec-kitty agent tasks add-history WP04 --note "..."`.

## Review Feedback

*[Filled from the status event log when this WP returns from review.]*

---

## Objectives & Success Criteria

Issue #113, decisions D9 and D10 (`research.md` §3), with plan-point findings S1 to S3, S8 to
S10 and A7 to A8 (§8).

### Contract

- `components.securitySchemes.charterWorktreeToken` (`type: http`, `scheme: bearer`), declared
  as root `security`, required on **every** operation, `/info` included.
- **401** (`responses/Unauthorized.yaml`): a missing, malformed or wrong token.
  - Shared `Problem` body, no `code`.
  - Declares `WWW-Authenticate` (`Bearer`), `Charter-Contract-Version` and `Charter-Worktree`.
  - Declares `Charter-Source-Fingerprint`, as lint requires, described as never present on
    this answer.
- **403** (`responses/Forbidden.yaml`): a browser request, meaning an `Origin`, `Sec-Fetch-Site`
  or `Sec-Fetch-Mode` header, or a host name that is not `localhost`, `127.0.0.1` or `[::1]`
  (`::1`). Same body and headers as the 401, without `WWW-Authenticate`.
- Both responses are referenced from every operation, the context path included. That path is
  WP01's file; the edit is out-of-map and recorded.
- **`POST /packs/refresh`** (`paths/packs_refresh.yaml`; **operator ruling 2026-10-19**: refresh lives in the packs domain):
  - `operationId: refreshPacks`, tag Packs.
  - It re-reads every configured pack and the project configuration, and discards any built read model. It answers 200 with the refreshed `PackListing` and one verified fingerprint header (not `ServiceInfo`).
  - **No request body.** A non-empty body is a 400 `Problem` (the request is rejected before
    routing), so a later optional paths hint is a clean addition.
  - 200 `ServiceInfo` with the verified fingerprint.
  - 500 `OfferingUnreadable`, which covers `stale_input` and the pack codes.
  - 401 and 403 as above.
  - Idempotent.

### Service

- **`WorktreeGuard`:** a servlet filter registered at `Ordered.HIGHEST_PRECEDENCE` for every
  REQUEST dispatch, ahead of everything that reads a file. In order:
  1. a non-loopback host name → 403;
  2. any `Origin`, `Sec-Fetch-Site` or `Sec-Fetch-Mode` header → 403;
  3. no token configured (only possible with the development flag) → pass;
  4. a missing, malformed or wrong `Authorization: Bearer` → 401.

  The guard writes the version and worktree headers and the `Problem` body itself, and never
  calls the chain on a refusal. Unknown paths therefore answer 401 before 404.
- **The token** comes from `charter.worktree-token`, bound from the environment
  (`CHARTER_WORKTREE_TOKEN`).
  - **Startup is refused when:**
    - the token is blank or shorter than 32 characters;
    - the token comes from the command line (`commandLineArgs`) or a system property
      (`systemProperties`);
    - there is no token, unless `charter.insecure-no-token=true`. That flag logs a WARN at
      every start.
  - It is held in `WorktreeToken`: redacted `toString`, and a compare of the SHA-256 digests
    with `MessageDigest.isEqual`.
  - The `Bearer` scheme is case-insensitive. Anything else is malformed → 401.
  - The token never appears in a log line or a response.
- **The packs controller** implements the generated `refreshPacks`. It reads through `ConsistentLoad`, as `GET /packs` does. Older text below said `RefreshController` and `ServiceInfoQuery`; the operator ruling supersedes it.
  which WP02 made two-digest, and sets the header as `InfoController` does.

**Done when:**

- `security.feature` and `refresh.feature` were seen failing on assertions, then pass;
- `GuardWiringTest` proves each startup refusal;
- every existing scenario sends the token;
- `verifyAll` is green.

## Context & Constraints

**Read:**

- `spec.md`: User Story 3, FR-011, FR-012, NFR-004;
- `research.md`: D9, D10, and §8 rows S1 to S3, S8 to S11, A7, A8;
- ADR-0011 (the trust model).

**Constraints:**

- **No identity claim:** the token proves the client, not the server. Do not describe `/info` as
  an identity proof.
- **Scenarios use MockMvc.** The request's server name is `localhost`, so the host check passes
  by default. A scenario sets `Host: attacker.example` to get the 403.
- **Lint:**
  - `charter-headers-on-every-response` needs all three headers declared on 401 and 403.
  - `error-responses-are-problems` makes 200 the only success.
  - Adding a rule fixture under `contracts/lint/fixtures` may be needed if `components` or
    `security` trips a rule. Check `contracts/lint/README.md`.
  - `contract-and-dtos.md:104` says `openapi.yaml` holds only info, servers, tags and paths.
    WP05 updates that page. Mention it in history.
- **The test resources copy** (`charter-service/build.gradle.kts:28-36`) must include any new
  contract directory. `responses/` and `paths/` are already copied.
- **`ContractConformance`** hardcodes the method `"get"` (`CharterSteps:66`). Pass the real
  method.
- **Expected out-of-map edits** (record each):
  - the context path file (401/403 refs);
  - `CucumberSpringConfiguration` (a token property for the suite);
  - `service.feature` and other features, only if a scenario must change because of the guard.
- **Runner B boundary:** none touched. **CHANGELOG:** not edited.

## Branch Strategy

- **Planning base branch**: `feat/action-context-reserved-surface`
- **Merge target branch**: `feat/action-context-reserved-surface`

```bash
spec-kitty agent action implement WP04 --agent claude --mission action-context-reserved-surface-01M5AFEK
```

**Environment:** `JAVA_HOME=/opt/jdk-25`; open-packs at `/home/user/spec-kitty-open-packs`.

## Subtasks & Detailed Guidance

### Subtask T017: Red security scenarios

**`contracts/charter/scenarios/security.feature`** (`@contract`). Exact bodies throughout:

1. no `Authorization` → 401, `WWW-Authenticate: Bearer`, no fingerprint header,
   `Charter-Worktree` present;
2. a wrong token → 401;
3. a malformed header (`Basic …`, or `Bearer` with no value) → 401;
4. `bearer <right token>`, lower case → 200;
5. the right token with `Origin: https://evil.example` → 403;
6. the right token with `Sec-Fetch-Site: cross-site` → 403;
7. the right token with `Host: attacker.example` → 403 (DNS rebinding);
8. no token on an unknown path → 401, not 404;
9. no token on `/info` → 401.

**`SecuritySteps`** (new glue):

- `When I GET {string} without the worktree token`;
- `When I GET {string} with the header {string}: {string}`;
- `When I GET {string} with the Authorization {string}`.

**Red run:** before T019, every scenario that expects 401 or 403 gets 200. Record it and the
scenario count. Commit.

### Subtask T018: The contract security surface

1. **`openapi.yaml`:** `components: securitySchemes: charterWorktreeToken: {type: http, scheme: bearer, description: …}`,
   and root `security: [{charterWorktreeToken: []}]`. The description says:
   - one token per worktree;
   - set by the launcher (#70);
   - passed in the environment;
   - never in a URL;
   - **container-level 400s** (a malformed request the connector rejects before routing,
     `ProblemErrorReportValve`) answer before the guard and carry no Charter headers (S9).
2. **`responses/Unauthorized.yaml` and `responses/Forbidden.yaml`:** `application/problem+json`
   with `$ref: ../../_shared/schemas/Problem.yaml`, and the headers listed above. Add both to
   `responses/_index.yaml`.
3. **Every operation** (`paths/info.yaml`, `paths/packs.yaml`, the context path, refresh)
   references both under `'401'` and `'403'`.
4. **The `CharterRefusalCode` dispatch paragraph** already says a body without a code is a
   transport failure. Make sure 401 and 403 fall under it. That file is WP03's: edit it only if
   the sentence does not already cover them, and record the edit.
5. **Checks:** `./gradlew :contracts:check`. Fix lint by following the conventions, never by
   disabling a rule.

### Subtask T019: `WorktreeGuard` and the startup refusals

**`WorktreeToken`** (adapter-in-rest):

- holds the SHA-256 digest of the token, not the token;
- `matches(String presented)` hashes the presented value and uses `MessageDigest.isEqual`;
- `toString()` returns `WorktreeToken[redacted]`.

**`WorktreeGuard extends OncePerRequestFilter`:** the order above.

- Its 401 and 403 bodies are the generated `Problem` DTO, serialized with the application
  context's Jackson 3 `JsonMapper`, so the omit-nulls mixin (`ProblemJson`) applies. There is
  no ready-made servlet writer: `Problems.of` returns a `ResponseEntity`. Check how
  `ProblemErrorController` builds a code-less `Problem`. Set
  `Content-Type: application/problem+json`.
- It sets `Charter-Contract-Version` (`ContractVersion.value()`) and `Charter-Worktree`
  (`ServiceInfoQuery.worktree()`, which needs no file read).
- It never calls `info.answer()`.

**`GuardWiring`** (charter-service):

- binds `charter.worktree-token` and `charter.insecure-no-token`;
- checks the property source origin through `ConfigurableEnvironment.getPropertySources()`:
  if `commandLineArgs` or `systemProperties` contains `charter.worktree-token`, fail;
- applies the length rule;
- registers the guard as a `FilterRegistrationBean` with `HIGHEST_PRECEDENCE` and the
  `REQUEST` dispatcher type, so a direct `GET /error` is guarded too;
- logs WARN at start when insecure.

**Unit tests:**

- `WorktreeGuardTest`: every branch;
- `WorktreeTokenTest`: matches, mismatches, different lengths, redaction;
- `GuardWiringTest`, with `ApplicationContextRunner` (one test per refusal):
  - blank token → fails;
  - short token → fails;
  - token from a system property → fails;
  - no token without the flag → fails;
  - the flag → starts with a warning.

### Subtask T020: Steps and the suite token

- **The suite:** `CucumberSpringConfiguration` (out-of-map) sets `charter.worktree-token` to a
  fixed 40-character test token through `@SpringBootTest(properties = …)`. Test properties are
  neither `commandLineArgs` nor `systemProperties`. Check that the source check allows them.
- **`ScenarioHttp`** (WP02's, out-of-map): every request sends `Authorization: Bearer <token>`
  unless a `SecuritySteps` step says otherwise. `ContractConformance` already takes the method
  (WP02).
- **`scenarios/README.md`** (out-of-map): list the new steps.
- **The other Spring tests** (`ProductionWiringTest`, `ProblemResponsesTest`,
  `ConnectorErrorsTest`, `NullOmissionTest`): set the token property, or send the header where
  they make requests. `ProductionWiringTest` must still prove production wiring. Give it the
  token through a property, not the insecure flag.
- **Connector-level errors** (`ProblemErrorReportValve`) answer before any filter. Document that
  in the guard's Javadoc. WP05 puts it in the contract text.

### Subtask T021: Refresh

**Red first:** `refresh.feature` (`@contract`), in which `POST /packs/refresh` with the token (answering the `PackListing`, per the operator ruling):

- answers 200 with the `ServiceInfo` body;
- carries the same fingerprint as `/info`;
- after a pack file changes, answers the new fingerprint;
- again, answers the same one (idempotent);
- with a body → 400 Problem;
- without a token → 401.

`CharterSteps` (WP02's, out-of-map) gains `When I POST {string}` and
`When I POST {string} with the body:`, through `ScenarioHttp`. The red
run expects 404 or 405.

**Then:**

- `paths/refresh.yaml` and `openapi.yaml`'s path map;
- the generated interface method;
- `RefreshController` implements it. It reads the `ServiceInfoQuery` and returns the same body
  and header as `/info`. A non-empty body is rejected with 400: check `Content-Length` or read
  the input stream; never parse it.

**Javadoc:** this version keeps no built graph, so refresh recomputes and verifies the info.
Once the service keeps one (#73), refresh rebuilds it.

**Final run:**

```bash
./gradlew verifyAll -PopenPacksDir=/home/user/spec-kitty-open-packs
```

## Red-first evidence (required)

- the T017 and T021 red runs (assertion failures, scenario counts);
- the green run;
- the commit hashes.

## Definition of Done

- [ ] The guard runs first and digests nothing on a refusal; 401 comes before 404.
- [ ] Every startup refusal is proven; the token is never logged; the compare is constant-time.
- [ ] Refresh exists with its refusals; a body → 400.
- [ ] Every existing scenario passes with the token.
- [ ] `verifyAll` green; out-of-map edits recorded; CHANGELOG untouched.

## Risks

- **Filter order with `CharterHeaders`:** the guard sets its own headers. Make sure a refused
  request does not also get `CharterHeaders`'s headers twice. The guard does not call the
  chain.
- **Spring Boot 4 package moves:** `FilterRegistrationBean`, `ApplicationContextRunner`. Find
  them in the catalog's version.
- **MockMvc** and `Host`: set it with `.header("Host", …)`. Check `request.getServerName()` reads
  it in MockMvc; if not, use `.with(r -> { r.setServerName(…); return r; })`.

## Reviewer guidance

Check that:

- there is no fail-open path;
- the token cannot leak through `toString`, logs or headers;
- the host and `Sec-Fetch` checks are always on;
- 401 and 403 carry no fingerprint;
- refresh takes no body;
- every red run is an assertion failure.
