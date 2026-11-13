package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A lifecycle of a pack (ADR-0050 decisions 17 to 21): a declared, reusable state machine, run by a mission step as a
 * submachine, once per work package. It declares its states, its initial state and its transitions, each behind guard
 * entries (ADR-0054). The charter declares the machine and checks it is well formed; the client runs and enforces it.
 * Two lifecycles are equal when they have the same identity.
 *
 * <p>A transition's {@code from} is one state, a list of states, or {@code "*"} for every non-terminal state. A
 * terminal state has no transition out, and {@code "*"} leaves out the transition's own target. Action points ({@code on_enter}, {@code on_exit}, {@code on_transition}) come
 * with the event registry (work breakdown item 7).
 */
public final class Lifecycle implements CharterArtifact {

    private static final ModularRuleset<Lifecycle> RULES = ModularRuleset.<Lifecycle>emptyRules()
            .must(Constraints.of("A lifecycle needs a title that is not blank.", l -> !l.title.isBlank()))
            .must(Constraints.of(
                    "A lifecycle needs a schema version that is not blank.", l -> !l.schemaVersion.isBlank()))
            .must(Constraints.of("A lifecycle declares at least one state.", l -> !l.states.isEmpty()))
            .must(Constraints.of("A lifecycle declares each state once.", Lifecycle::statesAreUnique))
            .must(Constraints.of(
                    "A lifecycle's initial state is one of its states, and not a terminal one.",
                    l -> l.state(l.initial).map(s -> !s.isTerminal()).orElse(false)))
            .must(Constraints.of("A lifecycle declares at least one transition.", l -> !l.transitions.isEmpty()))
            .must(Constraints.of(
                    "A lifecycle transition names only states the lifecycle declares.", Lifecycle::statesAreDeclared))
            .must(Constraints.of(
                    "A lifecycle transition's from list names each state once.", Lifecycle::sourcesAreUnique))
            .must(Constraints.of(
                    "A lifecycle transition leaves at least one state: \"*\" reaches a non-terminal state other than"
                            + " its target.",
                    l -> l.transitions.stream().noneMatch(t -> l.sources(t).isEmpty())))
            .must(Constraints.of(
                    "No lifecycle transition leaves a terminal state.", Lifecycle::noTransitionLeavesATerminal))
            .must(Constraints.of(
                    "A lifecycle declares each transition once: no two share a from state, a to state and an event.",
                    Lifecycle::transitionsAreUnique))
            .must(Constraints.of(
                    "A lifecycle transition names each guard once.", Lifecycle::guardsAreUniquePerTransition));

    private final LifecycleIdentifier id;
    private final PackIdentifier packId;
    private final String schemaVersion;
    private final String title;
    private final Optional<String> description;
    private final String initial;
    private final List<LifecycleState> states;
    private final List<LifecycleTransition> transitions;

    /**
     * Reconstructs a lifecycle from its parts.
     *
     * @param id the lifecycle's identity
     * @param packId the pack that owns it
     * @param schemaVersion the schema version text
     * @param title the display label
     * @param description the description, possibly empty
     * @param initial the initial state's id
     * @param states the states, in authored order
     * @param transitions the transitions, in authored order
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    public Lifecycle(
            LifecycleIdentifier id,
            PackIdentifier packId,
            String schemaVersion,
            String title,
            Optional<String> description,
            String initial,
            List<LifecycleState> states,
            List<LifecycleTransition> transitions) {
        this.id = Objects.requireNonNull(id, "id");
        this.packId = Objects.requireNonNull(packId, "packId");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.initial = Objects.requireNonNull(initial, "initial");
        this.states = List.copyOf(states);
        this.transitions = List.copyOf(transitions);
        RULES.guard(this);
        requireWellFormedMachine();
    }

    /** The checks whose refusal names the offender; they read the transitions, so they follow the ruleset. */
    private void requireWellFormedMachine() {
        if (states.stream().noneMatch(LifecycleState::isTerminal)) {
            throw new ConstraintBreachException(Reason.failed("A lifecycle declares at least one terminal state."));
        }
        Set<String> reached = reachableFromInitial();
        states.stream().filter(s -> !reached.contains(s.id())).findFirst().ifPresent(s -> {
            throw new ConstraintBreachException(
                    Reason.failed("state '" + s.id() + "' is not reachable from the initial state"));
        });
        nondeterminism().ifPresent(message -> {
            throw new ConstraintBreachException(Reason.failed(message));
        });
    }

