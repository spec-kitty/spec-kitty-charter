package ai.speckitty.charter.activations.infra.packs;

import ai.speckitty.charter.activations.application.PacksSession;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.EffectiveSet;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.ReachRequest;
import ai.speckitty.charter.packs.application.ReachResult;
import ai.speckitty.charter.packs.domain.InForceRequest;
import ai.speckitty.charter.packs.domain.InForceStates;
import ai.speckitty.charter.packs.domain.LayerOverride;
import ai.speckitty.charter.packs.domain.MergedClosure;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

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
        return inForceOf(view.inForce(requestOf(query)));
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
     * among them re-homed through the winners, and the layer overrides.
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
        return new MergedSnapshot(winners, languages, edgesOf(merged, winners.keySet()), overridesOf(closure));
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
                    .ifPresent(ref -> overrides.add(
                            new MergedSnapshot.LayerOverride(ref, side(replaced.winner()), side(replaced.loser()))));
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
                .forEach((urn, list) -> activatable(urn).ifPresent(ref -> targets.put(ref, activatableNodes(list))));
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

    private static List<EffectiveSkippedReport.RequiredNotInForce> requiredNotInForce(InForceStates states) {
        List<EffectiveSkippedReport.RequiredNotInForce> dropped = new ArrayList<>();
        for (InForceStates.RequiredNotInForce entry : states.requiredNotInForce()) {
            activatable(entry.urn())
                    .ifPresent(ref -> dropped.add(new EffectiveSkippedReport.RequiredNotInForce(
                            ref, EffectiveSet.Reason.valueOf(entry.reason().name()))));
        }
        return dropped;
    }

    /** The artifact a URN names, when it is one of an activatable kind: an action, a template or an asset is none. */
    private static Optional<ArtifactRef> activatable(Urn urn) {
        return ArtifactRef.of(urn).filter(ref -> ref.kind().activatable());
    }

    /** The nodes of the artifacts among the URNs; a step is still dropped here, as every other part of the report does. */
    private static List<NodeRef> activatableNodes(Collection<Urn> urns) {
        return listOf(urns).stream().map(NodeRef::of).toList();
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
        if (predecessor.kind() != NodeKind.ACTION) {
            return Optional.of(predecessor);
        }
        int slash = predecessor.id().indexOf('/');
        return slash <= 0
                ? Optional.empty()
                : Optional.of(Urn.missionType(predecessor.id().substring(0, slash)));
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
