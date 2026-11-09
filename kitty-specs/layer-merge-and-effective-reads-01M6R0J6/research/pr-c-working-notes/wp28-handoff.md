# WP28 hand-off (claude:sonnet:java-jenny)

Worktree `.worktrees/layer-merge-and-effective-reads-01M6R0J6-lane-z`, branch `kitty/mission-layer-merge-and-effective-reads-01M6R0J6-lane-z`.
Eight commits on top of `271b06f4`, all signed, `git status` clean. T138-T142 and T162-T164 marked done. Nothing pushed, no move-task.

## What was built

### Contract
- `NodeReference {kind: NodeKind, id}` (closed; `id` `^[A-Za-z0-9][A-Za-z0-9_./-]*$`, 1..256). The description states the reuse rule
  (`ArtifactReference` for an activatable artifact, `NodeReference` for a node that may not be activatable, `Urn` for a graph identity).
  `NodeKind.yaml` now says it lists the authored kinds only (no guard, no lifecycle), so no carrier of `NodeReference` can name one.
- `EnhancerWithoutTarget.targets`: items `NodeReference`, `minItems: 1`. Fragments `2026-11-09-c-node-reference-step-targets.md`
  (`### Breaking`: names the read, the context, the five writes that answer `ActivationChange.skipped`, the `artifactId` to `id` rename,
  the widened `kind`) and `2026-11-09-c-node-reference-added.md` (`### Added`).
- `LayerOverride {artifact: Urn, winner, loser}` and `LayerOverrideSide {packId, level}` (new, `### Added`).
  `EffectiveGraph.layerOverrides` required (`### Breaking`).
- `EffectiveActionContext`: description, `artifacts` description, and ONE new required member `enhancements: [ActionEnhancement]`
  (`ActionEnhancement {target: NodeReference, enhancedBy: [ArtifactReference] minItems 1}`); `skipped` was already `SkippedReport`.
  Why this member and no other (the "minimal, say why" call): rule E12 reads enhancers from the enhanced node's side and US5 AS4 says
  "with enhancements that are active". `EffectiveArtifact` is shared with the list, so a per-artifact `enhancedBy` there would change a
  served read; and a step enhancer enhances the step, which no artifact entry names. One list keyed by `NodeReference` (the step
  itself, or a listed artifact) carries both and is the one natural reuse of `NodeReference`. The walk's `actionEnhances` (the
  artifacts a step enhances) is not on the wire; the graph shows those edges.
- Fragments `2026-11-07-c-graph-and-context.md` (`### Breaking`) and `2026-11-07-c-graph-and-context-added.md` (`### Added`).
- Both paths lose `x-reserved` and `501`; `action_not_found` is `raised: true` (two sentences and one flag in
  `CharterRefusalCode.yaml`, nothing else, to stay clear of the phase 1 edits). `SkippedReport.yaml` descriptions (D16), examples
  (`EffectiveGraph.one-edge` + new `EffectiveGraph.layer-override`, `EffectiveActionContext.implement`, `ActivationSkippedReport.each-bucket`
  with a step target). `ActivationSkippedReport.nothing-skipped` and `ActivationChange.selected` needed no change (empty lists).
- Lint fixtures needed no change (they are standalone; `lintFixtures` and `lintContract` pass).

### Java
- `activations.domain.NodeRef(NodeKind kind, String id)` (authored kinds only; `of(Urn)` is empty for a guard or lifecycle; ordered by
  kind token, id). `EffectiveSkippedReport.EnhancerWithoutTarget.targets` and `InForceAnswer.enhancedTargets` hold `NodeRef`.
- `EffectiveSkippedReport.PruningReason {MISSION_TYPE_NOT_IN_FORCE, ENHANCED_TARGET_NOT_IN_FORCE}` behind `RequiredNotInForce.reason`;
  `ClosureViewSession.pruningReason` maps the packs enum by an exhaustive `switch`.
- Port: `PacksSession.graph(InForceQuery)` and `actionContext(mt, action, depth, InForceQuery)`; values `GraphSnapshot`, `ActionContext`
  (`Reached`, `SuggestionCandidate`), `EffectiveContext` (+ `Enhancement`). `MergedSnapshot.LayerOverride.ref` is a `NodeRef`.
  `ClosureViewSession` keeps the last request's `InForceStates`, so the set, the graph and the walk resolve once per request.
- Service: `EffectiveReadService.graph(id, ifNoneMatch)` and `actionContext(id, EffectiveTag.ContextQuery, ifNoneMatch)`; `GraphRead`,
  `ContextRead`. 304 before any read; the walk is asked before the set, so an unknown name reads nothing more.
