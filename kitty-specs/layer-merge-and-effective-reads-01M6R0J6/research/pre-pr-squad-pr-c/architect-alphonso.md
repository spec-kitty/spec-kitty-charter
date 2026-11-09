# PR C pre-merge squad: architect-alphonso

Mission `layer-merge-and-effective-reads-01M6R0J6`, PR C (`issue-10-layer-merge-effective-reads` worktree
`.worktrees/pr-c`, branch `issue-10-pr-c-graph-context`: `a07524cc` WP27, `d74382fd` WP28, `c893a7ec` WP29), read
against `origin/main`. Read-only. Gradle was not run.

## Profile and governance applied

- Profile `architect-alphonso` (`spec-kitty agent profile show`): the lens covers component boundaries, contract shape
  and decisions with their rationale. It does not cover implementation.
- Directives: DIRECTIVE_001 (architectural integrity: hexagonal boundaries, one owner per rule), DIRECTIVE_003
  (decisions recorded and traceable: the IC-13 decision is challenged here with evidence), DIRECTIVE_032 (conceptual
  alignment of names across contract, Java and the parallel mission), DIRECTIVE_041/051 (from the profile) and
  RECONCILE_CHANGE_SCOPE_TENSIONS (the overlap with #437/#438).
- Paradigms: domain-driven-design (the bounded contexts packs and activations, the anti-corruption mapping in
  `ClosureViewSession`) and c4-incremental-detail-modeling (component-level boundary checks).
- Tactic: dependency-hygiene (no domain-to-infra edge, one-way packs→activations through the port).
- Known environment gap: `charter context --action review` reports that the `internal` and `qa` packs are missing
  from disk. No QA-pack directives were loaded.

## Findings

[MAJOR] contracts/charter/changes/2026-11-07-c-deny-satisfied-by-override.md:1,15-18 + docs/conformance/divergence-register.md:500 + decision DM-01M70JFXZHY2NKA6Y6RQ66AYA9
- **Issue.** The IC-13 fragment stays `### Changed`, and the decision's rationale has a gap.
  - The rationale rests on two claims: "the response shapes do not change", and "a client that handled 409 keeps
    working".
  - PR C's own DIV-204 row shows a second change for the same input, which the decision did not weigh: "a layer merge
    that refuses (DIV-217, DIV-218) reaches that write as `500 pack_unreadable`, where it answered `409` before PR C".
    That moves the answer from a non-retryable client refusal (4xx) to a server fault (5xx).
  - The fragment's sentence "A merge refusal of the closure ... now reaches a write that adds a deny entry on a required
    artifact" gives neither status. A reader of the release notes cannot see the change from 409 to 500.
  - The squad rule says "### Breaking where a documented response of an operation on main changes". Both changes
    alter a documented response of `createActivation`, `replaceActivation` and `deactivateActivationArtifact` for an
    unchanged input: 409 becomes 200/201, and 409 becomes 500.
  - The mission's own precedent agrees. `2026-11-07-a0-ruling-breaking.md` filed a refusal that became a success
    (422 → drop) as Breaking.
  - ADR-0022 asks a `### Breaking` block to name core's seam. Here the seam diverges: core's in-process backend still
    refuses (DIV-204).
- **Recommendation.**
  - At minimum, the fragment must state "`500 pack_unreadable` where it answered `409 artifact_required`" for a closure
    whose merge refuses.
  - Preferred: add a separate `### Breaking` fragment (one block per file) for the 409→200/201 relaxation and the
    409→500 path. It should name core's seam (the core client must not rely on 409 to keep a required artifact, and
    must handle 500 on these writes). The prose can stay in `### Changed`.
  - Put the new evidence to the operator as a re-ruling of `01M70JFXZHY2NKA6Y6RQ66AYA9`. If moved, also update
    ADR-0059 amendment R6 (`docs/adr/0059-...md`, the "(`### Changed`: #399's shipped write behavior changes)" clause).

[MINOR] service/packs/src/main/java/ai/speckitty/charter/packs/domain/InForceResolution.java:346-351 (also LayerMerge.java:387-392, activations/infra/packs/ClosureViewSession.java:409-417)
- **Issue.** `StepHolding.missionTypeOf` is declared "the one predicate" for steps. Three other copies of the
  "mission type of `action:M/s`" parse remain in production:
  - `InForceResolution.bindingOf`, on the OD-6 path, in the same package as `StepHolding`;
  - `LayerMerge.settlementKey`;
  - `ClosureViewSession.writer`.
- They also disagree on an edge case. `StepHolding.missionTypeOf` returns empty for `action:M/` (StepHolding.java:44),
  while `bindingOf` binds `action:M/` to `M` (`slash > 0` only). `Urn.action` and `Urn.parse` do not exclude a trailing
  slash.
