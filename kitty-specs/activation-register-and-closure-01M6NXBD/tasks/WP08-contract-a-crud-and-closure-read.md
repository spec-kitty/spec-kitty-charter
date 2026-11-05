---
work_package_id: WP08
title: contract A, activation CRUD and the closure read (scenarios first)
dependencies:
- WP04
- WP07
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-005
- FR-006
- FR-007
- FR-008
- FR-009
- FR-010
- FR-017
- FR-018
- FR-021
- FR-022
- C-001
- NFR-002
- NFR-003
- NFR-007
- SC-001
- SC-002
- SC-004
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
subtasks:
- T047
- T048
- T049
- T050
- T051
- T052
- T053
- T054
- T055
- T056
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/application/
create_intent:
- contracts/charter/paths/activations_activationId_packs.yaml
- contracts/charter/schemas/ActivationPackList.yaml
- contracts/charter/parameters/IfNoneMatch.yaml
- contracts/charter/responses/NotModified.yaml
- contracts/charter/headers/ActivationPackListETag.yaml
- contracts/charter/examples/ActivationPackList.two-packs.yaml
- contracts/charter/changes/2026-11-05-activation-register-and-closure-read.md
- contracts/charter/scenarios/activation-register.feature
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationIdMint.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationServiceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/FakeAvailablePacks.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationMapperTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/packs/PacksAvailabilityTest.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ActivationBodyAdvice.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ActivationProblems.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationBodyAdviceTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationRoundTripPropertyTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ActivationSteps.java
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/openapi.yaml
- contracts/charter/paths/activations.yaml
- contracts/charter/paths/activations_activationId.yaml
- contracts/charter/paths/activations_activationId_packs.yaml
- contracts/charter/paths/activations_activationId_artifacts.yaml
- contracts/charter/paths/activations_activationId_skipped.yaml
- contracts/charter/paths/activations_activationId_graph.yaml
- contracts/charter/paths/activations_activationId_missionTypes_missionType_actions_action_context.yaml
- contracts/charter/paths/activations_activationId_selections_kind_artifactId.yaml
- contracts/charter/paths/activations_activationId_exclusions_kind_artifactId.yaml
- contracts/charter/schemas/Activation.yaml
- contracts/charter/schemas/ActivationConfig.yaml
- contracts/charter/schemas/SelectedArtifacts.yaml
- contracts/charter/schemas/EffectiveArtifactList.yaml
- contracts/charter/schemas/ActivationPackList.yaml
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/schemas/_index.yaml
- contracts/charter/parameters/IfNoneMatch.yaml
- contracts/charter/parameters/_index.yaml
- contracts/charter/responses/NotModified.yaml
- contracts/charter/responses/_index.yaml
- contracts/charter/headers/ActivationPackListETag.yaml
- contracts/charter/headers/_index.yaml
- contracts/charter/examples/Activation.minimal.yaml
- contracts/charter/examples/EffectiveArtifactList.first-page.yaml
- contracts/charter/examples/ActivationPackList.two-packs.yaml
- contracts/charter/examples/_index.yaml
- contracts/charter/changes/2026-11-05-activation-register-and-closure-read.md
- contracts/charter/scenarios/activation-register.feature
- contracts/charter/scenarios/activations.feature
- contracts/charter/scenarios/composition.feature
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationIdMint.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationServiceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/FakeAvailablePacks.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationMapperTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/packs/PacksAvailabilityTest.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ActivationBodyAdvice.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ActivationProblems.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationBodyAdviceTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationRoundTripPropertyTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedRefusalCodesTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ActivationSteps.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/CucumberSpringConfiguration.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ReservedOperationCoverageTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP08: contract A, activation CRUD and the closure read (scenarios first)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Serve the first five activation operations from the contract, red first:

- `createActivation` (`POST`) and `replaceActivation` (`PUT`, also a create with `If-None-Match: *`);
- `getActivation`, with `ETag` and `304`;
- `deleteActivation`;
- the new closure read `listActivationPacks` (`GET /activations/{id}/packs`).

