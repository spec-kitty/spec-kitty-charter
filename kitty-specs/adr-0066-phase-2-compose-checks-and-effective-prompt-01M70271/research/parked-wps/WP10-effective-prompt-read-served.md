---
work_package_id: WP10
title: Phase 2 · Contract delta B and the effective-prompt read served
dependencies:
- WP08
- WP09
requirement_refs:
- FR-010
- FR-011
- NFR-003
- NFR-004
- C-001
- C-007
- SC-001
planning_base_branch: issue-424-428-adr-0066-service-alignment
merge_target_branch: issue-424-428-adr-0066-service-alignment
branch_strategy: Planning artifacts for this mission were generated on issue-424-428-adr-0066-service-alignment. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-424-428-adr-0066-service-alignment unless the human explicitly redirects the landing branch.
subtasks:
- T044
- T045
- T046
- T047
- T048
phase: Phase 2 (after PR C merges)
agent: claude
history:
- timestamp: '2026-11-09T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectivePromptService.java
create_intent:
- contracts/charter/paths/activations_activationId_missionTypes_missionType_actions_action_prompt.yaml
- contracts/charter/schemas/EffectiveStepPrompt.yaml
- contracts/charter/schemas/EffectivePromptSlot.yaml
- contracts/charter/schemas/EffectivePromptFragment.yaml
- contracts/charter/schemas/SlotId.yaml
- contracts/charter/examples/EffectiveStepPrompt.three-levels.yaml
- contracts/charter/examples/EffectiveStepPrompt.two-org-packs.yaml
- contracts/charter/scenarios/effective-step-prompt.feature
- contracts/charter/changes/2026-11-09-effective-step-prompt-read.md
- contracts/charter/changes/2026-11-09-mission-type-not-in-force.md
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectivePromptService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveStepPrompt.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectivePromptSlot.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectivePromptFragment.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectivePromptMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectivePromptServiceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectivePromptDeterminismTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectivePromptOperationBudgetTest.java
execution_mode: code_change
model: sonnet
owned_files:
- contracts/charter/paths/activations_activationId_missionTypes_missionType_actions_action_prompt.yaml
- contracts/charter/schemas/EffectiveStepPrompt.yaml
- contracts/charter/schemas/EffectivePromptSlot.yaml
- contracts/charter/schemas/EffectivePromptFragment.yaml
- contracts/charter/schemas/SlotId.yaml
- contracts/charter/schemas/_index.yaml
- contracts/charter/schemas/EffectiveActionContext.yaml
- contracts/charter/openapi.yaml
- contracts/charter/headers/EffectiveETag.yaml
- contracts/charter/headers/CharterSourceFingerprint.yaml
- contracts/charter/examples/EffectiveStepPrompt.three-levels.yaml
- contracts/charter/examples/EffectiveStepPrompt.two-org-packs.yaml
- contracts/charter/examples/EffectiveActionContext.implement.yaml
- contracts/charter/scenarios/effective-step-prompt.feature
- contracts/charter/changes/2026-11-09-effective-step-prompt-read.md
- contracts/charter/changes/2026-11-09-mission-type-not-in-force.md
- contracts/src/test/java/ai/speckitty/charter/contracts/lint/RelationTableParityTest.java
- docs/development/hypermedia-hateoas.md
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalCode.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/RefusalHandler.java
- service/web-transport/src/test/java/ai/speckitty/charter/web/RefusalHandlerTest.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectivePromptService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/PacksSession.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveStepPrompt.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectivePromptSlot.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectivePromptFragment.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSession.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationController.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectivePromptMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationControllerTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectivePromptServiceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectivePromptDeterminismTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectivePromptOperationBudgetTest.java
role: implementer
tags: []
tracker_refs:
- '#424'
---
# WP10: Phase 2 · Contract delta B and the effective-prompt read served

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or read
`/home/user/spec-kitty-open-packs/packs/built-in/agent_profiles/java-jenny.agent.yaml` in full.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⛔ Blocked until

