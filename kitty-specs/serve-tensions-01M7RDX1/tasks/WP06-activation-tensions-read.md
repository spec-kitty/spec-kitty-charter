---
work_package_id: WP06
title: The activation's tensions read
dependencies:
- WP05
requirement_refs:
- FR-009
- FR-010
- C-003
- C-005
planning_base_branch: feat/602-tensions
merge_target_branch: feat/602-tensions
branch_strategy: Planning artifacts for this mission were generated on feat/602-tensions. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/602-tensions unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-serve-tensions-01M7RDX1
base_commit: c05ad96ab6cd3cb9755bdf5e7b2e892b9087f933
created_at: '2026-11-19T12:58:33.523608+00:00'
subtasks:
- T029
- T030
- T031
- T032
- T033
- T034
agent: claude
history:
- at: '2026-11-19T07:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/application/TensionReadService.java
create_intent:
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/TensionFinding.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/TensionReadService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/TensionReadMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/TensionFindingTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/TensionReadServiceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/TensionReadMapperTest.java
- contracts/charter/paths/activations_activationId_tensions.yaml
- contracts/charter/schemas/ActivationTensions.yaml
- contracts/charter/schemas/TensionFinding.yaml
- contracts/charter/examples/ActivationTensions.empty.yaml
- contracts/charter/examples/ActivationTensions.unreconciled.yaml
execution_mode: code_change
owned_files:
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/TensionFinding.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/TensionReadService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/TensionReadMapper.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationController.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/TensionFindingTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/TensionReadServiceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/TensionReadMapperTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationMapperLinksTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationControllerTest.java
- contracts/charter/paths/activations_activationId_tensions.yaml
- contracts/charter/openapi.yaml
- contracts/charter/schemas/_index.yaml
- contracts/charter/examples/_index.yaml
- contracts/charter/schemas/ActivationTensions.yaml
- contracts/charter/schemas/TensionFinding.yaml
- contracts/charter/schemas/Activation.yaml
- contracts/charter/examples/Activation.minimal.yaml
- contracts/charter/examples/ActivationTensions.empty.yaml
- contracts/charter/examples/ActivationTensions.unreconciled.yaml
- contracts/charter/headers/CharterSourceFingerprint.yaml
- contracts/charter/scenarios/every-operation.feature
- contracts/charter/scenarios/read-refusals.feature
- contracts/charter/scenarios/unexpected-failure.feature
- contracts/charter/scenarios/activation-links.feature
- contracts/charter/scenarios/switch-gate.yaml
- contracts/lint/ruleset.yaml
- docs/development/hypermedia-hateoas.md
role: implementer
tags: []
tracker_refs:
- '#602'
---

# Work Package Prompt: WP06 - The activation's tensions read

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else.

