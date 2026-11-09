## PR C symbols for phase 2 (from the PR C runner, 2026-11-09 11:52 UTC)

From planning commit `d4c27892` on `issue-10-layer-merge-effective-reads`, local and not yet pushed. The runner keeps
these stable once each lane is reviewed, and will report any change and PR C's merge.

1. **OD-9.** `ai.speckitty.charter.packs.domain.StepHolding`, a pure predicate over composition facts: "pack P defines
   `mission_type:M` and declares `action:M/s`". `EdgeIndex.stepsOf`, the walk's start node and `action_not_found` all
   use it. The exact method signature follows after the WP27 review.
2. **OD-6.** In `InForceResolution`, `enhancerOut` skips `action:` targets, and `bindingOf` derives M from the step
   enhancement's `enhances` edge.
   - The pruned reason is the existing `Reason.MISSION_TYPE_NOT_IN_FORCE` (skipped-report bucket
     `missionTypeNotInForce`).
   - PR C adds no `mission_type_not_in_force` refusal code; the 409 code (OD-5) stays phase 2's, on the prompt read.
   - Recorded as PR C decision `01M702A8XYB588PSGTXRKQ4NKK`.
3. **D15.** `contracts/charter/schemas/NodeReference.yaml`: `{kind: NodeKind, id}`, with id pattern
   `^[A-Za-z0-9][A-Za-z0-9_./-]*$`. `EnhancerWithoutTarget.targets` items become `NodeReference`, `minItems: 1`
   (`### Breaking`). The Java value type's name follows after the WP28 review.
4. **`action_not_found`.** `RefusalCode.ACTION_NOT_FOUND` becomes raised: 404 on `getEffectiveActionContext`.
5. **D4b.** `RequiredNotInForce.reason` is narrowed to a two-value type in both slices, and the
   `ActivationWireTokenParityTest` row is re-pinned to that type.
6. **Walk-node predicate** (PR C decision `01M702A71YF7WNDTG2AA89K8ZA`): Eff, plus each held step (`StepHolding`) whose
   mission type is in Eff. Template, asset, guard and lifecycle nodes are left out, with a divergence entry.

## Follow-up after PR C's plan-point squad (12:07 UTC; WP27–WP29 at `b7f06f46`)

Details are in PR C's `research/plan-point-squad-pr-c/dispositions.md`.

1. **`StepHolding` has two forms** (PR C decision `01M7037V93Y077H8M4W9V8Y6B3`):
   - **Per pack:** "P defines `mission_type:M` and declares `action:M/s`". It takes the pack's defined artifacts and
     works from both `EdgeIndex` and `PackCompositionFacts`. FR-009's compose check calls this form.
   - **On the closure:** some closure pack holds the step, and its place is the merged winner of `action:M/s`, so a
     layer override of a held step counts. `stepsOf`, `action_not_found` and the walk use this form.
2. **OD-6 scope.** It applies to a `step_enhancement` with exactly one `enhances` target, which must be an `action:`.
   - If the target is held, the enhancer binds to M: it is grounded if and only if M is in force, and is otherwise
     pruned with `MISSION_TYPE_NOT_IN_FORCE`.
   - If the target is not held, it is pruned with `ENHANCED_TARGET_NOT_IN_FORCE`.
   - Every other shape keeps E6 as built: C36 does not enforce a single target, and the ADR-0066 check (phase 1, D9)
     does.
   - **Deferred to the ADR-0066 integrity work:** a step enhancement that also `overrides` its own `mission_type`
     makes E7 unstable, and PR C does not refuse that shape. This is raised with the operator: refusing it is a new
     load rule.
3. **The Java value behind `NodeReference`:** `ai.speckitty.charter.activations.domain.NodeRef(NodeKind kind, String id)`.
4. **The walk** lists only activatable artifacts and starts at the held step. The graph's nodes are Eff plus the held
   steps of in-force mission types.
5. **#439:** an operator ruling is needed on the smallest-URN tie in `satisfiedBy`/`overriddenBy`. It is outside both
   missions' scope.

## WP27 approved: public names (13:37 UTC)

On lane branch `kitty/mission-layer-merge-and-effective-reads-01M6R0J6-lane-y`, pushed. They land on main with PR C.

