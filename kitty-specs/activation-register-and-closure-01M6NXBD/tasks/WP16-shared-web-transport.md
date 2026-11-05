---
work_package_id: WP16
title: Shared web transport module and bounded architecture
dependencies:
- WP09
- WP10
requirement_refs:
- C-002
- C-005
- FR-006
- NFR-001
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
subtasks:
- T097
- T098
- T099
- T100
- T101
- T102
- T103
- T104
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: service/web-transport/
create_intent:
- service/web-transport/build.gradle.kts
- service/web-transport/src/main/java/ai/speckitty/charter/web/package-info.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/Problems.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/ProblemHandler.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/RefusalHandler.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/JsonBodyGuard.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/ProblemJson.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/ProblemRuntimeHints.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/config/package-info.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/config/WebTransportConfiguration.java
- service/web-transport/src/test/java/ai/speckitty/charter/web/ProblemsTest.java
- service/web-transport/src/test/java/ai/speckitty/charter/web/RefusalHandlerTest.java
- service/web-transport/src/test/java/ai/speckitty/charter/web/RefusalHandlerLoggingTest.java
- service/web-transport/src/test/java/ai/speckitty/charter/web/JsonBodyGuardTest.java
- service/web-transport/src/test/java/ai/speckitty/charter/web/ProblemRuntimeHintsTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactJson.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationBodyAdvice.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationProblems.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationBodyAdviceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationProblemsTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/transportfixture/package-info.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/transportfixture/RootNamesGeneratedDto.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/domain/badfixture/DomainNamesKernelTransport.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/application/badfixture/ApplicationNamesWebTransport.java
- service/deployable/src/test/java/ai/speckitty/charter/web/badfixture/package-info.java
- service/deployable/src/test/java/ai/speckitty/charter/web/badfixture/WebNamesASlice.java
execution_mode: code_change
model: ''
owned_files:
- service/web-transport/build.gradle.kts
- service/web-transport/src/main/java/ai/speckitty/charter/web/package-info.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/Problems.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/ProblemHandler.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/RefusalHandler.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/JsonBodyGuard.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/ProblemJson.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/ProblemRuntimeHints.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/config/package-info.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/config/WebTransportConfiguration.java
- service/web-transport/src/test/java/ai/speckitty/charter/web/ProblemsTest.java
- service/web-transport/src/test/java/ai/speckitty/charter/web/RefusalHandlerTest.java
- service/web-transport/src/test/java/ai/speckitty/charter/web/RefusalHandlerLoggingTest.java
- service/web-transport/src/test/java/ai/speckitty/charter/web/JsonBodyGuardTest.java
- service/web-transport/src/test/java/ai/speckitty/charter/web/ProblemRuntimeHintsTest.java
- service/packs/build.gradle.kts
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/Problems.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ProblemHandler.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ProblemJson.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ProblemRuntimeHints.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactJson.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ProblemsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ProblemRuntimeHintsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RefusalHandlerLoggingTest.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationBodyAdvice.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationProblems.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationBodyAdviceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationProblemsTest.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/CharterRuntimeHints.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactCursor.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ConnectorProblems.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/DirectiveController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/OfferingController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ParadigmController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ProblemErrorController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ProblemErrorReportValve.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RelationshipController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/StyleguideController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ToolguideController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/WorktreeGuard.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ArtifactControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/DirectiveControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/OfferingControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ParadigmControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RelationshipControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/StyleguideControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ToolguideControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/WorktreeGuardTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/transportfixture/package-info.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/transportfixture/RootNamesGeneratedDto.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/domain/badfixture/DomainNamesKernelTransport.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/application/badfixture/ApplicationNamesWebTransport.java
- service/deployable/src/test/java/ai/speckitty/charter/web/badfixture/package-info.java
- service/deployable/src/test/java/ai/speckitty/charter/web/badfixture/WebNamesASlice.java
role: implementer
tags: []
tracker_refs: []
---

