package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;

/**
 * The pluggable format seam of pack persistence: it turns a persistence shape into text and back. A strategy knows a
 * format and nothing of where the text lives; {@link FilesystemPackStore} owns the paths.
 *
 * <p>Artifacts are read and written through one kind-parameterized pair (ADR-0037): the {@link KindBinding} names the
 * kind and its persistence shape, and the strategy supplies the format for every bound kind. A read never throws on
 * content: text that is not the format or breaks its schema is a {@link FileRead.Unreadable}.
 */
public interface SerializationStrategy {

    /**
     * Serializes an artifact's persistence shape from scratch.
     *
     * @param kind the kind's binding
     * @param dso the shape to write
     * @param <D> the kind's persistence shape
     * @return the file text
     */
    <D> String write(KindBinding<D, ?> kind, D dso);

    /**
     * Reads an artifact file of one kind.
     *
     * @param kind the kind's binding
     * @param text the file text
     * @param file the pack-relative file name, named in a failure
     * @param <D> the kind's persistence shape
     * @return the shape, or why the text is unreadable
     */
    <D> FileRead<D> read(KindBinding<D, ?> kind, String text, String file);

    /**
     * Serializes a descriptor from scratch.
     *
     * @param descriptor the descriptor to write
     * @return the file text
     */
    String writeDescriptor(PackDescriptorDso descriptor);

    /**
     * Reads a {@code pack.yaml}.
     *
     * @param text the file text
     * @return the descriptor, or why the text is unreadable
     */
    FileRead<PackDescriptorDso> readDescriptor(String text);

    /**
     * Serializes a DRG document from scratch, every field it holds written.
     *
     * @param document the document to write
     * @return the file text
     */
    String writeGraph(GraphDocument document);

    /**
     * Reads a DRG document with every field its schema allows.
     *
     * @param text the file text
     * @param shape which of the two shapes the document has
     * @param file the pack-relative file name, named in a failure and kept on the document
     * @return the document, or why the text is unreadable
     */
    FileRead<GraphDocument> readGraph(String text, Shape shape, String file);

    /**
     * Serializes an activation preset from scratch (ADR-0043).
     *
     * @param preset the preset to write
     * @return the file text
     */
    String writePreset(PresetDso preset);

    /**
     * Reads a {@code presets/<id>.yaml} against the bundled {@code preset} schema. Whether the id is the file stem is
     * the store's rule ({@link PresetFiles#stemMismatch}), not the format's.
     *
     * @param text the file text
     * @param file the pack-relative file name, named in a failure
     * @return the preset, or why the text is unreadable
     */
    FileRead<PresetDso> readPreset(String text, String file);

    /**
     * Serializes a pack manifest from scratch (ADR-0043), its derived fields as given: the caller stamps them.
     *
     * @param manifest the manifest to write
     * @return the file text
     */
    String writeManifest(PackManifestDso manifest);

    /**
     * Reads a {@code pack-manifest.yaml} against the bundled {@code pack-manifest} schema. Its derived fields are taken
     * as written and not checked ({@link PackManifests#stale} is the check).
     *
     * @param text the file text
     * @param file the pack-relative file name, named in a failure
     * @return the manifest, or why the text is unreadable
     */
    FileRead<PackManifestDso> readManifest(String text, String file);
}
