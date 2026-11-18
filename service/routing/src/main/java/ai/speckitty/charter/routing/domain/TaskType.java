package ai.speckitty.charter.routing.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A category of task a catalog scores its models against (ADR-0080 decision 5).
 *
 * @param id the task type's id, a letter-first kebab slug, unique in its catalog
 * @param title its title
 * @param description what the task type covers, when written
 * @param qualitySensitivity how much quality matters for it, when written
 * @param costSensitivity how much cost matters for it, when written
 */
public record TaskType(
        String id,
        String title,
        Optional<String> description,
        Optional<Grade> qualitySensitivity,
        Optional<Grade> costSensitivity) {

    /**
     * Checks the id and the texts.
     *
     * @param id the id
     * @param title the title
     * @param description the description, possibly empty
     * @param qualitySensitivity the quality sensitivity, possibly empty
     * @param costSensitivity the cost sensitivity, possibly empty
     */
    public TaskType {
        Checks.id(Objects.requireNonNull(id, "id"));
        Checks.text(Objects.requireNonNull(title, "title"));
        Objects.requireNonNull(description, "description").ifPresent(Checks::text);
        Objects.requireNonNull(qualitySensitivity, "qualitySensitivity");
        Objects.requireNonNull(costSensitivity, "costSensitivity");
    }
}
