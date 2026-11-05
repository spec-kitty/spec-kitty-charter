---
work_package_id: WP09
title: activation CRUD service, controller, body advice and the closure read (green)
dependencies:
- WP08
- WP04
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-005
- FR-006
- FR-007
- FR-009
- FR-010
- FR-017
- FR-022
- NFR-001
- NFR-003
- NFR-007
- SC-001
- SC-002
- SC-004
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-activation-register-and-closure-01M6NXBD
base_commit: 57723c3e9a3191286f1f7585fa4f4e28faec773b
created_at: '2026-11-05T21:17:28.420002+00:00'
subtasks:
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
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationConditionalRequestTest.java
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/schemas/CharterRefusalCode.yaml
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
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationConditionalRequestTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedRefusalCodesTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP09: activation CRUD service, controller, body advice and the closure read (green)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Turn WP08's red scenarios green. This WP builds:

- the `PacksAvailability` adapter over `ClosureQueries`;
- `ActivationService`: create, put, get with `304`, delete, and the closure read;
- the controller methods and the mapper;
- the #125 build notes: the activation body advice and the 422 mapping, both in `deployable`;
- the round-trip property test (NFR-003).

This WP also sets the raised flags of `activation_not_found`, `activation_invalid`, `artifact_required` and
`artifact_locked`, together with the code that raises them. It is PR A's **last code WP**; PR B's first code WP
(WP11) depends on it.

Implementation command:
`spec-kitty agent action implement WP09 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** A. **Concern:** IC-06b (service half).
- **Rulings:** ADR-0020 D3, D4, D6, D9 and D10; ADR-0059 D4 and D5 (on `main`).
- **Requirements:** FR-001 to FR-007, FR-009, FR-010, FR-017, FR-022 (end to end), NFR-003, NFR-007, SC-001, SC-002,
  SC-004.
- **Collisions:** rebase over #393 (`ProblemHandler`, draft). `ActivationProblems` sits beside it and is ordered before
  it. Rebase over #394 (mutation floors) if it lands; see WP05's risk.
- **Grounding:**
  - `ReservedRefusalCodesTest` fails when a main class names a code the contract marks `raised: false`, and when a
    `raised: true` code is named by no main class. Flip each flag in the same commit as the code that raises it.
  - `RefusalHandler.refused(CharterRefusal)` is public and renders a kernel refusal, with `violations` and
    `requiredBy` since WP01.
  - `activations.infra.api` may not name `packs.infra.api` or `deployable`. The body advice and the exception
    mapping therefore live in `deployable`, and use WP01's public `JsonBodyGuard` and WP05's widened
    `TRANSPORT_STAYS_IN_ADAPTER` rule.
  - Spring's `HttpEntityMethodProcessor` answers `304` by itself when a `ResponseEntity` carries an `ETag` and the
    request's `If-None-Match` matches, with a **weak** comparison, for GET and HEAD. The controller's own `304` logic
    and Spring's must agree (J10).

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure` (PR A).
- Lane worktree from `lanes.json`. It depends on WP08 (contract and scenarios) and WP04 (snapshot); WP07 and WP06
  come in transitively.
- Rebase onto the lane base before review.

## Out-of-map edits (sequenced by dependency)

- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationController.java` (owner WP05):
  implement the four CRUD methods and `listActivationPacks`, and remove their `@ReservedOperation`.
- `service/activations/src/main/java/ai/speckitty/charter/activations/application/AvailablePacks.java` (owner WP05):
  add `ClosureSnapshot snapshot(Optional<List<String>> packIds)`.
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/PacksAvailability.java` (owner WP05):
  implement it over `packs.application.ClosureQueries`.
- `service/activations/src/main/java/ai/speckitty/charter/activations/domain/ClosureSnapshot.java` (owner WP06): add
  any accessor the mapper needs. A shape change also updates `ClosureSnapshots`.

## Subtask Guidance

### Subtask T050: `PacksAvailability` over `ClosureQueries`

**Steps (red first, `PacksAvailabilityTest`, with a stubbed `ClosureQueries` or a real one over the packs test fakes):**
1. `snapshot(Optional.empty())` passes "every configured pack" and maps `ClosureView` to `ClosureSnapshot` field by
   field:
   - the packs, with their resolved dependencies;
   - the held ids per kind, and the mission types;
   - the floor (`requiredBy`);
   - the locks (`lockedBy`, only `LOCKED` levels);
   - the presets and the fingerprint.
2. **Unknown packs, one rule each (R4):**
   - **On write:** a document body whose `packs` names an id the catalog does not configure. `ClosureQueries` throws
     `IllegalArgumentException` for an unknown root (WP02). The adapter catches it and returns a snapshot flagged
     `unknownPacks`. `DocumentRules` then answers **422 `activation_invalid`** at `/packs/i/packId`. Assert exactly
     that.
   - **On the closure read:** a pack inside the closure that is unknown or unreadable, for example a stored document
     whose pack was removed or broken since. The closure read answers **500 `pack_unreadable`**, the composition
     refusal, as the offering does. Assert exactly that.
