package ai.speckitty.charter.packs.application.badfixture;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A known-bad control for {@code PackGraphWalkBoundaryTest}: an application class that walks the pack graph itself, as
 * {@code ClosureRoots#dependents} did before #637. It reads a descriptor's {@code depends_on} and {@code parent_pack}
 * by a call and by a method reference, and descends over a deque and over a priority queue. The unchanged production
 * rules must fail on it.
 */
public final class HandWrittenDescent {

    private HandWrittenDescent() {}

    /**
     * The pack and every pack that builds on it, walked by hand.
     *
     * @param packId the pack built on
     * @param descriptors the descriptors
     * @return the packs building on it
     */
    public static Set<String> dependents(String packId, Collection<PackDescriptor> descriptors) {
        Map<String, List<String>> reverse = new HashMap<>();
        for (PackDescriptor descriptor : descriptors) {
            List<String> targets = new ArrayList<>();
            descriptor.dependencies().forEach(dependency -> targets.add(dependency.packId()));
            descriptor.parentPack().ifPresent(targets::add);
            targets.forEach(target -> reverse.computeIfAbsent(target, ignored -> new ArrayList<>())
                    .add(descriptor.packId()));
        }
        Set<String> reached = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(List.of(packId));
        while (!pending.isEmpty()) {
            String next = pending.removeFirst();
            if (reached.add(next)) {
                pending.addAll(reverse.getOrDefault(next, List.of()));
            }
        }
        return reached;
    }

    /**
     * Each descriptor's parent, read by a method reference.
     *
     * @param descriptors the descriptors
     * @return the parents named
     */
    public static List<String> parentsByReference(Collection<PackDescriptor> descriptors) {
        return descriptors.stream()
                .map(PackDescriptor::parentPack)
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    /**
     * Pack ids in a priority order, as a hand-written ordering walk would hold them.
     *
     * @param packIds the ids
     * @return the ids in natural order
     */
    public static List<String> cycleCandidates(Collection<String> packIds) {
        PriorityQueue<String> ready = new PriorityQueue<>(packIds);
        List<String> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            ordered.add(ready.poll());
        }
        return ordered;
    }
}