# WP16: Shared web transport module and bounded architecture

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Fix the post-PR-A squad's MAJOR finding (architect-alphonso: "transport owned by packs"). The service-wide Spring and
Jackson transport still lives in `packs.infra.api`. Because activations may not name it, the activation body advice
and the problem mapping sit in `deployable`, which `TRANSPORT_STAYS_IN_ADAPTER` then has to exempt.

This WP moves the shared transport into a new Gradle module, `service/web-transport` (package
`ai.speckitty.charter.web`), that `packs.infra.api` and `activations.infra.api` both depend on:

- `RefusalHandler`, `ProblemHandler`, `Problems`, `JsonBodyGuard`, and the `Problem` part of `ProblemJson` and
  `ProblemRuntimeHints` move into the new module;
- `deployable`'s `ActivationBodyAdvice` and `ActivationProblems` move into `activations.infra.api`;
- `TRANSPORT_STAYS_IN_ADAPTER` loses its `DEPLOYABLE` exemption;
- the ArchUnit gate gains two rules: the web ring is an adapter ring that no domain or application ring may depend on,
  and no `..domain..` class depends on `kernel.transport..`.

This WP is **behaviour-preserving**. Every problem body and status stays byte-identical: `ProblemResponsesTest`,
`NullOmissionTest`, `CharterContractSuite` and `ReservedRefusalCodesTest` stay green, with no expected value changed.

Implementation command:
`spec-kitty agent action implement WP16 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** A. It is added after the post-PR-A squad ran on lane-i (WP01–WP09 combined). PR A is WP01–WP10 plus WP16
  and WP17.
- **Squad finding:** alphonso MAJOR (transport), and the "kernel.transport rule" MINOR.
- **Rulings:** ADR-0006 (onion slices; adapters composed only by the root), C-002.
- **Collisions:** rebase over #377, #379, #384, #390, #391 (settings and conventions), #392, #393 (`ProblemHandler`,
  draft) and #394 (mutation floors: the new module may need a floor). If #393 lands first, move its `ProblemHandler`
  change along with the file.
- **Grounding (lane-i, `.worktrees/activation-register-and-closure-01M6NXBD-lane-i`):**
  - `service/deployable/src/main/java/ai/speckitty/charter/deployable/ActivationBodyAdvice.java` imports
    `packs.infra.api.JsonBodyGuard`, `kernel.transport.BodyLimit` and the generated request DTOs.
  - `ActivationProblems.java` (same directory) imports `packs.infra.api.RefusalHandler`,
    `activations.infra.api.ActivationController`, `activations.application.MalformedPreconditionException` and the
    generated `CharterRefusal`/`Problem`.
  - `packs/infra/api/RefusalHandler.java` is a `@RestControllerAdvice @Order(HIGHEST_PRECEDENCE)` that imports only
    generated DTOs, `kernel.domain.RefusalCode` and `kernel.transport.PreconditionException`. It moves cleanly.
  - `ProblemHandler` (`@RestControllerAdvice @Order(LOWEST_PRECEDENCE)`) and `Problems` (package-private
    `final class`, `of(status, headers)` and `title(status)`) are used by:
    - `RefusalHandler`, `ProblemErrorController`, `ProblemErrorReportValve` and `ConnectorProblems`;
    - `WorktreeGuard` and the controllers.

    Make `Problems` public in its new home.
  - `ProblemJson` registers mix-ins for `Link`, `Artifact` and `ReferencedArtifact` and imports `ProblemRuntimeHints`.
    `Artifact` and `ReferencedArtifact` are packs DTOs. Split it: the `Problem`/`CharterRefusal`/`Link` part moves to
    the web module, and the artifact part stays in packs as `ArtifactJson`. `ActivationJson` (WP05) also mixes in
    `Link`. Keep exactly **one** `Link` mix-in (the web one), and drop the duplicate from `ActivationJson`.
  - `HexagonalArchitectureTest` (lane-i):
    - line ~638: `TRANSPORT_STAYS_IN_ADAPTER` with `.resideOutsideOfPackages(GENERATED_API, SLICE_APIS, DEPLOYABLE)`;
    - line ~570: `NON_SLICE_OWNERS = Set.of("kernel", "api", "deployable")`. `web` must be added, or
      `EVERY_SLICE_IS_REGISTERED` fails;
    - the existing fixtures `packs.application.badfixture.UsesTransportDto` and
      `activations.application.badfixture.ApplicationNamesGeneratedApi`.
  - `ReservedRefusalCodesTest` names the handler by string (`HANDLER_CLASS = "ai.speckitty.charter.packs.infra.api.RefusalHandler"`).

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure` (PR A).
- Lane worktree from `lanes.json`. It depends on WP09 and WP10 (WP10 owns the architecture doc it edits out of map), and is merged before WP17 and PR B's write WPs (WP13 depends
  on WP17).
