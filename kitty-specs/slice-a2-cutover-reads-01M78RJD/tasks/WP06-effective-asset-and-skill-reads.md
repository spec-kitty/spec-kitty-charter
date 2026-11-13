---
work_package_id: WP06
title: Activation twins of the asset and skill reads
dependencies:
- WP04
- WP05
requirement_refs:
- FR-009
- FR-010
- FR-011
planning_base_branch: slice-a2-536-cutover-reads
merge_target_branch: slice-a2-536-cutover-reads
branch_strategy: Planning artifacts for this mission were generated on slice-a2-536-cutover-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-a2-536-cutover-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-slice-a2-cutover-reads-01M78RJD
base_commit: 9245ddccd9188f415d8b07fe7f0da7f54023ffbd
created_at: '2026-11-13T00:16:11.472368+00:00'
subtasks:
- T008
- T026
- T027
- T028
- T029
- T030
phase: ''
history:
- at: '2026-11-12T22:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/application/
create_intent:
- contracts/charter/paths/activations_activationId_artifacts_asset.yaml
- contracts/charter/paths/activations_activationId_artifacts_asset_artifactId.yaml
- contracts/charter/paths/activations_activationId_artifacts_asset_artifactId_payload.yaml
- contracts/charter/paths/activations_activationId_artifacts_skill.yaml
- contracts/charter/paths/activations_activationId_artifacts_skill_artifactId.yaml
- contracts/charter/schemas/EffectiveAssetRead.yaml
- contracts/charter/schemas/EffectiveSkillRead.yaml
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureViewHeldWinnersTest.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveHeldWinner.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadServiceHeldTest.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/EffectiveAssetReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/EffectiveSkillReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/activations/ActivationsArtifacts.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/EffectiveAssetsController.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/EffectiveSkillsController.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/application/EffectiveAssetReadsTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/EffectiveAssetsControllerTest.java
- contracts/charter/scenarios/effective-assets-and-skills.feature
- contracts/charter/changes/2026-11-12-effective-asset-and-skill-reads.md
execution_mode: code_change
owned_files:
- contracts/charter/paths/activations_activationId_artifacts_asset.yaml
- contracts/charter/paths/activations_activationId_artifacts_asset_artifactId.yaml
- contracts/charter/paths/activations_activationId_artifacts_asset_artifactId_payload.yaml
- contracts/charter/paths/activations_activationId_artifacts_skill.yaml
- contracts/charter/paths/activations_activationId_artifacts_skill_artifactId.yaml
- contracts/charter/schemas/EffectiveAssetRead.yaml
- contracts/charter/schemas/EffectiveSkillRead.yaml
- contracts/charter/examples/Effective*Read.asset.yaml
- contracts/charter/examples/Effective*Read.skill.yaml
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureViewHeldWinnersTest.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/PacksSession.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveHeldWinner.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveTag.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSession.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadServiceHeldTest.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/EffectiveAssetReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/EffectiveSkillReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/activations/ActivationsArtifacts.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/EffectiveAssetsController.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/EffectiveSkillsController.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/application/EffectiveAssetReadsTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/EffectiveAssetsControllerTest.java
- contracts/charter/scenarios/effective-assets-and-skills.feature
- contracts/charter/changes/2026-11-12-effective-asset-and-skill-reads.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP06 - Activation twins of the asset and skill reads

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/slice-a2-cutover-reads-01M78RJD/`:

- `spec.md`
- `plan.md`, its concern for this WP
- `research.md`
- `research/squad-post-plan.md`

## Objective

Add the activation twins (ADR-0074 decision 12; research.md R1, R2, R3 and R15; operator ruling `DM-01M78S3RNPZ5ZFV5AR6H1X6HV1`; FR-009 to FR-011).

| Request | Answer |
|---|---|
| `GET /activations/{a}/artifacts/skill` (`listEffectiveSkills`, tag `EffectiveSkills`) | `200 SkillList`, the closure's skill winners in id order (a summary per winner; an item links `effective-artifact` and `artifact`) |
| `GET /activations/{a}/artifacts/skill/{id}` (`getEffectiveSkill`) | `200 EffectiveSkillRead`: the members of `EffectiveArtifactRead`, plus `skill`; whatever its state (R1); `ETag`, `304` |
| `GET /activations/{a}/artifacts/asset` (`listEffectiveAssets`, tag `EffectiveAssets`) | `200 AssetList` of the closure's held asset winners after the layer merge |
| `GET /activations/{a}/artifacts/asset/{id}` (`getEffectiveAsset`) | `200 EffectiveAssetRead` (R2: no state, reason, required or suggestion; `policy` kept; `kind` const `asset`); `ETag`, `304` |
| `GET …/asset/{id}/payload` (`getEffectiveAssetPayload`) | the winner's bytes, as WP04's payload, with a verified read (R15); `304` |
| templated `GET /activations/{a}/artifacts/asset/{id}` via `{kind}` | not reachable over HTTP (the literal route wins); in the service, `kind_not_found`, which names the literal path (T008) |
| no activation / not held by the closure | `404 activation_not_found` / `404 artifact_not_found` |

## Context

- `service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java`:
  - :373 is the token form;
  - :403 is the kind form;
  - :518 `artifactOf` (winners, ETag, `EffectiveSet`);
  - :429 `missionTypeWinner` is the winner-plus-fingerprint pattern.
- `service/activations/src/main/java/ai/speckitty/charter/activations/application/PacksSession.java` and `service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSession.java`: the activation side keeps only activatable winners (`activatable(urn)`, about :429).
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java`:
  - :658 `effectiveArtifact` answers any kind;
  - :690 `winnerPackOf`;
  - :704 the decision that assets are never listed by the effective list.
