package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.application.ArtifactPosition;
import ai.speckitty.charter.packs.application.ArtifactReadService;
import ai.speckitty.charter.web.InvalidParameter;
import ai.speckitty.charter.web.ProblemHandler;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * The page cursor of the generic artifact list: the unpadded base64url of {@code <kind>/<id>}, the position of the last
 * artifact of a page. It is opaque to a client, which passes it back unchanged; the application sees only the
 * {@link ArtifactPosition} it decodes to.
 *
 * <p>A cursor that does not decode to a listed kind and a non-empty id, or that names a kind other than the request's
 * kind filter, is refused as an {@link InvalidParameter}, which {@link ProblemHandler} answers as a {@code 400 Problem}
 * whose detail says why: a malformed request, not a charter refusal, so it carries no code.
 */
final class ArtifactCursor {

    private static final String NAME = "pageCursor";

    private ArtifactCursor() {}

    /**
     * The cursor of a position.
     *
     * @param position the position of the last artifact of a page
     * @return the unpadded base64url of {@code <kind>/<id>}
     */
    static String encode(ArtifactPosition position) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((position.kind().token() + "/" + position.id()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The position a cursor names.
     *
     * @param cursor the cursor, as the request carries it; padded or not
     * @param filter the request's kind filter; empty when it names none
     * @return the position
     * @throws InvalidParameter when the cursor is not base64url of UTF-8 {@code <kind>/<id>} with
     *     a listed kind and a non-empty id, or names a kind other than {@code filter}
     */
    static ArtifactPosition decode(String cursor, Optional<ArtifactKind> filter) {
        String text = text(cursor);
        int slash = text.indexOf('/');
        if (slash < 0 || slash == text.length() - 1) {
            throw malformed(cursor);
        }
        ArtifactKind kind =
                ArtifactReadService.listedKind(text.substring(0, slash)).orElseThrow(() -> malformed(cursor));
        if (filter.isPresent() && filter.get() != kind) {
            throw new InvalidParameter(
                    InvalidParameter.In.QUERY,
                    NAME,
                    cursor,
                    "which names a " + kind.token() + ", not a " + filter.get().token() + " as the kind filter asks");
        }
        return new ArtifactPosition(kind, text.substring(slash + 1));
    }

    private static String text(String cursor) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Base64.getUrlDecoder().decode(cursor)))
                    .toString();
        } catch (IllegalArgumentException | CharacterCodingException notBase64) {
            throw malformed(cursor);
        }
    }

    private static InvalidParameter malformed(String cursor) {
        return new InvalidParameter(InvalidParameter.In.QUERY, NAME, cursor, "which is not a cursor this list gave");
    }
}
