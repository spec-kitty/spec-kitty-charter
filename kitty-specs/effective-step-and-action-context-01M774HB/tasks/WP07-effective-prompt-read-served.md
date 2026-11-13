---
work_package_id: WP07
title: The effective-prompt read served
dependencies:
- WP02
- WP05
- WP06
requirement_refs:
- FR-003
- FR-004
- FR-005
- FR-007
- NFR-001
- SC-001
- C-001
- C-005
planning_base_branch: slice-c/effective-step-context
merge_target_branch: slice-c/effective-step-context
branch_strategy: Planning artifacts for this mission were generated on slice-c/effective-step-context. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-c/effective-step-context unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-effective-step-and-action-context-01M774HB
base_commit: 5c9f1d9ed1c909f535937f2c20c67caf457a4932
created_at: '2026-11-12T20:13:14.204063+00:00'
subtasks:
- T030
- T031
- T032
- T033
- T034
history:
- timestamp: '2026-11-12T06:30:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java
create_intent:
- contracts/charter/paths/activations_activationId_missionTypes_missionType_actions_action_prompt.yaml
- contracts/charter/schemas/EffectiveStepPrompt.yaml
- contracts/charter/schemas/EffectivePromptSlot.yaml
- contracts/charter/schemas/EffectivePromptFragment.yaml
- contracts/charter/schemas/SlotId.yaml
- contracts/charter/scenarios/effective-step-prompt.feature
- contracts/charter/changes/2026-11-12-effective-step-prompt.md
- contracts/charter/changes/2026-11-12-mission-type-not-in-force.md
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectivePrompt.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/EffectivePromptTest.java
execution_mode: code_change
owned_files:
- contracts/charter/paths/activations_activationId_missionTypes_missionType_actions_action_prompt.yaml
- contracts/charter/schemas/EffectiveStepPrompt.yaml
- contracts/charter/schemas/EffectivePromptSlot.yaml
- contracts/charter/schemas/EffectivePromptFragment.yaml
- contracts/charter/schemas/SlotId.yaml
- contracts/charter/openapi.yaml
- contracts/charter/schemas/_index.yaml
- contracts/charter/scenarios/effective-step-prompt.feature
- contracts/charter/headers/EffectiveETag.yaml
- contracts/charter/headers/CharterSourceFingerprint.yaml
- contracts/charter/changes/2026-11-12-effective-step-prompt.md
- contracts/charter/changes/2026-11-12-mission-type-not-in-force.md
- contracts/lint/ruleset.yaml
- docs/development/hypermedia-hateoas.md
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalCode.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/PacksSession.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectivePrompt.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationController.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadServiceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationControllerTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationMapperTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/EffectivePromptTest.java
role: implementer
tags: []
tracker_refs: []
---

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or run `spec-kitty agent profile show java-jenny`.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

## Objective

Declare and serve `getEffectiveStepPrompt`,
`GET /activations/{activationId}/mission-types/{missionType}/actions/{action}/prompt` (FR-003 to FR-005, FR-007):

- 200: `EffectiveStepPrompt` (data-model.md) with `ETag`, `304` on a weak match;
- refusals in this order (research R10): `404 activation_not_found`, `404 mission_type_not_found`,
  `404 action_not_found` (judged on the merged closure, `StepHolding`), **`409 mission_type_not_in_force`** for a
  mission type the closure holds but the activation does not put in force (OD-5 stands, research R5), `500` composition
  codes / `pack_unreadable` / `prompt_slot_missing`, then the tag and `304`;
- the action context's `_links.prompt` when its mission type is in force.

The contract and the controller land together: the generated interface (`skipDefaultInterface`) does not compile with
a declared but unimplemented operation.

## Context

