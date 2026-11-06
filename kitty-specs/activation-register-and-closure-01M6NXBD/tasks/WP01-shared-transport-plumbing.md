---
work_package_id: WP01
title: Shared transport plumbing (reserved marker, preconditions, refusal body, body guard)
dependencies: []
requirement_refs:
- C-002
- C-005
- FR-006
- FR-007
- NFR-001
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-activation-register-and-closure-01M6NXBD
base_commit: 4223221b50e247c5442a78fbf22de270060bbe32
created_at: '2026-11-05T19:20:15.079490+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
- T007
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/transport/
create_intent:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/transport/package-info.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/transport/ReservedOperation.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/transport/PreconditionException.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/transport/EntityTags.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/transport/BodyLimit.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalViolation.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RequiringPack.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/transport/EntityTagsTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/CharterRefusalTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/JsonBodyGuard.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ReservedOperationInterceptor.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedOperationInterceptorTest.java
execution_mode: code_change
model: ''
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/transport/package-info.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/transport/ReservedOperation.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/transport/PreconditionException.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/transport/EntityTags.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/transport/BodyLimit.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/CharterRefusal.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalViolation.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RequiringPack.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/transport/EntityTagsTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/CharterRefusalTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PreconditionException.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRevision.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackWriteStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/DirectiveRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ParadigmRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/StyleguideRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ToolguideRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackCommitStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ReservedOperation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ReservedOperationInterceptor.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ActivationController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RefusalHandler.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RegistrationBodyAdvice.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ToolguideRegistrationBodyAdvice.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RegistrationLists.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/JsonBodyGuard.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRevisionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/DirectiveRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ParadigmRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RelationshipRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/StyleguideRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ToolguideRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackCommitStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/LinkedPackCommitTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/BinaryPayloadRevisionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RefusalHandlerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RegistrationListsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RegistrationBodyAdviceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ToolguideRegistrationBodyAdviceTest.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ReservedOperationInterceptor.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedOperationInterceptorTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java
- service/deployable/src/test/java/ai/speckitty/charter/packs/infra/api/badfixture/ReservedReachesApplication.java
role: implementer
tags: []
tracker_refs: []
---

# WP01: Shared transport plumbing (reserved marker, preconditions, refusal body, body guard)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Move the HTTP plumbing that `packs` owns today to homes a second HTTP slice (`activations`, WP05) can reach,
**without changing any behaviour**:

- `@ReservedOperation` moves to `kernel` and becomes method- and type-level;
- `ReservedOperationInterceptor` moves to `deployable` and decides per handler **method** (method annotation first,
  then the bean type), so one controller can serve some operations and reserve others;
- `PreconditionException` and the strong `If-Match` comparison (`PackRevision.matches`) move to `kernel`, with every
  `packs` caller updated;
- the kernel `CharterRefusal` gains the two optional body members the contract already declares
  (`violations`, `requiredBy`), and `RefusalHandler` renders them;
- the 1 MiB / null-list body guard (`RegistrationBodyAdvice`, `RegistrationLists.BODY_LIMIT`) is split so a later
  slice's advice can apply it.

Every existing test stays green. No `@contract` scenario changes. This is PR A's first WP (IC-01).