**This WP MUST NOT be started until PR C of `layer-merge-and-effective-reads-01M6R0J6` — its WP27, WP28 and WP29,
including #429, the OD-6 E6 amendment and the OD-9 shared "pack holds step" predicate — is merged into `main`, and
this mission branch has been rebased onto that `main`.** Operator ruling D2 / constraint C-004.

PR C symbols this WP consumes:

- WP28's built action-context read: `EffectiveActionContext` (schema, example, `ActivationController` method), the
  action-context path, `PacksSession` / `ClosureViewSession` changes, and its edits to `ActivationControllerTest`
  (`BUILT` set, operation count, method name) — this WP adds one operation **on top of** them.
- WP28's `NodeReference` schema and value (reuse, never copy — DIRECTIVE_044); `action_not_found` raised.
- WP27's merged-closure name checks; `InForceResolution` with the **OD-6 E6 amendment** (without it no fragment is
  ever served: SC-001, US4 AS1).
- `ReservedOperationCoverageTest` at 0 after PR C (a built operation adds nothing there).

## Objective

Declare and serve `getEffectiveStepPrompt`,
`GET /activations/{activationId}/mission-types/{missionType}/actions/{action}/prompt` (`### Added`, D5): the prompt
in force unchanged in meaning, every declared slot with its ordered active fragments and their provenance, a
`diagnostics` member (OD-7), the renderer sentence for a legacy non-empty slot (OD-8), `ETag`/`304`, and the new
refusal **`409 mission_type_not_in_force`** for a mission type the closure holds but the activation does not put in
force (operator ruling **OD-5**, `### Breaking` enum addition). Add `_links.prompt` on the action context (D5
sub-ruling, C-05). Prove NFR-003 (body and ETag identical over 100 reads and every permutation of three packs) and
NFR-004 (counted, linear at 1,000 and 2,000 enhancements). The contract and the controller land together: the
generated interface (`skipDefaultInterface`) would not compile with a declared but unimplemented operation.

Implementation command:
`spec-kitty agent action implement WP10 --agent claude --mission adr-0066-step-enhancement-service-alignment-01M6ZRYK`

## Context

- **Concerns:** IC-05 (the path, schemas, links, scenarios, operation lists) and IC-07 (flow, determinism, cost) of
  `plan.md`. Read both in full, the ruled OD-5, OD-6, OD-7, OD-8 and OD-10 row 5, and `contracts/README.md` plus the
  draft `contracts/effective-step-prompt.openapi.yaml` in the mission directory (its `x-files` map is the planned
  content of each file).
- **Requirements:** FR-010, FR-011 (diagnostic on the read), NFR-003, NFR-004, C-001, C-007; SC-001, SC-004.
- **Rulings:** D5 (built op, every slot listed), OD-5 (`409 mission_type_not_in_force`; spec AS4.4 amended), OD-7
  (`diagnostics` reusing `Diagnostic.yaml` on this response), OD-8 (the renderer keeps anything between the markers,
  then appends the fragments; no flag), C-04 (`packName` required), C-09 (`400` for all three path parameters), C-10
  (`slot` as `allOf` with `x-derived`), C-14, C-05, C-11 (headers list), T-04, T-09, T-10, T-11.
- **Read first:** `plan.md` IC-05, IC-07; `data-model.md` EffectiveStepPrompt; `quickstart.md` Flow 3; the effective
  reads' path files and `EffectiveReadOperationBudgetTest` as models.

### Files shared with other work packages

Make only the edits named, each with `out-of-map: <file> — <why>`:

