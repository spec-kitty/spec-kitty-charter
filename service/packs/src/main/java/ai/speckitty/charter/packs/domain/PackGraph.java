package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.Descent;
import ai.speckitty.charter.kernel.domain.GraphWalker;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.domain.graph.EdgeKind;
import ai.speckitty.charter.packs.domain.graph.EdgeNode;
import ai.speckitty.charter.packs.domain.graph.PackGraphNode;
import ai.speckitty.charter.packs.domain.graph.PackNode;
import ai.speckitty.charter.packs.domain.graph.Scope;
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
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;

/**
 * Where the pack graph is walked (ADR-0082 decisions 1 and 2, #637): which edges a pack builds on ({@link #buildsOn}),
 * the descent along them ({@link #compositionClosure}, {@link #ancestorsOf}) and back up them
 * ({@link #dependentsClosure}, {@link #directDependents}), the cycles and strongly connected groups of a graph
 * ({@link #cyclesIn}), and the grounding of {@code enhances} chains ({@link #ungrounded}). No class of the application
 * layer walks the pack graph with a work queue or reads a descriptor's edges itself, but two read-model projections; it
 * hands this class what it read and takes the answer ({@code PackGraphWalkBoundaryTest}). The composition resolver's
 * walks are here too: the {@code depends_on}-only closure ({@link #dependsOnClosure}), the {@code parent_pack} line
 * ({@link #onParentLine}), the dependency order ({@link #topologicalOrder}) and the cycles it leaves
 * ({@link #leftoverCycles}).
 *
 * <p>A graph built over a closure's packs ({@link #of}) is also the one driver of the kernel's visitor contract over
 * the pack graph ({@link #walk}): a {@link Scope} narrows it, and the domain's {@link Walk} mode decides which edges it
 * emits. Each configured pack appears once, though several packs may share a descriptor {@code pack_id}. Such a graph
 * memoizes each pack's facts, so it is not thread-safe: one thread builds and walks it.
 *
 * <p>Every walk is iterative, so a long chain cannot exhaust the stack, and every answer is in a stable order: the
 * order of the inputs, never the hash order of a node (the determinism guard of the architecture page). A graph given
 * as a map is walked in the map's iteration order, so a caller passes an insertion-ordered map, never a hash map.
 */
public final class PackGraph {

    /**
     * What a walk emits beyond the packs it visits, decided by the domain and never by the caller: the rule factory
     * ({@code PackRulesets}) is to hand each check out with its mode.
     */
    public enum Walk {
        /** The packs alone; no pack's facts are read. */
        PACKS,
        /**
         * Each pack, then its {@code overrides}, {@code enhances}, {@code specializes_from} and own step-link edges.
         */
        RELATIONS
    }

    private final List<PackNode> packs;
    private final Map<String, List<PackNode>> byPackId = new LinkedHashMap<>();
    private final Map<PackIdentifier, PackNode> byConfigured = new LinkedHashMap<>();
    private final Function<PackIdentifier, PackCompositionFacts> factsOf;
    private final Map<PackIdentifier, PackCompositionFacts> facts = new LinkedHashMap<>();

    private PackGraph(List<PackNode> packs, Function<PackIdentifier, PackCompositionFacts> factsOf) {
        this.packs = List.copyOf(packs);
        this.factsOf = factsOf;
        for (PackNode pack : this.packs) {
            byPackId.computeIfAbsent(pack.descriptor().packId(), ignored -> new ArrayList<>())
                    .add(pack);
            if (byConfigured.put(pack.pack(), pack) != null) {
                throw new IllegalArgumentException(
                        "the pack " + pack.pack().value() + " is configured twice in one graph");
            }
        }
    }

    /**
     * The pack graph of a set of packs: the packs in the caller's merge order, and where each pack's composition
     * facts are read. Facts are read lazily, once per pack, and only by a walk whose mode needs them. The graph holds
     * that memo, so it is not thread-safe; build one per judge.
     *
     * @param packsInMergeOrder the configured packs with their descriptors, in the order a walk visits them
     * @param factsOf each pack's composition facts, by configured identifier
     * @return the graph
     * @throws IllegalArgumentException when two packs are configured under the same identifier
     */
    public static PackGraph of(
            List<PackNode> packsInMergeOrder, Function<PackIdentifier, PackCompositionFacts> factsOf) {
        Objects.requireNonNull(packsInMergeOrder, "packsInMergeOrder");
        Objects.requireNonNull(factsOf, "factsOf");
        return new PackGraph(packsInMergeOrder, factsOf);
    }

