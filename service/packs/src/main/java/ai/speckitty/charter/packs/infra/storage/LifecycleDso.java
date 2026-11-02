package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ConfigValue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of a lifecycle: a flat mirror of the fields of a {@code *.lifecycle.yaml} (ADR-0050 decision
 * 17), not the domain entity. Its record equality is the round-trip fidelity assertion: lists keep their order, an
 * absent optional differs from a present one, and a transition's {@code from} keeps its authored form.
 *
 * @param id the lifecycle's {@code id}
 * @param schemaVersion the {@code schema_version}
 * @param title the {@code title}
 * @param description the {@code description}, when stated
 * @param initial the {@code initial} state
 * @param states the {@code states}, in authored order
 * @param transitions the {@code transitions}, in authored order
 */
record LifecycleDso(
        String id,
        String schemaVersion,
        String title,
        Optional<String> description,
        String initial,
        List<State> states,
        List<Transition> transitions) {

    /**
     * Checks that the required fields are present and copies the lists.
     *
     * @param id the id
     * @param schemaVersion the schema version
     * @param title the title
     * @param description the description
     * @param initial the initial state
     * @param states the states
     * @param transitions the transitions
     */
    LifecycleDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(initial, "initial");
        states = List.copyOf(states);
        transitions = List.copyOf(transitions);
    }

    /**
     * One state.
     *
     * @param id the {@code id}
     * @param title the {@code title}
     * @param description the {@code description}, when stated
     * @param terminal the {@code terminal}, when stated
     */
    record State(String id, String title, Optional<String> description, Optional<Boolean> terminal) {
        /**
         * Checks that every component is present.
         *
         * @param id the id
         * @param title the title
         * @param description the description
         * @param terminal the terminal flag
         */
        State {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(terminal, "terminal");
        }
    }

    /**
     * One transition. Its {@code from} is held in the form it was authored in: one text (a state id or {@code *}), or
     * a list.
     *
     * @param from the {@code from} as one text, when authored as one
     * @param fromList the {@code from} as a list, when authored as one; empty otherwise
     * @param to the {@code to}
     * @param event the {@code event}, when stated
     * @param guard the {@code guard} entries, in authored order
     */
    record Transition(
            Optional<String> from, List<String> fromList, String to, Optional<String> event, List<GuardEntry> guard) {
        /**
         * Checks that exactly one form of {@code from} is held and copies the lists.
         *
         * @param from the one-text from
         * @param fromList the list from
         * @param to the to
         * @param event the event
         * @param guard the guard entries
         */
        Transition {
            Objects.requireNonNull(from, "from");
            fromList = List.copyOf(fromList);
            Objects.requireNonNull(to, "to");
            Objects.requireNonNull(event, "event");
            guard = List.copyOf(guard);
            if (from.isPresent() == !fromList.isEmpty()) {
                throw new IllegalArgumentException("a transition's from is one text or a non-empty list");
            }
        }
    }

    /**
     * One guard entry.
     *
     * @param ref the {@code ref}
     * @param config the {@code config}, as the tree it was authored as, when stated
     * @param blocking the {@code blocking}, when stated
     */
    record GuardEntry(String ref, Optional<ConfigValue> config, Optional<Boolean> blocking) {
        /**
         * Checks that every component is present.
         *
         * @param ref the ref
         * @param config the config
         * @param blocking the blocking flag
         */
        GuardEntry {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(blocking, "blocking");
        }
    }
}
