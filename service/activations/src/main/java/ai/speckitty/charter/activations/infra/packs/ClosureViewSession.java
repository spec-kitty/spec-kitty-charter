package ai.speckitty.charter.activations.infra.packs;

import ai.speckitty.charter.activations.application.PacksSession;
import ai.speckitty.charter.activations.domain.ActionContext;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.EffectiveArtifactRead;
import ai.speckitty.charter.activations.domain.EffectiveSet;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport;
import ai.speckitty.charter.activations.domain.GovernanceFacts;
import ai.speckitty.charter.activations.domain.GraphSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ActionContextWalk;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.GovernanceWalk;
import ai.speckitty.charter.packs.application.GraphProjection;
import ai.speckitty.charter.packs.application.ProfileGovernance;
import ai.speckitty.charter.packs.application.ReachRequest;
import ai.speckitty.charter.packs.application.ReachResult;
import ai.speckitty.charter.packs.domain.InForceRequest;
import ai.speckitty.charter.packs.domain.InForceStates;
import ai.speckitty.charter.packs.domain.LayerOverride;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.StepHolding;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * A {@link PacksSession} over one {@link ClosureView}: the snapshot copied from the view, and every walk answered by the
 * same view, so the request reads the packs once (NFR-007). The adapter maps the activation domain's
 * {@link ReachPlan} to the packs slice's {@link ReachRequest} ({@code specializes_from} passing {@code requires} and
 * {@code scope} through when the plan follows the lineage, and the plan's {@code suggests} bound) and its {@link ReachResult} back to a {@link Reach}; a node
 * that is no artifact (an action) is left out of the answer, and a way it was reached by from a step of a mission type is
 * kept as that mission type's.
 */
final class ClosureViewSession implements PacksSession {

    private final ClosureView view;
    private final ClosureSnapshot snapshot;

    /**
     * The in-force states of the last request put to this session, so a request that asks for the set and for the graph or
     * the walk resolves once (NFR-003). A session serves one request, on one thread.
     */
    private @Nullable InForceRequest resolvedFor;

    private @Nullable InForceStates resolved;