    /**
     * The pack graph of a composed closure for a walk that reads no pack's facts (the {@link Walk#PACKS} mode): the
     * closure's packs in its merge order. The facts function throws by design, so a walk that reads facts it should not
     * is a defect, never a silent read.
     *
     * @param closure the composed closure, in merge order, each configured pack once
     * @return the graph
     * @throws IllegalArgumentException when two packs are configured under the same identifier
     */
    public static PackGraph ofPacks(List<PackCompositionResolver.PackUnderResolution> closure) {
        Objects.requireNonNull(closure, "closure");
        return of(
                closure.stream()
                        .map(member -> new PackNode(member.configured(), member.descriptor()))
                        .toList(),
                pack -> {
                    throw new IllegalStateException("the packs walk reads no pack's facts: " + pack.value());
                });
    }

    /**
     * The pack graph of a composed closure whose walks read each pack's facts: the closure's packs in its merge order,
     * each pack's facts the ones the closure was composed with.
     *
     * @param closure the composed closure, in merge order, each configured pack once
     * @param factsOf each pack's composition facts
     * @return the graph
     * @throws IllegalArgumentException when two packs are configured under the same identifier
     */
    public static PackGraph ofPacks(
            List<PackCompositionResolver.PackUnderResolution> closure,
            Function<PackCompositionResolver.PackUnderResolution, PackCompositionFacts> factsOf) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(factsOf, "factsOf");
        Map<PackIdentifier, PackCompositionResolver.PackUnderResolution> members = new LinkedHashMap<>();
        closure.forEach(member -> members.put(member.configured().id(), member));
        return of(
                closure.stream()
                        .map(member -> new PackNode(member.configured(), member.descriptor()))
                        .toList(),
                pack -> factsOf.apply(Objects.requireNonNull(members.get(pack), "a pack of the closure")));
    }

    /**
     * Walks the composition closure of the scope's roots (ADR-0082 decision 2): every pack whose {@code pack_id} the
     * closure holds, once each, in the graph's order. When packs share a {@code pack_id}, each is visited and the
     * closure follows what each of them builds on. After each pack come the elements the mode emits for it, in declared
     * order. The visitor is handed only what the scope selects; an element it does not select is still walked through,
     * so its pack's elements are visited. {@link Descent#SKIP} on a pack skips its elements; on an element it is
     * {@link Descent#CONTINUE}, since nothing is walked beneath an element. {@link Descent#STOP} ends the walk. The
     * walk is iterative and visits each pack once, so a cycle ends and a long chain cannot exhaust the stack.
     *
     * @param scope the roots and the selection
     * @param walk the mode: which elements are emitted beyond the packs
     * @param visitor what is called at each selected element
     * @throws IllegalArgumentException when a root names no pack of the graph
     */
    public void walk(Scope scope, Walk walk, GraphWalker<? super PackGraphNode, ? super EdgeNode> visitor) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(walk, "walk");
        Objects.requireNonNull(visitor, "visitor");
        List<String> rootPackIds = new ArrayList<>();
        for (PackIdentifier root : scope.roots()) {
            PackNode node = byConfigured.get(root);
            if (node == null) {
                throw new IllegalArgumentException("no pack of the graph is configured as " + root.value());
            }
            rootPackIds.add(node.descriptor().packId());
        }
        Set<String> closure = ancestorsOf(
                rootPackIds,
                packId -> byPackId.getOrDefault(packId, List.of()).stream()
                        .flatMap(holder -> buildsOn(holder.descriptor()).stream())
                        .distinct()
                        .toList());
        for (PackNode pack : packs) {
            if (!closure.contains(pack.descriptor().packId())) {
                continue;
            }
            if (scope.selection().test(pack)) {
                Descent descent = visitor.onNode(pack);
                if (descent == Descent.STOP) {
                    return;
                }
                if (descent == Descent.SKIP) {
                    continue;
                }
            }
            if (!emit(pack, walk, scope, visitor)) {
                return;
            }
        }
    }

    /** The elements the mode emits for one pack; {@code false} when the visitor stopped the walk. */
    private boolean emit(
            PackNode pack, Walk walk, Scope scope, GraphWalker<? super PackGraphNode, ? super EdgeNode> visitor) {
        for (PackGraphNode element : elementsOf(pack, walk)) {
            if (!scope.selection().test(element)) {
                continue;
            }
            Descent descent = element instanceof EdgeNode edge ? visitor.onRelationship(edge) : visitor.onNode(element);
            if (descent == Descent.STOP) {
                return false;
            }
        }
        return true;
    }

    private List<PackGraphNode> elementsOf(PackNode pack, Walk walk) {
        PackIdentifier id = pack.pack();
        List<PackGraphNode> elements = new ArrayList<>();
        if (walk == Walk.RELATIONS) {
            factsOf(id)
                    .edges()
                    .forEach(edge -> relationKind(pack, edge)
                            .ifPresent(kind -> elements.add(new EdgeNode(kind, id, new EdgeNode.Related(edge)))));
        }
        return elements;
    }

    /** The kind of a typed edge a relations walk follows; empty for an edge it does not follow. */
    private static Optional<EdgeKind> relationKind(PackNode pack, Relationship edge) {
        return switch (edge.type()) {
            case OVERRIDES -> Optional.of(EdgeKind.OVERRIDES);
            case ENHANCES -> Optional.of(EdgeKind.ENHANCES);
            case SPECIALIZES_FROM -> Optional.of(EdgeKind.SPECIALIZES);
            case REQUIRES ->
                MissionStepLinkRule.isOwnStepLink(pack.descriptor().name(), edge.from(), edge)
                        ? Optional.of(EdgeKind.STEP_LINK)
                        : Optional.empty();
            default -> Optional.empty();
        };
    }

    private PackCompositionFacts factsOf(PackIdentifier pack) {
        return facts.computeIfAbsent(pack, factsOf);
    }

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
     * The starts and every pack they build on, transitively, each once, in breadth-first first-seen order. The caller
     * supplies the edges, keyed its own way: either {@link #buildsOn} of each pack's descriptor, or the builds-on edges a
     * composed closure already resolved ({@code ClosureView#parentOf}). The domain owns the descent; the caller owns only
     * how a key maps to its edges, and gives none for a pack it does not hold. A cycle terminates.
     *
     * @param starts the packs the descent starts at, in order
     * @param buildsOn each pack's builds-on edges, as the caller keys its packs
     * @param <K> how the caller keys a pack
     * @return the starts, then each pack they build on in the order first met; a key with no pack is included, with no
     *     descent from it
     */
    public static <K> Set<K> ancestorsOf(Collection<K> starts, Function<K, ? extends Collection<K>> buildsOn) {
        return reachable(starts, buildsOn);
    }

    /**
     * {@link #ancestorsOf(Collection, Function)} over packs the caller holds in a map: a key the map does not hold is
     * included and not descended from.
     *
     * @param starts the packs the descent starts at, in order
     * @param packs the packs the caller holds, by its key
     * @param buildsOn a held pack's builds-on edges
     * @param <K> how the caller keys a pack
     * @param <V> what the caller holds for a pack
     * @return as {@link #ancestorsOf(Collection, Function)}
     */
    public static <K, V> Set<K> ancestorsOf(
            Collection<K> starts, Map<K, V> packs, Function<V, ? extends Collection<K>> buildsOn) {
        Objects.requireNonNull(packs, "packs");
        Objects.requireNonNull(buildsOn, "buildsOn");
        return reachable(starts, key -> {
            V pack = packs.get(key);
            return pack == null ? List.of() : buildsOn.apply(pack);
        });
    }

    /** Every node reachable from the starts, the starts included, in breadth-first first-seen order, each once. */
    private static <N> Set<N> reachable(Collection<N> starts, Function<N, ? extends Collection<N>> successors) {
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
     * compose. A target no descriptor is found for is recorded but not descended from: the walk tolerates it,
     * and {@link PackCompositionResolver#resolve} refuses it. A cycle terminates, since each id is walked once.
     *
     * @param rootPackIds the descriptor {@code pack_id}s of the roots
     * @param descriptorOf the descriptor the walk descends into for a {@code pack_id}; empty for one no pack declares
     * @return the {@code pack_id}s of the closure, missing targets included
     */
    public static Set<String> compositionClosure(
            Collection<String> rootPackIds, Function<String, Optional<PackDescriptor>> descriptorOf) {
        Objects.requireNonNull(rootPackIds, "rootPackIds");
        Objects.requireNonNull(descriptorOf, "descriptorOf");
        return reachable(
                rootPackIds,
                packId -> descriptorOf.apply(packId).map(PackGraph::buildsOn).orElse(List.of()));
    }

    /**
     * A pack and every pack that reaches it over {@code depends_on} or {@code parent_pack}, transitively: the packs
     * whose composition closure holds it. The reverse index is one pass over the descriptors.
     *
     * @param packId the descriptor {@code pack_id} of the pack built on
     * @param descriptors the descriptors that may build on it
     * @return the {@code pack_id} itself first, then each pack building on it in first-seen order, which follows the
     *     order of {@code descriptors}
     */
    public static Set<String> dependentsClosure(String packId, Collection<PackDescriptor> descriptors) {
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
     * {@code pack_id}. A pack that names it both ways is in both lists; one that names it twice in {@code depends_on}
     * is listed once.
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
     * The pack and every pack it reaches through {@code depends_on} only, by {@code pack_id}: the packs a qualified
     * reference may name (FR-015), and the packs that may hold the step a step enhancement names (C37). It is not the
     * {@linkplain #compositionClosure composition closure}, which also follows {@code parent_pack}. The descent takes
     * the last pending target first, so a pack's dependencies are met last declared first.
     *
     * @param packId the descriptor {@code pack_id} of the pack the walk starts at
     * @param descriptorOf the descriptor of a {@code pack_id}; empty for one no pack declares
     * @return the {@code pack_id}s reached, the start included; a target no pack declares is included and not descended
     *     from
     */
    public static Set<String> dependsOnClosure(String packId, Function<String, Optional<PackDescriptor>> descriptorOf) {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(descriptorOf, "descriptorOf");
        Set<String> reached = new LinkedHashSet<>();
        // A LIFO stack on purpose: the resolver met dependencies last declared first, and
        // dependsOnClosureIsLifoOverDependsOnOnly pins that order.
        List<String> pending = new ArrayList<>(List.of(packId));
        while (!pending.isEmpty()) {
            String next = pending.remove(pending.size() - 1);
            Optional<PackDescriptor> pack = descriptorOf.apply(next);
            if (reached.add(next) && pack.isPresent()) {
                pack.get().dependencies().forEach(dependency -> pending.add(dependency.packId()));
            }
        }
        return reached;
    }

    /**
     * Whether one pack is on the other's {@code parent_pack} line, either way.
     *
     * @param first one pack
     * @param second the other pack
     * @param descriptorOf the descriptor of a {@code pack_id}; empty for one no pack declares
     * @return {@code true} when either extends the other along its parent line
     */
    public static boolean related(
            PackDescriptor first, PackDescriptor second, Function<String, Optional<PackDescriptor>> descriptorOf) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return onParentLine(first, second.packId(), descriptorOf) || onParentLine(second, first.packId(), descriptorOf);
    }

    /**
     * Whether a pack's {@code parent_pack} line reaches a pack: its parent, its parent's parent, and so on. The walk
     * visits each pack once, so a parent cycle ends it.
     *
     * @param child the pack whose line is walked
     * @param ancestorPackId the descriptor {@code pack_id} looked for
     * @param descriptorOf the descriptor of a {@code pack_id}; empty for one no pack declares
     * @return {@code true} when the line reaches the pack
     */
    public static boolean onParentLine(
            PackDescriptor child, String ancestorPackId, Function<String, Optional<PackDescriptor>> descriptorOf) {
        Objects.requireNonNull(child, "child");
        Objects.requireNonNull(ancestorPackId, "ancestorPackId");
        Objects.requireNonNull(descriptorOf, "descriptorOf");
        Set<String> walked = new LinkedHashSet<>();
        Optional<String> at = child.parentPack();
        while (at.isPresent() && walked.add(at.get())) {
            if (at.get().equals(ancestorPackId)) {
                return true;
            }
            at = descriptorOf.apply(at.get()).flatMap(PackDescriptor::parentPack);
        }
        return false;
    }

    /**
     * The order Kahn's pass places packs in, and the packs it could not place.
     *
     * @param placed the packs placed, each after the packs it depends on
     * @param leftover the packs no order places, in input order: those on a dependency cycle, those downstream of one,
     *     and those with no entry in the dependencies
     * @param <P> the pack type
     */
    public record Ordering<P>(List<P> placed, List<P> leftover) {

        /**
         * Copies the lists.
         *
         * @param placed the packs placed
         * @param leftover the packs left
         */
        public Ordering {
            placed = List.copyOf(placed);
            leftover = List.copyOf(leftover);
        }
    }

    /**
     * Orders packs so each comes after the packs it depends on (Kahn's pass). Among the packs ready at once, the one
     * first in {@code stable} order is taken, not the first that became ready: a pack whose dependency is met late
     * would otherwise land after a dependency-free pack of a nearer level.
     *
     * @param packs the packs, each once
     * @param idOf a pack's {@code pack_id}
     * @param dependenciesById each pack's dependencies within the set, by {@code pack_id}, in declared order
     * @param stable the order ready packs are taken in
     * @param byId the pack of a {@code pack_id} of the set
     * @param <P> the pack type
     * @return the packs placed, each after the packs it depends on; and the packs left, in the order of {@code packs}
     */
    public static <P> Ordering<P> topologicalOrder(
            List<P> packs,
            Function<P, String> idOf,
            Map<String, List<String>> dependenciesById,
            Comparator<? super P> stable,
            Function<String, P> byId) {
        Objects.requireNonNull(packs, "packs");
        Objects.requireNonNull(idOf, "idOf");
        Objects.requireNonNull(dependenciesById, "dependenciesById");
        Objects.requireNonNull(stable, "stable");
        Objects.requireNonNull(byId, "byId");
        Map<String, Integer> outstanding = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>();
        dependenciesById.keySet().forEach(id -> dependents.put(id, new ArrayList<>()));
        dependenciesById.forEach((packId, dependencies) -> {
            outstanding.put(packId, dependencies.size());
            for (String dependency : dependencies) {
                dependents.computeIfAbsent(dependency, key -> new ArrayList<>()).add(packId);
            }
        });
        PriorityQueue<P> ready = new PriorityQueue<>(stable);
        packs.stream()
                .filter(pack -> {
                    Integer count = outstanding.get(idOf.apply(pack));
                    return count != null && count == 0;
                })
                .forEach(ready::add);
        List<P> ordered = new ArrayList<>(packs.size());
        while (!ready.isEmpty()) {
            P pack = ready.poll();
            ordered.add(pack);
            for (String dependent : dependents.getOrDefault(idOf.apply(pack), List.of())) {
                int remaining = outstanding.merge(dependent, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(byId.apply(dependent));
                }
            }
        }
        Set<String> placed = new LinkedHashSet<>();
        ordered.forEach(pack -> placed.add(idOf.apply(pack)));
        return new Ordering<>(
                ordered,
                packs.stream()
                        .filter(pack -> !placed.contains(idOf.apply(pack)))
                        .toList());
    }

    /**
     * The cycles Kahn's pass left behind, each starting at a pack on it, by {@code pack_id}. What the pass leaves is
     * the cycles and everything downstream of them, and a leftover may be only downstream. Every leftover has an
     * unplaced dependency, so walking from one along its first unplaced dependency must repeat a pack, and the walk
     * from the first repeat back to it is a cycle. The walks start at each leftover in turn, in the order of
     * {@code dependenciesById}; a walk that reaches a pack an earlier walk visited finds no new cycle, so the first
     * cycle is the one the resolver has always refused with. A walk follows one dependency only, so it can pass by a
     * pack that lies on a cycle through another edge: each such pack is then named by the shortest cycle through it,
     * after the walks, so every pack on a cycle is named by at least one. A pack only downstream of a cycle lies on
     * none, and is not named.
     *
     * <p>Precondition: {@code placed} is what {@link #topologicalOrder} placed over the same {@code dependenciesById},
     * so every pack left has an unplaced dependency. Only the composition resolver calls it.
     *
     * @param placed the {@code pack_id}s Kahn's pass placed
     * @param dependenciesById each pack's dependencies within the set, by {@code pack_id}, in declared order
     * @return the cycles, each a list of {@code pack_id}s starting at a pack on it
     * @throws java.util.NoSuchElementException when a pack left has no unplaced dependency (the precondition broken)
     */
    static List<List<String>> leftoverCycles(Set<String> placed, Map<String, List<String>> dependenciesById) {
        Objects.requireNonNull(placed, "placed");
        Objects.requireNonNull(dependenciesById, "dependenciesById");
        Set<String> visited = new LinkedHashSet<>();
        List<List<String>> cycles = new ArrayList<>();
        for (String start : dependenciesById.keySet()) {
            if (placed.contains(start) || visited.contains(start)) {
                continue;
            }
            Set<String> walked = new LinkedHashSet<>();
            String current = start;
            while (!visited.contains(current) && walked.add(current)) {
                current = dependenciesById.getOrDefault(current, List.of()).stream()
                        .filter(id -> !placed.contains(id))
                        .findFirst()
                        .orElseThrow();
            }
            if (walked.contains(current) && !visited.contains(current)) {
                List<String> path = new ArrayList<>(walked);
                cycles.add(List.copyOf(path.subList(path.indexOf(current), path.size())));
            }
            visited.addAll(walked);
        }
        Set<String> named = new LinkedHashSet<>();
        cycles.forEach(named::addAll);
        for (String pack : dependenciesById.keySet()) {
            if (placed.contains(pack) || named.contains(pack)) {
                continue;
            }
            List<String> cycle = shortestCycleThrough(pack, placed, dependenciesById);
            if (!cycle.isEmpty()) {
                cycles.add(cycle);
                named.addAll(cycle);
            }
        }
        return List.copyOf(cycles);
    }

    /**
     * The shortest cycle through one unplaced pack, starting at it: a breadth-first search over the unplaced packs'
     * dependencies, each in its declared order, back to the pack; empty when the pack is on no cycle.
     */
    private static List<String> shortestCycleThrough(
            String pack, Set<String> placed, Map<String, List<String>> dependenciesById) {
        Map<String, String> reachedFrom = new LinkedHashMap<>();
        ArrayDeque<String> pending = new ArrayDeque<>(List.of(pack));
        while (!pending.isEmpty()) {
            String next = pending.removeFirst();
            for (String dependency : dependenciesById.getOrDefault(next, List.of())) {
                if (dependency.equals(pack)) {
                    List<String> cycle = new ArrayList<>();
                    for (String at = next; !at.equals(pack); ) {
                        cycle.addFirst(at);
                        at = Objects.requireNonNull(reachedFrom.get(at), "every pack the search reached has a source");
                    }
                    cycle.addFirst(pack);
                    return List.copyOf(cycle);
                }
                if (!placed.contains(dependency) && !reachedFrom.containsKey(dependency)) {
                    reachedFrom.put(dependency, next);
                    pending.addLast(dependency);
                }
            }
        }
        return List.of();
    }

    /**
     * Every cycle of a directed graph, one per strongly connected group of its nodes (#556, FR-003), whatever the order
     * of the edges. The first is the cycle the walk that found only one always found, from the first node in the
     * graph's order, so a single fault keeps its detail. Each other group that holds a cycle gives the first cycle that
     * walk finds within the group, the groups in the order of their first node. A cycle is the nodes along it from the
     * first one the walk meets twice. Iterative throughout, so a long chain cannot exhaust the stack, and linear in the
     * edges: the groups are found in one pass (Tarjan), and each group is walked once.
     *
     * @param graph each node's successors; the map's iteration order is the graph's order, so an insertion-ordered map,
     *     never a hash map; a node with no entry has none
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
     * The enhancers none of whose {@code enhances} chains reaches an artifact that enhances nothing (A8): an
     * enhancement never stands alone, and a chain that ends at an artifact nothing holds ends nowhere. Grounding
     * spreads from each edge onto a held non-enhancer to its enhancers, one pass over the reverse edges; an enhancer
     * that is on, or reaches, an excused node (one of a reported {@code enhances} cycle) is that cycle's fault and is
     * not found here.
     *
     * @param enhancers every node that enhances, held target or not, in the order the answer keeps
     * @param enhances each enhancer's held targets
     * @param excused the nodes already reported; every enhancer that reaches one is excused too
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
