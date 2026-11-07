package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.Descent;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.GraphWalker;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.EdgeIndex.Place;
import ai.speckitty.charter.packs.application.ReachResult.Via;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The one traversal of the cascade (ADR-0059 D2): every walk here is a {@link GraphWalk} over an
 * {@link EdgeIndex}, never a walker of its own. It supplies the mechanism; the caller supplies the policy (the relations,
 * the deny set, the lineage).
 *
 * <p>A mission type's steps are its edges too (A7 Q1, rule W20's step term): a reached mission type {@code M} reaches
 * every held {@code action:M/<step>}, crossed like {@code requires}, so a walk that crosses {@code requires} goes on from
 * the steps.
 *
 * <p>The walk is layered by {@code suggests} hops, a 0-1 breadth-first search (IC-21, FR-043). Layer k is the full
 * closure of the layer-k frontier over every other relation, the lineage pass-through and a mission type's steps; the
 * {@code suggests} targets not yet reached form layer k+1. A place is expanded once, in its lowest layer, so the answer
 * does not depend on the order the walk visits places in, and a place the lineage or a step produced has the hop count
 * of the place it came from. A request's {@link ReachRequest#suggestsBound() bound} is checked before a place is
 * entered: a {@code suggests} target past it is neither reached nor stopped at, and is reported in
 * {@link ReachResult#beyondBound()}. Without a bound the layers still run, so {@link ReachResult#hops()} is the same
 * either way.
 *
 * <p>A walk starts at every closure pack that holds a start, and every piece of state is keyed by {@link Place} (pack,
 * URN), as {@code GraphWalk}'s own visited set is, so two packs holding one URN are two nodes. A node's predecessors are
 * recorded in {@link GraphWalker#onRelationship}, before {@code GraphWalk}'s visited guard can pass over a second one.
 */
public final class Reachability {

    private Reachability() {}

    /**
     * Walks forward from the starts over the request's relations, stopping at what the request stops at, and passes the
     * lineage through when the request asks for it. When the request crosses {@code requires}, a reached mission type
     * also reaches its steps, each by a {@link Via} from the mission type with {@code requires}; a start may be a
     * mission type.
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
                byUrn(walked.ancestors),
                minimumHops(walked.hops),
                walked.beyondBound());
    }

    private static Map<Urn, Integer> minimumHops(Map<Place, Integer> hops) {
        Map<Urn, Integer> minimum = new LinkedHashMap<>();
        hops.forEach((place, count) -> minimum.merge(place.urn(), count, Math::min));
        return minimum;
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
        private final Set<Place> stepped = new HashSet<>();
        private final Map<Place, Integer> hops = new HashMap<>();
        private final Set<Place> deferred = new LinkedHashSet<>();
        private final Set<Place> beyond = new LinkedHashSet<>();
        private final List<Place> fresh = new ArrayList<>();
        private final int bound;
        private int layer;
        private @Nullable Place pending;

        private Cascade(EdgeIndex index, ReachRequest request) {
            this.index = index;
            this.request = request;
            this.bound = request.suggestsBound().orElse(Integer.MAX_VALUE);
        }

        static Cascade run(EdgeIndex index, ReachRequest request) {
            Cascade cascade = new Cascade(index, request);
            List<Place> frontier = cascade.startPlaces();
            while (!frontier.isEmpty()) {
                cascade.close(frontier);
                frontier = cascade.nextLayer();
            }
            return cascade;
        }

        /** The full closure of one layer's frontier over every relation but {@code suggests}, steps and lineage included. */
        private void close(List<Place> layerFrontier) {
            List<Place> frontier = layerFrontier;
            while (!frontier.isEmpty()) {
                frontier.forEach(this::walkFrom);
                List<Place> expandedNow = List.copyOf(fresh);
                fresh.clear();
                frontier = steps(expandedNow);
                frontier.addAll(lineage(expandedNow));
            }
        }

        /** The {@code suggests} targets entered in this layer that no cheaper path expanded: the next layer. */
        private List<Place> nextLayer() {
            layer++;
            List<Place> next =
                    deferred.stream().filter(place -> !expanded.contains(place)).toList();
            deferred.clear();
            return next;
        }

        /** The {@code suggests} targets one hop past the bound that the walk reached no other way. */
        Set<Urn> beyondBound() {
            Set<Urn> past = urnsOf(beyond);
            past.removeAll(urnsOf(reached.keySet()));
            past.removeAll(urnsOf(stopped.keySet()));
            return past;
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

        /** Records the place as reached, or as stopped, at this layer; answers whether the walk may descend into it. */
        private boolean enter(Place place, Optional<Via> via) {
            return enter(place, via, layer);
        }

        /** Records the place as reached at {@code hop} suggests hops (its minimum so far), or as stopped. */
        private boolean enter(Place place, Optional<Via> via, int hop) {
            boolean stop = request.stopAt().test(place.urn());
            record(stop ? stopped : reached, place, via);
            if (!stop) {
                hops.merge(place, hop, Math::min);
            }
            return !stop;
        }

        /** The node {@code GraphWalk} visits next is the place the last crossed relationship led to. */
        @Override
        public Descent onNode(DrgNode node) {
            Place here = Objects.requireNonNull(pending, "a walk starts at a place");
            if (!here.urn().equals(node.urn())) {
                throw new IllegalStateException(
                        "the walk visited " + node.urn() + " but the last crossed relationship led to " + here.urn()
                                + ": GraphWalk no longer visits a relationship's target right after crossing it");
            }
            if (!expanded.add(here)) {
                return Descent.SKIP;
            }
            fresh.add(here);
            return Descent.CONTINUE;
        }

        @Override
        public Descent onRelationship(Relationship relationship) {
            if (!request.relations().contains(relationship.type())) {
                return Descent.SKIP;
            }
            Optional<Place> target = index.neighbour(relationship).filter(index::holds);
            if (target.isEmpty()) {
                return Descent.SKIP;
            }
            Via via = new Via(index.sourceOf(relationship).urn(), relationship.type());
            return relationship.type() == RelationType.SUGGESTS ? suggest(target.get(), via) : cross(target.get(), via);
        }

        /** A relationship the walk crosses in this layer: descends into the target unless it is stopped. */
        private Descent cross(Place target, Via via) {
            if (!enter(target, Optional.of(via))) {
                return Descent.SKIP;
            }
            pending = target;
            return Descent.CONTINUE;
        }

        /**
         * A {@code suggests} edge costs a hop: past the bound the target is not entered, only noted; within it the target
         * is entered now (so its way is kept) and expanded in the next layer, unless a cheaper path expands it first.
         */
        private Descent suggest(Place target, Via via) {
            if (layer >= bound) {
                beyond.add(target);
            } else if (enter(target, Optional.of(via), layer + 1)) {
                deferred.add(target);
            }
            return Descent.SKIP;
        }

        /**
         * Enters the steps of each mission type expanded since the last call that is not stepped yet (W20's step term),
         * crossed like {@code requires} and at the mission type's own hop count; answers the steps to walk on from.
         */
        private List<Place> steps(List<Place> expandedNow) {
            List<Place> next = new ArrayList<>();
            if (!request.relations().contains(RelationType.REQUIRES)) {
                return next;
            }
            for (Place place : expandedNow) {
                if (place.urn().kind() == NodeKind.MISSION_TYPE && stepped.add(place)) {
                    Optional<Via> via = Optional.of(new Via(place.urn(), RelationType.REQUIRES));
                    index.stepsOf(place.urn()).stream()
                            .filter(step -> enter(step, via) && !expanded.contains(step))
                            .forEach(next::add);
                }
            }
            return next;
        }

        /**
         * Walks the lineage of each place expanded since the last call that is not walked yet; answers the new starts it
         * passed through to, each at its descendant's hop count.
         */
        private List<Place> lineage(List<Place> expandedNow) {
            List<Place> next = new ArrayList<>();
            request.lineage().ifPresent(lineage -> {
                for (Place place : expandedNow) {
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
}
