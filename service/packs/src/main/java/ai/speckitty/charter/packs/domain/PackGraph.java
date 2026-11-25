package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The one owner of how the pack graph is walked (ADR-0082 decisions 1 and 2, #637): which edges a pack builds on, the
 * descent along them ({@link #compositionClosure}) and back up them ({@link #buildingOn}, {@link #directDependents}),
 * the cycles and strongly connected groups of a graph ({@link #cyclesIn}), and the grounding of {@code enhances} chains
 * ({@link #ungrounded}). No class outside {@code packs.domain} walks the pack graph for cycles, groups or descendants;
 * it hands this class the nodes and edges it read and takes the answer.
 *
 * <p>Every walk is iterative, so a long chain cannot exhaust the stack, and every answer is in a stable order: the
 * order of the inputs, never the hash order of a node (the determinism guard of the architecture page).
 */
public final class PackGraph {

    private PackGraph() {}

    /**
     * The packs one pack builds on: its {@code depends_on} targets in declared order, then its {@code parent_pack}, by
     * descriptor {@code pack_id}. These are the edges every descent over the composition follows.
     *
     * @param descriptor the pack's descriptor
     * @return the {@code pack_id}s it builds on
     */
    public static List<String> buildsOn(PackDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        List<String> successors = new ArrayList<>();
        descriptor.dependencies().stream().map(PackDependency::packId).forEach(successors::add);
        descriptor.parentPack().ifPresent(successors::add);
        return List.copyOf(successors);
    }

    /**
     * Every node reachable from the starts, the starts included, in breadth-first first-seen order, each once. A cycle
     * terminates, since each node is expanded once.
     *
     * @param starts where the walk starts, in order
     * @param successors each node's successors; empty for a node with none
     * @param <N> the node type
     * @return the nodes reached, in the order they are first met
     */
    public static <N> Set<N> reachable(Collection<N> starts, Function<N, ? extends Collection<N>> successors) {
        Objects.requireNonNull(starts, "starts");
        Objects.requireNonNull(successors, "successors");
        Set<N> reached = new LinkedHashSet<>();
        Deque<N> pending = new ArrayDeque<>(starts);
        while (!pending.isEmpty()) {
            N next = pending.removeFirst();
            if (reached.add(next)) {
                pending.addAll(successors.apply(next));
            }
        }
        return reached;
    }

    /**
     * The composition closure of a set of packs: the roots and every pack they reach over {@code depends_on} and
     * {@code parent_pack}, by descriptor {@code pack_id}, in first-seen order. It is the one closure rule of a rooted
     * compose. A target no pack of {@code byPackId} declares is recorded but not descended from: the walk tolerates it,
     * and {@link PackCompositionResolver#resolve} refuses it. A cycle terminates, since each id is walked once.
     *
     * @param rootPackIds the descriptor {@code pack_id}s of the roots
     * @param byPackId the packs the walk may descend into, by descriptor {@code pack_id}
     * @return the {@code pack_id}s of the closure, missing targets included
     */
    public static Set<String> compositionClosure(
            Collection<String> rootPackIds, Map<String, PackUnderResolution> byPackId) {
        Objects.requireNonNull(rootPackIds, "rootPackIds");
        Objects.requireNonNull(byPackId, "byPackId");
        return reachable(
                rootPackIds,
                packId -> Optional.ofNullable(byPackId.get(packId))
                        .map(pack -> buildsOn(pack.descriptor()))
                        .orElse(List.of()));
    }

    /**
     * A pack and every pack that reaches it over {@code depends_on} or {@code parent_pack}, transitively: the packs
     * whose composition closure holds it. The reverse index is one pass over the descriptors.
     *
     * @param packId the descriptor {@code pack_id} of the pack built on
     * @param descriptors the descriptors that may build on it
     * @return the {@code pack_id} itself first, then each pack building on it in first-seen order
     */
    public static Set<String> buildingOn(String packId, Collection<PackDescriptor> descriptors) {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(descriptors, "descriptors");
        Map<String, List<String>> reverse = new LinkedHashMap<>();
        for (PackDescriptor descriptor : descriptors) {
            buildsOn(descriptor)
                    .forEach(target -> reverse.computeIfAbsent(target, ignored -> new ArrayList<>())
                            .add(descriptor.packId()));
        }
        return reachable(List.of(packId), next -> reverse.getOrDefault(next, List.of()));
    }

    /**
     * The direct dependents of a pack, one edge up.
     *
     * @param dependsOn the packs whose {@code depends_on} names the pack, in the order given
     * @param parentPack the packs whose {@code parent_pack} is the pack, in the order given
     * @param <P> the pack type
     */
    public record DirectDependents<P>(List<P> dependsOn, List<P> parentPack) {

        /**
         * Copies the lists.
         *
         * @param dependsOn the {@code depends_on} dependents
         * @param parentPack the {@code parent_pack} dependents
         */
        public DirectDependents {
            dependsOn = List.copyOf(dependsOn);
            parentPack = List.copyOf(parentPack);
        }
    }

    /**
     * The packs one {@code depends_on} or {@code parent_pack} edge up from a pack: those whose descriptor names its
     * {@code pack_id}. A pack that names it both ways is in both lists.
     *
     * @param packId the descriptor {@code pack_id} of the pack depended on
     * @param packs the packs that may depend on it, in the order the answer keeps
     * @param descriptorOf each pack's descriptor
     * @param <P> the pack type
     * @return the dependents, each list in the order of {@code packs}
     */
    public static <P> DirectDependents<P> directDependents(
            String packId, List<P> packs, Function<P, PackDescriptor> descriptorOf) {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(packs, "packs");
        Objects.requireNonNull(descriptorOf, "descriptorOf");
        List<P> dependsOn = new ArrayList<>();
        List<P> parentPack = new ArrayList<>();
        for (P pack : packs) {
            PackDescriptor descriptor = descriptorOf.apply(pack);
            if (descriptor.dependencies().stream().map(PackDependency::packId).anyMatch(packId::equals)) {
                dependsOn.add(pack);
            }
            if (descriptor.parentPack().filter(packId::equals).isPresent()) {
                parentPack.add(pack);
            }
        }
        return new DirectDependents<>(dependsOn, parentPack);
    }

    /**
     * Every cycle of a directed graph, one per strongly connected group of its nodes (#556, FR-003), whatever the order
     * of the edges. The first is the cycle the walk that found only one always found, from the first node in the
     * graph's order, so a single fault keeps its detail. Each other group that holds a cycle gives the first cycle that
     * walk finds within the group, the groups in the order of their first node. A cycle is the nodes along it from the
     * first one the walk meets twice. Iterative throughout, so a long chain cannot exhaust the stack, and linear in the
     * edges: the groups are found in one pass (Tarjan), and each group is walked once.
     *
     * @param graph each node's successors, in a stable order; a node with no entry has none
     * @param <N> the node type
     * @return the cycles, the first one first, then one per other group; empty when the graph is acyclic
     */
    public static <N> List<List<N>> cyclesIn(Map<N, List<N>> graph) {
        Objects.requireNonNull(graph, "graph");
        Optional<List<N>> first = firstCycle(graph);
        if (first.isEmpty()) {
            return List.of();
        }
        List<List<N>> cycles = new ArrayList<>(List.of(first.get()));
        for (Set<N> group : cyclicGroups(graph)) {
            if (group.contains(first.get().getFirst())) {
                continue;
            }
            Map<N, List<N>> within = new LinkedHashMap<>();
            graph.forEach((node, successors) -> {
                if (group.contains(node)) {
                    within.put(node, successors.stream().filter(group::contains).toList());
                }
            });
            cycles.add(firstCycle(within).orElseThrow(() -> new IllegalStateException("a cyclic group holds a cycle")));
        }
        return List.copyOf(cycles);
    }

    /**
     * The enhancers none of whose {@code enhances} chains reaches an artifact that enhances nothing (A8): an enhancement
     * never stands alone, and a chain that ends at an artifact nothing holds ends nowhere. Grounding spreads from each
     * edge onto a held non-enhancer to its enhancers, one pass over the reverse edges; an enhancer that is on, or
     * reaches, an excused node (one of a reported {@code enhances} cycle) is that cycle's fault and is not found here.
     *
     * @param enhancers every node that enhances, held target or not, in the order the answer keeps
     * @param enhances each enhancer's held targets
     * @param excused the nodes already reported, with every enhancer that reaches them
     * @param <N> the node type
     * @return the ungrounded enhancers, in the order of {@code enhancers}
     */
    public static <N> List<N> ungrounded(Collection<N> enhancers, Map<N, List<N>> enhances, Collection<N> excused) {
        Objects.requireNonNull(enhancers, "enhancers");
        Objects.requireNonNull(enhances, "enhances");
        Objects.requireNonNull(excused, "excused");
        Set<N> nodes = new LinkedHashSet<>(enhancers);
        Map<N, List<N>> enhancedBy = new LinkedHashMap<>();
        Set<N> onHeldNonEnhancer = new LinkedHashSet<>();
        enhances.forEach((source, targets) -> targets.forEach(target -> {
            enhancedBy.computeIfAbsent(target, ignored -> new ArrayList<>()).add(source);
            if (!nodes.contains(target)) {
                onHeldNonEnhancer.add(source);
            }
        }));
        Function<N, List<N>> back = node -> enhancedBy.getOrDefault(node, List.of());
        Set<N> grounded = reachable(onHeldNonEnhancer, back);
        Set<N> reachesExcused = reachable(excused, back);
        return nodes.stream()
                .filter(node -> !grounded.contains(node) && !reachesExcused.contains(node))
                .toList();
    }

    /** The first cycle an iterative depth-first walk from each node, in the graph's order, meets. */
    private static <N> Optional<List<N>> firstCycle(Map<N, List<N>> graph) {
        Set<N> finished = new LinkedHashSet<>();
        for (N start : graph.keySet()) {
            if (finished.contains(start)) {
                continue;
            }
            List<N> path = new ArrayList<>(List.of(start));
            Set<N> onPath = new LinkedHashSet<>(path);
            Deque<Iterator<N>> next = new ArrayDeque<>(
                    List.of(graph.getOrDefault(start, List.of()).iterator()));
            while (!next.isEmpty()) {
                if (!next.peek().hasNext()) {
                    next.pop();
                    N done = path.removeLast();
                    onPath.remove(done);
                    finished.add(done);
                    continue;
                }
                N step = next.peek().next();
                if (onPath.contains(step)) {
                    return Optional.of(List.copyOf(path.subList(path.indexOf(step), path.size())));
                }
                if (!finished.contains(step) && graph.containsKey(step)) {
                    path.add(step);
                    onPath.add(step);
                    next.push(graph.getOrDefault(step, List.of()).iterator());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The strongly connected groups of a graph that hold a cycle (more than one node, or a node that is its own
     * successor), found by an iterative Tarjan pass, in the order of each group's first node in the graph.
     */
    private static <N> List<Set<N>> cyclicGroups(Map<N, List<N>> graph) {
        Tarjan<N> pass = new Tarjan<>(graph);
        graph.keySet().forEach(pass::from);
        Map<N, Integer> position = new LinkedHashMap<>();
        for (N node : graph.keySet()) {
            position.put(node, position.size());
        }
        List<Set<N>> groups = new ArrayList<>(pass.groups);
        groups.sort(Comparator.comparingInt(group -> group.stream()
                .mapToInt(node -> position.getOrDefault(node, Integer.MAX_VALUE))
                .min()
                .orElse(Integer.MAX_VALUE)));
        return groups;
    }

    /** One iterative Tarjan pass over a graph: each node is visited once, each edge followed once. */
    private static final class Tarjan<N> {
        private final Map<N, List<N>> graph;
        private final Map<N, Integer> index = new LinkedHashMap<>();
        private final Map<N, Integer> low = new LinkedHashMap<>();
        private final Deque<N> stack = new ArrayDeque<>();
        private final Set<N> onStack = new LinkedHashSet<>();
        private final Deque<N> nodes = new ArrayDeque<>();
        private final Deque<Iterator<N>> successors = new ArrayDeque<>();
        private final List<Set<N>> groups = new ArrayList<>();

        Tarjan(Map<N, List<N>> graph) {
            this.graph = graph;
        }

        void from(N root) {
            if (index.containsKey(root)) {
                return;
            }
            enter(root);
            while (!nodes.isEmpty()) {
                N node = nodes.peek();
                Iterator<N> next = successors.peek();
                if (next.hasNext()) {
                    follow(node, next.next());
                } else {
                    leave(node);
                }
            }
        }

        private void follow(N node, N next) {
            if (!index.containsKey(next)) {
                enter(next);
            } else if (onStack.contains(next)) {
                low.put(node, Math.min(at(low, node), at(index, next)));
            }
        }

        private void enter(N node) {
            index.put(node, index.size());
            low.put(node, at(index, node));
            stack.push(node);
            onStack.add(node);
            nodes.push(node);
            successors.push(graph.getOrDefault(node, List.of()).iterator());
        }

        private void leave(N node) {
            nodes.pop();
            successors.pop();
            if (!nodes.isEmpty()) {
                N parent = nodes.peek();
                low.put(parent, Math.min(at(low, parent), at(low, node)));
            }
            if (at(low, node) != at(index, node)) {
                return;
            }
            Set<N> group = new LinkedHashSet<>();
            N member;
            do {
                member = stack.pop();
                onStack.remove(member);
                group.add(member);
            } while (!member.equals(node));
            if (group.size() > 1 || graph.getOrDefault(node, List.of()).contains(node)) {
                groups.add(group);
            }
        }

        private static <N> int at(Map<N, Integer> numbers, N node) {
            return Objects.requireNonNull(numbers.get(node), "a visited node");
        }
    }
}
