package ai.speckitty.charter.profiles.domain;

import java.util.Objects;

/**
 * A reasoning mode an {@link AgentProfile} offers: one item of {@code mode-defaults}.
 *
 * @param mode the mode's name
 * @param description what the mode does
 * @param useCase when to use it ({@code use-case})
 */
public record ModeDefault(String mode, String description, String useCase) {

    /**
     * Checks that every field is present.
     *
     * @param mode the mode
     * @param description the description
     * @param useCase the use case
     */
    public ModeDefault {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(useCase, "useCase");
    }
}
