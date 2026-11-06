package ai.speckitty.charter.activations.domain;

import java.util.Objects;

/**
 * One activation as the repository holds it: its id, its document and the document's revision (FR-019).
 *
 * @param id the activation's id
 * @param document the canonical document
 * @param revision the document's revision, {@link ActivationDocument#revision()}
 */
public record StoredActivation(ActivationId id, ActivationDocument document, Revision revision) {

    /**
     * Requires every field.
     *
     * @param id the activation's id
     * @param document the canonical document
     * @param revision the document's revision
     */
    public StoredActivation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(revision, "revision");
    }

    /**
     * An activation at its document's own revision.
     *
     * @param id the activation's id
     * @param document the canonical document
     * @return the stored activation
     */
    public static StoredActivation of(ActivationId id, ActivationDocument document) {
        return new StoredActivation(id, document, document.revision());
    }
}
