---
work_package_id: WP13
title: select, unselect, deactivate and reactivate (scenarios first)
dependencies:
- WP12
- WP17
requirement_refs:
- FR-010
- FR-011
- FR-012
- FR-013
- FR-014
- FR-015
- FR-007
- C-001
- NFR-002
- NFR-007
- SC-001
- SC-002
- NFR-001
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
subtasks:
- T075
- T076
- T077
- T078
- T079
- T080
- T081
- T082
- T083
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationSelectionService.java
create_intent:
- contracts/charter/scenarios/activation-selections.feature
- contracts/charter/changes/2026-11-05-activation-selections-and-exclusions.md
- service/activations/src/main/java/ai/speckitty/charter/activations/application/PacksSession.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationSelectionService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationExclusionService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationWrites.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationChangeMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationSelectionServiceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationExclusionServiceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationChangeMapperTest.java
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/scenarios/activation-selections.feature
- contracts/charter/changes/2026-11-05-activation-selections-and-exclusions.md
- contracts/charter/schemas/ActivationChange.yaml
- service/activations/src/main/java/ai/speckitty/charter/activations/application/PacksSession.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationSelectionService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationExclusionService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationWrites.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationChangeMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationSelectionServiceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationExclusionServiceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationChangeMapperTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP13: select, unselect, deactivate and reactivate (scenarios first)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Build the four per-artifact writes, red first, on the WP11 reach and the WP12 rules:

- `PUT …/selections/{kind}/{artifactId}` (select, with an optional cascade and `includeDeactivated`; kind
  `mission_type` maps to `missionTypes`);
- `DELETE …/selections/{kind}/{artifactId}` (unselect);
- `PUT …/exclusions/{kind}/{artifactId}` (deactivate, with an optional cascade and `skippedShared`);
- `DELETE …/exclusions/{kind}/{artifactId}` (reactivate).

Each answers an `ActivationChange` with a new `ETag`, or `200` with the same revision for a no-op. A missing
`If-Match` answers `428`. An id the closure does not hold answers `404 artifact_not_found` or
`mission_type_not_found`. A required or locked target answers `409`. The contract drops `x-reserved` from both
paths, and the reserved count goes 9 → 5.

Implementation command:
`spec-kitty agent action implement WP13 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** B. **Concern:** IC-09a. **Contract delta:** `contracts/contract-delta.md` "PR B".
- **Rulings:** ADR-0020 D6 (the sub-resource writes and their answer), ADR-0013 D2 to D5 (persistent deactivation;
  only the named id gets a deny entry), ADR-0059 D2 (as revised 2026-11-05) and D1 (behavioural scenarios gate).
- **Requirements:** FR-010 (path ids → 404), FR-011 to FR-015, FR-007 (deactivate on the floor → 409), NFR-002,
  NFR-007, SC-001, SC-002.
- **Collisions:** rebase PR B onto PR A, and onto `main` once PR A merges. #393 (`ProblemHandler`) may move under
  `ActivationProblems`.
- **Grounding:**
  - After WP08 and WP09, `activations.feature` holds 11 `@reserved` scenarios over 9 reserved operations
    (`selectActivationArtifact` has two pins, artifact and mission type, plus the binding pin); the binding pin ("answers 501 before it reads its request") is on
    `selectActivationArtifact`. When select is built, that pin must move to the last reserved operation that takes a
    body, `applyActivationPreset`, and WP14 retires it.
  - `ActivationBodyAdvice` (`activations.infra.api`, WP16) already guards `SelectionRequest` and `ExclusionRequest`.
  - `ClosureView.reach`, `enhancesPull` and `enhancersOf` (WP11) run on the view taken for the request. The activations port must
    keep that one view for the whole request, so `AvailablePacks.snapshot(...)` is not enough: add
    `AvailablePacks.session(packIds)`, returning a `PacksSession` with `snapshot()`, `reach(ReachPlan)` and
    `enhancersOf(Set<ArtifactRef>)`, all backed by one `ClosureView` (NFR-007).
  - The generated `kind` path parameter type decides parsing (see WP09 T053's finding). Map `mission_type` to the
    `missionTypes` key; every other kind through `ArtifactKind.fromToken` and `activatable()`.

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure` (PR B).
- Lane worktree from `lanes.json`. It depends on WP12 and on WP17 (the post-PR-A correctness fixes: the precondition
  order, the typed unknown-root handling, and the striped locks its writes run under).
- Rebase onto the lane base before review.

## Out-of-map edits (sequenced by dependency)

