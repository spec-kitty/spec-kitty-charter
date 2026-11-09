package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.EdgeIndex.Link;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The action-context walk (ADR-0018 D2 and D3, rule E16): from {@code action:<missionType>/<action>} over the merged
 * index, five breadth-first steps, each visiting a node at most once, with minimum-step semantics:
 *
 * <ol>
 *   <li>scope: the artifacts the action node scopes, one hop;
 *   <li>requires: the scoped artifacts' {@code requires}, transitive;
 *   <li>suggests: the scoped artifacts' {@code suggests}, {@code depth} hops (suggestions of required artifacts are not
 *       followed);
 *   <li>requires of the suggested artifacts, transitive, never walked for {@code suggests};
 *   <li>vocabulary: one hop from every artifact steps 1 to 4 list.
 * </ol>
 *
 * <p>An artifact an earlier step listed is never listed again and still seeds the later steps. Each entry names the step
 * that listed it, its parent ({@code via}: the action node for scope, otherwise another entry of the answer) and its
 * {@code hops}, the length of the path from the action. A node takes as its parent the one of the minimum-step frontier
 * with the fewest hops, then the smallest URN.
 *
 * <p>The predicate is the effective set: a node it refuses is neither listed nor walked through, so a step (no artifact
 * of an activatable kind) is never an entry and never changes a hop count. The one exception is the start node, the held
 * step the action names: the walk starts there when the mission type is in force. Each entry brings the enhancers in
 * force that enhance it and the artifacts in force it enhances, and pulls none of them into the answer (rule E12). An
 * entry the {@code suggests} step lists carries the {@code when} and the {@code reason} of the edge that reached it.
 *
 * <p>The walk reads the merged index it was built over and nothing else (NFR-007). Entries are in step order, then URN
 * order, and no answer depends on the order of the closure, the iteration or the writing of an edge.
 */
public final class ActionContextWalk {

    /** One of the five steps, with the relation its entries are listed by. */
    public enum Step {
        /** The artifacts the action node scopes. */
        SCOPE(RelationType.SCOPE),
        /** What the scoped artifacts require, transitively. */
        REQUIRES(RelationType.REQUIRES),
        /** What the scoped artifacts suggest, {@code depth} hops. */
        SUGGESTS(RelationType.SUGGESTS),
        /** What the suggested artifacts require, transitively. */
        REQUIRES_OF_SUGGESTED(RelationType.REQUIRES),
        /** One hop of vocabulary from every artifact the earlier steps list. */
        VOCABULARY(RelationType.VOCABULARY);

        private final RelationType relation;

        Step(RelationType relation) {
            this.relation = relation;
        }

        /**
         * The relation the step's entries are listed by (ADR-0018 D3: the step 4 entries are {@code requires}).
         *
         * @return the relation
         */
        public RelationType relation() {
            return relation;
        }
    }

    /**
     * One artifact the walk lists, and how it was reached.
     *
     * @param urn the artifact
     * @param step the step that listed it
     * @param via its parent: the action node for scope, otherwise another entry of the answer
     * @param hops the length of the path from the action node, {@code hops(via) + 1}
     * @param when the {@code when} of the {@code suggests} edge that reached it; empty for any other step, and for an
     *     edge that states none
     * @param reason the {@code reason} of that edge; empty likewise
     * @param enhancedBy the enhancers in force that enhance it, in URN order
     * @param enhances the artifacts in force it enhances, in URN order
     */
    public record Entry(
            Urn urn,
            Step step,
            Urn via,
            int hops,
            Optional<String> when,
            Optional<String> reason,
            List<Urn> enhancedBy,
            List<Urn> enhances) {

        /**
         * Checks and copies the fields.
         *
         * @param urn the artifact
         * @param step the step
         * @param via the parent
         * @param hops the path length
         * @param when the suggests edge's condition
         * @param reason the suggests edge's reason
         * @param enhancedBy the enhancers in force
         * @param enhances the artifacts it enhances
         */
        public Entry {
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(via, "via");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
            enhancedBy = List.copyOf(Objects.requireNonNull(enhancedBy, "enhancedBy"));
            enhances = List.copyOf(Objects.requireNonNull(enhances, "enhances"));
        }

        /**
         * The relation the entry is listed by.
         *
         * @return the step's relation
         */
        public RelationType relation() {
            return step.relation();
        }
    }

