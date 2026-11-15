package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.Objects;

/**
 * An artifact a scope holds, by its kind and a reference to it: a delegation candidate links its read when the scope
 * holds it. A scope's holdings name each artifact by its bare id; a held candidate ({@link
 * MissionTypeHolding#heldCandidates}) names it as the candidate is written, {@code [<pack>::]<id>}.
 *
 * @param kind the artifact's kind
 * @param id the artifact's bare id, or the candidate as written, {@code [<pack>::]<id>}
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
