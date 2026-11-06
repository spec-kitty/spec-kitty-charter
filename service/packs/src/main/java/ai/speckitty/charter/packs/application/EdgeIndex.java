package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ComponentResolver;
import ai.speckitty.charter.packs.domain.ComposeResolver;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.ResolvedReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The typed edges of one composed closure, indexed by (holding pack, source URN), as a {@link ComposeResolver}:
 * a {@link GraphWalk} over it reaches every graph-authored kind from the {@link PackCompositionFacts#edges() facts} the
 * compose already read, with no read of its own (NFR-007). A pack is named by its descriptor {@code pack_id}, the
 * identity the {@link ClosureView} uses; a {@code <pack>::} reference resolves by the compose's one rule
 * ({@link PackCompositionResolver#holderOf}).
 *
 * <p>The index is built once and never changes. Each edge it holds is its own instance, so the pack that holds an edge's
 * source is known from the edge itself ({@link #sourceOf}); the kernel {@link Relationship#equals} ignores the relation
 * and the qualifiers, so no edge is ever a key by value.
 */
public final class EdgeIndex implements ComposeResolver {

    /**
     * Where an artifact sits in the closure: the pack that holds it and its bare URN.
     *
     * @param pack the descriptor {@code pack_id} of the holding pack
     * @param urn the bare URN
     */
    public record Place(PackIdentifier pack, Urn urn) {

        /**
         * Checks the fields.
         *
         * @param pack the holding pack
         * @param urn the URN
         */
        public Place {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(urn, "urn");
        }
    }

    /** A held artifact as the walk sees it: its place and the indexed edges from it. */
    private record EdgeNode(PackIdentifier packId, Urn urn, List<Relationship> relationships)
            implements CharterArtifact {

        @Override
        public ArtifactKind kind() {
            return urn.kind()
                    .artifactKind()
                    .orElseThrow(() -> new IllegalStateException("a held artifact has an artifact kind: " + urn));
        }

        @Override
        public String localId() {
            return urn.id();
        }

        @Override
        public Optional<String> schemaVersion() {
            return Optional.empty();
        }

        @Override
        public String label() {
            return urn.toString();
        }

        @Override
        public Optional<String> description() {
            return Optional.empty();
        }
    }

    private final List<PackUnderResolution> closure;
    private final Map<PackIdentifier, PackUnderResolution> byPack = new LinkedHashMap<>();
    private final Map<Place, List<Relationship>> outgoing = new LinkedHashMap<>();
    private final IdentityHashMap<Relationship, Place> sources = new IdentityHashMap<>();

    private EdgeIndex(List<PackUnderResolution> closure) {
        this.closure = List.copyOf(closure);
        this.closure.forEach(pack -> byPack.put(idOf(pack), pack));
    }

    /**
     * Indexes the edges of a composed closure. The holding pack of an edge's source is the pack whose facts carry the
     * edge, unless the source is written {@code <pack>::}: then it is that pack, and a bare target keeps meaning the
     * writer's artifact.
     *
     * @param closure the composed closure, in closure order
     * @param factsByConfiguredId the facts each pack was read with, by configured identifier
     * @return the index
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as the compose refuses a {@code <pack>::} reference
     *     outside the writer's {@code depends_on} closure, which cannot happen after a successful compose
     */
    public static EdgeIndex of(
            List<PackUnderResolution> closure, Map<String, PackCompositionFacts> factsByConfiguredId) {
        Objects.requireNonNull(factsByConfiguredId, "factsByConfiguredId");
        EdgeIndex index = new EdgeIndex(Objects.requireNonNull(closure, "closure"));
        for (PackUnderResolution pack : index.closure) {
            List<Relationship> edges = factsByConfiguredId
                    .getOrDefault(pack.configured().id().value(), PackCompositionFacts.NONE)
                    .edges();
            index.checkQualified(pack, edges);
            edges.forEach(edge -> index.add(pack, edge));
        }
        return index;
    }

    /**
     * The index of an empty closure.
     *
     * @return an index that holds nothing
     */
    public static EdgeIndex empty() {
        return of(List.of(), Map.of());
    }

    @Override
    public Optional<PackIdentifier> holdingPack(PackQualifiedReference reference, PackIdentifier referringPack) {
        PackIdentifier referring = Optional.ofNullable(byPack.get(referringPack))
                .map(pack -> pack.configured().id())
                .orElse(referringPack);
        return PackCompositionResolver.holderOf(closure, referring, reference).map(EdgeIndex::idOf);
    }

    @Override
    public ComponentResolver inPack(PackIdentifier pack) {
        return urn -> node(new Place(pack, urn), outgoing.getOrDefault(new Place(pack, urn), List.of()));
    }

    /** Every place of the closure that holds the URN, in closure order. */
    List<Place> placesOf(Urn urn) {
        return closure.stream()
                .filter(pack -> pack.holds(urn.toString()))
                .map(pack -> new Place(idOf(pack), urn))
                .toList();
    }

    /** Whether the place's pack holds its URN. */
    boolean holds(Place place) {
        return Optional.ofNullable(byPack.get(place.pack()))
                .filter(pack -> pack.holds(place.urn().toString()))
                .isPresent();
    }

    /** The node at a held place, for a walk to start from. */
    CharterArtifact nodeAt(Place place) {
        return inPack(place.pack()).resolve(place.urn()).orElseThrow();
    }

    /** The place of an indexed edge's source. */
    Place sourceOf(Relationship edge) {
        return Objects.requireNonNull(sources.get(edge), "an edge of this index");
    }

    /**
     * The place an indexed edge leads to, as {@link GraphWalk} resolves it: a bare target in the source's pack, a
     * qualified one in the pack the compose's rule gives it; empty when the compose holds no such pack.
     */
    Optional<Place> neighbour(Relationship edge) {
        Place source = sourceOf(edge);
        if (edge.toPack().isEmpty()) {
            return Optional.of(new Place(source.pack(), edge.to()));
        }
        return holdingPack(edge.toReference(), source.pack()).map(pack -> new Place(pack, edge.to()));
    }

    private Optional<CharterArtifact> node(Place place, List<Relationship> edges) {
        return holds(place) ? Optional.of(new EdgeNode(place.pack(), place.urn(), edges)) : Optional.empty();
    }

    /** Refuses, as the compose does, a qualified endpoint outside the writer's {@code depends_on} closure. */
    private void checkQualified(PackUnderResolution pack, List<Relationship> edges) {
        List<ResolvedReference> qualified = edges.stream()
                .flatMap(edge -> Stream.of(edge.fromReference(), edge.toReference()))
                .filter(PackQualifiedReference::qualified)
                .distinct()
                .map(reference -> PackCompositionResolver.resolveReference(
                        closure, pack.configured().id(), reference))
                .toList();
        try {
            PackCompositionResolver.checkReferences(closure, pack.descriptor().packId(), qualified);
        } catch (PackCompositionResolver.Refusal refusal) {
            throw refusal.toCharterRefusal();
        }
    }

    private void add(PackUnderResolution writer, Relationship edge) {
        PackIdentifier writerId = idOf(writer);
        Optional<Place> source = edge.fromPack().isEmpty()
                ? Optional.of(new Place(writerId, edge.from()))
                : holdingPack(edge.fromReference(), writerId).map(pack -> new Place(pack, edge.from()));
        source.ifPresent(place -> put(place, homed(writer, place, edge)));
    }

    /** A copy of the edge held at its source; a bare target written for another pack's source names the writer. */
    private static Relationship homed(PackUnderResolution writer, Place source, Relationship edge) {
        Optional<String> toPack = source.pack().equals(idOf(writer))
                ? edge.toPack()
                : edge.toPack().or(() -> Optional.of(writer.descriptor().name()));
        return new Relationship(
                edge.from(), edge.fromPack(), edge.to(), toPack, edge.type(), edge.when(), edge.rationale());
    }

    private void put(Place source, Relationship edge) {
        outgoing.computeIfAbsent(source, ignored -> new ArrayList<>()).add(edge);
        sources.put(edge, source);
    }

    private static PackIdentifier idOf(PackUnderResolution pack) {
        return new PackIdentifier(pack.descriptor().packId());
    }
}
