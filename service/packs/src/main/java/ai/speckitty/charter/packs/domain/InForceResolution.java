package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.InForceStates.ArtifactState;
import ai.speckitty.charter.packs.domain.InForceStates.Bucket;
import ai.speckitty.charter.packs.domain.InForceStates.DenyNotApplied;
import ai.speckitty.charter.packs.domain.InForceStates.DenyReason;
import ai.speckitty.charter.packs.domain.InForceStates.OverrideNotApplied;
import ai.speckitty.charter.packs.domain.InForceStates.PruningReason;
import ai.speckitty.charter.packs.domain.InForceStates.Reason;
import ai.speckitty.charter.packs.domain.InForceStates.RequiredNotInForce;
import ai.speckitty.charter.packs.domain.InForceStates.State;
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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Decides what is in force: a stratified fixed point over the merged closure (research R13, rules E2 to E9). The strata
 * never change an earlier one, and each is monotone over a finite set:
 *
 * <ol>
 *   <li><strong>Candidates</strong> (E2, E3): the selected ids of the merged closure within the language filter, minus the
 *       deny entries, plus every locked artifact.
 *   <li><strong>Overrides</strong> (E4, C27, A3): transitive from the top, so in {@code F -> E -> T} only {@code F}
 *       stays. An override never hides an artifact that is not {@code open}.
 * </ol>
 *
 * <p>The function is pure and reads only its arguments; the iteration bounds are the size of the closure, and a bound
 * crossed fails loudly rather than answering.
 */
public final class InForceResolution {

    private static final AtomicLong RESOLUTIONS = new AtomicLong();
    private static final AtomicLong WALK_VISITS = new AtomicLong();

    private InForceResolution() {}

    /**
     * How many resolutions this JVM ran: a test hook of the NFR-003 gate, which counts the work a read does, as a delta
     * around one request.
     *
     * @return the resolutions so far
     */
    public static long resolutions() {
        return RESOLUTIONS.get();
    }

    /**
     * How many nodes the resolution's walks of the override graph visited: a test hook of the NFR-003 gate.
     *
     * @return the visits so far
     */
    public static long walkVisits() {
        return WALK_VISITS.get();
    }

    /**
     * The merged edges the strata walk, as plain values.
     *
     * @param overrides each overrider with the artifacts its {@code overrides} edges name
     * @param enhances each enhancer with the artifacts its {@code enhances} edges name
     * @param bindings each artifact bound to a mission type, beyond the steps ({@code action:M/s} is bound to
     *     {@code mission_type:M} by its URN)
     */
    public record Edges(Map<Urn, Set<Urn>> overrides, Map<Urn, Set<Urn>> enhances, Map<Urn, Urn> bindings) {

        /** No edge and no extra binding. */
        public static final Edges NONE = new Edges(Map.of(), Map.of(), Map.of());

        /**
         * Checks and copies the fields.
         *
         * @param overrides the overrides edges
         * @param enhances the enhances edges
         * @param bindings the extra bindings
         */
        public Edges {
            overrides = Map.copyOf(Objects.requireNonNull(overrides, "overrides"));
            enhances = Map.copyOf(Objects.requireNonNull(enhances, "enhances"));
            bindings = Map.copyOf(Objects.requireNonNull(bindings, "bindings"));
        }
    }

    /**
     * Resolves what is in force.
     *
     * @param merged the merged closure
     * @param edges the merged edges among its winners
     * @param request what the document asks
     * @return one state per winner and the rules not applied
     * @throws CharterRefusal with {@code PACK_UNREADABLE} on an override cycle among the candidates and the floor
     */
    public static InForceStates resolve(MergedClosure merged, Edges edges, InForceRequest request) {
        Objects.requireNonNull(merged, "merged");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(request, "request");
        RESOLUTIONS.incrementAndGet();
        Model model = new Model(merged, edges, request);
        model.refuseOverrideCycle();
        return model.states(model.solve());
    }

