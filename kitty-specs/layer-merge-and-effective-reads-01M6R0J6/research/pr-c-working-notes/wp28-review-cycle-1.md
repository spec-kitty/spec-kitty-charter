# WP28 review, cycle 1 (claude:opus:reviewer-renata)

Mission `layer-merge-and-effective-reads-01M6R0J6`, lane-z head `37409201` (WP28 code `1091a144^..700efb3a`, then the
planning commit `33718d54` for decision `01M70C42KC78KB9H6KB7TK9224`).

## Verdict: APPROVE

No blocker or major finding. Four MINOR and three NIT findings are below. The implementer can take them in a
follow-up or in WP29. None of them changes the wire or weakens a guard.

## What I applied

- Profile `reviewer-renata`: review, flag, assess; no fixes. Directives 001, 024, 030, 032, 041, 051; tactics
  code-review-incremental, delete-the-assertion-not-the-test, test-scaffolding-as-design-smell.
- `spec-kitty charter context --action review`: DIRECTIVE_010 (spec fidelity), QA_003_NO_FAKE_GREEN,
  NO_FULL_HEAVY_SUITES_IN_MISSION (module tasks only), RECONCILE_CHANGE_SCOPE_TENSIONS, PROJECT_001/002, the
  BDD/SbE/DDD/hexagonal paradigms. The context reported that the `internal` and `qa` pack paths are missing. This is
  an environment gap and does not affect this review.
- Sources I checked behaviour against: the WP file, plan IC-11 and "PR C scope added by operator rulings", contract-delta "PR C",
  spec FR-015, FR-017 to FR-021 and US5 AS1 to AS5, rules E12 and E14 to E17, the four named decisions, the dispositions and
  both hand-offs.

## Check 1: behaviour against the WP, the rulings and the rule ids

| Item | Result |
|---|---|
| `NodeReference` shape: closed `{kind: NodeKind, id}`, id pattern admits `/`, the reuse rule in the description, `EnhancerWithoutTarget.targets` `minItems: 1` | OK (`NodeReference.yaml`, `EnhancerWithoutTarget.yaml`). The Java side is `activations.domain.NodeRef`, which refuses guard and lifecycle. `NodeKind.yaml` states that it holds authored kinds only. |
| Step targets carried, D4(a) | OK. `ClosureViewSession.nodesOf` carries targets. 18 cases in `ClosureViewSessionStepRefsTest` cover each `activatable(` call site at `5458e1fa` (states, satisfiedBy, overriddenBy, deny, override-not-applied, staleIds and requiredNotHeld, requiredNotInForce, enhancer targets, enhancer key, winners, languages, edges from and to a step, merged overrides, graph). The production-shaped source is the real resolution of a step enhancement of an unheld step. |
| D16 pinned by its reason | OK. Schema parity over `policy`/`preset` JSON has a positive control (`contains("directive", ...)`). A fed `InForceStates` shows the explicit drop. |
| D4b | OK. `EffectiveSkippedReport.PruningReason` (two values). The exhaustive `switch` lives in `ClosureViewSession.pruningReason`. The parity row reads the record component's enum by reflection, and `PRUNING_REASONS` is gone. |
| `LayerOverride` | OK. A held step's override is listed. Template, asset, guard, lifecycle and out-of-force overrides are not, each with a positive control in the same answer (unit test and US5 AS2 scenario). |
| Graph and context served, 304, per-representation tag | OK. `EffectiveTag.graph`/`context` each name their own `Read`, and the context tag covers missionType, action and depth. A test shows the tags differ and that a closure change moves both. |
| Mission type not in force | OK. The answer is `artifacts: []`, `enhancements: []` and `missionTypeNotInForce = [mt]` (E16). The schema description already said "the mission type itself". |
| Unknown names give 404 | OK. Judged in packs against the merged closure, whether or not the mission type is in force. The scenario covers `missing/implement`, `software-dev/missing` and `research/missing` (not in force). The walk is asked before the set (`merges() == 0` on refusal). |
| Suggestions through the existing carrier (`01M7059T...`) | OK, with MINOR-2 below. `EffectiveSet.suggested` reuses `Suggestion` and the one `notSelected(Reason)` predicate, and has no second rule. |
| `enhancements` member (`01M70C42...`) | OK. One required `[ActionEnhancement {target: NodeReference, enhancedBy minItems 1}]` covers the step and the listed in-force artifacts. Suggestions are excluded. |
| One resolution per request | OK in code (`ClosureViewSession.statesOf` caches by `InForceRequest` equality, a record). Only the fake proves it: MINOR-1. |
| `ReservedOperationCoverageTest` at 0 with a control | OK. `noOperationOfTheContractIsReserved` passes. Planted-contract controls cover: reserved with no pin file, unpinned op, body not pinned before binding, a pin of a built op, and a fully pinned set that is clean. |
| `ReservedRefusalCodesTest` | OK. `ACTION_NOT_FOUND` is in the raised set and not in the unraised set (two hunks). |

