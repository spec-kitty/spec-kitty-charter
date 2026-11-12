---
work_package_id: WP06
title: The effective-prompt query in packs
dependencies:
- WP03
- WP05
requirement_refs:
- FR-003
- FR-005
- NFR-002
planning_base_branch: slice-c/effective-step-context
merge_target_branch: slice-c/effective-step-context
branch_strategy: Planning artifacts for this mission were generated on slice-c/effective-step-context. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-c/effective-step-context unless the human explicitly redirects the landing branch.
subtasks:
- T026
- T027
- T028
- T029
history:
- timestamp: '2026-11-12T06:30:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/EffectivePromptQuery.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/EffectivePromptQuery.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/EffectivePromptQueryTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/EffectivePromptQuery.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/EffectivePromptQueryTest.java
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

The packs-side answer of the effective-prompt read (FR-003, FR-005; ADR-0066 d4, d5, d9): for a merged closure, its
in-force states, mission type `M` and action `s`:

- the **prompt in force**: the stored prompt companion at the merged winner's place of `action:M/s` (research R8 as
  revised: `StepHolding`'s one place for a step), as stored (ADR-0066 d9: unchanged), and that pack's id; empty with no
  pack when the step has no companion;
- **every slot it declares**, in declaration order (from `PackCompositionFacts.prompts()` of that pack, WP03);
- per slot, the fragments of the **in-force** step enhancements whose sole target is `action:M/s` (no pull either way,
  d4), ordered by level (built-in < org < project), then pack descriptor `name`, then enhancement id, each by code
  point (d5), each `{packId, packName, level, enhancement, text}`; the winning copy only;
- `prompt_slot_not_empty` diagnostics for a declared slot that holds text (OD-7, OD-8: listed with no flag).

The slot check (WP05) runs before this query on every read, so a fragment never names an undeclared slot here.

## Context

- Spec: FR-003, FR-005, NFR-002, SC-001. Research R8, R9, R10. Squad A1, A8, R1.
- Read first: `application/ClosureView.java` (`effectiveArtifact` ~l.658, `WinnerSource`, `winnerPackOf` ~l.690,
  `Holder`), `application/ClosureQueries.winnerSource` (~l.217, the closure fingerprint check → `STALE_INPUT`),
  `domain/PackCompanionFiles.stepFiles` (the raw prompt bytes, as `getEffectiveMissionStep` serves them),
  `domain/StepHolding`, `domain/MergedClosure.Winner`, `kernel PackLevel` (enum order BUILT_IN, ORG, PROJECT), the pack
  descriptor `name` (available through the composed `PackUnderResolution.descriptor()`).

## Subtasks

### T026 — Red tests

`EffectivePromptQueryTest`, over hand-built merged closures and facts:

- `fragmentsAreOrderedByLevelThenPackNameThenEnhancementId` — the three-levels fixture of squad R1: two org packs whose
  ids sort opposite to their names, a built-in enhancement whose id sorts after the org one, and a project fragment;
  the order is built-in, org (by name), project — and fails under any single-key sort.
- `anEnhancementHeldButNotInForceIsAbsent`; `aSlotNoInForceEnhancementFillsIsListedEmpty`;
  `aStepWithNoSlotsAnswersItsPromptAndNoSlot`; `aStepWithNoPromptCompanionAnswersAnEmptyPromptAndNoPack`;
  `aSlotHoldingLegacyTextIsListedWithItsFragmentsAndADiagnostic`; `onlyTheWinningCopyOfAnEnhancementIsServed`;
  `thePromptIsReadAtTheWinnersPlaceOfTheStep` (a step that only a replaced version keeps, A7).

### T027 — The query

`packs.application.EffectivePromptQuery` (final): a pure function over (merged closure, facts by pack, in-force
predicate, enhancers of `action:M/s` from the merged index, descriptor names by pack, the prompt text supplier) →
an `EffectivePrompt` value (`prompt`, `promptPackId`, `slots`, `diagnostics`). Ordering by a single `Comparator`
chained on (level, packName, enhancement id) with `String::compareTo` (code point).

### T028 — `ClosureView.effectivePrompt`

- `ClosureView.effectivePrompt(String missionType, String action, InForceStates states)` (out-of-map edit of WP05's
  file, recorded): judges nothing itself (the caller has run `requireAction` and the mission-type check); reads the
  prompt companion through `PackCompanionFiles.stepFiles(holder pack, winning mission type)` under the closure
  fingerprint check, as `ClosureQueries.winnerSource` checks it (`STALE_INPUT` on a change); builds the query's inputs.
- `ClosureViewTest` case over the example packs (out-of-map test edit): `software-dev/plan` with `plan-risk-notes` and
  `spdd-plan` in force answers both fragments in that order.

### T029 — NFR-002

Operation count (a visit of an enhancement or a slot) at 1,000 and 2,000 step enhancements on one step and spread over
steps; ratio ≤ 2.5.

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
- Implementation command: `spec-kitty agent action implement WP06 --agent claude --mission effective-step-and-action-context-01M774HB`

## Definition of Done

- The tests failed first and pass; `./gradlew :service:packs:test` green.

## Risks and reviewer guidance

- The text is the stored companion, not a re-render (d9). Fragments are the canonical texts from the facts.
- The three-levels test must fail under a sort by id only and by name only.
