---
work_package_id: WP10
title: Every overrider in force is named (#439)
dependencies:
- WP02
requirement_refs:
- FR-013
- SC-006
- C-001
planning_base_branch: slice-c/effective-step-context
merge_target_branch: slice-c/effective-step-context
branch_strategy: Planning artifacts were generated on the mission branch kitty/mission-effective-step-and-action-context-01M774HB; completed changes merge into slice-c/effective-step-context, then by pull request into main. Execution worktrees are allocated per computed lane from lanes.json.
subtasks:
- T043
- T044
- T045
- T046
agent: claude
history:
- timestamp: '2026-11-12T06:30:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/InForceResolution.java
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/InForceResolutionOverridersTest.java
- contracts/charter/scenarios/effective-overriders.feature
- contracts/charter/changes/2026-11-12-every-overrider-named.md
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/InForceResolution.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/InForceStates.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/InForceResolutionOverridersTest.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/MergedSnapshot.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveSet.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveSkippedReport.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/DocumentRules.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectiveArtifactMapper.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/EffectiveProfileRead.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/EffectiveProfileDtos.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/ProfileJson.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/activations/ActivationsEffectiveProfiles.java
- contracts/charter/schemas/EffectiveArtifact.yaml
- contracts/charter/schemas/EffectiveArtifactRead.yaml
- contracts/charter/schemas/EffectiveGovernanceEntry.yaml
- contracts/charter/schemas/EffectiveAgentProfileRead.yaml
- contracts/charter/schemas/OverriddenArtifact.yaml
- contracts/charter/scenarios/effective-overriders.feature
- contracts/charter/changes/2026-11-12-every-overrider-named.md
role: implementer
---

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or run `spec-kitty agent profile show java-jenny`.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

## Objective

Operator ruling of 2026-11-12 on #439, option (b) (FR-013): `satisfiedBy` and `overriddenBy` list **every**
overrider in force that satisfies or hides an artifact, sorted by level (built-in < org < project), then full URN by
code point. Today `InForceResolution.overriderOf` reports the one with the smallest URN, which no rule ruled. The
contract change is `### Breaking` (a scalar becomes an array in closed schemas).

## Context

- Spec FR-013, SC-006; research `research/in-force-rulings-439-447.md`; rules page E5, E9 (its "smallest URN"
  paragraph and the #439 note), E14 (`overridden {artifact, overriddenBy}`), D16 (carriers hold activatable kinds only).
- Read first: `packs/domain/InForceResolution.java` (`overriderOf` ~l.366), `InForceStates`; activations
  `MergedSnapshot`, `EffectiveSet` (`EffectiveArtifact.satisfiedBy`), `EffectiveSkippedReport`, `DocumentRules`
  (the IC-13 write reads the satisfier), `ClosureViewSession.stateOf` (D16 drop), `EffectiveReadMapper`,
  `EffectiveArtifactMapper`; profiles-edge `EffectiveProfileRead`, `EffectiveProfileDtos`, `ProfileJson`,
  `ActivationsEffectiveProfiles`; the contract schemas `EffectiveArtifact.yaml` (and the schemas that copy its members:
  `EffectiveArtifactRead`, `EffectiveGovernanceEntry`, `EffectiveAgentProfileRead`, `EffectiveContextArtifact` from WP02),
  `OverriddenArtifact.yaml`.

## Subtasks

### T043 — Red

- `effective-overriders.feature` (`@contract`): a required directive `T` overridden by `E1` (org pack) and `E2`
  (project pack), both in force and both `open`-permitted: the listing's `T` has `satisfiedBy: [E1, E2]` (org, then
  project), the skipped report's `overridden` (when `T` is selected) has `overriddenBy` in the same order; the context
  item and the effective artifact read carry the same. One pack authoring two overriders of `T`: sorted by URN within
  the level. Twin: one overrider → a one-element list.
- `InForceResolutionOverridersTest`: the packs-level list, its order, and that the state and reason do not change.

### T044 — Packs

- `InForceResolution`/`InForceStates`: replace the single overrider with an ordered list (level of the winning pack of
  each overrider, then URN). Keep the state and reason computation unchanged.

### T045 — Activations and profiles

- Carry lists through `MergedSnapshot`, `EffectiveSet.EffectiveArtifact`, `EffectiveSkippedReport` (`overridden`),
  `ClosureViewSession` (out-of-map: WP02 owns it; D16 drops non-activatable entries from the list),
  `EffectiveReadMapper` (out-of-map: WP02), `EffectiveArtifactMapper`, and the profiles-edge read and DTOs.
- `DocumentRules` (IC-13): a required artifact is satisfied when the list is non-empty; nothing else changes.

### T046 — Contract

- `satisfiedBy` and `overriddenBy` become arrays (`items: ArtifactReference`, `minItems: 1` where present, sorted as
  ruled, described) in `EffectiveArtifact.yaml` and every schema that copies its members (`EffectiveContextArtifact` is
  WP02's file: out-of-map), and in `OverriddenArtifact.yaml`; examples updated; `x-derived` kept.
- Fragment `changes/2026-11-12-every-overrider-named.md`: `### Breaking`, every operation whose answer changes shape
  named (`listEffectiveArtifacts`, `getActivationSkippedReport`, `getEffectiveActionContext`, `getEffectiveArtifact`,
  `getEffectiveAgentProfile`, the effective mission-type reads if they carry it); "core: nothing while it is not a
  consumer (epic #186)".

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
- Implementation command: `spec-kitty agent action implement WP10 --agent claude --mission effective-step-and-action-context-01M774HB`

## Definition of Done

- Scenarios and tests failed first and pass; `:packs:test`, `:activations:test`, `:profiles-edge:test`, the contract
  lint and the scenario run are green; no scalar `satisfiedBy`/`overriddenBy` remains in the contract or the code.

## Risks and reviewer guidance

- The order is level then URN, by the winning pack of each overrider; a reviewer checks it fails under URN-only.