- `contracts/charter/paths/activations_activationId_selections_kind_artifactId.yaml` and
  `contracts/charter/paths/activations_activationId_exclusions_kind_artifactId.yaml` (owner WP08): remove `x-reserved`
  and the 501 text and response.
- `contracts/charter/schemas/CharterRefusalCode.yaml` (owner WP09): `artifact_not_found` and `mission_type_not_found`
  become `raised: true`; update the reserved-codes paragraph.
- `contracts/charter/scenarios/activations.feature` (owner WP08): remove the six select, unselect, deactivate and
  reactivate pins (select artifact, select mission type, unselect, deactivate, re-activate, and the binding pin); add
  the binding pin back on `applyActivationPreset`. The result is 6 `@reserved` scenarios (preset, the four effective
  reads, and the binding pin) over 5 reserved operations.
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ReservedOperationCoverageTest.java` (owner
  WP08): 9 → 5.
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedRefusalCodesTest.java` (owner WP09): the two
  newly raised codes.
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ActivationSteps.java` (owner WP08): steps for a
  graph fixture and for reading the report buckets.
- `service/activations/src/main/java/ai/speckitty/charter/activations/application/AvailablePacks.java`,
  `.../infra/packs/PacksAvailability.java` and `.../infra/api/ActivationController.java` (owner WP05): add `session`,
  and implement the four controller methods.
- `service/activations/src/test/java/ai/speckitty/charter/activations/application/FakeAvailablePacks.java` and
  `.../infra/packs/PacksAvailabilityTest.java` (owner WP09): fake and test the session.

## Subtask Guidance

### Subtask T075: Contract B, part one

**Steps:**
1. Remove `x-reserved` and the 501 sentence and response from the selections and exclusions path files (four
   operations). Check each declares `428` (`PreconditionRequired`), `404` (`ArtifactNotFound`; for the mission-type
   case the `ActionNotFound`-style `mission_type_not_found` response; read what the path declares, and add it if it
   is missing), `409` (exclusions) and `422`.
2. `CharterRefusalCode.yaml`: `artifact_not_found` and `mission_type_not_found` become `raised: true`.
3. Fragment `contracts/charter/changes/2026-11-05-activation-selections-and-exclusions.md` (date it the day you write
   it): `### Changed`, saying the four writes are served and the two codes are raised; note that an enhancer is never
   added to `selected` (ADR-0059 D2.2). With `includeDeactivated`, a denied enhancer's entry is cleared, so the
   enhancer appears in `cleared` and in `applied` (removed from `deactivated`), but never in `selected`.
   **R5, deferred and recorded:** `ActivationChange.skipped` reports the **write-time** walk only. What the read-time
   `enhances` pull leaves out is reported by the effective reads (`getActivationSkippedReport`, the next mission).
   Say so in this fragment, and add one sentence to the `skipped` member description in
   `contracts/charter/schemas/ActivationChange.yaml`.
4. `./gradlew :contracts:check :charter-api:compileJava`.

### Subtask T076: Red scenarios (US3, US4)

