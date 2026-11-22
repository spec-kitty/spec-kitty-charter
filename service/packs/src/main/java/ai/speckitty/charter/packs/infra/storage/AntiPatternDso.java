package ai.speckitty.charter.packs.infra.storage;

import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of an anti-pattern: a flat mirror of the fields of an {@code *.anti-pattern.yaml} (ADR-0052
 * decision 8, as amended by ADR-0053 decision 30), not the domain entity. Its record equality is the round-trip
 * fidelity assertion: an absent {@code good_example} differs from a present one.
 *
 * @param schemaVersion the {@code schema_version}
 * @param id the anti-pattern's {@code id}
 * @param title the {@code title}
 * @param description the {@code description}
 * @param severity the {@code severity} token, as authored
 * @param badExample the {@code bad_example}
 * @param goodExample the {@code good_example}, when stated
 */
record AntiPatternDso(
        String schemaVersion,
        String id,
        String title,
        String description,
        String severity,
        String badExample,
        Optional<String> goodExample) {

    /**
     * Checks that every component is present.
     *
     * @param schemaVersion the schema version
     * @param id the id
     * @param title the title
     * @param description the description
     * @param severity the severity token
     * @param badExample the bad example
     * @param goodExample the good example
     */
    AntiPatternDso {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(badExample, "badExample");
        Objects.requireNonNull(goodExample, "goodExample");
    }
}
