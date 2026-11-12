package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.EnhancementCompose;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
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

    /**
     * A rule a closure breaks.
     *
     * @param code the refusal code
     * @param detail what is wrong, naming the artifacts
     * @param packs the packs involved, the one that breaks the rule first
     */
    record Violation(RefusalCode code, String detail, List<PackUnderResolution> packs) {}

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
    private @Nullable Map<String, MutabilityPolicySource> scopedPolicies;

    private ImpactRelations(OfferingReadService.ComposedClosure composed) {
        this.composed = composed;
        this.packs = composed.packs();
        packs.forEach(pack -> byConfigured.put(pack.configured().id(), pack));
    }

    /**
     * The first rule the closure breaks, in a fixed order.
     *
     * @param composed a composed closure with the facts it was judged on
     * @return the violation, or empty when the closure is sound
     */
    static Optional<Violation> check(OfferingReadService.ComposedClosure composed) {
        ImpactRelations relations = new ImpactRelations(composed);
        relations.collect();
        if (relations.edges.isEmpty()) {
            return Optional.empty();
        }
        List<Supplier<Optional<Violation>>> rules = List.of(
                relations::selfOverride,
                relations::conflicting,
                relations::notPermitted,
                relations::lockedStepEnhanced,
                relations::overrideCycle,
                relations::enhancesCycle,
                relations::ungroundedEnhancer);
        for (Supplier<Optional<Violation>> rule : rules) {
            Optional<Violation> broken = rule.get();
            if (broken.isPresent()) {
                return broken;
            }
        }
        return Optional.empty();
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

    /** An {@code overrides} edge whose target resolves to its own source (A12). */
    private Optional<Violation> selfOverride() {
        return edges.stream()
                .filter(edge -> edge.is(RelationType.OVERRIDES))
                .filter(edge -> edge.target().filter(edge.source()::equals).isPresent())
                .findFirst()
                .map(edge -> violation(
                        RefusalCode.SELF_OVERRIDE,
                        "the overrides edge of " + edge.source() + " in pack " + name(edge.source())
                                + " resolves to its own source",
                        edge.writer(),
                        edge.source()));
    }

    /** An artifact that both enhances and overrides one target (A11). */
    private Optional<Violation> conflicting() {
        Set<Pair> enhanced = new HashSet<>();
        edges.stream()
                .filter(edge -> edge.is(RelationType.ENHANCES))
                .forEach(edge -> edge.target().ifPresent(target -> enhanced.add(new Pair(edge.source(), target))));
        return edges.stream()
                .filter(edge -> edge.is(RelationType.OVERRIDES))
                .filter(edge -> edge.target()
                        .filter(target -> enhanced.contains(new Pair(edge.source(), target)))
                        .isPresent())
                .findFirst()
                .map(edge -> violation(
                        RefusalCode.CONFLICTING_RELATIONS,
                        edge.source() + " in pack " + name(edge.source()) + " both enhances and overrides "
                                + edge.target().orElseThrow(),
                        edge.writer(),
                        edge.source(),
                        edge.target().orElseThrow()));
    }

    /**
     * An {@code overrides} edge onto a target that is not open (A6): the level is the strictest any pack of the closure
     * declares for it, each policy scoped to what its pack and the packs it builds on hold (A15), the source's own pack
     * left out as it is by the write gate (B8).
     */
    private Optional<Violation> notPermitted() {
        Map<String, MutabilityPolicySource> scoped = scoped();
        Map<PackIdentifier, PerPackPolicy> judging = new HashMap<>();
        for (Edge edge : edges) {
            if (!edge.is(RelationType.OVERRIDES) || edge.target().isEmpty()) {
                continue;
            }
            Place target = edge.target().get();
            PerPackPolicy policy = judging.computeIfAbsent(edge.source().pack(), pack -> judgedWithout(pack, scoped));
            PerPackPolicy.Level level =
                    policy.levelIn(List.copyOf(policy.byPack().keySet()), target.urn());
            if (level.level() != MutabilityLevel.OPEN) {
                return Optional.of(notOpen(edge, target, level));
            }
        }
        return Optional.empty();
    }

    /**
     * An enhancement of a step whose mission type is locked (C22 and C25 as amended, OD-2 (A)): a step takes its mission
     * type's level, judged along the closure with the enhancing pack's own policy left out, as {@link #notPermitted}
     * leaves out the source's pack. The read refuses the same closure {@code pack_unreadable} from the merge.
     */
    private Optional<Violation> lockedStepEnhanced() {
        return EnhancementCompose.lockedTargets(
                        packs, member -> composed.factsOf(member).edges(), new PerPackPolicy(scoped()))
                .stream()
                .findFirst()
                .map(locked -> {
                    List<PackUnderResolution> involved = new ArrayList<>(List.of(locked.enhancingPack()));
                    locked.lockedBy()
                            .filter(pack -> !pack.equals(locked.enhancingPack()))
                            .ifPresent(involved::add);
                    return new Violation(RefusalCode.OVERRIDE_NOT_PERMITTED, locked.detail(), involved);
                });
    }

    /** Each closure pack's policy, scoped to what it and the packs it builds on hold (A15), read once. */
    private Map<String, MutabilityPolicySource> scoped() {
        if (scopedPolicies == null) {
            scopedPolicies = PackMutabilityPolicy.scopedPolicies(
                    packs, member -> composed.factsOf(member).policy());
        }
        return scopedPolicies;
    }

    private PerPackPolicy judgedWithout(PackIdentifier pack, Map<String, MutabilityPolicySource> scoped) {
        Map<String, MutabilityPolicySource> others = new LinkedHashMap<>(scoped);
        others.remove(packOf(pack).descriptor().packId());
        return new PerPackPolicy(others);
    }

    private Violation notOpen(Edge edge, Place target, PerPackPolicy.Level level) {
        List<PackUnderResolution> involved =
                new ArrayList<>(List.of(packOf(edge.source().pack())));
        level.setBy().flatMap(this::byDescriptorPackId).ifPresent(involved::add);
        return new Violation(
                RefusalCode.OVERRIDE_NOT_PERMITTED,
                "the overrides edge of " + edge.source() + " in pack " + name(edge.source()) + " is onto " + target
                        + ", which is " + level.level().token() + along(level)
                        + "; only an open artifact can be overridden",
                involved);
    }

    private static String along(PerPackPolicy.Level level) {
        return level.setBy().map(setBy -> " by the pack " + setBy).orElse("");
    }

    private Optional<PackUnderResolution> byDescriptorPackId(String packId) {
        return packs.stream()
                .filter(pack -> pack.descriptor().packId().equals(packId))
                .findFirst();
    }

    // ---- C28 and A8 ---------------------------------------------------------------------------------------------

    private Optional<Violation> overrideCycle() {
        return cycleOf(RelationType.OVERRIDES)
                .map(cycle -> violation(
                        RefusalCode.OVERRIDE_CYCLE,
                        "overrides edges form a cycle: " + describe(cycle),
                        cycle.toArray(Place[]::new)));
    }

    private Optional<Violation> enhancesCycle() {
        return cycleOf(RelationType.ENHANCES)
                .map(cycle -> violation(
                        RefusalCode.ENHANCES_CYCLE,
                        "enhances edges form a cycle: " + describe(cycle),
                        cycle.toArray(Place[]::new)));
    }

    /**
     * An enhancer none of whose {@code enhances} chains reaches an artifact that enhances nothing (A8): an enhancement
     * never stands alone, and a chain that ends at an artifact nothing holds ends nowhere. The graph is acyclic here, so
     * grounding spreads from each edge onto a held non-enhancer to its enhancers, one pass over the reverse edges.
     */
    private Optional<Violation> ungroundedEnhancer() {
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
        return enhancers.stream()
                .filter(enhancer -> !grounded.contains(enhancer))
                .findFirst()
                .map(enhancer -> violation(
                        RefusalCode.ENHANCES_CYCLE,
                        enhancer + " in pack " + name(enhancer)
                                + " enhances, and no chain of enhances edges from it reaches an artifact that enhances"
                                + " nothing",
                        enhancer));
    }

    private Map<Place, List<Place>> graphOf(RelationType type) {
        Map<Place, List<Place>> graph = new LinkedHashMap<>();
        for (Edge edge : edges) {
            if (edge.is(type)) {
                edge.target()
                        .ifPresent(target -> graph.computeIfAbsent(edge.source(), ignored -> new ArrayList<>())
                                .add(target));
            }
        }
        return graph;
    }

    /** A cycle among the edges of one relation, as the places along it, found by an iterative depth-first walk. */
    private Optional<List<Place>> cycleOf(RelationType type) {
        Map<Place, List<Place>> graph = graphOf(type);
        Set<Place> finished = new HashSet<>();
        for (Place start : graph.keySet()) {
            Optional<List<Place>> cycle = walkFrom(start, graph, finished);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        return Optional.empty();
    }

    private static Optional<List<Place>> walkFrom(Place start, Map<Place, List<Place>> graph, Set<Place> finished) {
        if (finished.contains(start)) {
            return Optional.empty();
        }
        List<Place> path = new ArrayList<>(List.of(start));
        Deque<Iterator<Place>> next =
                new ArrayDeque<>(List.of(graph.getOrDefault(start, List.of()).iterator()));
        Set<Place> onPath = new HashSet<>(path);
        while (!next.isEmpty()) {
            if (!next.peek().hasNext()) {
                next.pop();
                Place done = path.removeLast();
                onPath.remove(done);
                finished.add(done);
                continue;
            }
            Place step = next.peek().next();
            if (onPath.contains(step)) {
                return Optional.of(List.copyOf(path.subList(path.indexOf(step), path.size())));
            }
            if (!finished.contains(step) && graph.containsKey(step)) {
                path.add(step);
                onPath.add(step);
                next.push(graph.getOrDefault(step, List.of()).iterator());
            }
        }
        return Optional.empty();
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

    /** A violation naming the packs of the places, each once. */
    private Violation violation(RefusalCode code, String detail, Place... places) {
        return build(code, detail, Optional.empty(), places);
    }

    /** A violation naming first the writing pack, then the packs of the places, each once. */
    private Violation violation(RefusalCode code, String detail, PackUnderResolution writer, Place... places) {
        return build(code, detail, Optional.of(writer), places);
    }

    private Violation build(RefusalCode code, String detail, Optional<PackUnderResolution> writer, Place... places) {
        Set<PackUnderResolution> involved = new LinkedHashSet<>();
        writer.ifPresent(involved::add);
        for (Place place : places) {
            involved.add(packOf(place.pack()));
        }
        return new Violation(code, detail, List.copyOf(involved));
    }
}