- Rebase onto the lane base before review.

## Out-of-map edits (sequenced by dependency)

- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RefusalHandler.java` and `JsonBodyGuard.java`
  (owner WP01): delete them; they move to `ai.speckitty.charter.web`.
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RegistrationBodyAdvice.java`,
  `ToolguideRegistrationBodyAdvice.java` and `RegistrationLists.java` (owner WP01): imports only.
- `service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RefusalHandlerTest.java` (owner WP01): delete; it
  moves to the web module's tests.
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java` (owner WP01): the
  rules in T102.
- `settings.gradle.kts`, `service/charter-reports/build.gradle.kts`, `.github/workflows/sonar.yml`,
  `service/activations/build.gradle.kts` and `service/deployable/build.gradle.kts` (owner WP05): the new module.
- `service/deployable/src/main/java/ai/speckitty/charter/deployable/DeployableConfiguration.java` (owner WP05): scan
  `ai.speckitty.charter.web.config`.
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationJson.java` (owner WP05): drop
  the duplicate `Link` mix-in.
- `service/deployable/src/main/java/ai/speckitty/charter/deployable/ActivationBodyAdvice.java`,
  `ActivationProblems.java` and `service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationBodyAdviceTest.java`
  (owner WP09): delete them; they move to `activations.infra.api`.
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedRefusalCodesTest.java` (owner WP09):
  `HANDLER_CLASS` names `ai.speckitty.charter.web.RefusalHandler`.
- `docs/architecture/offering-and-activation.md` (owner WP10, approved): the slice flowchart and the mapping table
  show the new shared `web-transport` module (`ai.speckitty.charter.web`). Both inbound adapters, `packs.infra.api`
  and `activations.infra.api`, depend on it, and `deployable` no longer holds activation transport. Rationale: WP10
  documented the layout before the squad moved the transport; the doc must match the code PR A ships (T104 step 4).
- `docs/architecture/hexagonal-service.md` (owner WP05): describe the web ring as a recorded decision in that doc (no
  new ADR):
  - `ai.speckitty.charter.web`, an adapter ring shared by the slices' `infra.api`;
  - no domain or application ring may depend on it, and `WEB_NAMES_NO_SLICE` holds;
  - no `..domain..` depends on `kernel.transport..`;
  - `TRANSPORT_STAYS_IN_ADAPTER` has dropped its `DEPLOYABLE` exemption.

  Rationale: WP05 documented the cross-slice rule before the squad moved the transport (T104 step 5).
- `service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RegistrationListsTest.java` and
  `ToolguideRegistrationBodyAdviceTest.java` (owner WP01): imports only.
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationController.java` (owner WP05):
  imports only, if it names a moved class.
