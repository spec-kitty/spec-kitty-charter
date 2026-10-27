package ai.speckitty.charter.profiles.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * What an {@link AgentProfile} focuses on and what it avoids: its {@code specialization}. The three optional parts
 * are empty when the profile does not declare them; the schema then reads them as the empty text.
 *
 * @param primaryFocus the {@code primary-focus}
 * @param secondaryAwareness the {@code secondary-awareness}, when declared
 * @param avoidanceBoundary the {@code avoidance-boundary}, when declared
 * @param successDefinition the {@code success-definition}, when declared
 */
public record Specialization(
        String primaryFocus,
        Optional<String> secondaryAwareness,
        Optional<String> avoidanceBoundary,
        Optional<String> successDefinition) {

    /**
     * Checks that every part is present.
     *
     * @param primaryFocus the primary focus
     * @param secondaryAwareness the secondary awareness, or empty
     * @param avoidanceBoundary the avoidance boundary, or empty
     * @param successDefinition the success definition, or empty
     */
    public Specialization {
        Objects.requireNonNull(primaryFocus, "primaryFocus");
        Objects.requireNonNull(secondaryAwareness, "secondaryAwareness");
        Objects.requireNonNull(avoidanceBoundary, "avoidanceBoundary");
        Objects.requireNonNull(successDefinition, "successDefinition");
    }
}
