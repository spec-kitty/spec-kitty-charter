package ai.speckitty.charter.packs.infra.storage;

import java.util.Objects;

/**
 * One {@code principles} item of a paradigm or a styleguide file, as written (ADR-0067).
 *
 * @param name the principle's name
 * @param description its description
 */
record PrincipleDso(String name, String description) {
    PrincipleDso {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
    }
}
