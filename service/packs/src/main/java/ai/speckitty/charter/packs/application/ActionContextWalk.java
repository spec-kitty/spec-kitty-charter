package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
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
import org.jspecify.annotations.Nullable;

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
 *
 * <p>{@link #walk} is the offering form (ADR-0018 D1): the five steps alone. {@link #walkEffective} is the effective
 * form, which applies the ownership rule (research R1, Decision Moment {@code 01M774HQ8N5EVYDRTP16MBJCN3}) in three
 * {@link Pass passes} over one listing.
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
     * The rule of the ownership rule that brings an entry into the effective action context (research R1 and R3): the
     * pass of {@link #walkEffective} that listed it. The passes run in this order over one listing, and the first pass
     * that reaches an artifact lists it.
     */
    public enum Pass {
        /** The action's own five steps (ADR-0018 D2, as built); the offering form lists nothing else. */
        ACTION,
        /** The five steps seeded by the in-force directives, tactics and paradigms the action's mission type scopes. */
        MISSION_TYPE,
        /** The five steps seeded by the in-force directives, tactics and paradigms no action and no mission type scopes. */
        PROJECT
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
     * @param pass the pass that listed it; {@link Pass#ACTION} for every entry of the offering form
     * @param root the root of its pass whose steps listed it, the entry itself for a root; empty for
     *     {@link Pass#ACTION}, present for the other passes
     */
    public record Entry(
            Urn urn,
            Step step,
            Urn via,
            int hops,
            Optional<String> when,
            Optional<String> reason,
            List<Urn> enhancedBy,
            List<Urn> enhances,
            Pass pass,
            Optional<Urn> root) {

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
         * @param pass the pass that listed it
         * @param root the root of its pass
         * @throws IllegalArgumentException when the root is present for {@link Pass#ACTION} or absent for another pass
         */
        public Entry {
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(via, "via");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
            enhancedBy = List.copyOf(Objects.requireNonNull(enhancedBy, "enhancedBy"));
            enhances = List.copyOf(Objects.requireNonNull(enhances, "enhances"));
            Objects.requireNonNull(pass, "pass");
            Objects.requireNonNull(root, "root");
            if (root.isPresent() == (pass == Pass.ACTION)) {
                throw new IllegalArgumentException(
                        "an entry of pass " + pass + " has a root exactly when its pass is" + " not ACTION: " + urn);
            }
        }

        /**
         * An entry the action's own steps list ({@link Pass#ACTION}, no root): the offering form's every entry.
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
        public Entry(
                Urn urn,
                Step step,
                Urn via,
                int hops,
                Optional<String> when,
                Optional<String> reason,
                List<Urn> enhancedBy,
                List<Urn> enhances) {
            this(urn, step, via, hops, when, reason, enhancedBy, enhances, Pass.ACTION, Optional.empty());
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
     * @param pass the pass whose {@code suggests} step first reached it (research R3); {@link Pass#ACTION} for the
     *     offering form and for the profile walk, which run one pass
     */
    public record SuggestionCandidate(
            Urn urn, Urn via, int hops, Optional<String> when, Optional<String> reason, Pass pass) {

        /**
         * Checks the fields.
         *
         * @param urn the suggested artifact
         * @param via the suggesting artifact
         * @param hops the path length
         * @param when the edge's condition
         * @param reason the edge's reason
         * @param pass the pass that reached it
         */
        public SuggestionCandidate {
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(via, "via");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(pass, "pass");
        }

        /**
         * A candidate the action's own steps reach ({@link Pass#ACTION}).
         *
         * @param urn the suggested artifact
         * @param via the suggesting artifact
         * @param hops the path length
         * @param when the edge's condition
         * @param reason the edge's reason
         */
        public SuggestionCandidate(Urn urn, Urn via, int hops, Optional<String> when, Optional<String> reason) {
            this(urn, via, hops, when, reason, Pass.ACTION);
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

    /**
     * The order of two edges of one parent to one target: a held edge before a dangling one, then the smaller
     * {@code when}, then the smaller {@code reason}, an absent word as the empty string.
     */
    private static final Comparator<Link> EDGE_TIE_ORDER = Comparator.comparing((Link link) -> !link.held())
            .thenComparing(link -> link.when().orElse(""))
            .thenComparing(link -> link.reason().orElse(""));

    /** The kinds a root of the mission-type and project passes may have (research R3). */
    private static final Set<NodeKind> ROOT_KINDS = Set.of(NodeKind.DIRECTIVE, NodeKind.TACTIC, NodeKind.PARADIGM);

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
     * Walks the action's effective context (FR-008, rule E16 as amended): the ownership rule (research R1, Decision
     * Moment {@code 01M774HQ8N5EVYDRTP16MBJCN3}) in three passes over one listing (research R3).
     *
     * <ol>
     *   <li>{@link Pass#ACTION}: the five steps from {@code action:<missionType>/<action>}, exactly as {@link #walk}
     *       runs them;
     *   <li>{@link Pass#MISSION_TYPE}: the roots are the in-force directives, tactics and paradigms that
     *       {@code mission_type:<missionType>} scopes; each root no earlier step listed is listed by the scope step,
     *       its {@code via} the mission type and its {@code hops} 1, and steps 2 to 5 run from the roots;
     *   <li>{@link Pass#PROJECT}: the roots are the in-force directives, tactics and paradigms that no action and no
     *       mission type scopes ({@link EdgeIndex#isProjectWide}, research R2: every authored {@code scope} edge from
     *       an action or a mission type owns its target, held or not); each is listed as in pass 2, its {@code via} the
     *       action node, and steps 2 to 5 run from the roots.
     * </ol>
     *
     * <p>An artifact an earlier step or pass listed is never listed again and still seeds the later steps, a root
     * included (ADR-0018 D3 within the walk): the first reach wins, vocabulary included, so a root the action's
     * vocabulary step listed is listed through the action and still seeds its own pass. A pass root's hops are seeded
     * explicitly (1, from the pass's own origin), never asked of a node the walk has not reached (squad A7). An
     * artifact one scoped only by other actions or other mission types is not a root. A suggestion candidate carries the
     * pass whose {@code suggests} step first reached it. Entries are sorted by full URN string, and each brings its
     * enhancers in force (E12), whatever pass listed it.
     *
     * @param missionType the mission type
     * @param action the action
     * @param depth the number of {@code suggests} hops, at least 0
     * @param inForce whether an artifact is in force: the effective set, which holds no step
     * @return what applies to the action; no entry and no candidate when the mission type is not in force
     * @throws CharterRefusal as {@link #walk} refuses the names
     * @throws IllegalArgumentException when {@code depth} is negative
     */
    public Context walkEffective(String missionType, String action, int depth, Predicate<Urn> inForce) {
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
        List<Urn> typeRoots = new ArrayList<>();
        List<Urn> projectRoots = new ArrayList<>();
        for (Urn held : merged.heldUrns()) {
            if (ROOT_KINDS.contains(held.kind()) && inForce.test(held)) {
                if (merged.isScopedBy(held, type)) {
                    typeRoots.add(held);
                } else if (merged.isProjectWide(held)) {
                    projectRoots.add(held);
                }
            }
        }
        runPass(run, Pass.MISSION_TYPE, typeRoots, type, depth);
        runPass(run, Pass.PROJECT, projectRoots, start, depth);
        List<Entry> entries = run.listed().stream()
                .map(item -> new Entry(
                        item.urn(),
                        item.step(),
                        item.via(),
                        item.hops(),
                        suggestsOnly(item, item.when()),
                        suggestsOnly(item, item.reason()),
                        enhancersOf(item.urn(), inForce),
                        enhancedTargetsOf(item.urn(), inForce),
                        run.passOf(item.urn()),
                        run.rootOf(item.urn())))
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
     * One pass of the effective form after the first: lists the roots no earlier step listed, at one hop from the
     * pass's origin, then runs steps 2 to 5 from every root, listed now or earlier. The vocabulary step takes one hop
     * from the roots and from what this pass's steps 2 to 4 listed.
     */
    private static void runPass(Engine<Step> run, Pass pass, List<Urn> roots, Urn origin, int depth) {
        run.beginPass(pass, roots, Step.SCOPE, origin);
        List<Urn> required = run.expand(roots, Step.REQUIRES, Integer.MAX_VALUE);
        List<Urn> suggested = run.expand(roots, Step.SUGGESTS, depth);
        List<Urn> requiredOfSuggested = run.expand(suggested, Step.REQUIRES_OF_SUGGESTED, Integer.MAX_VALUE);
        Set<Urn> worded = new TreeSet<>(roots);
        worded.addAll(required);
        worded.addAll(suggested);
        worded.addAll(requiredOfSuggested);
        run.expand(worded, Step.VOCABULARY, 1);
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
        private final Set<Urn> unheld = new HashSet<>();
        private final Map<Urn, Pass> passes = new HashMap<>();
        private final Map<Urn, Urn> rootOf = new HashMap<>();
        private final Map<Urn, Pass> refusedPasses = new HashMap<>();
        private Pass pass = Pass.ACTION;
        private Set<Urn> roots = Set.of();

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

        /**
         * Starts a pass of the effective form ({@link ActionContextWalk#walkEffective}): what the steps list from now on
         * belongs to {@code next}, and each root no earlier step listed is listed by {@code step} from {@code origin}
         * at one hop. The hops are seeded explicitly: the origin is never asked its own, which the walk may not have
         * reached (squad A7). A root an earlier step listed keeps its entry and still seeds the pass.
         *
         * @param next the pass
         * @param passRoots the pass's roots
         * @param step the step that lists a root
         * @param origin the root's parent: the node the pass starts from
         */
        void beginPass(Pass next, Collection<Urn> passRoots, S step, Urn origin) {
            pass = next;
            roots = Set.copyOf(passRoots);
            for (Urn root : new TreeSet<>(passRoots)) {
                if (!listed.containsKey(root)) {
                    hops.put(root, 1);
                    passes.put(root, next);
                    rootOf.put(root, root);
                    listed.put(root, new Listed<>(root, step, origin, 1, Optional.empty(), Optional.empty(), true));
                }
            }
        }

        /** The pass that listed the node; {@link Pass#ACTION} for a walk that never began another. */
        Pass passOf(Urn urn) {
            return passes.getOrDefault(urn, Pass.ACTION);
        }

        /** The root of the pass that listed the node; empty for {@link Pass#ACTION}. */
        Optional<Urn> rootOf(Urn urn) {
            return Optional.ofNullable(rootOf.get(urn));
        }

        /**
         * The root of the current pass the node belongs to: itself for a root, the root it was listed under for a node
         * the current pass listed; {@code null} for any other node, and for every node of {@link Pass#ACTION}.
         */
        private @Nullable Urn ownRoot(Urn urn) {
            if (roots.contains(urn)) {
                return urn;
            }
            return passes.get(urn) == pass ? rootOf.get(urn) : null;
        }

        /** What the steps listed, in URN order (ADR-0018 D3). */
        List<Listed<S>> listed() {
            return listed.values().stream()
                    .sorted(Comparator.comparing(Listed::urn))
                    .toList();
        }

        /**
         * One step over the step's relation from the seeds: lists each node reached that no earlier step listed, and
         * walks through the ones already listed. A node listed with {@code held} false is never walked from, by this
         * step or a later one, whether it is a seed or reached again through a held edge (fold W2): an entry with
         * {@code held} false has nothing below it.
         *
         * <p>The step's reach is breadth-first from every seed at once, as far as {@code layers} hops from the nearest
         * seed. Within that reach, each node takes as its parent the node it can be reached from with the fewest hops
         * from the start, then the smallest URN, then the smallest edge: the nodes are walked from in the order of their
         * hops, so a seed deep in a lineage does not fix a node that a shorter path reaches, and {@code hops} is the
         * fewest the step allows.
         *
         * @return the nodes this step listed
         */
        List<Urn> expand(Collection<Urn> seeds, S step, int layers) {
            Set<Urn> blocked = new HashSet<>(seeds);
            blocked.add(start);
            Map<Urn, Urn> origins = new HashMap<>();
            Map<Urn, Integer> reach = reachOf(seeds, step.relation(), layers, blocked, origins);
            TreeMap<Integer, Set<Urn>> byHops = new TreeMap<>();
            reach.keySet().stream()
                    .filter(listed::containsKey)
                    .forEach(relay -> byHops.computeIfAbsent(hopsOf(relay), ignored -> new TreeSet<>())
                            .add(relay));
            seeds.stream()
                    .filter(reach::containsKey)
                    .forEach(seed -> byHops.computeIfAbsent(hopsOf(seed), ignored -> new TreeSet<>())
                            .add(seed));
            Set<Urn> reached = new HashSet<>(blocked);
            List<Urn> fresh = new ArrayList<>();
            while (!byHops.isEmpty()) {
                List<Urn> frontier = byHops.pollFirstEntry().getValue().stream()
                        .filter(parent -> reach.getOrDefault(parent, layers) < layers)
                        .toList();
                Map<Urn, Link> parents = parentsOf(frontier, step.relation(), reached);
                for (Map.Entry<Urn, Link> next : parents.entrySet()) {
                    Urn node = next.getKey();
                    reached.add(node);
                    if (!listed.containsKey(node)) {
                        list(node, step, next.getValue(), origins);
                        fresh.add(node);
                        if (next.getValue().held() && reach.containsKey(node)) {
                            byHops.computeIfAbsent(hopsOf(node), ignored -> new TreeSet<>())
                                    .add(node);
                        }
                    }
                }
            }
            return fresh;
        }

        /**
         * The nodes the step may walk from, each with its distance from the nearest seed: breadth-first from the seeds
         * through held edges to nodes in force, for at most {@code layers} layers. A seed is at 0; a node listed with
         * {@code held} false is not among them, and neither is a target only a dangling edge reaches.
         *
         * <p>In a pass with roots, {@code origins} receives the root each reached node is walked under: its own
         * ({@link #ownRoot}) when it has one, else the one of the first node, in URN order, of the layer before that
         * reaches it. A node an earlier pass listed is walked through under the root that reaches it now.
         */
        private Map<Urn, Integer> reachOf(
                Collection<Urn> seeds, RelationType relation, int layers, Set<Urn> blocked, Map<Urn, Urn> origins) {
            Map<Urn, Integer> reach = new HashMap<>();
            List<Urn> frontier =
                    new TreeSet<>(seeds).stream().filter(this::expandable).toList();
            frontier.forEach(seed -> {
                reach.put(seed, 0);
                inherit(origins, seed, null);
            });
            for (int layer = 0; layer < layers && !frontier.isEmpty(); layer++) {
                Set<Urn> next = new TreeSet<>();
                for (Urn parent : frontier) {
                    for (Link link : outgoing.getOrDefault(parent, List.of())) {
                        Urn target = link.target();
                        if (link.relation() == relation
                                && link.held()
                                && !blocked.contains(target)
                                && !reach.containsKey(target)
                                && expandable(target)
                                && inForce.test(target)) {
                            next.add(target);
                            inherit(origins, target, origins.get(parent));
                        }
                    }
                }
                int distance = layer + 1;
                next.forEach(target -> reach.put(target, distance));
                frontier = List.copyOf(next);
            }
            return reach;
        }

        /** Records the node's root: its own when it has one, else the one handed down; the first record stands. */
        private void inherit(Map<Urn, Urn> origins, Urn node, @Nullable Urn handedDown) {
            Urn own = ownRoot(node);
            Urn root = own != null ? own : handedDown;
            if (root != null) {
                origins.putIfAbsent(node, root);
            }
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
         * recorded while the layer is walked, whether or not the target is reached by another edge later. A target an
         * earlier pass recorded keeps that pass's record: the first pass that reaches it wins.
         */
        private void recordRefusedSuggestion(
                Link link, RelationType relation, Set<Urn> visited, Comparator<Link> byParent) {
            if (relation == RelationType.SUGGESTS
                    && link.relation() == RelationType.SUGGESTS
                    && !link.target().equals(start)
                    && !visited.contains(link.target())
                    && !inForce.test(link.target())
                    && refusedPasses.getOrDefault(link.target(), pass) == pass) {
                refused.merge(
                        link.target(), link, (first, second) -> byParent.compare(first, second) <= 0 ? first : second);
                refusedPasses.put(link.target(), pass);
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
                            candidate.getValue().reason(),
                            refusedPasses.getOrDefault(candidate.getKey(), Pass.ACTION)))
                    .toList();
        }

        /** Whether a node may be walked from: not one an earlier reach listed with {@code held} false (fold W2). */
        private boolean expandable(Urn urn) {
            return !unheld.contains(urn);
        }

        private void list(Urn urn, S step, Link parent, Map<Urn, Urn> origins) {
            int reached = hopsOf(parent.source()) + 1;
            hops.put(urn, reached);
            passes.put(urn, pass);
            Urn own = ownRoot(parent.source());
            Urn root = own != null ? own : origins.get(parent.source());
            if (root != null) {
                rootOf.put(urn, root);
            }
            if (!parent.held()) {
                unheld.add(urn);
            }
            listed.put(
                    urn,
                    new Listed<>(urn, step, parent.source(), reached, parent.when(), parent.reason(), parent.held()));
        }
    }
}
