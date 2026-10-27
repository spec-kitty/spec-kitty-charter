package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;

/**
 * The pluggable format seam of pack persistence: it turns a persistence shape into text and back. A strategy knows a
 * format and nothing of where the text lives; {@link FilesystemPackStore} owns the paths.
 *
 * <p>Artifacts are read and written through one kind-parameterized pair (ADR-0036): the {@link KindBinding} names the
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
}