- Tags: `EffectiveTag.graph(revision, closure)` and `EffectiveTag.context(revision, closure, ContextQuery(missionType, action, depth))`,
  each its own `Read` in the preimage.
- Suggestions: `EffectiveSet.suggested(ref, when, reason)` is the one carrier (the `not_selected` predicate is the same private method
  the list's `suggestionsOf` uses); it takes the walk's edge. `EffectiveContext.of` lists entries + suggestions in URN-string order.
- Controller: marks and `notBuilt` removed; `getEffectiveActionContext` / `getEffectiveGraph` answer through `verified(...)`; the
  context's `self` carries `?depth=` only when the request named one.
- `EffectiveSkippedReport.withMissionTypeNotInForce`: in an action context the `missionTypeNotInForce` bucket is the mission type itself
  when it is not in force and empty otherwise (the schema description already said so); every other bucket is the activation's own.

## Red-first evidence (red -> green)
| Part | Red | Green | Failing assertion seen |
|---|---|---|---|
| Contract (fragment before code) | - | `1091a144`, `c560d6c7` | contract-first: `ReservedOperationCoverageTest` (2 cases), `ReservedRefusalCodesTest`, `OperationCoverageTest`, `LinkChecksTest` failed from `c560d6c7` on |
| T139 scenarios (11 failed, none undefined) | `4adf1ea1` | `700efb3a` | `expected: 200 but was: 501` (US5 AS1, AS2 x2, AS3, AS4, AS5 x2, NodeReference scenario (its context read), FR-020, FR-021 x2) |
| T164 parity row | `ffb5665b` | `ff6f5d0d` | `RequiredNotInForce.reason accepts selected` (`IllegalArgumentException: Unexpected value 'selected'`) |
| T163 `ClosureViewSessionStepRefsTest` | `91f19c21` (after the type-only refactor `0c147d9e`) | `700efb3a` | 5 of 18 fail: step target dropped (`[NodeRef[DIRECTIVE D]]` vs `[NodeRef[ACTION M/s], ...]`), the real resolution of a step enhancement of an unheld step gives `[]`, three graph cases empty. The other 13 (one per `activatable(` call site, D16 drop, policy and preset schemas) pin and pass |
| T138/T140/T141 units | `91f19c21` | `700efb3a` | 34 of 781 activations tests failed: `EffectiveContextTest` 5/5, `EffectiveSetTest.suggested` 3/4, `EffectiveReadServiceTest` 8, `EffectiveReadMapperTest` 3, `EffectiveReadHttpTest` 8 (501), `EffectiveTagTest` 1, `ActivationControllerTest` 1 |
| T142 | `91f19c21` | `700efb3a` | `ReservedOperationCoverageTest.noScenarioPinsAnOperationAs501AndNoPinFileRemains` fails on the two pins of `activations.feature` |

A test written red caught a real defect in my first green: `Enhancement.enhancedBy` relied on the walk's order (`EffectiveContextTest`
gave `[e2, e1]`); it now sorts.

## Gates
`spotlessApply` clean; `:contracts:check`, `:activations:check`, `:packs:check`, `:deployable:check` (test: 1201 pass, the full
contract suite and the R-20 coverage among them), `:client-java:test` all BUILD SUCCESSFUL. `HexagonalArchitectureTest` green. No
suppression, no `@Disabled`, no `@WIP`. No Maven Central 429 hit.

## Out-of-map edits (each is in its commit message)
Contract: `NodeKind.yaml` (description), `SkippedReport.yaml`, `EnhancerWithoutTarget.yaml`, `CharterRefusalCode.yaml`, the three examples,
`scenarios/errors.feature` (action_not_found row and wording), `scenarios/README.md`, `scenarios/activations.feature` (removed, it held
only the two pins). Java main: `EffectiveReadService`, `ActivationController`, `PacksSession`, `PacksAvailability` (its `Unresolved`
session), `ClosureViewSession`, `EffectiveReadMapper`, `EffectiveSet`, `EffectiveSkippedReport`, `MergedSnapshot`, `EffectiveTag`.
Tests: `EffectiveReadMapperTest`, `EffectiveSetTest`, `EffectiveReadServiceTest`, `EffectiveReadHttpTest`, `ActivationControllerTest`,
`EffectiveTagTest`, `FakeAvailablePacks`, `PacksAvailabilityTest`, `ActivationWireTokenParityTest`, packs `WireTokenParityTest`,
`ActivationSteps` (the ids step reads `id` when an item has one), `ActivationTestWiring`, `ReservedOperationCoverageTest`,
`ReservedRefusalCodesTest`, `OperationCoverageTest`, `LinkChecksTest`, `ContractDescriptionsTest`, `HexagonalArchitectureTest`.
`ReservedActivationOperationTest` is replaced by `ReservedInterfaceOverrideTest` (same four properties on a planted interface and
controller: with no reserved operation left there is nothing real to route).

