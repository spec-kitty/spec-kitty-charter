# WP27 hand-off (claude:sonnet:java-jenny)

Worktree `.worktrees/layer-merge-and-effective-reads-01M6R0J6-lane-y`, branch `kitty/mission-layer-merge-and-effective-reads-01M6R0J6-lane-y`. 10 commits on top of the lane base, red and green separate, all signed. `git status` clean. Subtasks T134-T137, T159-T161 marked done. Nothing pushed, no move-task.

## Public names (the step-enhancements mission pins these)

`ai.speckitty.charter.packs.domain.StepHolding` (static, pure, no reads):
- `Optional<Urn> missionTypeOf(Urn step)`  (`action:M/s` -> `mission_type:M`; empty for anything else)
- `boolean heldByPack(Collection<String> definedArtifacts, Urn step)`  per pack: defines `mission_type:M` and declares `action:M/s`. Takes the bare-URN-text lists `PackCompositionFacts.defined()` and `EdgeIndex`'s `definedArtifacts()` both hold.
- `Set<Urn> stepsHeldByPack(Collection<String>)`  (URN order)
- `boolean heldByClosure(Collection<? extends Collection<String>> packs, Urn step)`  some pack holds it per pack
- `Set<Urn> stepsHeldByClosure(Collection<? extends Collection<String>>)`  (URN order)
- `Optional<Urn> soleActionTarget(Urn source, Collection<Urn> enhancesTargets)`  the OD-6 shape: STEP_ENHANCEMENT with exactly one distinct `enhances` target, an ACTION.

`packs.application.EdgeIndex` (package-private): `stepsOf(Urn)` now closure form; new `heldSteps()`, `holdsStep(Urn)`, `links()` + `record Link(source, relation, target, when, reason)`.

`packs.application.GraphProjection` (public record): `nodes: List<Urn>` (URN order), `edges: List<Edge(source, RelationType relation, target)>` (source, relation token, target; distinct), `layerOverrides: List<LayerOverride>` (artifact, losing level, losing pack). Factory `of(EdgeIndex merged, MergedClosure, Predicate<Urn> effective)` is package-private; callers use `ClosureView.mergedGraph`.

`packs.application.ActionContextWalk`: `public ActionContextWalk(EdgeIndex merged)`, `public Context walk(String missionType, String action, int depth, Predicate<Urn> inForce)`.
- `Context(Urn action, boolean missionTypeInForce, List<Entry> entries)`; M not in force -> `false` and no entries.
- `Entry(Urn urn, Step step, Urn via, int hops, Optional<String> when, Optional<String> reason, List<Urn> enhancedBy, List<Urn> enhances)`, `relation()` = step relation.
- `enum Step {SCOPE, REQUIRES, SUGGESTS, REQUIRES_OF_SUGGESTED, VOCABULARY}`; `relation()`: step 4 reports `requires`.
- Throws `CharterRefusal(MISSION_TYPE_NOT_FOUND | ACTION_NOT_FOUND)` judged on the merged closure (type: some winning place holds `mission_type:M`; action: `StepHolding` closure form), before and whether or not M is in force. `IllegalArgumentException` for depth < 0 (any depth >= 0 works; the contract caps it).
- Entries: step order, then URN. `via`/`hops` by ADR-0018 D3 (parent = minimum-step frontier, fewest hops, smallest URN). `when`/`reason` only on SUGGESTS entries.

`ClosureView` (public, instance): `GraphProjection mergedGraph(InForceStates states)`, `ActionContextWalk.Context actionContext(String missionType, String action, int depth, InForceStates states)`. The predicate is built inside from `states`: ACTIVE and an activatable kind (so no step, template, asset, guard or lifecycle). The walk starts at the held step when `mission_type:M` passes; graph nodes = Eff + held steps of an in-force M. `MergedView.resolutionEdges()` now derives the step-enhancer binding (see OD-6).

D4b: `InForceStates.PruningReason {MISSION_TYPE_NOT_IN_FORCE, ENHANCED_TARGET_NOT_IN_FORCE}`; `RequiredNotInForce(Urn urn, PruningReason reason)`.

## Red-first evidence (red commit -> green commit; failing assertion)
- D4b/T161: `72807824` -> `7758398f`. `InForceResolutionTest.theReasonOfARequiredNotInForceEntryIsATypeOfExactlyTheTwoPruningReasons`: "but the following elements were unexpected" (the other 8 Reason constants).
- OD-9/T159: `5b4f2d3a` -> `4ceb46d9` (red carries a stub StepHolding). 8 of 11 StepHoldingTest/EdgeIndexTest cases failed on assertions, e.g. `EdgeIndexTest.anActionDeclaredWithoutItsMissionTypeInAnyPackIsNotAStepOfThatMissionType` (stepsOf still included `action:M/two`); the A7 and layer-override cases pin behaviour and pass in both. `theStepsOfAMissionTypeAreThoseOfTheWinnersOnTheMergedView` untouched and green.
- OD-6/T160: `0b40d5ef` (packs) and `90991919` (activations) -> `1b0ab787`, with the fragment `654334dc` committed before the code. ClosureViewTest: ACTIVE expected/INACTIVE given; `MISSION_TYPE_NOT_IN_FORCE` expected/`ENHANCED_TARGET_NOT_IN_FORCE` given; unheld target INACTIVE expected/ACTIVE given (edge dropped). EffectiveSetTest (through `PacksAvailability.session`, real closure): same two messages. The shapes that keep E6 pass in both and pin it.
- T134-T137: `22b64214` -> `41bac554` (red has stub classes/accessors). 30 of 31 unit cases and 7 of 8 ClosureViewTest cases failed on assertions; the order-independence and one-read (NFR-007 counting) cases pass vacuously at red and bite at green. Fixture fix in the green commit: one GraphProjectionTest case had two sibling packs at one level (the merge refuses that); I dropped the extra pack, assertions unchanged.