- `contracts/charter/schemas/CharterRefusalCode.yaml` (WP04) — add `mission_type_not_in_force` to the enum and to
  `x-refusal` (`status: 409`, `retryable: false`, `raised: true` in the commit where `EffectivePromptService` names
  it), with a description distinguishing it from `mission_type_not_found` ("held by the closure, not put in force by
  the activation").
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedRefusalCodesTest.java` (WP04) — add the
  constant wherever the test enumerates every code, if it does.
- `contracts/charter/scenarios/errors.feature` (WP04) — a raised-code table row for `mission_type_not_in_force`.
  **This row and the `ReservedRefusalCodesTest` addition are not in the approved OD-10 list** (rows 1–8 predate
  OD-5's new code): ask the run lead to confirm them as consequences of OD-5 before landing (escalation class (c)).

## Project constraints (apply to every subtask)

- **Test first, red first (C-007):** `effective-step-prompt.feature` and the unit tests before code.
- **Approved expectation edit — only this:** OD-10 row 5: `ActivationControllerTest.java:30,57-58` — `BUILT` gains
  `getEffectiveStepPrompt`, the operation count goes up by one on top of PR C's own edit, and the method
  `everyOneOfTheFourteenOperations…` is renamed to the new count. Any other expectation edit (see the OD-5 note above):
  stop and escalate.
- **Hexagonal:** `activations` names `packs` only from `activations.infra.packs` (`ACTIVATIONS_NAME_PACKS_ONLY_FROM_ADAPTER`);
  ordering stays in `packs` (WP09); domain values are named as on the wire (A13).
- **No new dependency;** no `501`, no `x-reserved` on the built operation.
- No disabled tests, no suppressions; Checkstyle limits.
- **Gradle:** `JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=../spec-kitty-open-packs <task>`;
  run **all** of `:activations:test` (T-20); never `verifyAll`.
- **Commits** end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01JuXv6fZ4jMGna4kLBUA2jr`; no AI model name elsewhere.

## Branch Strategy

- Planning branch and merge target: `issue-424-428-adr-0066-service-alignment`.
- Lanes from `lanes.json`. Claimable once WP08 and WP09 are approved or done (and the "Blocked until" condition holds).

## Subtasks

### T044: Red — `effective-step-prompt.feature`

**Purpose:** US4 and the read's HTTP behaviour first (T-11). README vocabulary only (`UndefinedStepGuard`).

**Steps:** create `contracts/charter/scenarios/effective-step-prompt.feature` (`@contract`), each absence beside a
positive control in the same answer (as `effective-reads.feature` does):

- US4 AS1: three active enhancements (built-in, org, project) fill `spdd:reasons-block` → ordered built-in, org,
  project; within a level by `packName`, then enhancement id; each with `packId`, `packName`, `level`,
  `enhancement`, `text`. This is **SC-001** on the five-enhancement `spdd`-shaped fixture: each selected enhancement's
  fragment appears in its step's prompt.
- AS2 (unselected absent, a selected one present), AS3 (empty slot listed with `fragments: []`), the slot-less prompt.
- AS4: unknown activation, mission type, action → `404` with `activation_not_found` / `mission_type_not_found` /
  `action_not_found`; a mission type held but not in force → `409 mission_type_not_in_force` (OD-5).
- AS5 (OD-8/OD-7): an unversioned prompt whose slot holds legacy text plus one active fragment → slot listed with the
  fragment; `diagnostics` lists `prompt_slot_not_empty` for that slot.
- `304` on a matching `If-None-Match`; a new ETag after a change to a fragment's bytes in the closure; the tag kept
  after a change to a pack outside it.
- `500 internal_error` (`Given the service fails unexpectedly while it reads the packs`); `500 stale_input`
  (`Given a pack file changes while the next read is answered`); `400` for each of the three path parameters.
- `401`/`403` are covered by `security.feature`.

**Validation:** red on assertions once T045's contract compiles (until then the steps fail on the missing route —
record that the first assertion-level red happened after T045).

### T045: The contract — path, schemas, examples, refusal code, lists, fragments

**Purpose:** C-001; `### Added` and `### Breaking`.

**Steps:**

1. Path file (from the draft `x-files`): tag `Activation`; `IfNoneMatch`; `ETag` = `EffectiveETag`; `304`
   `NotModified`; the three `Charter-*` headers; `400` Problem without a code for a bad `activationId`, `missionType`
   or `action` (`Slug`, `maxLength: 64`, C-09); `404` through `ActivationNotFound` (three codes); `409`
   `mission_type_not_in_force`; `500` through `OfferingUnreadable` (composition codes, `prompt_slot_missing`,
   `pack_unreadable`, `stale_input`, `internal_error`). No `501`, no `x-reserved`.
