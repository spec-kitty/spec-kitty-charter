---
work_package_id: WP08
title: The catalog reads
dependencies:
- WP06
requirement_refs:
- FR-003
- FR-004
- FR-014
subtasks:
- T027
- T028
- T029
- T030
- T031
phase: Phase 3 - Reads
agent: claude
history:
- at: '2026-11-18T08:55:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/paths/
create_intent:
- contracts/charter/paths/packs_packId_artifacts_routing_catalog.yaml
- contracts/charter/paths/packs_packId_artifacts_routing_catalog_artifactId.yaml
- contracts/charter/paths/activations_activationId_artifacts_routing_catalog.yaml
- contracts/charter/paths/activations_activationId_artifacts_routing_catalog_artifactId.yaml
- contracts/charter/parameters/RoutingCatalogId.yaml
- contracts/charter/scenarios/routing-catalogs.feature
- contracts/charter/scenarios/effective-routing-catalogs.feature
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/RoutingCatalogsController.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/EffectiveRoutingCatalogsController.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/RoutingCatalogBodies.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/PacksRoutingCatalogs.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/EffectiveRoutingCatalogReads.java
execution_mode: code_change
owned_files:
- contracts/charter/openapi.yaml
- contracts/charter/paths/packs_packId_artifacts_routing_catalog.yaml
- contracts/charter/paths/packs_packId_artifacts_routing_catalog_artifactId.yaml
- contracts/charter/paths/activations_activationId_artifacts_routing_catalog.yaml
- contracts/charter/paths/activations_activationId_artifacts_routing_catalog_artifactId.yaml
- contracts/charter/parameters/RoutingCatalogId.yaml
- contracts/charter/parameters/_index.yaml
- contracts/charter/schemas/RoutingCatalog*.yaml
- contracts/charter/schemas/EffectiveRoutingCatalogRead.yaml
- contracts/charter/schemas/Artifact.yaml
- contracts/charter/schemas/ArtifactSummary.yaml
- contracts/charter/schemas/Activation.yaml
- contracts/charter/schemas/_index.yaml
- contracts/charter/examples/_index.yaml
- contracts/charter/examples/*RoutingCatalog*.yaml
- contracts/charter/examples/Activation.*.yaml
- contracts/charter/headers/EffectiveETag.yaml
- contracts/charter/headers/PackRevisionETag.yaml
- contracts/charter/scenarios/routing-catalogs.feature
- contracts/charter/scenarios/effective-routing-catalogs.feature
- contracts/charter/scenarios/every-operation.feature
- contracts/charter/scenarios/unexpected-failure.feature
- contracts/charter/scenarios/read-refusals.feature
- contracts/charter/scenarios/activation-links.feature
- contracts/charter/scenarios/switch-gate.yaml
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/RoutingCatalog*.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/EffectiveRoutingCatalogReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/ActivationArtifacts.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/PacksRoutingCatalogs.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/activations/ActivationsArtifacts.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/RoutingCatalogsController.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/EffectiveRoutingCatalogsController.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/RoutingCatalogBodies.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/EffectiveBodies.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/EffectiveJson.java
- service/artifacts-edge/src/test/java/**/*RoutingCatalog*Test.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactJson.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveTag.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationMapper.java
role: implementer
task_type: implement
---

# Work Package Prompt: WP08 – The catalog reads

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according to
its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ Review Feedback

Check the `review_ref` field in the event log (`spec-kitty agent tasks status`). Address every feedback item, and record
each change with `spec-kitty agent tasks add-history WP08 --note "..."`.

## Branch Strategy

- **Planning base branch**: `feat/108-model-task-routing`
- **Merge target branch**: `feat/108-model-task-routing`
- The execution worktree is allocated per computed lane from `lanes.json`; never pick a base branch by hand.

