package ai.speckitty.charter.packs.infra.api;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * The bundled contract {@code GET /openapi.yaml} answers (#209): the bytes the contract build wrote, as text, and
 * their strong entity tag {@code "sha256:<64 lower-case hex>"}. The digest is the SHA-256 of the bytes themselves, so
 * it equals the checksum a contract release publishes beside the file. Built once, when the service starts.
 */
public final class ContractDocument {

    private final String text;
    private final StrongETag tag;

    /**
     * A document over the given bytes.
     *
     * @param bytes the bundled contract, which must be UTF-8 (RFC 9512)
     * @throws IllegalArgumentException when the bytes are not well-formed UTF-8, so the text could not be written
     *     back as the same bytes
     */
    public ContractDocument(byte[] bytes) {
        try {
            this.text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("the bundled contract is not well-formed UTF-8", e);
        }
        this.tag = StrongETag.of(bytes);
    }

    /**
     * The document as text; encoded as UTF-8 it is the bytes it was built from.
     *
     * @return the bundled contract
     */
    public String text() {
        return text;
    }

    /**
     * The strong entity tag of the document, double quotes included, as the {@code ETag} header carries it.
     *
     * @return {@code "sha256:<64 lower-case hex>"}
     */
    public String etag() {
        return tag.etag();
    }

    /**
     * Whether an {@code If-None-Match} value matches this document (RFC 9110, section 13.1.2), by
     * {@link StrongETag#matchedBy}.
     *
     * @param ifNoneMatch the request's {@code If-None-Match}, or null when it sent none
     * @return true when the client holds this document
     */
    public boolean matchedBy(@Nullable String ifNoneMatch) {
        return tag.matchedBy(ifNoneMatch);
    }
}
