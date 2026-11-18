---
work_package_id: WP08
title: CLOSURE, every composition fault collected
dependencies:
- WP05
requirement_refs:
- FR-003
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts for this mission were generated on slice-5-554-pack-closure-rulesets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-5-554-pack-closure-rulesets unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-closure-rulesets-01M7FA90
base_commit: 8813d8084246dfe7c63fa193ecd1009a9c4c74ab
created_at: '2026-11-17T06:49:24.903511+00:00'
subtasks:
- T042
- T043
- T044
- T045
- T046
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
- at: '2026-11-16T12:00:00Z'
  actor: planner-priti
  action: Replanned for the S1 to S3 rulings (squad-shaping)
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/CompositionRules.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/CompositionRulesTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/CompositionRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/EdgeIndex.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/CompositeComponentResolver.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCompositionResolverTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/CompositionRulesTest.java
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP08 - CLOSURE, every composition fault collected

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else.

Then read, in `kitty-specs/pack-closure-rulesets-01M7FA90/`:

- `spec.md` (the Intent Summary's rulings table);
- `plan.md` (Decisions, and the IC named below);
- `data-model.md`;
- `research/squad-shaping.md` (S1 to S3 and S6) and `research/design-r3.md` (with its §7 folds and §8, what S1 to S3
  and S6 superseded), and `research/squad-plan.md`;
- `research/squad-similar-surfaces.md` (file:line map, as of before slice F; re-check each reference).

Charter rules that bind every subtask:

- The charter's model is canonical. There is no legacy reader and no shim.
- Tests carry only the canonical shape, apart from error-path fixtures.

## Branch Strategy

- Planning base and final merge target: `slice-5-554-pack-closure-rulesets`. The mission branch is `kitty/mission-pack-closure-rulesets-01M7FA90`.
- The execution worktree is allocated per computed lane from `lanes.json`; this mission has one lane.
- Start with `spec-kitty agent action implement WP08 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.

## Objective

The resolver's composition checks collect every fault, instead of throwing the first. `CLOSURE` wraps them as a
kernel ruleset for pack-check. The offering keeps answering exactly today's first refusal, with its pack list, in
today's interleaved order. Spec FR-003; plan IC-07; squad findings A-M4, C-6 and C-7. `WP01`'s `CompositionGoldenTest`
is now owned here (out-of-map hand-over).

## Subtasks

### T042: Collect-all functions

- **The throws to replace:** `indexById` (:827), `indexByName` (:842), `refuseDisjointRanges` (:576),
  `dependenciesOf` (:861-893), `requireLevel` (:914), `topologicalOrder` (:977), `refuseSiblingContests` (:628 and
  :696) and `refusePresetContests` (:718). These are pre-F line numbers; re-check them.
- **The change:** each becomes a function that returns `List<Refusal>`, in today's check order within the function.
- **`resolve`** collects all of them in today's order: `DUPLICATE_ID`, `NAME_CONFLICT`, disjoint ranges, then pack by
  pack in `STABLE_ORDER` (dependency: self-cycle, unknown, out of range, level; parent: cycle, unresolved, level),
  then sibling contests, then the topological `CYCLE`. It throws the first only where today's callers need a throw.
- **The resolver's precondition** calls WP03's `DescriptorChecks`.

### T043: `CompositionRules`

- **The ruleset:** `ModularRuleset<List<PackUnderResolution>>`. Its `Reason`s carry today's `RefusalCode` tokens,
  through `PackCodes`.
- **Its name on the core:** `PackRulesets.closure()` (`data-model.md`). `PackRulesets` is WP07's file and WP07 runs
  beside this work package, so WP09 adds the one-line delegate to `CompositionRules`; expose a static accessor here
  for it.
- **Its input:** the `PackUnderResolution` WP05's one composition-facts builder makes from the one read (P7). Never
  build a second derivation here.
- **The pack list:** a `Reason` has no slot for it, so `CompositionRules` keeps a typed `CompositionFault(code, packs,
  detail)` beside the `Reason` for callers that need the list (A-M4).
- **Presets:** a separate phase. The preset ids come from `PackUnderResolution` or `PackCompositionFacts`.

### T044: The offering

- **`OfferingReadService` (`:359-375`):** keeps its interleaved order: resolve, delegations, references (#556), step
  enhancement targets, preset contests, then case-only matches. It takes the first refusal of each phase in turn,
  byte-equal to today.
- **The descriptor check** at `:549-558` calls `DescriptorChecks`, so `composition.feature:304` is unchanged.
- **Other callers:** `EdgeIndex` and `CompositeComponentResolver` call only `checkReferences`, which stays (#556).
  Confirm this, and leave them unchanged if so.

### T045: Classification

Record the decision in the WP review log:

- `refuseUnresolvedDelegations` (slice F, `PACK_UNREADABLE`) and `checkStepEnhancementTargets` stay in the offering's
  sequence and are not part of `CLOSURE`.
- `refuseCaseOnlyMatches` and the three reference checks are #556's (`ClosureWalk`).

### T046: Tests

- **`CompositionRulesTest`:** one failing and one passing case per code. Multi-fault sets report every fault.
- **`CompositionGoldenTest`:** reproduces WP01's `composition.json` byte for byte.
- **`PackCompositionResolverTest` (56 tests):** its refusal cases become `CompositionRulesTest` cases. Its composition
  and precedence cases stay.

## Definition of Done

- Every composition fault is collected.
- The offering's golden snapshot is byte-equal.
- `composition.feature` passes.
- `verifyAll` is green.
- A narrowed PIT run on the resolver is reported.

## Reviewer guidance

- Is the first refusal identical for multi-fault sets?
- Are the reference checks untouched?
