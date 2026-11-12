---
work_package_id: WP04
title: 'Compose checks: target held and inherited lock'
dependencies: []
requirement_refs:
- FR-002
- SC-002
- C-001
- C-003
- C-004
planning_base_branch: slice-c/effective-step-context
merge_target_branch: slice-c/effective-step-context
branch_strategy: Planning artifacts for this mission were generated on slice-c/effective-step-context. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-c/effective-step-context unless the human explicitly redirects the landing branch.
subtasks:
- T015
- T016
- T017
- T018
- T019
history:
- timestamp: '2026-11-12T06:30:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/EnhancementCompose.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/EnhancementCompose.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/EnhancementComposeTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ImpactRelationsEnhancementTest.java
- contracts/charter/scenarios/step-enhancement-compose.feature
- contracts/charter/changes/2026-11-12-step-enhancement-compose-target-and-lock.md
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/EnhancementCompose.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/EnhancementComposeTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ImpactRelations.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/OfferingReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCompositionResolverTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ImpactRelationsEnhancementTest.java
- contracts/charter/scenarios/step-enhancement-compose.feature
- contracts/charter/changes/2026-11-12-step-enhancement-compose-target-and-lock.md
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

Two compose checks of ADR-0066 decision 8 for step enhancements (FR-002):

- **C37 target held.** A step enhancement's `enhances` target `action:M/s` is held by a pack of the enhancing pack's
  `depends_on` closure (`StepHolding.heldByPack`, OD-9). Otherwise the compose refuses with
  `pack_reference_pack_missing` (the qualifier names no pack of the closure) or `pack_reference_artifact_missing` (the
  pack exists but holds no such step). Today an `action:` target is never artifact-missing because
  `NodeKind.ACTION.artifactKind()` is empty; close that for step enhancements only.
- **Inherited lock (C22/C25 amended, OD-2 (A)).** A step inherits its mission type's mutability level: when
  `mission_type:M` is `LOCKED` in the closure without the enhancing pack's own policy (`PerPackPolicy.levelIn`, as
  `ImpactRelations.judgedWithout`), an enhancement of `action:M/s` is refused: `422 override_not_permitted` at
  registration and on the impact-checked writes, `500 pack_unreadable` on reads — whatever the activation, like C21.

## Context

