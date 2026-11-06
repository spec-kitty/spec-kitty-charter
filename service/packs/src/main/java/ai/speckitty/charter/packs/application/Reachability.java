package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.Descent;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.GraphWalker;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.EdgeIndex.Place;
import ai.speckitty.charter.packs.application.ReachResult.Direction;
import ai.speckitty.charter.packs.application.ReachResult.Pulled;
import ai.speckitty.charter.packs.application.ReachResult.Via;
import ai.speckitty.charter.packs.domain.ComposeResolver;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The one traversal of the cascade (ADR-0059 D2): every walk here is a {@link GraphWalk} over an
 * {@link EdgeIndex}, never a walker of its own. It supplies the mechanism; the caller supplies the policy (the relations,
 * the deny set, the lineage).
 *
 * <p>A walk starts at every closure pack that holds a start, and every piece of state is keyed by {@link Place} (pack,
 * URN), as {@code GraphWalk}'s own visited set is, so two packs holding one URN are two nodes. A node's predecessors are
 * recorded in {@link GraphWalker#onRelationship}, before {@code GraphWalk}'s visited guard can pass over a second one.
 */
public final class Reachability {

    private Reachability() {}

    /**
     * Walks forward from the starts over the request's relations, stopping at what the request stops at, and passes the
     * lineage through when the request asks for it.
     *
     * <p>The lineage is a walk of its own (J4): one walk cannot visit a node once as an ancestor and once in force.
     * For each reached node it walks the ancestry chain, passing only the pass-through relations of each ancestor; their
     * targets join as new starts of the forward walk, and this repeats until no new start appears. An ancestor is
     * recorded as passed, never reached, with the child it was passed from; a stopped ancestor stops the chain.
     *
     * @param index the closure's edges
     * @param request what to walk
     * @return the reached, stopped and behind-stopped nodes, and the ancestors passed
     */
    public static ReachResult reach(EdgeIndex index, ReachRequest request) {
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(request, "request");
        Cascade walked = Cascade.run(index, request);
        Set<Urn> behind =
                walked.stopped.isEmpty() ? Set.of() : behind(walked, Cascade.run(index, request.withoutStops()));
        return new ReachResult(
                byUrn(walked.reached),
                byUrn(walked.stopped),
                behind,
                urnsOf(walked.ancestors.keySet()),
                byUrn(walked.ancestors));
    }

    /**
     * What the {@code enhances} pull reaches from a set of artifacts (ADR-0059 D2.2, R1): every artifact joined to a
     * start over {@code enhances} in either direction, transitively, with every way it was reached. It never crosses
     * another relation. A start is not part of the answer.
     *
     * @param index the closure's edges
     * @param starts the artifacts the pull starts from
     * @return each pulled artifact with every predecessor and the direction of the edge crossed
     */
    public static Map<Urn, List<Pulled>> enhancesPull(EdgeIndex index, Set<Urn> starts) {
        return enhancesPull(index, starts, urn -> true);
    }

    /**
     * The {@code enhances} pull bounded by the caller's policy (ADR-0059 D2.2): a node the bound does not admit is not
     * pulled, and the pull does not continue through it. A start is walked whatever the bound.
     *
     * @param index the closure's edges
     * @param starts the artifacts the pull starts from
     * @param admits the nodes the pull may reach, for example those of the kinds an activation activates
     * @return each pulled artifact with every predecessor and the direction of the edge crossed
     */
    public static Map<Urn, List<Pulled>> enhancesPull(EdgeIndex index, Set<Urn> starts, Predicate<Urn> admits) {
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(starts, "starts");
        Objects.requireNonNull(admits, "admits");
        Pull pull = new Pull(index, admits);
        new TreeSet<>(starts).forEach(start -> index.placesOf(start).forEach(pull::walkFrom));
        Map<Urn, List<Pulled>> pulled = byUrn(pull.pulled);
        starts.forEach(pulled::remove);
        return pulled;
    }

    /**
     * The one-hop reverse lookup of {@code enhances}: for each target, the artifacts that write {@code enhances} onto
     * it, with their packs. Deactivate's shared check reads it.
     *
     * @param index the closure's edges
     * @param targets the targets
     * @return each target that has an enhancer, with its enhancers' places
     */
    public static Map<Urn, List<Place>> enhancersOf(EdgeIndex index, Set<Urn> targets) {
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(targets, "targets");
        Map<Urn, List<Place>> enhancers = new LinkedHashMap<>();
        for (Urn target : new TreeSet<>(targets)) {
            List<Place> of = index.incoming(target, RelationType.ENHANCES);
            if (!of.isEmpty()) {
                enhancers.put(target, of);
            }
        }
        return enhancers;
    }

    private static Set<Urn> behind(Cascade walked, Cascade open) {
        Set<Urn> seen = urnsOf(walked.reached.keySet());
        seen.addAll(urnsOf(walked.stopped.keySet()));
        Set<Urn> behind = urnsOf(open.reached.keySet());
        behind.removeAll(seen);
        return behind;
    }

    private static Set<Urn> urnsOf(Set<Place> places) {
        Set<Urn> urns = new LinkedHashSet<>();
        places.forEach(place -> urns.add(place.urn()));
        return urns;
    }

    private static <T> Map<Urn, List<T>> byUrn(Map<Place, Set<T>> byPlace) {
        Map<Urn, Set<T>> merged = new LinkedHashMap<>();
        byPlace.forEach((place, ways) -> merged.computeIfAbsent(place.urn(), ignored -> new LinkedHashSet<>())
                .addAll(ways));
        Map<Urn, List<T>> byUrn = new LinkedHashMap<>();
        merged.forEach((urn, ways) -> byUrn.put(urn, List.copyOf(ways)));
        return byUrn;
    }

    private static <T> void record(Map<Place, Set<T>> into, Place place, Optional<T> way) {
        Set<T> ways = into.computeIfAbsent(place, ignored -> new LinkedHashSet<>());
        way.ifPresent(ways::add);
    }

    /** The state of one forward walk, keyed by place, and its visitor. */
    private static final class Cascade implements GraphWalker {

        private final EdgeIndex index;
        private final ReachRequest request;
        private final Map<Place, Set<Via>> reached = new LinkedHashMap<>();
        private final Map<Place, Set<Via>> stopped = new LinkedHashMap<>();
        private final Map<Place, Set<Via>> ancestors = new LinkedHashMap<>();
        private final Set<Place> expanded = new HashSet<>();
        private final Set<Place> lineageWalked = new HashSet<>();
        private @Nullable Place pending;

        private Cascade(EdgeIndex index, ReachRequest request) {
            this.index = index;
            this.request = request;
        }

        static Cascade run(EdgeIndex index, ReachRequest request) {
            Cascade cascade = new Cascade(index, request);
            List<Place> frontier = cascade.startPlaces();
            while (!frontier.isEmpty()) {
                frontier.forEach(cascade::walkFrom);
                frontier = cascade.lineage();
            }
            return cascade;
        }

        private List<Place> startPlaces() {
            List<Place> starts = new ArrayList<>();
            for (Urn start : new TreeSet<>(request.starts())) {
                index.placesOf(start).stream()
                        .filter(place -> enter(place, Optional.empty()))
                        .forEach(starts::add);
            }
            return starts;
        }

        private void walkFrom(Place place) {
            if (expanded.contains(place)) {
                return;
            }
            pending = place;
            new GraphWalk(index).walk(index.nodeAt(place), this);
        }

        /** Records the place as reached, or as stopped; answers whether the walk may descend into it. */
        private boolean enter(Place place, Optional<Via> via) {
            boolean stop = request.stopAt().test(place.urn());
            record(stop ? stopped : reached, place, via);
            return !stop;
        }

        /** The node {@code GraphWalk} visits next is the place the last crossed relationship led to. */
        @Override
        public Descent onNode(DrgNode node) {
            Place here = Objects.requireNonNull(pending, "a walk starts at a place");
            return expanded.add(here) ? Descent.CONTINUE : Descent.SKIP;
        }

        @Override
        public Descent onRelationship(Relationship relationship) {
            if (!request.relations().contains(relationship.type())) {
                return Descent.SKIP;
            }
            Optional<Place> target = index.neighbour(relationship).filter(index::holds);
            Via via = new Via(index.sourceOf(relationship).urn(), relationship.type());
            if (target.isEmpty() || !enter(target.get(), Optional.of(via))) {
                return Descent.SKIP;
            }
            pending = target.get();
            return Descent.CONTINUE;
        }

        /** Walks the lineage of each reached place not walked yet; answers the new starts it passed through to. */
        private List<Place> lineage() {
            List<Place> next = new ArrayList<>();
            request.lineage().ifPresent(lineage -> {
                for (Place place : List.copyOf(reached.keySet())) {
                    walkLineage(place, lineage, next);
                }
            });
            return next;
        }

        private void walkLineage(Place descendant, ReachRequest.Lineage lineage, List<Place> next) {
            if (lineageWalked.add(descendant)) {
                new GraphWalk(index).walk(index.nodeAt(descendant), new Ancestry(descendant, lineage, next));
            }
        }

        /** The lineage walk from one descendant: up the ancestry chain, passing only the pass-through relations. */
        private final class Ancestry implements GraphWalker {

            private final Place descendant;
            private final ReachRequest.Lineage lineage;
            private final List<Place> next;

            Ancestry(Place descendant, ReachRequest.Lineage lineage, List<Place> next) {
                this.descendant = descendant;
                this.lineage = lineage;
                this.next = next;
            }

            @Override
            public Descent onNode(DrgNode node) {
                return Descent.CONTINUE;
            }

            @Override
            public Descent onRelationship(Relationship relationship) {
                Place source = index.sourceOf(relationship);
                Optional<Place> target = index.neighbour(relationship).filter(index::holds);
                Via via = new Via(source.urn(), relationship.type());
                if (target.isEmpty()) {
                    return Descent.SKIP;
                }
                if (relationship.type() == lineage.ancestry()) {
                    return ancestor(target.get(), via);
                }
                if (!source.equals(descendant) && lineage.passThrough().contains(relationship.type())) {
                    passThrough(target.get(), via);
                }
                return Descent.SKIP;
            }

            /** An ancestor is passed through, never put in force; a stopped one stops the chain. */
            private Descent ancestor(Place parent, Via via) {
                if (request.stopAt().test(parent.urn())) {
                    record(stopped, parent, Optional.of(via));
                    return Descent.SKIP;
                }
                record(ancestors, parent, Optional.of(via));
                return Descent.CONTINUE;
            }

            /** A pass-through target joins the forward walk as a new start, unless it is stopped or already walked. */
            private void passThrough(Place target, Via via) {
                if (enter(target, Optional.of(via)) && !expanded.contains(target)) {
                    next.add(target);
                }
            }
        }
    }

    /** The state of the {@code enhances} pull, keyed by place, and its visitor. */
    private static final class Pull implements GraphWalker {

        private final EdgeIndex index;
        private final ComposeResolver bothWays;
        private final Predicate<Urn> admits;
        private final Map<Place, Set<Pulled>> pulled = new LinkedHashMap<>();
        private final Set<Place> expanded = new HashSet<>();
        private @Nullable Place pending;

        private Pull(EdgeIndex index, Predicate<Urn> admits) {
            this.index = index;
            this.bothWays = index.enhancesBothWays();
            this.admits = admits;
        }

        private void walkFrom(Place place) {
            if (expanded.contains(place)) {
                return;
            }
            pending = place;
            new GraphWalk(bothWays)
                    .walk(bothWays.inPack(place.pack()).resolve(place.urn()).orElseThrow(), this);
        }

        @Override
        public Descent onNode(DrgNode node) {
            Place here = Objects.requireNonNull(pending, "a walk starts at a place");
            return expanded.add(here) ? Descent.CONTINUE : Descent.SKIP;
        }

        @Override
        public Descent onRelationship(Relationship relationship) {
            Optional<Place> target = index.neighbour(relationship).filter(index::holds);
            if (relationship.type() != RelationType.ENHANCES
                    || target.isEmpty()
                    || !admits.test(target.get().urn())) {
                return Descent.SKIP;
            }
            Direction direction = index.isReversed(relationship) ? Direction.ENHANCER_OF : Direction.TARGET_OF;
            record(
                    pulled,
                    target.get(),
                    Optional.of(new Pulled(index.sourceOf(relationship).urn(), direction)));
            pending = target.get();
            return Descent.CONTINUE;
        }
    }
}