2. Schemas (closed, every property `x-derived`): `EffectiveStepPrompt` (`activationId`, `revision`,
   `governanceFingerprint`, `missionType`, `action`, `prompt`, `slots`, `diagnostics` (array of `Diagnostic.yaml`,
   OD-7), `_links`); `EffectivePromptSlot` (`slot` as `allOf: [$ref SlotId]` with its own description and
   `x-derived`, C-10; `fragments`); `EffectivePromptFragment` (`packId`, required `packName` (descriptor name,
   `x-derived` from `pack.yaml`, C-04), `level`, `enhancement` ("the declared id, without a kind prefix", C-14),
   `text` (canonical render)); `SlotId` (the d2 grammar). The `prompt` description carries the **renderer sentence**
   (OD-8): keep anything between a slot's markers, then add its fragments after it; a slot with none leaves nothing
   added.
3. Examples: one with three levels; one with two org packs whose descriptor names sort opposite to their ids (C-04).
4. `openapi.yaml` gains the path; `schemas/_index.yaml` lists the four schemas; `headers/EffectiveETag.yaml` and
   `headers/CharterSourceFingerprint.yaml` (:17-19) list the new operation (C-11).
5. `mission_type_not_in_force`: `RefusalCode` enum constant (kernel), `RefusalHandler` maps it to `409` (+
   `RefusalHandlerTest`), YAML enum and `x-refusal` (shared edit) — enum, Java constant and handler in one commit so
   `WireTokenParityTest` / `ActivationWireTokenParityTest` / `VocabularyEqualityTest` stay green; `raised: true` lands
   with T046's service.
6. Fragments (one `###` block each; rename to landing date if later): `2026-11-09-effective-step-prompt-read.md`
   (`### Added`: the operation, four schemas, examples, `_links.prompt`) and `2026-11-09-mission-type-not-in-force.md`
   (`### Breaking`: a new `CharterRefusalCode` value; core must regenerate its refusal-code DTOs).

**Validation:** `:contracts:check` (`lintContract` incl. `built-operation-declares-no-501`, `x-source-shape`,
`enum-snake-case`, `resource-property-cited`; `checkClosedResponseObjects`; `checkRefusalExamples`; fragments).

### T046: Serve it — activations service, port, session, controller, mapper

**Purpose:** FR-010 end to end (IC-07 flow steps 1 and 5).

**Steps:**

1. Domain values (`activations.domain`): `EffectiveStepPrompt`, `EffectivePromptSlot` (slot as kernel `SlotId`),
   `EffectivePromptFragment`, named as on the wire (A13).
2. `PacksSession.prompt(missionType, action, inForce)` port method; `ClosureViewSession` implements it with WP09's
   `EffectivePromptQuery` and maps to the domain values (the only adapter naming `packs`).
3. `EffectivePromptService` (`activations.application`): load the stored activation (`404 activation_not_found`);
   open **one** `PacksSession` (one verified read, one compose); take the in-force answer; if the closure holds
   `mission_type:M` but it is not in the effective set → `CharterRefusal(MISSION_TYPE_NOT_IN_FORCE)` (`409`); else ask
   the port. `ETag` = `EffectiveTag(revision, closure fingerprint)` plus the path; `304` on a match.
4. `ActivationController.getEffectiveStepPrompt` + `EffectivePromptMapper`; `_links.self`.
5. Shared edit on `CharterRefusalCode.yaml`: `mission_type_not_in_force` `raised: true` in this commit.
6. `EffectivePromptServiceTest` (each branch); OD-10 row 5 in `ActivationControllerTest`.

**Validation:** T044 scenarios green; `OperationCoverageTest` (the `200` is covered), `ReservedRefusalCodesTest`,
`HexagonalArchitectureTest`.

### T047: `_links.prompt` on the action context

**Purpose:** D5 sub-ruling (C-05); no relation may point at an unbuilt operation, so it lands with the read.

**Steps:**

1. `docs/development/hypermedia-hateoas.md`: list the new read under `self`; add a `prompt` rel row for the action
   context.
