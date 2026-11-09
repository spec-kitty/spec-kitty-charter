---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-08T15:46:18Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP21
---

# WP21 review, cycle 1: B · packs: the layer merge

Reviewer: claude:opus:reviewer-renata (profile reviewer-renata)
Lane: kitty/mission-layer-merge-and-effective-reads-01M6R0J6-lane-s, WP21 commits ae5fede7..1abdbb37 (HEAD 7b5f296c adds status files only)
Verdict: **changes requested** (1 blocking finding)

## What was checked, and what passed

- **Commands:**
  - `:packs:check` passed: test, Checkstyle, PMD, SpotBugs, Spotless, Javadoc and openPacksRoundTrip.
  - `:deployable:test` passed: 1127 tests, 0 skipped, 0 failed. It covers the Spring context, the BDD suite and `HexagonalArchitectureTest`, which is unchanged in the diff.
- **Red first:** I checked out four red commits in a throwaway worktree and ran the named test class on each. Every failure was an `AssertionError`, and every red commit compiled against stubs:
  - 5c7cc198 `LayerMergeTest`: 5 of 7 failed;
  - 7afe9fa0 `EdgeIndexTest`: 4 of 12 failed;
  - a4f69c8f `CompositionFactsCacheTest`: 7 of 15 failed;
  - cc7e7e5e `ClosureViewMergedTest`: 7 of 9 failed.
  In every pair, the red commit comes before its feat commit.
- **Suppressions and tests:** no test was weakened, disabled or suppressed. No `@SuppressWarnings`, `@Disabled`, `NOPMD` or `@WIP` was added, and the suppression register did not change.
- **/offering and writes:** no offering test or write test changed. `holderOf`, `heldPresets` and `floor` keep the first-holder and winning-preset rules, and the merge is built only on the first `merged()` call (`ClosureViewMergedTest`: a refused override does not refuse the read).
- **`LayerMerge`:** it is pure, with no I/O, no Spring and no state. The merged `EdgeIndex` keeps `stepsOf` and `Reachability` working (`EdgeIndexTest.theStepsOfAMissionTypeAreThoseOfTheWinnersOnTheMergedView`, `ClosureViewMergedTest.aReachOverTheMergedEdges...`).
- **Rules:**
  - C17, C18 and FR-009 (whole replacement, no language inherited);
  - C15 and #357 (the settled member's version is in force, and the loser is not a layer override);
  - C21 and FR-011, with the judging set C ∖ {w}, extendable refused, the winner's own lock never blocking, a third pack's raise refusing, A15 scope, and both packs and the level named;
  - C19, C20 and C36 (edges from a loser dropped, edges to a loser re-pointed whether bare or qualified, the self-override refused);
  - C29, C31 and A1 (the winner plus the union of the must-have items of every copy, the counter-example row, unheld items reported per A10).
  Each of these matches IC-07, research R3 and the rules page.
- **Facts cache, decision DM-01M6XT2FKK7YPV1GCESFW1ANZ7 and the #416 invariants:**
  - it is keyed on the configured name. `PackIdentifier.value()` equals `location.name()`, which `CommitStorePackWriteStore`/`PackCommitStore` bracket with;
  - the key is `ofPack` plus `PackRevisions.currentRevision`;
  - it neither serves nor fills on an odd generation, a moved generation, a revision moved mid-read or a `RegistryWriteLock` holder. Staged views (`RegistrationImpact.Views`) use the uncached constructor, so they never reach the cache;
  - failures are not kept, and a revision that cannot be read leaves the loader's refusal as the answer;
  - it is an LRU bounded at 256, with a test above the cap;
  - it lives in `packs.application`, with no storage type.
- **Shared-file edits:** all are necessary and minimal.
  - `OfferingReadService`'s @Autowired constructor is the only way to put the cache on the compose path;
  - `ClosureQueries.viewOf` passes `MergeInputs`;
  - `WriteFacts` carries languages, which keeps the overlay facts complete, and a removed artifact drops its entry;
  - `PackWriteGeneration` changes its Javadoc only, as the WP12 hand-off asked;
  - the two new tests pin the language assembly and the write overlay.

## Findings

### B1 (blocking): an unsettled sibling contest on a step falls back to "last loaded wins"

**Where:** `service/packs/src/main/java/ai/speckitty/charter/packs/domain/LayerMerge.java:260-266` (`settledAmong`), and the class Javadoc at :31-35.

**Problem:** `settledAmong` answers `siblings.getLast()` when no `overrides` entry settles a contest. The implementer says the compose makes this unreachable, but it does not:

- `PackCompositionResolver.contestableDefinitions` (PackCompositionResolver.java:606-612) leaves every `action:` URN out of the sibling contest, on the assumption that siblings defining a step also define its mission type.
- A pack holds `action:<M>/<step>` simply by declaring it as a node of a graph document (`DomainRepositoryService.declaredWithoutFile`, :565). It does not have to define `mission_type:M`.

So two org packs that each declare the node `action:M/s`, where neither defines `M` (for example, `M` is built-in), compose without a refusal. `LayerMerge` then puts the later pack's step in force without a word, and C19 drops the earlier pack's edges from it.