## Check 2: contract hygiene

- Fragments: four files under `contracts/charter/changes/`, one `###` block each. `### Breaking` covers `layerOverrides`,
  `enhancements`, the context's `skipped`, the `x-reserved`/501 removal and the `NodeReference` change. The last names every
  `SkippedReport` carrier, including `ActivationChange.skipped` of the five writes, the `artifactId` to `id` rename and the
  `ActivatableKind` to `NodeKind` widening. `### Added` covers `NodeReference`, `LayerOverride`, `LayerOverrideSide`,
  `ActionEnhancement` and the two operations. `checkContractChangelogFragments` is green.
- Examples: `ActivationSkippedReport.each-bucket` (step target), `EffectiveGraph.one-edge` (+`layerOverrides: []`), the new
  `EffectiveGraph.layer-override` and `EffectiveActionContext.implement` (a suggestion and a step enhancement) all
  validate under `:contracts:check`.
- `CharterRefusalCode.yaml`: three small hunks (two sentences and `raised: true`). `ReservedRefusalCodesTest`: two hunks. Both are minimal.
- Generated client and client-java: `:client-java:test` is green (19 tests). `CascadeDepthRoundTripTest` carries only empty lists, so
  leaving it unchanged is correct.

## Check 4: deletions and replacements

- `activations.feature` deleted: it held only the two `@reserved` 501 pins (I read the file at `271b06f4`). The coverage
  test now also fails on any `@reserved` tag or 501 step left in any feature. Coverage is not weakened.
- `ReservedActivationOperationTest` was replaced by `ReservedInterfaceOverrideTest`. The same four properties are shown on a planted interface
  and controller, and the 501-vs-400 pair with and without the interceptor still bites. What is lost is the proof on the
  real generated `ActivationApi`, but no real reserved operation is left to route. I accept this.
- `RESERVED_METHOD_READS_NOTHING.allowEmptyShould(true)`: the bad-fixture control
  `theReservedMethodRuleFailsOnAMarkedMethodThatReachesAUseCase` still runs the unchanged rule and bites, so the guard holds.
- Scenario fixtures declare the held step as a graph `nodes` entry (`kind: action`). This matches production: open-packs
  declare steps in `packs/built-in/action.graph.yaml`. No assertion changed between red `4adf1ea1` and green `700efb3a`. I checked the diff.

## Findings

- **[MINOR] MINOR-1** `service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSession.java:103` -
  The real once-per-request resolution (`statesOf` cache) is untested. `EffectiveReadServiceTest.theContextAsksTheSessionOnce...`
  counts calls on `FakeAvailablePacks`, so it passes whether or not the adapter caches. Removing the cache leaves every test green.
  Required change: add a `ClosureViewSession`/`PacksAvailability` test. It should ask `actionContext` (or `graph`) and then `inForce` with an equal query,
  assert one resolution (for example through a counting `ClosureView` input, or through the `MergeInputs` read count the WP27
  NFR-007 test uses), and include the control that a different query resolves again.
