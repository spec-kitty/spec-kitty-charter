---
work_package_id: WP07
title: Phase 2 · Slot facts and the prompt_slot_missing compose check
dependencies:
- WP01
- WP05
requirement_refs:
- FR-008
- FR-011
- NFR-004
- C-001
- C-004
- C-007
- SC-004
planning_base_branch: issue-424-428-adr-0066-service-alignment
merge_target_branch: issue-424-428-adr-0066-service-alignment
branch_strategy: Planning artifacts for this mission were generated on issue-424-428-adr-0066-service-alignment. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-424-428-adr-0066-service-alignment unless the human explicitly redirects the landing branch.
subtasks:
- T031
- T032
- T033
- T034
- T035
phase: Phase 2 (after PR C merges)
agent: claude
history:
- timestamp: '2026-11-09T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/PromptSlotDeclaredCheck.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PromptSlotDeclaredCheck.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PromptSlotDeclaredCheckTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PrCSignaturesTest.java
- contracts/charter/scenarios/step-enhancement-compose.feature
- contracts/charter/changes/2026-11-09-prompt-slot-missing-raised.md
- contracts/charter/changes/2026-11-09-prompt-slot-missing-refuses-compose.md
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PromptSlotDeclaredCheck.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PromptSlotDeclaredCheckTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/CompositionFactsCache.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/CompositionFactsCacheTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationImpact.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ImpactRelations.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationImpactTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PrCSignaturesTest.java
- contracts/charter/responses/OfferingUnreadable.yaml
- contracts/charter/paths/activations_activationId_artifacts.yaml
- contracts/charter/paths/activations_activationId_skipped.yaml
- contracts/charter/paths/activations_activationId_graph.yaml
- contracts/charter/paths/activations_activationId_missionTypes_missionType_actions_action_context.yaml
- contracts/charter/paths/activations_activationId_selections_kind_artifactId.yaml
- contracts/charter/paths/activations_activationId_exclusions_kind_artifactId.yaml
- contracts/charter/paths/activations_activationId_presets_presetId.yaml
- contracts/charter/paths/activations_activationId_packs.yaml
- contracts/charter/paths/activations_activationId.yaml
- contracts/charter/paths/pack-registrations_registrationId.yaml
- contracts/charter/scenarios/step-enhancement-compose.feature
- contracts/charter/changes/2026-11-09-prompt-slot-missing-raised.md
- contracts/charter/changes/2026-11-09-prompt-slot-missing-refuses-compose.md
role: implementer
tags: []
tracker_refs:
- '#424'
---
# WP07: Phase 2 · Slot facts and the prompt_slot_missing compose check

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
this mission branch (`issue-424-428-adr-0066-service-alignment`) has been rebased onto that `main`.** Operator ruling
D2 / constraint C-004. Before claiming, the run lead confirms the merge and the rebase; if either is missing, stop.

PR C symbols this WP consumes (pinned by T031 first, so a drift fails at the rebase, not in implementation):

- `InForceResolution` with the **E6 amendment (OD-6)**: a selected step enhancer is in force exactly when the mission
  type of its single `enhances` target is in force (`enhancerOut` skips `action:` targets; `bindingOf` derives the
  mission type from the enhances edge); otherwise inactive with reason `MISSION_TYPE_NOT_IN_FORCE`.
- WP27's merged closure view: `ClosureView` (`mergedGraph(...)`, `actionContext(...)`), `EdgeIndex` and its enhancer
  lookup for `action:M/s` (E12, both directions); `ActionContextWalk`; `GraphProjection`.
- WP27's merged-closure name checks (`mission_type_not_found`, `action_not_found`, the latter raised by WP28).
- The **OD-9 predicate** "pack P defines `mission_type:M` and declares `action:M/s`", in `packs.domain` over
  composition facts (used here to key the prompt in force per step; WP08 uses it for target resolution).
- PR C's reconciliation of `CharterRefusalCode.yaml` and `ReservedRefusalCodesTest` (WP28 `action_not_found`, WP29
  `artifact_required` text): rebase conflicts with WP04/WP05 hunks are resolved before this WP starts.

