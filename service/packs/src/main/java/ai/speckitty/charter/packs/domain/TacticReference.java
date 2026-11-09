package ai.speckitty.charter.packs.domain;

import java.util.Objects;

/**
 * A reference carried verbatim on a tactic or one of its steps.
 *
 * @param name the reference's {@code name}
 * @param type the artifact-kind token, kept as plain text and never resolved into a relationship
 * @param id the referenced artifact's {@code id}
 * @param when the {@code when} text
 */
public record TacticReference(String name, String type, String id, String when) {
    /**
     * Checks that every field is present.
     *
     * @param name the reference's name
     * @param type the artifact-kind token
     * @param id the referenced id
     * @param when the when text
     */
    public TacticReference {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(when, "when");
    }
}
