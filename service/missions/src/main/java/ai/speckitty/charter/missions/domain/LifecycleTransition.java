package ai.speckitty.charter.missions.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One transition of a {@link Lifecycle}: from a source, to a state, on an event (the {@code event} key), behind guards (ADR-0050 decision 17,
 * ADR-0054 decision 2). Action points are added with the event registry (work breakdown item 7).
 *
 * @param from where the transition leaves from
 * @param to the state it enters
 * @param event the triggering event, a free token the client interprets, when stated
 * @param guards the guards it names, in order
 */
public record LifecycleTransition(TransitionSource from, String to, Optional<String> event, List<GuardEntry> guards) {

    /**
     * Checks the parts and copies the guards.
     *
     * @param from the source
     * @param to the target state
     * @param event the event, possibly empty
     * @param guards the guard entries
     */
    public LifecycleTransition {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(event, "event");
        guards = List.copyOf(guards);
    }

    /**
     * A short name for messages.
     *
     * @return {@code <from> -> <to>}, with a list joined by commas
     */
    public String describe() {
        String source = switch (from) {
            case TransitionSource.One one -> one.state();
            case TransitionSource.Many many -> String.join(",", many.states());
            case TransitionSource.EveryNonTerminal any -> TransitionSource.EveryNonTerminal.SPELLING;
        };
        return source + " -> " + to;
    }
}
