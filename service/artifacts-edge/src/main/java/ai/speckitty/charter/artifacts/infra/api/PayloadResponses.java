package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.web.DeferredETag;
import java.io.ByteArrayInputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The answer of a payload read (#105, research R6 and R15): the bytes, exactly, with the asset's own media type. Both
 * payload reads build it here, the pack's and, under an activation, the winner's.
 *
 * <ul>
 *   <li>The {@code Content-Type} is the asset's {@code mime} when the packs domain's rule calls it well-formed (the
 *       rule validate applies) and Spring can write it as a concrete header value; otherwise
 *       {@code application/octet-stream}. The second test is a limit of the transport, not a second rule: a mime the
 *       rule accepts but Spring cannot parse, or one with a wildcard, has no header value to give.
 *   <li>Every {@code 200} carries {@code X-Content-Type-Options: nosniff} and
 *       {@code Content-Security-Policy: sandbox; default-src 'none'}. No {@code Content-Disposition} is written, on
 *       purpose: the payload is read by programs, and the two headers already contain a browser that opens it.
 *   <li>The {@code ETag} is the token the caller gives. {@code If-None-Match} is judged here by {@link EntityTags}, and
 *       a match ({@code *} included) is a {@code 304} without a body. On a {@code 200} the tag is handed to the web
 *       transport's {@link DeferredETag}, so that Spring's own conditional check, which mishandles {@code *} and a
 *       value that is not an entity-tag list, never runs.
 *   <li>The body is a stream, not a byte-array resource, so Spring answers no {@code Range} with a {@code 206} and
 *       writes no {@code Accept-Ranges}, which the contract does not declare; the length is set here.
 *   <li>A preset {@code Content-Type} skips Spring's negotiation, so the payload ignores {@code Accept}: it is never a
 *       {@code 406}.
 * </ul>
 */
public final class PayloadResponses {

    /** The {@code X-Content-Type-Options} header. */
    public static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    /** The {@code Content-Security-Policy} header. */
    public static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";

    /** The value of {@link #CONTENT_TYPE_OPTIONS}: the client never sniffs another type. */
    public static final String NOSNIFF = "nosniff";

    /**
     * The value of {@link #CONTENT_SECURITY_POLICY}: a browser that opens the payload runs it in a sandbox and loads
     * nothing from it.
     */
    public static final String SANDBOX = "sandbox; default-src 'none'";

    private static final char FIRST_VISIBLE = ' ';
    private static final char LAST_VISIBLE = '~';

    private PayloadResponses() {}

    /**
     * The answer of a payload read.
     *
     * @param bytes the payload's bytes
     * @param mime the asset's media type, as the sidecar writes it
     * @param wellFormedMime whether the packs domain's rule calls {@code mime} well-formed
     * @param etagToken the answer's entity tag, without quotes
     * @param sourceFingerprint the whole-source digest the read verified
     * @param ifNoneMatch the request's {@code If-None-Match}, verbatim, or null
     * @return {@code 304} when {@code ifNoneMatch} matches, otherwise {@code 200} with the bytes and the headers
     */
    public static ResponseEntity<Resource> answer(
            byte[] bytes,
            String mime,
            boolean wellFormedMime,
            String etagToken,
            String sourceFingerprint,
            @Nullable String ifNoneMatch) {
        String etag = EntityTags.quote(etagToken);
        if (EntityTags.noneMatch(ifNoneMatch, etagToken)) {
            // A 304 carries no fingerprint, the tag again, and no body (the contract's NotModified).
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        DeferredETag.offer(etag);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType(mime, wellFormedMime))
                .contentLength(bytes.length)
                .header(HeaderNames.SOURCE_FINGERPRINT, sourceFingerprint)
                .header(CONTENT_TYPE_OPTIONS, NOSNIFF)
                .header(CONTENT_SECURITY_POLICY, SANDBOX)
                .body(new InputStreamResource(new ByteArrayInputStream(bytes)));
    }

    /**
     * The {@code Content-Type} of a payload.
     *
     * @param mime the asset's media type, as the sidecar writes it
     * @param wellFormedMime whether the packs domain's rule calls it well-formed
     * @return {@code mime} when it is well-formed and the transport can write it, otherwise
     *     {@code application/octet-stream}
     */
    static String contentType(String mime, boolean wellFormedMime) {
        return wellFormedMime && writable(mime) ? mime : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    /** Whether Spring can write the value as a concrete media type header: visible ASCII that parses to one. */
    private static boolean writable(String mime) {
        if (mime.chars().anyMatch(c -> c < FIRST_VISIBLE || c > LAST_VISIBLE)) {
            return false;
        }
        try {
            return MediaType.parseMediaType(mime).isConcrete();
        } catch (InvalidMediaTypeException unparsable) {
            return false;
        }
    }
}
