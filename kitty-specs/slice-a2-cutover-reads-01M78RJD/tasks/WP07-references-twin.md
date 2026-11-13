---
work_package_id: WP07
title: The references twin (#532)
dependencies:
- WP03
- WP06
requirement_refs:
- FR-012
planning_base_branch: slice-a2-536-cutover-reads
merge_target_branch: slice-a2-536-cutover-reads
branch_strategy: Planning artifacts for this mission were generated on slice-a2-536-cutover-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-a2-536-cutover-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-slice-a2-cutover-reads-01M78RJD
base_commit: 152bd080e4c9dc30fd89d28c0c396a291cf92f90
created_at: '2026-11-13T01:14:27.436012+00:00'
subtasks:
- T031
- T032
- T033
- T034
phase: ''
history:
- at: '2026-11-12T22:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/
create_intent:
- contracts/charter/paths/activations_activationId_artifacts_kind_artifactId_references.yaml
- service/packs/src/main/java/ai/speckitty/charter/packs/application/WinnerReferences.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/WinnerReferencesTest.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/EffectiveReferenceReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/PacksReferences.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/EffectiveReferencesController.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/EffectiveReferencesControllerTest.java
- contracts/charter/scenarios/effective-references.feature
- contracts/charter/changes/2026-11-12-effective-references.md
execution_mode: code_change
owned_files:
- contracts/charter/paths/activations_activationId_artifacts_kind_artifactId_references.yaml
- service/packs/src/main/java/ai/speckitty/charter/packs/application/WinnerReferences.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/WinnerReferencesTest.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/EffectiveReferenceReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/PacksReferences.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/EffectiveReferencesController.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/EffectiveReferencesControllerTest.java
- contracts/charter/scenarios/effective-references.feature
- contracts/charter/changes/2026-11-12-effective-references.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP07 - The references twin (#532)

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/slice-a2-cutover-reads-01M78RJD/`:

- `spec.md`
- `plan.md`, its concern for this WP
- `research.md`
- `research/squad-post-plan.md`

## Objective

`listEffectiveArtifactReferences`: `GET /activations/{a}/artifacts/{kind}/{artifactId}/references` (ADR-0074 decision 12; research.md R1 and R10; FR-012).

The answer is the pack references read (`ArtifactReadService.references`) of the closure winner's pack, in the same `ArtifactReferenceList` schema and through the same `Reference` record and mapper semantics, read against the session fingerprint. Each item adds an `effective-artifact` link when the closure holds the target and its kind is listed. The read answers whatever the artifact's state. `404`s: `activation_not_found`, `kind_not_found` (a kind that is not listed) and `artifact_not_found` (not held by the closure).

## Context

- `ArtifactReadService.references()` (:517) and `resolved` (:549-581) give the written spelling, the compose-resolved holder, the graph-document edges and `derivedRelationships()`.
- The pack controller is `ArtifactController.java:113`.
- The winner and fingerprint come from WP06's `EffectiveReadService` (reuse the winner query for a listed kind: `merged.winners()` for an activatable kind, held winners otherwise).
- Squad finding #3: one rule per fact. `ReferencedArtifact`'s `x-source` must stay true.

## Subtasks

### T031: Contract

Write the path file, registered in `openapi.yaml` after the activation templated item. It joins an `EffectiveReferences` tag. Its description: "the references of the version in force: the pack read of the winner's pack". Examples.

### T032: Verified references read

Write `WinnerReferences` in `packs.application`. It calls `ArtifactReadService.references(winnerPack, kind, id)` inside a `ConsistentRead`, refusing `STALE_INPUT` when the source fingerprint differs from the expected one.

- If `references` needs a package-private seam, add it to `ArtifactReadService` as a recorded out-of-map edit (WP02 owns the file). Keep it to one method.
- Unit test it.

### T033: Controller

- `EffectiveReferencesController` in `:artifacts-edge`: the winner from `infra.activations`, the references from `infra.packs`.
- Item links: `artifact` (as the pack read gives it) and `effective-artifact` (`/activations/{a}/artifacts/{kind}/{id}`, or the literal path for agent_profile, mission_type, asset and skill).
- Answer links: `self` and `up`.

### T034: Scenarios

Write `contracts/charter/scenarios/effective-references.feature`:

- directive `D` requires tactic `T`: `T` is answered with `requires` and an `effective-artifact` link;
- an overridden `D` (a project copy with another edge): the winner's references;
- **parity:** the same referrer read in its pack and under the activation gives equal `items` (ignoring links), including an agent profile's field-derived references;
- the 404s.

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
./gradlew --max-workers=1 :contracts:check :packs:test :artifacts-edge:test
./gradlew --max-workers=1 :deployable:test   # contract scenarios, switch gate, link checks, ArchUnit
```
Run Gradle in the foreground, one run at a time; never `--stop`.

## Definition of Done

- The operation is `ready`.
- The parity scenario passes.
- No second references computation exists.

## Risks and reviewer guidance

Check that the twin cannot disagree with the pack read for the winner's pack. That is the whole design.

## Branch Strategy

- **Planning base and merge target:** `slice-a2-536-cutover-reads`.
- **Mission branch:** `kitty/mission-slice-a2-cutover-reads-01M78RJD`.
- **Worktrees:** allocated per computed lane (`lanes.json`).
- **Implement with:** `spec-kitty agent action implement WP07 --agent claude --mission slice-a2-cutover-reads-01M78RJD`.
- **Commits:** sign them if signing works. Add no model or `Co-Authored-By` trailer. Reference the issue, for example `(#532)`.

## Coordination

Slice C (#467) is in flight and changes:

- the `prompt` path and link relation;
- four schemas;
- `mission_type_not_in_force` in `CharterRefusalCode`;
- `ClosureQueries.closureFingerprint` ordering.

Do not use or change those names. Do not pin fingerprint values in tests.
