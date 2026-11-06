---
work_package_id: WP08
title: contract A and the red scenarios for CRUD and the closure read
dependencies:
- WP05
- WP07
requirement_refs:
- C-001
- C-004
- FR-008
- FR-009
- FR-018
- FR-021
- FR-022
- NFR-001
- NFR-002
- SC-002
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-activation-register-and-closure-01M6NXBD
base_commit: dbd91c0ef0d938099c24084f86a68caa82293d08
created_at: '2026-11-05T20:52:27.529510+00:00'
subtasks:
- T047
- T048
- T049
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: contracts/charter/
create_intent:
- contracts/charter/paths/activations_activationId_packs.yaml
- contracts/charter/schemas/ActivationPackList.yaml
- contracts/charter/parameters/IfNoneMatch.yaml
- contracts/charter/responses/NotModified.yaml
- contracts/charter/headers/ActivationPackListETag.yaml
- contracts/charter/examples/ActivationPackList.two-packs.yaml
- contracts/charter/changes/2026-11-05-activation-register-and-closure-read.md
- contracts/charter/scenarios/activation-register.feature
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
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ActivationSteps.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/CucumberSpringConfiguration.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ReservedOperationCoverageTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP08: contract A and the red scenarios for CRUD and the closure read

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Land **contract A** and the **failing** behavioural scenarios for PR A's five operations, before any service code
(C-001, NFR-002). WP09 makes them pass.

Contract A:

- the new closure read `listActivationPacks` (`GET /activations/{id}/packs`) and `ActivationPackList`;
- `summary` moved to `EffectiveArtifactList`; `governanceFingerprint` out of `Activation`;
- `If-None-Match`/`304` on `getActivation` and the four effective reads;
- the absent-means-none texts;
- `x-reserved` removed from the four CRUD operations, and their 501 pins removed;
- a changelog fragment.

The scenarios cover US1, US2, US6, the edge cases and the `@WIP` composition rewrite. The reserved count goes 13 → 9,
and the "501 before binding" pin moves to `selectActivationArtifact`. So the generated interface compiles, the
controller gains a `listActivationPacks` **stub marked `@ReservedOperation`** (it answers 501 until WP09).

The raised flags of the four activation codes are **not** set here. `ReservedRefusalCodesTest` requires a raised
code to be named by main code, which WP09 writes, so WP09 flips the flags with the code that raises them.

Implementation command:
`spec-kitty agent action implement WP08 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** A. **Concern:** IC-06b (contract and scenarios half). **Contract delta:** `contracts/contract-delta.md`
  "PR A" is the checklist.
- **Rulings:**
  - ADR-0020: D3 (POST mints an id; PUT stores under a client id), D4 (canonical form, revision), D6 (round trip;
    last writer wins without `If-Match`), D9 and D10 (refusals and their members).
  - ADR-0059 (on `main`): D1 (a `@reserved` pin is not a gate; NFR-002), D4 (a stale document is served on read and
    refused on write), D5 (the `Activation` `ETag` is its revision; `summary` and the fingerprint move; `304`).
- **Decisions:** `DM-01M6NXNBA1J808JV4SHT743SCF` (the closure sub-read; the `@WIP` scenario rewritten),
  `DM-01M6NXQJ61D2GSFJFK02XW6F9N` (absent means none).
- **Collisions:** the base is `main` (with #368 and #376). Rebase over #393 (`ProblemHandler`, draft) and #379 and
  #384 if they touch the contract or the scenarios.
- **Grounding:**
  - The generated `ActivationApi` uses `skipDefaultInterface=true`. Adding `listActivationPacks` to the contract
    **breaks compilation** until `ActivationController` declares it. Add the reserved stub in the same commit.
  - `ReservedOperationCoverageTest` scans **every** request line of `activations.feature`, not only the `@reserved`
    blocks, and pins `hasSize(13)` **operations**. The file holds 15 `@reserved` scenarios: 13 operations, with
    `selectActivationArtifact` pinned twice (artifact and mission type), plus the binding pin on `replaceActivation`.
    Verify with `grep -c '@reserved' contracts/charter/scenarios/activations.feature` before editing.
  - `OperationCoverageTest` requires every declared `(method, path, 2xx)` of a non-reserved operation to be
    exercised by a scenario. The new scenarios provide them; run it and record the result.
  - The step vocabulary in `CharterSteps`, `RegistrationSteps` and `SecuritySteps` already covers:
    - `I PUT … with the body:` and `I PUT … with If-Match … and the body:`;
    - `I {word} … with the header …: …`;
    - `the problem code is …`, `the field at … is …` and `the response header … is …`.

    It has no step for "the project activates …", none for a `PUT` with `If-None-Match` and a body, and none for the
    `@WIP` scenario's `the offering holds exactly 2 packs`.
  - All scenarios share **one** Spring context. With no `ConfigRoot`, WP07's wiring gives the in-memory repository;
    it must be cleared per scenario (`InMemoryActivationRepository.clear()`, from WP07).

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure` (PR A).
- Lane worktree from `lanes.json`. It depends on WP05 (the controller) and WP07 (the repository the glue clears).
  WP09 follows in the same lane.