This breaks:

- C15: "there is no 'last loaded wins'";
- C16: one definer per level after C15;
- I2;
- T103: "an unsettled one still refuses".

**Fix I want:**

1. For artifacts, replace the fallback with a refusal: `pack_sibling_conflict`, worded like the compose's C14 message, naming both siblings and the level. The refusal is raised from `merged()`, so writes and `/offering` are unaffected (FR-023).
2. Keep the "last in closure order" rule for **presets only**. There ADR-0053 d6, C29 and C34 make same-level copies parent-and-child only. Give the preset path its own explicit call, not the shared fallback.
3. Add a `LayerMergeTest` row, red first: two `ORG` packs each holding `action:M/s`, no `overrides` entry, no `mission_type:M`. The merge must refuse with the code and both names.
4. Correct the Javadoc at :31-35.

### N1 (non-blocking): `MergedClosure` loses the declared order of languages

**Where:** `service/packs/src/main/java/ai/speckitty/charter/packs/domain/MergedClosure.java:460-463`.

**Problem:** `Set.copyOf` turns the `LinkedHashSet` that `LayerMerge.languagesOf` builds into a set whose iteration order changes between JVM runs. The effective reads of WP22+ would then serialize languages in an unstable order.

**Fix:** keep the order, with an unmodifiable `LinkedHashSet` or a `List`. You may do this in the B1 cycle.

### N2 (non-blocking, hand-off): the cache adds a revision read per pack per read

**Problem:** `CompositionFactsCache.facts` calls `PackRevisions.currentRevision` on every call, and that is itself a listing plus a digest. The cache therefore trades a parse for a digest, and it does not remove the listings that #416 measures. That is acceptable under the decision, which requires the out-of-band check (#416 invariant 4).

**Fix:** none in this WP. Do not claim a #416 win in the PR B text, and record the cost for #416 (options A and B).

### N3 (non-blocking, hand-off): no test proves the facts cache is on the Spring path

**Where:** `OfferingReadService`'s @Autowired constructor.

**Problem:** the full deployable run boots the context and drives every BDD read through the cached constructor, so the wiring works. But no test asserts that the deployed `OfferingReadService` reads through `CompositionFactsCache`.

**Fix:** WP22 adds a context-level check when the first effective read lands, for example a counting `PackArtifactRepository` showing a warm second read does not reload an unchanged pack.

## Rulings on the implementer's open questions

**(a) A settled same-level sibling that replaces a locked artifact is not gated by C21.**

- **Ruling:** accepted for WP21, because it is what the plan says.
  - C21 gates "a layer override … by a later level".
  - R3 says "a settled sibling contest is not a layer override".
  - ADR-0063 D3 defines only how the level is computed, and names two callers (the registration gate and the activation lock query), neither of which is a settlement.
- **Open item:** the gap is real. Until #358 limits who may settle, any pack of the closure can, through a descriptor `overrides` entry, displace a sibling's locked artifact with no level check. That runs against ADR-0012's purpose (a level protects the original from others), and C27's statement that "both override paths share one gate" does not name this third path.
  - Log it as an open review item for the architect: either a C21-style gate on settlements, or #358's restriction.
  - The PR B docs WP should mention it beside C15 on the rules page.
- **Severity:** not blocking WP21.

**(b) `LayerMerge` falls back to the last holder for an unsettled sibling contest.**

- **Ruling:** not safe. The fallback is reachable through steps declared as graph nodes, so it must refuse. See B1. Keep the last-holder rule for presets only, where C29 and C34 make it well defined.

**(c) No Spring-context test covers the new `OfferingReadService` wiring.**

- **Ruling:** acceptable for WP21. The full `:deployable:test` run (1127 tests, 0 failures) boots the context, and every BDD read composes through the @Autowired five-argument constructor.
- A direct assertion is handed to WP22 (N3).

## Hand-off notes (WP22–WP26, PR B closeout)

- **Floors and presets:** read the floor and the presets from `merged()` (A1 union over every copy), not from `ClosureView.floor()` or `heldPresets()`. Those keep the winners-only floor, for writes and `/offering` (FR-023).
- **Steps under the merge:** the merge is per URN, so a winning `mission_type:M` does not take its losers' unique steps with it. A built-in step `M/s2` stays in force under a project's `M` that has only `s1`. Under A7, where steps are artifacts, that follows the rules, but the effective-context read (WP for IC-08/FR-019) should confirm the reading with the architect.
- **Graph-declared nodes:** a node declared in a graph counts as holding the artifact. An org graph that only declares a built-in step's node, to hang an edge from it, therefore replaces that step and drops its built-in edges (C17, C19). Record this for the divergence and docs WP; a qualified source (`built-in::action:M/s`) avoids it.
- **Divergence register (C-007):** the docs WP should record:
  - C21 judged over C ∖ {w} and answered as `500 pack_unreadable`, not `governance_policy_violation`;
  - the settlement gap of ruling (a);
  - the facts cache and its revision check (N2).
- **Rebase:** if B1 changes any signature, WP22 and WP23 depend on WP21 and must rebase on the fixed lane.