Implementation command:
`spec-kitty agent action implement WP01 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** A (WP01–WP10). **Concern:** IC-01 in `plan.md`. **Research:** R9, squad findings A4, A7, B4.
- **Requirements:** C-002 (slices reach shared plumbing, not each other), FR-006 (the body part: 1 MiB, `null`
  list or item), FR-007 (the `requiredBy` member the 409 refusals need), C-005, NFR-001.
- **Rulings:** ADR-0020 D8 (reserved operations answer `501` before binding), ADR-0020 D10 (`violations`,
  `requiredBy` on the refusal body), ADR-0026 D5 (412/428 are plain `Problem`s, never refusal codes).
- **Collisions to rebase over:** the base is `main`, which now carries PR #368 and PR #376 (ADR-0059). PR #393 edits
  `ProblemHandler` (this WP does not touch it, but `RefusalHandler` sits beside it; rebase if #393 lands first).
  PR #377 (test-tidy-mockito-refusal-codes, ready) edits `PackCommitStore`, the registration service tests and the
  refusal-code tests. **Gate (P3):** start this WP only after #377 merges, or after rebasing over #377's head. Edits
  to `packs` files in this WP are limited to imports and call-site changes (the `PackRevision.matches` delegation
  and the `RefusalHandler` rendering are the only body changes). Other open PRs to rebase over: #379, #384, #390,
  #391, #392 (ADR-0061), #393 (draft) and #394 (draft, mutation floors per module).
- **Grounding (read before coding):**
  - `service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ReservedOperation.java` is `@Target(TYPE)`.
  - `ReservedOperationInterceptor.preHandle` checks `method.getBeanType().isAnnotationPresent(...)` only.
  - `ActivationController` calls `ReservedOperationInterceptor.notBuilt()` in every method body. A controller can
    never name `deployable` (`ONION_NOTHING_DEPENDS_ON_COMPOSITION_ROOT`), so after the move each body throws a
    local `new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED)` (Spring is allowed in `infra.api`).
  - `PreconditionException` is in `packs.application` and is mapped by `RefusalHandler.precondition`.
  - `PackRevision.matches(String, PackRevision)` is the one strong-comparison parser; its string core is generic.
  - The kernel is pure Java (`charter.domain-conventions`, `KERNEL_IS_PURE`): no Jackson, no Spring. The token-stream
    null scan in `RegistrationLists` needs Jackson, so it **cannot** move to the kernel; only the limit constant can.
  - `activations.infra.api` will not be allowed to name `packs.infra.api` (`ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS`)
    nor `deployable`. The activation body advice therefore lives in `deployable` (WP09) and calls the public
    `JsonBodyGuard` this WP extracts.

## Branch Strategy

- Planning branch and final merge target: `issue-360-activated-closure` (PR A's branch, rebased onto `main`).
- Worktrees are per computed lane from `lanes.json` (written by finalize-tasks). This WP has no dependency and
  starts from the lane base.
- Rebase onto the current lane base before review, and over #377's head if it has not merged (P3).
- **Evidence:** list every `packs` file this WP touched in the review notes, each marked "import only", "call site"
  or "body", so the reviewer can check the P3 limit.

## Subtask Guidance

### Subtask T001: `@ReservedOperation` in the kernel, method- and type-level

**Purpose:** a marker any slice's controller can use on a whole class or on single methods.

**Steps (red first):**
1. Create `service/kernel/src/main/java/ai/speckitty/charter/kernel/transport/package-info.java` (`@NullMarked`,
   same style as `kernel/domain/package-info.java`). Javadoc: framework-free transport vocabulary shared by every
   slice's inbound adapter; nothing here imports Spring or Jackson.
2. Create `kernel/transport/ReservedOperation.java`: `@Documented @Retention(RUNTIME)
   @Target({ElementType.TYPE, ElementType.METHOD})`. Move the Javadoc from the packs annotation and add: "on a
   method, only that operation is reserved; on a type, every handler method of it".
3. Delete `service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ReservedOperation.java`.
4. Update `packs/infra/api/ActivationController.java` to import the kernel annotation. Keep it on the class: all
   13 operations stay reserved here. WP05 moves the controller and switches to method-level marks.

**Validation:** `./gradlew :kernel:check :packs:compileJava`. `ActivationController` still compiles, and the
`activations.feature` pins still answer 501 after T003.

**Edge cases:** an annotation on an interface default method is not seen by `HandlerMethod.getMethodAnnotation`
unless the controller overrides it. The generated `ActivationApi` uses `skipDefaultInterface=true`, so every
operation is overridden; keep it that way.

### Subtask T002: `PreconditionException` and strong `If-Match` parsing in the kernel

**Purpose:** one parser of entity-tags for both slices (squad B4: do not rebuild it).

**Steps (red first):**
1. Write `service/kernel/src/test/java/ai/speckitty/charter/kernel/transport/EntityTagsTest.java` first. Port every
   `matches` case from `PackRevisionTest` (search it for `matches(`) to string tokens:
   - `*` matches;
   - a list matches when one strong member equals the token;
   - `W/"…"` never matches;
   - an unquoted member compares as bare text;
   - an unterminated quote matches nothing;
   - `null` matches nothing.
   Add cases for `EntityTags.quote(token)` (`"sha256:…"` with quotes) and `EntityTags.noneMatch(ifNoneMatch, token)`
   (the weak comparison RFC 9110 §13.1.2 uses for `If-None-Match`: `W/"x"` matches `x`, `*` matches any existing
   resource). WP09 uses `noneMatch` for `304`.
2. Create `kernel/transport/EntityTags.java`: a final utility class with `matches(@Nullable String ifMatch, String
   currentToken)`, `noneMatch(@Nullable String ifNoneMatch, String currentToken)`, `isWildcard(@Nullable String)`
   and `quote(String)`. Move the loop out of `PackRevision.matches`, and split it into small private helpers
   (`nextMember`, `opaqueOf`) so that each method stays within the limits (see "Checkstyle limits").
3. `PackRevision.matches(ifMatch, current)` becomes a one-line delegation to `EntityTags.matches(ifMatch,
   current.token())`. Keep its signature; the registration services call it.
4. Move `PreconditionException` (with `Kind.STALE`, `Kind.MISSING`) to `kernel/transport/`, unchanged in meaning.
   Delete `packs/application/PreconditionException.java`.
5. Update the imports in `PackWriteStore`, the five `*RegistrationService` classes, `PackCommitStore` and
   `RefusalHandler`, and in the tests listed in `owned_files` that import it.

**Validation:** `./gradlew :kernel:check :packs:test --tests '*Registration*' --tests '*PackRevision*'
--tests '*PackCommitStore*' --tests '*LinkedPackCommit*' --tests '*BinaryPayloadRevision*'`.

**Edge cases:** `PackRevisionTest` keeps its digest and domain-separation tests. Only the `matches` cases move, and
one delegation test stays to prove the wiring.

### Subtask T003: `ReservedOperationInterceptor` in `deployable`, method-aware

**Purpose:** answer `501` per operation, not per controller.

**Steps (red first):**
1. Write `service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedOperationInterceptorTest.java`:
   - a `HandlerMethod` on a class marked `@ReservedOperation` answers 501 (throws a `ResponseStatusException` with
     501);
   - an unmarked class whose one method is marked: that method answers 501 and the other passes (`true`);
   - an unmarked class with no marked method passes;
   - a non-`HandlerMethod` handler (a resource handler) passes.
   Use small nested fixture controllers in the test; do not boot Spring.
2. Create `deployable/ReservedOperationInterceptor.java`, a `@Component` `HandlerInterceptor` and `WebMvcConfigurer`,
   with the Javadoc moved from packs. `preHandle` reads `method.hasMethodAnnotation(ReservedOperation.class)`, then
   `method.getBeanType().isAnnotationPresent(...)`. Keep `notBuilt()` as a package-level helper inside `deployable`
   for the interceptor's own use.
3. Delete `packs/infra/api/ReservedOperationInterceptor.java`. In `packs/infra/api/ActivationController.java`,
   replace each `throw ReservedOperationInterceptor.notBuilt();` with a private static `notBuilt()` that returns
   `new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED)`.
4. The interceptor is found by component scan: `CharterServiceApplication` lives in `ai.speckitty.charter.deployable`,
   so its default scan includes the new class. Confirm it with the BDD suite.

**Validation:** `./gradlew :deployable:test --tests '*ReservedOperationInterceptorTest*' --tests
'*CharterContractSuite*'`. All 15 `@reserved` scenarios (13 operations; `selectActivationArtifact` is pinned twice, for an artifact and a mission type; plus the binding pin) of `activations.feature` still answer 501, including the
"answers 501 before it reads its request" pin.

**Edge cases:** the interceptor must run before argument resolution. `preHandle` does, so keep it a
`HandlerInterceptor`, not an AOP advice.

### Subtask T004: Refusal body members `violations` and `requiredBy`

**Purpose:** the kernel refusal carries what `422 activation_invalid` and `409 artifact_required` /
`artifact_locked` need (ADR-0020 D10). `contracts/_shared/schemas/Problem.yaml` already declares both members.

**Steps (red first):**
1. `kernel/domain/CharterRefusalTest.java`: a refusal built with violations and requiring packs exposes them as
   unmodifiable lists; the existing constructors give empty lists.
2. Create `kernel/domain/RefusalViolation.java`, a record `(String pointer, String code, String detail)`, and
   `RequiringPack.java`, a record matching `contracts/_shared/schemas/RequiringPack.yaml` (read the schema for the
   member names; do not guess). Validate non-null; `code` matches `^[a-z][a-z0-9_]*$`.
3. `CharterRefusal`: add `violations()` and `requiredBy()`, with a builder-style factory such as
   `CharterRefusal.withViolations(code, detail, List<RefusalViolation>)` and `withRequiredBy(...)`. Keep every
   existing constructor; keep `serialVersionUID` handling as it is.
4. `RefusalHandlerTest` first: a refusal with violations renders `violations` on the problem body; one without
   omits the member (not `[]`). Then `RefusalHandler.respond` maps both onto the generated DTO, as it maps `packs`
   today (lines ~154–170).
   **Name clash (J8):** the generated model package has `RequiringPack` and `RefusalViolation` DTOs too, and
   `RefusalHandler` already writes `ai.speckitty.charter.kernel.domain.CharterRefusal` fully qualified against the
   generated `CharterRefusal`. Follow the same rule: import the generated DTOs, and write the kernel records fully
   qualified (or the reverse, consistently). Never import both simple names; add a test that renders each member,
   so a wrong type fails to compile or fails the test.
5. `RefusalHandler.precondition` now takes the kernel `PreconditionException`.

**Validation:** `./gradlew :kernel:check :packs:test --tests '*RefusalHandler*'`, then
`:deployable:test --tests '*ProblemResponsesTest*' --tests '*NullOmissionTest*'`.

**Edge cases:** the NON_NULL omission of the problem body must hold for the new members (an empty list is left out),
or `NullOmissionTest`/`ProblemResponsesTest` will catch a `[]`.

### Subtask T005: Share the body guard

**Purpose:** a later slice can apply the 1 MiB and null-list guard without copying it (#323, ADR-0020 build notes).

**Steps (red first):**
1. Create `kernel/transport/BodyLimit.java`: `public static final long MAX_BYTES = 1024L * 1024;` with the
   rationale moved from `RegistrationLists.BODY_LIMIT`. `RegistrationLists.BODY_LIMIT` becomes
   `BodyLimit.MAX_BYTES` (or is removed and its callers use the kernel constant).
2. Extract the slice-independent half of `RegistrationLists` into a **public** `packs/infra/api/JsonBodyGuard.java`
   with a typed API (J1):
   - `readBounded(HttpInputMessage, long limit, Function<BodyFault, ? extends RuntimeException> refusal)` reads at
     most one byte past the limit. On overflow it throws what the factory builds from a `BodyFault.TooLarge(limit)`;
   - `firstNull(byte[] body, MediaType, List<String> lists)` returns `Optional<NullValue>`. **`NullValue` is public**,
     with public `pointer()` (an RFC 6901 pointer) and `detail()`;
   - `replay(HttpHeaders, byte[])`.

   `BodyFault` is a small sealed type (`TooLarge`, `NullFound(NullValue)`) that a caller maps to its own refusal: the
   registration advices to `VALIDATION_FAILED` with today's detail texts (unchanged); WP09's activation advice to
   `ACTIVATION_INVALID` with a `RefusalViolation` (`body_too_large`, `null_value`). Keep the charset rule
   (`DETECTED`) and its comment with the code that reads tokens. `RegistrationLists` keeps only the DTO-specific part
   (`of(Class<?>)`) and delegates.
3. Move the matching cases of `RegistrationListsTest` to target `JsonBodyGuard` where they test the generic part.
   `RegistrationBodyAdviceTest` and `ToolguideRegistrationBodyAdviceTest` must pass unchanged in behaviour.
4. Do **not** add the activation advice here. WP09 adds `ActivationBodyAdvice` in `deployable`, which may name
   `packs.infra.api` (the composition root) and calls `JsonBodyGuard`. Note this in `JsonBodyGuard`'s Javadoc.

**Validation:** `./gradlew :packs:test --tests '*RegistrationLists*' --tests '*RegistrationBodyAdvice*'
--tests '*ToolguideRegistrationBodyAdvice*'`, then `:deployable:test --tests '*CharterContractSuite*'` (the
registration scenarios with oversized and null bodies are the end-to-end proof).

**Edge cases:** the refusal code stays the caller's choice. `JsonBodyGuard` returns the detail and the advice
throws `VALIDATION_FAILED` for registrations; WP09's advice throws `ACTIVATION_INVALID`.

### Subtask T006: Architecture test follows the moves

**Purpose:** keep `HexagonalArchitectureTest` green and biting after the moves. This is not yet the WP05
generalisation.

**Steps:**
1. In `HexagonalArchitectureTest`, `RESERVED_CONTROLLER_READS_NOTHING` imports the kernel `ReservedOperation`. The
   "allowed" set becomes the kernel marker plus `org.springframework..` (the local `ResponseStatusException`); the
   interceptor is no longer named by the controller.
2. Update `packs/infra/api/badfixture/ReservedReachesApplication.java` to the kernel annotation, and keep its
   `assertBites` passing.
3. Confirm `ONION_NOTHING_DEPENDS_ON_COMPOSITION_ROOT` still passes: no main class outside `deployable` names
   `deployable.ReservedOperationInterceptor`.

**Validation:** `./gradlew :deployable:test --tests '*HexagonalArchitectureTest*'`.

### Subtask T007: No-behaviour-change evidence and gates

**Steps:**
1. Run the module gates listed below, all green.
2. `git grep -n "packs.infra.api.ReservedOperation\|packs.application.PreconditionException"` returns nothing.
3. Record in the review notes: the 15 `@reserved` scenarios (13 operations; `selectActivationArtifact` is pinned twice, for an artifact and a mission type; plus the binding pin) pass, and the registration 412/428 scenarios in
   `directives.feature` and `relationships.feature` pass unchanged.

## Checkstyle limits (NFR-001)

Each new or changed method stays within: cyclomatic complexity ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1.
The limits are still off in `config/checkstyle/checkstyle.xml` on `main` (held until the methods over them are
refactored), so hold to them by construction; the reviewer measures new and changed methods by hand. Moving `PackRevision.matches` is the place this bites: split it into helpers. No new
entry in `config/checkstyle/suppressions.xml`, no `@SuppressWarnings` added to dodge Error Prone or NullAway, and no
`@Disabled` test (C-005).

## Gate commands (module-scoped; not `verifyAll`)

```bash
./gradlew :kernel:check
./gradlew :packs:check -x openPacksRoundTrip    # add openPacksRoundTrip if the open-packs checkout is present
./gradlew :deployable:test --tests '*HexagonalArchitectureTest*' --tests '*ReservedOperationInterceptorTest*'
./gradlew :deployable:test --tests '*CharterContractSuite*' --tests '*ReservedOperationCoverageTest*' \
  --tests '*ReservedRefusalCodesTest*' --tests '*ProblemResponsesTest*' --tests '*NullOmissionTest*'
