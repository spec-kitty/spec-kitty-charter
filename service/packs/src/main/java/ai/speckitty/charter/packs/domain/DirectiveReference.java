package ai.speckitty.charter.packs.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A typed cross-artefact reference carried on a directive.
 *
 * @param type the artifact-kind token of the target
 * @param id the referenced artefact's id
 * @param when the activation trigger, if authored
 * @param reason the rationale, if authored
 */
public record DirectiveReference(String type, String id, Optional<String> when, Optional<String> reason) {
    /**
     * Checks that the fields are present.
     *
     * @param type the artifact-kind token
     * @param id the referenced id
     * @param when the when text, empty when absent
     * @param reason the reason text, empty when absent
     */
    public DirectiveReference {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(reason, "reason");
    }
}