    /**
     * A session over a verified view.
     *
     * @param view the view of the request
     * @param snapshot the view, as the activation domain reads it
     */
    ClosureViewSession(ClosureView view, ClosureSnapshot snapshot) {
        this.view = Objects.requireNonNull(view, "view");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    @Override
    public ClosureSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public Reach reach(ReachPlan plan) {
        return reachOf(view.reach(requestOf(plan)));
    }

    @Override
    public MergedSnapshot merged() {
        return mergedOf(view.merged());
    }

    @Override
    public InForceAnswer inForce(InForceQuery query) {
        return inForceOf(statesOf(query));
    }

    @Override
    public GraphSnapshot graph(InForceQuery query) {
        return graphOf(view.mergedGraph(statesOf(query)));
    }

    @Override
    public ActionContext actionContext(String missionType, String action, int depth, InForceQuery query) {
        return contextOf(view.actionContext(missionType, action, depth, statesOf(query)));
    }

    @Override
    public Optional<EffectiveArtifactRead> effectiveArtifact(
            ArtifactKind kind, String id, int depth, InForceQuery query) {
        // One view answers the winner and the governance, against the digest the view was verified at; the states are
        // the ones every other question of this request put to the session already resolved.
        return view.effectiveArtifact(kind, id, depth, statesOf(query))
                .map(winner -> new EffectiveArtifactRead(
                        winner.artifact(),
                        winner.holder().packId().value(),
                        winner.holder().level(),
                        winner.governance().map(ClosureViewSession::governanceOf)));
    }

    @Override
    public void requireAction(String missionType, String action) {
        // The packs slice judges the names, whether or not the mission type is in force, so no in-force resolution and
        // no walk is paid for.
        view.requireAction(missionType, action);
    }

    /**
     * What is in force under the query: resolved on the first ask and kept for a read request that asks again. The cache
     * holds one query: a write that asks for another document (a deactivate judges the early floor, then the committed
     * document) resolves again.
     */
    InForceStates statesOf(InForceQuery query) {
        InForceRequest request = requestOf(query);
        InForceStates states = resolved;
        if (states == null || !request.equals(resolvedFor)) {
            states = view.inForce(request);
            resolved = states;
            resolvedFor = request;
        }
        return states;
    }

    /**
     * The activation domain's graph for the packs slice's projection: every node a node reference can name, the edges
     * among them and the layer overrides of those nodes, in the order the projection gave them.
     *
     * @param graph the projection
     * @return the graph, as values
     */
    static GraphSnapshot graphOf(GraphProjection graph) {
        List<GraphSnapshot.Edge> edges = new ArrayList<>();
        for (GraphProjection.Edge edge : graph.edges()) {
            NodeRef.of(edge.source())
                    .ifPresent(source -> NodeRef.of(edge.target())
                            .ifPresent(target -> edges.add(new GraphSnapshot.Edge(source, edge.relation(), target))));
        }
        List<MergedSnapshot.LayerOverride> overrides = new ArrayList<>();
        for (LayerOverride replaced : graph.layerOverrides()) {
            NodeRef.of(replaced.urn())
                    .ifPresent(node -> overrides.add(
                            new MergedSnapshot.LayerOverride(node, side(replaced.winner()), side(replaced.loser()))));
        }
        return new GraphSnapshot(nodesOf(graph.nodes()), edges, overrides);
    }

    /**
     * The activation domain's context for the packs slice's walk: the artifacts in force the walk lists with their
     * enhancers, the candidates for a suggestion, and the enhancers in force of the step. Each entry and each candidate
     * keeps the pass that listed it, as its {@link ActionContext.AppliesThrough} (FR-009). The walk's step, via, hops and
     * pass root are its own: the context names artifacts, and the effective set decides their states.
     *
     * @param walked what the walk reached
     * @return the context, as values
     */
    static ActionContext contextOf(ActionContextWalk.Context walked) {
        if (!walked.missionTypeInForce()) {
            return ActionContext.notInForce();
        }
        List<ActionContext.Reached> entries = new ArrayList<>();
        for (ActionContextWalk.Entry entry : walked.entries()) {
            activatable(entry.urn())
                    .ifPresent(ref -> entries.add(new ActionContext.Reached(
                            ref, listOf(entry.enhancedBy()), appliesThroughOf(entry.pass()))));
        }
        List<ActionContext.SuggestionCandidate> candidates = new ArrayList<>();
        for (ActionContextWalk.SuggestionCandidate candidate : walked.suggestionCandidates()) {
            activatable(candidate.urn())
                    .ifPresent(ref -> candidates.add(new ActionContext.SuggestionCandidate(
                            ref, candidate.when(), candidate.reason(), appliesThroughOf(candidate.pass()))));
        }
        return new ActionContext(true, entries, candidates, listOf(walked.actionEnhancedBy()));
    }

    /**
     * The activation domain's name for a pass of the effective walk: the same three parts of the ownership rule, in the
     * same order (research R3).
     *
     * @param pass the pass that listed an entry or reached a candidate
     * @return its appliesThrough
     */
    static ActionContext.AppliesThrough appliesThroughOf(ActionContextWalk.Pass pass) {
        return switch (pass) {
            case ACTION -> ActionContext.AppliesThrough.ACTION;
            case MISSION_TYPE -> ActionContext.AppliesThrough.MISSION_TYPE;
            case PROJECT -> ActionContext.AppliesThrough.PROJECT;
        };
    }

    /**
     * The activation domain's governance for the packs slice's profile walk: the artifacts in force the walk lists, with
     * the step, relation, via and hops it reached them by (which {@link #contextOf} drops for an action), and the
     * candidates for a suggestion. The predicate of an activation admits only artifacts of an activatable kind, so an
     * entry that is none is dropped as every other part of the answer drops what is no artifact; a via is the profile or
     * an entry, so one that is no artifact is a fault.
     *
     * @param governance the walk's answer, with holders
     * @return the governance, as values
     * @throws IllegalStateException when the via of an entry names no artifact
     */
    static GovernanceFacts governanceOf(ProfileGovernance.Governance governance) {
        List<GovernanceFacts.Entry> entries = new ArrayList<>();
        for (ProfileGovernance.Entry entry : governance.entries()) {
            activatable(entry.urn())
                    .ifPresent(ref -> entries.add(new GovernanceFacts.Entry(
                            ref,
                            stepOf(entry.step()),
                            entry.relation(),
                            viaOf(entry.via()),
                            entry.hops(),
                            entry.when(),
                            entry.reason())));
        }
        List<GovernanceFacts.Candidate> candidates = new ArrayList<>();
        for (ActionContextWalk.SuggestionCandidate candidate : governance.suggestionCandidates()) {
            activatable(candidate.urn())
                    .ifPresent(ref -> candidates.add(new GovernanceFacts.Candidate(
                            ref, viaOf(candidate.via()), candidate.hops(), candidate.when(), candidate.reason())));
        }
        return new GovernanceFacts(viaOf(governance.profile()), entries, candidates);
    }

    /** The activations slice's step for the packs slice's, by an exhaustive switch and never by name. */
    static GovernanceFacts.Step stepOf(GovernanceWalk.Step step) {
        return switch (step) {
            case LINEAGE -> GovernanceFacts.Step.LINEAGE;
            case REQUIRES -> GovernanceFacts.Step.REQUIRES;
            case SUGGESTS -> GovernanceFacts.Step.SUGGESTS;
            case REQUIRES_OF_SUGGESTED -> GovernanceFacts.Step.REQUIRES_OF_SUGGESTED;
            case VOCABULARY -> GovernanceFacts.Step.VOCABULARY;
        };
    }

    private static ArtifactRef viaOf(Urn urn) {
        return ArtifactRef.of(urn)
                .orElseThrow(() -> new IllegalStateException(
                        "The governance walk was reached through " + urn + ", which is no artifact."));
    }

    /**
     * The packs slice's in-force request for a document's query.
     *
     * @param query the query
     * @return the request
     */
    static InForceRequest requestOf(InForceQuery query) {
        return new InForceRequest(urns(query.selected()), urns(query.deactivated()), query.languages(), query.pins());
    }

    /**
     * The activation domain's merged closure for the packs slice's merge: the winners of an activatable kind, the edges
     * among them re-homed through the winners, the layer overrides and the mission type each bound winner is bound to.
     *
     * @param merged the merged view
     * @return the snapshot, as values
     */
    static MergedSnapshot mergedOf(ClosureView.MergedView merged) {
        MergedClosure closure = merged.closure();
        Map<ArtifactRef, MergedSnapshot.Winner> winners = winnersOf(closure);
        Map<ArtifactRef, Set<String>> languages = new LinkedHashMap<>();
        closure.languages()
                .forEach((urn, declared) ->
                        activatable(urn).filter(winners::containsKey).ifPresent(ref -> languages.put(ref, declared)));
        return new MergedSnapshot(
                winners,
                languages,
                edgesOf(merged, winners.keySet()),
                overridesOf(closure),
                bindingsOf(merged, winners));
    }

    /**
     * The mission type each winner of an activatable kind is bound to, as the merge reads it from the pack that holds
     * the version in force (a step contract to the type its {@code mission} names).
     */
    private static Map<ArtifactRef, ArtifactRef> bindingsOf(
            ClosureView.MergedView merged, Map<ArtifactRef, MergedSnapshot.Winner> winners) {
        Map<ArtifactRef, ArtifactRef> bindings = new LinkedHashMap<>();
        merged.bindings()
                .forEach((bound, missionType) -> activatable(bound)
                        .filter(winners::containsKey)
                        .ifPresent(ref -> activatable(missionType).ifPresent(type -> bindings.put(ref, type))));
        return bindings;
    }

    /**
     * The version in force of each artifact of an activatable kind: the configured id and level of the pack that holds
     * it, and its mutability level.
     *
     * @param closure the layer merge
     * @return the winners, in the order the closure first holds the artifacts
     */
    static Map<ArtifactRef, MergedSnapshot.Winner> winnersOf(MergedClosure closure) {
        Map<ArtifactRef, MergedSnapshot.Winner> winners = new LinkedHashMap<>();
        closure.winners()
                .forEach((urn, winner) -> activatable(urn)
                        .ifPresent(ref -> winners.put(
                                ref,
                                new MergedSnapshot.Winner(
                                        winner.packId().value(),
                                        winner.level(),
                                        EffectiveSet.Policy.valueOf(
                                                winner.policy().level().name())))));
        return winners;
    }

    /** The edges of the winners that end at a winner, from the merged index the layer merge re-homed. */
    private static List<MergedSnapshot.Edge> edgesOf(ClosureView.MergedView merged, Set<ArtifactRef> held) {
        List<MergedSnapshot.Edge> edges = new ArrayList<>();
        merged.closure()
                .winners()
                .forEach((urn, winner) -> activatable(urn)
                        .filter(held::contains)
                        .ifPresent(source -> merged.edges()
                                .inPack(new PackIdentifier(winner.descriptorPackId()))
                                .resolve(urn)
                                .ifPresent(node -> node.derivedRelationships()
                                        .forEach(edge ->
                                                edgeOf(source, edge, held).ifPresent(edges::add)))));
        return edges;
    }

    private static Optional<MergedSnapshot.Edge> edgeOf(ArtifactRef source, Relationship edge, Set<ArtifactRef> held) {
        return activatable(edge.to())
                .filter(held::contains)
                .map(target -> new MergedSnapshot.Edge(source, target, edge.type(), edge.when(), edge.rationale()));
    }

    private static List<MergedSnapshot.LayerOverride> overridesOf(MergedClosure closure) {
        List<MergedSnapshot.LayerOverride> overrides = new ArrayList<>();
        for (LayerOverride replaced : closure.layerOverrides()) {
            activatable(replaced.urn())
                    .ifPresent(ref -> overrides.add(new MergedSnapshot.LayerOverride(
                            NodeRef.of(ref), side(replaced.winner()), side(replaced.loser()))));
        }
        return overrides;
    }

    private static MergedSnapshot.LayerOverride.Side side(LayerOverride.Side side) {
        return new MergedSnapshot.LayerOverride.Side(side.packId().value(), side.level());
    }

    /**
     * The activation domain's answer for the packs slice's in-force states, restricted to artifacts of an activatable
     * kind. The enums of the two slices share their constants' names, and a test pins that.
     *
     * @param states the states
     * @return the answer, as values
     */
    static InForceAnswer inForceOf(InForceStates states) {
        Map<ArtifactRef, MergedSnapshot.ArtifactState> byRef = new LinkedHashMap<>();
        states.states().forEach((urn, state) -> activatable(urn).ifPresent(ref -> byRef.put(ref, stateOf(ref, state))));
        Map<ArtifactRef, List<NodeRef>> targets = new LinkedHashMap<>();
        states.enhancedTargets()
                .forEach((urn, list) -> activatable(urn).ifPresent(ref -> targets.put(ref, nodesOf(list))));
        return new InForceAnswer(
                byRef,
                listOf(states.staleIds()),
                denyNotApplied(states),
                overrideNotApplied(states),
                listOf(states.requiredNotHeld()),
                requiredNotInForce(states),
                targets);
    }

    private static MergedSnapshot.ArtifactState stateOf(ArtifactRef ref, InForceStates.ArtifactState state) {
        return new MergedSnapshot.ArtifactState(
                ref,
                EffectiveSet.State.valueOf(state.state().name()),
                EffectiveSet.Reason.valueOf(state.reason().name()),
                state.required(),
                state.satisfiedBy().flatMap(ClosureViewSession::activatable),
                state.overriddenBy().flatMap(ClosureViewSession::activatable),
                state.bucket().map(bucket -> EffectiveSet.Bucket.valueOf(bucket.name())));
    }

    private static List<EffectiveSkippedReport.DenyNotApplied> denyNotApplied(InForceStates states) {
        List<EffectiveSkippedReport.DenyNotApplied> denied = new ArrayList<>();
        for (InForceStates.DenyNotApplied entry : states.denyNotApplied()) {
            activatable(entry.urn())
                    .ifPresent(ref -> denied.add(new EffectiveSkippedReport.DenyNotApplied(
                            ref,
                            EffectiveSkippedReport.DenyReason.valueOf(
                                    entry.reason().name()))));
        }
        return denied;
    }

    private static List<EffectiveSkippedReport.OverrideNotApplied> overrideNotApplied(InForceStates states) {
        List<EffectiveSkippedReport.OverrideNotApplied> kept = new ArrayList<>();
        for (InForceStates.OverrideNotApplied entry : states.overrideNotApplied()) {
            activatable(entry.target())
                    .ifPresent(target -> activatable(entry.overriddenBy())
                            .ifPresent(by -> kept.add(new EffectiveSkippedReport.OverrideNotApplied(target, by))));
        }
        return kept;
    }

    /**
     * The required or locked artifacts the resolution pruned. Only an artifact an activation can select is carried: no
     * preset and no pack lock can name a step (the policy and preset schemas exclude {@code action}), so the required floor
     * holds activatable kinds by construction and the resolution never lists a step here. Were one fed in, it is dropped
     * explicitly, as every other part of the report drops what is no artifact (decision D16;
     * {@code ClosureViewSessionStepRefsTest} pins the drop and the schemas).
     */
    private static List<EffectiveSkippedReport.RequiredNotInForce> requiredNotInForce(InForceStates states) {
        List<EffectiveSkippedReport.RequiredNotInForce> dropped = new ArrayList<>();
        for (InForceStates.RequiredNotInForce entry : states.requiredNotInForce()) {
            activatable(entry.urn())
                    .ifPresent(ref -> dropped.add(
                            new EffectiveSkippedReport.RequiredNotInForce(ref, pruningReason(entry.reason()))));
        }
        return dropped;
    }

    /** The activations slice's pruning reason for the packs slice's, by an exhaustive switch and never by name. */
    static EffectiveSkippedReport.PruningReason pruningReason(InForceStates.PruningReason reason) {
        return switch (reason) {
            case MISSION_TYPE_NOT_IN_FORCE -> EffectiveSkippedReport.PruningReason.MISSION_TYPE_NOT_IN_FORCE;
            case ENHANCED_TARGET_NOT_IN_FORCE -> EffectiveSkippedReport.PruningReason.ENHANCED_TARGET_NOT_IN_FORCE;
        };
    }

    /** The artifact a URN names, when it is one of an activatable kind: an action, a template or an asset is none. */
    private static Optional<ArtifactRef> activatable(Urn urn) {
        return ArtifactRef.of(urn).filter(ref -> ref.kind().activatable());
    }

    /**
     * The nodes the URNs name, steps included: the targets of an enhancer are the one part of the report whose type is a
     * node, so a step an enhancer enhances is carried and not dropped (#429, D4(a)).
     */
    private static List<NodeRef> nodesOf(Collection<Urn> urns) {
        return urns.stream().flatMap(urn -> NodeRef.of(urn).stream()).toList();
    }

    private static List<ArtifactRef> listOf(Collection<Urn> urns) {
        return urns.stream().flatMap(urn -> activatable(urn).stream()).toList();
    }

    /**
     * The packs slice's request for a plan.
     *
     * @param plan the plan
     * @return the request
     */
    static ReachRequest requestOf(ReachPlan plan) {
        Set<Urn> stops = urns(plan.stopAt());
        return new ReachRequest(
                urns(plan.starts()),
                plan.relations(),
                stops::contains,
                plan.lineage() ? Optional.of(ReachRequest.Lineage.specializesFrom()) : Optional.empty(),
                plan.suggestsBound());
    }

    /**
     * The activation domain's reach for a walk's answer.
     *
     * @param result the answer
     * @return the reach
     */
    static Reach reachOf(ReachResult result) {
        return new Reach(
                vias(result.reached()),
                vias(result.stopped()),
                refs(result.behindStopped()),
                refs(result.ancestorsPassed()),
                vias(result.ancestry()),
                refs(result.beyondBound()));
    }

    private static Map<ArtifactRef, List<Reach.Via>> vias(Map<Urn, List<ReachResult.Via>> byUrn) {
        Map<ArtifactRef, List<Reach.Via>> vias = new LinkedHashMap<>();
        byUrn.forEach((urn, list) -> ArtifactRef.of(urn).ifPresent(ref -> vias.put(ref, viasOf(list))));
        return vias;
    }

    private static List<Reach.Via> viasOf(List<ReachResult.Via> list) {
        return list.stream()
                .flatMap(
                        via -> writer(via.predecessor())
                                .flatMap(ArtifactRef::of)
                                .map(from -> new Reach.Via(from, via.relation()))
                                .stream())
                .toList();
    }

    /**
     * The node that answers for a relation's predecessor: a step of a mission type ({@code action:<M>/<step>}, which is
     * no artifact) writes the relation for its mission type, so the via is kept as {@code mission_type:<M>}.
     */
    private static Optional<Urn> writer(Urn predecessor) {
        return predecessor.kind() != NodeKind.ACTION
                ? Optional.of(predecessor)
                : StepHolding.missionTypeOf(predecessor);
    }

    private static Set<Urn> urns(Set<ArtifactRef> refs) {
        Set<Urn> urns = new TreeSet<>();
        refs.forEach(ref -> urns.add(new Urn(NodeKind.of(ref.kind()), ref.id())));
        return urns;
    }

    private static Set<ArtifactRef> refs(Collection<Urn> urns) {
        Set<ArtifactRef> refs = new TreeSet<>();
        urns.forEach(urn -> ArtifactRef.of(urn).ifPresent(refs::add));
        return refs;
    }
}