## Final runs
`:packs:check` BUILD SUCCESSFUL (3m14s); `:activations:check` BUILD SUCCESSFUL; `:deployable:test --tests *HexagonalArchitectureTest` BUILD SUCCESSFUL; `:contracts:checkContractChangelogFragments` BUILD SUCCESSFUL. (One Maven Central 429 on deployable, retried.)

## Out-of-map edits (each is in its commit message)
ClosureView, EdgeIndex, InForceStates, InForceResolution (only the PruningReason conversion; the OD-6 change needed none: binding rides in `Edges.bindings`, the kept edge is read as built), InForceResolutionTest, EdgeIndexTest (new cases plus a `mergedClosure()` helper), ClosureViewTest (did not exist; created), EffectiveSetTest. Also `InForceResolutionPropertyTest` (packs): compares the narrowed reason by constant name; not in the shared list. `ClosureViewSession` needed NO change: it maps by `entry.reason().name()` and compiled unchanged.

## For WP28 (activations)
- `RequiredNotInForce.reason` mapping: add the own two-value enum and an exhaustive switch; the parity row can read the packs record component by reflection.
- The suggestion carrier: `EffectiveSet.suggestionsOf` attaches `{when, reason}` only to an out-of-force `not_selected` artifact an active one suggests. The walk's predicate is Eff (lists only in-force artifacts, per the WP), so an unselected suggested artifact is NOT an entry; a suggests entry is always in force and the carrier would not mark it. If the context must list unselected suggestions as conditional entries (FR-019, #408 ruling 3), the walk needs a second predicate for the suggests step; I did not add it. Please decide with the operator. The data (`when`, `reason`, `step == SUGGESTS`) is on every suggests entry.
- Walk entry -> wire: `via`/`hops`/`step` are extra data; map `enhancedBy` (and `enhances`) to refs with `ArtifactRef.of`; all entries are activatable kinds.
- Graph nodes include held steps (`action:` URNs), so `NodeRef`/`NodeKind` must carry `action`; edges can start at a step (`scope`). `layerOverrides` may name a step.
- Layer overrides: `LayerOverride.Side` carries the configured `packId` and level; order is fixed in `GraphProjection`, do not re-sort differently.
- `ClosureView.mergedGraph/actionContext` take the `InForceStates` the request already resolved (one resolution per request, NFR-003); the state comes from `ClosureView.inForce(request)`.
- Both throw the merge refusals `merged()` does.

## For WP29 (divergences/docs)
- E6 amended (OD-6) scope and the unheld-target rule; I11 holds. Fragment `contracts/charter/changes/2026-11-09-c-step-enhancer-binding.md`.
- OD-9: two forms. `Reachability` uses `stepsOf` (W20), so the cascade now follows only held steps (an action declared with no pack defining its mission type is no longer followed): a behaviour change to name in the register. A winning `mission_type:M` keeps its losers' unique steps (A7, pinned in `StepHoldingTest` and `EdgeIndexTest`); the architect's C17/C18 dissent stands.
- Kinds that are not activatable (template, asset, guard, lifecycle) are absent from graph and walk even when a lock puts them in force inside packs (pinned in `ClosureViewTest`).
- Strict per-pack: mission type in pack A and `action:M/s` declared in pack B alone is NOT a held step.
- Entry order is step order then URN; ADR-0018 D3 says "sorted by full URN string" - I read the WP's "ADR-0018 order" as step order first.
- `override_unstable` for a step enhancement that overrides its own mission type is not handled (deferred to ADR-0066 integrity check).
- `ReservedOperationCoverageTest`/contract/`CharterRefusalCode` untouched (WP28).

## Deviations
- No `GraphWalk` change and no `GraphWalkTest` change: the new reads do not use it (they walk `EdgeIndex.links()`); the listed owned file stays as is.
- The predicate is built from `InForceStates` (not a `Predicate<Urn>` argument) on `ClosureView`, per the node-predicate ruling; the unit-level walk and projection take a plain `Predicate<Urn>` as the plan's signature has it.
- `ClosureViewTest.java` was named in the WP but absent; created.

