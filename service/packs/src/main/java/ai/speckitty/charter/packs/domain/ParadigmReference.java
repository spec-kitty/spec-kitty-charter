package ai.speckitty.charter.packs.domain;

import java.util.Objects;

/**
 * A typed cross-artifact reference from a paradigm. Unlike a directive's reference it carries no {@code when} or
 * {@code reason}: the paradigm schema defines only {@code type} and {@code id}.
 *
 * @param type the artifact-kind token of the target
 * @param id the referenced artifact's {@code id}
 */
public record ParadigmReference(String type, String id) {
    /**
     * Checks that both fields are present.
     *
     * @param type the artifact-kind token
     * @param id the referenced id
     */
    public ParadigmReference {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }
}
