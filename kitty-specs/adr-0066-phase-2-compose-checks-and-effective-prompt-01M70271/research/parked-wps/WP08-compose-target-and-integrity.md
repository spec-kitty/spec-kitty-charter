---
work_package_id: WP08
title: 'Phase 2 · Compose checks: the target step resolves, and its inherited integrity permits enhancement'
dependencies:
- WP07
requirement_refs:
- FR-009
- C-001
- C-007
- SC-002
planning_base_branch: issue-424-428-adr-0066-service-alignment
merge_target_branch: issue-424-428-adr-0066-service-alignment
branch_strategy: Planning artifacts for this mission were generated on issue-424-428-adr-0066-service-alignment. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-424-428-adr-0066-service-alignment unless the human explicitly redirects the landing branch.
subtasks:
- T036
- T037
- T038
- T039
phase: Phase 2 (after PR C merges)
agent: claude
history:
- timestamp: '2026-11-09T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java
create_intent:
- contracts/charter/scenarios/step-enhancement-integrity.feature
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/StepEnhancementIntegrityTest.java
- contracts/charter/changes/2026-11-09-override-not-permitted-covers-enhances.md
- contracts/charter/changes/2026-11-09-step-enhancement-target-and-integrity.md
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCompositionResolverTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/LayerMerge.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/LayerMergeTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/StepEnhancementIntegrityTest.java
- contracts/charter/scenarios/step-enhancement-integrity.feature
- contracts/charter/changes/2026-11-09-override-not-permitted-covers-enhances.md
- contracts/charter/changes/2026-11-09-step-enhancement-target-and-integrity.md
role: implementer
tags: []
tracker_refs:
- '#424'
---
# WP08: Phase 2 · Compose checks: the target step resolves, and its inherited integrity permits enhancement

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
this mission branch has been rebased onto that `main`.** Operator ruling D2 / constraint C-004. WP07's T031
(`PrCSignaturesTest`) must be green on the rebased branch.

PR C symbols this WP consumes:

- The **OD-9 predicate** "pack P defines `mission_type:M` and declares `action:M/s`" (`packs.domain`, over composition
  facts), the single authority for "P holds step M/s" shared with WP27's `action_not_found` and `EdgeIndex.stepsOf`.
- WP27's merged closure (`ClosureView`, `EdgeIndex`) and merged-closure name checks.
- `InForceResolution` with the OD-6 amendment (an integrity refusal is judged on composition, not in-force state, but
  the scenarios select enhancements through their mission type).

## Objective

Refuse a compose that would land a fragment on a step it may not reach (FR-009): (1) the enhancement's target step
must resolve in the `depends_on` closure — qualified `<pack>::action:<type>/<step>` in a pack of the closure that holds
the step, bare `action:<type>/<step>` in the enhancing pack itself — using the one shared OD-9 predicate; (2) the target
step inherits its mission type's mutability level (operator ruling **OD-2 (A)**), so a locked mission type refuses an
enhancement of its steps: `422 override_not_permitted` at registration, `500 pack_unreadable` on reads, as for a
forbidden override under rule C21, with `override_not_permitted`'s description broadened (`### Changed`, no new enum
value). Both are also judged at registration and on merge-rebuilding writes (OD-4).

Implementation command:
`spec-kitty agent action implement WP08 --agent claude --mission adr-0066-step-enhancement-service-alignment-01M6ZRYK`

## Context

- **Concern:** IC-06 of `plan.md` ("Target resolved", "Integrity", "At registration"). Read it in full with OD-2,
  OD-4, OD-9 in the ruled table.
- **Requirements:** FR-009, NFR-002, C-001, C-007; SC-002.
- **Rulings:** OD-2 (A) and its sub-ruling (codes as C21; not `artifact_locked`, which is a `409` of activation writes
  carrying `requiredBy`, C-07); OD-4 (i); OD-9 (shared predicate, implemented by PR C — call it, do not re-implement
  it); T-16 (six twins); T-18 (no longer needed for branch B, which was not chosen — do not build the no-op).
- **Read first:** `plan.md` IC-06; `data-model.md` (StepEnhancement invariants: qualified target, bare target,
  integrity); `PackCompositionResolver.checkQualifiedReference` and `checkReferences`, noting that the
  artifact-missing branch is skipped for `action:` today (`PackCompositionResolver.java:505-509`); `LayerMerge.java:88`
  (the C21 refusal) and `PackMutabilityPolicy` (`packs.application`); `contracts/packs/schemas/policy.schema.json`
  (policy keys name `mission_type:`, not `action:`).

