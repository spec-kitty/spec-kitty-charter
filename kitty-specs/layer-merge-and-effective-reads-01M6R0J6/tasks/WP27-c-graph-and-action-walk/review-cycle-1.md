---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-09T13:05:55Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP27
---

# WP27 review, cycle 1 (reviewer-renata, claude:opus:reviewer-renata)

Lane `kitty/mission-layer-merge-and-effective-reads-01M6R0J6-lane-y`. Reviewed WP commits `ae1c3ed2..13d1c861`; the planning merge `fa3c2942` was left out.

Profile and context applied: `reviewer-renata`, a quality gate that does not implement. Directives 001, 024, 030, 032, 041 and 051. Tactics: code-review-incremental, test-readability-clarity-check, delete-the-assertion-not-the-test and test-scaffolding-as-design-smell. Charter review context: QA_003_NO_FAKE_GREEN, NO_FULL_HEAVY_SUITES_IN_MISSION (module tasks only), RECONCILE_CHANGE_SCOPE_TENSIONS, BDD and specification-by-example. Behaviour was judged against the WP, the rulings and the rule ids, not against core. The charter context reported that the `internal` and `qa` packs are missing on disk. This does not affect the review.

## Verdict: REQUEST CHANGES

One blocker: the suggestion-candidate list added by the decision 01M7059T addendum leaks targets reached by relations other than `suggests`. Everything else is sound:
- D4b
- OD-9, both forms
- OD-6: scope, binding taken before the winner filter, and an unheld target pruned with `ENHANCED_TARGET_NOT_IN_FORCE`
- the node predicate built in `ClosureView`
- the graph projection

Red-first evidence is real, and both gates are green.

## Findings

- **[BLOCKER] service/packs/src/main/java/ai/speckitty/charter/packs/application/ActionContextWalk.java:351-358**
  - Issue: the candidate collection checks only that the *step's* relation is `SUGGESTS` (`relation == RelationType.SUGGESTS`). It never checks the link's own relation. During step 3, every outgoing link of a frontier node whose target is out of force is filed as a `Suggested` candidate, carrying that link's `when` and `reason`. Links of kind `vocabulary`, `enhances`, `overrides`, `applies`, `replaces`, `rejects`, `delegates_to` and so on all leak through. `HARD_RELATIONS` masks only `requires`, `refines`, `scope` and `instantiates`.
  - Effect: an artifact hidden by an active overrider is reported as a suggestion. So are an out-of-force vocabulary target and an out-of-force enhanced target. This contradicts decision 01M7059T4K64TAVKXN3T1FKWYG ("reached only through a suggests edge") and 01M6Y6VE6VZQ4MXD98N93JB3GJ (an overridden artifact must not invite selection).
  - Reproduced on HEAD with a scratch test, since removed. Scoped `tactic:T1` has `vocabulary→glossary_pack:G`, `enhances→directive:D` and `overrides→tactic:O`, and only `mission_type:M` and `T1` are in force. `context.suggested()` returned `[directive:D, glossary_pack:G, tactic:O]`; the expected answer is empty.
  - Required change: add `link.relation() == RelationType.SUGGESTS` to the candidate condition. Add a red test with a scoped artifact whose out-of-force targets are reached by at least `vocabulary`, `enhances` and `overrides`, asserting no candidate.
- **[MAJOR] ActionContextWalk.java:408-410, and the `walk` Javadoc at lines 48-49 and 173**
  - Issue: entries are ordered by step, then URN. ADR-0018 D3 says "Entries are sorted by full URN string". The WP says "walk entries in ADR-0018 order, ties by the ADR-0018 parent rule then URN". The implementer flagged the ambiguity in the hand-off. Under the standing ruling (never accidental order), this order will be pinned on the wire by WP28 and by the step-enhancements mission, so it needs an explicit ruling now.
  - Required change: get a recorded decision. Either follow ADR-0018 D3 (URN order) or rule step-then-URN and note it as an ADR-0018 amendment or divergence for WP29. Then align the code and `theEntriesComeInStepOrderThenUrnOrder` with that decision.
- **[MINOR] ActionContextWalk.java:374-380**
  - Issue: the hard-reach mask is built only from *listed* sources. The start node's own `scope` edges are left out. An out-of-force artifact that the action scopes and that a listed artifact also suggests is reported as a candidate, although it is not "reached only through suggests".
  - Required change: decide and pin it. Either include the start node's outgoing hard edges in the mask, or record why a step's scope does not count, and add a test either way.
