package ai.speckitty.charter.missions.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One state of a {@link Lifecycle}.
 *
 * @param id the state's id
 * @param title the state's title
 * @param description the state's description, when stated
 * @param terminal whether the state ends the machine, as authored, when stated
 */
public record LifecycleState(String id, String title, Optional<String> description, Optional<Boolean> terminal) {

    /**
     * Checks the parts.
     *
     * @param id the id
     * @param title the title
     * @param description the description, possibly empty
     * @param terminal the terminal flag, possibly empty
     */
    public LifecycleState {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(terminal, "terminal");
    }

    /**
     * Whether no transition leaves the state.
     *
     * @return {@code true} when the state is declared {@code terminal: true}
     */
    public boolean isTerminal() {
        return terminal.orElse(false);
    }
}