- **Red by design.** This WP merges with the new scenarios failing. Its DoD lists them by name. Every pre-existing
  scenario and test stays green. PR A is cut only after WP09 and WP10, so `main` never sees the red state.
- Rebase onto the lane base before review.

## Out-of-map edits (sequenced by dependency)

- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationController.java` (owner WP05):
  add the `listActivationPacks` override, marked `@ReservedOperation`, whose body throws the local `notBuilt()`. The
  four CRUD methods keep their marks until WP09.

## Subtask Guidance

### Subtask T047: Contract A (contract first, C-001), and the stub

**Steps:**
1. **New operation.** Create `paths/activations_activationId_packs.yaml`:
   - `operationId: listActivationPacks`, tag `Activation`, the `ActivationId` parameter and the new `IfNoneMatch`
     parameter;
   - `200`: `ActivationPackList`, with the headers `Charter-Contract-Version`, `Charter-Worktree`,
     `Charter-Source-Fingerprint` and `ETag` from a new `headers/ActivationPackListETag.yaml` ("opaque; covers the
     revision and the pack fingerprint");
   - `304`, `401`, `403`, `404` (`ActivationNotFound`), `500` (`OfferingUnreadable`, which lists the composition
     codes, including `pack_reference_pack_missing`) and `default`.

   Add the path to `openapi.yaml` after `/activations/{activationId}`.
2. **`ActivationPackList.yaml`:** `{packs: [OfferingPack], _links}`, closed, both required. The description says "in
   dependency order; items reuse the offering's pack shape". Add the example; register it in `schemas/_index.yaml`
   and `examples/_index.yaml`.
3. **`Activation.yaml`:** remove `governanceFingerprint` and `summary` from `properties` and `required`. The new
   description: "the ETag is the quoted revision; the answer's bytes depend only on the stored document (ADR-0059
   D5)". Update `examples/Activation.minimal.yaml`.
4. **`EffectiveArtifactList.yaml`:** add the required `summary` (`$ref: ActivationSummary.yaml`) and update its
   example. These reads stay reserved, so nothing serves it yet (FR-021).
5. **`If-None-Match` and `304`:**
   - add `parameters/IfNoneMatch.yaml`, with the RFC 9110 §13.1.2 weak-comparison text modelled on the inline one in
     `paths/openapi.yaml`;
   - add `responses/NotModified.yaml`: no body, with the `ETag` and `Charter-*` headers;
   - reference both from `getActivation` and the four effective-read paths, and register them in the `_index.yaml`
     files.
6. **`replaceActivation` If-None-Match:** the existing `IfNoneMatchCreate` parameter allows only `*`. State in the
   operation description that any other value answers `400` (a malformed precondition, J10). WP09 pins it.
7. **Absent means none:**
   - `ActivationConfig`: the three-state paragraph says absent and `[]` both mean none for `activatedKinds`,
     `missionTypes` and each selected kind, and stay distinct on the wire; for `packs`, drop "a later pack wins", and
     say absent means every configured pack;
   - the twelve `SelectedArtifacts` property texts, `skill` included: "Absent means none of the kind; [] means none;
     a list means exactly those";
   - the select, unselect and deactivate descriptions in the selections and exclusions path files: text only; their
     `x-reserved` stays until WP13.
8. **Built:** remove `x-reserved` from `createActivation`, `getActivation`, `replaceActivation` and
   `deleteActivation`, with their "Reserved: it answers 501 …" sentences and their `501` responses. In
   `replaceActivation`, replace the "open question 5" sentence with ADR-0059 D4's write rule.
9. **Fragment:** `contracts/charter/changes/2026-11-05-activation-register-and-closure-read.md`, one `### Breaking`
   block:
   - `Activation` loses two required members, and a new success status (`304`) appears (ADR-0020 D8). Say what
     core's seam changes;
   - for #359, `PackPin` is unchanged and the dependency facts are on the closure read;
   - the four activation refusal codes become raised in this PR (WP09 sets the flags).