- **[MINOR] service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureViewTest.java**
  - Issue: the addendum has no case at `ClosureView` level for `Context.suggested()` with real in-force states. All candidate tests use test-supplied predicates. Per the node-predicate ruling, the real path should be tested once at view level.
  - Required change: add one `ClosureViewTest` case with a real resolution: a selected T1 suggests an unselected S, and S is reported with the edge's `when` and `reason`.
- **[MINOR] Behaviour change in the cascade: `Reachability.java:276` reads `EdgeIndex.stepsOf`, which is now the closure form**
  - Issue: an action declared where no pack defines its mission type is no longer entered by a cascade from `mission_type:M`. The change is covered only indirectly by the new `EdgeIndexTest.stepsOf` cases. No `ReachabilityTest` case shows it, and the WP29 prompt does not list it; only the implementer's hand-off does.
  - Required change: add a `ReachabilityTest` case. A reach from `mission_type:M` over a closure where pack B alone declares `action:M/s` must not enter it. Make sure the orchestrator relays the register entry to WP29.
- **[MINOR] API before pinning, ActionContextWalk.java:177**
  - Issue: `Context` carries no enhancers of the start step. A step enhancer of `action:M/a` never appears in the walk, because the start node is not an entry and `enhancedTargetsOf` filters the step out. If the step-enhancements mission needs "the enhancers of this action" from the context, `Context` will change shape after it is pinned.
  - Required change: confirm with that mission's runner. Either add `List<Urn> actionEnhancedBy` now, or state in the Javadoc that the start step's enhancers are out of scope.
- **[MINOR] API naming, ActionContextWalk.java:149 and 177**
  - Issue: `Suggested` and `Context.suggested()` read like "the entries of step SUGGESTS" (`Step.SUGGESTS`). They are out-of-force *candidates*, and the name will be pinned.
  - Required change: rename to something unambiguous before pinning, for example `SuggestionCandidate` and `suggestionCandidates()`.
- **[MINOR] service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepHolding.java:57-63, 83-91, and EdgeIndex.java:256, 269-278**
  - Issue: `heldByPack` calls `List.contains` twice per URN, so `stepsHeldByPack` is O(n²) per pack. `EdgeIndex.stepsOf` recomputes `heldSteps()` over the whole closure on every call, and `Reachability` calls it once per mission type it expands. `ReachabilityBudgetTest` is green, so this is not a defect today.
  - Required change: copy the defined artifacts to a `Set` once inside the function, or cache `heldSteps()` on the index (it is immutable). No API change.
- **[NIT] service/packs/src/test/java/ai/speckitty/charter/packs/application/GraphProjectionTest.java:134-148, commit 41bac554**
  - Issue: the commit message and the hand-off say "assertions unchanged". The fix also removed an expected tuple (`directive:D, BUILT_IN, alpha`). The third key of `OVERRIDE_ORDER` (losing pack) is therefore untested. It is also unreachable, because the merge refuses two packs of one level for the same URN.
  - Required change: correct the record. Either drop the dead third comparator key, or note in the Javadoc that the merge makes it unreachable.
- **[NIT] ActionContextWalk.java:197**
  - Issue: `BEST_PARENT_LAST` sorts ascending, and the smallest value wins. The name says the opposite.
  - Required change: rename it, for example `EDGE_TIE_ORDER`.

Checked with no finding:
- OD-6 scope: `StepHolding.soleActionTarget` accepts only a `step_enhancement` with exactly one distinct target, and that target is an `action:`.
- OD-6 binding: derived in `ClosureView.resolutionEdges` from the winner's written edges before the winner filter. An unheld target keeps its edge, so the enhancer is pruned with `ENHANCED_TARGET_NOT_IN_FORCE` (I11).
- Every other shape keeps E6, with one test each. No fixture declares a facts binding.
- `InForceResolution.resolve` has a single caller, so the derivation cannot be bypassed.
- OD-9: both forms. `theStepsOfAMissionTypeAreThoseOfTheWinnersOnTheMergedView` is unchanged. A7 is pinned. There is one consumer test each for `stepsOf`, `ACTION_NOT_FOUND` and the start node.
- Node predicate: built only in `ClosureView` from the in-force states (ACTIVE and an activatable kind). Graph nodes are Eff plus the held steps of an in-force M, and `layerOverrides` follow the nodes.
- Orders: graph nodes, edges and overrides are explicitly sorted. Walk parent choice uses hops, then source URN, then (when, reason), so it does not depend on order.
- The edge-order-reversal test is effective.
- `InForceResolutionPropertyTest`: comparing by constant name keeps the same two-reason restriction and the same state/entry equality. It is not weakened. The edit is unlisted but has an `out-of-map:` line in 7758398f.

## Red-first table

All runs were in a temporary detached worktree, one Gradle run at a time. The worktree has been removed.