**Steps:**
1. `contracts/charter/scenarios/activation-selections.feature`, tagged `@contract @activations`. Fixture: one org
   pack with a graph document holding `D requires T`, `D requires X`, `E enhances T`, a profile `P specializes_from Q`,
   `Q requires R`, `Q suggests S`, and `D2 requires T2`, `D requires T2`, `E2 enhances T3`, `D requires T3`. Write it
   with the existing `the org pack "…" with the file "…":` step (graph shards and the artifact files; copy the
   shapes from `composition.feature`'s fixtures). **Naming (R6):** E enhances T is the US3 enhancer, and E2 enhances
   T3 is the US4 enhancer. Never reuse E for both.
   A second org pack `shared` holds tactic `XT`, and the first pack depends on it and writes `D requires shared::XT`
   (for the cross-pack scenario below).
2. One scenario per acceptance scenario: US3 AS1–AS8 and US4 AS1–AS4. Each starts from an activation created with the
   `the activation "a-1" holds:` step, writes with the remembered `ETag` (`I PUT … with the remembered ETag and the
   body:` exists in `RegistrationSteps`), and checks the JSON pointers of `selected`, `applied`, `cleared` and each
   report bucket, plus a new `ETag`.
   - **US3 AS3 (R2):** the starting document **denies E** (`deactivated.tactic: [E]`) as well as X. Selecting D with
     a cascade and `includeDeactivated: true` selects D, T and X. Assert that `cleared` holds X **and E** (E enhances
     T, which the walk selects: ADR-0059 D2.2), that E is in `applied` (removed from `deactivated`, A1), and that E
     is **absent** from `selected`.
   - **US4 AS2 (R6):** the starting document has `selected.tactic` holding **E2** (and T3 through D's cascade).
     Deactivating D with a cascade keeps T3, and `skippedShared` names E2 in `referencedBy`.
   - **`applied` (A1):** US3 AS5 (unselect) asserts `applied` lists the id and `cleared` is empty; US4 AS1 asserts
     `applied` is [D, T]; US4 AS4 (reactivate) asserts the id is in both `applied` and `cleared`; every no-op scenario
     asserts both are empty and the `ETag` is unchanged.
3. Add the edge cases: a missing `If-Match` answers 428; a no-op answers 200 with the same `ETag`; a cascade cycle
   (A requires B requires A) answers 200 and selects both once; an unknown kind in the path answers 422.
   **Cross-pack follow (FR-012, R7):** selecting D with a cascade `["all"]` selects `XT` from the `shared` pack through
   `D requires shared::XT`. Assert `selected.tactic` holds XT and `applied` lists it.
4. The new `ActivationSteps`: `the field at {string} holds exactly the ids:` (a docstring list) and `the response
   header "ETag" is the remembered one` (reuse it if it exists).
5. Run the suite and **record the failing names** (each answers 501 now).

### Subtask T077: The reserved pins (9 → 5)

**Steps:** in `activations.feature`, remove the select (artifact and mission type), unselect, deactivate and reactivate
pins. Move the binding pin to `PUT "/activations/a-1/presets/event-driven"` with `If-Match` and the unbindable body
`{"force": "yes"}`. `ReservedOperationCoverageTest`: `theActivationSetReservesFiveOperations`, with `hasSize(5)`
(operations). The feature file goes from 11 to 6 `@reserved` scenarios. Count them with
`grep -c '@reserved' contracts/charter/scenarios/activations.feature` and record both numbers.

### Subtask T078: The session port

**Steps (red first, `PacksAvailabilityTest` and `FakeAvailablePacks`):**
1. `PacksSession` (application interface): `ClosureSnapshot snapshot()`, `Reach reach(ReachPlan plan)`,
   `Set<ArtifactRef> enhancesPull(Set<ArtifactRef> starts)` (both directions, transitive over `enhances`, WP11 R1),
   and `Map<ArtifactRef, Set<ArtifactRef>> enhancersOf(Set<ArtifactRef> targets)` (one hop, reverse).
2. `AvailablePacks.session(Optional<List<String>> packIds)`. Keep `snapshot(...)` as `session(...).snapshot()` for
   WP09's callers, or migrate them.
3. `PacksAvailability`: one `ClosureQueries.snapshot` per session; `reach` maps `ReachPlan` to `ReachRequest`
   (`specializes_from` with `{requires, scope}` when `lineage` is true) and `ReachResult` back to `Reach`.
4. Test: two `reach` calls in one session cause one `ClosureQueries.snapshot` call.

### Subtask T079: `ActivationSelectionService` (select, unselect)

**Steps (red first, `ActivationSelectionServiceTest` with `FakeAvailablePacks` and the in-memory repository):**
1. Shared preconditions in `ActivationWrites`: `If-Match` absent gives `PreconditionException(MISSING)` (428); stale
   gives 412; the document is loaded (404 `activation_not_found`); one session; the path id is checked against the
   snapshot (404 `artifact_not_found` or `mission_type_not_found`); after computing, an unchanged document is a no-op
   (`200`, same revision, **no write**); otherwise a CAS write with the loaded revision (412 when a concurrent write
   won).
2. Select: `CascadeScope.parse` (422 on the mix, before any read of packs); `planSelect`; `session.reach`; with
   `includeDeactivated`, `session.enhancesPull(walkSelected)`; `applySelect`; then **`DocumentRules.check`** on the
   result (a cleared deny entry cannot violate it, but the rules run on every write).
3. Select `mission_type`: add to `missionTypes` (absent becomes `[id]`); no cascade applies (mission types have no
   cascade edges here; document that), and `applied` lists `{kind: mission_type, artifactId}`.
4. Unselect: remove from `selected` (or `missionTypes`); `applied` lists it (removed from `selected`); `cleared` is
   empty; a no-op when it was not selected (empty `applied` and `cleared`, same revision).
5. **`applied` and `cleared` (A1, the contract rule):** `applied` lists every id the write **added to or removed from**
   `selected`, `deactivated` or `missionTypes` (`contracts/charter/schemas/ActivationChange.yaml`); `cleared` lists the
   deny entries removed. A kind (`activatedKinds`) is not an `ArtifactReference` and never appears. A no-op has both
   empty and keeps the revision. Build `applied` with WP12's `ActivationChange.of(before, after, …)`, never by hand
   in the service.

### Subtask T080: `ActivationExclusionService` (deactivate, reactivate)

**Steps (red first, `ActivationExclusionServiceTest`):**
1. Deactivate: if the target is on the floor or locked, answer 409 `artifact_required` or `artifact_locked` (with
   `requiredBy` packs) **before** planning, and keep the revision. Otherwise `planDeactivate`, reach, `planShared`,
   reach, `enhancersOf(candidates)`, then `applyDeactivate` with the floor from the snapshot.
2. Without a cascade: deny the target and unselect it.
3. Reactivate: remove the deny entry (`cleared` lists it, and `applied` lists it as removed from `deactivated`, A1);
   drop the key when its list empties; a no-op when not denied (empty `applied` and `cleared`).
4. A no-op deactivate (already denied, nothing selected to remove) answers the same revision.

### Subtask T081: The controller and the `ActivationChange` mapper

**Steps (red first, `ActivationChangeMapperTest`):**
1. Domain `ActivationChange` to the DTO: `revision`, `applied` and `cleared` (`ArtifactReference`), `skipped`
   (four lists, sorted), and `_links` (self, and the activation).
2. `ActivationController`: implement the four methods; set `ETag` to the quoted new revision; remove their
   `@ReservedOperation`. Keep the five remaining reserved methods marked.
3. A path kind outside the enum: 422 `activation_invalid` with a violation at the path (`/kind`), via
   `ActivationProblems` or a direct refusal.

### Subtask T082: Refusal flags and coverage

**Steps:**
1. `ReservedRefusalCodesTest`: move `ARTIFACT_NOT_FOUND` and `MISSION_TYPE_NOT_FOUND` to the raised side.
   `PRESET_NOT_FOUND` stays unraised until WP14. Check that no main class names it yet.
2. `OperationCoverageTest`: each built operation's 2xx is exercised by a scenario.

### Subtask T083: All green, and evidence

**Steps:** run the gates; record the red-run names from T076, the green summary, and the reserved count 5.

## Checkstyle limits (NFR-001)

The limits are still off on `main`, held until the methods over them are refactored, so they are held by
construction: the reviewer measures new and changed methods by hand.

CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1, by construction. The shared precondition flow lives in
`ActivationWrites` so each service method stays short. No new suppression, no disabled test, and no `@WIP`.

## Gate commands

```bash
./gradlew :contracts:check :charter-api:compileJava
./gradlew :activations:check
./gradlew :deployable:test --tests '*CharterContractSuite*' --tests '*ReservedOperationCoverageTest*' \
  --tests '*ReservedRefusalCodesTest*' --tests '*OperationCoverageTest*' --tests '*HexagonalArchitectureTest*'
```

## Definition of Done

- [ ] The four writes are served per the contract; their pins are gone; the binding pin is on the preset operation;
      the count is 5.
- [ ] `activation-selections.feature` covers US3 AS1–AS8, US4 AS1–AS4 and the edge cases; each was seen red first.
- [ ] One `ClosureQueries.snapshot` per request (the session test).
- [ ] A no-op answers 200 with the same revision and performs no write; a missing `If-Match` answers 428.
- [ ] `artifact_not_found` and `mission_type_not_found` are raised; `ReservedRefusalCodesTest` is green.
- [ ] No new suppression, no disabled test, and no `@WIP`.

## Risks

- **Fixture weight.** The graph fixture is large; keep it in one `Background` so every scenario shares it.
- **The no-op check** must compare documents (or revisions) after the computation, not the request. A select that
  only clears its own deny entry is not a no-op.
- **A floor change between read and write** cannot happen inside a session; a pack change during the request answers
  `stale_input`.

## Reviewer guidance (reviewer-renata)

- **Red first:** demand the T076 red-run names (each answered 501) and the commit order (feature before services).
- **One read:** check the session test asserts one `ClosureQueries.snapshot` per request.
- **The enhancer rule end to end:** US3 AS1 (E not denied) must assert E is **absent** from `selected` and from
  `applied`.
- **Shared by an enhancer:** US4 AS2 must start with E2 selected and name E2 in `skippedShared.referencedBy` (R6).
- **Clearing (R2, A1):** US3 AS3 must start with E denied, and assert E in `cleared` **and** in `applied`, and absent
  from `selected`.
- **Cross-pack (R7):** the `shared::XT` scenario exists and passes.
- **Non-fakeable evidence:** the suite summary with the new scenario count, `ReservedOperationCoverageTest` asserting 5,
  and the 428 and 412 scenario names.