## Addendum: suggested, out of force (decision 01M7059T4K64TAVKXN3T1FKWYG)
Commits `3aa72cce` (red; two cases failed on assertions, control/hard/depth-0 cases guard the green) -> the feat commit after it. `:packs:check` and `:activations:check` green.
- `ActionContextWalk.Context(Urn action, boolean missionTypeInForce, List<Entry> entries, List<Suggested> suggested)` (a 4th component; the 3-arg constructor is gone).
- `ActionContextWalk.Suggested(Urn urn, Urn via, int hops, Optional<String> when, Optional<String> reason)`; `hops = hops(via)+1`.
- Candidates: targets of `suggests` edges followed in step 3 from in-force frontier nodes (scoped, and suggested ones when depth > 1) that the closure holds and the predicate refuses. Not entries, not expanded in step 4, no vocabulary in step 5. Dropped when any listed artifact reaches the target via requires, refines, scope or instantiates (packs' copy of the hard set = cascade relations minus suggests/enhances). Depth 0: none. Sorted by URN; several edges to one target: fewest hops of source, then smallest source URN, then smallest edge (when, reason). Documented on `walk`.
- Packs does not judge `not_selected`: WP28 passes each candidate through the existing `EffectiveSet` suggestion carrier (state/reason from the in-force states).

## Cycle 1 fixes (claude:sonnet:java-jenny)

Ten commits on top of f0ffc9fc, signed; `git status` clean; T134-T137, T159-T161 marked done again. Nothing pushed, no move-task. Order: rename, then (red -> fix) per finding.

| Finding | Commits | Failing assertion seen at red |
|---|---|---|
| BLOCKER, candidates only from `suggests` links | red `95f89cb4` -> fix `056d90a5` | `ActionContextWalkTest` parameterized by relation: "a VOCABULARY link to an out-of-force target is no suggestion / Expecting empty but was: [SuggestionCandidate[urn=tactic:Out ...]]" (also APPLIES, ENHANCES, OVERRIDES; REFINES and INSTANTIATES pass at red, the hard mask drops them); mixed case "to contain exactly [tactic:S] but some elements were not expected: [tactic:O]" |
| MAJOR, entry order (ADR-0018 D3, URN string alone) | red `ff64dbbb` -> fix `57ac09a4` | `theEntriesAreSortedByFullUrnStringWhateverStepListedThem`: expected `directive:R, glossary_pack:G, tactic:A...` but was `tactic:A, tactic:B, directive:R...`. Seven other tests had their expected sequence reordered to URN order (listed in the fix commit message); no assertion dropped; ClosureViewTest unchanged. Javadoc of class, Context and walk cites D3 |
| MINOR, start step's `scope` edges count as hard reach | red `583c42b0` -> fix `d955503d` | "Expecting empty but was: [SuggestionCandidate[urn=tactic:Scoped, via=tactic:T1, hops=2 ...]]" |
| MINOR, ClosureViewTest real in-force case | `6bf680a8` (test only) | Pins; passes on fixed code. Bites on pre-fix code (restored `ActionContextWalk` from 82bdb725 locally): "Expected size: 1 but was: 2" |
| MINOR, ReachabilityTest cascade case | `c18cf02b` (test only; out-of-map: ReachabilityTest, WP05) | Pins; positive control (held step and its requires reached). With the held-step filter in `EdgeIndex.stepsOf` neutralised locally it fails: orphan `action:m/orphan` and `directive:Orphan` reached. Register entry for WP29 stays relayed by the orchestrator |
| MINOR, start-step enhancers | red `fcd45306` (stub) -> feat `1cf230f5` | "Expecting actual: [] to contain exactly (and in same order): [skill:E1, skill:E2]"; the no-enhancer and mission-type-not-in-force cases pass at red as guards |
| MINOR/NIT, renames | `82bdb725` (refactor, tests green before and after) | n/a |

Deferred as dispositioned: the `StepHolding`/`heldSteps` quadratic MINOR; the 41bac554 message NIT (record only).

## Public signatures after cycle 1

- `ActionContextWalk.SuggestionCandidate(Urn urn, Urn via, int hops, Optional<String> when, Optional<String> reason)` (was `Suggested`).
- `ActionContextWalk.Context(Urn action, boolean missionTypeInForce, List<Entry> entries, List<SuggestionCandidate> suggestionCandidates, List<Urn> actionEnhancedBy, List<Urn> actionEnhances)`. `suggestionCandidates()` replaces `suggested()`. `actionEnhancedBy`: enhancers in force of `action:M/a`; `actionEnhances`: artifacts in force it enhances; both in URN order, empty when the mission type is not in force, never pulled into `entries`.
- Entries: sorted by full URN string (D3); `via`/`hops`/`step` unchanged. Candidates: only targets of links whose own relation is `suggests`; not reported when the start step or a listed artifact reaches the target through requires, refines, scope or instantiates.
- Private: `BEST_PARENT_LAST` is now `EDGE_TIE_ORDER`.
- For WP28: map `actionEnhancedBy`/`actionEnhances` to refs like the entry enhancers; entry order on the wire is URN order.

## Gates (final head 1cf230f5)

`:packs:check :activations:check` BUILD SUCCESSFUL (3m26s, Checkstyle included), `:deployable:test --tests '*HexagonalArchitectureTest'` BUILD SUCCESSFUL. spotlessApply clean, no suppressions, no skipped or disabled tests, no assertion weakened.
