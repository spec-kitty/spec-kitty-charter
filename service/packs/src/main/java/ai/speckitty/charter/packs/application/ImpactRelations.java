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
import ai.speckitty.charter.packs.domain.PackGraph;
import ai.speckitty.charter.packs.domain.PackOrigins;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import java.util.ArrayList;
import java.util.Collection;
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
 * linear in the edges. The cycles and the grounding are {@link PackGraph}'s walks (ADR-0082, #637), iterative, so a
 * long chain cannot exhaust the stack.
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
    private final Map<PackIdentifier, PackUnderResolution> byConfigured = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final OfferingReadService.ComposedClosure composed;
    private @Nullable Map<PackUlid, MutabilityPolicySource> scopedPolicies;
    private final Set<Place> enhancesCycles = new LinkedHashSet<>();
    private @Nullable Map<Place, List<Place>> enhances;

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
        Set<Pair> enhanced = new LinkedHashSet<>();
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
        Map<PackIdentifier, PerPackPolicy> judging = new LinkedHashMap<>();
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
        return PackGraph.cyclesIn(graphOf(RelationType.OVERRIDES, edge -> !isSelfOverride(edge))).stream()
                .map(cycle -> violation(
                        RefusalCode.OVERRIDE_CYCLE,
                        "overrides edges form a cycle: " + describe(cycle),
                        cycle.toArray(Place[]::new)))
                .toList();
    }

    /** Every cycle of {@code enhances} edges, each once; the places on them are remembered for the grounding rule. */
    private List<ClosureFault> enhancesCycle() {
        List<List<Place>> cycles = PackGraph.cyclesIn(enhancesGraph());
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
     * or reaches, a reported enhances cycle is that cycle's fault and is not found again here. {@link PackGraph} walks
     * the cycles and the grounding (#637).
     */
    private List<ClosureFault> ungroundedEnhancer() {
        Set<Place> enhancers = new LinkedHashSet<>();
        edges.stream().filter(edge -> edge.is(RelationType.ENHANCES)).forEach(edge -> enhancers.add(edge.source()));
        return PackGraph.ungrounded(enhancers, enhancesGraph(), enhancesCycles).stream()
                .map(enhancer -> violation(
                        RefusalCode.ENHANCES_CYCLE,
                        enhancer + " in pack " + name(enhancer)
                                + " enhances, and no chain of enhances edges from it reaches an artifact that enhances"
                                + " nothing",
                        enhancer))
                .toList();
    }

    /** The {@code enhances} edges onto a held target, built once for the cycle and the grounding rules. */
    private Map<Place, List<Place>> enhancesGraph() {
        if (enhances == null) {
            enhances = graphOf(RelationType.ENHANCES, edge -> true);
        }
        return enhances;
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

    /** A fault naming packs of the closure, each as the refusal names a pack, about the first it names. */
    static ClosureFault fault(RefusalCode code, String detail, List<PackUnderResolution> packs) {
        List<RefusalPack> named = packs.stream()
                .distinct()
                .map(pack -> new RefusalPack(
                        pack.descriptorId(),
                        pack.configured().id(),
                        pack.descriptor().name()))
                .toList();
        return ClosureFault.of(PackOrigins.firstOf(named), code, detail, named);
    }

    /** Whether an edge is a self-override: an {@code overrides} edge whose target resolves to its own source. */
    private static boolean isSelfOverride(Edge edge) {
        return edge.is(RelationType.OVERRIDES)
                && edge.target().filter(edge.source()::equals).isPresent();
    }
}