- **[MINOR] MINOR-2** `service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveSet.java:265-290` - The Javadoc says
  `suggested` applies "the rule `suggestionsOf` applies to the list". Only the `not_selected` half is shared. The
  "not reached through a hard relation" half differs in scope:
  - the list drops a suggestion when any artifact in force reaches the target through a hard relation;
  - the walk drops it only when the start step or a listed artifact does.

  So a `not_selected` artifact suggested by a listed artifact and also required by an in-force artifact outside the context is a
  suggestion in the context and not in the list. Decision `01M6Y6VE...` says "one definition for the list and, in PR C, the
  action context". Required change: either pin the context-local reading with a test and a sentence in
  `EffectiveActionContext.yaml` and the Javadoc, and relay it to WP29 as a divergence note, or ask the operator to align the two.
- **[MINOR] MINOR-3** commit messages of `1091a144`, `c560d6c7`, `0c147d9e`, `91f19c21` and `700efb3a` - These changed files are outside
  `owned_files`/`create_intent` and the shared list, and have no `out-of-map:` line:
  - `ActionEnhancement.yaml`
  - `_index.yaml`
  - `2026-11-07-c-graph-and-context-added.md`
  - `2026-11-09-c-node-reference-added.md`
  - `EffectiveGraph.layer-override.yaml`
  - `EffectiveGraph.one-edge.yaml`
  - `EffectiveContext.java`
  - `GraphSnapshot.java`
  - `ActionContext.java`
  - `NodeRef.java`/`NodeRefTest.java`
  - `EffectiveContextTest.java`
  - `EffectiveTag.java`/`EffectiveTagTest.java`
  - `LayerOverrideSide.yaml`

  Most are new files the WP clearly implies. Required change: record the rationale for each, either in a short follow-up commit
  message or in the hand-off's out-of-map list, so the lane's ownership check can see it. History does not need rewriting.
- **[MINOR] MINOR-4** `service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java:289` - 304
  is decided before the name check. A client that computes the tag (the preimage parts are public: revision and closure
  fingerprint) gets `304` for an unknown action instead of `404`. The implementer noted this. Required change: none in code if
  accepted. Record it for WP29 (E17/E16 note), or check the names cheaply before the tag.
- **[NIT]** `service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java:341-343` and
  `ClosureViewSessionStepRefsTest.java:276-278` - Comment lines are broken mid-sentence ("one" / "that is marked", "pack" /
  "holds"), probably by a formatter. Reflow them.
- **[NIT]** `service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveTag.java:17` - A 225-character Javadoc line.
  There is no Checkstyle rule against it, but it breaks the file's wrapping. Rewrap it.
- **[NIT]** `EffectiveReadMapper.contextLink`: `?depth=0` and an absent depth share one ETag but differ in `self`. This follows the PR B
  `pageSize` precedent. Record it only.

## Check 5: fakeability and order

- The new tests pair each absence with a positive control in the same answer. I found no test that passes without the behaviour,
  except the one in MINOR-1.
- Order: graph nodes and edges keep the `GraphProjection` order (URN; source, relation, target). The context artifacts are in URN string order,
  enhancements in `NodeRef` order (kind token, id), and `enhancedBy` is sorted in the record whatever the walk gives. Each order is stated
  in the contract and tested (`theContextListsTheEntriesAndTheSuggestionsInUrnOrderWhateverOrderTheWalkGaveThem`).

## Check 6: ownership and architecture

- Shared edits listed in the WP all carry `out-of-map:` lines. For the unlisted ones, see MINOR-3.
- `HexagonalArchitectureTest` is green. `activations` names no packs type outside `infra.packs`, and `PruningReason` is the slice's own type.
- There is no `@SuppressWarnings`, `@Disabled`, `@WIP`, NOPMD or Checkstyle suppression in the added lines, and the suppression register is unchanged.
  Checkstyle is green in the `check` tasks.

## Check 3: red first (temporary worktree `/tmp/claude-0/review-wt-z`, now removed)

