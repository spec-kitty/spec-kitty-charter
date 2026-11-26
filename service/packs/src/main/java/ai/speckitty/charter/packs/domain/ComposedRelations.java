package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The typed edges of one composed closure, as the relation rules judge them (#639, ADR-0082 slice 4): the
 * {@code enhances} and {@code overrides} edges in writer-then-declared order, each endpoint placed through
 * {@link PackCompositionResolver#holderOf}, the one owner of which pack a reference means; the {@code specializes_from}
 * edges between agent profiles; and the closure's mutability policies, each scoped to what its pack and the packs it
 * builds on hold (A15). The enhances cycles are computed once here, so the grounding rule reads them from the subject
 * and never from a field another rule filled.
 *
 * <p>The edges are collected when the subject is built; the graphs, the cycles and the scoped policies are read once,
 * on first use. A subject is confined to the thread that judges it.
 */
public final class ComposedRelations {

    private static final NodeKind PROFILE = NodeKind.of(ArtifactKind.AGENT_PROFILE);

    /**
     * An artifact in the pack that holds it, or is taken to.
     *
     * @param pack the holding pack, by configured identifier
     * @param urn the artifact
     */
    public record Place(PackIdentifier pack, Urn urn) {

        @Override
        public String toString() {
            return urn.toString();
        }
    }

    /**
     * One {@code enhances} or {@code overrides} edge: its writer, its source and, when something holds it, its target.
     *
     * @param writer the pack that writes the edge
     * @param source where the edge starts
     * @param target where it ends, when a pack of the closure holds the target
     * @param type the edge's type
     */
    public record Edge(PackUnderResolution writer, Place source, Optional<Place> target, RelationType type) {

        /**
         * Whether the edge has a type.
         *
         * @param wanted the type
         * @return {@code true} when it is the edge's
         */
        public boolean is(RelationType wanted) {
            return type == wanted;
        }

        /**
         * Whether the edge is a self-override: an {@code overrides} edge whose target resolves to its own source.
         *
         * @return {@code true} when it is
         */
        public boolean isSelfOverride() {
            return is(RelationType.OVERRIDES) && target.filter(source::equals).isPresent();
        }
    }

    /**
     * The {@code specializes_from} graph between agent profiles, and the first pack that writes each of its edges.
     *
     * @param graph each profile and the profiles it specializes from, in the order the edges are written
     * @param writers the first pack writing each edge, by its two ends
     */
    public record Specializations(Map<Urn, List<Urn>> graph, Map<List<Urn>, PackUnderResolution> writers) {}

    private final List<PackUnderResolution> packs;
    private final Function<PackUnderResolution, List<Relationship>> edgesOf;
    private final Supplier<Map<PackUlid, MutabilityPolicySource>> scopedPolicies;
    private final Map<PackIdentifier, PackUnderResolution> byConfigured = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private @Nullable Map<PackUlid, MutabilityPolicySource> scoped;
    private @Nullable Map<Place, List<Place>> enhances;
    private @Nullable List<List<Place>> enhancesCycles;
    private @Nullable Specializations specializations;

    private ComposedRelations(
            List<PackUnderResolution> packs,
            Function<PackUnderResolution, List<Relationship>> edgesOf,
            Supplier<Map<PackUlid, MutabilityPolicySource>> scopedPolicies) {
        this.packs = List.copyOf(packs);
        this.edgesOf = edgesOf;
        this.scopedPolicies = scopedPolicies;
        this.packs.forEach(pack -> byConfigured.put(pack.configured().id(), pack));
        collect();
    }

    /**
     * The relations of a composed closure. The {@code enhances} and {@code overrides} edges are collected and placed
     * now; the scoped policies are read the first time a rule needs them.
     *
     * @param packs the closure's packs, in merge order
     * @param edgesOf the typed edges each pack writes, in declared order
     * @param scopedPolicies the closure's mutability policies, each scoped to what its pack and the packs it builds on
     *     hold (A15)
     * @return the subject
     */
    public static ComposedRelations of(
            List<PackUnderResolution> packs,
            Function<PackUnderResolution, List<Relationship>> edgesOf,
            Supplier<Map<PackUlid, MutabilityPolicySource>> scopedPolicies) {
        Objects.requireNonNull(packs, "packs");
        Objects.requireNonNull(edgesOf, "edgesOf");
        Objects.requireNonNull(scopedPolicies, "scopedPolicies");
        return new ComposedRelations(packs, edgesOf, scopedPolicies);
    }

    private void collect() {
        for (PackUnderResolution writer : packs) {
            for (Relationship edge : edgesOf.apply(writer)) {
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

    /**
     * The closure's packs.
     *
     * @return the packs, in merge order
     */
    public List<PackUnderResolution> packs() {
        return packs;
    }

    /**
     * The {@code enhances} and {@code overrides} edges whose source a pack of the closure holds.
     *
     * @return the edges, in writer-then-declared order
     */
    public List<Edge> edges() {
        return List.copyOf(edges);
    }

    /**
     * The typed edges one pack writes.
     *
     * @param pack a pack of the closure
     * @return its edges, in declared order
     */
    public List<Relationship> edgesOf(PackUnderResolution pack) {
        return edgesOf.apply(pack);
    }

    /**
     * The closure's mutability policies, each scoped to what its pack and the packs it builds on hold (A15), read once.
     *
     * @return the policies, by descriptor {@code pack_id}
     */
    public Map<PackUlid, MutabilityPolicySource> scopedPolicies() {
        if (scoped == null) {
            scoped = Objects.requireNonNull(scopedPolicies.get(), "scopedPolicies");
        }
        return scoped;
    }

    /**
     * A pack of the closure.
     *
     * @param pack its configured identifier
     * @return the pack
     */
    public PackUnderResolution packOf(PackIdentifier pack) {
        return Objects.requireNonNull(byConfigured.get(pack), "a place of the closure");
    }

    /**
     * The first pack of the closure with a descriptor {@code pack_id}.
     *
     * @param packId the descriptor {@code pack_id}
     * @return the pack; empty when none has it
     */
    public Optional<PackUnderResolution> byDescriptorPackId(PackUlid packId) {
        return packs.stream().filter(pack -> pack.descriptorId().equals(packId)).findFirst();
    }

    /**
     * The edges of one type onto a held target, as a graph, in the order the edges are collected.
     *
     * @param type the type
     * @param kept which of those edges the graph keeps
     * @return each source and its targets
     */
    public Map<Place, List<Place>> graphOf(RelationType type, Predicate<Edge> kept) {
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
     * The {@code enhances} edges onto a held target, built once for the cycle and the grounding rules.
     *
     * @return each enhancer and what it enhances
     */
    public Map<Place, List<Place>> enhancesGraph() {
        if (enhances == null) {
            enhances = graphOf(RelationType.ENHANCES, edge -> true);
        }
        return enhances;
    }

    /**
     * Every cycle of the {@code enhances} edges, each once ({@link PackGraph#cyclesIn}), computed once.
     *
     * @return the cycles
     */
    public List<List<Place>> enhancesCycles() {
        if (enhancesCycles == null) {
            enhancesCycles = PackGraph.cyclesIn(enhancesGraph());
        }
        return enhancesCycles;
    }

    /**
     * The {@code specializes_from} edges between agent profiles, read once. A profile is one node whichever pack holds
     * it, since the layer merge keeps one version of an id.
     *
     * @return the graph and the first writer of each edge
     */
    public Specializations specializations() {
        if (specializations == null) {
            Map<Urn, List<Urn>> graph = new LinkedHashMap<>();
            Map<List<Urn>, PackUnderResolution> writers = new LinkedHashMap<>();
            for (PackUnderResolution writer : packs) {
                for (Relationship edge : edgesOf.apply(writer)) {
                    if (edge.type() == RelationType.SPECIALIZES_FROM
                            && edge.from().kind() == PROFILE
                            && edge.to().kind() == PROFILE) {
                        graph.computeIfAbsent(edge.from(), ignored -> new ArrayList<>())
                                .add(edge.to());
                        writers.putIfAbsent(List.of(edge.from(), edge.to()), writer);
                    }
                }
            }
            specializations = new Specializations(graph, writers);
        }
        return specializations;
    }

    /**
     * The name of the pack an artifact is placed in.
     *
     * @param place the place
     * @return the pack's descriptor name
     */
    public String name(Place place) {
        return packOf(place.pack()).descriptor().name();
    }
}