### Files shared with other work packages

Make only the edits named, each with `out-of-map: <file> — <why>`:

- `service/packs/src/main/java/ai/speckitty/charter/packs/application/ImpactRelations.java` and
  `RegistrationImpact.java` (WP07) — judge the target-resolution and integrity refusals over the closures a registering
  pack joins; answer `422`.
- `service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationImpactTest.java` (WP07) — new cases.
- `contracts/charter/schemas/CharterRefusalCode.yaml` (WP04) — `override_not_permitted` description broadened to "an
  overrides edge, or an enhances edge onto a locked target, that the target's mutability level forbids"; the
  integrity sentence of the C21 text extended likewise.

## Project constraints (apply to every subtask)

- **Test first, red first (C-007).**
- **Asserted expectations:** none of the existing assertions change. `LayerMergeTest` and `PackCompositionResolverTest`
  gain new cases only. Any other expectation edit: stop and escalate (class (c)).
- **Scope the extension:** the new step check runs only for `enhances` edges whose source is a `step_enhancement:` node,
  so NFR-002 is not put at risk for other qualified `action:` references.
- No new enum value (`WireTokenParityTest`, `ActivationWireTokenParityTest`, `VocabularyEqualityTest` need no row).
- Hexagonal: rules in `packs.domain`; registration in `packs.application`.
- No disabled tests, no suppressions; Checkstyle limits; no dependency added.
- **Gradle:** `JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=../spec-kitty-open-packs <task>`;
  never `verifyAll`.