10. **The stub:** add `listActivationPacks` to `ActivationController`, marked `@ReservedOperation`.
11. Validate: `./gradlew :contracts:check :charter-api:compileJava :activations:compileJava`.

### Subtask T048: Red scenarios (US1, US2, US6, the edge cases) and the `@WIP` rewrite

**Steps:**
1. Create `contracts/charter/scenarios/activation-register.feature`, tagged `@contract @activations`. Its feature
   text cites ADR-0020 D3–D6 and ADR-0059 D4–D5. Write one scenario per acceptance scenario.
   - **US1, AS1–AS9:**
     - sorted and deduplicated, and `ETag` equals the quoted revision;
     - the round trip;
     - a stale `If-Match` answers 412, and the old revision is kept;
     - last writer wins without `If-Match`;
     - an unknown id answers 422 with the violation pointer;
     - a deny entry on a must-have item answers 409 `artifact_required` with `requiredBy`;
     - an empty deny list answers 422;
     - delete twice: 204, then 404;
     - `If-None-Match: *` answers 201, then 412.
   - **US1 additions:**
     - a `GET` with a matching `If-None-Match` answers 304;
     - a `GET` of an unknown id answers 404 `activation_not_found` (R7);
     - both validators answer 400;
     - a `PUT` with `If-None-Match: "x"` (not `*`) answers 400 (J10);
     - a body over 1 MiB, an unknown member, and `"selected": {"directive": null}` each answer 422
       `activation_invalid`, the first with a violation code `body_too_large`.
   - **US2, AS1–AS4:**
     - the alpha, beta and gamma fixture from `composition.feature`'s `@WIP` scenario;
     - gamma broken (an unreadable `pack.yaml`);
     - no `packs` key: every configured pack, in offering order;
     - out of range answers 500 `pack_dependency_out_of_range`.
   - **US2 additions (R7):**
     - a `<pack>::` reference to a pack outside the closure answers 500 `pack_reference_pack_missing` on the closure
       read;
     - an unknown or unreadable pack **inside** the closure answers 500 `pack_unreadable` (R4).
   - **US6, AS1–AS3:**
     - an org pack whose `pack.yaml` names `policy.yaml` locking `directive:D1`, written with the existing
       `the org pack "…" with the file "policy.yaml":` step;
     - a built-in pack open, and an org pack locked;
     - a named policy file that is **missing** answers `pack_unreadable` on `/offering` and on the closure read;
     - a named policy file that **fails its schema** answers the same (R7).
   - **Stale documents (Edge Cases, FR-017, R7):**
     - store a document naming D, delete D's file from the pack (`the file … of the pack … is deleted`), then `GET`
       answers 200 with the same document, and a `PUT` of the same config answers 422;
     - store a document denying D, then add a policy file locking D: `GET` answers 200 and keeps the stored deny
       entry, and a new `PUT` that denies D answers 409 `artifact_locked`.
   - **Unknown pack in a body (R4):** a document whose `packs` names an unconfigured id answers 422
     `activation_invalid` at `/packs/0/packId`.
2. `composition.feature` (~line 819): remove the "Deferred to #360" comment and `@WIP`. Add
   `And the project activates the pack "alpha"`, read `GET "/activations/project/packs"`, and assert beta then alpha,
   exactly two packs.
3. `ActivationSteps.java` (glue, with `ScenarioHttp` injected through the constructor):
   - `@Given("the project activates the pack(s) {string}")`: comma-separated configured ids;
     `PUT /activations/project` with `{"schemaVersion":1,"packs":[…]}`; assert 201;
   - `@Given("the activation {string} holds:")`, with a docstring body: `PUT`, assert 201, remember the `ETag`;
   - `@When("I PUT {string} with the header {string}: {string} and the body:")`;
   - `@Then("the answer holds exactly {int} packs")`;
   - `@Then("the response header {string} is the quoted field {string}")`.

   Reuse existing steps where they fit; `ScenarioHttp` holds the last response.
