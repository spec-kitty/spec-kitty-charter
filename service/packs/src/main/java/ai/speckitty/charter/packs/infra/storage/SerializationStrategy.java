package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.PackContents.FileRead;

/**
 * The pluggable format seam of pack persistence: it turns a persistence shape into text and back. A strategy knows a
 * format and nothing of where the text lives; {@link FilesystemPackStore} owns the paths.
 *
 * <p>A read never throws on content: text that is not the format or breaks its schema is a {@link FileRead.Unreadable}.
 */
public interface SerializationStrategy {

    /**
     * Serializes a tactic from scratch.
     *
     * @param tactic the tactic to write
     * @return the file text
     */
    String writeTactic(TacticDso tactic);

    /**
     * Reads a tactic file.
     *
     * @param text the file text
     * @param file the pack-relative file name, named in a failure
     * @return the tactic, or why the text is unreadable
     */
    FileRead<TacticDso> readTactic(String text, String file);

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
}