## Deviations, with reasons
- `CascadeDepthRoundTripTest` (listed) needed no change: it carries only empty lists. `activations.feature` is removed, not edited: after
  the last two pins went it held nothing (as the pack-registration pin file did).
- The #429 scenario in `effective-graph-and-context.feature` pins the `{kind, id}` rename and the context's bucket, not a step target:
  no pack file can author a step enhancement before #424 loads them, and a tactic that `enhances` an `action:` target is in force
  (E6 skips action targets for every kind but `step_enhancement`). The step target is pinned in `ClosureViewSessionStepRefsTest`
  from the real `ClosureView` resolution of a step enhancement of an unheld step.
- A held step is declared by a graph document's `nodes` entry (`kind: action`), not by the mission type file's `steps` alone; the first
  green run showed it and the fixtures declare the node. No assertion changed.
- `WireTokenParityTest` row for `EffectiveGraphEdge.relation` leaves out `implemented_by`: a guard's derived edge starts at a guard,
  which is no node of the graph, and the contract's `Relation` has no such value.
- `MergedSnapshot.layerOverrides` (the merged snapshot, activatable winners only) still lists no step; the graph's overrides come from
  `GraphProjection`, which includes held steps. `ClosureViewSessionStepRefsTest` pins both.
- Context `skipped` is the activation's whole report with `missionTypeNotInForce` replaced as above (the contract description said
  "the mission type itself"). A client wanting the other mission-type-bound artifacts reads `getActivationSkippedReport`.
- `depth` follows the contract (0 or 1); ADR-0018 D4 allows 2 and the plan's open note stands.
- A forged matching `If-None-Match` for a name that does not exist would answer 304 (the tag is a hash of revision, closure and the
  query, so only the real 200 gives it); not worth the merge it would cost, noted.

## For WP29 (docs)
- Divergence entries to write: (1) D16: `requiredNotInForce` holds activatable kinds only, because the policy and preset schemas exclude
  `action`; E14 and C31 get the sentence; cite `ClosureViewSessionStepRefsTest` (`aRequiredStepThatIsNotInForceIsDroppedAndARequiredArtifactIsKept`,
  `thePolicyAndPresetSchemasCannotNameAStepSoTheFloorHoldsNone`). (2) Kinds absent from the effective reads: template, asset, guard,
  lifecycle (no graph node, no listing, no layer override), and `layerOverrides` lists only nodes of the graph: artifacts in force
  and held steps of a mission type in force (decisions `01M702A71YF7WNDTG2AA89K8ZA`, `01M7037ZNKJ6JTRKGD7G695ZV1`); the scenario
  "the override of a held step is listed..." cites it. (3) The context's `enhancements` member (E12, E16) and the suggestion rule
  (`DM-01M7059T4K64TAVKXN3T1FKWYG`, `DM-01M6Y6VE6VZQ4MXD98N93JB3GJ`): a suggestion is `not_selected` and reached only through
  `suggests` from a listed artifact, carries the nearest edge's `when`/`reason`, is not walked through; entries are in URN-string order
  (ADR-0018 D3). (4) The `missionTypeNotInForce` of a context is the mission type itself.
- `NodeReference` reuse rule: it is in the schema description and the Added fragment; the step-enhancements mission reuses
  `NodeReference` and `activations.domain.NodeRef`, never a second copy. `ArtifactReference` stays activatable-only.
- From WP27 (unchanged by me, still to register): the cascade now follows only held steps (OD-9), the E6 amendment (OD-6,
  `2026-11-09-c-step-enhancer-binding.md`), A7 kept, and the strict per-pack form.
- Fragments to cite: `2026-11-09-c-node-reference-step-targets.md` (Breaking), `2026-11-09-c-node-reference-added.md`,
  `2026-11-07-c-graph-and-context.md` (Breaking), `2026-11-07-c-graph-and-context-added.md`, `2026-11-09-c-step-enhancer-binding.md`
  (Changed, WP27).
- Docs that still say "reserved" / `501` for the two reads: `docs/api/service-contract.md` rows, `offering-and-activation.md`, E15/E16
  ("Ruled, not built" / "Planned, not built") and E12's "Partly built", the `Links.yaml` prose is unaffected. `ReservedOperationCoverageTest`
  now counts 0 (its control is a planted contract), and no scenario pins a 501.
- The `HexagonalArchitectureTest` rule `RESERVED_METHOD_READS_NOTHING` now allows an empty selection (as its type-level sibling does).
