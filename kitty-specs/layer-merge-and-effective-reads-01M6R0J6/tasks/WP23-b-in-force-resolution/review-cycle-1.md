---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command: spec-kitty agent tasks move-task WP23 --to approved --mission layer-merge-and-effective-reads-01M6R0J6 --agent claude:opus:reviewer-renata
reviewed_at: '2026-11-08T16:59:56Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP23
---

Approved by claude:opus:reviewer-renata: approval:WP23

# WP23 review, cycle 1: B · packs: in-force resolution, a stratified fixed point

- **Reviewer:** claude:opus:reviewer-renata
- **Lane:** lane-u. I reviewed the WP23 commits 7e3be32b..HEAD (6b683349 … 35f5164a) and ignored the planning merges.
- **Verdict: approved.** There are no blocking findings. The document mismatches and non-blocking items below go to the runner and the later WPs.

## What I ran

- **`:packs:check`:** passed (test, Checkstyle, PMD, SpotBugs, Spotless, Javadoc). The property test runs 2,000 seeded closures.
- **`:activations:check`:** passed.
- **`:deployable:test`:** passed. 1127 tests: 0 skipped, 0 failures, 0 errors. This covers the Spring context, BDD and `HexagonalArchitectureTest`.
- **Red first:** I ran four red commits in a throwaway worktree. All fail on assertions and compile against stubs:

  | Commit | Test | Result |
  |---|---|---|
  | 6b683349 | `InForceResolutionPropertyTest` | `AssertionFailedError` |
  | a8ac2549 | `InForceResolutionTest` | 18/18 fail, "expected ACTIVE / EXCLUDED …" |
  | 9945ae51 | `InForceResolutionTest` | 12/33 fail: state and `satisfiedBy` |
  | c9687861 | `ClosureViewMergedTest` | 5/15 fail |

  In each pair, the red commit comes before its feat commit.
- **Diff hygiene:**
  - No `@SuppressWarnings`, `@Disabled`, `NOPMD` or `@WIP` was added.
  - Owned files are respected.
  - The out-of-map edits carry their rationale (35f5164a, c9687861).
  - `Reachability.java` is untouched. FR-038 is done in `ClosureView.reach`, which is the smaller edit.

## Behaviour checked against the rules

| Rule | Status |
|---|---|
| E2/E3 candidates (no kind gate, language filter, deny, locked) | correct |
| E4/A3 transitive hiding (a hidden overrider still hides) | correct |
| C27 (non-`open` target stays, `overrideNotApplied`) | correct |
| E5/A2 floor fixed point, judged on the final set | correct |
| E6/A8 least-fixed-point pruning, binding and enhancer rings | correct |
| `DM-01M6R4W3ZMSESETMQ0ZQTCZCFJ` / `DM-01M6RFGFW3S0YJ222QMHD2W11B` (dropped and reported in `requiredNotInForce`) | correct |
| E9 precedence and buckets | correct |
| E1 stale ids | correct |
| C31 unheld | correct |

The A2, mutual-override and cross-stratum answers are pinned exactly by these tests:
- `theRequirementIsJudgedOnTheFinalSetAndTheAnswerIsExactlyTheRuledOne`
- `twoRequiredArtifactsOverridingEachOtherAreRefusedAsUnreadable`
- `anOverriderPrunedByItsBindingNoLongerSatisfiesTheRequirement`

Every iteration is bounded by the closure size and fails loudly past the bound.

## Rulings on the implementer's questions

### 1. The two test corrections are required by the rules, not a weakening

**(a) `aLockedTargetStopsTheChain` → `aLockedArtifactInForceStillHidesWhatItOverrides` (ffb51068).**
- The old test had a locked `L` that overrides an open `T`, and expected `T` active/selected.
- E3 puts every locked artifact in `Base`, and E4 hides everything `Ov(Base)` reaches. So `L` hides `T`; the old expectation contradicted E4.
- The "locked target stays" half of C27 is still pinned by `aLockedTargetStaysAndTheOverrideIsReported` and `anExtendableTargetIsNotHiddenEither`.
- **Non-blocking N1:** "stops the chain there" can only be seen through `satisfiedBy`. Add a test: `F → L(locked) → T`, with `T` required. Assert that `T` is satisfied by `L`, never by `F`.