- `EffectiveTag` has `Read` values `PROFILE` and `ARTIFACT`; add one per new read so no two URLs share a tag.
- Missions-edge precedent: `ActivationsMissionTypes` asks `missionTypeWinner`, and the packs side reads the body against the fingerprint.

## Subtasks

### T008: The templated effective gate

In `EffectiveReadService.artifact(id, kindToken, …)`, gate on activatable ∩ listed. An `asset` token is `kind_not_found`, with "read it at /activations/<a>/artifacts/asset/<id>". Unit test it.

### T026: Contract

- Write the five path files, the `EffectiveAssets` and `EffectiveSkills` tags, `EffectiveAssetRead`, `EffectiveSkillRead` (a flat, closed copy of the `EffectiveArtifactRead` members plus `skill`, as `EffectiveAgentProfileRead` is) and the examples.
- The descriptions state R1 and R2 and the fact that an asset is in force when a pack of the closure holds it.

### T027: Winners

- On `ClosureView`, add `heldWinners(ArtifactKind)`: the merged winners of one kind, any kind, in id order, with holder and level.
- On `PacksSession` (`ClosureViewSession`), add a method returning `EffectiveHeldWinner(ref, packId, level, policy)` values for a non-activatable kind (list and one), plus `snapshot().fingerprint()`. These are activations-domain values; activations names packs only in `infra.packs`.
- In `EffectiveReadService`, add `heldWinner(id, kind, artifactId, ifNoneMatch)` and `heldWinners(id, kind)`, with new `EffectiveTag` reads. For skill, reuse `artifact(id, SKILL, …)` with a new `EffectiveTag.Read.SKILL` when it is called from the skill route (as `profileRoute` is).

### T028: Verified payload

- The edge's `infra.activations` asks for the winner and fingerprint.
- `infra.packs` calls `ForeignArtifactRead.payload(winnerPack, id, fingerprint)` from WP04: a mismatch is `500 stale_input`.
- Unit-test the mismatch.

### T029: Controllers

- Write `EffectiveAssetsController` and `EffectiveSkillsController`, reusing `AssetBodies`, `SkillBodies` and `PayloadResponses`.
- Links:
  - `self`;
  - `up` (the activation);
  - `artifact` (the winner pack's literal read);
  - `collection`;
  - `payload` for an asset.

### T030: Scenarios

Write `contracts/charter/scenarios/effective-assets-and-skills.feature`:

- a project pack copy of asset `run` wins over the built-in copy, for the item and the payload bytes;
- a skill selected (in force) and a skill not selected (answered, with state `inactive`);
- `304` on each;
- the 404s;
- `?kind=asset` on the generic effective list is `400`, as today (pin it, R3).

Add the registry rows and the fragment.

**Shared registries (out-of-map rows, recorded).** Add rows for this WP's operations to the files WP01 owns:

- `contracts/charter/openapi.yaml`
- `contracts/charter/scenarios/switch-gate.yaml` (`ready`)
- `every-operation.feature`, `unexpected-failure.feature` and `read-refusals.feature`
- the relation table and the lint enum, only for relations this WP introduces

Record each edit in the commit message as "out-of-map: registry rows for <operationIds>". Keep each file's existing order: path order in openapi.yaml, and the table orders.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :contracts:check :packs:test :activations:test :artifacts-edge:test
./gradlew --max-workers=1 :deployable:test   # contract scenarios, switch gate, link checks, ArchUnit
```
Run Gradle in the foreground, one run at a time; never `--stop`.

## Definition of Done

- All five operations are `ready`.
- The layer override is proven for asset bytes.
- The stale read is refused.
- ArchUnit is green.

## Risks and reviewer guidance

- No state is invented for assets.
- The ETag discriminators are distinct.
- The payload is read against the session's fingerprint, never on its own.

## Branch Strategy

- **Planning base and merge target:** `slice-a2-536-cutover-reads`.
- **Mission branch:** `kitty/mission-slice-a2-cutover-reads-01M78RJD`.
- **Worktrees:** allocated per computed lane (`lanes.json`).
- **Implement with:** `spec-kitty agent action implement WP06 --agent claude --mission slice-a2-cutover-reads-01M78RJD`.
- **Commits:** sign them if signing works. Add no model or `Co-Authored-By` trailer. Reference the issue, for example `(#105)`.

## Coordination

Slice C (#467) is in flight and changes:

- the `prompt` path and link relation;
- four schemas;
- `mission_type_not_in_force` in `CharterRefusalCode`;
- `ClosureQueries.closureFingerprint` ordering.

Do not use or change those names. Do not pin fingerprint values in tests.