## Objective

Compute per-pack **slot facts** in the composition facts and refuse, with `prompt_slot_missing`, any compose in which
an **active** step enhancement names a slot that the prompt in force for its target step does not declare — including
after an override drops it (FR-008). `500` on composing reads; `422` at registration and on writes that rebuild the
merge (operator ruling **OD-4**). An inactive enhancement does not refuse (operator ruling **OD-1 (B)**). Raise the
fourth code in the commit that names it and narrow the contract's "an active or inactive enhancement" clause.

Implementation command:
`spec-kitty agent action implement WP07 --agent claude --mission adr-0066-step-enhancement-service-alignment-01M6ZRYK`

## Context

- **Concerns:** IC-06 of `plan.md` ("Slot facts", "Slot declared", "At registration") and the `prompt_slot_missing`
  parts of IC-05 (raised, `operations` map, `OfferingUnreadable` scoping, C-08 operation descriptions, Breaking
  class). Read both, the ruled table (OD-1, OD-4, OD-6, OD-9) and the overlap notes ("Signatures IC-05 to IC-07 assume
  from PR C").
- **Requirements:** FR-008, FR-011 (the fourth code), NFR-002, NFR-004 (linear check), C-001, C-004, C-007; SC-002,
  SC-004.
- **Rulings:** OD-1 (B) active only — filter the pure check by the in-force answer; OD-4 (i) — judged at registration
  and on merge-rebuilding writes, `422`, `operations: {getPackRegistration: 422}`; A7 (slot facts in
  `DomainRepositoryService.compositionFacts`, pure check in `packs.domain`); A11 (winning copy only); C-08, C-13; D19;
  OD-10 row 6 (the `errors.feature` row for `prompt_slot_missing`).
- **Read first:** `plan.md` IC-05, IC-06; `data-model.md` "Composition facts added"; `research.md` P5, P10;
  `contracts/README.md` (the `prompt_slot_missing` row); `CharterRefusalCode.yaml:135-142` (the twelve write
  operations judged as `422`), `:160-166`, `:725-729`; `ImpactRelations.java:208-215`.

### Files shared with other work packages

Make only the edits named, each with `out-of-map: <file> — <why>` in the commit message:

- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java` (WP05) —
  `compositionFacts` computes `declaredSlots` and `filledSlots` per pack.
- `contracts/charter/schemas/CharterRefusalCode.yaml` (WP04) — `prompt_slot_missing` raised, `operations:
  {getPackRegistration: 422}`, description; the reserved paragraph removed or reduced to history; "an active or
  inactive enhancement" narrowed to active (OD-1, `### Changed`, C-13).
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedRefusalCodesTest.java` (WP04) — move
  `PROMPT_SLOT_MISSING` out of the reserved list (D19).
- `contracts/charter/scenarios/errors.feature` (WP04) — OD-10 row 6: `prompt_slot_missing` joins the raised-code table.

## Project constraints (apply to every subtask)

- **Test first, red first (C-007);** compose scenarios before code.
- **Raise in the same commit (T-15)** as the main class that names `RefusalCode.PROMPT_SLOT_MISSING`.
- **Asserted expectations:** only D19 (list move) and OD-10 row 6. `InForceResolutionPropertyTest` stays unchanged.
  Any other expectation edit: stop and escalate (class (c)).
- **Hexagonal:** the check is pure in `packs.domain`; the facts are built in `infra.storage` (which may name
  `missions.domain`); `activations` sees nothing new here.
- **Linear (NFR-004):** the check visits each enhancement once.
- No disabled tests, no suppressions; Checkstyle limits; no dependency added.
- **Gradle:** `JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=../spec-kitty-open-packs <task>`;
  never `verifyAll`.
- **Commits** end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01JuXv6fZ4jMGna4kLBUA2jr`; no AI model name elsewhere.

## Branch Strategy

- Planning branch and merge target: `issue-424-428-adr-0066-service-alignment`.
- Lanes from `lanes.json`. Claimable once WP01 and WP05 are approved or done **and** the "Blocked until" condition
  holds.

## Subtasks

### T031: Pin PR C's signatures in a compile-only test

**Purpose:** a drift in what Phase 2 assumes from PR C fails here first (plan "Signatures IC-05 to IC-07 assume").

**Steps:**

1. `PrCSignaturesTest` (`packs.application` test): references, without behaviour assertions beyond existence and
   types, (a) the enhancer lookup for `action:M/s` on the merged index, both directions; (b) the merged-closure name
   checks for a mission type and an action and the `RefusalCode.ACTION_NOT_FOUND` constant; (c) the `NodeReference`
   value; (d) the OD-9 predicate; (e) `InForceResolution`'s reason `MISSION_TYPE_NOT_IN_FORCE` for a step enhancer.
2. Add one behavioural assertion for OD-6 (production-shaped, as the plan states): select `step_enhancement:x` and
   `mission_type:M` (never `action:`) → ACTIVE; deselect M → INACTIVE with `MISSION_TYPE_NOT_IN_FORCE`. If it fails,
   PR C did not land OD-6: stop and escalate (WP09/WP10 and SC-001 depend on it).

**Validation:** `:packs:test` compiles and passes this test; if a symbol has a different name in merged PR C, adapt
the reference and record the actual name in the hand-off note for WP08–WP10.

### T032: Red — compose scenarios and the pure-check tests

**Purpose:** US5 AS1 first.

**Steps:**

1. `contracts/charter/scenarios/step-enhancement-compose.feature` (`@contract`, README vocabulary). Fixture: the
   two-pack `spdd` shape plus a **project** pack whose prompt override of `software-dev/plan` does **not** declare
   `spdd:reasons-block`.
   - enhancement **active** (selected, its mission type in force) → every composing read (effective artifacts,
     skipped report, graph, action context, offering where it composes) answers `500 prompt_slot_missing`; the detail
     names the enhancement, its pack, the slot and the pack whose prompt is in force;
   - the **same** closure with the enhancement held but not selected → `200` (OD-1 B; positive control);
   - registering the project pack while the enhancement is active in a closure it joins → `422`, the registration's
     problem `code` `prompt_slot_missing` (OD-4);
   - a write that rebuilds the merge (e.g. selecting the enhancement while the override is in force) → `422
     prompt_slot_missing`;
   - twin: the override declares the slot → `200`.
2. `PromptSlotDeclaredCheckTest`: pure cases over merged facts — slot declared by base; dropped by override; two
   enhancements, one missing; inactive filtered out; winning copy only (A11: a lower-level same-id copy naming a
   missing slot does not refuse when the higher copy does not name it); linear visit count.

**Validation:** red on assertions.

### T033: Slot facts in the composition facts (A7)

**Purpose:** give the pure check its inputs.

**Steps:**

1. `PackCompositionFacts` gains, per pack, `declaredSlots: Map<action URN, List<SlotId>>` (from each step prompt's
   canonical text via `PromptSlots`, the same parse as WP05's load check) and `filledSlots: Map<step_enhancement URN,
   (target action URN, Set<SlotId>)>` (from the single `enhances` edge and the entries).
2. Shared edit: `DomainRepositoryService.compositionFacts` computes them; `CompositionFactsCache` caches them with the
   other facts (`CompositionFactsCacheTest`: facts computed once per fingerprint).
3. Facts are computed regardless of in-force state; the filter is in T034.

**Validation:** `CompositionFactsCacheTest`, `DomainRepositoryServiceTest` green.

### T034: The pure check, filtered by the in-force answer, and the raise — one commit

**Purpose:** FR-008 on composing reads (OD-1 B).

**Steps:**

1. `PromptSlotDeclaredCheck` (`packs.domain`, pure): for each `action:<type>/<step>`, the slots of the prompt in force
   (the layer-merge winner's `declaredSlots`; an override's when one wins) must include every slot that the **winning
   copy** of each enhancement of that step names. Answer the missing ones: enhancement, its pack, slot, pack whose
   prompt is in force. Linear in enhancements.
2. Filter by the in-force answer of the same compose (E6 as amended by PR C): only active enhancements refuse.
   An inactive one leaves a domain diagnostic with no wire surface.
3. Composing reads throw `CharterRefusal(PROMPT_SLOT_MISSING)` (→ `500` through `RefusalHandler`'s existing map).
4. Same commit: shared edits on `CharterRefusalCode.yaml` (`raised: true`, description, reserved paragraph, "active
   or inactive" → active), `ReservedRefusalCodesTest` list, `errors.feature` row.

**Validation:** T032 read scenarios and unit tests green; `ReservedRefusalCodesTest`, `InForceResolutionPropertyTest`
(unchanged) green; `:packs:openPacksRoundTrip` (NFR-002: no pinned pack fills a slot an override drops).

### T035: Registration and merge-rebuilding writes (OD-4); contract scoping and fragments

**Purpose:** the breaking registration is refused up front, not discovered on every read.

**Steps:**

1. `RegistrationImpact` / `ImpactRelations` judge the slot check over the closures the pack joins and answer `422
   prompt_slot_missing` (the write is what leaves the packs unable to compose, `CharterRefusalCode.yaml:135-142`).
   The per-artifact writes and the two DELETEs join where they rebuild the merge. `RegistrationImpactTest` cases with
   twins.
2. `CharterRefusalCode.yaml` (shared edit): `prompt_slot_missing` gets `operations: {getPackRegistration: {status:
   422}}` (match the shape of existing maps).
3. `responses/OfferingUnreadable.yaml`: its shared description says "on the operations that build the layer merge"
   instead of naming the code per operation; add one `prompt_slot_missing` example. Each owned path file whose
   operation builds the merge names `prompt_slot_missing` in its description (C-08). Edit only operations that really
   build the merge; leave the others' text alone.
4. Fragments (one `###` block each; rename to landing date if later): `2026-11-09-prompt-slot-missing-raised.md`
   (`### Changed`: raised; operations map; clause narrowed to active, OD-1) and
   `2026-11-09-prompt-slot-missing-refuses-compose.md` (`### Breaking`: a compose and a registration that answer
   today are refused; core: nothing while it is not a consumer, epic #186).

**Validation:** T032 registration/write scenarios green; `:contracts:check` (`checkRefusalExamples`, lint,
compatibility, fragments).

## Test strategy

- Red first. Positive controls: the inactive-enhancement `200` beside the active `500`; the override-declares-slot
  twin. Non-vacuity: the A11 winning-copy case; the linear visit count.

## Gates (keep green, honestly)

- `ReservedRefusalCodesTest`, `checkRefusalExamples`, `ContractDescriptionsTest`, `OperationCoverage` /
  `OperationCoverageTest`, `HexagonalArchitectureTest`, `InForceResolutionPropertyTest` (unchanged),
  `WireTokenParityTest`, `ActivationWireTokenParityTest`, `KindBindingsTest`, `PackFileSetExactnessTest`,
  `WholePackRoundTripTest`, contract lint / compatibility / changelog-fragment checks.
- Commands: `:packs:test`, `:activations:test`, `:packs:openPacksRoundTrip`, `:deployable:test --tests
  '*CharterContractSuite' --tests '*ReservedRefusalCodesTest' --tests '*ContractDescriptionsTest' --tests
  '*HexagonalArchitectureTest' --tests '*OperationCoverageTest'`, `:contracts:check`.

## Definition of Done

- Every subtask done and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- Started only after PR C merged and the branch was rebased; T031 green.
- Scenarios and tests seen failing first, now passing; the raise in the naming commit.
- Only D19 and OD-10 row 6 expectation edits; nothing outside `owned_files` beyond the listed shared edits.

## Risks

- If PR C's E6 amendment is missing, no enhancement is active and the check can never fire: T031 stops the WP.
- Judging inactive enhancements by mistake reintroduces branch A: the inactive-`200` control guards it.
- A project override that drops a slot turns every composing read into `500` while an active enhancement fills it —
  the intended ADR-0066 risk; the scenario documents it.

## Reviewer guidance

- Check the "Blocked until" condition was met (the rebase commit on the branch).
- Check the filter uses the same compose's in-force answer, and only the winning copy's slots are judged.
- Check the narrowed clause and the `operations` map are in the same change as the raise.
