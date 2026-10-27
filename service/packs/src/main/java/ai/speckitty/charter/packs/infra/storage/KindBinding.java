package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;

/**
 * How one artifact kind is stored (ADR-0037): its persistence shape and the mapping between that shape and the domain
 * entity. A binding knows no format and no path; the {@link SerializationStrategy} turns the shape into text and
 * {@link FilesystemPackStore} places the text. {@link KindBindings} lists every bound kind.
 *
 * @param <D> the persistence shape (DSO)
 * @param <E> the domain entity
 */
interface KindBinding<D, E extends CharterArtifact> {

    /**
     * The kind this binding stores.
     *
     * @return the kernel kind
     */
    ArtifactKind kind();

    /**
     * The domain entity type of the kind.
     *
     * @return the entity class
     */
    Class<E> entityType();

    /**
     * The kind's spelling in a file name, {@code <id>.<fileToken>.yaml}, as {@code file-kinds.yaml} matches it. It is
     * the kind's token unless the pack format spells it otherwise (an agent profile is {@code *.agent.yaml}).
     *
     * @return the file-name token
     */
    default String fileToken() {
        return kind().token();
    }

    /**
     * The name of the schema that validates the kind's files, as {@code file-kinds.yaml} maps them. It is the kind's
     * token unless the pack schemas spell it otherwise (an agent profile is {@code agent-profile}).
     *
     * @return the schema name
     */
    default String schemaName() {
        return kind().token();
    }

    /**
     * The key of the field that holds an artifact's id in its file. It is {@code id} unless the kind's schema names it
     * otherwise (an agent profile's is {@code profile-id}).
     *
     * @return the id key
     */
    default String idKey() {
        return "id";
    }

    /**
     * The file name a new artifact of this kind is created under.
     *
     * @param elementId the artifact's id
     * @return {@code <id>.<fileToken>.yaml}
     */
    default String fileName(String elementId) {
        return elementId + "." + fileToken() + ".yaml";
    }

    /**
     * The persistence shape of the kind.
     *
     * @return the DSO class
     */
    Class<D> dsoType();

    /**
     * The artifact id a persistence shape declares.
     *
     * @param dso the shape
     * @return its schema {@code id}
     */
    String idOf(D dso);

    /**
     * Builds the domain entity from a persistence shape.
     *
     * @param pack the owning pack
     * @param dso the shape
     * @return the entity
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the entity breaks an invariant
     * @throws IllegalArgumentException when a value has no domain counterpart, such as an unknown token
     */
    E toEntity(PackIdentifier pack, D dso);

    /**
     * Maps a domain entity to its persistence shape.
     *
     * @param entity the entity
     * @return the shape
     */
    D toDso(E entity);

    /**
     * Maps an artifact of this binding's kind to its persistence shape.
     *
     * @param artifact the artifact
     * @return the shape
     * @throws ClassCastException when the artifact is not of this binding's entity type
     */
    default D dsoOf(CharterArtifact artifact) {
        return toDso(entityType().cast(artifact));
    }
}