- Spec: FR-002, SC-002, C-001, C-003. Research and squad A4, A5. Twin matrix in `tasks.md`.
- Read first: `domain/PackCompositionResolver.java` (`checkReferences` ~l.442, `checkQualifiedReference` ~l.483,
  `dependsOnClosure` ~l.523, private), `application/OfferingReadService.composed` (~l.351, `checkReferences` call
  ~l.491), `application/ImpactRelations.java` (`collect` ~l.111, `notPermitted` ~l.183, `judgedWithout` ~l.202),
  `application/ClosureView.java` (`MergeInputs.merged()` ~l.139), `domain/LayerMerge.allowOverride` (C21's read path),
  `domain/{StepHolding, PerPackPolicy, MutabilityLevel, MutabilityPolicySource}`, `web/RefusalHandler`
  (`registrationStatusCodes`, `STATUS_BY_OPERATION`: the eleven composition codes are already 422 on registration and
  the impact-checked writes; `override_not_permitted` is 422 everywhere), and `docs/architecture/
  composition-and-activation-rules.md` C21, C22, C25, E6, G8.

## Subtasks

### T015 — Scenarios first

`step-enhancement-compose.feature` (`@contract`), inline fixture packs in the canonical shape, one failing fixture and
one passing twin per cell of the twin matrix rows C37 and inherited lock:

- C37: a project pack whose step enhancement targets `built-in::action:software-dev/nosuch` → reads 500
  `pack_reference_artifact_missing`, registration 422; twin: `.../plan` → 200/201. A qualifier naming a pack outside
  the `depends_on` closure → `pack_reference_pack_missing`; twin: in the closure.
- Lock: a policy that locks `mission_type:software-dev` in built-in; an org pack enhancing `.../plan` → registration
  `422 override_not_permitted`, reads `500 pack_unreadable`; twin: the type `extendable` → accepted. An
  `extendable` lock on the step itself is not the subject (only `LOCKED` refuses an enhancement, `MutabilityLevel`).
- An impact-checked write (`putPackRelationship` adding the `enhances` edge) gets the same 422s.

### T016 — Pure rules

`packs.domain.EnhancementCompose` (final, static, pure):

- `targetHeld(...)`: for each step enhancement of a pack (a `step_enhancement:` source with a sole `action:` target,
  `StepHolding.soleActionTarget`), resolve the target through the qualifier and judge `heldByPack` over the
  `definedArtifacts` of the packs of its `depends_on` closure; return a violation naming code, enhancement, pack and
  target, or empty.
- `lockedTargets(...)`: given the closure's per-pack policies with the enhancing pack left out, the enhancements whose
  target's mission type (`StepHolding.missionTypeOf`) is `LOCKED`.
- `EnhancementComposeTest`: each rule's failing case and twin; a bare target resolved in the enhancing pack itself; a
  linear visit.

### T017 — C37 in the compose

- Expose the depends_on closure (make `dependsOnClosure` package-visible or add a public query in
  `PackCompositionResolver`) and call `EnhancementCompose.targetHeld` from `OfferingReadService.composed` right after
  `checkReferences`, mapping a violation to `CharterRefusal(PACK_REFERENCE_*)` with the packs named, as
  `checkReferences` does. Registration composes through the same method (`Views.over(staged)`), so the 422 follows.
- `OfferingReadServiceTest`: the refusal and its twin.

### T018 — Inherited lock

- Registration: a rule in `ImpactRelations` beside `notPermitted` (same `judgedWithout` scoping), giving
  `OVERRIDE_NOT_PERMITTED` with the enhancement, its pack and the locked mission type. `ImpactRelationsEnhancementTest`.
- Read: in `ClosureView.MergeInputs.merged()` (where C21 already raises `pack_unreadable`), call
  `EnhancementCompose.lockedTargets` and throw `PACK_UNREADABLE` naming both packs. `ClosureView` is owned by WP05:
  out-of-map edit, recorded. `ClosureViewTest` case and twin (out-of-map test edit, recorded).

### T019 — Description, E6 pins, fragment

- `CharterRefusalCode.yaml` (owned by WP05; out-of-map, recorded): broaden `override_not_permitted`'s description to
  cover "an enhancement of a step whose mission type is locked" (OD-2), no new enum value.
- E6's unheld-target branch: find the pinning tests named in the rules page E6 (`aStepEnhancerWhoseKeptEnhancesEdge
  EndsAtNoWinnerIsPrunedForItsTarget`, `aStepEnhancementOfAStepNoPackHoldsNamesThatStepInTheReportOfTheRealResolution`,
  `aStepEnhancerOfAnActionDeclaredWithoutItsMissionTypeIsPrunedForItsTarget`): where C37 now refuses the compose,
  change the fixture so the case is still reachable (a shape C37 does not refuse, for example a two-target enhancer) or
  turn it into a C37 refusal test, and say which in the commit (squad A5). Do not delete an assertion to pass.
- Fragment `changes/2026-11-12-step-enhancement-compose-target-and-lock.md`: `### Breaking` — registrations,
  impact-checked writes and reads refused that answered before; `### Changed` — the `override_not_permitted` wording.

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
- Implementation command: `spec-kitty agent action implement WP04 --agent claude --mission effective-step-and-action-context-01M774HB`

## Definition of Done

- Every twin-matrix cell of C37 and the inherited lock has a failing fixture and a passing twin; scenarios green.
- `./gradlew :service:packs:test`, `:service:web-transport:test` and the scenario run green.

## Risks and reviewer guidance

- The lock judges the **mission type's** level, leaving the enhancing pack's own policy out; `EXTENDABLE` allows.
- Reads refuse whatever the activation (OD-2 (A), like C21); do not filter by in-force here (that is C38 only).
