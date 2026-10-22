package ai.speckitty.charter.packs.domain;

import java.util.Objects;

/**
 * An anti-pattern within a styleguide; unlike a {@link StyleguidePattern}, all four parts are required.
 *
 * @param name the anti-pattern's name
 * @param description what the anti-pattern is
 * @param badExample the offending example
 * @param goodExample the corrected example
 */
public record StyleguideAntiPattern(String name, String description, String badExample, String goodExample) {

    /** Checks that every component is present. */
    public StyleguideAntiPattern {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(badExample, "badExample");
        Objects.requireNonNull(goodExample, "goodExample");
    }
}