- This is one rule with two owners: the walk/graph and the in-force resolution could disagree on whether a degenerate
  action is a step.
- **Recommendation.** Route `bindingOf` (at least) through `StepHolding.missionTypeOf`. The packs-domain copy is a
  one-liner, and `PACKS_DOMAIN_IS_PURE` allows it. The two pre-existing copies can be tracked as a follow-up.

[MINOR] service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ContractDescriptionsTest.java:51-54 vs phase 1 WP04 (#437)
- **Issue.** PR C pins `.doesNotContain("stay reserved")` over the whole `CharterRefusalCode` description. Phase 1
  WP04 conflicts with that pin in two ways:
  - It rewrites the reserved-codes paragraph of the same description (main :160-166) and must keep "the two slot codes
    reserved". The plural wording "... codes stay reserved" would then break PR C's test after the rebase, with no
    semantic fault.
  - WP04 step 1 also says "Leave the 'two reads that stay reserved' sentence alone (PR C's, pinned by
    `ContractDescriptionsTest`)". PR C deleted that sentence, so the instruction is stale.
- **Recommendation.**
  - Narrow the negative pin to the removed text (`"of an activation that remain (the graph and the action context)
    stay reserved"` and the existing `"Only the two effective reads"`).
  - Say in the merge notice that the sentence is gone.

[MINOR] contracts/charter/schemas/CharterRefusalCode.yaml:30-33 (PR C hunk 1, the rewrapped line "... pack_level_mismatch and drg_schema_error are no longer raised: no operation")
- **Issue.** The two sides' edits fall on the same lines, so the rebase will conflict. Phase 1 assumes they land in
  separate hunks.
  - PR C's rewrap moved the start of the `drg_schema_error` sentence onto a line it changed.
  - Phase 1 WP04 (task T-step at its line 364) rewrites main :32-35 to say `drg_schema_error` is raised (OD-3 (a)).
  - Main :56-60 has the same problem. PR C rewrote the `action_not_found` sentence there, and the line ends with the
    start of "A unit test of the service fails ... outside the two classes no operation reaches (the whole-pack load)".
    OD-3 (a) also makes that sentence false.
- **Recommendation.** No code change is required, because rebase reconciliation (A10) is planned. The merge notice
  should name both spots: main :30-35 and :56-60. The merged text must keep PR C's `action_not_found` sentences and
  phase 1's `drg_schema_error` flip. `ReservedRefusalCodesTest` is fine: PR C changes main :195-208 and phase 1 changes
  :189-194, and one unchanged line separates the two edits.

[MINOR] client-java/src/test/java/ai/speckitty/charter/client/CascadeDepthRoundTripTest.java:51
- **Issue.** The `### Breaking` fragment `2026-11-09-c-node-reference-step-targets.md` tells core to regenerate its
  strict DTOs. The only `client-java` coverage of `enhancedTargetNotInForce` is an empty list, so no generated-client
  test reads a `NodeReference` target, an `action` kind or an id with a slash. WP28 listed this file as "follows the
  `NodeReference` change".
- **Recommendation.** Add one client round-trip with a step target `{kind: action, id: implement/plan}`. This is the
  DTO evidence the Breaking block promises core.

[NIT] service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSession.java:103-111
- **Issue.** The port `PacksSession.requireAction` is implemented by running `ClosureView.actionContext` with an empty
  `InForceStates` and discarding the result. The one owner of the name rule is the private
  `ActionContextWalk.known(...)`. The port works only through a side effect of the walk, and a later change to the
  walk's early return would silently couple the two. The comment's line wrap is also broken ("the\n// walk").
- **Recommendation.** Expose the name check as its own method (`ClosureView.requireAction`, delegating to the walk's
  `known`), so the port maps one to one.

[NIT] contracts/charter/paths/activations_activationId_graph.yaml:5
- **Issue.** The `summary` still reads "Read the graph of the offering filtered by an activation". The new description
  (and ADR-0018 D1) says the read is the merged closure under the activation, never the offering's graph.
- **Recommendation.** Use a summary such as "Read the effective graph of an activation".

