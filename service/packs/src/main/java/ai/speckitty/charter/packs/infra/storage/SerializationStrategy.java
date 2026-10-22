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

    /**
     * Serializes a directive from scratch.
     *
     * @param directive the directive to write
     * @return the file text
     */
    default String writeDirective(DirectiveDso directive) {
        throw new UnsupportedOperationException("writeDirective is not supported by this strategy");
    }

    /**
     * Reads a directive file.
     *
     * @param text the file text
     * @param file the pack-relative file name, named in a failure
     * @return the directive, or why the text is unreadable
     */
    default FileRead<DirectiveDso> readDirective(String text, String file) {
        throw new UnsupportedOperationException("readDirective is not supported by this strategy");
    }

    /**
     * Serializes a styleguide from scratch.
     *
     * @param styleguide the styleguide to write
     * @return the file text
     */
    default String writeStyleguide(StyleguideDso styleguide) {
        throw new UnsupportedOperationException("writeStyleguide is not supported by this strategy");
    }

    /**
     * Reads a styleguide file.
     *
     * @param text the file text
     * @param file the pack-relative file name, named in a failure
     * @return the styleguide, or why the text is unreadable
     */
    default FileRead<StyleguideDso> readStyleguide(String text, String file) {
        throw new UnsupportedOperationException("readStyleguide is not supported by this strategy");
    }

    /**
     * Serializes a paradigm from scratch.
     *
     * @param paradigm the paradigm to write
     * @return the file text
     */
    default String writeParadigm(ParadigmDso paradigm) {
        throw new UnsupportedOperationException("writeParadigm is not supported by this strategy");
    }

    /**
     * Reads a paradigm file.
     *
     * @param text the file text
     * @param file the pack-relative file name, named in a failure
     * @return the paradigm, or why the text is unreadable
     */
    default FileRead<ParadigmDso> readParadigm(String text, String file) {
        throw new UnsupportedOperationException("readParadigm is not supported by this strategy");
    }
}