- Spec: FR-003, FR-004, FR-005, FR-007, NFR-001, SC-001, C-001. Research R5, R8, R10. Squad R1, R6, D1, A10, A11.
- Plan input: `contracts/effective-step-prompt.draft.openapi.yaml` and `contracts/README.md` of the mission (the
  draft's placeholders are decided there).
- Read first: the context read end to end — `ActivationController.getEffectiveActionContext` (~l.178) and `verified`
  (~l.320), `EffectiveReadService.actionContext` (~l.331), `EffectiveTag` (`Read`, `ContextQuery`), `PacksSession`,
  `ClosureViewSession`, `EffectiveReadMapper.toContext`/`contextLink`, `ActivationMapper` (`expand`,
  `effectiveMissionStep`), the context path file, `contracts/lint/ruleset.yaml` (`link-relations-are-known`, ~l.257),
  `docs/development/hypermedia-hateoas.md` (Relation names), `RelationTableParityTest`, `WireTokenParityTest`,
  `contracts/charter/scenarios/README.md`.

## Subtasks

### T030 — Scenarios first: `effective-step-prompt.feature`

- On the example packs (SC-001): select `mission_type:software-dev`, `step_enhancement:plan-risk-notes` and
  `step_enhancement:spdd-plan`; the prompt of `software-dev/plan` is the stored plan prompt, one slot
  `spdd:reasons-block` with `plan-risk-notes` (built-in) then `spdd-plan` (org), each with its provenance.
- The three-levels ordering fixture (squad R1), inline: the order of decision 5 and no other.
- `spdd-plan` held but not selected → absent; a slot nobody fills → listed empty; a prompt with no slot → no slots.
- The mission type held but not in force → `409 mission_type_not_in_force`, while `.../context` answers 200 empty.
- Unknown activation, mission type, action → the 404s, each before a `304` (send a matching `If-None-Match`).
- An edit of only a fragment's bytes changes the ETag; an edit outside the closure does not.
- The context of an action of a mission type in force carries `_links.prompt`; not in force → no `prompt` link.
- The legacy slot case is the load rule's warning path (C-005; FR-005): an unversioned descriptor with text in a slot
  → the prompt read lists the slot with its fragments and a `prompt_slot_not_empty` diagnostic.

### T031 — The contract

- Path file (tag `Activation`, so `ActivationController` implements it), the four schemas (closed, every property
  `x-derived`; `promptPackId` optional; `diagnostics` reusing `Diagnostic.yaml`; `_links` with `self`,
  `effective-artifact`, `effective-context`), `SlotId.yaml` (the grammar of ADR-0066 d2), examples (one with two org
  packs whose names sort opposite to their ids), `openapi.yaml`, `schemas/_index.yaml`.
- `EffectiveActionContext.yaml` (owned by WP02; out-of-map): `_links.prompt` described.
- `contracts/lint/ruleset.yaml`: `prompt` joins the `link-relations-are-known` enum, **in the same commit** as the
  `prompt` row of `docs/development/hypermedia-hateoas.md` (`RelationTableParityTest`).
- `headers/EffectiveETag.yaml`, `headers/CharterSourceFingerprint.yaml`: add the operation to their lists (C-11).
- Fragments: `2026-11-12-effective-step-prompt.md` (`### Added`: the read, its schemas, the `prompt` link);
  `2026-11-12-mission-type-not-in-force.md` (`### Breaking`: a new value of the closed `CharterRefusalCode` enum).

### T032 — `mission_type_not_in_force`

- `kernel RefusalCode.MISSION_TYPE_NOT_IN_FORCE("mission_type_not_in_force")`; `CharterRefusalCode.yaml` (owned by
  WP05; out-of-map): the enum value, its `x-refusal` entry (`status: 409`, `retryable: false`, `raised: true`,
  `operations: {getEffectiveStepPrompt: ...}` in the file's format), a description saying why the prompt read refuses
  where the context answers empty (research R5).
- `RefusalHandler` (owned by WP05; out-of-map): `409` in `statusOf` (its switch is exhaustive);
  `RefusalHandlerTest`. `WireTokenParityTest`/`ContractConformanceTest`/`errors.feature` raised-code row (out-of-map,
  recorded) as they require.

### T033 — Served

- `PacksSession.prompt(missionType, action, InForceQuery)` → `ClosureViewSession` (owned by WP02; out-of-map) calls
  `view.effectivePrompt` (WP06) with the request's cached states.
- `activations.domain.EffectivePrompt` (the activations-side value: prompt, promptPackId, slots of fragments with
  packId, packName, level, enhancement, text; diagnostics).
- `EffectiveTag.Read.PROMPT` and a `PromptQuery(missionType, action)` (owned by WP02; out-of-map).
- `EffectiveReadService.prompt(id, query, ifNoneMatch)`: `found` → `open` → `snapshot` → `requireAction` → the
  mission type's state from the effective set (`ACTIVE` else `MISSION_TYPE_NOT_IN_FORCE`) → states resolved (the
  session runs `requireSlots`) → tag → `NotModified` or `Found`. The order above is the contract's (A10: the cost of a
  resolution per conditional read is accepted and recorded in research R10).