4. Add a `@Before` hook in `ActivationSteps` that clears the `InMemoryActivationRepository` bean. Touch
   `CucumberSpringConfiguration` only if the bean must be exposed `@Primary` there.
5. Run `./gradlew :deployable:test --tests '*CharterContractSuite*'` and **record every failing scenario name**. Each
   new scenario must fail: most with 501, the closure read with 501 from the stub.

### Subtask T049: The reserved pins (13 → 9)

**Steps:**
1. In `activations.feature`:
   - delete the four CRUD pins (create, register, read, remove): 15 → 11 scenarios once the binding pin has moved;
   - move "A reserved operation answers 501 before it reads its request" to `selectActivationArtifact`:
     `PUT "/activations/a-1/selections/directive/DIRECTIVE_001"` with `If-Match` and the unbindable body
     `{"cascade": 5, "includeDeactivated": "no"}`;
   - update the feature text: "the nine writes by #291" becomes "the five per-artifact and preset writes, PR B".
2. `ReservedOperationCoverageTest`: collect request lines **only from `@reserved` blocks**. Split the text with
   `RESERVED_TAG`, keep the blocks that contain `Scenario:`, and scan their lines. Rename the count test to
   `theActivationSetReservesNineOperations` and assert `hasSize(9)`.
3. `listActivationPacks` is **not** `x-reserved` in the contract, yet it answers 501 through its stub. The coverage
   test keys on `x-reserved`, so it ignores the operation. The pin set and the reserved set stay equal (9 operations,
   11 `@reserved` scenarios). Record both counts in the evidence.

## Checkstyle limits (NFR-001)

These are held by construction. The limits are still off on `main`, held until the methods over them are
refactored, so the reviewer measures new and changed methods by hand: CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if
depth ≤ 1. The step methods are the only Java here; keep them small. No new suppression, no disabled test, and no
`@WIP`. A failing scenario is red by design, never tagged out.

## Gate commands

```bash
./gradlew :contracts:check :charter-api:compileJava :activations:compileJava
./gradlew :deployable:test --tests '*ReservedOperationCoverageTest*' --tests '*OperationCoverageTest*' \
  --tests '*ContractDescriptionsTest*' --tests '*OpenApiDocumentTest*' --tests '*NullOmissionTest*'
./gradlew :deployable:test --tests '*CharterContractSuite*'    # expected: only the new scenarios fail
./gradlew :packs:test --tests '*ActivationConfigSchemaParityTest*'
```

## Definition of Done

- [ ] The contract carries every PR A item of `contract-delta.md` except the raised flags (WP09), with one fragment.
      `:contracts:check` is green.
- [ ] `listActivationPacks` compiles as a reserved stub.
- [ ] `activation-register.feature` has a scenario for every US1, US2 and US6 acceptance scenario and every addition
      above (R4, R7, J10).
- [ ] The `@WIP` composition scenario is rewritten against the closure read; no `@WIP` remains.
- [ ] `ReservedOperationCoverageTest` counts only `@reserved` blocks and asserts 9. The binding pin is on
      `selectActivationArtifact`.
- [ ] The failing-scenario list is recorded: exactly the new scenarios fail, and nothing pre-existing does.
- [ ] No new suppression, and no disabled test.

## Risks

- **`ActivationConfigSchemaParityTest`** validates a corpus against the canonical JSON schema and its OpenAPI mirror.
  Description-only edits should pass. If it compares descriptions, mirror the wording in
  `contracts/offering/schemas/ActivationConfig.schema.json`, and record that as an out-of-map edit in the review
  notes.
- **Conformance checks during red scenarios** (a 501 the contract no longer declares) add noise to the failure list.
  The failure reasons do not matter; the names do.
- **State leaking between scenarios** if the clear hook is missing.

## Reviewer guidance (reviewer-renata)

- **Red is the deliverable.** Check the recorded failing names against the feature file one to one. Run the suite
  yourself: no pre-existing scenario may fail.
- **Coverage scope:** plant a non-reserved request line in a scratch copy of `activations.feature` and check that
  `ReservedOperationCoverageTest` ignores it.
- **Contract review:** walk `contract-delta.md` "PR A" item by item against the diff.
- **Non-fakeable evidence:**
  - the suite summary, with failures equal to the new scenario count;
  - the `OperationCoverageTest` output;
  - the `:contracts:check` output.
