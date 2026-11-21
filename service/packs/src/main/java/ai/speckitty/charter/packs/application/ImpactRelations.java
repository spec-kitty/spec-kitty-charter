package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.EnhancementCompose;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The relation rules of the registration impact check (IC-15, C28, C36, A6, A8, A11, A12, and the lock a step inherits
 * from its mission type, OD-2 (A)), judged over one composed closure. They are pure checks over the typed edges the
 * packs write ({@code PackCompositionFacts.edges()}), never over {@code GraphWalk}, which sees derived relations only
 * (#397). Each endpoint is resolved to a place, a pack and an artifact, by {@link PackCompositionResolver#holderOf}, the
 * one owner of which pack a reference means; the layer re-pointing is the merge's (IC-07, PR B), which judges
 * {@code self_override} again.
 *
 * <p>One pass collects the {@code enhances} and {@code overrides} edges; each rule then reads that list, so the cost is
 * linear in the edges, and every walk is iterative, so a long chain cannot exhaust the stack.
 */
final class ImpactRelations {

    /** An artifact in the pack that holds it, or is taken to. */
    private record Place(PackIdentifier pack, Urn urn) {

        @Override
        public String toString() {
            return urn.toString();
        }
    }

    /** One {@code enhances} or {@code overrides} edge, its source and, when something holds it, its target. */
    private record Edge(PackUnderResolution writer, Place source, Optional<Place> target, RelationType type) {

        boolean is(RelationType wanted) {
            return type == wanted;
        }
    }

    private record Pair(Place source, Place target) {}

    private final List<PackUnderResolution> packs;
    private final Map<PackIdentifier, PackUnderResolution> byConfigured = new HashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final OfferingReadService.ComposedClosure composed;
    private @Nullable Map<PackUlid, MutabilityPolicySource> scopedPolicies;
    private final Set<Place> enhancesCycles = new HashSet<>();

    private ImpactRelations(OfferingReadService.ComposedClosure composed) {
        this.composed = composed;
        this.packs = composed.packs();
        packs.forEach(pack -> byConfigured.put(pack.configured().id(), pack));
    }

    /**
     * Every rule the closure breaks (#556, FR-003): the rules in a fixed order, each with every fault it finds, in the
     * order of the edges. A fault an earlier one implies is not found again: a self-override is no override cycle, and
     * an enhancer on or leading into a reported enhances cycle is not judged ungrounded.
     *
     * @param composed a composed closure with the facts it was judged on
     * @return the faults, each naming the packs involved; empty when the closure is sound
     */
    static List<ClosureFault> check(OfferingReadService.ComposedClosure composed) {
        ImpactRelations relations = new ImpactRelations(composed);
        relations.collect();
        if (relations.edges.isEmpty()) {
            return List.of();
        }
        List<Supplier<List<ClosureFault>>> rules = List.of(
                relations::selfOverride,
                relations::conflicting,
                relations::notPermitted,
                relations::lockedStepEnhanced,
                relations::overrideCycle,
                relations::enhancesCycle,
                relations::ungroundedEnhancer);
        List<ClosureFault> faults = new ArrayList<>();
        for (Supplier<List<ClosureFault>> rule : rules) {
            faults.addAll(rule.get());
        }
        return List.copyOf(faults);
    }

    // ---- the edges ----------------------------------------------------------------------------------------------

    private void collect() {
        for (PackUnderResolution writer : packs) {
            for (Relationship edge : composed.factsOf(writer).edges()) {
                if (edge.type() == RelationType.ENHANCES || edge.type() == RelationType.OVERRIDES) {
                    sourceOf(writer, edge)
                            .ifPresent(
                                    source -> edges.add(new Edge(writer, source, targetOf(writer, edge), edge.type())));
                }
            }
        }
    }

    /** The place an edge starts at: the writing pack's own artifact, or the holder of the qualified source. */
    private Optional<Place> sourceOf(PackUnderResolution writer, Relationship edge) {
        if (edge.fromPack().isEmpty()) {
            return Optional.of(new Place(writer.configured().id(), edge.from()));
        }
        return holder(writer, edge.fromReference())
                .map(pack -> new Place(pack.configured().id(), edge.from()));
    }

    private Optional<Place> targetOf(PackUnderResolution writer, Relationship edge) {
        return holder(writer, edge.toReference())
                .map(pack -> new Place(pack.configured().id(), edge.to()));
    }

    private Optional<PackUnderResolution> holder(PackUnderResolution writer, PackQualifiedReference reference) {
        return PackCompositionResolver.holderOf(packs, writer.configured().id(), reference);
    }

    // ---- A12, A11, A6 -------------------------------------------------------------------------------------------

    /** Every {@code overrides} edge whose target resolves to its own source (A12). */
    private List<ClosureFault> selfOverride() {
        return edges.stream()
                .filter(ImpactRelations::isSelfOverride)
                .map(edge -> violation(
                        RefusalCode.SELF_OVERRIDE,
                        "the overrides edge of " + edge.source() + " in pack " + name(edge.source())
                                + " resolves to its own source",
                        edge.writer(),
                        edge.source()))
                .toList();
    }

    /** Every artifact that both enhances and overrides one target (A11). */
    private List<ClosureFault> conflicting() {
        Set<Pair> enhanced = new HashSet<>();
        edges.stream()
                .filter(edge -> edge.is(RelationType.ENHANCES))
                .forEach(edge -> edge.target().ifPresent(target -> enhanced.add(new Pair(edge.source(), target))));
        return edges.stream()
                .filter(edge -> edge.is(RelationType.OVERRIDES))
                .filter(edge -> edge.target()
                        .filter(target -> enhanced.contains(new Pair(edge.source(), target)))
                        .isPresent())
                .map(edge -> violation(
                        RefusalCode.CONFLICTING_RELATIONS,
                        edge.source() + " in pack " + name(edge.source()) + " both enhances and overrides "
                                + edge.target().orElseThrow(),
                        edge.writer(),
                        edge.source(),
                        edge.target().orElseThrow()))
                .toList();
    }

    /**
     * Every {@code overrides} edge onto a target that is not open (A6): the level is the strictest any pack of the closure
     * declares for it, each policy scoped to what its pack and the packs it builds on hold (A15), the source's own pack
     * left out as it is by the mutability check (B8).
     */
    private List<ClosureFault> notPermitted() {
        Map<PackUlid, MutabilityPolicySource> scoped = scoped();
        Map<PackIdentifier, PerPackPolicy> judging = new HashMap<>();
        List<ClosureFault> faults = new ArrayList<>();
        for (Edge edge : edges) {
            if (!edge.is(RelationType.OVERRIDES) || edge.target().isEmpty()) {
                continue;
            }
            Place target = edge.target().get();
            PerPackPolicy policy = judging.computeIfAbsent(edge.source().pack(), pack -> judgedWithout(pack, scoped));
            PerPackPolicy.Level level =
                    policy.levelIn(List.copyOf(policy.byPack().keySet()), target.urn());
            if (level.level() != MutabilityLevel.OPEN) {
                faults.add(notOpen(edge, target, level));
            }
        }
        return List.copyOf(faults);
    }

    /**
     * Every enhancement of a step whose mission type is locked (C22 and C25 as amended, OD-2 (A)): a step takes its mission
     * type's level, judged along the closure with the enhancing pack's own policy left out, as {@link #notPermitted}
     * leaves out the source's pack. The read refuses the same closure {@code pack_unreadable} from the merge.
     */
    private List<ClosureFault> lockedStepEnhanced() {
        return EnhancementCompose.lockedTargets(
                        packs, member -> composed.factsOf(member).edges(), new PerPackPolicy(scoped()))
                .stream()
                .map(locked -> {
                    List<PackUnderResolution> involved = new ArrayList<>(List.of(locked.enhancingPack()));
                    locked.lockedBy()
                            .filter(pack -> !pack.equals(locked.enhancingPack()))
                            .ifPresent(involved::add);
                    return fault(RefusalCode.OVERRIDE_NOT_PERMITTED, locked.detail(), involved);
                })
                .toList();
    }

    /** Each closure pack's policy, scoped to what it and the packs it builds on hold (A15), read once. */
    private Map<PackUlid, MutabilityPolicySource> scoped() {
        if (scopedPolicies == null) {
            scopedPolicies = PackMutabilityPolicy.scopedPolicies(
                    packs, member -> composed.factsOf(member).policy());
        }
        return scopedPolicies;
    }

    private PerPackPolicy judgedWithout(PackIdentifier pack, Map<PackUlid, MutabilityPolicySource> scoped) {
        Map<PackUlid, MutabilityPolicySource> others = new LinkedHashMap<>(scoped);
        others.remove(packOf(pack).descriptorId());
        return new PerPackPolicy(others);
    }

    private ClosureFault notOpen(Edge edge, Place target, PerPackPolicy.Level level) {
        List<PackUnderResolution> involved =
                new ArrayList<>(List.of(packOf(edge.source().pack())));
        level.setBy().flatMap(this::byDescriptorPackId).ifPresent(involved::add);
        return fault(
                RefusalCode.OVERRIDE_NOT_PERMITTED,
                "the overrides edge of " + edge.source() + " in pack " + name(edge.source()) + " is onto " + target
                        + ", which is " + level.level().token() + along(level)
                        + "; only an open artifact can be overridden",
                involved);
    }

    private static String along(PerPackPolicy.Level level) {
        return level.setBy().map(setBy -> " by the pack " + setBy.value()).orElse("");
    }

    private Optional<PackUnderResolution> byDescriptorPackId(PackUlid packId) {
        return packs.stream().filter(pack -> pack.descriptorId().equals(packId)).findFirst();
    }

    // ---- C28 and A8 ---------------------------------------------------------------------------------------------

    /** Every cycle of {@code overrides} edges, each once; a self-override is {@link #selfOverride}'s, not a cycle. */
    private List<ClosureFault> overrideCycle() {
        return cyclesIn(graphOf(RelationType.OVERRIDES, edge -> !isSelfOverride(edge))).stream()
                .map(cycle -> violation(
                        RefusalCode.OVERRIDE_CYCLE,
                        "overrides edges form a cycle: " + describe(cycle),
                        cycle.toArray(Place[]::new)))
                .toList();
    }

    /** Every cycle of {@code enhances} edges, each once; the places on them are remembered for the grounding rule. */
    private List<ClosureFault> enhancesCycle() {
        List<List<Place>> cycles = cyclesIn(graphOf(RelationType.ENHANCES, edge -> true));
        cycles.forEach(enhancesCycles::addAll);
        return cycles.stream()
                .map(cycle -> violation(
                        RefusalCode.ENHANCES_CYCLE,
                        "enhances edges form a cycle: " + describe(cycle),
                        cycle.toArray(Place[]::new)))
                .toList();
    }

    /**
     * Every enhancer none of whose {@code enhances} chains reaches an artifact that enhances nothing (A8): an
     * enhancement never stands alone, and a chain that ends at an artifact nothing holds ends nowhere. Grounding spreads
     * from each edge onto a held non-enhancer to its enhancers, one pass over the reverse edges; an enhancer that is on,
     * or reaches, a reported enhances cycle is that cycle's fault and is not found again here.
     */
    private List<ClosureFault> ungroundedEnhancer() {
        Set<Place> enhancers = new LinkedHashSet<>();
        edges.stream().filter(edge -> edge.is(RelationType.ENHANCES)).forEach(edge -> enhancers.add(edge.source()));
        Map<Place, List<Place>> enhancedBy = new HashMap<>();
        Set<Place> grounded = new HashSet<>();
        Deque<Place> spread = new ArrayDeque<>();
        for (Edge edge : edges) {
            Optional<Place> target = edge.target().filter(ignored -> edge.is(RelationType.ENHANCES));
            if (target.isEmpty()) {
                continue;
            }
            enhancedBy
                    .computeIfAbsent(target.get(), ignored -> new ArrayList<>())
                    .add(edge.source());
            if (!enhancers.contains(target.get()) && grounded.add(edge.source())) {
                spread.add(edge.source());
            }
        }
        while (!spread.isEmpty()) {
            for (Place enhancer : enhancedBy.getOrDefault(spread.removeFirst(), List.of())) {
                if (grounded.add(enhancer)) {
                    spread.add(enhancer);
                }
            }
        }
        Set<Place> reachesCycle = new HashSet<>(enhancesCycles);
        Deque<Place> back = new ArrayDeque<>(enhancesCycles);
        while (!back.isEmpty()) {
            for (Place enhancer : enhancedBy.getOrDefault(back.removeFirst(), List.of())) {
                if (reachesCycle.add(enhancer)) {
                    back.add(enhancer);
                }
            }
        }
        return enhancers.stream()
                .filter(enhancer -> !grounded.contains(enhancer) && !reachesCycle.contains(enhancer))
                .map(enhancer -> violation(
                        RefusalCode.ENHANCES_CYCLE,
                        enhancer + " in pack " + name(enhancer)
                                + " enhances, and no chain of enhances edges from it reaches an artifact that enhances"
                                + " nothing",
                        enhancer))
                .toList();
    }

    private Map<Place, List<Place>> graphOf(RelationType type, Predicate<Edge> kept) {
        Map<Place, List<Place>> graph = new LinkedHashMap<>();
        for (Edge edge : edges) {
            if (edge.is(type) && kept.test(edge)) {
                edge.target()
                        .ifPresent(target -> graph.computeIfAbsent(edge.source(), ignored -> new ArrayList<>())
                                .add(target));
            }
        }
        return graph;
    }

    /**
     * Every cycle of a directed graph, one per strongly connected group of its nodes (#556, FR-003), whatever the order
     * of the edges. The first is the cycle the walk that found only one always found, from the first node in the
     * graph's order, so a single fault keeps its detail. Each other group that holds a cycle gives the first cycle that
     * walk finds within the group, the groups in the order of their first node. A cycle is the nodes along it from the
     * first one the walk meets twice. Iterative throughout, so a long chain cannot exhaust the stack, and linear in the
     * edges: the groups are found in one pass (Tarjan), and each group is walked once.
     *
     * @param graph each node's successors; a node with no entry has none
     * @param <N> the node type
     * @return the cycles, the first one first, then one per other group; empty when the graph is acyclic
     */
    static <N> List<List<N>> cyclesIn(Map<N, List<N>> graph) {
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

    /** The first cycle an iterative depth-first walk from each node, in the graph's order, meets. */
    private static <N> Optional<List<N>> firstCycle(Map<N, List<N>> graph) {
        Set<N> finished = new HashSet<>();
        for (N start : graph.keySet()) {
            if (finished.contains(start)) {
                continue;
            }
            List<N> path = new ArrayList<>(List.of(start));
            Set<N> onPath = new HashSet<>(path);
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
        Map<N, Integer> position = new HashMap<>();
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
        private final Map<N, Integer> index = new HashMap<>();
        private final Map<N, Integer> low = new HashMap<>();
        private final Deque<N> stack = new ArrayDeque<>();
        private final Set<N> onStack = new HashSet<>();
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
            Set<N> group = new HashSet<>();
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

    // ---- naming -------------------------------------------------------------------------------------------------

    private static String describe(Collection<Place> cycle) {
        List<String> names = new ArrayList<>(cycle.stream().map(Place::toString).toList());
        names.add(names.getFirst());
        return String.join(" -> ", names);
    }

    private String name(Place place) {
        return packOf(place.pack()).descriptor().name();
    }

    private PackUnderResolution packOf(PackIdentifier pack) {
        return Objects.requireNonNull(byConfigured.get(pack), "a place of the closure");
    }

    /** A fault naming the packs of the places, each once. */
    private ClosureFault violation(RefusalCode code, String detail, Place... places) {
        return build(code, detail, Optional.empty(), places);
    }

    /** A fault naming first the writing pack, then the packs of the places, each once. */
    private ClosureFault violation(RefusalCode code, String detail, PackUnderResolution writer, Place... places) {
        return build(code, detail, Optional.of(writer), places);
    }

    private ClosureFault build(RefusalCode code, String detail, Optional<PackUnderResolution> writer, Place... places) {
        Set<PackUnderResolution> involved = new LinkedHashSet<>();
        writer.ifPresent(involved::add);
        for (Place place : places) {
            involved.add(packOf(place.pack()));
        }
        return fault(code, detail, List.copyOf(involved));
    }

    /** A fault naming packs of the closure, each as the refusal names a pack. */
    static ClosureFault fault(RefusalCode code, String detail, List<PackUnderResolution> packs) {
        return ClosureFault.of(
                code,
                detail,
                packs.stream()
                        .distinct()
                        .map(pack -> new RefusalPack(
                                pack.descriptorId(),
                                pack.configured().id(),
                                pack.descriptor().name()))
                        .toList());
    }

    /** Whether an edge is a self-override: an {@code overrides} edge whose target resolves to its own source. */
    private static boolean isSelfOverride(Edge edge) {
        return edge.is(RelationType.OVERRIDES)
                && edge.target().filter(edge.source()::equals).isPresent();
    }
}
