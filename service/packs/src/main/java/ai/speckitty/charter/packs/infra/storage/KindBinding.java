package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * How one artifact kind is stored (ADR-0037): its persistence shape and the mapping between that shape and the domain
 * entity. A binding knows no format and no path but its {@linkplain #companions companions'}, which are relative to
 * its artifact's file (ADR-0039); the {@link SerializationStrategy} turns the shape into text and
 * {@link FilesystemPackStore} places the text. {@link KindBindings} lists every bound kind.
 *
 * @param <D> the persistence shape (DSO)
 * @param <E> the domain entity
 */
interface KindBinding<D, E extends CharterArtifact> {

    /**
     * A file an artifact names, read and written with it (ADR-0039): a skill's prompt body is one.
     *
     * @param path the path relative to the artifact's file, exactly as the artifact declares it
     * @param content what the file holds; empty until the store has read it
     */
    record Companion(String path, Optional<CompanionContent> content) {
        /**
         * Checks the fields.
         *
         * @param path the relative path
         * @param content the content, possibly empty
         */
        public Companion {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(content, "content");
        }
    }

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

    /**
     * Whether artifacts of this kind can name companion files. A kind that cannot is never asked for them, so the
     * source fingerprint does not read its files to find any, and a replaced artifact's old companions are not looked
     * up. A kind that overrides {@link #companions} must return {@code true} here; a registry test holds every bound
     * kind to that.
     *
     * @return {@code false} by default
     */
    default boolean hasCompanions() {
        return false;
    }

    /**
     * The companion files a persistence shape names, with their content when the shape holds it. One list names both
     * the paths and the contents, so the two cannot drift.
     *
     * @param dso the shape
     * @return the companions, in the order the artifact names them; none by default
     */
    default List<Companion> companions(D dso) {
        return List.of();
    }

    /**
     * The persistence shape with its companions' contents filled in, once the store has read them.
     *
     * @param dso the shape as its file declares it
     * @param contents the content of each companion, keyed by the {@link Companion#path()} {@link #companions} named
     * @return the shape holding the contents; {@code dso} itself by default
     */
    default D withCompanions(D dso, Map<String, CompanionContent> contents) {
        return dso;
    }
}
