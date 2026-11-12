---
work_package_id: WP09
title: The effective step read follows the step's place
dependencies:
- WP07
requirement_refs:
- FR-003
- C-007
planning_base_branch: slice-c/effective-step-context
merge_target_branch: slice-c/effective-step-context
branch_strategy: Planning artifacts for this mission were generated on slice-c/effective-step-context. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-c/effective-step-context unless the human explicitly redirects the landing branch.
subtasks:
- T040
- T041
- T042
history:
- timestamp: '2026-11-12T06:30:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/missions-edge/src/main/java/ai/speckitty/charter/missions/application/EffectiveMissionTypeReadService.java
create_intent:
- service/missions-edge/src/main/java/ai/speckitty/charter/missions/application/EffectiveStepPlace.java
- contracts/charter/changes/2026-11-12-effective-step-follows-its-place.md
execution_mode: code_change
owned_files:
- service/missions-edge/src/main/java/ai/speckitty/charter/missions/application/EffectiveMissionTypeReadService.java
- service/missions-edge/src/main/java/ai/speckitty/charter/missions/application/ActivationMissionTypes.java
- service/missions-edge/src/main/java/ai/speckitty/charter/missions/application/EffectiveStepPlace.java
- service/missions-edge/src/main/java/ai/speckitty/charter/missions/infra/activations/ActivationsMissionTypes.java
- service/missions-edge/src/test/java/ai/speckitty/charter/missions/application/EffectiveMissionTypeReadServiceTest.java
- contracts/charter/scenarios/effective-mission-types.feature
- contracts/charter/changes/2026-11-12-effective-step-follows-its-place.md
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

Operator ruling of 2026-11-12 (relayed by the orchestrator; it replaces the deferral of squad A8/R13): fold the fix so
that **`getEffectiveMissionStep`, the action context and the prompt read agree** for a step that only a replaced
version of the mission type keeps (A7). Today `getEffectiveMissionStep` reads only the winning type's `steps()` and
`onDemandSteps()` and the holding pack's step files, so it answers `404 action_not_found` where the context
(`StepHolding`, OD-9) and the prompt read (research R8) answer 200, and the prompt read's `effective-artifact` link 404s.

## Context

- Research R8 (revised): a step's place is the merged winner of `action:M/s` (`StepHolding`, `ClosureView.winnerPackOf`).
- Read first: `missions-edge/.../application/EffectiveMissionTypeReadService.java` (`step`, `holding`,
  `storedBeside`), `MissionTypeReadService.stepOf` (~l.107), `ActivationMissionTypes` (port),
  `infra/activations/ActivationsMissionTypes` (→ activations `EffectiveReadService.missionTypeWinner` ~l.429),
  `packs/domain/StepHolding`, `PacksSession`/`ClosureViewSession`, rules page A7, DIV-243.

## Subtasks

### T040 — Red

- Scenario in `effective-mission-types.feature`: a project pack layer-overrides `mission_type:M` with a version that
  drops step `x`, which the replaced built-in version declares; `GET /activations/{id}/artifacts/mission_type/M/steps/x`
  answers 200 with the built-in version's step and prompt; the context and the prompt read of `M/x` answer 200;
  twin: a step no version declares → `404 action_not_found` on all three.
- `EffectiveMissionTypeReadServiceTest`: the same at the service level.

### T041 — The step's place through the activations port

- `ActivationMissionTypes` gains `EffectiveStepPlace stepPlace(activationId, missionTypeId, stepId)`: the configured
  pack id of the step's place and the mission-type artifact of that version, judged with `requireAction` on the merged
  closure first (`404 action_not_found` as the context judges it). `ActivationsMissionTypes` implements it through a new
  `EffectiveReadService` method (owned by WP07; out-of-map, recorded) that asks the session for `winnerPackOf(action:M/s)`
  and that pack's version of the mission type, under the closure fingerprint.

### T042 — The read uses the place

- `EffectiveMissionTypeReadService.step`: when the winning type declares the step, unchanged; otherwise build the
  holding from the place's pack and version (`packs.holding(placePack, M)`, the same fingerprint check and `stale_input`
  mapping), then `stepOf`. The step's `effective-artifact`/`artifact` links name the place's pack.
- Fragment `changes/2026-11-12-effective-step-follows-its-place.md`: `### Changed` — `getEffectiveMissionStep` answers
  200 for a step a replaced version keeps (was 404); no shape change.

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
- Implementation command: `spec-kitty agent action implement WP09 --agent claude --mission effective-step-and-action-context-01M774HB`

## Definition of Done

- The scenario and the service test failed first and pass; the three reads agree on both the held and the unheld step.
- `./gradlew :service:missions-edge:test :service:activations:test` and the scenario run green.

## Risks and reviewer guidance

- Never read outside the closure fingerprint; map a vanished pack to `stale_input` as `storedBeside` does.
