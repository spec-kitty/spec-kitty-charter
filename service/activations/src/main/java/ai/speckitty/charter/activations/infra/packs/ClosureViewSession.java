package ai.speckitty.charter.activations.infra.packs;

import ai.speckitty.charter.activations.application.PacksSession;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
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
import java.util.function.Predicate;

/**
 * A {@link PacksSession} over one {@link ClosureView}: the snapshot copied from the view, and every walk answered by the
 * same view, so the request reads the packs once (NFR-007). The adapter maps the activation domain's
 * {@link ReachPlan} to the packs slice's {@link ReachRequest} ({@code specializes_from} passing {@code requires} and
 * {@code scope} through when the plan follows the lineage) and its {@link ReachResult} back to a {@link Reach}; a node
 * that is no artifact (an action) is left out of the answer, and so is a way it was reached by.
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
    public Set<ArtifactRef> enhancesPull(Set<ArtifactRef> starts, Set<ArtifactKind> kinds) {
        Predicate<Urn> admits = urn ->
                ArtifactRef.of(urn).filter(ref -> kinds.contains(ref.kind())).isPresent();
        Set<ArtifactRef> pulled = refs(view.enhancesPull(urns(starts), admits).keySet());
        pulled.removeAll(starts);
        return pulled;
    }

    @Override
    public Map<ArtifactRef, Set<ArtifactRef>> enhancersOf(Set<ArtifactRef> targets) {
        Map<ArtifactRef, Set<ArtifactRef>> enhancers = new LinkedHashMap<>();
        view.enhancersOf(urns(targets))
                .forEach((urn, places) -> ArtifactRef.of(urn)
                        .ifPresent(target -> enhancers.put(
                                target,
                                refs(places.stream().map(place -> place.urn()).toList()))));
        return enhancers;
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
                plan.lineage() ? Optional.of(ReachRequest.Lineage.specializesFrom()) : Optional.empty());
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
                vias(result.ancestry()));
    }

    private static Map<ArtifactRef, List<Reach.Via>> vias(Map<Urn, List<ReachResult.Via>> byUrn) {
        Map<ArtifactRef, List<Reach.Via>> vias = new LinkedHashMap<>();
        byUrn.forEach((urn, list) -> ArtifactRef.of(urn).ifPresent(ref -> vias.put(ref, viasOf(list))));
        return vias;
    }

    private static List<Reach.Via> viasOf(List<ReachResult.Via> list) {
        return list.stream()
                .flatMap(via ->
                        ArtifactRef.of(via.predecessor()).map(from -> new Reach.Via(from, via.relation())).stream())
                .toList();
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
