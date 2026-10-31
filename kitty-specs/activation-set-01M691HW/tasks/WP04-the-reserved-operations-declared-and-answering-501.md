---
work_package_id: WP04
title: The reserved operations, declared and answering 501
dependencies:
- WP01
- WP02
- WP03
requirement_refs:
- FR-005
- FR-007
- FR-008
- FR-009
- FR-010
- FR-015
- NFR-001
- C-001
- C-002
- C-003
planning_base_branch: issue-125-activation-set
merge_target_branch: issue-125-activation-set
branch_strategy: Planning artifacts for this mission were generated on issue-125-activation-set. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-125-activation-set unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-activation-set-01M691HW
base_commit: d0c409c862bcd6c557e9100aba8d74fa8076d1a9
created_at: '2026-10-31T14:07:57.601610+00:00'
subtasks:
- T020
- T021
- T022
- T023
- T024
- T025
- T026
phase: Phase 2 - Declare
agent: claude
history:
- at: '2026-10-31T13:45:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/paths/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ActivationController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ReservedOperation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ReservedOperationInterceptor.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ActivationConfigConformanceTest.java
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/openapi.yaml
- contracts/charter/paths/activations*.yaml
- contracts/charter/parameters/**
- contracts/charter/headers/**
- contracts/charter/responses/**
- contracts/charter/examples/**
- contracts/charter/schemas/CharterRefusalCode.yaml
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalCode.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RefusalHandler.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ActivationController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ReservedOperation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ReservedOperationInterceptor.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/config/**
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedRefusalCodesTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ContractDescriptionsTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ActivationConfigConformanceTest.java
- service/deployable/build.gradle.kts
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP04 – The reserved operations, declared and answering 501

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave
according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Declare the 13 operations of `contracts/activation-operations.md` in full under the tag
`Activation`, add the six reserved codes to contract and service, and answer every operation with
`501` before its request is bound (FR-005, FR-007 to FR-010, FR-015, C-001 to C-003, SC-001,
SC-002). The red scenarios and lint fixtures of WP01/WP02 turn green here.

## Branch Strategy

Planning base and merge target: `issue-125-activation-set`. Lanes from `lanes.json`.

## Subtasks

### T020 — Parameters, headers, responses

- `parameters/`: `ActivationId` (path, pattern ADR-0020 D2), `ActivatableKindPath`,
  `SelectableKindPath`, `ArtifactIdPath`, `PresetId`, `MissionTypePath`, `ActionPath`,
  `IfMatch` (header, `required: false`, pattern `^(\*|"sha256:[0-9a-f]{64}")$`, 428 when
  absent on a sub-resource write), `IfNoneMatchCreate` (`^\*$`), `EffectiveKindQuery`,
  `EffectiveStateQuery`, `Depth` (bounded as ADR-0018), and reuse the `_shared` page
  parameters if they exist (else declare beside them).
- `headers/`: `ETag` (quoted pattern), `Location`.
- `responses/`: `ActivationNotFound` (404; examples for `activation_not_found`,
  `artifact_not_found`, `preset_not_found`, `mission_type_not_found`, `action_not_found`),
  `ArtifactPolicyRefused` (409; example with `requiredBy`), `ActivationInvalid` (422; example
  with `violations`), `PreconditionFailed` (412), `PreconditionRequired` (428),
  `UnsupportedMediaType` (415), `NotImplemented` (501) — every response with the Charter
  headers as the existing responses carry them; problem bodies via `application/problem+json`.
  Examples under `examples/` named as the existing ones.

### T021 — Paths and operations

Nine path files (`activations.yaml`, `activations_activationId.yaml`,
`..._selections_kind_artifactId.yaml`, `..._exclusions_kind_artifactId.yaml`,
`..._presets_presetId.yaml`, `..._artifacts.yaml`, `..._skipped.yaml`, `..._graph.yaml`,
`..._missionTypes_missionType_actions_action_context.yaml`) with the operationIds, statuses and
bodies of the operations table; each operation: tag `Activation`, `x-reserved: {contractSet:
activation, issue: "#125"}`, `501`, `500` (`OfferingUnreadable`), `401`/`403`/`default` as
the existing operations; request bodies `required: true`; descriptions state the ADR-0020 D6 verb
semantics. Add the tag and paths to `openapi.yaml`. Check `CharterSourceFingerprint.yaml`'s
operation list and `charter-headers-on-every-response`.

### T022 — Refusal codes

`CharterRefusalCode.yaml`: six codes in the enum and `x-refusal` with `raised: false`; rewrite
the prose that says "every other code is 500" and that `mission_type_not_found`/`action_not_found`
are gone (now reserved for the activation operations). `RefusalCode.java` (kernel) gains the six
constants with Javadoc; `RefusalHandler.statusOf`/`title` gain the cases. One commit, so
`WireTokenParityTest` never sees a half state.

### T023 — The 501

- `@ReservedOperation` (type annotation, `packs.infra.api`), Javadoc citing ADR-0020 D8.
- `ReservedOperationInterceptor implements HandlerInterceptor`: in `preHandle`, when the handler
  is a `HandlerMethod` whose bean type carries `@ReservedOperation`, throw
  `new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED)` so `ProblemHandler` renders the
  closed Problem. Register it in the existing `config` (find where WebMvc config lives).
- `ActivationController implements ActivationApi`, annotated `@RestController`,
  `@RequestMapping("${charter.api.base-path:/api/v1}")`, `@ReservedOperation`; every method
  throws the same exception (unreachable). No constructor dependencies.
- Verify the Charter headers appear on the 501 (they are added globally; confirm, do not add by
  hand) and no `code` is in the body.

### T024 — ArchUnit

- `ReservedRefusalCodesTest`: parametrize "raised by no class except `RefusalCode` and
  `RefusalHandler`" over every `raised: false` code read from the contract, with a bite probe.
- `HexagonalArchitectureTest`: `ActivationController` depends on no `..application..` or
  `..storage..` type (C-001).

### T025 — Description pins and the corpus through the scenario validator

- `ContractDescriptionsTest`: pin the new `CharterRefusalCode` sentences.
- `ActivationConfigConformanceTest` (bdd package): every corpus document validated against the
  `ActivationConfig` mirror with `ContractConformance`'s schema validation; valid pass, invalid
  fail. Copy `contracts/offering/corpus` into the deployable test resources
  (`service/deployable/build.gradle.kts`).

### T026 — Green run

`./gradlew :contracts:check`, `:deployable:test`, `verifyAll -PopenPacksDir=<abs path>`. Record
the counts in the WP history.

## Definition of Done

- 13 operations declared and reserved; WP02's scenarios and coverage test green; WP01's fixtures
  green; `verifyAll` green; no suppressions.

## Risks

- `GeneratedModelNamesTest` (no `_1`), `NullOmissionTest`, exhaustive switches.
- A `415` before the interceptor for a non-JSON body is expected and declared.

`spec-kitty agent action implement WP04 --agent claude --mission activation-set-01M691HW`