**(b) The property oracle (04443f48).**
- I reproduced the reason. I ran the old oracle (8dcde283) against the final implementation, and it fails on seed 20261108, case 47:
  - `action:m1/s1` is required.
  - `procedure:a2` (active) satisfies it through `a2 → action:m1/s0 → action:m1/s1`.
  - The old oracle dropped `s0`'s edges because `s0`'s final E9 reason is a pruning reason. But `s0` was *hidden*, not pruned, and under E4 a hidden overrider still hides.
  - So the old oracle contradicted E4, and the implementation is right.
- The new oracle uses two sound bounds instead:
  - The hide check is a lower bound, through intermediates that cannot be pruned.
  - The `satisfiedBy` check is an upper bound, over every edge.
- It keeps FR-029, I9, I11, determinism and the cycle refusal, and adds outcome-mix assertions.
- **Non-blocking N2:** the oracle no longer pins a chain through a hidden intermediate that can be pruned. Add a unit test with the seed-47 shape (a hidden step/enhancer intermediate keeps the chain and the satisfaction).

### 2. The `ClosureViewMergedTest` edit is required by FR-038, not a weakening

- The old assertion said `ClosureView.reach` (the cascade) follows both versions' edges. That is exactly what FR-038 and E20 forbid.
- The unmerged-index property is kept on `Reachability.reach(view.edges(), …)`.
- `view.reach` is now asserted equal to the merged walk.
- The new tests pin the cascade over a layer override and its identity where no override exists:
  - `theCascadeNeverFollowsAnEdgeOfAnOverriddenArtifact`
  - `theCascadeWalksTheSameEdgesWhereNoLayerOverrideExists`

### 3. Writes now build the merge: acceptable, because FR-038 requires it

Contract and documents still need follow-up; see the hand-offs below.

**What requires it:**
- Spec **FR-038** (PR B, `DM-01M6R4W1H9P3451V89KHP97XKM` "Merged edges") and plan IC-07 ("The cascade (FR-038) … the write cascade walks the merged `EdgeIndex`"; Risks: "open item 3 is closed").
- The traceability row FR-038 → IC-07 → B.

**Why FR-023 and SC-004 are not broken:**
- **FR-023** covers only `GET /offering` and its reads ("unchanged … unmerged"). `/offering` still never builds the merge.
- **SC-004** asks that today's offering and composition scenarios pass unchanged. The full deployable run shows they do.

**Exact contradictions for the runner to escalate or fix:**
- **plan.md, IC-07 "Facts" bullet:** "`ClosureView.merged()` builds the merge lazily … so the writes and `/offering` do not change (FR-023, SC-004)". This contradicts the same IC's FR-038 bullet. "the writes and" should go, or say "the offering; the writes change only as FR-038 says".
- **composition-and-activation-rules.md, E20:** "Ruled, not built (layer-merge PR C, with the merge)". The plan puts FR-038 in PR B. E20 should become Built in PR B.
- **The WP21 `ClosureView.MergeInputs` Javadoc:** already updated by WP23.

**Effect on writes:**
- An activation write can now refuse with `500 pack_unreadable` when the merge refuses (C21, the read-time step `pack_sibling_conflict`, `self_override`).
- That falls under the 500 the activation write paths already declare ("does not compose … 500 pack_unreadable").
- Registration (IC-15) already refuses C21 and C36 at write time, so the new refusal is rare: pre-existing or out-of-band packs, and unsettled step contests.

**Required follow-up (non-blocking for WP23, blocking for the PR B closeout):**
- a `contracts/charter/changes/` `### Changed` fragment covering both changes:
  - the cascade over merged edges;
  - the write's new merge refusals.
- a divergence-register entry, for core's cascade over unmerged edges.

### 4. The refusals beyond the literal rules are sound readings; record them

**C28, "reachable from the candidates or the floor".**
- A cycle that touches a member of `Base ∪ Req` makes that member hide itself through the chain, so it must refuse.
- A cycle wholly outside `Base ∪ Req` that is only reached is harmless under E4/E5: those nodes never enter the set. Refusing it is stricter than the letter.
- It is consistent with "never broken by a pick" (A2, `DM-01M6RN6BWE6SVZZE8ERQ8Z9MV6`).
- **Accepted.** The docs WP should clarify C28's "among" on the rules page, and the divergence register should note the conservative reading.