- `ActivationController.getEffectiveStepPrompt`, `EffectiveReadMapper.toPrompt` (owned by WP02; out-of-map),
  `ActivationMapper.prompt(...)` from the generated `PATH_...` constant; the context's `_links.prompt` when the mission
  type is in force.
- Tests: `EffectiveReadServiceTest` (order, 409 before 304), `ActivationControllerTest`, `ActivationMapperTest`.

### T034 — NFR-001

100 repeated reads give identical body and ETag; every permutation of the order in which three packs are configured
and loaded (the activation's revision fixed) gives the same body and ETag.

## Project constraints (every subtask)

- **Test first (C-004)**: the scenario and a failing unit test come before the code; commit the red test first when
  the behaviour is new. Every refusal has a failing fixture and a passing twin that differ only in the faulty part.
- **Canonical shape only (C-005)**: fixtures, examples and scenarios use the canonical shape; an invalid shape appears
  only to exercise a refusal. Never exclude a field from a parity or round-trip check.
- **Stay out of slice B (C-006)**: do not edit `PackCommitStore`, `PackLocator`, `PackDirectories`,
  `CommitStorePackWriteStore`, `FileSystemPackSources`, `Loader`/`LoadedPack`, `CharterWiring`, `file-kinds.yaml`, or
  the example packs' `pack.md`/`README.md`/`org-charter.yaml`. `DomainRepositoryService` only in WP03's one hunk.
- **Hexagonal**: `HexagonalArchitectureTest` must stay green: only `activations.infra.packs` names packs, and only
  `packs.application`/`packs.domain`; generated `api` types only in `*.infra.api` and `web`.
- **Quality gates**: Checkstyle, palantir-java-format (Spotless), Error Prone, NullAway and doclint are on; no
  suppression, no disabled test, no new dependency.
- **Gradle**: `JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon --max-workers=1 <module>:test` (or
  `:contracts:...`, `:service:deployable:test --tests ...`), foreground, one run at a time, never `--stop`, never
  `verifyAll` locally.
- **Commits**: signed, conventional (`feat(...)`, `test(...)`, `docs(...)`), no AI model name and no
  `Co-Authored-By` trailer; an edit to a file another WP owns is recorded in the message as
  `out-of-map: <file> — <why>`.
- **Contract changes** each carry a dated fragment under `contracts/charter/changes/2026-11-12-*.md` with the class
  `contracts/README.md` of the mission names (Added, Changed wording only, Breaking).

## Branch Strategy

- Planning branch: `kitty/mission-effective-step-and-action-context-01M774HB`; final merge target: `slice-c/effective-step-context` (then a pull request into `main`).
- Execution worktrees are allocated per computed lane from `lanes.json`; never pick a base by hand.
- Implementation command: `spec-kitty agent action implement WP07 --agent claude --mission effective-step-and-action-context-01M774HB`

## Definition of Done

- The scenarios failed first and pass; the contract lint, `RelationTableParityTest`, `ContractConformanceTest` and the
  module tests pass; SC-001 holds on the example packs.

## Risks and reviewer guidance

- Many shared files: every out-of-map edit is listed in the commit message.
- The 409 is judged after the 404s and before the 304; the 500 slot refusal before the 304 too.
- Squad A8/R13: `effective-artifact` points at `getEffectiveMissionStep`; WP09 makes that read answer for a step only
  a replaced version keeps (operator ruling 2026-11-12).