```bash
spec-kitty agent action implement WP08 --agent claude --mission model-task-routing-01M7PVHQ
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

Serve the catalog through four literal reads, modelled on the skill reads (ADR-0074 decisions 9–14; ADR-0080
decision 6). All four are `ready` in the switch gate (FR-003, FR-004, FR-014; plan IC-02; squad findings C1, C5, C7,
C8, A6).

**This WP owns the shared registry files.** `openapi.yaml`, `switch-gate.yaml`, `every-operation.feature`,
`unexpected-failure.feature` and `read-refusals.feature` are this WP's; WP09 adds its rows to them after this WP
merges.

## Precedent to copy

The skill reads, at these paths:

- path files: `contracts/charter/paths/{packs_packId,activations_activationId}_artifacts_skill{,_artifactId}.yaml`;
- schemas: `Skill.yaml`, `SkillSummary.yaml`, `SkillList.yaml` and `EffectiveSkillRead.yaml`;
- pack tree: `artifacts-edge` `SkillReads`, `SkillView`, `PacksSkills`, `SkillsController` and `SkillBodies`;
- activation tree: `EffectiveSkillReads`, `ActivationArtifacts.skill`, `ActivationsArtifacts`, `WinnerReads`,
  `EffectiveSkillsController`, `EffectiveBodies` and `EffectiveJson`;
- activation plumbing: `EffectiveReadService` (lines 756, 778, 781, 856, 1028), `EffectiveTag` (`Read` constants at
  lines 82 and 87; guards at lines 480 and 499) and `ActivationMapper` (line 161).

## Subtasks

### T027 – Contract

- **`openapi.yaml`:**
  - tags `RoutingCatalogs` and `EffectiveRoutingCatalogs`, each with a description naming the module that implements
    it (`artifacts-edge`), as the skill tags at lines 58 and 85 do;
  - the four paths, beside the skill paths (lines 165–168 and 223–226).
- **The four path files.** Their statuses follow `kitty-specs/.../contracts/operations.md`:
  - **pack reads:** `200` with `PackRevisionETag`, `Charter-Source-Fingerprint` and `Charter-Worktree`; then `400`,
    `401`, `403`, `404` and `500`;
  - **twins:** `IfNoneMatch`, with `200` carrying `EffectiveETag`, `Charter-Source-Fingerprint` and
    `Charter-Worktree`; then `304`, `400`, `401`, `403`, `404` and `500`;
  - add the four operation names to `headers/EffectiveETag.yaml` and `headers/PackRevisionETag.yaml`.
- **Parameter.** `parameters/RoutingCatalogId.yaml`, with the kebab-case id pattern and `maxLength` 64. Do not reuse
  `AssetOrSkillId`. Register it in `parameters/_index.yaml`.
- **Schemas**, every property with `x-source` (rule `resource-property-cited`), camel case on the wire:
  - `RoutingCatalog`, with its parts (`RoutingCatalogTaskType`, `RoutingCatalogModel`, `RoutingCatalogTaskFit`,
    `RoutingCatalogCost`, `RoutingCatalogPolicy`, `RoutingCatalogWeights`, `RoutingCatalogTierConstraint`,
    `RoutingCatalogSource`). Cost tiers use WP04's `CostTier.yaml`. Scores and weights are `type: number`, from 0 to 1.
  - `RoutingCatalogSummary`: id, description, `objective`, `taskTypeCount` and `modelCount`.
  - `RoutingCatalogList`.
  - `EffectiveRoutingCatalogRead`: the catalog, with `pack`, `level`, `state`, `reason`, `policy` and `required`, as
    `EffectiveSkillRead`. `EffectiveArtifactCopiesParityTest` names the effective copies; add it there.
- **Existing schemas.** Add the kind to `Artifact.yaml` (`kind` enum, prose, and a `oneOf` member
  `routing_catalog: $ref RoutingCatalog.yaml`, at lines 36, 50–53 and 85) and to the `ArtifactSummary.yaml` prose.
  Register every schema in `schemas/_index.yaml`.
- **Examples.** Write examples from the example packs: `RoutingCatalog.org-base-default.yaml`,
  `RoutingCatalogList.org-base.yaml`, `RoutingCatalogList.activation.yaml` and
  `EffectiveRoutingCatalogRead.default.yaml`. Register them in `examples/_index.yaml`.
- **The activation link.** `routing-catalogs`, a relation WP03 registered, in `Activation.yaml`, in every
  `examples/Activation.*.yaml` that lists the per-kind links, and in `activation-links.feature` (near line 118).

### T028 – Pack reads (`artifacts-edge`)

- Port `RoutingCatalogReads`, view `RoutingCatalogView`, adapter `PacksRoutingCatalogs` (`packs.held(pack,
  ArtifactKind.ROUTING_CATALOG)`), controller `RoutingCatalogsController` (implements the generated
  `RoutingCatalogsApi`) and wire mapper `RoutingCatalogBodies`.
- Numbers go to the wire as `BigDecimal`, in plain notation.
- The package scans in `ArtifactsApplicationConfiguration`, `ArtifactsApiConfiguration` and
  `ArtifactsPacksConfiguration` pick the classes up. Check, and add an entry if one lists classes.

### T029 – Effective twins and the activation link

- `EffectiveRoutingCatalogReads` (`winners(activationId, ROUTING_CATALOG, …)`), `ActivationArtifacts.routingCatalog`
  and its `Winner`, the `ActivationsArtifacts` adapter, `EffectiveRoutingCatalogsController` and the links in
  `EffectiveBodies`.
- In `activations`:
  - `EffectiveReadService`: the item tag case, the list tag, `heldWinners`, `heldWinner` and `listedKinds`;
  - `EffectiveTag`: `Read.ROUTING_CATALOG` and `ROUTING_CATALOGS`, with their guards at lines 480 and 499;
  - `ActivationMapper`: `links.put("routing-catalogs", …)`.
- The literal path must win over the templated read. Extend `PackArtifactRoutingTest` and `ActivationRoutingTest`
  (`service/deployable`) as recorded edits outside the map.

### T030 – Null omission and native hints

`description`, `family`, `confidence`, `rationale`, `latencyTier` and the cost's optional members are absent when
undeclared, never `null`.

- Add mix-ins to `ArtifactJson` (`packs/infra/api`, lines 51–54) and to `EffectiveJson`, with their `RuntimeHints`.
- Add a row to `NullOmissionTest` if it enumerates DTOs, as a recorded edit outside the map.

### T031 – Scenarios and the switch gate

- **`routing-catalogs.feature`:**
  - list and item in built-in and in org-base, asserting the full body of one catalog;
  - `404 pack_not_found` and `404 artifact_not_found`;
  - `400` for a malformed id;
  - the locked policy refusing a same-id override: register a pack fixture whose policy locks `default`, and expect
    the mutability refusal (FR-005);
  - an `overrides` relationship between catalogs refused `relationship_endpoint_not_allowed` (squad A1).
- **`effective-routing-catalogs.feature`:**
  - the org-base `default` wins over built-in's, with pack, level, state, policy and required;
  - `cost-led` is listed `inactive` / `not_selected` until it is selected;
  - `?kind=routing_catalog` in both trees;
  - the `304` outline, as in `effective-assets-and-skills.feature` near line 249;
  - `404 activation_not_found` and `404 artifact_not_found`.
- **Rows** for the four operations in `every-operation.feature` (401 and 403), `unexpected-failure.feature` (500) and
  `read-refusals.feature`.
- **`switch-gate.yaml`.** Add `listPackRoutingCatalogs`, `getPackRoutingCatalog`, `listEffectiveRoutingCatalogs` and
  `getEffectiveRoutingCatalog`, each `{switch: ready}`. The suite fails if a declared status is not asserted, so
  assert every one.

## Definition of Done

- The four reads are served and documented, with their scenarios, and all four are `ready`.
- `./gradlew verifyAll` passes on Java 25 for the modules this WP touches, with no new style-gate finding.
- Every subtask's status is recorded with `spec-kitty agent tasks mark-status`.
- Nothing outside `owned_files` changed, except a recorded edit outside the map, named in the WP history with its
  reason.

## Reviewer Guidance

- Diff each new path file against its skill twin: every difference must be deliberate.
- Run the switch-gate check, and confirm `ready` for all four with no held status.
- **Independent reviewer.** Review is by a separate reviewer session, never by the implementer.