- The other `packs.infra.api` users of the moved classes (the controllers, `ArtifactCursor`, `ConnectorProblems`,
  `ProblemErrorController`, `ProblemErrorReportValve`, `WorktreeGuard` and their tests) were owned by no WP before.
  This WP now owns them (see `owned_files`), and changes them by **imports only**. List each one in the evidence.

## Subtask Guidance

### Subtask T097: The `web-transport` module

**Steps:**
1. Create `service/web-transport/build.gradle.kts`, modelled on `service/packs/build.gradle.kts`:
   - plugins `charter.spring-conventions` and `charter.hand-written`;
   - `api(project(":kernel"))` and `api(project(":charter-api"))`;
   - `implementation(catalog.lib("spring-boot-starter-webmvc"))` and `implementation(catalog.lib("jackson-databind"))`;
   - tests: `spring-boot-starter-test` and `archunit`.

   No new library (DIRECTIVE_051). Add a header comment: the service-wide HTTP transport (problem rendering, refusal
   mapping, body guards), shared by every slice's inbound adapter. It names no slice.
2. Register the module:
   - `settings.gradle.kts`: add `"web-transport"` after `"charter-api"`;
   - `charter-reports/build.gradle.kts` `serviceModules`: add `"web-transport" to "ai.speckitty.charter.web"` (its
     `projectsEvaluated` guard fails otherwise);
   - `.github/workflows/sonar.yml`: add the four `sonar.*` lists;
   - `packs/build.gradle.kts` and `activations/build.gradle.kts`: `implementation(project(":web-transport"))`;
   - `deployable/build.gradle.kts`: the same.
3. Add `web/package-info.java` (`@NullMarked`), and `web/config/WebTransportConfiguration`, a
   `@Configuration @ComponentScan("ai.speckitty.charter.web")`. Add its package to `DeployableConfiguration`'s scan.

**Validation:** `./gradlew :web-transport:check :charter-reports:check`.

### Subtask T098: Move `Problems`, `ProblemHandler` and the `Problem` JSON

**Steps (red first: move the tests, and see them fail to compile until the classes move):**
1. Move `ProblemsTest`, `ProblemRuntimeHintsTest` and `RefusalHandlerLoggingTest` to
   `service/web-transport/src/test/java/ai/speckitty/charter/web/`, changing only their package and imports.
2. Move `Problems` (now `public final`), `ProblemHandler` and `ProblemRuntimeHints` to `web`.
3. Split `ProblemJson`. The web copy registers `Problem`, `CharterRefusal` and `Link` and carries
   `@ImportRuntimeHints(ProblemRuntimeHints.class)`. `packs/infra/api/ArtifactJson.java` registers `Artifact` and
   `ReferencedArtifact`, with the same `OmitAbsentMembers` style. Delete the packs `ProblemJson`.
4. Remove the `Link` mix-in from `ActivationJson`; the web module registers it once. Run
   `ActivationRuntimeHintsTest`. Its `Link` assertion moves to `ProblemRuntimeHintsTest` if the hint now lives there.
5. Update the importers listed in the out-of-map section (imports only).

**Validation:** `./gradlew :web-transport:test :packs:test --tests '*Problem*' :deployable:test --tests '*ProblemResponsesTest*' --tests '*NullOmissionTest*' --tests '*CharterRuntimeHintsTest*'`.

### Subtask T099: Move `RefusalHandler` and `JsonBodyGuard`

**Steps:**
1. Move `RefusalHandlerTest` and the generic `JsonBodyGuard` cases (from `RegistrationListsTest`, or a new
   `JsonBodyGuardTest`) to the web module's tests first.
2. Move `RefusalHandler` and `JsonBodyGuard` (with `NullValue` and `BodyFault`) to `ai.speckitty.charter.web`.
   Keep the `@Order` values exactly. `ActivationProblems` must still run before `ProblemHandler`.
3. `ReservedRefusalCodesTest`: `HANDLER_CLASS` becomes `"ai.speckitty.charter.web.RefusalHandler"`; `MAPPINGS` is
   unchanged.
