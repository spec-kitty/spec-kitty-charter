package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.Optional;

/**
 * The port of the pack document read (#451): what a pack's {@code README.md} says, for the document read and the
 * {@code doc} link. Call it inside the verified read that resolved the pack, so the text is the one the source
 * fingerprint and the revision cover.
 */
public interface PackDocumentRepository {

    /**
     * The pack's document.
     *
     * @param pack the pack's identity
     * @return the document; empty when the pack holds no {@code README.md}
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_NOT_FOUND} when no configured pack has
     *     this identifier, or with {@code PACK_UNREADABLE} when the document cannot be read as the store reads it
     *     (not UTF-8, a carriage return, a byte order mark, an ambiguous heading, a schema violation); the text is
     *     never normalized
     */
    Optional<PackDocumentText> document(PackIdentifier pack);

    /**
     * Whether the pack holds a document, without reading it: the {@code doc} link is present exactly then.
     *
     * @param pack the pack's identity
     * @return {@code true} when the pack's {@code README.md} is a regular file
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_NOT_FOUND} when no configured pack has
     *     this identifier
     */
    boolean holdsDocument(PackIdentifier pack);
}