- **Commits** end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01JuXv6fZ4jMGna4kLBUA2jr`; no AI model name elsewhere.

## Branch Strategy

- Planning branch and merge target: `issue-424-428-adr-0066-service-alignment`.
- Lanes from `lanes.json`. Claimable once WP07 is approved or done (and the "Blocked until" condition holds).

## Subtasks

### T036: Red — the six target twins and the integrity scenarios

**Purpose:** US5 AS2 and AS3 first (T-16).

**Steps:**

1. `PackCompositionResolverTest`, six new cases, each a refusal or acceptance on otherwise identical closures:
   - bare `action:software-dev/plan` that resolves in the enhancing pack itself → accepted (the enhancing pack must then
     define `mission_type:software-dev` and declare the step, per OD-9 — build the fixture accordingly);
   - bare target that resolves **only** in another pack → `pack_reference_unqualified`;
   - qualified `base::action:software-dev/plan`, base in the `depends_on` closure holding the step → accepted;
   - qualified naming a pack outside the closure → `pack_reference_pack_missing`;
   - qualified, known pack, unknown mission type → `pack_reference_artifact_missing`;
   - qualified, known mission type, unknown step → `pack_reference_artifact_missing`.
   Add the hole OD-9 closes: an enhancing pack that **declares** the node `action:software-dev/plan` in its own graph
   but does not define the mission type does not resolve a bare target locally.
2. `StepEnhancementIntegrityTest` (`packs.domain`): a `mission_type:software-dev` locked by a lower pack's policy (or
   the kind default along the closure, ADR-0063) refuses an enhancement of `software-dev/plan` from a higher pack; the
   twin with the mission type open accepts it.
3. `contracts/charter/scenarios/step-enhancement-integrity.feature` (`@contract`, README vocabulary): a locked mission
   type — registration of the enhancing pack answers `422 override_not_permitted` naming the root pack and the packs
   involved; a composing read of a closure where it already sits answers `500 pack_unreadable`; the open twin `200`.
   A qualified target outside the closure — `422` at registration (OD-4) and the composing read refusal.

**Validation:** red on assertions.

### T037: Target resolution through the shared predicate

**Purpose:** FR-009 first part, new code (the artifact-missing branch is skipped for `action:` today).

**Steps:**

1. In `PackCompositionResolver`, for `enhances` edges from `step_enhancement:` nodes only:
   - **qualified:** the qualifier is looked up by descriptor `name` (as `checkQualifiedReference` does); the pack must
     be in the enhancing pack's `depends_on` closure (`pack_reference_pack_missing`, already judged) and must hold the
     step by the **OD-9 predicate** (`pack_reference_artifact_missing`);
   - **bare:** resolves in the enhancing pack itself by the same predicate, as every bare reference does
     (`checkReferences`); a bare target that resolves only in another pack is `pack_reference_unqualified`.
2. Call PR C's predicate; never write a second "pack holds step" rule (A6).
3. Keep each method within Checkstyle limits; extract the step-enhancement branch into its own method.

**Validation:** the six twins green; `PackCompositionResolverTest` otherwise unchanged; `:packs:openPacksRoundTrip`
green (NFR-002).

### T038: Integrity — a step inherits its mission type's mutability level (OD-2 A)

**Purpose:** FR-009 second part.

**Steps:**

1. A step's level is its mission type's: the `mission_type:<type>` policy entry, or the `mission_type` kind default
   along the closure (ADR-0063). An `enhances` edge from a `step_enhancement:` onto `action:<type>/<step>` is refused
   when that level forbids change, exactly as `LayerMerge` refuses a forbidden layer override (C21, :88): the read
   answers `500 pack_unreadable` (worded as C21 is), naming the enhancement and the locked target.
2. Reuse the C21 machinery (`LayerMerge`, `PackMutabilityPolicy`); do not add a parallel mutability model.
3. Make the read scenario and `StepEnhancementIntegrityTest` green.

**Validation:** `LayerMergeTest` (new cases only) and `StepEnhancementIntegrityTest` green; `InForceResolutionTest`
and `InForceResolutionPropertyTest` unchanged and green.

**Edge cases:** an enhancing pack at the same level as the mission type's owner (sibling) — follow C21's own reading
for overrides; a mission type `open` at built-in but locked by an org policy — the closure's effective level decides.

### T039: Registration and merge-rebuilding writes; contract description; fragments

**Purpose:** OD-4 for FR-009; C-001.

**Steps:**

1. Shared edits on `ImpactRelations` / `RegistrationImpact`: judge target resolution and integrity over the closures
   the registering pack joins; answer `422` with the codes above (`override_not_permitted`, the
   `pack_reference_*` codes). The writes that rebuild the merge join, as for WP07's check.
2. Shared edit on `CharterRefusalCode.yaml`: broaden `override_not_permitted`'s description and the C21 sentence (no
   new enum value).
3. Fragments (one `###` block each; rename to landing date if later):
   `2026-11-09-override-not-permitted-covers-enhances.md` (`### Changed`: the description broadened) and
   `2026-11-09-step-enhancement-target-and-integrity.md` (`### Breaking`: a registration or compose that answered
   before is refused when an enhancement's target step does not resolve or is locked; core: nothing while it is not a
   consumer, epic #186).

**Validation:** registration scenarios and `RegistrationImpactTest` green; `:contracts:check`.

## Test strategy

- Red first; every refusal has an accepting twin that differs only in the fault (SC-002). Non-vacuity: the OD-9 hole
  case (a declared node without the mission type does not resolve).

## Gates (keep green, honestly)

- `ReservedRefusalCodesTest` (no change in raised status), `checkRefusalExamples`, `ContractDescriptionsTest`,
  `OperationCoverage` / `OperationCoverageTest`, `HexagonalArchitectureTest`, `WireTokenParityTest`,
  `ActivationWireTokenParityTest`, `InForceResolutionPropertyTest`, `KindBindingsTest`, `PackFileSetExactnessTest`,
  `WholePackRoundTripTest`, contract lint / compatibility / changelog-fragment checks.
- Commands: `:packs:test`, `:packs:openPacksRoundTrip`, `:activations:test`, `:deployable:test --tests
  '*CharterContractSuite' --tests '*ReservedRefusalCodesTest' --tests '*HexagonalArchitectureTest'`,
  `:contracts:check`.

## Definition of Done

- Every subtask done and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- Started only after PR C merged and the branch was rebased.
- Six target twins, integrity twins and scenarios seen failing first, now passing.
- No existing assertion edited; nothing outside `owned_files` beyond the listed shared edits.

## Risks

- A second "holds step" predicate drifting from PR C's: the reviewer checks there is only one.
- Extending the qualified-reference check to every `action:` reference would refuse pinned packs (NFR-002): scope it to
  `step_enhancement:` sources.

## Reviewer guidance

- Check the predicate is PR C's (OD-9), and the extension is limited to `step_enhancement:` sources.
- Check the integrity refusal reuses C21 and answers `422 override_not_permitted` / `500 pack_unreadable`, never
  `artifact_locked`.
