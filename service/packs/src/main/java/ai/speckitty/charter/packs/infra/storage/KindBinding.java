package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownDocument;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
     * <p>The companion declares its content form (#194): {@link ContentForm#TEXT}, read and written byte for byte as
     * today, or {@link ContentForm#MARKDOWN}, read into its model, validated against the structure schema it names
     * and written in the canonical form ({@link Companions}). The form reuses {@link ContentForm} rather than a
     * dedicated companion-form enum: that is the smaller change, since the gate and the store already dispatch
     * exhaustively over {@code ContentForm}, and a companion's form then means exactly what a {@code files} rule's
     * does. A companion admits only {@code TEXT} and {@code MARKDOWN}; declaring {@code TEXT} here does not build that
     * form for {@code files} rules, which the table still refuses ({@link PackFileKind}).
     *
     * @param path the path relative to the artifact's file, exactly as the artifact declares it
     * @param content what the file holds; empty until the store has read it
     * @param form how the file is read, held, written and compared: {@code TEXT} or {@code MARKDOWN}
     * @param structureSchema the schema a Markdown companion's model is validated with, as {@link PackSchemas} names
     *     it; present exactly when the form is {@code MARKDOWN}
     */
    record Companion(
            String path, Optional<CompanionContent> content, ContentForm form, Optional<String> structureSchema) {
        /**
         * Checks the fields: the form is one a companion admits, the structure schema is present exactly for Markdown,
         * and content, when present, has the declared form.
         *
         * @param path the relative path
         * @param content the content, possibly empty
         * @param form the declared form
         * @param structureSchema the structure schema, for Markdown
         * @throws IllegalArgumentException when the form is not {@code TEXT} or {@code MARKDOWN}, the structure schema
         *     does not match the form, or the content has another form
         */
        public Companion {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(form, "form");
            Objects.requireNonNull(structureSchema, "structureSchema");
            boolean admitted = switch (form) {
                case TEXT -> structureSchema.isEmpty();
                case MARKDOWN -> structureSchema.isPresent();
                case YAML, BYTES -> false;
            };
            if (!admitted) {
                throw new IllegalArgumentException("companion " + path + " is declared " + form.token()
                        + structureSchema
                                .map(schema -> " with structure schema " + schema)
                                .orElse("")
                        + ": a companion is text with no structure schema, or markdown with one");
            }
            content.filter(held -> held.form() != form).ifPresent(held -> {
                throw new IllegalArgumentException("companion " + path + " is declared " + form.token() + " but holds "
                        + held.form().token());
            });
        }

        /**
         * A text companion, the form every companion had before #194.
         *
         * @param path the relative path
         * @param content the text, possibly empty
         */
        Companion(String path, Optional<CompanionContent> content) {
            this(path, content, ContentForm.TEXT, Optional.empty());
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

    /**
     * The structure schemas this kind's Markdown companions are validated with, as {@link Companion#structureSchema()}
     * names them: the one place a companion structure is declared, from which {@link KindBindings} derives the set
     * the pack listing and validate mode consult. A kind that overrides it must return {@code true} from
     * {@link #hasCompanions}.
     *
     * @return the structure schemas; none by default
     */
    default Set<String> companionStructures() {
        return Set.of();
    }

    /**
     * Checks a stored Markdown companion against the rules its structure schema cannot express, on the companion
     * alone: validate mode reads a companion file without the artifact that names it (#194).
     *
     * @param structureSchema one of {@link #companionStructures()}
     * @param document the parsed companion
     * @throws IllegalArgumentException naming the first rule the companion breaks
     */
    default void checkCompanion(String structureSchema, MarkdownDocument document) {}

    /**
     * Whether an artifact's {@code id} must equal its file stem, the file name less {@code .<fileToken>.yaml}
     * (ADR-0036 S1, ADR-0043). The store's load and validate mode apply it alike ({@link FileStems}).
     *
     * @return {@code false} by default
     */
    default boolean idIsFileStem() {
        return false;
    }
}
