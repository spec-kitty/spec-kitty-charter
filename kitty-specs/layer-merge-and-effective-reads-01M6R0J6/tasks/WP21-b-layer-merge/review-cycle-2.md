---
affected_files: []
cycle_number: 2
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command: spec-kitty agent tasks move-task WP21 --to approved --mission layer-merge-and-effective-reads-01M6R0J6 --agent claude:opus:reviewer-renata
reviewed_at: '2026-11-08T16:03:04Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP21
---

Approved by claude:opus:reviewer-renata: approval:WP21

# WP21 review, cycle 2: B · packs: the layer merge

Reviewer: claude:opus:reviewer-renata
Commits reviewed:
- 0a45cdf1 (red)
- f0a000e7 (feat)

Everything else since cycle 1 is status or planning files only.

Verdict: **approved**

## B1: resolved

`LayerMerge.settledBy` / `unsettled` (LayerMerge.java:271-296) now refuses with `pack_sibling_conflict` whenever two or more holders at one level have no `overrides` entry naming one of them.
- The refusal names the artifact, every sibling (name and pack_id) and the level.
- It is not limited to steps. It applies to every artifact kind, so any kind the compose leaves out of its contest is covered too.
- Today that is only `action:` (`contestableDefinitions`). `anti_pattern` nodes declared without a file are still contested by the compose.
- For every other kind the compose has already refused, so the new refusal cannot fire there. That makes it strictly defence in depth.

A step contest settled through its mission type still resolves through `settlementKey` (`action:M/s` → `mission_type:M`).

Presets keep last-in-closure-order through an explicit `lastHolder` (C29, C34). A single holder at a level skips the picker.

The class Javadoc is corrected.

The new row `c15UnsettledStep` composes two ORG packs that both hold `action:M/s`. It asserts:
- the compose lets them through;
- the merge refuses with the code;
- the message carries the artifact, both names and the level;
- both packs appear in `packs()`.

**Can the refusal reach `/offering` or a write?** No. `LayerMerge.merge` has exactly one caller in main code, `ClosureView.MergeInputs.merged`. `ClosureView.merged()` has no caller in main code yet. So the refusal cannot reach `/offering` or any write path (FR-023).

## N1: resolved

`MergedClosure` keeps each winner's languages as an unmodifiable `LinkedHashSet` (MergedClosure.java:110-111). The row `c18LanguageOrder` pins six languages in a non-sorted order.

## Red first

I checked out 0a45cdf1 in a throwaway worktree and ran `:packs:test --tests '*LayerMergeTest'`. Of 22 rows, 2 failed, both on assertions:
- "Expecting code to raise a throwable" (B1);
- "Expecting actual [bravo, delta, mike, alpha, …]" (N1).

The red commit changes the test only, and the feat commit follows it.

## Regressions

- `:packs:check` passed on HEAD 1d9269ff.
- `:deployable:test` passed: 1127 tests, 0 skipped, 0 failures, 0 errors. This covers the Spring context, BDD and `HexagonalArchitectureTest`.
- No test was weakened, and no suppression was added.
- No file outside the cycle-1 set changed.

## Findings

None blocking and none new.

The runner has already recorded these in `research/closeout-notes-pr-b.md`:
- ruling (a), deferred to #358;
- N2;
- N3, which goes to WP22.

## Hand-off (unchanged from cycle 1, still relevant)

- **WP22+** must read the floor and the presets from `merged()`, not from `ClosureView.floor()` or `heldPresets()`.
- **The steps of a mission type that loses** are merged per URN. A loser's unique steps stay in force. Confirm this reading in the effective-context work.
- **Graph-declared nodes count as holding.** The docs/divergence WP should record:
  - this rule;
  - C21 judged over C∖{w}, refused with `pack_unreadable`;
  - the new read-time `pack_sibling_conflict` for unsettled step contests, which the compose does not refuse.
