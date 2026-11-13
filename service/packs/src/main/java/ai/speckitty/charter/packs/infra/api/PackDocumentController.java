package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.PackDocumentRead;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.PackDocumentQuery;
import ai.speckitty.charter.web.DeferredETag;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * The adapter of {@code GET /packs/{packId}/doc} (#451): the two forms of one read, and the answer they share. It is
 * what {@link PackRegistrationController} calls from its two mappings, as it calls {@link PackRemovalController} for
 * the two {@code DELETE}s; the mappings, and so the negotiation, are the controller's.
 *
 * <p>Both forms answer the same {@code ETag}, the pack revision, with {@code Vary: Accept}. A {@code 304} is answered
 * here, from {@link EntityTags}, and never by Spring's own conditional check: that check scans a header for any quoted
 * tag it finds, so a value the contract answers {@code 200} (not an entity-tag list) would be a {@code 304}, and it does
 * not match {@code *} on a {@code GET}. Spring runs it only for an entity that carries an {@code ETag}, so the tag is not
 * put on the entity: it is handed to the web transport's {@link DeferredETag} through the request, and set when the
 * body is written, after that check has run.
 */
@Component
final class PackDocumentController {

    /** {@code text/markdown;charset=UTF-8}, the form that answers the held {@code README.md}. */
    static final MediaType MARKDOWN = new MediaType("text", "markdown", StandardCharsets.UTF_8);

    private static final String ACCEPT = HttpHeaders.ACCEPT;

    private final PackDocumentQuery documents;
    private final PackRegistrationMapper mapper;

    /**
     * An adapter over the document read.
     *
     * @param documents the document read
     * @param mapper the contract shapes
     */
    @Autowired
    PackDocumentController(PackDocumentQuery documents, PackRegistrationMapper mapper) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * The {@code application/json} form: the title, the body and the links.
     *
     * @param packId the pack identifier
     * @param ifNoneMatch the request's {@code If-None-Match}, verbatim, or null
     * @return {@code 304}, or {@code 200} with a {@link PackDocumentRead}
     */
    ResponseEntity<PackDocumentRead> json(String packId, @Nullable String ifNoneMatch) {
        return answer(packId, ifNoneMatch, null, mapper::document);
    }

    /**
     * The {@code text/markdown} form: the held {@code README.md}, byte for byte.
     *
     * @param packId the pack identifier
     * @param ifNoneMatch the request's {@code If-None-Match}, verbatim, or null
     * @return {@code 304}, or {@code 200} with the text
     */
    ResponseEntity<String> markdown(String packId, @Nullable String ifNoneMatch) {
        return answer(packId, ifNoneMatch, MARKDOWN, read -> read.document().markdown());
    }

    private <T> ResponseEntity<T> answer(
            String packId,
            @Nullable String ifNoneMatch,
            @Nullable MediaType contentType,
            Function<PackDocumentQuery.Read, T> body) {
        ConsistentRead.Verified<PackDocumentQuery.Read> verified = documents.document(packId);
        String token = verified.value().revision().token();
        String etag = EntityTags.quote(token);
        if (EntityTags.noneMatch(ifNoneMatch, token)) {
            // A 304 carries no fingerprint (the contract's 304), the tag again, and the Vary the 200 would carry.
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .header(HttpHeaders.VARY, ACCEPT)
                    .build();
        }
        DeferredETag.offer(etag);
        ResponseEntity.BodyBuilder ok = ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .header(HttpHeaders.VARY, ACCEPT);
        if (contentType != null) {
            ok.contentType(contentType);
        }
        return ok.body(body.apply(verified.value()));
    }
}
