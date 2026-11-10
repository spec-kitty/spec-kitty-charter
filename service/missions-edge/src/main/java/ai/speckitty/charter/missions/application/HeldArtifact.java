package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.Objects;

/**
 * An artifact a scope holds, by its kind and id: a delegation candidate links its read when the scope holds it.
 *
 * @param kind the artifact's kind
 * @param id the artifact's id
 */
public record HeldArtifact(ArtifactKind kind, String id) {

    /**
     * Checks the fields.
     *
     * @param kind the kind
     * @param id the id
     */
    public HeldArtifact {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }
}
