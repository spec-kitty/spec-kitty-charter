package ai.speckitty.charter.packs.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A positive code pattern within a styleguide; the examples are optional.
 *
 * @param name the pattern's name
 * @param description what the pattern is
 * @param badExample a counter-example, when stated
 * @param goodExample an example, when stated
 */
public record StyleguidePattern(
        String name, String description, Optional<String> badExample, Optional<String> goodExample) {

    /** Checks that every component is present. */
    public StyleguidePattern {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(badExample, "badExample");
        Objects.requireNonNull(goodExample, "goodExample");
    }
}