**E7, "pruned while its overrides apply, in force once withdrawn" → `pack_unreadable`.**
- Accepted. Under the literal one-rerun E7, a step that overrides its own mission type would end up in force with its override silently not applied: an incoherent answer.

**Further finding, non-blocking for the code, which follows the newer ruling: the documents disagree about E7.**
- `InForceResolution.solve` (InForceResolution.java:226-239) iterates withdrawal to a fixed point. It answers where the literal E7 refuses.
- I confirmed this with a scratch probe: `A` enhancer→`Z`, `A` overrides `B`; `B` enhancer→`Y`, `B` overrides `C`; select `A`, `B`, `C`. The code answers `C` active. The literal E7 refuses, because the second run removes another overriding enhancer.
- The code follows **research R13**: "One pass with a re-run (the earlier R11 step 5): replaced". It also follows A2 `DM-01M6RA7M56M1409EV9JX1EW83K` (repeat until nothing changes).
- These still state the superseded one-rerun rule:
  - rules page **E7** ("run once more … if that run removes another overriding enhancer, the read refuses");
  - **data-model.md:224-226** ("the steps run once more; a second change refuses").
- **Hand-off:** the docs WP should rewrite E7 and data-model.md in R13's terms, including the unstable refusal. **N3:** WP23 should add a unit test pinning the chained-withdrawal answer above, so the reading is executable.

### 5. The other behaviours are acceptable

**A required artifact the language filter excludes is put in force.**
- The rules page flags this as Unclear under E5, and nothing rules it out. FR-029 requires the floor to be satisfied or reported.
- Accepted. Record it in the divergence register and in the E5 Unclear note, and keep `aRequiredArtifactTheLanguageFilterExcludesIsStillPutInForce`.

**Only steps are bound.**
- `MergedView.resolutionEdges()` passes `bindings = Map.of()`, and `Edges.bindings` is the seam for other kinds.
- E2's `bnd` allows "an artifact that declares a mission-type binding". The only such fact in the codebase is `Template.missionType()`, and templates are not activatable artifacts (E11 Unclear).
- Accepted for now. If a later WP brings a binding fact for an activatable kind, it must fill `bindings` in `resolutionEdges`.

**`InForceRequest.pins` is carried but unused.**
- Accepted. `pinNotHeld` and dead pins (E1, B1) belong to WP24.

## Non-blocking findings

- **N1:** a C27 "stops the chain" test through `satisfiedBy` (ruling 1a).
- **N2:** a unit test for a chain through a hidden intermediate that can be pruned (ruling 1b).
- **N3:** a unit test pinning the iterated E7 answer (ruling 4).

## Hand-offs

**WP24 (reads, skipped report):**
- Map `InForceStates` through the `AvailablePacks` port, with no shadow logic in `activations`.
- `pins` and `deadPins`/`pinNotHeld` are yours.
- `requiredNotInForce` carries both the floor-enhancer and the binding drops. Map them to `requiredUngrounded` and the buckets per E14.
- `unheldRequired` comes from `merged().floor()`, which is the A1 union. Do not use `ClosureView.floor()`.

**WP25:**
- N3 from the WP21 review (the facts cache on the Spring path) is yours.
- `ClosureView.inForce` builds the merge per view. Keep one verified read per effective read (E19), with the 3× `stale_input` retry.

**WP26 / docs and closeout:**
1. `### Changed` contract fragment: the cascade walks merged edges (FR-038), and a write over a closure whose merge refuses answers `500 pack_unreadable`.
2. Divergence entries:
   - the cascade over merged edges;
   - C28 as reachable cycles;
   - the E7 unstable refusal;
   - required items put in force despite the language filter;
   - from WP21: C21 over C∖{w} as `pack_unreadable`, and the read-time step `pack_sibling_conflict`.
3. Rules page:
   - E7 rewritten to R13's fixed point;
   - E20 Built in PR B;
   - C28's "among" clarified;
   - the E5 Unclear note answered.
4. data-model.md:224-226: align with R13.
5. plan.md IC-07: fix the "writes … do not change" sentence. Escalate to the operator if the runner reads FR-023 as covering writes; I read it as `/offering` only.
