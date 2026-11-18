---
work_package_id: WP09
title: The recommendation read
dependencies:
- WP04
- WP07
- WP08
requirement_refs:
- FR-006
- FR-011
- FR-012
- FR-013
- FR-014
- SC-001
- SC-002
planning_base_branch: feat/108-model-task-routing
merge_target_branch: feat/108-model-task-routing
branch_strategy: Planning artifacts for this mission were generated on feat/108-model-task-routing. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/108-model-task-routing unless the human explicitly redirects the landing branch.
subtasks:
- T032
- T033
- T034
- T035
- T036
phase: Phase 3 - Reads
history:
- at: '2026-11-18T08:55:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/routing-edge/
create_intent:
- contracts/charter/parameters/TaskType.yaml
- contracts/charter/examples/CharterRefusal.task-type-not-found.yaml
- service/routing-edge/src/main/java/ai/speckitty/charter/routing/application/RecommendationInputs.java
- contracts/charter/paths/activations_activationId_taskTypes_taskType_model-recommendation.yaml
- contracts/charter/scenarios/model-recommendation.feature
- service/routing-edge/src/main/java/ai/speckitty/charter/routing/application/ModelRecommendationService.java
- service/routing-edge/src/main/java/ai/speckitty/charter/routing/infra/api/ModelRecommendationsController.java
- service/routing-edge/src/main/java/ai/speckitty/charter/routing/infra/api/RoutingJson.java
execution_mode: code_change
owned_files:
- contracts/charter/paths/activations_activationId_taskTypes_taskType_model-recommendation.yaml
- contracts/charter/parameters/TaskType.yaml
- contracts/charter/parameters/ModelRecommendationQuery*.yaml
- contracts/charter/schemas/ModelRecommendation*.yaml
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/examples/ModelRecommendation.*.yaml
- contracts/charter/examples/CharterRefusal.routing-catalog-*.yaml
- contracts/charter/examples/CharterRefusal.task-type-not-found.yaml
- contracts/charter/scenarios/model-recommendation.feature
- contracts/charter/scenarios/errors.feature
- service/routing-edge/src/main/java/ai/speckitty/charter/routing/application/ModelRecommendation*.java
- service/routing-edge/src/main/java/ai/speckitty/charter/routing/application/RecommendationInputs.java
- service/routing-edge/src/main/java/ai/speckitty/charter/routing/infra/activations/**
- service/routing-edge/src/main/java/ai/speckitty/charter/routing/infra/api/**
- service/routing-edge/src/main/java/ai/speckitty/charter/routing/infra/config/**
- service/routing-edge/src/main/java/ai/speckitty/charter/routing/application/config/**
- service/routing-edge/src/test/java/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP09 – The recommendation read

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according to
its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ Review Feedback

Check the `review_ref` field in the event log (`spec-kitty agent tasks status`). Address every feedback item, and record
each change with `spec-kitty agent tasks add-history WP09 --note "..."`.

## Branch Strategy

- **Planning base branch**: `feat/108-model-task-routing`
- **Merge target branch**: `feat/108-model-task-routing`
- The execution worktree is allocated per computed lane from `lanes.json`; never pick a base branch by hand.

```bash
spec-kitty agent action implement WP09 --agent claude --mission model-task-routing-01M7PVHQ
```

## Before you start (every WP)

1. **#355 must be merged.** No code starts before #355 (PR #599) has merged and this branch is rebased onto that main
   (C-001). If it has not merged, stop and report.
2. **Recheck every path below on the merged main.** This prompt was written against main `8284bd36` with #599 open. #599
   rewrites `PackContents`, `PackRulesets`, `PackAssembly`, `StoreRead`, `StorageRulesets` and
   `ReferenceFacts`/`ReferenceClosure`, and deletes `MissionTemplates` and `GuardClosure` (research R1). Follow the
   merged code, not this text, where they differ, and record the difference in the WP history.
3. **Read the authority:**
   - `docs/adr/0080-the-routing-catalog-kind-and-the-model-recommendation.md` (accepted);
   - `kitty-specs/model-task-routing-01M7PVHQ/{plan.md,data-model.md,research.md,contracts/operations.md}`;
   - the charter companion `.kittify/charter/charter.md`.

   Core's `/opt/spec-kitty/src/charter/offering/model_task_routing/` is evidence only (ADR-0015).
4. **Tests use the canonical shape only**, except tests of invalid content (charter companion; ADR-0070). Never exclude
   a field from a parity or round-trip check to make a test pass.
5. **Commits:** signed (`git commit -S`), ending with the `Claude-Session:` trailer, with no model names and no
   `Co-Authored-By`.

## Objective

`getModelRecommendation`, `GET /activations/{activationId}/task-types/{taskType}/model-recommendation`. It is
advisory, deterministic and `ready` in the switch gate, with one scenario per R7 row and per refusal, each asserting
the exact ordered candidate list (ADR-0080 decisions 7–13; FR-006, FR-011, FR-012, FR-013, FR-014, SC-001, SC-002;
plan IC-05; squad findings C1, C2, C6, C8–C11, D2–D4, D9, D11).

**Recorded edits outside the map.** WP08 owns `openapi.yaml`, `switch-gate.yaml`, `every-operation.feature`,
`unexpected-failure.feature`, `EffectiveReadService.java` and `EffectiveTag.java`. This WP adds its rows and its read
to them after WP08 merged, and records each edit.

## Precedent to copy

`selectAgentProfile` and `selectAgentProfileForStep`:

- path files: `contracts/charter/paths/activations_activationId_roles_role_profile-selection.yaml` and
  `…_missionTypes_missionType_actions_action_profile-selection.yaml`;
- controller: `profiles-edge` `EffectiveAgentProfilesController` (ETag and fingerprint at lines 124–131);
- use case: `ProfileSelectionService.select` (line 50; `If-None-Match` compared after selecting, line 98);
- activation half: `EffectiveReadService.profileSelection` (line 622) and `stepProfileSelection` (line 650);
- tag: `EffectiveTag.selection` (line 425; `Read.SELECTION` at line 123; `of(...)` at line 534).

## Subtasks

### T032 – Contract

- **Tag.** `ModelRecommendations` in `openapi.yaml`, described as implemented by `routing-edge`, with the path.
- **Parameters**, each with a pattern:
  - `taskType` (path): kebab-case, `maxLength` 64;
  - `agentProfile` (query, required): spelled as `ArtifactId` is for `getEffectiveAgentProfile`;
  - `missionType` and `step` (query, optional, both or neither);
  - `modelTier` (query, optional): `$ref CostTier.yaml`;
  - `IfNoneMatch`.
- **Schemas**, with `x-source` pointing at `service/routing`:
  - `ModelRecommendation`: `taskType`, `catalog` (`ModelRecommendationCatalog`: `id`, `packId`, `level`), `objective`,
    `tierConstraint?`, `modelTier?` (`ModelTierResolution`: `effective`, `source` `step|request`, `offer?`),
    `candidates` and `_links` (`self`, `catalog`, `agent-profile`, `step?`);
  - `ModelRecommendationCandidate`: a `oneOf` discriminated by `source` (`catalog` / `profile`), with the members of
    `data-model.md`.
  - The `score` is a number. Its description states the wire form (plain, trailing zeros stripped) and that the order
    of `candidates` is presentation, not preference.
  - The `objective` description states that `balanced` and `cost_first` compute alike today and differ in meaning.
- **Statuses:**
  - `200`, with `EffectiveETag`, `Charter-Source-Fingerprint` and `Charter-Worktree`;
  - `304`, `400`, `401` and `403`;
  - `404` (activation, profile, mission type, step, task type; the description states the order of `data-model.md`
    "Refusal order", and that `task_type_not_found` follows the 409s);
  - `409` (the two new codes);
  - `500`, placed after the step 404s and before the 409s (C2).
- **`CharterRefusalCode.yaml`:**
  - add `routing_catalog_not_in_force` (409), `routing_catalog_conflict` (409; its `detail` or extension names the ids
    in force; follow how `fallback_profile_conflict` names its packs) and `task_type_not_found` (404);
  - each gets an `x-refusal` entry with `raised: true` and a description naming `getModelRecommendation` and its place
    in the order;
  - update the schema description's prose lists of codes by status (lines 5–10);
  - write examples: `CharterRefusal.routing-catalog-not-in-force.yaml`, `CharterRefusal.routing-catalog-conflict.yaml`
    and `CharterRefusal.task-type-not-found.yaml`.
- **Answer examples** (`ModelRecommendation.*.yaml`) for R7 rows 2 and 6.

### T033 – The activation half

`EffectiveReadService.modelRecommendation(activationId, taskType, profileId, Optional<StepRef>, Optional<CostTier>,
ifNoneMatch)` opens one session (`open(stored)`, then `session.snapshot()`), and in this order:

1. `found(id)`: `404 activation_not_found`.
2. The profile: the ADR-0078 resolution, `PacksSession.profileResolution(id, query)`, whatever its state. It answers
   `404 artifact_not_found` when no pack of the closure holds the profile. Take its `model` and `effort`.
3. With a step: `session.requireAction`, then `session.stepPlace`, judged as `stepProfileSelection` judges them
   (`mission_type_not_found`, then `action_not_found`). Read the step's `modelTier` at its place. A held step is
   offered whether or not its mission type is in force (C11).
4. A closure that cannot compose or merge answers its `500`.
5. The catalogs in force: the `routing_catalog` winners whose state is in force. None gives
   `409 routing_catalog_not_in_force`; more than one gives `409 routing_catalog_conflict`, naming the ids.
6. A task type the catalog does not declare gives `404 task_type_not_found`.
7. The tag, a new `EffectiveTag.Read.RECOMMENDATION`: SHA-256 over the read name, the revision, the closure
   fingerprint and the normalized query (task type; profile id as normalized by `ArtifactId`; mission type; step;
   tier). It is compared after every refusal.

It returns the catalog in force (id, pack, level), the profile's model and effort, the step's offer, the source
fingerprint and the tag. It does no ranking; ranking is `routing-edge`'s, with WP07's ranker.

### T034 – `routing-edge`

- **`ModelRecommendationService`.** It calls the activation half through a port (`RecommendationInputs`, with an
  adapter in `routing-edge` `infra/activations`, as `profiles-edge` does) and resolves the effective tier: the
  request's `modelTier` (source `request`), else the step's (`step`), else none, and keeps the step's offer.
  - It ranks with `Ranker`.
  - It appends the profile candidate last when the profile declares a model, even when a catalog candidate names the
    same model.
  - It compares `If-None-Match` and answers `Found` or `NotModified`.
- **`ModelRecommendationsController`.** It implements the generated `ModelRecommendationsApi`, and sets `ETag`
  (`EntityTags.quote`) and `Charter-Source-Fingerprint`. It renders scores as `stripTrailingZeros().toPlainString()`
  numbers. Links: `self`, `catalog` (`getEffectiveRoutingCatalog`), `agent-profile` (`getEffectiveAgentProfile`) and
  `step` (`getEffectiveMissionStep`).
- **`RoutingJson`**: the mix-ins and `RuntimeHints` for `tierConstraint`, `modelTier`, `offer`, `rationale`, `effort`
  and `withinModelTier`.
- **Unit tests** for the service (tier resolution, profile placement, 304) and the controller (headers, links, number
  form).

### T035 – `model-recommendation.feature`

- **One scenario per R7 row (1–10)**, each asserting the **exact ordered candidate list** with every flag, `objective`,
  `tierConstraint` and `modelTier`, never containment.
- **One scenario per refusal:**
  - `400` for each malformed parameter, and for `missionType` without `step`;
  - `404 activation_not_found`;
  - `404 artifact_not_found` (profile);
  - `404 mission_type_not_found`;
  - `404 action_not_found`;
  - the `500`'s place (reuse the unreadable-pack harness of `unexpected-failure.feature`);
  - the two 409s;
  - `404 task_type_not_found`.
- **The order is asserted** by sending a request that breaks two rules at once, and expecting the earlier refusal.
- **Caching:** a `304` with a matching `If-None-Match`, and `200` with another tag. The ETag changes with
  `modelTier`.
- **Determinism (NFR-001):** two identical requests answer equal bodies and tags.

### T036 – Shared files and the switch gate

Recorded edits outside the map:

- rows for `getModelRecommendation` in `every-operation.feature` (401, 403), `unexpected-failure.feature` (500) and
  `errors.feature` (the new codes);
- `switch-gate.yaml`: `getModelRecommendation: {switch: ready}`.

Run the switch-gate check: every declared status is asserted.

## Definition of Done

- `getModelRecommendation` is served, deterministic, and `ready`, with scenarios that assert exact candidate lists and
  every refusal in order.
- `./gradlew verifyAll` passes on Java 25 for the modules this WP touches, with no new style-gate finding.
- Every subtask's status is recorded with `spec-kitty agent tasks mark-status`.
- Nothing outside `owned_files` changed, except a recorded edit outside the map, named in the WP history with its
  reason.

## Reviewer Guidance

- Check that rows 5 and 6 differ in candidates, and that row 6 keeps the `medium` cap.
- Check that every refusal comes before the 304.
- Check that the profile candidate comes from the ADR-0078 resolution, not from the declared fields.
- **Independent reviewer.** Review is by a separate reviewer session, never by the implementer.