3. Composition refusals propagate unchanged (US2 AS4, and `pack_reference_pack_missing`).

### Subtask T051: `ActivationService`

**Purpose:** the application use cases. Each takes exactly **one** snapshot (NFR-007).

**Steps:** write `ActivationServiceTest` first, with `FakeAvailablePacks` and `InMemoryActivationRepository`.
`FakeAvailablePacks` **counts** its `snapshot` calls. Every test asserts the count: one per write, one per closure
read, and zero for `get` and `delete` (R8).

**Operations:**
- `create(document)`:
  - mint an id with `ActivationIdMint`, an in-house ULID (48-bit time and 80 random bits, Crockford base32). Test the
    grammar and the time order;
  - check `DocumentRules` against `snapshot(document.packs())`, then `repository.create`;
  - answer `Created(stored)`.
- `put(id, document, ifMatch, ifNoneMatch)`:
  - both validators present: `400`, through a dedicated `BothValidatorsException` mapped to a plain 400 `Problem`;
  - `If-None-Match` present but not `*`: `400`, a malformed precondition (J10), through the same mapping;
  - `If-None-Match: *`: `repository.create`, answering 412 when the id exists;
  - `If-Match` present: `replace(id, Optional.of(revision))`. A stale revision answers 412, and so does a missing
    document (RFC 9110 §13.1.1). `*` matches any existing document (`EntityTags.matches`);
  - neither: `replace(id, Optional.empty())`, answering 201 when it created and 200 when it replaced (last writer
    wins, ADR-0020 D6);
  - the rules are checked **before** the write. Nothing is stored on a 409 or 422.
- `get(id, ifNoneMatch)`: `NotModified(revision)` when `EntityTags.noneMatch` holds, otherwise `Found(stored)`, and
  404 when missing. It takes no snapshot (FR-017).
- `delete(id, ifMatch)`: `Deleted`, 404 when missing, 412 when stale.
- `packs(id, ifNoneMatch)`:
  - load the document (404 if missing) and take `snapshot(document.packs())`;
  - answer the packs with the quoted `ETag` `sha256(revision + "\n" + fingerprint)`, or `304` on a match.

Extract `checked(document)` and `preconditionOf(ifMatch, ifNoneMatch)`; each method stays short.

### Subtask T052: The controller and the mapper

**Steps (red first, `ActivationMapperTest`):**
1. `ActivationMapper`:
   - `ActivationConfig` DTO to the domain builder, giving `Built(document, violations)`. Keep absent and `[]` apart:
     the generated DTO leaves an absent list `null`;
   - the domain document back to the DTO, in canonical form;
   - `ClosureSnapshot.packs` to `OfferingPack` and `ResolvedDependency`. Build the `_links` locally, the same way
     `OfferingController` builds them; activations may not name `Hypermedia`. A scenario pins their equality.
2. `ActivationController` (out-of-map):
   - `createActivation`: 201, with `Location` and `ETag`;
   - `getActivation`: 200 or 304, with `ETag`;
   - `replaceActivation`: 200 or 201;
   - `deleteActivation`: 204;
   - `listActivationPacks`.

   Remove each one's `@ReservedOperation`. The four effective reads and the five PR B writes keep theirs.
3. **Conditional requests (J10, `ActivationConditionalRequestTest`, MockMvc):**
   - a `GET` with `If-None-Match: W/"<revision>"` answers 304, under both Spring's weak comparison and
     `EntityTags.noneMatch`;
   - a `GET` with a non-matching tag answers 200;
   - a `PUT` with `If-None-Match: "x"` answers 400.

   Check that the controller's 304 and Spring's `HttpEntityMethodProcessor` 304 agree. If both act, return 304
   explicitly, and keep Spring from re-evaluating with a different result.

### Subtask T053: The #125 build notes (body guard and 422 mapping)

**Steps (red first, `ActivationBodyAdviceTest`):**
1. `deployable/ActivationBodyAdvice` (`@ControllerAdvice`, a `RequestBodyAdviceAdapter`) supports `ActivationConfig`,
   and also `SelectionRequest`, `ExclusionRequest` and `PresetApplication`, so PR B changes nothing here. It calls WP01's
   `JsonBodyGuard`, whose API returns a typed result and takes a refusal factory (J1). The advice supplies
   `ACTIVATION_INVALID` with a `RefusalViolation`:
   - a body over `BodyLimit.MAX_BYTES`: pointer `""`, code `body_too_large`;
   - a `null` list or item (`NullValue.detail()`, public since WP01): its pointer, code `null_value`;
   - an unknown member anywhere: read the bytes with a strict `JsonMapper` (`FAIL_ON_UNKNOWN_PROPERTIES`) into the DTO
     class; on an `UnrecognizedPropertyException`, use the pointer from the exception's path, code `unknown_member`.
