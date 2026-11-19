package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ComponentResolver;
import ai.speckitty.charter.packs.domain.ComposeResolver;
import ai.speckitty.charter.packs.domain.GlossaryTerm;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.MissionStepLinkRule;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.ResolvedReference;
import ai.speckitty.charter.packs.domain.StepHolding;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The typed edges of one composed closure, indexed by (holding pack, source URN), as a {@link ComposeResolver}:
 * a {@link GraphWalk} over it reaches every graph-authored kind from the {@link PackCompositionFacts#edges() facts} the
 * compose already read, with no read of its own (NFR-007). A pack is keyed by its descriptor identity, the
 * {@link PackUlid} the {@link ClosureView} orders by; a node it answers carries the id its pack is registered under, as
 * every artifact does. A {@code <pack>::} reference resolves by the compose's one rule
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
     * @param pack the descriptor identity of the holding pack
     * @param urn the bare URN
     */
    public record Place(PackUlid pack, Urn urn) {

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
     * walk). It carries the id its pack is registered under, as every artifact does.
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

    /**
     * A count of the work the index and the walks over it do (NFR-003): one call per visit of an edge or a node. The
     * reads never use one; an operation-count test hands one in through {@link #merged(MergedClosure, Visits)}.
     */
    @FunctionalInterface
    interface Visits {

        /** Counts nothing. */
        Visits NONE = () -> {};

        /** Counts one visit of an edge or a node. */
        void visit();
    }

    private final List<PackUnderResolution> closure;
    private final Visits visits;
    private final Map<PackUlid, PackUnderResolution> byPack = new LinkedHashMap<>();
    private final Map<Place, List<Relationship>> outgoing = new LinkedHashMap<>();
    private final IdentityHashMap<Relationship, Place> sources = new IdentityHashMap<>();
    /** The edges a pack writes at another pack's source ({@code <pack>::} source), by identity. */
    private final Set<Relationship> foreign = Collections.newSetFromMap(new IdentityHashMap<>());

    /** The steps the closure holds, worked out once on first use: the index never changes, so neither does the set. */
    private volatile @Nullable Set<Urn> heldSteps;

    /** The scope owners per target, worked out once on first use: the index never changes, so neither do they. */
    private volatile @Nullable Map<Urn, Set<Urn>> scopeOwners;

    /** The URNs the closure holds, worked out once on first use, in URN order. */
    private volatile @Nullable List<Urn> heldUrns;

    /** The place of the version in force per URN under a layer merge; empty for an index that is not merged. */
    private final Map<Urn, Place> winners;

    private EdgeIndex(List<PackUnderResolution> closure, Map<Urn, Place> winners, Visits visits) {
        this.visits = visits;
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
        EdgeIndex index = new EdgeIndex(Objects.requireNonNull(closure, "closure"), Map.of(), Visits.NONE);
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
     *     at its own source, or when another pack's {@code requires} edge at a mission type in force names an action
     *     that is no step of the type ({@value MissionStepLinkRule#CODE})
     */
    public EdgeIndex merged(MergedClosure merged) {
        return merged(merged, visits);
    }

    /**
     * The index as the layer merge sees it ({@link #merged(MergedClosure)}), counting to {@code visits} each edge its
     * edge lists, its scope-owner build and the walks over it visit, and each node its held-URN build visits.
     *
     * @param merged the layer merge of this index's closure
     * @param visits the count
     * @return the merged index
     * @throws CharterRefusal as {@link #merged(MergedClosure)} does
     */
    EdgeIndex merged(MergedClosure merged, Visits visits) {
        Objects.requireNonNull(merged, "merged");
        Objects.requireNonNull(visits, "visits");
        Map<Urn, Place> placesInForce = new LinkedHashMap<>();
        merged.winners().forEach((urn, winner) -> placesInForce.put(urn, new Place(winner.descriptorPackId(), urn)));
        EdgeIndex view = new EdgeIndex(closure, placesInForce, visits);
        outgoing.forEach((place, edges) -> {
            if (view.wins(place)) {
                edges.forEach(edge -> {
                    Relationship placed = repointed(edge, view);
                    view.put(place, placed);
                    if (foreign.contains(edge)) {
                        view.foreign.add(placed);
                    }
                });
            }
        });
        view.refuseSelfOverrides();
        view.refuseStrayMissionTypeEdges(ownSteps());
        return view;
    }

    private PackUnderResolution packOf(Place place) {
        return Objects.requireNonNull(byPack.get(place.pack()), "a place of the closure");
    }

    /**
     * Whether the place holds the version in force of its URN; every place does in an index that is not merged. A term
     * is judged by its glossary context's place: the context overrides whole (ADR-0049), so a losing copy of the context
     * holds none of its terms.
     */
    private boolean wins(Place place) {
        Place judged = judged(place);
        return winners.getOrDefault(judged.urn(), judged).equals(judged);
    }

    /** The place whose version in force decides a place's: its glossary context's for a term, else itself. */
    private static Place judged(Place place) {
        return GlossaryTerm.contextOf(place.urn())
                .map(context -> new Place(place.pack(), context))
                .orElse(place);
    }

    /** The edge with its target moved to the winner's place when it ends at a losing place (C20); else as it is. */
    private Relationship repointed(Relationship edge, EdgeIndex view) {
        Optional<Place> lost = neighbour(edge).filter(this::holds).filter(place -> !view.wins(place));
        if (lost.isEmpty()) {
            return edge;
        }
        Place winner =
                Objects.requireNonNull(view.winners.get(judged(lost.get()).urn()), "a lost place has a winner");
        return edge.qualified(
                edge.fromPack(), Optional.of(packOf(winner).descriptor().name()));
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
                            List.of(refusalPack(writer)));
                }
            }
        }
    }

    /**
     * The steps of each mission type as its own packs link them: the targets of the {@code requires} edges each pack that
     * holds the type writes from it. The load refuses a pack whose type file and own edges disagree
     * ({@link MissionStepLinkRule}), so these are the steps the type files declare, and a merged type keeps the steps of
     * every version it layers over.
     */
    private Map<Urn, Set<String>> ownSteps() {
        Map<Urn, Set<String>> steps = new LinkedHashMap<>();
        outgoing.forEach((place, edges) -> {
            if (place.urn().kind() == NodeKind.MISSION_TYPE) {
                for (Relationship edge : edges) {
                    if (!foreign.contains(edge)
                            && isStepLink(edge)
                            && edge.toPack().isEmpty()) {
                        steps.computeIfAbsent(place.urn(), ignored -> new LinkedHashSet<>())
                                .add(edge.to().id());
                    }
                }
            }
        });
        return steps;
    }

    /**
     * Refuses a {@code requires} edge that another pack writes at a mission type in force and that names an action that
     * is no step of the type (operator ruling DM-01M7B1YQR5ZKQPDW6DX64KPNRN). Each pack's own edges are judged when it
     * loads; an edge written at another pack's type is judged here, once the merge has placed it.
     */
    private void refuseStrayMissionTypeEdges(Map<Urn, Set<String>> steps) {
        outgoing.forEach((place, edges) -> {
            if (place.urn().kind() != NodeKind.MISSION_TYPE) {
                return;
            }
            Set<String> known = steps.getOrDefault(place.urn(), Set.of());
            for (Relationship edge : edges) {
                if (foreign.contains(edge)
                        && isStepLink(edge)
                        && !known.contains(edge.to().id())) {
                    PackUnderResolution holder = packOf(place);
                    throw new CharterRefusal(
                            RefusalCode.PACK_UNREADABLE,
                            "the mission type in force `" + place.urn() + "` of pack `"
                                    + holder.descriptor().name()
                                    + "` is required to `" + edge.to() + "` by another pack's edge, which names no"
                                    + " step of the type (" + MissionStepLinkRule.CODE + ")",
                            List.of(refusalPack(holder)));
                }
            }
        });
    }

    private static boolean isStepLink(Relationship edge) {
        return edge.type() == RelationType.REQUIRES && edge.to().kind() == NodeKind.ACTION;
    }

    @Override
    public Optional<PackUlid> holdingPack(PackQualifiedReference reference, PackUlid referringPack) {
        Objects.requireNonNull(reference, "reference");
        return Optional.ofNullable(byPack.get(referringPack))
                .flatMap(referring -> PackCompositionResolver.holderOf(
                        closure, referring.configured().id(), reference))
                .map(EdgeIndex::idOf);
    }

    @Override
    public ComponentResolver inPack(PackUlid pack) {
        Objects.requireNonNull(pack, "pack");
        return urn -> node(new Place(pack, urn), outgoing.getOrDefault(new Place(pack, urn), List.of()));
    }

    @Override
    public Optional<PackUlid> descriptorIdOf(PackIdentifier pack) {
        Objects.requireNonNull(pack, "pack");
        return closure.stream()
                .filter(held -> held.configured().id().equals(pack))
                .map(EdgeIndex::idOf)
                .findFirst();
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

    /**
     * The scope owners of each artifact (research R2, squad A6; C-003): for each target URN, the sources of the
     * authored {@code scope} edges of this index whose source is an {@code action} or a {@code mission_type} node,
     * held or not. An edge counts whether or not its source is a held step, whether or not any pack holds its source,
     * and whether or not its target resolves to a held place, because ownership is where the author placed the
     * artifact and turning a mission type off must not make what it scopes project-wide. In a merged index the edges
     * of a version a later level replaced are gone (C19), so they own nothing. Worked out once, in one pass over the
     * edges, on first use.
     *
     * @return the owners by target, each set in URN order; a target no action and no mission type scopes is absent
     */
    Map<Urn, Set<Urn>> scopeOwners() {
        Map<Urn, Set<Urn>> owners = scopeOwners;
        if (owners == null) {
            Map<Urn, Set<Urn>> collected = new HashMap<>();
            forEachEdge((place, edge) -> {
                if (edge.type() == RelationType.SCOPE
                        && (edge.from().kind() == NodeKind.ACTION || edge.from().kind() == NodeKind.MISSION_TYPE)) {
                    collected
                            .computeIfAbsent(edge.to(), ignored -> new TreeSet<>())
                            .add(edge.from());
                }
            });
            Map<Urn, Set<Urn>> frozen = new HashMap<>();
            collected.forEach((target, sources) -> frozen.put(target, Collections.unmodifiableSet(sources)));
            owners = Collections.unmodifiableMap(frozen);
            scopeOwners = owners;
        }
        return owners;
    }

    /**
     * Whether no action and no mission type scopes the artifact ({@link #scopeOwners()}): a project-wide artifact
     * when it is in force.
     *
     * @param artifact the artifact
     * @return whether it has no scope owner
     */
    boolean isProjectWide(Urn artifact) {
        return !scopeOwners().containsKey(artifact);
    }

    /**
     * Whether the mission type is one of the artifact's scope owners ({@link #scopeOwners()}).
     *
     * @param artifact the artifact
     * @param missionType the mission type, {@code mission_type:<id>}
     * @return whether an authored {@code scope} edge runs from the mission type to the artifact
     */
    boolean isScopedBy(Urn artifact, Urn missionType) {
        return scopeOwners().getOrDefault(artifact, Set.of()).contains(missionType);
    }

    /**
     * Every URN a place of the closure holds, in URN order, each once: in a merged index, the URNs whose version in
     * force a pack holds. Worked out once on first use.
     *
     * @return the held URNs
     */
    List<Urn> heldUrns() {
        List<Urn> urns = heldUrns;
        if (urns == null) {
            Set<Urn> held = new TreeSet<>();
            closure.forEach(pack -> pack.definedArtifacts().stream()
                    .map(this::visited)
                    .flatMap(defined -> Urn.parse(defined).stream())
                    .filter(urn -> wins(new Place(idOf(pack), urn)))
                    .forEach(held::add));
            urns = List.copyOf(held);
            heldUrns = urns;
        }
        return urns;
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
     * @param held whether the place the edge leads to holds its artifact: always true in {@link #links()}, false only for
     *     a link of {@link #writtenLinks()} to a dangling target
     */
    record Link(
            Urn source,
            RelationType relation,
            Urn target,
            Optional<String> when,
            Optional<String> reason,
            boolean held) {

        /** A link between two held places. */
        Link(Urn source, RelationType relation, Urn target, Optional<String> when, Optional<String> reason) {
            this(source, relation, target, when, reason, true);
        }
    }

    /**
     * Every edge between two held places, one per written edge, in no promised order: the callers sort. An edge from a
     * place that does not hold its source, or to a place that holds nothing (a target no pack of the closure holds, or a
     * version a later level replaced), is not among them.
     */
    List<Link> links() {
        List<Link> links = new ArrayList<>();
        forEachEdge((place, edge) -> {
            if (holds(place)) {
                neighbour(edge)
                        .filter(this::holds)
                        .ifPresent(target -> links.add(
                                new Link(place.urn(), edge.type(), target.urn(), edge.when(), edge.rationale())));
            }
        });
        return links;
    }

    /**
     * Every edge a held artifact writes, one per written edge, whether or not the place it leads to holds its target
     * (fold W2): {@link #links()} plus the dangling targets. The target of each edge is resolved as {@link #links()}
     * resolves it ({@link #neighbour}: a bare target in the source's pack, a qualified one in the pack it names), so the
     * two lists agree on every edge; a link is {@link Link#held() held} exactly when {@link #links()} holds the same edge,
     * and an edge whose resolved place holds nothing, or whose named pack the compose does not give, is a link with
     * {@code held} false to the URN the edge names. A walk that lists an unheld target and goes no further asks for these
     * ({@link GovernanceWalk}). In a merged index the edges of a version a later level replaced are not among them.
     */
    List<Link> writtenLinks() {
        List<Link> links = new ArrayList<>();
        forEachEdge((place, edge) -> {
            if (holds(place)) {
                Optional<Place> target = neighbour(edge);
                links.add(new Link(
                        place.urn(),
                        edge.type(),
                        edge.to(),
                        edge.when(),
                        edge.rationale(),
                        target.filter(this::holds).isPresent()));
            }
        });
        return links;
    }

    /** Hands every edge of the index, with the place of its source, to the action, counting each one visited. */
    private void forEachEdge(BiConsumer<Place, Relationship> action) {
        outgoing.forEach((place, edges) -> edges.forEach(edge -> {
            visits.visit();
            action.accept(place, edge);
        }));
    }

    /** Counts one visit of a node and hands it back. */
    private <T> T visited(T node) {
        visits.visit();
        return node;
    }

    /**
     * Counts one visit of an edge for a walk over this index ({@link ActionContextWalk}); counts nothing unless an
     * operation-count test handed in a {@link Visits}.
     */
    void visitEdge() {
        visits.visit();
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
        return holds(place)
                ? Optional.of(new EdgeNode(packOf(place).configured().id(), place.urn(), edges))
                : Optional.empty();
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
        PackUlid writerId = idOf(writer);
        Optional<Place> source = edge.fromPack().isEmpty()
                ? Optional.of(new Place(writerId, edge.from()))
                : holdingPack(edge.fromReference(), writerId).map(pack -> new Place(pack, edge.from()));
        source.ifPresent(place -> {
            Relationship placed = homed(writer, place, edge);
            put(place, placed);
            if (!place.pack().equals(writerId)) {
                foreign.add(placed);
            }
        });
    }

    /** A copy of the edge held at its source; a bare target written for another pack's source names the writer. */
    private static Relationship homed(PackUnderResolution writer, Place source, Relationship edge) {
        Optional<String> toPack = source.pack().equals(idOf(writer))
                ? edge.toPack()
                : edge.toPack().or(() -> Optional.of(writer.descriptor().name()));
        return edge.qualified(edge.fromPack(), toPack);
    }

    private void put(Place source, Relationship edge) {
        outgoing.computeIfAbsent(source, ignored -> new ArrayList<>()).add(edge);
        sources.put(edge, source);
    }

    private static PackUlid idOf(PackUnderResolution pack) {
        return pack.descriptorId();
    }

    private static RefusalPack refusalPack(PackUnderResolution pack) {
        return new RefusalPack(
                pack.descriptorId(), pack.configured().id(), pack.descriptor().name());
    }
}