Land **contract A** first:

- the new operation and `ActivationPackList`;
- `summary` moved to `EffectiveArtifactList`, and `governanceFingerprint` out of `Activation`;
- `If-None-Match`/`304` on `getActivation` and the four effective reads;
- the absent-means-none texts;
- `x-reserved` removed from the four CRUD operations;
- the raised flags for `activation_not_found`, `activation_invalid`, `artifact_required` and `artifact_locked`;
- a changelog fragment.

Then write the US1, US2 and US6 scenarios and see them fail, and then build the service, the adapter, the
controller methods and the #125 build notes until they pass. The four effective reads stay `501` (FR-021). The
reserved count goes 13 → 9.

Implementation command:
`spec-kitty agent action implement WP08 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** A. **Concern:** IC-06b. **Contract delta:** `contracts/contract-delta.md` "PR A" is the checklist.
- **Rulings:** ADR-0020 D3 (POST mints; PUT under a client id), D4 (canonical form, revision), D6 (round trip; LWW
  without `If-Match`), D9 and D10 (refusals and their members); ADR-0059 D1 (a `@reserved` pin is not a gate;
  NFR-002), D4 (stale documents: served on read, refused on write), D5 (`Activation`'s ETag is its revision;
  `summary` and `governanceFingerprint` move; `304`). ADR-0059 is on PR #376, a merge-before dependency of PR A.
- **Decisions:** `DM-01M6NXNBA1J808JV4SHT743SCF` (the closure sub-read; the `@WIP` scenario rewritten),
  `DM-01M6NXQJ61D2GSFJFK02XW6F9N` (absent means none).
- **Collisions:** #393 touches `ProblemHandler` (this WP adds `ActivationProblems` beside it in `deployable`, ordered
  before it); #376 must merge first (ADR-0059 links). Rebase in order.
- **Grounding:**
  - The generated `ActivationApi` uses `skipDefaultInterface=true`, so adding `listActivationPacks` to the contract
    **breaks compilation** until `ActivationController` implements it. Land the contract change and the controller
    method in one commit.
  - `OperationCoverageTest` requires every declared `(method, path, 2xx)` of a **non-reserved** operation to be
    exercised by a scenario: `POST 201`, `GET 200`, `PUT 200`, `PUT 201`, `DELETE 204`, `GET …/packs 200`.
  - `ReservedOperationCoverageTest` scans **every** request line of `activations.feature`, not only the `@reserved`
    blocks, and pins `hasSize(13)`.
  - `ReservedRefusalCodesTest` fails when a main class names a code the contract marks `raised: false`, and when a
    `raised: true` code is named by no main class.
  - The existing step vocabulary (`CharterSteps`, `RegistrationSteps`, `SecuritySteps`) already has `I PUT … with
    the body:`, `I PUT … with If-Match … and the body:`, `I {word} … with the header …: …`, `the problem code is …`,
    `the field at … is …` and `the response header … is …`. There is no step for "the project activates …" and none
    for a `PUT` with `If-None-Match` and a body. The `@WIP` scenario's `the offering holds exactly 2 packs` is
    unbound.
  - The scenario context is **one** Spring context for all scenarios (`CucumberSpringConfiguration`). With no
    `ConfigRoot`, WP07's wiring gives the in-memory repository, which must be cleared per scenario.
  - Body guard: `activations.infra.api` may not name `packs.infra.api` or `deployable`, so the activation body advice
    and the activation exception mapping live in `deployable` (WP01 made `JsonBodyGuard` public for this).
    `RefusalHandler.refused(CharterRefusal)` is public and renders a kernel refusal.

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure`.
- Lane worktree from `lanes.json`. It depends on WP04 (snapshot) and WP07 (storage), and transitively WP01, WP05 and
  WP06.
- Rebase onto the lane base, after #376 and #393, before review.

## Out-of-map edits (sequenced by dependency)

- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationController.java` (owner WP05):
  implement the four CRUD methods and `listActivationPacks`; remove their `@ReservedOperation`.
- `service/activations/src/main/java/ai/speckitty/charter/activations/application/AvailablePacks.java` (owner WP05): add
  `ClosureSnapshot snapshot(Optional<List<String>> packIds)`.
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/PacksAvailability.java` (owner
  WP05): implement it over `packs.application.ClosureQueries`.
- `service/activations/src/main/java/ai/speckitty/charter/activations/domain/ClosureSnapshot.java` (owner WP06): add
  any accessor the mapper needs; no shape change without updating `ClosureSnapshots`.

## Subtask Guidance

### Subtask T047: Contract A (contract first, C-001)

**Steps:**
1. **New operation.** Create `paths/activations_activationId_packs.yaml` with `operationId: listActivationPacks`,
   tag `Activation`, the `ActivationId` parameter and the new `IfNoneMatch` parameter. Responses: `200`
   (`ActivationPackList`, with the headers `Charter-Contract-Version`, `Charter-Worktree`,
   `Charter-Source-Fingerprint` and `ETag` from a new `headers/ActivationPackListETag.yaml`, "opaque; covers the
   revision and the pack fingerprint"), `304`, `401`, `403`, `404` (`ActivationNotFound`), `500`
   (`OfferingUnreadable`, which already lists the composition codes) and `default`. Add the path to `openapi.yaml`
   after `/activations/{activationId}`.
2. **`ActivationPackList.yaml`:** `{packs: [OfferingPack], _links}`, closed, required both. The description says
   "in dependency order; items reuse the offering's pack shape". Add an example and register it in
   `schemas/_index.yaml` and `examples/_index.yaml`.
3. **`Activation.yaml`:** remove `governanceFingerprint` and `summary` from `properties` and `required`. Reword the
   description: "the ETag is the quoted revision; the answer's bytes depend only on the stored document (ADR-0059
   D5)". Update `examples/Activation.minimal.yaml`.
4. **`EffectiveArtifactList.yaml`:** add the required `summary` (`$ref: ActivationSummary.yaml`) and update its
   example. These reads stay reserved, so nothing serves it yet.
5. **`If-None-Match`/`304`:** add `parameters/IfNoneMatch.yaml` (the RFC 9110 §13.1.2 weak comparison text, modelled
   on the inline one in `paths/openapi.yaml`) and `responses/NotModified.yaml` (no body; `ETag`, `Charter-*`
   headers). Reference both from `getActivation` and the four effective-read paths. Register them in the `_index.yaml`
   files.