2. `deployable/ActivationProblems`, a `@RestControllerAdvice(assignableTypes = ActivationController.class)` ordered
   before `ProblemHandler`:
   - an unbindable body (`HttpMessageNotReadableException`) and bean validation (`MethodArgumentNotValidException`,
     `HandlerMethodValidationException`) map to 422 `activation_invalid` with violations, rendered through
     `RefusalHandler.refused(...)`;
   - `BothValidatorsException` maps to a plain 400 `Problem`;
   - a body that is not JSON stays 415.
3. **Path kinds:** check the generated `kind` parameter type. A `String` parsed with `ArtifactKind.fromToken` (snake
   case) needs no `Converter`. Record the finding for WP13.

### Subtask T054: The round-trip property test (NFR-003)

**Steps:** `ActivationRoundTripPropertyTest` in `deployable` uses MockMvc, the in-memory repository, and a fixture pack
set holding a few ids of every activatable kind.
1. Generate at least 1,000 valid documents with a seeded `java.util.Random`, and print the seed on failure. Vary:
   - random subsets per kind;
   - absent versus `[]`;
   - duplicate and unsorted lists;
   - `packs` absent or present;
   - non-empty deny lists that avoid the floor.
2. For each document: `PUT` creates it, `GET` reads it, and a `PUT` of the fetched `config` with its `If-Match`
   answers 200 with the same `ETag`, in 100% of cases.
3. Use no property-testing library (DIRECTIVE_051): a plain loop over a fixed seed set.

### Subtask T055: The closure read and US6, end to end

**Steps:**
1. Run the US2 and US6 scenarios until they pass. The missing and schema-invalid policy files (`pack_unreadable`)
   prove WP03's fail-closed path through HTTP.
2. The closure read's `ETag`: a second `GET` with it as `If-None-Match` answers 304; after the document changes, it
   answers 200.

### Subtask T056: The raised flags, all green, and the evidence

**Steps:**
1. `CharterRefusalCode.yaml`: set `raised: true` for `activation_not_found`, `activation_invalid`,
   `artifact_required` and `artifact_locked`. Update the reserved-codes paragraph (lines ~42–45).
2. `ReservedRefusalCodesTest`: extend `theContractMarksTheCodesTheWritesAndTheLoadRaiseAsDecided` with the four codes.
   Keep `ARTIFACT_NOT_FOUND`, `MISSION_TYPE_NOT_FOUND` and `PRESET_NOT_FOUND` unraised. `ActivationProblems` must name
   no unraised code.
3. Run the gates. Record:
   - the whole of WP08's red list, now green;
   - the reserved count, 9;
   - the property test's count and seed;
   - the snapshot-count assertions.

## Checkstyle limits (NFR-001)

These are held by construction. The limits are still off on `main`, held until the methods over them are refactored,
so the reviewer measures new and changed methods by hand: CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1.
`ActivationService.put` and the mapper are the hotspots. No new suppression, no disabled test, and no `@WIP`.

## Gate commands

```bash
./gradlew :activations:check
./gradlew :deployable:test --tests '*CharterContractSuite*' --tests '*ReservedOperationCoverageTest*' \
  --tests '*ReservedRefusalCodesTest*' --tests '*OperationCoverageTest*' --tests '*ActivationBodyAdviceTest*' \
  --tests '*ActivationRoundTripPropertyTest*' --tests '*ActivationConditionalRequestTest*' \
  --tests '*HexagonalArchitectureTest*' --tests '*NullOmissionTest*'
```

## Definition of Done

- [ ] Every scenario WP08 recorded as red passes, and the whole suite is green.
- [ ] `ActivationService` implements create, put (every precondition branch, the non-`*` `If-None-Match` 400
      included), get with 304, delete, and packs. The snapshot count is asserted per operation.
- [ ] The unknown-pack rules hold: 422 on write, 500 `pack_unreadable` on the closure read. One assertion each.
- [ ] Unknown members, `null`s, oversized bodies (`body_too_large`) and bean-validation failures answer 422
      `activation_invalid` with violations.
- [ ] The controller's 304 agrees with Spring's weak comparison.
- [ ] The four codes are raised, and `ReservedRefusalCodesTest` is green.
- [ ] The property test passes over at least 1,000 documents.
- [ ] No new suppression, and no disabled test.

## Risks

- **A double 304 path:** the explicit 304 and Spring's processor can disagree on weak tags. The conditional-request
  test guards it.
- **The strict re-read for unknown members** parses the body twice. It is bounded by the 1 MiB limit; note the cost in
  the Javadoc.

## Reviewer guidance (reviewer-renata)

- **Green against the recorded red:** compare WP08's failing list with this WP's passing run, name by name.
- **The precondition matrix:** check the tests for:
  - no validator;
  - `If-Match` fresh, stale, and `*`;
  - `If-None-Match: *` on an absent id and on a present one;
  - `If-None-Match: "x"`;
  - both validators.
- **Nothing is stored on a refusal:** the 409 and 422 scenarios must `GET` afterwards and see 404 or the old revision.
- **One read:** the snapshot-count assertions in `ActivationServiceTest`.
- **Non-fakeable evidence:**
  - the suite summary, before and after;
  - the property test's iteration count and seed;
  - the `ReservedRefusalCodesTest` output.