    /**
     * What the walk answers.
     *
     * @param action the action node
     * @param missionTypeInForce whether the mission type is in force; when it is not, there is no entry
     * @param entries the entries, in step order then URN order
     */
    public record Context(Urn action, boolean missionTypeInForce, List<Entry> entries) {

        /**
         * Checks and copies the fields.
         *
         * @param action the action node
         * @param missionTypeInForce whether the mission type is in force
         * @param entries the entries
         */
        public Context {
            Objects.requireNonNull(action, "action");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }

    /** An entry before its enhancers are known. */
    private record Listed(Urn urn, Step step, Urn via, int hops, Optional<String> when, Optional<String> reason) {}

    private static final Comparator<Link> BEST_PARENT_LAST = Comparator.comparing(
                    (Link link) -> link.when().orElse(""))
            .thenComparing(link -> link.reason().orElse(""));

    private final EdgeIndex merged;
    private final Map<Urn, List<Link>> outgoing = new HashMap<>();
    private final Map<Urn, List<Link>> enhancing = new HashMap<>();

    /**
     * A walk over the merged index of one verified read.
     *
     * @param merged the closure's merged index
     */
    public ActionContextWalk(EdgeIndex merged) {
        this.merged = Objects.requireNonNull(merged, "merged");
        for (Link link : merged.links()) {
            outgoing.computeIfAbsent(link.source(), ignored -> new ArrayList<>())
                    .add(link);
            if (link.relation() == RelationType.ENHANCES) {
                enhancing
                        .computeIfAbsent(link.target(), ignored -> new ArrayList<>())
                        .add(link);
            }
        }
    }

    /**
     * Walks the action's context.
     *
     * @param missionType the mission type
     * @param action the action
     * @param depth the number of {@code suggests} hops, at least 0
     * @param inForce whether an artifact is in force: the effective set, which holds no step
     * @return what the action reaches
     * @throws CharterRefusal with {@code MISSION_TYPE_NOT_FOUND} when no pack of the closure holds the mission type, and
     *     with {@code ACTION_NOT_FOUND} when the closure holds no such step ({@link
     *     ai.speckitty.charter.packs.domain.StepHolding}); both are judged on the merged closure, whether or not the
     *     mission type is in force
     * @throws IllegalArgumentException when {@code depth} is negative
     */
    public Context walk(String missionType, String action, int depth, Predicate<Urn> inForce) {
        Objects.requireNonNull(inForce, "inForce");
        if (depth < 0) {
            throw new IllegalArgumentException("depth counts suggests hops and is at least 0: " + depth);
        }
        Urn type = known(missionType, action);
        Urn start = Urn.action(missionType, action);
        if (!inForce.test(type)) {
            return new Context(start, false, List.of());
        }
        Run run = new Run(start, inForce);
        List<Urn> scoped = run.expand(List.of(start), Step.SCOPE, 1);
        run.expand(scoped, Step.REQUIRES, Integer.MAX_VALUE);
        List<Urn> suggested = run.expand(scoped, Step.SUGGESTS, depth);
        run.expand(suggested, Step.REQUIRES_OF_SUGGESTED, Integer.MAX_VALUE);
        run.expand(run.listedUrns(), Step.VOCABULARY, 1);
        return new Context(start, true, run.entries());
    }

    /** The mission type's URN, once the names are judged on the merged closure (OD-9, closure form). */
    private Urn known(String missionType, String action) {
        Optional<Urn> type = urnOf(() -> Urn.missionType(missionType));
        if (type.isEmpty() || merged.placesOf(type.get()).isEmpty()) {
            throw new CharterRefusal(
                    RefusalCode.MISSION_TYPE_NOT_FOUND,
                    "no pack of the closure holds the mission type `" + missionType + "`");
        }
        Optional<Urn> step = urnOf(() -> Urn.action(missionType, action));
        if (step.isEmpty() || !merged.holdsStep(step.get())) {
            throw new CharterRefusal(
                    RefusalCode.ACTION_NOT_FOUND,
                    "the closure holds no action `" + action + "` of the mission type `" + missionType + "`");
        }
        return type.get();
    }