Then read, in `kitty-specs/serve-tensions-01M7RDX1/`: `spec.md`, `plan.md` (its last section, "Revisions after the
plan-point squad and rulings G1 to G3", wins over the concerns above it), `data-model.md`, `research.md`,
`research/squad-plan.md`; and `docs/adr/0045-tension-beside-the-relationships-and-nodes-keep-every-declared-field.md`
(accepted, with rulings G1 to G3). Read `.kittify/charter/charter.md` and `AGENTS.md`.

Charter rules that bind every subtask:

- The charter's model is canonical: no reader of `in_tension_with`, no shim, no deprecation window.
- Tests and examples carry only the canonical shape, except error-handling fixtures. Never exclude a field from a
  parity or round-trip check to make a test pass.
- Tests are held to the production bar: outcome assertions, a positive control beside every refusal, no fake green.

## Branch Strategy

- Planning base and final merge target: `feat/602-tensions` (PR #606 to `main`). The mission branch is
  `kitty/mission-serve-tensions-01M7RDX1`.
- The execution worktree is allocated per computed lane from `lanes.json`.
- Start with `spec-kitty agent action implement WP06 --agent claude --mission serve-tensions-01M7RDX1`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names. Conventional commit subjects naming
  `(#602)`.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time. Before `for_review`, run the module tests
  you touched and `./gradlew verifyAll --max-workers=1` when the WP changes the contract.
- An edit outside `owned_files` is allowed only when small and recorded in the WP's activity log with a one-line
  reason.


## Objective

A new read, `GET /api/v1/activations/{activationId}/tensions` (`getActivationTensions`), answers every tension in
force and one `unreconciled_tension` finding per tension nothing in force reconciles, with its resolutions and links.
It never refuses for a finding (ADR-0045 decisions 11 and 12, ruling G3; spec FR-009, FR-010, C-003, US1; plan IC-07
and the Revisions section).

## Context (re-check each line)

- Template: the skipped report. `paths/activations_activationId_skipped.yaml` (operationId, tag Activation,
  `IfNoneMatch`, 200 with Charter-Contract-Version, Charter-Worktree, Charter-Source-Fingerprint and
  `ETag: ../headers/EffectiveETag.yaml`, 304, 400, 401, 403, 404 `UnknownActivation`, 500 `OfferingUnreadable`,
  default); `openapi.yaml:249-251`; `EffectiveReadService.skipped` (:481-492) with `EffectiveTag.skipped`;
  `ActivationController` (:169-176); `EffectiveReadMapper.toSkipped` (:138-146).
- `ActivationMapper.activationLinks` (:127-170) is an ordered `LinkedHashMap`; add `tensions` after `skipped`.
- Registries: `contracts/lint/ruleset.yaml:259-321` (link relation enum), `docs/development/hypermedia-hateoas.md`
  "Relation names" table (:114; `RelationTable.java` in deployable bdd reads it as the source of relation keys;
  `exclude`/`include` at :166, `effective-artifact` at :169).
- The exclusion write `deactivateActivationArtifact`
  (`paths/activations_activationId_exclusions_kind_artifactId.yaml:13-16`) refuses a locked side (409
  `artifact_locked`) and a required side (409 `artifact_required`). The predicates live in
  `ActivationExclusionService` / `DocumentRules`; reuse them, never re-derive them.
- Shared files with charter #108 (PR #600): `openapi.yaml`, `EffectiveTag`, `ActivationMapper`, the operation
  features, `switch-gate.yaml`, the ruleset, the hypermedia table. Append; on rebase keep both sides (C-005).
- `EffectiveTag.java` is WP05's: adding `Read.TENSIONS` and its factory is a recorded out-of-map edit. Same for
  `EffectiveETag.yaml`'s operation list (WP05's file).

## Subtasks

### T029: The finding model (`activations.domain`)

`TensionFinding(NodeRef source, NodeRef target, List<Resolution> resolutions, boolean sourceExcludable, boolean targetExcludable)`,
`enum Resolution { RECONCILE, DEACTIVATE_ONE_SIDE }`, `code` constant `unreconciled_tension`.
- `RECONCILE` always; `DEACTIVATE_ONE_SIDE` only when at least one side is excludable (G3).
- A side is excludable when the exclusion write would accept it: not locked and not required, judged with the same
  predicates the exclusion write uses on the same activation document and closure.

### T030: The read

1. `EffectiveTag.Read.TENSIONS` and `EffectiveTag.tensions(revision, closure)`: the skipped report's inputs, its own
   read constant, so its tag differs from the skipped report's (out-of-map, recorded).
2. `TensionReadService` (`activations.application`): `found`, `open`, `snapshot`, the tag and 304 check, then
   `session.tensions(query)` (WP05) and the excludability for each unreconciled side. One closure resolution (NFR-004).
3. `ActivationController.getActivationTensions` and `TensionReadMapper` to the generated DTOs.

### T031: The contract

1. `paths/activations_activationId_tensions.yaml`: every status of the skipped report's path, and the same headers.
2. `ActivationTensions.yaml`: `activationId`, `revision`, `governanceFingerprint` (copy the skipped report's closure
   digest description, `ActivationSkippedReport.yaml:94-107`), `tensions` (array of `ContextTension`, WP05),
   `findings` (array of `TensionFinding`), `_links` (`$ref Links.yaml`, `self`). All with `x-derived`.
3. `TensionFinding.yaml`: `code` (enum `unreconciled_tension`), `source`, `target`, `resolutions` (array of enum
   `reconcile` | `deactivate_one_side`, unique, in that order, `reconcile` always first), `_links` (`$ref Links.yaml`;
   the description names `effective-source`, `effective-target`, and `exclude-source`/`exclude-target` present only
   for an excludable side, each a concrete href with method PUT, needing If-Match: the answer's `revision` quoted is a
   valid If-Match, and an empty `ExclusionRequest` body).
4. `openapi.yaml`, `schemas/_index.yaml`, `examples/_index.yaml`; examples `ActivationTensions.empty.yaml` and
   `.unreconciled.yaml`; `Activation.yaml`'s `_links` prose lists `tensions`; `Activation.minimal.yaml` gains it;
   `headers/CharterSourceFingerprint.yaml` and `EffectiveETag.yaml` op lists name `getActivationTensions`.

### T032: Links and registries

`ActivationMapper`: `tensions` → `PATH_GET_ACTIVATION_TENSIONS`. Add `tensions`, `exclude-source`, `exclude-target`,
`effective-source`, `effective-target` to the ruleset's relation enum and the hypermedia table (with meanings), so
`RelationTable` and its parity checks pass.

### T033: Operation rows

- `every-operation.feature`: 401 and 403 rows.
- `read-refusals.feature`: 400 (malformed id) and 304 rows; 404 for an unknown activation (a behavioural scenario if
  that is where 404s live for the skipped report).
- `unexpected-failure.feature`: 500 row.
- `activation-links.feature`: `tensions` in the prose and the Examples table; following it answers 200.
- `switch-gate.yaml`: `getActivationTensions: {switch: ready}`. A scenario asserts its ETag differs from the skipped
  report's on the same activation.

### T034: Unit tests

`TensionFindingTest`: both sides excludable → both resolutions; one locked → still both, one link; both locked or
required → `[reconcile]`. `TensionReadServiceTest`: 304 on a matching tag; findings only for unreconciled tensions.
`TensionReadMapperTest`: links per excludable side. `ActivationMapperLinksTest`: `tensions` present and ordered.

## Definition of Done

`./gradlew :activations:test :deployable:test --max-workers=1` and `verifyAll` pass, including the switch gate and the
link checks.

## Risks and reviewer guidance

- Never advertise a link the write would refuse (hypermedia guide :49, :189).
- Reviewers: follow `exclude-source` from a finding with the answer's revision as If-Match; it must succeed (WP07
  proves it end to end).
