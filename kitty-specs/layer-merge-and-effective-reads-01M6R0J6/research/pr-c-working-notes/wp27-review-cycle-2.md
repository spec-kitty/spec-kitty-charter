# WP27 review, cycle 2 (reviewer-renata, claude:opus:reviewer-renata)

Lane-y, fixes `f0ffc9fc..1cf230f5` (11 commits). `f0ffc9fc` itself only merges status and review-record files. Same profile and charter context as in cycle 1, and the same rules: review only, Gradle in the foreground, the temporary worktree removed.

## Verdict: APPROVE

All cycle 1 dispositions are met:
- The BLOCKER is fixed and pinned by a red test.
- Entries now follow ADR-0018 D3.
- Both pin-only tests fail when their behaviour is removed.
- The gates are green.

The deferred items (the `StepHolding` quadratic and the 41bac554 message) were deferred by the runner with rationale, and I accept that. Only NITs remain.

## Disposition check

| Cycle 1 finding | Disposition | Met? |
|---|---|---|
| BLOCKER: candidates from links that are not `suggests` | fix, plus a red test per relation | Yes. `ActionContextWalk.java:366` adds `link.relation() == RelationType.SUGGESTS`. A parameterized test covers VOCABULARY, ENHANCES, OVERRIDES, APPLIES, REFINES and INSTANTIATES, and a mixed `suggests` plus `overrides` case was added. |
| MAJOR: entry order | ADR-0018 D3, URN alone | Yes. `entries()` sorts by `Listed::urn`. `Urn.compareTo` is `toString().compareTo`, which is the "full URN string". The class, `Context` and `walk` Javadoc cite D3. |
| MINOR: the start step's scope edges as hard reach | fix with a test | Yes. `suggestions()` adds `start` to the sources of the hard mask, and the test is `anArtifactTheActionItselfScopesIsNeverACandidate…`. |
| MINOR: ClosureViewTest with real states | add | Yes. `aSuggestedArtifactNotSelectedIsASuggestionCandidate…` uses `view.inForce(...)` and a decoy `vocabulary` link. |
| MINOR: ReachabilityTest for the cascade change | add, out-of-map, relay to WP29 | Yes. The test has a positive control. The `out-of-map:` line is in c18cf02b. Relaying the register entry to WP29 is the orchestrator's job. |
| MINOR: the start step's enhancers | add both ways | Yes. `Context.actionEnhancedBy` and `actionEnhances` are in URN order, filtered by the predicate, never pulled into `entries`, and empty when M is out of force. |
| MINOR: rename `Suggested` | rename | Yes. It is now `SuggestionCandidate` and `suggestionCandidates()`. |
| MINOR: quadratic `StepHolding`/`heldSteps` | deferred with rationale | Accepted. |
| NIT: the 41bac554 message | recorded, rebuilt at closeout | Accepted. |
| NIT: `BEST_PARENT_LAST` | rename | Yes. It is now `EDGE_TIE_ORDER`. |

## Findings

- **[NIT] service/packs/src/main/java/ai/speckitty/charter/packs/application/ActionContextWalk.java:43-49, 248-256, 390**
  - Issue: the edited Javadoc lines were not reflowed. Several run to 121-145 characters, where the file otherwise wraps at 120. Checkstyle passes, because the limit does not apply to comments.
  - Required change: reflow them when the file is next touched.
- **[NIT] ActionContextWalkTest `onlyASuggestsLinkMakesACandidate…`**
  - Issue: the disposition lists `scope`, but the test covers APPLIES, REFINES and INSTANTIATES instead. REFINES and INSTANTIATES, like `scope`, are in the hard mask, so they pass at red. They guard the mask, not the fix.
  - Required change: none. The four relations outside the mask failed at red and carry the fix.

Point (2), the reorder in 57ac09a4: I compared the diff line by line. The seven reordered expectations hold the same tuples or strings, only in URN order. No assertion was removed, no `containsExactly` was relaxed, and the `.as(...)` texts are kept. One test was renamed to `theEntriesAreSortedByFullUrnStringWhateverStepListedThem`. `ClosureViewTest` is unchanged by the reorder.

Point (3), API to pin: `Context(action, missionTypeInForce, entries, suggestionCandidates, actionEnhancedBy, actionEnhances)` is sound to pin:
- Each list has a documented order: entries by URN (D3), candidates by URN, enhancers by URN.
- Each component is null-checked and copied.
- The names match `Entry.enhancedBy` and `Entry.enhances`.
- `SuggestionCandidate` cannot be confused with the entries listed by `Step.SUGGESTS`.

`Run.enhancersOf` and `Run.enhancedTargetsOf` went from private to package-private inside a private inner class, so no surface was widened.

Point (5), regressions: none found.
- No change outside `ActionContextWalk` and the four test files.
- `GraphProjection`, `ClosureView`, `EdgeIndex`, `StepHolding` and the OD-6 and D4b code are unchanged since cycle 1.
- The packs test count went from 4202 to 4215, with no new skips.
- No suppression and no `@Disabled`.

## Red-first table

Each run used a temporary detached worktree, one Gradle run at a time. The worktree has been removed.

| Pair (red → fix) | Test run on the red commit | Observed on red |
|---|---|---|
| 95f89cb4 → 056d90a5 (BLOCKER) | `ActionContextWalkTest` | 5 of 34 failed: APPLIES, VOCABULARY, ENHANCES and OVERRIDES each gave "Expecting empty but was: [SuggestionCandidate[urn=tactic:Out, via=tactic:T1, hops=2…]]". The mixed case failed with "some elements were not expected" (`tactic:O`). REFINES and INSTANTIATES passed, as expected because of the hard mask. |
| 583c42b0 → d955503d (start step scope) | `ActionContextWalkTest` | 1 of 35 failed: "Expecting empty but was: [SuggestionCandidate[urn=tactic:Scoped, via=tactic:T1, hops=2…]]" |
| ff64dbbb → 57ac09a4 (D3 order) | `ActionContextWalkTest` | 1 of 35 failed: index 0 expected "directive:R" but was "tactic:A", and so on (step order observed) |
| fcd45306 → 1cf230f5 (start-step enhancers) | `ActionContextWalkTest` | 1 of 38 failed: `theContextCarriesTheEnhancersInForceOfTheStartStep…`, "Expecting actual: [] …". The guard cases passed. |
| 6bf680a8 (pin only) | `ClosureViewTest` at 1cf230f5 with the `link.relation() == SUGGESTS` check removed | Fails: "Expected size: 1 but was: 2" |
| c18cf02b (pin only) | `ReachabilityTest` at 1cf230f5 with the held-step filter in `EdgeIndex.stepsOf` neutralised | Fails: "Expecting actual: … not to contain keys" (the orphan step and `directive:Orphan` were reached) |
| 82bdb725 (rename) | n/a | A pure refactor |

## Gate results (head 1cf230f5)

- `:packs:check`: BUILD SUCCESSFUL in 2m 44s. 4215 tests, 0 failed, 0 errors. 4 skipped, all pre-existing infra (`FileSystemPackSourcesTest`, `SymbolicLinksTest`). Checkstyle, including the register check, passed.
- `:activations:check`: BUILD SUCCESSFUL in 36s.
- `:deployable:test --tests '*HexagonalArchitectureTest'`: BUILD SUCCESSFUL.

The commits carry SSH signatures (`%G?` reports N only because no allowed-signers file is configured locally).
