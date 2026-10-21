package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.DoctrineGraph;
import ai.speckitty.charter.kernel.domain.DrgEdge;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.Relation;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The five-step walk of one action over a merged graph (research D2 and D3 of the mission). One instance is one
 * walk: it keeps the entries listed so far, in the order the steps listed them.
 *
 * <p>Only the scoped artifacts seed {@code suggests}, as in core; the {@code requires} of the suggested artifacts
 * are listed too, an intentional difference from core's code (DIV-110).
 *
 * <p>Every step is a breadth-first, multi-source, cycle-safe walk over the edges of one relation. A node a step
 * reaches by a shorter path is expanded from that path, so a depth cap never hides a child a shorter path reaches.
 * A step never lists a node an earlier step listed, and it still walks through it. The action node and the mission
 * type node are never entries and never walked: the mission type's scope edges are the activation cascade's entry
 * point, not part of this walk.
 */
final class ActionContextWalk {

    private static final int UNBOUNDED = Integer.MAX_VALUE;

    private final DoctrineGraph graph;
    private final Urn action;
    private final Urn missionType;
    private final Map<Urn, GuidanceEntry> listed = new LinkedHashMap<>();

    ActionContextWalk(DoctrineGraph graph, Urn action, Urn missionType) {
        this.graph = graph;
        this.action = action;
        this.missionType = missionType;
    }

    /**
     * Runs the five steps.
     *
     * @param depth how many {@code suggests} hops step 3 follows; 0 skips steps 3 and 4
     * @return the listed entries, sorted by full URN string
     */
    List<GuidanceEntry> run(int depth) {
        List<Urn> scoped = step(List.of(action), Relation.SCOPE, 1);
        step(scoped, Relation.REQUIRES, UNBOUNDED);
        if (depth > 0) {
            // Step 3 matches core's intent: suggests is walked "from scoped artifacts" (offering/drg/query.py:196-197),
            // so a required artifact is no seed. It is still walked through when a suggests path reaches it.
            List<Urn> suggested = step(scoped, Relation.SUGGESTS, depth);
            // Step 4 is DIV-110, an intentional difference from core's code (query.py:226), which lists a suggested
            // artifact without its hard dependencies; core's own docstring calls requires "hard dependencies"
            // (query.py:194-195).
            step(suggested, Relation.REQUIRES, UNBOUNDED);
        }
        step(List.copyOf(listed.keySet()), Relation.VOCABULARY, 1);
        return listed.values().stream()
                .sorted(Comparator.comparing(GuidanceEntry::urn))
                .toList();
    }

    /**
     * One breadth-first step: lists every node reachable from {@code seeds} over {@code relation} within
     * {@code maxSteps} edges that no earlier step listed.
     *
     * @return the nodes this step listed, in the order it listed them
     */
    private List<Urn> step(Collection<Urn> seeds, Relation relation, int maxSteps) {
        List<Urn> added = new ArrayList<>();
        Set<Urn> visited = new HashSet<>(seeds);
        Collection<Urn> frontier = seeds;
        for (int distance = 1; distance <= maxSteps && !frontier.isEmpty(); distance++) {
            Map<Urn, Urn> parents = parentsOfNextLevel(frontier, relation, visited);
            parents.forEach((target, parent) -> {
                if (!listed.containsKey(target)) {
                    listed.put(target, entry(target, relation, parent));
                    added.add(target);
                }
            });
            visited.addAll(parents.keySet());
            frontier = parents.keySet();
        }
        return added;
    }

    /**
     * The nodes one edge beyond {@code frontier}, each with the parent it takes: the frontier node with the fewest
     * hops, then the smallest URN.
     */
    private Map<Urn, Urn> parentsOfNextLevel(Collection<Urn> frontier, Relation relation, Set<Urn> visited) {
        Map<Urn, Urn> parents = new TreeMap<>();
        for (Urn node : frontier) {
            for (DrgEdge edge : graph.outgoing(node, relation)) {
                Urn target = edge.target();
                if (!visited.contains(target) && !isEndpoint(target)) {
                    parents.merge(target, node, this::preferredParent);
                }
            }
        }
        return parents;
    }

    private Urn preferredParent(Urn first, Urn second) {
        int byHops = Integer.compare(hops(first), hops(second));
        if (byHops != 0) {
            return byHops < 0 ? first : second;
        }
        return first.compareTo(second) <= 0 ? first : second;
    }

    private boolean isEndpoint(Urn node) {
        return node.equals(action) || node.equals(missionType);
    }

    private int hops(Urn node) {
        if (node.equals(action)) {
            return 0;
        }
        return Objects.requireNonNull(listed.get(node), () -> node + " is walked before it is listed")
                .hops();
    }

    private GuidanceEntry entry(Urn target, Relation relation, Urn parent) {
        Optional<String> label = graph.node(target).flatMap(DrgNode::label);
        return new GuidanceEntry(target, target.kind(), label, relation, Optional.empty(), parent, hops(parent) + 1);
    }
}
