package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The specialization rule of ruling D2, judged over one composed closure at registration: the {@code specializes_from}
 * edges between agent profiles form no cycle, so every profile's lineage ends and its depth is defined
 * ({@code specialization_cycle}). It reads the typed edges the closure's packs write, as {@link ImpactRelations} does,
 * and builds none. A profile is one node whichever pack holds it, since the layer merge keeps one version of an id.
 */
final class SpecializationCycleRule {

    private static final NodeKind PROFILE = NodeKind.of(ArtifactKind.AGENT_PROFILE);

    private record Pair(Urn from, Urn to) {}

    private SpecializationCycleRule() {}

    /**
     * The cycle the closure's {@code specializes_from} edges between agent profiles form.
     *
     * @param composed a composed closure with the facts it was judged on
     * @return the violation naming the cycle's profile ids in order and the packs that write its edges; empty when the
     *     lineage is acyclic
     */
    static Optional<ImpactRelations.Violation> check(OfferingReadService.ComposedClosure composed) {
        Map<Urn, List<Urn>> graph = new LinkedHashMap<>();
        Map<Pair, PackUnderResolution> writers = new LinkedHashMap<>();
        for (PackUnderResolution writer : composed.packs()) {
            for (Relationship edge : composed.factsOf(writer).edges()) {
                if (edge.type() == RelationType.SPECIALIZES_FROM
                        && edge.from().kind() == PROFILE
                        && edge.to().kind() == PROFILE) {
                    graph.computeIfAbsent(edge.from(), ignored -> new ArrayList<>())
                            .add(edge.to());
                    writers.putIfAbsent(new Pair(edge.from(), edge.to()), writer);
                }
            }
        }
        return ImpactRelations.cycleIn(graph).map(cycle -> violation(cycle, writers));
    }

    private static ImpactRelations.Violation violation(List<Urn> cycle, Map<Pair, PackUnderResolution> writers) {
        List<String> ids = new ArrayList<>(cycle.stream().map(Urn::id).toList());
        ids.add(ids.getFirst());
        Set<PackUnderResolution> involved = new LinkedHashSet<>();
        for (int i = 0; i < cycle.size(); i++) {
            Pair edge = new Pair(cycle.get(i), cycle.get((i + 1) % cycle.size()));
            Optional.ofNullable(writers.get(edge)).ifPresent(involved::add);
        }
        return new ImpactRelations.Violation(
                RefusalCode.SPECIALIZATION_CYCLE,
                "specializes_from edges among agent profiles form a cycle: " + String.join(" -> ", ids)
                        + "; a profile's lineage ends at a root",
                List.copyOf(involved));
    }
}