| Red commit | Targeted run | Failed for the right reason? | Green |
|---|---|---|---|
| `1091a144`, `c560d6c7` (contract first) | not rerun; the hand-off says coverage and description tests fail from `c560d6c7` on | n/a (contract-first order holds: fragments and schemas land before the code) | `700efb3a` |
| `4adf1ea1` scenarios | `:deployable:test --tests '*CharterContractSuite*'` | Yes. All 11 new scenarios fail on `expected: 200/404 but was: 501`, and none is undefined or pending. The R-20 coverage and `errors.feature` raised-codes check also fail, as expected, plus the 2 old `activations.feature` pins (x-reserved removed) | `700efb3a` |
| `ffb5665b` parity row | `:activations:test --tests '*ActivationWireTokenParityTest*'` | Yes. `RequiredNotInForce.reason accepts selected` fails with `IllegalArgumentException: Unexpected value 'selected'`; 1 of 14 failed | `ff6f5d0d` |
| `91f19c21` units | `:activations:test` | Yes. 34 of 781 fail on assertions, matching the hand-off exactly: EffectiveReadHttpTest 8 (501), EffectiveReadServiceTest 8, EffectiveContextTest 5, ClosureViewSessionStepRefsTest 5 (step target dropped, real resolution `[]`, three graph cases empty), EffectiveSetTest 3, EffectiveReadMapperTest 3, EffectiveTagTest 1, ActivationControllerTest 1 (`getEffectiveGraph is marked reserved`) | `700efb3a` |
| `91f19c21` `ReservedOperationCoverageTest` | hand-off evidence (fails on the two pins of `activations.feature`). I confirmed that those pins existed at red | Yes | `700efb3a` |

The 13 `ClosureViewSessionStepRefsTest` cases that pass at red state the answer at each call site, as the WP allows ("a case
stating its answer"). They are not red tests, and the hand-off says so.

## Check 7: gates on the lane head `37409201`

`JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=/home/user/open-packs :contracts:check :activations:check :packs:check :deployable:check :client-java:test`

**BUILD SUCCESSFUL in 1m 34s**: 89 tasks, 11 executed, the rest up to date. The up-to-date tasks had matching inputs: the
planning commit changes no input.

| Module | Tests | Failed | Skipped |
|---|---|---|---|
| activations | 781 | 0 | 0 |
| deployable (ran) | 1201 | 0 | 0 |
| packs | 4221 | 0 | 4 (existing filesystem/symlink skips: `FileSystemPackSourcesTest`, `SymbolicLinksTest`) |
| client-java | 19 | 0 | 0 |
| contracts | verifyOasdiff, verifyVacuum, changelog fragments, compatibility: pass | | |

Log: `/tmp/claude-0/wp28-gate.log`.

## Runner dispositions (cycle 1)
The verdict was APPROVE with minors; the runner folds the behaviour minors before approval (cycle 2).
- MINOR-1 (once-per-request resolution only checked on the fake): fix: an adapter-level counting test over ClosureViewSession with a control that fails when the cache in statesOf is removed.
- MINOR-2 (context-local vs global "only through suggests"): ruled by decision 01M70CTZP7P2EHSCK2G0RET74B: a context suggestion is an artifact the listing gives a suggestion and that a listed context entry reaches through suggests. Fix with a red test (an artifact suggested by a context entry but hard-reached by another in-force artifact outside the context is not a suggestion in the context).
- MINOR-3 (15 changed files without an out-of-map line): accepted as a record. New files created by the WP's own subtasks (NodeRef, PruningReason, ActionEnhancement, LayerOverride/LayerOverrideSide, the Added fragments, examples, _index.yaml) are inside its scope (T138, T162, T164); EffectiveTag carries the per-representation variants (T140, T141). The PR C history is rebuilt at closeout and its WP28 commit message lists every shared file with its rationale.
- MINOR-4 (304 for an unknown name): fix: judge the mission type and action names before the conditional check, so an unknown name answers 404 whatever If-None-Match says; red test first.
- NITs (broken comment lines, 225-char Javadoc line): fix. depth=0 sharing a tag with an absent depth: kept (the PR B pageSize precedent: the normalized query is part of the tag, and an absent depth means 0).