**`ai.speckitty.charter.packs.domain.StepHolding`** (static, pure; OD-9):
- `Optional<Urn> missionTypeOf(Urn step)`
- `boolean heldByPack(Collection<String> definedArtifacts, Urn step)`: the per-pack form, for FR-009. It takes
  `PackCompositionFacts.defined()`.
- `Set<Urn> stepsHeldByPack(Collection<String>)`
- `boolean heldByClosure(Collection<? extends Collection<String>> packs, Urn step)`
- `Set<Urn> stepsHeldByClosure(...)`
- `Optional<Urn> soleActionTarget(Urn source, Collection<Urn> enhancesTargets)`: the OD-6 shape.

**OD-6:**
- The binding rides in `InForceResolution.Edges.bindings`, derived in `ClosureView` before the winner filter.
- A pruned step enhancer has `Reason.MISSION_TYPE_NOT_IN_FORCE`; an unheld target gives
  `ENHANCED_TARGET_NOT_IN_FORCE`.
- Fragment: `contracts/charter/changes/2026-11-09-c-step-enhancer-binding.md`.

**`packs.application.ActionContextWalk(EdgeIndex merged)`:**
- `Context walk(String missionType, String action, int depth, Predicate<Urn> inForce)` throws
  `CharterRefusal(MISSION_TYPE_NOT_FOUND | ACTION_NOT_FOUND)`.
- `Context(Urn action, boolean missionTypeInForce, List<Entry> entries, List<SuggestionCandidate>,
  List<Urn> actionEnhancedBy, List<Urn> actionEnhances)`.
- `Entry(Urn urn, Step step, Urn via, int hops, Optional<String> when, Optional<String> reason, List<Urn> enhancedBy,
  List<Urn> enhances)`, sorted by full URN.
- `actionEnhancedBy` holds the enhancers in force of the start step: the lookup phase 2's prompt read needs.

**`packs.application.ClosureView`:**
- `actionContext(String missionType, String action, int depth, InForceStates states)`
- `GraphProjection mergedGraph(InForceStates states)`

**D4b:** `InForceStates.PruningReason {MISSION_TYPE_NOT_IN_FORCE, ENHANCED_TARGET_NOT_IN_FORCE}`.

**Still to come from WP28:** `NodeReference.yaml` plus `activations.domain.NodeRef(NodeKind kind, String id)`, and
`ACTION_NOT_FOUND` raised.

**Behaviour change:** the activation cascade (W20, via `EdgeIndex.stepsOf`) now follows only held steps.

## WP28 approved: public names (15:19 UTC; lane-z, pushed)

**Contract:**
- `NodeReference.yaml`: `{kind: NodeKind, id}`, closed. `id` matches `^[A-Za-z0-9][A-Za-z0-9_./-]*$`, 1–256
  characters. The reuse rule: `ArtifactReference` for an activatable artifact, `NodeReference` for a node that may not
  be activatable, `Urn` for a graph identity.
- `NodeKind.yaml` lists only the authored kinds.
- `EnhancerWithoutTarget.targets` items are `NodeReference`, `minItems: 1`.
- `EffectiveActionContext` gains a required member,
  `enhancements: [ActionEnhancement {target: NodeReference, enhancedBy: [ArtifactReference] minItems 1}]`, which
  includes the start step's enhancers (decision `01M70C42KC78KB9H6KB7TK9224`).
- `action_not_found` is raised: 404 on `getEffectiveActionContext`, before any 304. Its `CharterRefusalCode.yaml` hunk
  is minimal.

**Java:**
- `activations.domain.NodeRef(NodeKind kind, String id)`: `of(Urn)` is empty for a guard or lifecycle; ordering is by
  kind token, then id.
- `EffectiveSkippedReport.PruningReason {MISSION_TYPE_NOT_IN_FORCE, ENHANCED_TARGET_NOT_IN_FORCE}`.
- Port: `PacksSession.graph(InForceQuery)`, `PacksSession.actionContext(String missionType, String action, int depth,
  InForceQuery)`, `PacksSession.requireAction(...)`.
- Service: `EffectiveReadService.graph(id, ifNoneMatch)`, `EffectiveReadService.actionContext(id,
  EffectiveTag.ContextQuery, ifNoneMatch)`. Each read has its own `EffectiveTag` over `closureFingerprint()`.

**Suggestions** (decision `01M70CTZP7P2EHSCK2G0RET74B`): a subset of the listing's suggestions.

WP29 is in progress. The merge notice will be posted on #438.