2. `RelationTableParityTest` (`contracts/src/test/java/.../lint/`): extend for the new rel (the test pins the table to
   the contract — add the row; do not loosen the check).
3. `EffectiveActionContext.yaml`: `_links` is the open `Links` map, so document the `prompt` rel in its description;
   `examples/EffectiveActionContext.implement.yaml` shows `_links.prompt`; the action-context answer carries it
   (PR C's mapper — add the link where the context's links are built, a small edit recorded as `out-of-map` if the
   builder is in a file not listed here, and tell the run lead).

**Validation:** `:contracts:test --tests '*RelationTableParityTest'` (or the module's equivalent), `:contracts:check`,
`python3 scripts/docs/check_docs.py`.

### T048: NFR gates — determinism and operation budget

**Purpose:** NFR-003 and NFR-004, counted and compared byte for byte (T-04, T-09, T-10).

**Steps:**

1. `EffectivePromptDeterminismTest`: 100 reads of the same state compare the **whole body and the ETag** byte for
   byte; then every permutation of three packs' configured order (the WP09 fixture where merge order, configured name,
   descriptor name and `pack_id` all differ). Nothing excluded. A fragment byte edit changes the ETag (T-04).
2. `EffectivePromptOperationBudgetTest` (`:activations:test`, counted, never timed, modelled on
   `EffectiveReadOperationBudgetTest`): hooks `cache.loads()`, `InForceResolution.resolutions()`, `walkVisits()`, the
   digest counters, and WP09's enhancement-visit and slot-parse counters. Sizes 1,000 and 2,000 **valid**
   enhancements (each with a companion, an edge and a declared empty slot, so WP04's and WP07's rules accept the
   fixture). Exact counts: one compose, one resolution, one canonical render, one slot parse, the digest counts, cold
   and warm; visits grow by at most the delta between the sizes. The per-slot sort is excluded, with the reason in a
   comment.

**Validation:** both green in `:activations:test`.

## Test strategy

- Red first. Positive controls: each absence beside a presence in the same answer; the `409` beside a `200` for a
  mission type in force. Non-vacuity: the permutation fixture; the fragment-byte ETag change.

## Gates (keep green, honestly)

- `OperationCoverage` / `OperationCoverageTest`, `ReservedOperationCoverageTest` (stays 0), `ReservedRefusalCodesTest`,
  `checkRefusalExamples`, `ContractDescriptionsTest`, `HexagonalArchitectureTest`, `WireTokenParityTest`,
  `ActivationWireTokenParityTest`, `VocabularyEqualityTest`, `RelationTableParityTest`, `ActivationControllerTest`
  (OD-10 row 5), `EffectiveReadOperationBudgetTest` (unchanged), contract lint / closed responses / compatibility /
  changelog-fragment checks.
- Commands: `:packs:test`, `:activations:test`, `:web-transport:test`, `:contracts:check`, `:deployable:test --tests
  '*CharterContractSuite' --tests '*OperationCoverageTest' --tests '*ReservedOperationCoverageTest' --tests
  '*ReservedRefusalCodesTest' --tests '*HexagonalArchitectureTest'`, `python3 scripts/docs/check_docs.py`.

## Definition of Done

- Every subtask done and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- Started only after PR C merged and the branch was rebased.
- Scenarios and tests seen failing first, now passing; SC-001 demonstrated on the five-enhancement fixture.
- Only OD-10 row 5 plus the run-lead-confirmed OD-5 consequences as expectation edits; nothing outside `owned_files`
  beyond the listed shared edits.

## Risks

- Declaring the path without implementing it breaks compilation: keep contract and controller in one commit series
  that compiles at each commit.
- A second `NodeReference` copy (DIRECTIVE_044) if a step reference is added to the answer: reuse PR C's.
- The closure fingerprint depending on configured order makes NFR-003 fail: WP09 fixed it at the source; do not
  exclude the ETag from the comparison.

## Reviewer guidance

- Check the `409` is distinct from `404 mission_type_not_found`, and the enum addition has its `### Breaking` fragment.
- Check `packName` is the descriptor name and the example where names and ids sort opposite.
- Check the budget test asserts exact counts, cold and warm, at both sizes.