4. `CharterRuntimeHints` (deployable) adds the web hints, if it lists registrars explicitly.

### Subtask T100: Move the activation body advice and problem mapping into the slice

**Steps:**
1. Move `ActivationBodyAdviceTest` to `activations/src/test/.../infra/api/`. Add `ActivationProblemsTest` there
   (MockMvc standalone over `ActivationController` and its advices), pinning the 422, 400 and 415 answers that WP09's
   scenarios already cover, so the move is proven at unit level too.
2. Move `ActivationBodyAdvice` and `ActivationProblems` from `deployable` to
   `activations/infra/api/`. Their imports become `ai.speckitty.charter.web.JsonBodyGuard`,
   `ai.speckitty.charter.web.RefusalHandler` and same-slice types. No `packs` import remains.
3. `ActivationApiConfiguration` scans `activations.infra.api`, so both advices are picked up with no wiring change.
   Confirm `ActivationProblems` keeps `assignableTypes = ActivationController.class` and its order.

### Subtask T101: Behaviour preservation

**Steps:** run the whole BDD suite and the problem tests, and diff no expected value:

```bash
./gradlew :deployable:test --tests '*CharterContractSuite*' --tests '*ProblemResponsesTest*' --tests '*NullOmissionTest*' \
  --tests '*ReservedRefusalCodesTest*' --tests '*ConnectorErrorsTest*' --tests '*ActivationBodyAdvice*' \
  --tests '*ActivationConditionalRequestTest*' --tests '*ActivationRoundTripPropertyTest*'
```

`git diff` on `contracts/` and on every scenario file must be empty.

### Subtask T102: The architecture rules

**Steps (red first: the fixtures of T103 must fail the new rules):**
1. `TRANSPORT_STAYS_IN_ADAPTER`: `.resideOutsideOfPackages(GENERATED_API, SLICE_APIS, WEB)` with
   `WEB = ROOT + ".web.."`, and **no `DEPLOYABLE`**. Update its Javadoc: the composition root no longer names
   transport types.
2. `NON_SLICE_OWNERS` gains `"web"`.
3. `WEB_IS_AN_ADAPTER_RING`: no class in `..domain..` or `..application..` depends on `ai.speckitty.charter.web..`.
4. `WEB_NAMES_NO_SLICE`: no class in `web..` depends on `packs..`, `activations..`, `profiles..` or `missions..`.
5. `ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS`: allow every slice's `infra..` to name `web..`. The web ring is shared,
   not composed.
6. `DOMAIN_NAMES_NO_TRANSPORT`: no class in `..domain..` (the kernel's `kernel.domain` included) depends on
   `ai.speckitty.charter.kernel.transport..` (alphonso MINOR). Check today's code first. If a domain class names
   `PreconditionException` or `EntityTags`, move that use to the application ring in this WP, and list it.
7. **Preservation (P4):** every existing rule and `assertBites` stays verbatim except
   `TRANSPORT_STAYS_IN_ADAPTER`'s exemption list.

### Subtask T103: Bad fixtures

**Steps:** one class each, the smallest that breaks exactly one rule, each with an `assertBites`:
- `deployable/transportfixture/RootNamesGeneratedDto`: a class in the composition root's package that names a
  generated DTO. It proves the removed exemption bites. The existing `seamfixture` and `unmarkedfixture` show the
  pattern;
- `activations/domain/badfixture/DomainNamesKernelTransport`: names `kernel.transport.EntityTags`;
- `activations/application/badfixture/ApplicationNamesWebTransport`: names `web.Problems`;
- `web/badfixture/WebNamesASlice`: names a `packs.domain` type.

`UsesTransportDto` and `ApplicationNamesGeneratedApi` must still bite.

### Subtask T104: Evidence and gates

**Steps:**
1. List every moved file (old path → new path), and every file changed by imports only.
2. `git grep -n "packs.infra.api.RefusalHandler\|packs.infra.api.JsonBodyGuard\|deployable.Activation\(BodyAdvice\|Problems\)"`
   returns nothing.
3. Grep `service/deployable/src/main/java` for `ai.speckitty.charter.api` imports: none remain.
4. Update `docs/architecture/offering-and-activation.md` (out-of-map, owner WP10):
   - add `web-transport` to the slice flowchart, as a node that both `infra.api` adapters point to;
   - add a row to the mapping table: module `web-transport`, package `ai.speckitty.charter.web`, holding the problem
     and refusal rendering and the body guard;
   - move `ActivationBodyAdvice` and `ActivationProblems` from `deployable` to `activations.infra.api`.

   Run `python3 scripts/docs/check_docs.py all` and the spelling check.
5. Update `docs/architecture/hexagonal-service.md` (out-of-map, owner WP05). Add a dated "Decision (2026-11-0x, PR A
   squad)" subsection under "The rule":
   - the web ring;
   - `WEB_NAMES_NO_SLICE`;
   - no domain on `kernel.transport`;
   - the dropped `DEPLOYABLE` exemption.

   No new ADR.