    /**
     * Two URNs by their full text in Unicode code point order, which differs from {@link Urn#compareTo} (UTF-16 units)
     * only past the Basic Multilingual Plane.
     */
    private static int byCodePoint(Urn left, Urn right) {
        String a = left.toString();
        String b = right.toString();
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int x = a.codePointAt(i);
            int y = b.codePointAt(j);
            if (x != y) {
                return Integer.compare(x, y);
            }
            i += Character.charCount(x);
            j += Character.charCount(y);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    /** What the strata settle: the set in force, what the pruning removed, and the overrides still applied. */
    private record Outcome(Set<Urn> inForce, Set<Urn> pruned, Map<Urn, Set<Urn>> reach) {}

    /** The closure as the strata read it: the held ids, the locked and required ones, and the override graph. */
    private static final class Model {

        private final MergedClosure merged;
        private final Edges edges;
        private final InForceRequest request;
        private final Set<Urn> held;
        private final Set<Urn> locked = new LinkedHashSet<>();
        private final Set<Urn> required = new LinkedHashSet<>();
        private final Map<Urn, Set<Urn>> overridable = new HashMap<>();
        private final Map<Urn, Set<Urn>> notApplied = new HashMap<>();
        private final int bound;

        Model(MergedClosure merged, Edges edges, InForceRequest request) {
            this.merged = merged;
            this.edges = edges;
            this.request = request;
            this.held = merged.winners().keySet();
            this.bound = held.size() + 1;
            merged.winners().forEach((urn, winner) -> {
                if (winner.policy().level() == MutabilityLevel.LOCKED) {
                    locked.add(urn);
                }
            });
            merged.floor().requiredBy().keySet().stream().filter(held::contains).forEach(required::add);
            edges.overrides().forEach(this::classify);
        }

        /** Splits an overrider's edges into those that hide an {@code open} target and those that do not (C27). */
        private void classify(Urn source, Set<Urn> targets) {
            if (!held.contains(source)) {
                return;
            }
            for (Urn target : targets) {
                if (!held.contains(target)) {
                    continue;
                }
                boolean open = Objects.requireNonNull(merged.winners().get(target), "a held artifact has a winner")
                                .policy()
                                .level()
                        == MutabilityLevel.OPEN;
                (open ? overridable : notApplied)
                        .computeIfAbsent(source, ignored -> new LinkedHashSet<>())
                        .add(target);
            }
        }

        /** The candidate set (E2, E3): selected, within scope, not denied, plus every locked artifact. */
        Set<Urn> base() {
            Set<Urn> base = new LinkedHashSet<>();
            for (Urn urn : held) {
                if (locked.contains(urn) || candidate(urn)) {
                    base.add(urn);
                }
            }
            return base;
        }

        private boolean candidate(Urn urn) {
            return request.selected().contains(urn)
                    && !request.deactivated().contains(urn)
                    && request.admits(merged.languages().getOrDefault(urn, Set.of()));
        }

        /** Refuses an override cycle that the candidates or the floor reach (C28, {@code DM-01M6RN6BWE6SVZZE8ERQ8Z9MV6}). */
        void refuseOverrideCycle() {
            Set<Urn> roots = base();
            roots.addAll(required);
            Set<Urn> finished = new HashSet<>();
            for (Urn root : roots) {
                walk(root, new LinkedHashSet<>(), finished);
            }
        }

        private void walk(Urn node, Set<Urn> path, Set<Urn> finished) {
            if (finished.contains(node)) {
                return;
            }
            WALK_VISITS.incrementAndGet();
            if (!path.add(node)) {
                throw new CharterRefusal(
                        RefusalCode.PACK_UNREADABLE,
                        "the overrides edges form a cycle through `" + node
                                + "`; an override cycle is refused, never broken by a pick (override_cycle)");
            }
            for (Urn target : overridable.getOrDefault(node, Set.of())) {
                walk(target, path, finished);
            }
            path.remove(node);
            finished.add(node);
        }

        /** Every artifact an artifact overrides, directly or through a chain, over the edges not withdrawn (E4). */
        Map<Urn, Set<Urn>> reach(Set<Urn> withdrawn) {
            Map<Urn, Set<Urn>> reach = new HashMap<>();
            for (Urn urn : held) {
                reach.put(urn, descendants(urn, withdrawn));
            }
            return reach;
        }

        private Set<Urn> descendants(Urn source, Set<Urn> withdrawn) {
            Set<Urn> seen = new LinkedHashSet<>();
            List<Urn> todo = new ArrayList<>(edgesOf(source, withdrawn));
            int steps = 0;
            while (!todo.isEmpty()) {
                Urn next = todo.removeLast();
                if (seen.add(next)) {
                    todo.addAll(edgesOf(next, withdrawn));
                }
                steps = bounded(steps);
            }
            return seen;
        }

        private Set<Urn> edgesOf(Urn source, Set<Urn> withdrawn) {
            return withdrawn.contains(source) ? Set.of() : overridable.getOrDefault(source, Set.of());
        }

        /** Counts one step of a walk and fails loudly when a walk takes more steps than the closure can need. */
        private int bounded(int steps) {
            WALK_VISITS.incrementAndGet();
            int next = steps + 1;
            if (next > (long) bound * bound * bound) {
                throw new IllegalStateException("an in-force walk crossed its bound of " + bound + " (closure size)");
            }
            return next;
        }

        /**
         * The four strata, to a fixed point (R13): the requirement and the pruning are run again, without the overrides of
         * every overrider the pruning removed, until no overrider is newly removed. A removed overrider that comes back
         * in force with its overrides withdrawn has no stable answer and is refused (E7).
         */
        Outcome solve() {
            Set<Urn> withdrawn = new LinkedHashSet<>();
            for (int round = 0; round <= bound; round++) {
                Outcome outcome = settle(withdrawn);
                Set<Urn> removed = minus(overridersOf(outcome.pruned()), withdrawn);
                if (removed.isEmpty()) {
                    refuseUnstable(withdrawn, outcome);
                    return outcome;
                }
                withdrawn.addAll(removed);
            }
            throw new IllegalStateException(
                    "the in-force resolution crossed its bound of " + bound + " (closure size) without settling");
        }

        private Set<Urn> overridersOf(Set<Urn> urns) {
            Set<Urn> overriders = new LinkedHashSet<>(urns);
            overriders.retainAll(overridable.keySet());
            return overriders;
        }

        private void refuseUnstable(Set<Urn> withdrawn, Outcome outcome) {
            TreeSet<Urn> back = new TreeSet<>(withdrawn);
            back.retainAll(outcome.inForce());
            if (!back.isEmpty()) {
                throw new CharterRefusal(
                        RefusalCode.PACK_UNREADABLE,
                        "`" + back.first()
                                + "` is pruned while its overrides apply and in force once they are withdrawn: the"
                                + " in-force set has no stable answer (override_unstable)");
            }
        }

        /** One pass of the strata 1 to 4 (E4 to E6) over the overrides not withdrawn. */
        private Outcome settle(Set<Urn> withdrawn) {
            Map<Urn, Set<Urn>> reach = reach(withdrawn);
            Set<Urn> demanded = requirement(reach);
            Set<Urn> inForce = prune(demanded);
            return new Outcome(inForce, minus(demanded, inForce), reach);
        }

        /** The candidates less what an overrider among them hides, then the requirement iterated to a fixed point (A2). */
        private Set<Urn> requirement(Map<Urn, Set<Urn>> reach) {
            Set<Urn> base = base();
            Set<Urn> inForce = minus(base, hiddenBy(base, reach));
            for (int round = 0; round <= bound; round++) {
                Set<Urn> next = step(inForce, reach);
                if (next.equals(inForce)) {
                    return inForce;
                }
                inForce = next;
            }
            throw new IllegalStateException(
                    "the requirement iteration crossed its bound of " + bound + " (closure size) without settling");
        }

        /** The least fixed point of the grounding (E6, A8): an enhancer needs a target in the set, a bound one its mission type. */
        private Set<Urn> prune(Set<Urn> candidates) {
            Set<Urn> grounded = new LinkedHashSet<>();
            for (int round = 0; round <= bound; round++) {
                Set<Urn> next = groundedIn(candidates, grounded);
                if (next.equals(grounded)) {
                    return grounded;
                }
                grounded = next;
            }
            throw new IllegalStateException(
                    "the enhancer pruning crossed its bound of " + bound + " (closure size) without settling");
        }

        private Set<Urn> groundedIn(Set<Urn> candidates, Set<Urn> set) {
            Set<Urn> next = new LinkedHashSet<>();
            candidates.stream().filter(urn -> standsOn(urn, set)).forEach(next::add);
            return next;
        }

        /** Whether an artifact is grounded in the set: a target of its enhancement in it, and its mission type in it. */
        private boolean standsOn(Urn urn, Set<Urn> set) {
            return !enhancerOut(urn, set) && !bindingOut(urn, set);
        }

        private boolean enhancerOut(Urn urn, Set<Urn> inForce) {
            Set<Urn> targets = edges.enhances().getOrDefault(urn, Set.of());
            return !targets.isEmpty() && targets.stream().noneMatch(inForce::contains);
        }

        private boolean bindingOut(Urn urn, Set<Urn> inForce) {
            return bindingOf(urn)
                    .filter(missionType -> !inForce.contains(missionType))
                    .isPresent();
        }

        /** The mission type an artifact is bound to: a step by its URN, any other by the closure's bindings. */
        private Optional<Urn> bindingOf(Urn urn) {
            return StepHolding.missionTypeOf(urn)
                    .or(() -> Optional.ofNullable(edges.bindings().get(urn)));
        }

        /** One step of the requirement (E5): add what no artifact in force satisfies, then hide what that hides. */
        private Set<Urn> step(Set<Urn> inForce, Map<Urn, Set<Urn>> reach) {
            Set<Urn> with = new LinkedHashSet<>(inForce);
            for (Urn urn : required) {
                if (!locked.contains(urn)
                        && !inForce.contains(urn)
                        && overridersOf(urn, inForce, reach).isEmpty()) {
                    with.add(urn);
                }
            }
            return minus(with, hiddenBy(with, reach));
        }

        /**
         * Every artifact of the set that overrides the target, directly or through a chain, in the ruled order (operator
         * ruling of 2026-11-12 on #439, option (b)): by the level of its winning pack, then by URN.
         */
        private List<Urn> overridersOf(Urn target, Set<Urn> among, Map<Urn, Set<Urn>> reach) {
            return among.stream()
                    .filter(overrider -> reach.getOrDefault(overrider, Set.of()).contains(target))
                    .sorted(this::inRuledOrder)
                    .toList();
        }

        private Set<Urn> hiddenBy(Set<Urn> overriders, Map<Urn, Set<Urn>> reach) {
            Set<Urn> hidden = new HashSet<>();
            overriders.forEach(urn -> hidden.addAll(reach.getOrDefault(urn, Set.of())));
            return hidden;
        }

        private Set<Urn> minus(Set<Urn> set, Set<Urn> removed) {
            Set<Urn> rest = new LinkedHashSet<>(set);
            rest.removeAll(removed);
            return rest;
        }

        InForceStates states(Outcome outcome) {
            Map<Urn, ArtifactState> states = new LinkedHashMap<>();
            held.forEach(urn -> states.put(urn, stateOf(urn, outcome)));
            return new InForceStates(
                    states,
                    stale(),
                    denyNotApplied(outcome),
                    overrideNotApplied(outcome),
                    sorted(merged.floor().unheld().keySet()),
                    requiredNotInForce(outcome, states),
                    enhancedTargets(states));
        }

        /** The required or locked artifacts the pruning removed (the enhancer and binding rules win over the floor). */
        private List<RequiredNotInForce> requiredNotInForce(Outcome outcome, Map<Urn, ArtifactState> states) {
            return sorted(outcome.pruned()).stream()
                    .filter(urn -> required.contains(urn) || locked.contains(urn))
                    .map(urn -> new RequiredNotInForce(
                            urn,
                            pruningReasonOf(Objects.requireNonNull(states.get(urn), "a held artifact has a state")
                                    .reason())))
                    .toList();
        }

        /** The pruning reason of an artifact the pruning removed: it is the enhancer rule's or the binding rule's. */
        private static PruningReason pruningReasonOf(Reason reason) {
            return switch (reason) {
                case MISSION_TYPE_NOT_IN_FORCE -> PruningReason.MISSION_TYPE_NOT_IN_FORCE;
                case ENHANCED_TARGET_NOT_IN_FORCE -> PruningReason.ENHANCED_TARGET_NOT_IN_FORCE;
                default -> throw new IllegalStateException("a pruned artifact has a pruning reason, not " + reason);
            };
        }

        private Map<Urn, List<Urn>> enhancedTargets(Map<Urn, ArtifactState> states) {
            Map<Urn, List<Urn>> targets = new LinkedHashMap<>();
            states.forEach((urn, state) -> {
                if (state.reason() == Reason.ENHANCED_TARGET_NOT_IN_FORCE) {
                    targets.put(urn, sorted(edges.enhances().getOrDefault(urn, Set.of())));
                }
            });
            return targets;
        }

        private ArtifactState stateOf(Urn urn, Outcome outcome) {
            Reason reason = reasonOf(urn, outcome);
            State state = decided(urn, outcome, reason);
            return new ArtifactState(
                    urn,
                    state,
                    reason,
                    required.contains(urn) || locked.contains(urn),
                    satisfiedBy(urn, outcome),
                    overriddenBy(urn, outcome, reason),
                    bucketOf(urn, state, reason));
        }

        /** Every artifact in force that satisfies a required artifact that is out of force (E5, E9), in the ruled order. */
        private List<Urn> satisfiedBy(Urn urn, Outcome outcome) {
            if (!required.contains(urn)
                    || locked.contains(urn)
                    || outcome.inForce().contains(urn)) {
                return List.of();
            }
            return overridersOf(urn, outcome.inForce(), outcome.reach());
        }

        /**
         * Every artifact in force that hides an artifact whose reason is {@link Reason#OVERRIDDEN} (the same walk as E5), in
         * the ruled order.
         */
        private List<Urn> overriddenBy(Urn urn, Outcome outcome, Reason reason) {
            return reason == Reason.OVERRIDDEN ? overridersOf(urn, outcome.inForce(), outcome.reach()) : List.of();
        }

        /** The ruled order of two overriders (#439): the level of the winning pack (built-in, org, project), then the URN. */
        private int inRuledOrder(Urn left, Urn right) {
            int byLevel = levelOf(left).compareTo(levelOf(right));
            return byLevel != 0 ? byLevel : byCodePoint(left, right);
        }

        /** The level of the pack that holds the version in force of an artifact of the closure. */
        private PackLevel levelOf(Urn urn) {
            return Objects.requireNonNull(merged.winners().get(urn), "an overrider in force has a winner")
                    .level();
        }

        private State decided(Urn urn, Outcome outcome, Reason reason) {
            if (outcome.inForce().contains(urn)) {
                return State.ACTIVE;
            }
            return reason == Reason.DEACTIVATED ? State.EXCLUDED : State.INACTIVE;
        }

        /** The first row of the precedence table that holds (E9). */
        private Reason reasonOf(Urn urn, Outcome outcome) {
            if (outcome.inForce().contains(urn)) {
                return locked.contains(urn)
                        ? Reason.LOCKED
                        : required.contains(urn) ? Reason.REQUIRED : Reason.SELECTED;
            }
            if (bindingOut(urn, outcome.inForce())) {
                return Reason.MISSION_TYPE_NOT_IN_FORCE;
            }
            if (enhancerOut(urn, outcome.inForce())) {
                return Reason.ENHANCED_TARGET_NOT_IN_FORCE;
            }
            if (!request.admits(merged.languages().getOrDefault(urn, Set.of()))) {
                return Reason.LANGUAGE_FILTERED;
            }
            if (request.deactivated().contains(urn)) {
                return Reason.DEACTIVATED;
            }
            return request.selected().contains(urn) ? Reason.OVERRIDDEN : Reason.NOT_SELECTED;
        }

        private Optional<Bucket> bucketOf(Urn urn, State state, Reason reason) {
            boolean named =
                    request.selected().contains(urn) || request.deactivated().contains(urn);
            if (state == State.ACTIVE || !named || listedAsRequiredNotInForce(urn, reason)) {
                return Optional.empty();
            }
            return switch (reason) {
                case MISSION_TYPE_NOT_IN_FORCE -> Optional.of(Bucket.MISSION_TYPE_NOT_IN_FORCE);
                case ENHANCED_TARGET_NOT_IN_FORCE -> Optional.of(Bucket.ENHANCED_TARGET_NOT_IN_FORCE);
                case LANGUAGE_FILTERED -> Optional.of(Bucket.LANGUAGE_FILTERED);
                case DEACTIVATED -> Optional.of(Bucket.DEACTIVATED);
                case OVERRIDDEN -> Optional.of(Bucket.OVERRIDDEN);
                default -> Optional.empty();
            };
        }

        /**
         * Whether the artifact is a required or locked one the pruning removed: it is listed once, in
         * {@code requiredNotInForce}, and in no bucket as well (decision {@code 01M6YM02EG3X5V8G33T1HT5N0K}).
         */
        private boolean listedAsRequiredNotInForce(Urn urn, Reason reason) {
            return (required.contains(urn) || locked.contains(urn))
                    && (reason == Reason.MISSION_TYPE_NOT_IN_FORCE || reason == Reason.ENHANCED_TARGET_NOT_IN_FORCE);
        }

        private List<Urn> stale() {
            Set<Urn> stale = new TreeSet<>();
            request.selected().stream().filter(urn -> !held.contains(urn)).forEach(stale::add);
            request.deactivated().stream().filter(urn -> !held.contains(urn)).forEach(stale::add);
            return List.copyOf(stale);
        }

        private List<DenyNotApplied> denyNotApplied(Outcome outcome) {
            List<DenyNotApplied> entries = new ArrayList<>();
            for (Urn urn : sorted(request.deactivated())) {
                if (locked.contains(urn)) {
                    entries.add(new DenyNotApplied(urn, DenyReason.LOCKED));
                } else if (required.contains(urn) && outcome.inForce().contains(urn)) {
                    entries.add(new DenyNotApplied(urn, DenyReason.REQUIRED));
                }
            }
            return entries;
        }

        /**
         * The overrides not applied because the target is not {@code open} (C27): each target in force once, with every
         * overrider in force whose edge names it, in the ruled order.
         */
        private List<OverrideNotApplied> overrideNotApplied(Outcome outcome) {
            Map<Urn, Set<Urn>> byTarget = new HashMap<>();
            notApplied.forEach((source, targets) -> {
                if (outcome.inForce().contains(source)) {
                    targets.stream()
                            .filter(outcome.inForce()::contains)
                            .forEach(target -> byTarget.computeIfAbsent(target, ignored -> new HashSet<>())
                                    .add(source));
                }
            });
            return sorted(byTarget.keySet()).stream()
                    .map(target -> new OverrideNotApplied(
                            target,
                            byTarget.getOrDefault(target, Set.of()).stream()
                                    .sorted(this::inRuledOrder)
                                    .toList()))
                    .toList();
        }

        private static List<Urn> sorted(Set<Urn> urns) {
            return List.copyOf(new TreeSet<>(urns));
        }
    }
}
