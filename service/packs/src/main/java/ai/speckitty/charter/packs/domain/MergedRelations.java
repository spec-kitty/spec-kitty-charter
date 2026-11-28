package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The typed edges of one composed closure as the layer merge places them, as the edge-index rules judge them (#639,
 * ADR-0082 slice 4, operator ruling D2): the closure's packs, the edges each writes, its mutability policies, the layer
 * merge and the edges of the closure's edge index before and after the merge re-points them (C19, C20, C36). The
 * application's edge index places the edges, by the compose's one rule; this subject only carries them, so a read and
 * a registration judge the same placed edges.
 *
 * <p>A subject is a value: it is built once a closure is merged and never changes.
 */
public final class MergedRelations {

    /**
     * One edge of the edge index, at the place of its source.
     *
     * @param holder the descriptor identity of the pack that holds the edge's source
     * @param source the source URN
     * @param edge the edge as the index holds it: a bare target written for another pack's source names the writer, and
     *     in a merged index a target that lost to a later level names the winner's pack
     * @param target the descriptor identity of the pack the edge's target is placed in; empty when the compose gives no
     *     such pack
     * @param foreign whether another pack than the holder wrote the edge, at the holder's source
     */
    public record PlacedEdge(
            PackUlid holder, Urn source, Relationship edge, Optional<PackUlid> target, boolean foreign) {

        /**
         * Checks the fields.
         *
         * @param holder the source's pack
         * @param source the source
         * @param edge the edge
         * @param target the target's pack
         * @param foreign whether another pack wrote it
         */
        public PlacedEdge {
            Objects.requireNonNull(holder, "holder");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(edge, "edge");
            Objects.requireNonNull(target, "target");
        }

        /**
         * Whether the edge is an {@code overrides} edge that ends at its own source's place (C36, A12).
         *
         * @return {@code true} when it is
         */
        public boolean isSelfOverride() {
            return edge.type() == RelationType.OVERRIDES
                    && target.filter(holder::equals).isPresent()
                    && edge.to().equals(source);
        }

        /**
         * Whether the edge links a mission type to a step: a {@code requires} edge from a mission type to an action.
         *
         * @return {@code true} when it is
         */
        public boolean isStepLink() {
            return source.kind() == NodeKind.MISSION_TYPE
                    && edge.type() == RelationType.REQUIRES
                    && edge.to().kind() == NodeKind.ACTION;
        }
    }

    private final List<PackUnderResolution> packs;
    private final Function<PackUnderResolution, List<Relationship>> edgesOf;
    private final PerPackPolicy policy;
    private final List<LayerOverride> layerOverrides;
    private final boolean complete;
    private final List<PlacedEdge> written;
    private final List<PlacedEdge> placed;
    private final Map<PackUlid, PackUnderResolution> byDescriptor = new LinkedHashMap<>();
    private final Map<PackIdentifier, PackUnderResolution> byConfigured = new LinkedHashMap<>();

    private MergedRelations(
            List<PackUnderResolution> packs,
            Function<PackUnderResolution, List<Relationship>> edgesOf,
            PerPackPolicy policy,
            List<LayerOverride> layerOverrides,
            boolean complete,
            List<PlacedEdge> written,
            List<PlacedEdge> placed) {
        this.packs = List.copyOf(packs);
        this.edgesOf = edgesOf;
        this.policy = policy;
        this.layerOverrides = List.copyOf(layerOverrides);
        this.complete = complete;
        this.written = List.copyOf(written);
        this.placed = List.copyOf(placed);
        this.packs.forEach(pack -> {
            byDescriptor.put(pack.descriptorId(), pack);
            byConfigured.putIfAbsent(pack.configured().id(), pack);
        });
    }

    /**
     * The merged relations of a composed closure.
     *
     * @param packs the closure's packs, in merge order
     * @param edgesOf the typed edges each pack writes, in declared order
     * @param policy the closure's mutability policies, each scoped as the reads scope it (A15)
     * @param merged the layer merge of the closure
     * @param written the edges of the closure's index before the merge, source by source
     * @param placed the edges of the merged index, source by source
     * @return the subject
     */
    public static MergedRelations of(
            List<PackUnderResolution> packs,
            Function<PackUnderResolution, List<Relationship>> edgesOf,
            PerPackPolicy policy,
            MergedClosure merged,
            List<PlacedEdge> written,
            List<PlacedEdge> placed) {
        Objects.requireNonNull(packs, "packs");
        Objects.requireNonNull(edgesOf, "edgesOf");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(merged, "merged");
        Objects.requireNonNull(written, "written");
        Objects.requireNonNull(placed, "placed");
        return new MergedRelations(packs, edgesOf, policy, merged.layerOverrides(), true, written, placed);
    }

    /**
     * The merged relations of a closure whose merge refused before it ended (a step contest no entry settles,
     * {@link LayerMerge#attempt}): only the layer overrides it placed first are judged, so the fault the merge met
     * first is the answer, as when the merge refused at the first fault it met (#639); no edge is placed.
     *
     * @param packs the closure's packs, in merge order
     * @param policy the closure's mutability policies, each scoped as the reads scope it (A15)
     * @param overrides the layer overrides the merge placed before it refused, in the order it placed them
     * @return the subject
     */
    public static MergedRelations stopped(
            List<PackUnderResolution> packs, PerPackPolicy policy, List<LayerOverride> overrides) {
        Objects.requireNonNull(packs, "packs");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(overrides, "overrides");
        return new MergedRelations(packs, pack -> List.of(), policy, overrides, false, List.of(), List.of());
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
     * The typed edges one pack writes.
     *
     * @param pack a pack of the closure
     * @return its edges, in declared order
     */
    public List<Relationship> edgesOf(PackUnderResolution pack) {
        return edgesOf.apply(pack);
    }

    /**
     * The closure's mutability policies.
     *
     * @return the policies
     */
    public PerPackPolicy policy() {
        return policy;
    }

    /**
     * The layer overrides the merge placed: every one when it ended, else those it placed before it refused.
     *
     * @return the overrides, in the order the merge placed them
     */
    public List<LayerOverride> layerOverrides() {
        return layerOverrides;
    }

    /**
     * Whether the merge ended; one that refused judges only the layer overrides it placed first.
     *
     * @return {@code true} when the merge answered
     */
    public boolean complete() {
        return complete;
    }

    /**
     * The edges of the closure's index before the merge: what each pack wrote, at its source's place.
     *
     * @return the edges, source by source
     */
    public List<PlacedEdge> written() {
        return written;
    }

    /**
     * The edges of the merged index: no edge from a losing place, and every edge to one ending at the winner's place.
     *
     * @return the edges, source by source
     */
    public List<PlacedEdge> placed() {
        return placed;
    }

    /**
     * The pack of the closure with a descriptor identity; of two with one, the later in merge order, as the edge index
     * keys them.
     *
     * @param pack the descriptor identity
     * @return the pack
     */
    public PackUnderResolution packOf(PackUlid pack) {
        return Objects.requireNonNull(byDescriptor.get(pack), "a pack of the closure");
    }

    /**
     * The pack of the closure with a configured identifier.
     *
     * @param pack the configured identifier
     * @return the pack
     */
    public PackUnderResolution packOf(PackIdentifier pack) {
        return Objects.requireNonNull(byConfigured.get(pack), "a pack of the closure");
    }
}
