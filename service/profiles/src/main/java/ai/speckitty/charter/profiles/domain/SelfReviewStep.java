package ai.speckitty.charter.profiles.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One check an {@link AgentProfile} runs before it hands work off: one step of {@code self-review-protocol}.
 *
 * @param name the step's name
 * @param command the command that runs the check, when it has one
 * @param gate what must hold for the step to pass
 */
public record SelfReviewStep(String name, Optional<String> command, String gate) {

    /**
     * Checks that every field is present.
     *
     * @param name the name
     * @param command the command, or empty
     * @param gate the gate
     */
    public SelfReviewStep {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(gate, "gate");
    }
}
