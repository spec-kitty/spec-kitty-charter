package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.domain.PackContents;
import java.util.List;
import java.util.Map;

/**
 * The YAML form of one stored kind (ADR-0037): how its persistence shape is written from scratch and read back from a
 * document that has already passed the kind's bundled schema. {@link YamlFormats} lists one per {@link KindBinding}.
 *
 * @param <D> the persistence shape (DSO)
 */
interface YamlFormat<D> {

    /**
     * The kind; its binding's {@link KindBinding#schemaName()} names the bundled schema the document is validated
     * against.
     *
     * @return the kernel kind
     */
    ArtifactKind kind();

    /**
     * The persistence shape this format reads and writes.
     *
     * @return the DSO class
     */
    Class<D> dsoType();

    /**
     * Maps a schema-valid document to the persistence shape.
     *
     * @param root the document's root mapping
     * @return the shape
     */
    D read(Map<?, ?> root);

    /**
     * Serializes the persistence shape from scratch.
     *
     * @param dso the shape
     * @return the file text
     */
    String write(D dso);

    /**
     * The rules of a schema-valid document that the schema cannot state, each reported as a schema violation with a
     * pointer (ADR-0067 decision 5, ADR-0069 decision 3); empty when the kind has none.
     *
     * @param root the schema-valid root mapping
     * @param file the pack-relative file
     * @return the violations, in document order
     */
    default List<PackContents.SchemaViolation> rules(Map<?, ?> root, String file) {
        return List.of();
    }
}
