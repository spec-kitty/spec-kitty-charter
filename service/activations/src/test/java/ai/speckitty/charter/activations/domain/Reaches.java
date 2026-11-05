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

    Reach build() {
        return new Reach(reached, stopped, behind, passed);
    }

    /**
     * A test-only closure: forward edges walked over a plan's relations, stopping at its stops (no lineage). It stands
     * in for the packs slice's walk, so a test can drive {@link CascadeRules#planShared}'s fixed point the way the
     * application does.
     */
    static final class Graph implements Function<ReachPlan, Reach> {

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
            Map<ArtifactRef, List<Reach.Via>> reached = new LinkedHashMap<>();
            Map<ArtifactRef, List<Reach.Via>> stopped = new LinkedHashMap<>();
            Deque<ArtifactRef> frontier = new ArrayDeque<>(plan.starts());
            plan.starts().forEach(start -> reached.put(start, new ArrayList<>()));
            while (!frontier.isEmpty()) {
                ArtifactRef from = frontier.pop();
                for (Reach.Via edge : out.getOrDefault(from, List.of())) {
                    if (!plan.relations().contains(edge.relation())) {
                        continue;
                    }
                    ArtifactRef to = edge.from();
                    Reach.Via via = new Reach.Via(from, edge.relation());
                    if (plan.stopAt().contains(to)) {
                        stopped.computeIfAbsent(to, k -> new ArrayList<>()).add(via);
                    } else {
                        if (!reached.containsKey(to)) {
                            frontier.push(to);
                        }
                        reached.computeIfAbsent(to, k -> new ArrayList<>()).add(via);
                    }
                }
            }
            return new Reach(reached, stopped, Set.of(), Set.of());
        }
    }
}
