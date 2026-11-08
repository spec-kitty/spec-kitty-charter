package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * The page cursor of the effective artifact list: the unpadded base64url of {@code <set tag>\n<kind>/<id>}, the tag of
 * the set the page belongs to and the position of its last artifact (IC-09, research R5). It is opaque to a client, which
 * passes it back unchanged; the service sees only the {@link EffectiveReadService.After} it decodes to, and refuses it
 * when the set it names is not the one the request reads.
 *
 * <p>A cursor that is not base64url of UTF-8 in that shape, whose tag is no {@code sha256:} digest, or that names no
 * activatable kind or an empty id, is refused as a {@code 400} {@link ErrorResponseException}, which the shared problem
 * handler answers as the plain {@code Problem} of its status, without a code.
 */
final class EffectiveCursor {

    private static final Pattern TAG = Pattern.compile("sha256:[0-9a-f]{64}");

    private EffectiveCursor() {}

    /**
     * The cursor of a position in a set.
     *
     * @param position the last artifact of a page
     * @param setTag the tag of the set the page belongs to
     * @return the unpadded base64url of the tag and the position
     */
    static String encode(ArtifactRef position, String setTag) {
        String text = setTag + "\n" + position.kind().token() + "/" + position.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Where a cursor continues.
     *
     * @param cursor the cursor, as the request carries it
     * @return the position, the tag of its set and the cursor itself
     * @throws ErrorResponseException with status 400 when the cursor is not one this class encodes
     */
    static EffectiveReadService.After decode(String cursor) {
        String text = text(cursor);
        int newline = text.indexOf('\n');
        if (newline < 0 || !TAG.matcher(text.substring(0, newline)).matches()) {
            throw refused();
        }
        String position = text.substring(newline + 1);
        int slash = position.indexOf('/');
        if (slash < 0 || slash == position.length() - 1) {
            throw refused();
        }
        ArtifactKind kind = ArtifactKind.fromToken(position.substring(0, slash))
                .filter(ArtifactKind::activatable)
                .orElseThrow(EffectiveCursor::refused);
        return new EffectiveReadService.After(
                new ArtifactRef(kind, position.substring(slash + 1)), text.substring(0, newline), cursor);
    }

    private static String text(String cursor) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Base64.getUrlDecoder().decode(cursor)))
                    .toString();
        } catch (IllegalArgumentException | CharacterCodingException malformed) {
            throw refused();
        }
    }

    private static ErrorResponseException refused() {
        return new ErrorResponseException(HttpStatus.BAD_REQUEST);
    }
}
