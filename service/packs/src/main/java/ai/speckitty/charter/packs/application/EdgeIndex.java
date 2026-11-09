package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ComponentResolver;
import ai.speckitty.charter.packs.domain.ComposeResolver;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.ResolvedReference;
import ai.speckitty.charter.packs.domain.StepHolding;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

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

    /**
     * A held artifact as the walk sees it: its place and the indexed edges from it. {@link GraphWalk} follows a
     * component's {@link CharterArtifact#derivedRelationships()}, so this node answers its indexed graph-document edges
     * there: it is a walk node, not a stored artifact, and nothing else reads it (ADR-0064; #397 retires or rebases the
     * walk).
     */
    private record EdgeNode(PackIdentifier packId, Urn urn, List<Relationship> edges) implements CharterArtifact {

        @Override
        public List<Relationship> derivedRelationships() {
            return edges;
        }

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

    /** The steps the closure holds, worked out once on first use: the index never changes, so neither does the set. */
    private volatile @Nullable Set<Urn> heldSteps;

    /** The place of the version in force per URN under a layer merge; empty for an index that is not merged. */
    private final Map<Urn, Place> winners;

    private EdgeIndex(List<PackUnderResolution> closure, Map<Urn, Place> winners) {
        this.winners = Map.copyOf(winners);
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
        EdgeIndex index = new EdgeIndex(Objects.requireNonNull(closure, "closure"), Map.of());
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

    /**
     * The index as the layer merge sees it (FR-010, rules C19, C20 and C36): the edges that start at a losing place are
     * dropped, the edges that end at one end at the winner's place, and a losing place no longer holds its artifact.
     *
     * @param merged the layer merge of this index's closure
     * @return the merged index
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when an {@code overrides} edge, re-pointed to the winner, ends
     *     at its own source
     */
    public EdgeIndex merged(MergedClosure merged) {
        Objects.requireNonNull(merged, "merged");
        Map<Urn, Place> placesInForce = new LinkedHashMap<>();
        merged.winners()
                .forEach((urn, winner) ->
                        placesInForce.put(urn, new Place(new PackIdentifier(winner.descriptorPackId()), urn)));
        EdgeIndex view = new EdgeIndex(closure, placesInForce);
        outgoing.forEach((place, edges) -> {
            if (view.wins(place)) {
                edges.forEach(edge -> view.put(place, repointed(edge, view)));
            }
        });
        view.refuseSelfOverrides();
        return view;
    }

    private PackUnderResolution packOf(Place place) {
        return Objects.requireNonNull(byPack.get(place.pack()), "a place of the closure");
    }

    /** Whether the place holds the version in force of its URN; every place does in an index that is not merged. */
    private boolean wins(Place place) {
        return winners.getOrDefault(place.urn(), place).equals(place);
    }

    /** The edge with its target moved to the winner's place when it ends at a losing place (C20); else as it is. */
    private Relationship repointed(Relationship edge, EdgeIndex view) {
        Optional<Place> lost = neighbour(edge).filter(this::holds).filter(place -> !view.wins(place));
        if (lost.isEmpty()) {
            return edge;
        }
        Place winner = Objects.requireNonNull(view.winners.get(lost.get().urn()), "a lost place has a winner");
        return new Relationship(
                edge.from(),
                edge.fromPack(),
                edge.to(),
                Optional.of(packOf(winner).descriptor().name()),
                edge.type(),
                edge.when(),
                edge.rationale());
    }

    /** Refuses an {@code overrides} edge that, re-pointed to the winner, ends at its own source (C36, A12). */
    private void refuseSelfOverrides() {
        for (Map.Entry<Place, List<Relationship>> held : outgoing.entrySet()) {
            for (Relationship edge : held.getValue()) {
                if (edge.type() == RelationType.OVERRIDES
                        && neighbour(edge).filter(held.getKey()::equals).isPresent()) {
                    PackUnderResolution writer = packOf(held.getKey());
                    throw new CharterRefusal(
                            RefusalCode.PACK_UNREADABLE,
                            "pack `" + writer.descriptor().name() + "` writes the overrides edge `" + edge.from()
                                    + "` -> `" + edge.toReference()
                                    + "`, which resolves to its own source in the merged closure (self_override)",
                            List.of(new RefusalPack(
                                    writer.descriptor().packId(),
                                    writer.configured().id().value(),
                                    writer.descriptor().name())));
                }
            }
        }
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
                .filter(this::wins)
                .toList();
    }

    /**
     * The places of a mission type's steps (A7 Q1, rule W20's {@code steps(M)}): every place of the closure, in closure
     * order, that holds an {@code action:<M>/<step>} node the closure holds as a step ({@link StepHolding}, OD-9, closure
     * form). A layer override of a held step is a place of it; an action declared where no pack defines its mission type
     * is none.
     */
    List<Place> stepsOf(Urn missionType) {
        Set<Urn> held = heldSteps();
        return closure.stream()
                .flatMap(pack -> pack.definedArtifacts().stream()
                        .flatMap(defined -> Urn.parse(defined).stream())
                        .filter(urn -> StepHolding.missionTypeOf(urn)
                                .filter(missionType::equals)
                                .isPresent())
                        .filter(held::contains)
                        .map(urn -> new Place(idOf(pack), urn)))
                .filter(this::wins)
                .toList();
    }

    /** The steps the closure holds ({@link StepHolding#stepsHeldByClosure}), from the artifacts each pack defines. */
    Set<Urn> heldSteps() {
        Set<Urn> steps = heldSteps;
        if (steps == null) {
            steps = Collections.unmodifiableSet(StepHolding.stepsHeldByClosure(
                    closure.stream().map(PackUnderResolution::definedArtifacts).toList()));
            heldSteps = steps;
        }
        return steps;
    }

    /** Whether the closure holds the step ({@link StepHolding#heldByClosure}). */
    boolean holdsStep(Urn step) {
        return heldSteps().contains(step);
    }

    /**
     * A typed edge as the effective reads see it: from an artifact held at its place in force to the place it ends at.
     *
     * @param source the source artifact
     * @param relation the relation
     * @param target the target artifact, at the place the edge leads to
     * @param when the authored condition of the edge
     * @param reason the authored reason of the edge
     */
    record Link(Urn source, RelationType relation, Urn target, Optional<String> when, Optional<String> reason) {}

    /**
     * Every edge between two held places, one per written edge, in no promised order: the callers sort. An edge from a
     * place that does not hold its source, or to a place that holds nothing (a target no pack of the closure holds, or a
     * version a later level replaced), is not among them.
     */
    List<Link> links() {
        List<Link> links = new ArrayList<>();
        outgoing.forEach((place, edges) -> {
            if (holds(place)) {
                edges.forEach(edge -> neighbour(edge)
                        .filter(this::holds)
                        .ifPresent(target -> links.add(
                                new Link(place.urn(), edge.type(), target.urn(), edge.when(), edge.rationale()))));
            }
        });
        return links;
    }

    /**
     * Every edge a held artifact writes, one per written edge, whether or not a pack of the closure holds its target
     * (fold W2): {@link #links()} without its drop of a target nobody holds. A walk that lists an unheld target and goes no
     * further asks for these ({@link GovernanceWalk}); the target of a link is the URN the edge names, whichever pack it
     * names it in. In a merged index the edges of a version a later level replaced are not among them.
     */
    List<Link> writtenLinks() {
        List<Link> links = new ArrayList<>();
        outgoing.forEach((place, edges) -> {
            if (holds(place)) {
                edges.forEach(edge ->
                        links.add(new Link(place.urn(), edge.type(), edge.to(), edge.when(), edge.rationale())));
            }
        });
        return links;
    }

    /** Whether a pack of the closure holds the URN (the version in force, in a merged index). */
    boolean holdsUrn(Urn urn) {
        return !placesOf(urn).isEmpty();
    }

    /** Whether the place's pack holds its URN and, in a merged index, holds the version in force. */
    boolean holds(Place place) {
        return Optional.ofNullable(byPack.get(place.pack()))
                        .filter(pack -> pack.holds(place.urn().toString()))
                        .isPresent()
                && wins(place);
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
