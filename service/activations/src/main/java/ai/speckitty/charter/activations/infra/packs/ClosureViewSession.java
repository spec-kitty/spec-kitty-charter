package ai.speckitty.charter.activations.infra.packs;

import ai.speckitty.charter.activations.application.PacksSession;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.ReachRequest;
import ai.speckitty.charter.packs.application.ReachResult;
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
        return MergedSnapshot.empty();
    }

    @Override
    public InForceAnswer inForce(InForceQuery query) {
        return InForceAnswer.empty();
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
