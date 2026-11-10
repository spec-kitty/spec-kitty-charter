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
 * <p>An artifact an earlier step listed is never listed again and still seeds the later steps. Each entry names the
 * step that listed it, its parent ({@code via}: the action node for scope, otherwise another entry of the answer) and
 * its {@code hops}, the length of the path from the action. A node takes as its parent the one of the minimum-step
 * frontier with the fewest hops, then the smallest URN.
 *
 * <p>The predicate is the effective set: a node it refuses is neither listed nor walked through, so a step (no artifact
 * of an activatable kind) is never an entry and never changes a hop count. The one exception is the start node, the
 * held step the action names: the walk starts there when the mission type is in force, and the context carries its own
 * enhancers in force and what it enhances ({@link Context#actionEnhancedBy()}, {@link Context#actionEnhances()}). Each
 * entry brings the enhancers in force that enhance it and the artifacts in force it enhances, and pulls none of them
 * into the answer (rule E12). An entry the {@code suggests} step lists carries the {@code when} and the {@code reason}
 * of the edge that reached it.
 *
 * <p>The walk reads the merged index it was built over and nothing else (NFR-007). Entries are sorted by full URN
 * string (ADR-0018 D3), whatever step listed them, and no answer depends on the order of the closure, the iteration or
 * the writing of an edge.
 */
public final class ActionContextWalk {

    /**
     * A step of a breadth-first walk over the typed edges: the relation its entries are listed by. The action walk's
     * {@link Step} is one, and so is any other walk's, so {@link Engine} serves them all.
     */
    interface WalkStep {

        /**
         * The relation the step's entries are listed by.
         *
         * @return the relation
         */
        RelationType relation();
    }

    /** One of the five steps, with the relation its entries are listed by. */
    public enum Step implements WalkStep {
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
        @Override
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
     * An artifact a listed artifact suggests that is held in the merged closure but not in force: a candidate for a
     * conditional suggestion (decision {@code 01M7059T4K64TAVKXN3T1FKWYG}). The walk reports it and goes no further:
     * step 4 does not expand it and step 5 does not take its vocabulary. Whether it is {@code not_selected} is the
     * activations slice's to judge.
     *
     * @param urn the suggested artifact
     * @param via the listed artifact whose {@code suggests} edge reaches it
     * @param hops {@code hops(via) + 1}
     * @param when the {@code when} of that edge; empty when it states none
     * @param reason the {@code reason} of that edge; empty when it states none
     */
    public record SuggestionCandidate(Urn urn, Urn via, int hops, Optional<String> when, Optional<String> reason) {

        /**
         * Checks the fields.
         *
         * @param urn the suggested artifact
         * @param via the suggesting artifact
         * @param hops the path length
         * @param when the edge's condition
         * @param reason the edge's reason
         */
        public SuggestionCandidate {
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(via, "via");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * What the walk answers.
     *
     * @param action the action node
     * @param missionTypeInForce whether the mission type is in force; when it is not, there is no entry
     * @param entries the entries, sorted by full URN string (ADR-0018 D3)
     * @param suggestionCandidates the out-of-force candidates step 3 reached, in URN order; see
     *     {@link ActionContextWalk#walk}
     * @param actionEnhancedBy the enhancers in force that enhance the start step itself, in URN order; empty when the
     *     mission type is not in force
     * @param actionEnhances the artifacts in force the start step enhances, in URN order; empty likewise
     */
    public record Context(
            Urn action,
            boolean missionTypeInForce,
            List<Entry> entries,
            List<SuggestionCandidate> suggestionCandidates,
            List<Urn> actionEnhancedBy,
            List<Urn> actionEnhances) {

        /**
         * Checks and copies the fields.
         *
         * @param action the action node
         * @param missionTypeInForce whether the mission type is in force
         * @param entries the entries
         * @param suggestionCandidates the out-of-force suggestions
         * @param actionEnhancedBy the enhancers in force of the start step
         * @param actionEnhances the artifacts in force the start step enhances
         */
        public Context {
            Objects.requireNonNull(action, "action");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            suggestionCandidates = List.copyOf(Objects.requireNonNull(suggestionCandidates, "suggestionCandidates"));
            actionEnhancedBy = List.copyOf(Objects.requireNonNull(actionEnhancedBy, "actionEnhancedBy"));
            actionEnhances = List.copyOf(Objects.requireNonNull(actionEnhances, "actionEnhances"));
        }
    }

    /**
     * What a walk lists before the caller adds what is its own (the enhancers of an action entry, the holder of a profile's).
     * {@code when} and {@code reason} are the words of the edge that reached the node, whatever its relation: the edge of
     * the parent the walk keeps, which is the first reach (the fewest hops, then the smallest parent, then the smallest
     * edge). A node a later step, or another edge, would also reach keeps the words of its first reach.
     */
    record Listed<S extends WalkStep>(
            Urn urn, S step, Urn via, int hops, Optional<String> when, Optional<String> reason, boolean held) {}

    private static final Comparator<Link> EDGE_TIE_ORDER = Comparator.comparing(
                    (Link link) -> link.when().orElse(""))
            .thenComparing(link -> link.reason().orElse(""))
            .thenComparing(link -> !link.held());

    /** The relations that reach an artifact for certain: the cascade's, but {@code suggests} and {@code enhances}. */
    private static final Set<RelationType> HARD_RELATIONS =
            Set.of(RelationType.REQUIRES, RelationType.REFINES, RelationType.SCOPE, RelationType.INSTANTIATES);

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
     * @return what the action reaches. Beside the entries, {@link Context#suggestionCandidates()} holds each target of
     *     a {@code suggests} edge of step 3 (from a scoped or suggested artifact in force, within {@code depth}) that
     *     the closure holds and the predicate refuses, unless the start step or a listed artifact also reaches it
     *     through a hard relation ({@code requires}, {@code refines}, {@code scope}, {@code instantiates}). They are
     *     candidates only: never listed, expanded or given vocabulary; whether one is {@code not_selected} is the
     *     activations slice's to judge. Where several {@code suggests} edges reach one target, the parent is the one of
     *     the frontier with the fewest hops, then the smallest source URN, then the smallest edge ({@code when}, then
     *     {@code reason}); the candidates come in URN order
     * @throws CharterRefusal with {@code MISSION_TYPE_NOT_FOUND} when no pack of the closure holds the mission type,
     *     and with {@code ACTION_NOT_FOUND} when the closure holds no such step ({@link
     *     ai.speckitty.charter.packs.domain.StepHolding}); both are judged on the merged closure, whether or not the
     *     mission type is in force
     * @throws IllegalArgumentException when {@code depth} is negative
     */
    public Context walk(String missionType, String action, int depth, Predicate<Urn> inForce) {
        Objects.requireNonNull(inForce, "inForce");
        if (depth < 0) {
            throw new IllegalArgumentException("depth counts suggests hops and is at least 0: " + depth);
        }
        Urn type = requireKnown(missionType, action);
        Urn start = Urn.action(missionType, action);
        if (!inForce.test(type)) {
            return new Context(start, false, List.of(), List.of(), List.of(), List.of());
        }
        Engine<Step> run = new Engine<>(start, inForce, outgoing);
        List<Urn> scoped = run.expand(List.of(start), Step.SCOPE, 1);
        run.expand(scoped, Step.REQUIRES, Integer.MAX_VALUE);
        List<Urn> suggested = run.expand(scoped, Step.SUGGESTS, depth);
        run.expand(suggested, Step.REQUIRES_OF_SUGGESTED, Integer.MAX_VALUE);
        run.expand(run.listedUrns(), Step.VOCABULARY, 1);
        List<Entry> entries = run.listed().stream()
                .map(item -> new Entry(
                        item.urn(),
                        item.step(),
                        item.via(),
                        item.hops(),
                        suggestsOnly(item, item.when()),
                        suggestsOnly(item, item.reason()),
                        enhancersOf(item.urn(), inForce),
                        enhancedTargetsOf(item.urn(), inForce)))
                .toList();
        return new Context(
                start,
                true,
                entries,
                run.suggestions(),
                enhancersOf(start, inForce),
                enhancedTargetsOf(start, inForce));
    }

    /**
     * The action context lists the words of a {@code suggests} edge only (E12); the engine carries the words of every
     * edge, for the profile walk.
     */
    private static Optional<String> suggestsOnly(Listed<Step> item, Optional<String> words) {
        return item.step().relation() == RelationType.SUGGESTS ? words : Optional.empty();
    }

    /**
     * Judges the names alone, on the merged closure (OD-9, closure form), and walks nothing: the mission type is judged
     * first, whether or not it is in force, then the step.
     *
     * @param missionType the mission type
     * @param action the action
     * @return the mission type's URN
     * @throws CharterRefusal with {@code MISSION_TYPE_NOT_FOUND} when no pack of the closure holds the mission type,
     *     and with {@code ACTION_NOT_FOUND} when the closure holds no such step
     */
    public Urn requireKnown(String missionType, String action) {
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

    private List<Urn> enhancersOf(Urn target, Predicate<Urn> inForce) {
        Set<Urn> enhancers = new TreeSet<>();
        enhancing.getOrDefault(target, List.of()).stream()
                .map(Link::source)
                .filter(inForce)
                .forEach(enhancers::add);
        return List.copyOf(enhancers);
    }

    private List<Urn> enhancedTargetsOf(Urn enhancer, Predicate<Urn> inForce) {
        Set<Urn> targets = new TreeSet<>();
        outgoing.getOrDefault(enhancer, List.of()).stream()
                .filter(link -> link.relation() == RelationType.ENHANCES)
                .map(Link::target)
                .filter(inForce)
                .forEach(targets::add);
        return List.copyOf(targets);
    }

    /**
     * One walk of the steps a caller runs in order, over the links it hands in: what the steps have listed so far, and
     * the hops of everything the walk has reached. It is the engine the action walk and the profile walk
     * share; each caller states its start node, the steps it runs and their seeds, so the
     * rules that make a walk (minimum step, the parent tie order, the predicate) are written once.
     *
     * @param <S> the walk's step type
     */
    static final class Engine<S extends WalkStep> {

        private final Urn start;
        private final Predicate<Urn> inForce;
        private final Map<Urn, List<Link>> outgoing;
        private final Map<Urn, Listed<S>> listed = new LinkedHashMap<>();
        private final Map<Urn, Integer> hops = new HashMap<>();
        private final Map<Urn, Link> refused = new TreeMap<>();

        /**
         * A walk from the start node.
         *
         * @param start the start node; the walk never lists it, whatever the predicate says of it
         * @param inForce the predicate: a node it refuses is neither listed nor walked through
         * @param outgoing the links by source
         */
        Engine(Urn start, Predicate<Urn> inForce, Map<Urn, List<Link>> outgoing) {
            this.start = start;
            this.inForce = inForce;
            this.outgoing = outgoing;
            hops.put(start, 0);
        }

        private int hopsOf(Urn urn) {
            return Objects.requireNonNull(hops.get(urn), "a node of the frontier has been reached");
        }

        List<Urn> listedUrns() {
            return List.copyOf(listed.keySet());
        }

        /** What the steps listed, in URN order (ADR-0018 D3). */
        List<Listed<S>> listed() {
            return listed.values().stream()
                    .sorted(Comparator.comparing(Listed::urn))
                    .toList();
        }

        /**
         * One breadth-first step over the step's relation from the seeds, for at most {@code layers} layers: lists each
         * node reached that no earlier step listed, and walks through the ones already listed.
         *
         * @return the nodes this step listed
         */
        List<Urn> expand(Collection<Urn> seeds, S step, int layers) {
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
                frontier = parents.entrySet().stream()
                        .filter(reached -> reached.getValue().held())
                        .map(Map.Entry::getKey)
                        .toList();
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
                    .thenComparing(EDGE_TIE_ORDER);
            Map<Urn, Link> parents = new TreeMap<>();
            for (Urn parent : frontier) {
                for (Link link : outgoing.getOrDefault(parent, List.of())) {
                    recordRefusedSuggestion(link, relation, visited, byParent);
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

        /**
         * Records, for the report of refused suggestions, a {@code suggests} edge of a suggests layer whose target is
         * out of force: the one with the fewest hops, then the smallest parent and edge, is kept per target. It is
         * recorded while the layer is walked, whether or not the target is reached by another edge later.
         */
        private void recordRefusedSuggestion(
                Link link, RelationType relation, Set<Urn> visited, Comparator<Link> byParent) {
            if (relation == RelationType.SUGGESTS
                    && link.relation() == RelationType.SUGGESTS
                    && !link.target().equals(start)
                    && !visited.contains(link.target())
                    && !inForce.test(link.target())) {
                refused.merge(
                        link.target(), link, (first, second) -> byParent.compare(first, second) <= 0 ? first : second);
            }
        }

        /**
         * The out-of-force suggestion candidates that neither the start step nor a listed artifact reaches through a
         * hard relation (the start step's own {@code scope} edges count), in URN order.
         */
        List<SuggestionCandidate> suggestions() {
            Set<Urn> hard = new HashSet<>();
            Set<Urn> reaching = new HashSet<>(listed.keySet());
            reaching.add(start);
            for (Urn source : reaching) {
                outgoing.getOrDefault(source, List.of()).stream()
                        .filter(link -> HARD_RELATIONS.contains(link.relation()))
                        .forEach(link -> hard.add(link.target()));
            }
            return refused.entrySet().stream()
                    .filter(candidate -> !hard.contains(candidate.getKey()))
                    .map(candidate -> new SuggestionCandidate(
                            candidate.getKey(),
                            candidate.getValue().source(),
                            hopsOf(candidate.getValue().source()) + 1,
                            candidate.getValue().when(),
                            candidate.getValue().reason()))
                    .toList();
        }

        private void list(Urn urn, S step, Link parent) {
            int reached = hopsOf(parent.source()) + 1;
            hops.put(urn, reached);
            listed.put(
                    urn,
                    new Listed<>(urn, step, parent.source(), reached, parent.when(), parent.reason(), parent.held()));
        }
    }
}
