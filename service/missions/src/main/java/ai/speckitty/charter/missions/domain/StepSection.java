package ai.speckitty.charter.missions.domain;

import java.util.Objects;

/**
 * A titled section of a {@link StepDocument}, in the order the document holds it.
 *
 * @param title the section's heading text
 * @param content the section's body
 */
public record StepSection(String title, String content) {

    /**
     * Checks that both parts are present.
     *
     * @param title the heading text
     * @param content the body
     */
    public StepSection {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(content, "content");
    }
}