| Pair (red → green) | Test(s) run on the red commit | Observed on red |
|---|---|---|
| 72807824 → 7758398f (D4b) | `InForceResolutionTest` | 1 of 66 failed: `theReasonOfARequiredNotInForceEntryIsATypeOfExactlyTheTwoPruningReasons`, "but the following elements were unexpected" (assertion) |
| 5b4f2d3a → 4ceb46d9 (OD-9) | `StepHoldingTest`, `EdgeIndexTest` | 8 of 26 failed, all on assertions, e.g. `stepsOf` "Expecting empty but was: [Place[id-beta, action:M/s]]". The existing step case passed. |
| 0b40d5ef → 1b0ab787 (OD-6, packs) | `ClosureViewTest`, `InForceResolutionTest` | 4 of 77 failed on assertions: ACTIVE expected, INACTIVE given (×2); MISSION_TYPE_NOT_IN_FORCE expected, ENHANCED_TARGET_NOT_IN_FORCE given; INACTIVE expected, ACTIVE given (unheld target) |
| 90991919 → 1b0ab787 (OD-6, activations) | `EffectiveSetTest` | 2 of 28 failed on assertions: ACTIVE expected, INACTIVE given; MISSION_TYPE_NOT_IN_FORCE expected, ENHANCED_TARGET_NOT_IN_FORCE given |
| 654334dc (fragment) | n/a | The fragment is committed before the code, as required |
| 22b64214 → 41bac554 (T134-T137) | `GraphProjectionTest`, `ActionContextWalkTest`, `ClosureViewTest` | 37 of 48 failed. 36 were assertion failures against stubs. 1 (`theLayerOverridesAre…LosingPackOrder`) failed on a fixture `CharterRefusal`, fixed in green (see NIT). |
| 3aa72cce → 13d1c861 (suggestion candidates) | `ActionContextWalkTest` | 2 of 27 failed on assertions (`aSuggestsTargetOutOfForce…`, `severalSuggestsEdgesToOneTarget…`). The red set has no case for non-suggests relations, so the BLOCKER was not caught. |

No test was disabled or deleted. Existing assertions changed only by type (`Reason` to `PruningReason`) or by name comparison. No `@Disabled`, `@SuppressWarnings` or NOPMD was added, and no suppression or register file changed.

## Gate results (lane head fa3c2942, which has the same code as 13d1c861)

- `:packs:check`: BUILD SUCCESSFUL in 3m 3s. 4202 tests, 0 failed. 4 skipped, all pre-existing infra (`FileSystemPackSourcesTest`, `SymbolicLinksTest`). Checkstyle, including the register check, passed.
- `:activations:check`: BUILD SUCCESSFUL in 38s. 724 tests, 0 failed, 0 skipped.
- `:deployable:test --tests '*HexagonalArchitectureTest'`: BUILD SUCCESSFUL. The walks stay in `packs`, and activations is untouched apart from `EffectiveSetTest`.

## Ownership

Changed files: the owned files plus the listed shared edits:
- `ClosureView` and `EdgeIndex`
- `InForceStates` and `InForceResolution`
- `InForceResolutionTest`, `EdgeIndexTest`, `ClosureViewTest` and `EffectiveSetTest`
- `InForceResolutionPropertyTest`, which is unlisted, has a rationale and is not weakened

Each has an `out-of-map:` line. `GraphWalk` and `GraphWalkTest` are unchanged. Nothing was touched outside packs and activations apart from the fragment.

## Runner dispositions (cycle 1)
- BLOCKER (suggestion list admits non-suggests relations): fix, red test per relation (vocabulary, enhances, overrides, applies, scope).
- MAJOR (entry order): ADR-0018 D3 rules "sorted by full URN string"; entries sorted by URN alone. No new decision needed; cite D3 in the Javadoc.
- MINOR scope edges of the start step count as hard reach: fix with a test.
- MINOR ClosureViewTest suggestion case with real in-force states: add.
- MINOR cascade change has no ReachabilityTest case: add (out-of-map: ReachabilityTest, owned by WP05); relayed to WP29 for docs.
- MINOR Context lacks the start step's enhancers: add both-ways enhancers of the start step (phase 2 of the step-enhancements mission pins the enhancer lookup for action:M/s).
- MINOR rename `Suggested` to `SuggestionCandidate` before it is pinned.
- MINOR StepHolding/heldSteps quadratic: deferred with rationale (bounded by pack size; no hot path yet); recorded in the PR C closeout notes for #416's lookup cache.
- NIT commit message of 41bac554 overclaims "assertions unchanged": recorded here; the PR history is rebuilt at closeout with an accurate message.
- NIT BEST_PARENT_LAST naming: rename to match its order.
