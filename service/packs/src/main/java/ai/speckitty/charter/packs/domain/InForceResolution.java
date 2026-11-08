package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.InForceStates.ArtifactState;
import ai.speckitty.charter.packs.domain.InForceStates.Bucket;
import ai.speckitty.charter.packs.domain.InForceStates.DenyNotApplied;
import ai.speckitty.charter.packs.domain.InForceStates.DenyReason;
import ai.speckitty.charter.packs.domain.InForceStates.OverrideNotApplied;
import ai.speckitty.charter.packs.domain.InForceStates.Reason;
import ai.speckitty.charter.packs.domain.InForceStates.State;
import java.util.ArrayList;
import java.util.Comparator;
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

    private InForceResolution() {}

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
        Model model = new Model(merged, edges, request);
        model.refuseOverrideCycle();
        return model.states(model.solve());
    }

    /** What the strata settle: the set the requirement and the pruning leave, and the overrides that were withdrawn. */
    private record Outcome(Set<Urn> inForce, Map<Urn, Set<Urn>> reach) {}

    /** The closure as the strata read it: the held ids, the locked and required ones, and the override graph. */
    private static final class Model {

        private final MergedClosure merged;
        private final InForceRequest request;
        private final Set<Urn> held;
        private final Set<Urn> locked = new LinkedHashSet<>();
        private final Set<Urn> required = new LinkedHashSet<>();
        private final Map<Urn, Set<Urn>> overridable = new HashMap<>();
        private final Map<Urn, Set<Urn>> notApplied = new HashMap<>();
        private final int bound;

        Model(MergedClosure merged, Edges edges, InForceRequest request) {
            this.merged = merged;
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
        Map<Urn, Set<Urn>> reach() {
            Map<Urn, Set<Urn>> reach = new HashMap<>();
            for (Urn urn : held) {
                reach.put(urn, descendants(urn));
            }
            return reach;
        }

        private Set<Urn> descendants(Urn source) {
            Set<Urn> seen = new LinkedHashSet<>();
            List<Urn> todo = new ArrayList<>(overridable.getOrDefault(source, Set.of()));
            int steps = 0;
            while (!todo.isEmpty()) {
                Urn next = todo.removeLast();
                if (seen.add(next)) {
                    todo.addAll(overridable.getOrDefault(next, Set.of()));
                }
                steps = bounded(steps);
            }
            return seen;
        }

        /** Counts one step of a walk and fails loudly when a walk takes more steps than the closure can need. */
        private int bounded(int steps) {
            int next = steps + 1;
            if (next > bound * bound * bound) {
                throw new IllegalStateException("an in-force walk crossed its bound of " + bound + " (closure size)");
            }
            return next;
        }

        /**
         * The strata 1 to 3 (E4, E5): the candidates less what an overrider among them hides, then the requirement
         * iterated until no required artifact is unsatisfied on the final set (A2).
         */
        Outcome solve() {
            Map<Urn, Set<Urn>> reach = reach();
            Set<Urn> base = base();
            Set<Urn> inForce = minus(base, hiddenBy(base, reach));
            for (int round = 0; round <= bound; round++) {
                Set<Urn> next = step(inForce, reach);
                if (next.equals(inForce)) {
                    return new Outcome(inForce, reach);
                }
                inForce = next;
            }
            throw new IllegalStateException(
                    "the requirement iteration crossed its bound of " + bound + " (closure size) without settling");
        }

        /** One step of the requirement (E5): add what no artifact in force satisfies, then hide what that hides. */
        private Set<Urn> step(Set<Urn> inForce, Map<Urn, Set<Urn>> reach) {
            Set<Urn> with = new LinkedHashSet<>(inForce);
            for (Urn urn : required) {
                if (!locked.contains(urn)
                        && !inForce.contains(urn)
                        && overriderOf(urn, inForce, reach).isEmpty()) {
                    with.add(urn);
                }
            }
            return minus(with, hiddenBy(with, reach));
        }

        /** The smallest artifact of the set that overrides the target, directly or through a chain. */
        private Optional<Urn> overriderOf(Urn target, Set<Urn> among, Map<Urn, Set<Urn>> reach) {
            return among.stream()
                    .filter(overrider -> reach.getOrDefault(overrider, Set.of()).contains(target))
                    .min(Comparator.naturalOrder());
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
                    List.of(),
                    Map.of());
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
                    bucketOf(urn, state, reason));
        }

        /** The artifact in force that satisfies a required artifact that is out of force (E5, E9). */
        private Optional<Urn> satisfiedBy(Urn urn, Outcome outcome) {
            if (!required.contains(urn)
                    || locked.contains(urn)
                    || outcome.inForce().contains(urn)) {
                return Optional.empty();
            }
            return overriderOf(urn, outcome.inForce(), outcome.reach());
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
            if (state == State.ACTIVE || !named) {
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

        private List<OverrideNotApplied> overrideNotApplied(Outcome outcome) {
            List<OverrideNotApplied> entries = new ArrayList<>();
            notApplied.forEach((source, targets) -> {
                if (outcome.inForce().contains(source)) {
                    targets.stream()
                            .filter(outcome.inForce()::contains)
                            .forEach(target -> entries.add(new OverrideNotApplied(target, source)));
                }
            });
            entries.sort(
                    Comparator.comparing(OverrideNotApplied::target).thenComparing(OverrideNotApplied::overriddenBy));
            return entries;
        }

        private static List<Urn> sorted(Set<Urn> urns) {
            return List.copyOf(new TreeSet<>(urns));
        }
    }
}