    /**
     * The lifecycle's identity.
     *
     * @return the identifier
     */
    public LifecycleIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.LIFECYCLE;
    }

    @Override
    public PackIdentifier packId() {
        return packId;
    }

    @Override
    public String localId() {
        return id.value();
    }

    @Override
    public Optional<String> schemaVersion() {
        return Optional.of(schemaVersion);
    }

    @Override
    public String label() {
        return title;
    }

    @Override
    public Optional<String> description() {
        return description;
    }

    /**
     * The lifecycle's {@code title}.
     *
     * @return the title
     */
    public String title() {
        return title;
    }

    /**
     * The initial state's id.
     *
     * @return the state id
     */
    public String initial() {
        return initial;
    }

    /**
     * The states, in authored order.
     *
     * @return an immutable list
     */
    public List<LifecycleState> states() {
        return states;
    }

    /**
     * The transitions, in authored order.
     *
     * @return an immutable list
     */
    public List<LifecycleTransition> transitions() {
        return transitions;
    }

    /**
     * The ids of the terminal states, in authored order.
     *
     * @return an immutable list
     */
    public List<String> terminalStates() {
        return states.stream()
                .filter(LifecycleState::isTerminal)
                .map(LifecycleState::id)
                .toList();
    }

    /**
     * The transitions that leave a state, in authored order, {@code "*"} expanded.
     *
     * @param state a state id
     * @return the transitions whose source includes the state; empty for a terminal or unknown state
     */
    public List<LifecycleTransition> edgesFrom(String state) {
        return transitions.stream().filter(t -> sources(t).contains(state)).toList();
    }

    /**
     * The states a transition leaves from, {@code "*"} expanded to every non-terminal state other than the target, so
     * {@code "*" -> blocked} adds no {@code blocked -> blocked} loop.
     *
     * @param transition a transition of this lifecycle
     * @return the state ids, in order
     */
    public List<String> sources(LifecycleTransition transition) {
        return switch (transition.from()) {
            case TransitionSource.One one -> List.of(one.state());
            case TransitionSource.Many many -> many.states();
            case TransitionSource.EveryNonTerminal any ->
                states.stream()
                        .filter(s -> !s.isTerminal() && !s.id().equals(transition.to()))
                        .map(LifecycleState::id)
                        .toList();
        };
    }

    private Optional<LifecycleState> state(String stateId) {
        return states.stream().filter(s -> s.id().equals(stateId)).findFirst();
    }

    private boolean statesAreUnique() {
        Set<String> seen = new HashSet<>();
        return states.stream().allMatch(s -> seen.add(s.id()));
    }

    private boolean statesAreDeclared() {
        return transitions.stream()
                .allMatch(t -> state(t.to()).isPresent() && sources(t).stream().allMatch(s -> state(s).isPresent()));
    }

    private boolean sourcesAreUnique() {
        return transitions.stream()
                .allMatch(t -> !(t.from() instanceof TransitionSource.Many many)
                        || new HashSet<>(many.states()).size() == many.states().size());
    }

    private boolean noTransitionLeavesATerminal() {
        return transitions.stream()
                .allMatch(t -> sources(t).stream()
                        .allMatch(s -> state(s).map(st -> !st.isTerminal()).orElse(true)));
    }

    private boolean transitionsAreUnique() {
        Set<List<String>> seen = new HashSet<>();
        for (LifecycleTransition t : transitions) {
            for (String source : sources(t)) {
                List<String> key = new ArrayList<>(List.of(source, t.to()));
                // An absent event means next (ADR-0050 decision 8), so the two spellings are one transition.
                key.add(t.event().orElse("next"));
                if (!seen.add(key)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The states some path of transitions reaches from the initial state, itself included. */
    private Set<String> reachableFromInitial() {
        Set<String> reached = new HashSet<>(List.of(initial));
        java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>(reached);
        while (!pending.isEmpty()) {
            for (LifecycleTransition t : edgesFrom(pending.poll())) {
                if (reached.add(t.to())) {
                    pending.add(t.to());
                }
            }
        }
        return reached;
    }

    /** The first (state, event) pair, an absent event being {@code next}, that leaves for two different targets. */
    private Optional<String> nondeterminism() {
        java.util.Map<List<String>, String> targets = new java.util.HashMap<>();
        for (LifecycleTransition t : transitions) {
            for (String source : sources(t)) {
                String event = t.event().orElse("next");
                String other = targets.putIfAbsent(List.of(source, event), t.to());
                if (other != null && !other.equals(t.to())) {
                    return Optional.of(
                            "'" + source + "' on '" + event + "' leads to both '" + other + "' and '" + t.to() + "'");
                }
            }
        }
        return Optional.empty();
    }

    private boolean guardsAreUniquePerTransition() {
        return transitions.stream().allMatch(t -> {
            Set<GuardReference> seen = new HashSet<>();
            return t.guards().stream().allMatch(g -> seen.add(g.ref()));
        });
    }

    /** Entity identity: equal when the identifier is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof Lifecycle that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Lifecycle[" + id.value() + " of pack " + packId.value() + "]";
    }
}
