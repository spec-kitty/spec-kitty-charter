package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Test builder of {@link Reach} values, as the packs adapter would copy them in; mirrors {@link ClosureSnapshots}. */
final class Reaches {

    private final Map<ArtifactRef, List<Reach.Via>> reached = new LinkedHashMap<>();
    private final Map<ArtifactRef, List<Reach.Via>> stopped = new LinkedHashMap<>();
    private final Set<ArtifactRef> behind = new LinkedHashSet<>();
    private final Set<ArtifactRef> passed = new LinkedHashSet<>();
    private final Map<ArtifactRef, List<Reach.Via>> ancestry = new LinkedHashMap<>();

    private Reaches() {}

    static Reaches reach() {
        return new Reaches();
    }

    /** An artifact by URN text, {@code <kind>:<id>}. */
    static ArtifactRef ref(String urn) {
        int colon = urn.indexOf(':');
        ArtifactKind kind = ArtifactKind.fromToken(urn.substring(0, colon)).orElseThrow();
        return new ArtifactRef(kind, urn.substring(colon + 1));
    }

    static Set<ArtifactRef> refs(String... urns) {
        Set<ArtifactRef> refs = new LinkedHashSet<>();
        Arrays.stream(urns).map(Reaches::ref).forEach(refs::add);
        return refs;
    }

    Reaches start(String urn) {
        reached.computeIfAbsent(ref(urn), k -> new ArrayList<>());
        return this;
    }

    Reaches reached(String urn, String from, RelationType relation) {
        reached.computeIfAbsent(ref(urn), k -> new ArrayList<>()).add(new Reach.Via(ref(from), relation));
        return this;
    }

    Reaches stopped(String urn, String from, RelationType relation) {
        stopped.computeIfAbsent(ref(urn), k -> new ArrayList<>()).add(new Reach.Via(ref(from), relation));
        return this;
    }

    Reaches behind(String... urns) {
        behind.addAll(refs(urns));
        return this;
    }

    Reaches passed(String... urns) {
        passed.addAll(refs(urns));
        return this;
    }

    /** An ancestor passed through from the child that specializes from it, as the lineage records it. */
    Reaches ancestor(String urn, String child) {
        passed.add(ref(urn));
        ancestry.computeIfAbsent(ref(urn), k -> new ArrayList<>())
                .add(new Reach.Via(ref(child), RelationType.SPECIALIZES_FROM));
        return this;
    }

    Reach build() {
        return new Reach(reached, stopped, behind, passed, ancestry);
    }

    /**
     * A test-only closure: forward edges walked over a plan's relations, stopping at its stops. When the plan follows
     * the lineage, every reached node's {@code specializes_from} chain is walked as the packs slice walks it
     * (ADR-0059 D2.4): an ancestor is passed (recorded in {@code ancestorsPassed} and {@code ancestry}, never reached),
     * its {@code requires} and {@code scope} targets join the forward walk, and a stopped ancestor stops the chain. It
     * stands in for the packs slice's walk, so a test can drive {@link CascadeRules#planShared}'s fixed point the way
     * the application does.
     */
    static final class Graph implements Function<ReachPlan, Reach> {

        private static final Set<RelationType> PASS_THROUGH = Set.of(RelationType.REQUIRES, RelationType.SCOPE);

        private final Map<ArtifactRef, List<Reach.Via>> out = new LinkedHashMap<>();
        private int walks;

        Graph edge(String from, RelationType relation, String to) {
            out.computeIfAbsent(ref(from), k -> new ArrayList<>()).add(new Reach.Via(ref(to), relation));
            return this;
        }

        int walks() {
            return walks;
        }

        @Override
        public Reach apply(ReachPlan plan) {
            walks++;
            Walk walk = new Walk(plan);
            plan.starts().forEach(start -> walk.reached.put(start, new ArrayList<>()));
            Deque<ArtifactRef> frontier = new ArrayDeque<>(plan.starts());
            while (!frontier.isEmpty()) {
                walk.forward(frontier);
                if (plan.lineage()) {
                    walk.lineage(frontier);
                }
            }
            return new Reach(walk.reached, walk.stopped, Set.of(), walk.ancestry.keySet(), walk.ancestry);
        }

        /** The state of one walk. */
        private final class Walk {

            private final ReachPlan plan;
            private final Map<ArtifactRef, List<Reach.Via>> reached = new LinkedHashMap<>();
            private final Map<ArtifactRef, List<Reach.Via>> stopped = new LinkedHashMap<>();
            private final Map<ArtifactRef, List<Reach.Via>> ancestry = new LinkedHashMap<>();
            private final Set<ArtifactRef> lineageWalked = new LinkedHashSet<>();

            Walk(ReachPlan plan) {
                this.plan = plan;
            }

            void forward(Deque<ArtifactRef> frontier) {
                while (!frontier.isEmpty()) {
                    ArtifactRef from = frontier.pop();
                    out.getOrDefault(from, List.of()).stream()
                            .filter(edge -> plan.relations().contains(edge.relation()))
                            .forEach(edge -> enter(edge.from(), new Reach.Via(from, edge.relation()), frontier));
                }
            }

            void lineage(Deque<ArtifactRef> frontier) {
                for (ArtifactRef descendant : List.copyOf(reached.keySet())) {
                    if (lineageWalked.add(descendant)) {
                        up(descendant, frontier);
                    }
                }
            }

            private void up(ArtifactRef child, Deque<ArtifactRef> frontier) {
                for (Reach.Via edge : out.getOrDefault(child, List.of())) {
                    if (edge.relation() != RelationType.SPECIALIZES_FROM) {
                        continue;
                    }
                    ArtifactRef parent = edge.from();
                    Reach.Via via = new Reach.Via(child, RelationType.SPECIALIZES_FROM);
                    if (plan.stopAt().contains(parent)) {
                        stopped.computeIfAbsent(parent, k -> new ArrayList<>()).add(via);
                        continue;
                    }
                    if (ancestry.getOrDefault(parent, List.of()).contains(via)) {
                        continue;
                    }
                    ancestry.computeIfAbsent(parent, k -> new ArrayList<>()).add(via);
                    out.getOrDefault(parent, List.of()).stream()
                            .filter(passed -> PASS_THROUGH.contains(passed.relation()))
                            .forEach(
                                    passed -> enter(passed.from(), new Reach.Via(parent, passed.relation()), frontier));
                    up(parent, frontier);
                }
            }

            private void enter(ArtifactRef to, Reach.Via via, Deque<ArtifactRef> frontier) {
                if (plan.stopAt().contains(to)) {
                    stopped.computeIfAbsent(to, k -> new ArrayList<>()).add(via);
                    return;
                }
                if (!reached.containsKey(to)) {
                    frontier.push(to);
                }
                reached.computeIfAbsent(to, k -> new ArrayList<>()).add(via);
            }
        }
    }
}