6. **Absent means none:** reword `ActivationConfig` (the three-state paragraph: absent and `[]` both mean none for
   `activatedKinds`, `missionTypes` and each selected kind, distinct on the wire; `packs`: drop "a later pack wins",
   absent means every configured pack), the twelve `SelectedArtifacts` property texts (skill included: "Absent means
   none of the kind; [] means none; a list means exactly those"), and the select, unselect and deactivate
   descriptions in the selections and exclusions path files (text only; their `x-reserved` stays until WP12).
7. **Built:** remove `x-reserved` from `createActivation`, `getActivation`, `replaceActivation` and
   `deleteActivation`, and their "Reserved: it answers 501 …" sentences, and their `501` responses.
   `replaceActivation`: replace the "open question 5" sentence with ADR-0059 D4's write rule.
8. **`CharterRefusalCode.yaml`:** set `raised: true` for `activation_not_found`, `activation_invalid`,
   `artifact_required` and `artifact_locked`, and update the description paragraph that lists the reserved codes
   (lines ~42–45).
9. **Fragment:** `contracts/charter/changes/2026-11-05-activation-register-and-closure-read.md`, one `### Breaking`
   block. `Activation` loses two required members, and a new success status (`304`) appears (ADR-0020 D8). Say
   what core's seam changes. Note `#359`: `PackPin` stays as it is, and the depends-on facts are on the closure read.
10. Validate: `./gradlew :contracts:check :charter-api:compileJava`. Then the expected compile failure in
    `:activations:compileJava` (the missing `listActivationPacks`) is fixed in T052.

### Subtask T048: Red scenarios (US1, US2, US6) and the `@WIP` rewrite

**Steps:**
1. Create `contracts/charter/scenarios/activation-register.feature`, tagged `@contract @activations`, with a
   feature text citing ADR-0020 D3–D6 and ADR-0059 D4–D5. One scenario per acceptance scenario:
   - **US1** AS1–AS9: sorted and deduplicated, `ETag` equals the quoted revision; round trip; stale `If-Match` 412
     and the old revision kept; LWW without `If-Match`; unknown id 422 with the violation pointer; deny on a
     must-have item 409 `artifact_required` with `requiredBy`; empty deny list 422; delete twice 204 then 404;
     `If-None-Match: *` 201, then 412. Add: `GET` with a matching `If-None-Match` answers 304; both validators answer
     400; a body over 1 MiB, an unknown member, and `"selected": {"directive": null}` each answer 422
     `activation_invalid`.
   - **US2** AS1–AS4: the alpha/beta/gamma fixture from `composition.feature`'s `@WIP` scenario; gamma broken
     (an unreadable `pack.yaml`); no `packs` key (every configured pack, offering order); out of range answers 500
     `pack_dependency_out_of_range`.
   - **US6** AS1–AS3: an org pack whose `pack.yaml` names `policy.yaml` locking `directive:D1` (use the existing
     `the org pack "…" with the file "policy.yaml":` step); a built-in pack open and an org pack locked; a named
     policy file that is missing answers `pack_unreadable` on `/offering` **and** on the closure read.
   - Stale document (Edge Cases, FR-017): store a document naming D, delete D's file from the pack (`the file … of the
     pack … is deleted`), `GET` answers 200 with the same document, and `PUT` of the same config answers 422.
2. `composition.feature` (~line 819): remove the "Deferred to #360" comment and `@WIP`, add `And the project
   activates the pack "alpha"`, read `GET "/activations/project/packs"`, and assert beta then alpha and exactly two
   packs.
3. `ActivationSteps.java` (glue, constructor-injected `ScenarioHttp`):
   - `@Given("the project activates the pack(s) {string}")`: comma-separated configured ids;
     `PUT /activations/project` with `{"schemaVersion":1,"packs":[…]}`; assert 201;
   - `@Given("the activation {string} holds:")` with a docstring body: `PUT`, assert 201, remember the `ETag`;
   - `@When("I PUT {string} with the header {string}: {string} and the body:")`;
   - `@Then("the answer holds exactly {int} packs")`;
   - `@Then("the response header {string} is the quoted field {string}")` (`ETag` equals `"<revision>"`).
   Check the existing steps first; reuse rather than duplicate (`ScenarioHttp` holds the last response).
4. `CucumberSpringConfiguration`: a `@Before` hook in `ActivationSteps` (or a scenario-scoped bean) clears the
   `InMemoryActivationRepository` bean so scenarios are independent.
5. Run `./gradlew :deployable:test --tests '*CharterContractSuite*'` and **record the failing scenario names**:
   every new scenario must fail (most with 501, the closure read with 404 or 405) before any service code exists
   (NFR-002).

### Subtask T049: The reserved pins (13 → 9)

**Steps:**
1. In `activations.feature`, delete the four CRUD pins. Move "A reserved operation answers 501 before it reads its
   request" to `selectActivationArtifact`: `PUT "/activations/a-1/selections/directive/DIRECTIVE_001"` with `If-Match`
   and the unbindable body `{"cascade": 5, "includeDeactivated": "no"}`. Update the feature text ("the nine writes by
   #291" becomes "the five per-artifact and preset writes by #291, PR B").
2. `ReservedOperationCoverageTest`: collect request lines **only from `@reserved` blocks** (split the text with
   `RESERVED_TAG`, keep the blocks that contain `Scenario:`, then scan their lines). Rename the count test to
   `theActivationSetReservesNineOperations` and assert `hasSize(9)`.
3. `ReservedRefusalCodesTest`: extend `theContractMarksTheCodesTheWritesAndTheLoadRaiseAsDecided` with the four
   newly raised codes, and keep `ARTIFACT_NOT_FOUND`, `MISSION_TYPE_NOT_FOUND` and `PRESET_NOT_FOUND` in
   `unraisedCodes()`. Also: `HANDLER_CLASS` still names `packs.infra.api.RefusalHandler`; if `ActivationProblems`
   (deployable) maps codes to statuses, it must not name an unraised code.

### Subtask T050: `PacksAvailability` over `ClosureQueries`

**Steps (red first, `PacksAvailabilityTest` with a stubbed `ClosureQueries`, or a real one over the packs test
fakes):**
1. `snapshot(Optional.empty())` passes "every configured pack" and maps `ClosureView` to `ClosureSnapshot`
   field by field: packs with resolved dependencies, held ids per kind, mission types, the floor (`requiredBy`), the
   locks (`lockedBy`: only `LOCKED` levels), the presets and the fingerprint.
2. `snapshot(Optional.of(["alpha"]))` maps configured ids to `PackIdentifier`. An id the catalog does not hold must
   reach the domain as an "unknown pack" fact, not as an exception. Decide the representation: `ClosureQueries`
   throws `IllegalArgumentException` for an unknown root (WP02), so catch that here and return a snapshot flagged
   with `unknownPacks`. `DocumentRules` then reports `/packs/i/packId unknown_pack` (422), and the closure read
   reports 422 too (a document cannot be stored with an unknown pack, so this only happens if the catalog shrank;
   then answer 500 `pack_unreadable`, matching a pack that vanished). Pin both in tests.
3. Composition refusals propagate unchanged (US2 AS4).

### Subtask T051: `ActivationService`

**Purpose:** the application use cases; each takes **one** snapshot (NFR-007).

**Operations (red first, `ActivationServiceTest` with `FakeAvailablePacks` and `InMemoryActivationRepository`):**
- `create(document)`: mint an id (`ActivationIdMint`, an in-house ULID: 48-bit time plus 80 random bits,
  Crockford base32, with a test of the grammar and monotonic time order), check `DocumentRules` against
  `snapshot(document.packs())`, then `repository.create`. Answer `Created(stored)`.
- `put(id, document, ifMatch, ifNoneMatch)`:
  - both present → `400`. Throw an `IllegalArgumentException` type the HTTP edge maps to a plain 400 `Problem`;
    add a dedicated `BothValidatorsException` in the application ring if that is clearer;
  - `If-None-Match: *` → `repository.create`; when the id exists, `PreconditionException(STALE)` (412);
  - `If-Match` present → `replace(id, Optional.of(revision))`; when stale, 412; when missing, 412 (an `If-Match` on
    a resource that does not exist fails, RFC 9110 §13.1.1); `*` matches any existing document (use
    `EntityTags.matches`);
  - neither → `replace(id, Optional.empty())`: 201 when created, 200 when replaced (LWW, ADR-0020 D6, FR-003).
  - The rules are checked **before** the write; nothing is stored on a 409 or 422.
- `get(id, ifNoneMatch)`: `NotModified(revision)` when `EntityTags.noneMatch` holds; otherwise `Found(stored)`;
  `404 activation_not_found` when missing. No snapshot is taken: the stored document is served unchanged (FR-017).
- `delete(id, ifMatch)`: `Deleted`; 404 when missing; 412 when stale.
- `packs(id, ifNoneMatch)`: load the document (404 if missing), take `snapshot(document.packs())`, and answer the
  packs with an `ETag` of `sha256(revision + "\n" + fingerprint)`, quoted. Answer `304` on a match.

Keep each method short: extract `checked(document)` (snapshot plus rules) and `preconditionOf(ifMatch, ifNoneMatch)`.

### Subtask T052: The controller and the mapper

**Steps (red first, `ActivationMapperTest`):**
1. `ActivationMapper`: `ActivationConfig` DTO → domain builder (preserving absent versus `[]`: the generated DTO
   leaves an absent list `null`, per `ActivationJson`'s Javadoc) → `Built(document, violations)`; domain → DTO for the
   answer (canonical form); `ClosureSnapshot.packs` → `OfferingPack` and `ResolvedDependency`, with `_links` built
   as `OfferingController` builds them (look at `Hypermedia` in packs: activations may not name it, so build the
   same links locally and pin their equality in a scenario).
2. `ActivationController` (out-of-map): implement `createActivation` (`201`, `Location`, `ETag`), `getActivation`
   (`200` or `304`, `ETag`, `Charter-Source-Fingerprint` only if the contract keeps it on 200: check after T047),
   `replaceActivation` (`200` or `201`), `deleteActivation` (`204`) and `listActivationPacks`. Remove each one's
   `@ReservedOperation`; the four effective reads and the five writes keep theirs.
3. Map `PreconditionException` and the domain `CharterRefusal`s by throwing them; `RefusalHandler` (global advice)
   renders them. `ActivationProblems` handles the rest (T053).

### Subtask T053: The #125 build notes (body guard and 422 mapping)

**Steps (red first, `ActivationBodyAdviceTest`):**
1. `deployable/ActivationBodyAdvice` (`@ControllerAdvice`, a `RequestBodyAdviceAdapter`) supports `ActivationConfig`
   (and, for PR B, `SelectionRequest`, `ExclusionRequest` and `PresetApplication`, which bodies cannot reach until
   those operations are built; list them now so PR B changes nothing here). For each body:
   - over `BodyLimit.MAX_BYTES`: 422 `activation_invalid`, with a violation at `""`, code `body_too_large`;
   - a `null` list or item (`JsonBodyGuard.firstNull` over `selected.*`, `deactivated.*`, `packs`, `activatedKinds`,
     `missionTypes` and `languages`): 422, with the pointer;
   - an unknown member anywhere: read the bytes with a strict `JsonMapper` (`FAIL_ON_UNKNOWN_PROPERTIES` on) into the
     DTO class; on an `UnrecognizedPropertyException`, 422 with the pointer built from the exception's path,
     code `unknown_member`. Never drop it silently (ADR-0020 build notes).
   Throw `CharterRefusal.withViolations(ACTIVATION_INVALID, …)`.
2. `deployable/ActivationProblems` (`@RestControllerAdvice(assignableTypes = ActivationController.class)`, ordered
   before `ProblemHandler`): `HttpMessageNotReadableException` (an unbindable body) and
   `MethodArgumentNotValidException` / `HandlerMethodValidationException` (bean validation) → 422
   `activation_invalid` with violations, rendered by delegating to `RefusalHandler.refused(...)`. A non-JSON body stays
   415 (the contract's `UnsupportedMediaType`).
3. **Path kinds:** the `kind` path parameter of PR B's operations is a `String` in the generated API; parsing it with
   `ArtifactKind.fromToken` (snake case, as the contract enums spell it) is enough, so no Spring `Converter` is needed
   unless the generator emits an enum. Check the generated `ActivationApi` signature and record the finding.

### Subtask T054: Round-trip property test (NFR-003)

**Steps:** `ActivationRoundTripPropertyTest` in `deployable` (MockMvc, the in-memory repository, a fixture pack set
holding a few ids of every activatable kind):
1. Generate at least 1,000 valid documents with a seeded `java.util.Random` (print the seed on failure): random
   subsets per kind, random absent versus `[]`, random duplicate and unsorted lists, `packs` absent or present,
   non-empty deny lists that avoid the floor.
2. For each: `PUT` creates it; `GET` it; `PUT` the fetched `config` with its `If-Match`; assert `200` and the same
   `ETag`, 100%.
3. No new property-testing library (DIRECTIVE_051): a plain loop with a fixed seed set.

### Subtask T055: The closure read and US6 end to end

**Steps:**
1. Run the US2 and US6 scenarios until green. The `pack_unreadable` case for a missing policy file proves WP03's
   fail-closed path through the HTTP surface.
2. The closure read's `ETag`: a second `GET` with that `If-None-Match` answers 304; after the document changes, 200.

### Subtask T056: All green, and evidence

**Steps:** run the gates; then fill in the review notes with the red-run scenario list from T048, the green run,
the reserved count 9, and the property test count and seed.

## Checkstyle limits (NFR-001)

CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1, by construction. `ActivationService.put` and the mapper are
the hotspots; split them. No new suppression, and no disabled test. No scenario tagged `@WIP` is added: every new
scenario runs.

## Gate commands

```bash
./gradlew :contracts:check :charter-api:compileJava
./gradlew :activations:check
./gradlew :deployable:test --tests '*CharterContractSuite*' --tests '*ReservedOperationCoverageTest*' \
  --tests '*ReservedRefusalCodesTest*' --tests '*OperationCoverageTest*' --tests '*ActivationBodyAdviceTest*' \
  --tests '*ActivationRoundTripPropertyTest*' --tests '*HexagonalArchitectureTest*' --tests '*NullOmissionTest*' \
  --tests '*ContractDescriptionsTest*' --tests '*OpenApiDocumentTest*'
./gradlew :packs:test --tests '*ActivationConfigSchemaParityTest*'
```

## Definition of Done

- [ ] The contract carries every PR A item of `contract-delta.md`, with one fragment; `:contracts:check` is green.
- [ ] `activation-register.feature` holds a scenario for every US1, US2 and US6 acceptance scenario, plus the edge
      cases above. Each was seen failing before the code (names recorded) and passes now.
- [ ] The `@WIP` composition scenario runs against `GET /activations/{id}/packs` and passes.
- [ ] `ReservedOperationCoverageTest` counts only `@reserved` blocks and asserts 9; the binding pin is on
      `selectActivationArtifact`.
- [ ] `ReservedRefusalCodesTest` is green with the four codes raised.
- [ ] `ActivationService` implements create, put (both modes), get (304), delete and packs, with one snapshot per
      request.
- [ ] Unknown members, `null`s, oversized bodies and bean-validation failures answer 422 `activation_invalid` with
      violations.
- [ ] The property test passes over at least 1,000 documents.
- [ ] No new suppression, no disabled test, and no `@WIP`.

## Risks

- **The contract change breaks the build until the controller implements the new method.** Commit them together.
- **`ActivationConfigSchemaParityTest`** validates a corpus against the canonical JSON schema
  (`contracts/offering/schemas/ActivationConfig.schema.json`) and its OpenAPI mirror. Description-only edits should
  not break it. If it compares descriptions, mirror the wording, and record that file as an out-of-map edit in the
  review notes.
- **Shared scenario context.** A missing per-scenario clear lets one scenario's activation leak into another; the
  clear hook is mandatory.
- **ADR-0059 not on the base.** Links to `/adr/0059-…` fail the docs link check until #376 merges; the contract
  descriptions cite it by number only.

## Reviewer guidance (reviewer-renata)

- **Red first is checkable:** demand the failing scenario names from T048's run (501s and 404s), with the commit
  that added the feature file **before** the commit that added `ActivationService`.
- **Count:** `ReservedOperationCoverageTest` asserts 9, and its scan is restricted to `@reserved` blocks. Plant a
  non-reserved request line in a scratch copy of `activations.feature` to see it is ignored.
- **The precondition matrix:** check the tests for neither, `If-Match` fresh, stale and `*`, `If-None-Match: *` on
  absent and present, and both (400).
- **Nothing stored on refusal:** the 409 and 422 scenarios must `GET` afterwards and see 404 or the old revision.
- **Non-fakeable evidence:** the `CharterContractSuite` summary (scenario counts before and after), the
  `OperationCoverageTest` output, and the property test's iteration count and seed.