```

## Definition of Done

- [ ] `kernel.transport` holds `ReservedOperation` (TYPE and METHOD), `PreconditionException`, `EntityTags` and
      `BodyLimit`; the packs copies are deleted.
- [ ] `deployable.ReservedOperationInterceptor` decides per method, and its unit test covers the four cases.
- [ ] `PackRevision.matches` delegates to `EntityTags.matches`; `EntityTagsTest` holds every former `matches` case.
- [ ] `CharterRefusal` carries `violations` and `requiredBy`; `RefusalHandler` renders them and omits them when
      empty.
- [ ] `JsonBodyGuard` is public and generic; the registration advices use it; the behaviour is unchanged.
- [ ] Every gate command above is green; the reserved count is still 13 and all 15 pins (13 operations, `selectActivationArtifact` pinned twice, plus the binding pin) pass.
- [ ] No new suppression, no disabled test.

## Risks

- **A silent 501 regression.** If the interceptor is not picked up, the reserved pins answer 400 or 500. The BDD
  suite catches it; run it, not only the unit test.
- **The `If-Match` parser drifts.** The registration 412 scenarios are the end-to-end check. Run them.
- **Rebase churn with #377** on `PackCommitStore`: an import-only change; resolve by keeping #377's body.

## Reviewer guidance (reviewer-renata)

- **Verify the method-level path is real.** The interceptor test must include a class with **no** type annotation
  and one marked method. A test that only covers the type-level path proves nothing new.
- **Non-fakeable evidence:**
  - `git show --stat` shows the two packs files deleted and the kernel and deployable files added, not copied;
  - the full `CharterContractSuite` run log, with the 15 reserved scenarios green;
  - `EntityTagsTest` case names mapping one-to-one to the removed `PackRevisionTest` cases.
- **Check `RefusalHandler` omits empty lists:** read `NullOmissionTest`'s output; an empty `violations: []` on a
  `404` is a bug.
- **Check that nothing outside `deployable` names `deployable`** (the ArchUnit onion rule), and that the kernel has no
  Spring or Jackson import (`KERNEL_IS_PURE`).