    private static Optional<Urn> urnOf(Supplier<Urn> urn) {
        try {
            return Optional.of(urn.get());
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    /** One walk: what the steps have listed so far, and the hops of everything it has reached. */
    private final class Run {

        private final Urn start;
        private final Predicate<Urn> inForce;
        private final Map<Urn, Listed> listed = new LinkedHashMap<>();
        private final Map<Urn, Integer> hops = new HashMap<>();

        Run(Urn start, Predicate<Urn> inForce) {
            this.start = start;
            this.inForce = inForce;
            hops.put(start, 0);
        }

        private int hopsOf(Urn urn) {
            return Objects.requireNonNull(hops.get(urn), "a node of the frontier has been reached");
        }

        List<Urn> listedUrns() {
            return List.copyOf(listed.keySet());
        }

        /**
         * One breadth-first step over the step's relation from the seeds, for at most {@code layers} layers: lists each
         * node reached that no earlier step listed, and walks through the ones already listed.
         *
         * @return the nodes this step listed
         */
        List<Urn> expand(Collection<Urn> seeds, Step step, int layers) {
            Set<Urn> visited = new HashSet<>(seeds);
            visited.add(start);
            List<Urn> frontier = List.copyOf(new TreeSet<>(seeds));
            List<Urn> fresh = new ArrayList<>();
            for (int layer = 0; layer < layers && !frontier.isEmpty(); layer++) {
                Map<Urn, Link> parents = parentsOf(frontier, step.relation(), visited);
                for (Map.Entry<Urn, Link> reached : parents.entrySet()) {
                    if (!listed.containsKey(reached.getKey())) {
                        list(reached.getKey(), step, reached.getValue());
                        fresh.add(reached.getKey());
                    }
                }
                visited.addAll(parents.keySet());
                frontier = List.copyOf(parents.keySet());
            }
            return fresh;
        }

        /**
         * The nodes of the next layer, each with the edge of its parent: the one of the frontier with the fewest hops,
         * then the smallest URN, then the smallest edge. The map is in URN order.
         */
        private Map<Urn, Link> parentsOf(List<Urn> frontier, RelationType relation, Set<Urn> visited) {
            Comparator<Link> byParent = Comparator.comparingInt((Link link) -> hopsOf(link.source()))
                    .thenComparing(Link::source)
                    .thenComparing(BEST_PARENT_LAST);
            Map<Urn, Link> parents = new TreeMap<>();
            for (Urn parent : frontier) {
                for (Link link : outgoing.getOrDefault(parent, List.of())) {
                    if (link.relation() == relation
                            && !visited.contains(link.target())
                            && inForce.test(link.target())) {
                        parents.merge(
                                link.target(),
                                link,
                                (first, second) -> byParent.compare(first, second) <= 0 ? first : second);
                    }
                }
            }
            return parents;
        }

        private void list(Urn urn, Step step, Link parent) {
            int reached = hopsOf(parent.source()) + 1;
            boolean suggested = step == Step.SUGGESTS;
            hops.put(urn, reached);
            listed.put(
                    urn,
                    new Listed(
                            urn,
                            step,
                            parent.source(),
                            reached,
                            suggested ? parent.when() : Optional.empty(),
                            suggested ? parent.reason() : Optional.empty()));
        }

        /** The entries in step order then URN order, each with its enhancers in force at either end (rule E12). */
        List<Entry> entries() {
            return listed.values().stream()
                    .sorted(Comparator.comparing(Listed::step).thenComparing(Listed::urn))
                    .map(this::entry)
                    .toList();
        }

        private Entry entry(Listed item) {
            return new Entry(
                    item.urn(),
                    item.step(),
                    item.via(),
                    item.hops(),
                    item.when(),
                    item.reason(),
                    enhancersOf(item.urn()),
                    enhancedTargetsOf(item.urn()));
        }

        private List<Urn> enhancersOf(Urn target) {
            Set<Urn> enhancers = new TreeSet<>();
            enhancing.getOrDefault(target, List.of()).stream()
                    .map(Link::source)
                    .filter(inForce)
                    .forEach(enhancers::add);
            return List.copyOf(enhancers);
        }

        private List<Urn> enhancedTargetsOf(Urn enhancer) {
            Set<Urn> targets = new TreeSet<>();
            outgoing.getOrDefault(enhancer, List.of()).stream()
                    .filter(link -> link.relation() == RelationType.ENHANCES)
                    .map(Link::target)
                    .filter(inForce)
                    .forEach(targets::add);
            return List.copyOf(targets);
        }
    }
}