[NIT] phase 1 tasks/WP01-step-enhancement-held.md:161 and plan.md:290 (#437)
- **Issue.** The C-004 "PR C-owned" list names `GraphWalk`. PR C ships no such class: the walk is
  `ActionContextWalk`, and the graph is `GraphProjection`/`EdgeIndex.links`.
- **Recommendation.** Correct the name in the merge notice. It is harmless.

## Checked and clean (no finding)

- **Hexagonal boundaries.**
  - `activations.domain` gains `NodeRef`, `InForceFacts`, `GraphSnapshot`, `ActionContext` and `EffectiveContext`.
    These import only `activations.domain`, `kernel.domain` and `java.util`.
  - `packs` types reach activations only through the `PacksSession` port and the `ClosureViewSession` adapter mapping.
  - `HexagonalArchitectureTest` changes only `allowEmptyShould(true)` on the reserved-method rule. The rule still has
    its marked-method control.
- **One owner per rule.**
  - **Step holding (OD-9).** It lives in `StepHolding` alone (per-pack and closure forms). `EdgeIndex.stepsOf`,
    `EdgeIndex.holdsStep`, `ActionContextWalk.known` (`action_not_found`), `GraphProjection` and
    `ClosureView.resolutionEdges` all call it.
  - **The OD-6 shape.** Only `StepHolding.soleActionTarget` defines it. The binding is derived once, in
    `ClosureView.enhancements`, before the winner filter.
  - **The IC-13 deny rule.** `DocumentRules.floorRefusal` asks `InForceFacts`, which the `PacksSession.inForce` port
    answers through the same `InForceResolution` the reads use. No copy of the resolution is in the domain.
  - **The effective predicate.** It is built once, in `ClosureView.effective`. The walk, the graph and the enhancer
    lists all take it.
  - **Write warnings and read buckets.** `enhanced_target_not_in_force` in both comes from `InForceStates` only.
  - **D4b.** `InForceStates.PruningReason` maps to `EffectiveSkippedReport.PruningReason` through an exhaustive switch,
    with no copy across slices.
- **Fragment classification (other five).**
  - `c-graph-and-context` is `### Breaking`. It is correct and conservative: closed responses gain required members,
    and 501 and `x-reserved` are removed.
  - `c-graph-and-context-added` is `### Added`, and `c-node-reference-added` is `### Added`.
  - `c-node-reference-step-targets` is `### Breaking`. It names all seven carriers, the `artifactId`→`id` rename, the
    widened kind and core's DTO regeneration.
  - `c-step-enhancer-binding` is `### Changed`. It is acceptable: response shapes do not change, and it is a read and
    warning outcome that the operator ruled under OD-6. Each fragment has one `###` block.
- **ADR consistency.**
  - ADR-0059 amendment R6 matches the code: OD-6 binding, held steps, D16 floor and write scope.
  - ADR-0018 D1/D2 is respected. The offering's walk is untouched, and the effective context reuses D2's five steps
    over the in-force predicate, from the held step and not the mission-type node.
- **Contract reuse rule.** `ArtifactReference`, `NodeReference` and `Urn` are each defined once. The Java `NodeRef`
  rejects guard and lifecycle, mirroring the closed `NodeKind`.
- **DIV numbers.** PR C takes DIV-239 to DIV-244 and leaves DIV-238 to the step-enhancements mission
  (divergence-register.md says so). Main ends at DIV-237, so nothing collides.

## Where this lens does not apply

- Test adequacy beyond the boundary tests: the red-first evidence and mutation strength are the reviewer's lens.
- Build status, Checkstyle and Gradle results: another delegate runs Gradle.
- #439 (smallest-URN tie) and the A7 dissent: these were dispositioned or are operator-pending, and are not raised again.
- Wording and cspell quality of the docs (C15 file list).

## Public symbols #438 (phase 2) consumes, as built on PR C HEAD c893a7ec

All of these match `prc-symbols.md` on `origin/issue-424-adr-0066-phase-2-effective-prompt`; no drift was found.

- `ai.speckitty.charter.packs.domain.StepHolding` (final, static, pure):
  - `static Optional<Urn> missionTypeOf(Urn step)`
  - `static boolean heldByPack(Collection<String> definedArtifacts, Urn step)` (per-pack form, for FR-009 over
    `PackCompositionFacts.defined()`)
  - `static Set<Urn> stepsHeldByPack(Collection<String> definedArtifacts)`
  - `static boolean heldByClosure(Collection<? extends Collection<String>> packs, Urn step)`
  - `static Set<Urn> stepsHeldByClosure(Collection<? extends Collection<String>> packs)`
  - `static Optional<Urn> soleActionTarget(Urn source, Collection<Urn> enhancesTargets)`
- `ai.speckitty.charter.packs.domain.InForceResolution.Edges(Map<Urn,Set<Urn>> overrides, Map<Urn,Set<Urn>> enhances,
  Map<Urn,Urn> bindings)`. The OD-6 binding rides in `bindings`.
- `ai.speckitty.charter.packs.domain.InForceStates.PruningReason {MISSION_TYPE_NOT_IN_FORCE,
  ENHANCED_TARGET_NOT_IN_FORCE}`, and `InForceStates.RequiredNotInForce(Urn urn, PruningReason reason)`.
- `ai.speckitty.charter.packs.application.ActionContextWalk`:
  - `public ActionContextWalk(EdgeIndex merged)`
  - `Context walk(String missionType, String action, int depth, Predicate<Urn> inForce)`, which throws
    `CharterRefusal(MISSION_TYPE_NOT_FOUND | ACTION_NOT_FOUND)`.
  - `record Context(Urn action, boolean missionTypeInForce, List<Entry> entries, List<SuggestionCandidate>
    suggestionCandidates, List<Urn> actionEnhancedBy, List<Urn> actionEnhances)`
  - `record Entry(Urn urn, Step step, Urn via, int hops, Optional<String> when, Optional<String> reason, List<Urn>
    enhancedBy, List<Urn> enhances)`
  - `record SuggestionCandidate(Urn urn, Urn via, int hops, Optional<String> when, Optional<String> reason)`
  - `enum Step`
- `ai.speckitty.charter.packs.application.ClosureView`:
  - `ActionContextWalk.Context actionContext(String missionType, String action, int depth, InForceStates states)`
  - `GraphProjection mergedGraph(InForceStates states)`
- `ai.speckitty.charter.packs.application.GraphProjection(List<Urn> nodes, List<Edge> edges, List<LayerOverride>
  layerOverrides)`.
- `ai.speckitty.charter.packs.application.EdgeIndex` (public class; `holdsStep(Urn)`, `heldSteps()`, `stepsOf(Urn)`
  and `links()` are package-private, so they can be used from `packs.application` only).
- `ai.speckitty.charter.activations.domain.NodeRef(NodeKind kind, String id)` (Comparable: kind token, then id):
  - `static Optional<NodeRef> of(Urn)` (empty for guard and lifecycle)
  - `static NodeRef of(ArtifactRef)`
  - `String urn()`
- `ai.speckitty.charter.activations.domain.EffectiveSkippedReport.PruningReason {MISSION_TYPE_NOT_IN_FORCE,
  ENHANCED_TARGET_NOT_IN_FORCE}`.
- `ai.speckitty.charter.activations.application.PacksSession` (port):
  - `GraphSnapshot graph(InForceQuery query)`
  - `ActionContext actionContext(String missionType, String action, int depth, InForceQuery query)`
  - `void requireAction(String missionType, String action)`
  - `InForceAnswer inForce(InForceQuery query)`
- `ai.speckitty.charter.activations.application.EffectiveReadService`:
  - `Read<GraphRead> graph(ActivationId id, @Nullable String ifNoneMatch)`
  - `Read<ContextRead> actionContext(ActivationId id, EffectiveTag.ContextQuery query, @Nullable String ifNoneMatch)`
- `ai.speckitty.charter.activations.domain.InForceFacts` (functional interface,
  `InForceAnswer inForce(InForceQuery)`, `NONE`).
- `kernel.domain.RefusalCode.ACTION_NOT_FOUND` is raised (404 on `getEffectiveActionContext`, before any 304).
- Contract:
  - `schemas/NodeReference.yaml` `{kind: NodeKind, id}` is closed. Its `id` matches `^[A-Za-z0-9][A-Za-z0-9_./-]*$`
    with length 1 to 256.
  - `NodeKind` holds the authored kinds only.
  - `EnhancerWithoutTarget.targets: [NodeReference] minItems 1`.
  - `EffectiveActionContext.enhancements: [ActionEnhancement {target: NodeReference, enhancedBy}]`.
  - `LayerOverride` and `LayerOverrideSide`.
- Merge-notice corrections for #437/#438:
  - `GraphWalk` does not exist.
  - The "two reads that stay reserved" sentence is gone.
  - The `CharterRefusalCode.yaml` conflicts fall at main :30-35 and :56-60.

## Verdict

**GO-WITH-FIXES.** No blocker. The boundaries hold and each rule has one owner (StepHolding, the OD-6 binding,
InForceFacts→InForceResolution). The NodeReference contract change is shaped and classified correctly, and the public
names match what #438 was told.

The fixes:
- **Required:** the IC-13 fragment must state the 409→500 path. Re-rule `01M70JFXZHY2NKA6Y6RQ66AYA9` to file a
  `### Breaking` block for it and for the 409→2xx relaxation.
- **Recommended:**
  - fold `InForceResolution.bindingOf` onto `StepHolding.missionTypeOf`;
  - narrow the `doesNotContain("stay reserved")` pin;
  - name the two YAML conflict spots in the merge notice;
  - add a `client-java` round trip of a step `NodeReference`.