## Checkstyle limits (NFR-001)

The limits are still off on `main`, held until the methods over them are refactored, so they are held by
construction: the reviewer measures new and changed methods by hand (CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if
depth ≤ 1). A move must not grow a method. No new suppression, and no disabled test (C-005).

## Gate commands

```bash
./gradlew :web-transport:check :charter-reports:check
./gradlew :packs:check -x openPacksRoundTrip
./gradlew :activations:check
./gradlew :deployable:test --tests '*HexagonalArchitectureTest*' --tests '*CharterContractSuite*' \
  --tests '*ProblemResponsesTest*' --tests '*NullOmissionTest*' --tests '*ReservedRefusalCodesTest*' \
  --tests '*CharterRuntimeHintsTest*' --tests '*ProductionWiringTest*'
```

## Definition of Done

- [ ] `service/web-transport` exists and is registered in settings, charter-reports, `sonar.yml`, and the packs,
      activations and deployable builds.
- [ ] `RefusalHandler`, `ProblemHandler`, `Problems`, `JsonBodyGuard` and the `Problem` JSON and hints live in
      `ai.speckitty.charter.web`; packs keeps only `ArtifactJson`; `Link` is mixed in once.
- [ ] `ActivationBodyAdvice` and `ActivationProblems` live in `activations.infra.api`; `deployable` main names no
      generated type.
- [ ] `TRANSPORT_STAYS_IN_ADAPTER` has no `DEPLOYABLE` exemption; the three new rules exist; each bites on its fixture.
- [ ] Every problem body and status is unchanged: the listed suites are green with no expected value edited.
- [ ] No new suppression, and no disabled test.

## Risks

- **Advice ordering:** moving `@RestControllerAdvice` classes between modules can change bean order where `@Order` is
  absent. Keep the explicit orders, and assert `ActivationProblems` beats `ProblemHandler` in `ActivationProblemsTest`.
- **The native image:** the hints registrars move. `CharterRuntimeHintsTest` and CI's native build are the guard.
- **#393 and #394:** rebase churn on `ProblemHandler`, and a mutation floor for the new module.

## Reviewer guidance (reviewer-renata)

- **Behaviour preserved:** `git diff` on `contracts/` and on the test expectations is empty; only package and import
  lines change in the moved tests.
- **The exemption is gone:** read `TRANSPORT_STAYS_IN_ADAPTER`, and check that `RootNamesGeneratedDto` bites.
- **No slice in web:** `git grep -n "charter.packs\|charter.activations" service/web-transport/src/main` is empty.
- **Non-fakeable evidence:**
  - the moved-file table;
  - the `CharterContractSuite` summary (the same counts as before);
  - the ArchUnit report listing the new rules and fixtures.
