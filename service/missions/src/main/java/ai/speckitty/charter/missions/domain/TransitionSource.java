package ai.speckitty.charter.missions.domain;

import java.util.List;
import java.util.Objects;

/** The {@code from} of a lifecycle transition (ADR-0050 decision 17): one state, a list of states, or {@code "*"}. */
public sealed interface TransitionSource
        permits TransitionSource.One, TransitionSource.Many, TransitionSource.EveryNonTerminal {

    /**
     * One state.
     *
     * @param state the state's id
     */
    record One(String state) implements TransitionSource {
        /**
         * Checks the state.
         *
         * @param state the state's id
         */
        public One {
            Objects.requireNonNull(state, "state");
        }
    }

    /**
     * A list of states, as authored.
     *
     * @param states the states' ids, in order
     */
    record Many(List<String> states) implements TransitionSource {
        /**
         * Copies the states.
         *
         * @param states the states' ids
         */
        public Many {
            states = List.copyOf(states);
        }
    }

    /**
     * {@code "*"}: every non-terminal state other than the transition's target, as core's {@code blocked} and
     * {@code canceled} edges are.
     */
    record EveryNonTerminal() implements TransitionSource {
        /** The authored spelling. */
        public static final String SPELLING = "*";
    }
}
