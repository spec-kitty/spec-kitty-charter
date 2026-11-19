---
work_package_id: WP03
title: The pack-set check in pack-validation
dependencies:
- WP02
requirement_refs:
- FR-002
- FR-003
- FR-004
- FR-007
- NFR-005
planning_base_branch: feat/478-pack-check
merge_target_branch: feat/478-pack-check
branch_strategy: Planning artifacts for this mission were generated on feat/478-pack-check. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/478-pack-check unless the human explicitly redirects the landing branch.
subtasks:
- T010
- T011
- T012
- T013
phase: Phase 2 - Validation edge
history:
- at: '2026-11-19T08:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/
create_intent:
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/set/PackSetCheck.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/set/PackSetVerdict.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/set/package-info.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/roundtrip/RoundTripping.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/PackCheckReadoutTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/set/PackSetCheckTest.java
execution_mode: code_change
owned_files:
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/PackCheck.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/set/**
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/roundtrip/RoundTripping.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/PackCheckReadoutTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/set/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP03 — The pack-set check in pack-validation

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

The validation edge gains the pack-set check:

- validate mode per pack (`PackCheck`);
- `CLOSURE` over the set **as given** (D5), with the preset phase (`PackRulesets.closure(presetIdsOf)`);
- skipped when any pack has no facts (D3);
- optionally the round trip per pack, through a port the composition root supplies.

It names only the packs domain and the kernel.

## Context

- Spec: FR-002, FR-003, FR-004, FR-007, User Stories 2 and 3, edge cases. data-model.md "PackSetCheck" and
  "PackCheckReport". research.md squad S1, S4, S8 (D5) and S10.
- WP02 gives `PackReadout.facts()` (`Built(descriptor, facts)` or `Unreadable(code, detail)`) and
  `PackRulesets.closure(presetIdsOf)`.
- `PackCheck.validate(root)` reads once and returns `PackVerdicts.of(read.files(), read.rationale().and(placement))`.
- The round trip's production flow is `packs.infra.storage.PackRoundTrip.run(source, target)`, which
  `pack-validation` may not name. Its result type `PackRoundTripResult` is `packs.domain.roundtrip`. The verdict is
  `new WholePackRoundTripRule(WholePackExpectation.none()).apply(result)`, as `WholePackDrift` does in the drift test.
- `PACK_VALIDATION_IS_FRAMEWORK_FREE` (direct half: the packs domain and the kernel only).

## Branch Strategy

Planning base `feat/478-pack-check`, merge target `feat/478-pack-check`; worktrees per lane from `lanes.json`. Run
`spec-kitty agent action implement WP03 --agent <name> --mission pack-check-cli-01M7SASC`.

## Subtasks

### T010 — `PackCheck.check(root)`

- Add `public Checked check(PackRoot root)`, where `record Checked(PackReadout readout, PackVerdict verdict)` is nested
  in `PackCheck`. `validate(root)` returns `check(root).verdict()`, so there is one read per call.
- **Tests** (new `PackCheckReadoutTest`): `check` reads once (count calls on a stub `PackReading`), and its verdict
  equals `validate`'s for a valid example pack and for a planted subject.

### T011 — `PackSetCheck` and `PackSetVerdict` (`ai.speckitty.charter.packvalidation.set`)

- **Input:** `record PackSetEntry(PackRoot root)`, in order. The configured level is the root's, else the descriptor's
  `declaredLevel` from `Built`. Add a `boolean roundTrip` flag, or a separate method.
- **Algorithm:**
  1. For each entry, `check(root)`.
  2. If every readout's facts are `Built` and each has a level:
     - build `PackUnderResolution(new ConfiguredPack(new PackIdentifier(root.name()), level), descriptor,
       facts.changed(), facts.defined(), Set.copyOf(facts.terms()))`, with the same field mapping as
       `CompositionFactsBuilder.underResolution`;
     - run `PackRulesets.closure(p -> presetIds.get(p))`, the preset ids from `facts.presets()` by pack;
     - collect the faults with their packs: use `CompositionRules.faults(...)` and its preset counterpart, which keep
       `RefusalPack`s, the way `closure()` builds them. Avoid a second judging path: if the reasons and the faults
       must both be had, take the faults and derive the reasons from them, as `CompositionRules.reasons` does.
  3. Otherwise `CLOSURE` is skipped (D3). The skip names the packs whose facts are `Unreadable`, or whose level is
     unknown.
- **The verdict:** `PackSetVerdict` holds:
  - `List<PackResult>`, each with the entry, its `PackVerdict`, its level (`Optional`), its `Facts`, and its round-trip
    reasons;
  - `List<CompositionFault>`;
  - `checks` (`PACK`, then `CLOSURE` if run, then `ROUND_TRIP` if asked);
  - `skipped`.
- **D3, the problem rule.** It is applied in WP04's `Problems`, but the verdict must make it possible: an `Unreadable`
  pack whose `PackVerdict` has no violation still fails. Give `PackResult` a `failing()` that says so, so the CLI can
  report the refusal once.
- **No duplicate packs:** two entries with one name are refused with `IllegalArgumentException`. The CLI checks real
  paths first.

### T012 — The round-trip port

- New `packvalidation.roundtrip.RoundTripping`: `@FunctionalInterface PackRoundTripResult run(PackRoot root)`. The
  composition root (WP04) implements it over `PackRoundTrip.run(source, tempTarget)`.
- `PackSetCheck` runs it per pack when asked, and judges the result with
  `new WholePackRoundTripRule(WholePackExpectation.none())`. A pack whose `PACK` verdict has a violation is still
  round-tripped: the two are independent checks.

### T013 — Tests (`service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/set/`)

Use the example packs from `:packs` test fixtures, with the real `FileSystemPackInspector`.

- **Valid set:** the five example packs at their levels give no violation, no composition fault, and
  `checks = [PACK, CLOSURE]`.
- **D5:** `project` alone gives two `pack_dependency_unknown` faults whose packs name `project`. The same pack with
  its dependencies passes.
- **Cycle:** two copies of org packs edited to depend on each other give `pack_dependency_cycle` naming both. Edit
  copies in `@TempDir`, never the fixtures.
- **Preset conflict:** two org packs holding one preset id give the preset conflict code.
- **D3:**
  - a pack with an unreadable graph skips `CLOSURE`, and `skipped` names it;
  - its `PackResult.failing()` is true even when its `PackVerdict` has no violation. Find a plant for which that
    holds, or construct the readout with a stub `PackReading` and document it.
- **Round trip:**
  - with a fake `RoundTripping` returning a red result, the round-trip reasons appear and `ROUND_TRIP` is in `checks`;
  - with the real flow over `built-in` (test-only composition), no reason.
- **Order:** the result order follows the input order.
- **Mutation floor:** the `pack-validation` group stays at or above 85. Run the module's PIT task as the build-logic
  documents and report the score in the PR.

## Definition of Done

- `./gradlew verifyAll` is green.
- `PACK_VALIDATION_IS_FRAMEWORK_FREE` passes with the new package.
- Every branch of the algorithm above has a test that fails if the branch is removed.

## Risks and reviewer guidance

- **A second CLOSURE.** The set check must call `PackRulesets.closure(presetIdsOf)` (or the fault collectors it is
  built from), never its own rules.
- **Level unknown.** A `--pack dir` with no level and no readable declared level means `CLOSURE` is skipped for the
  whole set, and the skip names that pack.
