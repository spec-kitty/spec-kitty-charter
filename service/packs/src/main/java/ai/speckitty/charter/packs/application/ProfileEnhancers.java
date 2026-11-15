package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.MergedClosure;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * The enhancers of each agent profile of a merged closure (ruling D5): every winning agent profile that {@code enhances}
 * another winning agent profile, as the in-force resolution reads the edges ({@link ClosureView.MergedView}), listed
 * under the profile it enhances in closure order, the nearest pack last, then by URN. The one order both the
 * registration rule ({@link ProfileEnhancementRule}) and the effective profile read ({@link ClosureView#resolvedProfile})
 * merge in. Whether an enhancer is in force is the caller's to judge.
 */
final class ProfileEnhancers {

    private static final NodeKind PROFILE = NodeKind.of(ArtifactKind.AGENT_PROFILE);

    private ProfileEnhancers() {}

    /**
     * The enhancers of each enhanced agent profile.
     *
     * @param merged the merged closure
     * @param closurePackIds the closure's descriptor {@code pack_id}s, dependencies first
     * @return each enhanced profile, in URN order, with the winners that enhance it, in merge order
     */
    static Map<Urn, List<MergedClosure.Winner>> of(ClosureView.MergedView merged, List<String> closurePackIds) {
        Objects.requireNonNull(merged, "merged");
        Objects.requireNonNull(closurePackIds, "closurePackIds");
        Map<Urn, MergedClosure.Winner> winners = merged.closure().winners();
        Map<Urn, List<MergedClosure.Winner>> enhancersOf = new TreeMap<>();
        merged.resolutionEdges().enhances().forEach((source, targets) -> {
            if (source.kind() == PROFILE && winners.containsKey(source)) {
                targets.stream()
                        .filter(target -> target.kind() == PROFILE)
                        .forEach(target -> enhancersOf
                                .computeIfAbsent(target, ignored -> new ArrayList<>())
                                .add(winners.get(source)));
            }
        });
        Comparator<MergedClosure.Winner> order = Comparator.comparingInt(
                        (MergedClosure.Winner winner) -> closurePackIds.indexOf(winner.descriptorPackId()))
                .thenComparing(MergedClosure.Winner::urn);
        enhancersOf.replaceAll((target, list) -> list.stream().sorted(order).toList());
        return enhancersOf;
    }

    /**
     * The agent profiles a profile enhances.
     *
     * @param merged the merged closure
     * @param profile the profile
     * @return the winning agent profiles it enhances, in URN order; empty when it enhances none
     */
    static List<Urn> targetsOf(ClosureView.MergedView merged, Urn profile) {
        return merged.resolutionEdges().enhances().getOrDefault(profile, Set.of()).stream()
                .filter(target -> target.kind() == PROFILE)
                .sorted()
                .toList();
    }
}
