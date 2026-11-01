package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.application.ArtifactPosition;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.ErrorResponseException;

/** The opaque page cursor: base64url of {@code <kind>/<id>}, and every way a cursor is refused as a 400. */
class ArtifactCursorTest {

    @Test
    void aCursorIsTheUnpaddedBase64urlOfKindSlashId() {
        assertThat(ArtifactCursor.encode(new ArtifactPosition(ArtifactKind.TACTIC, "rehearse")))
                .isEqualTo("dGFjdGljL3JlaGVhcnNl");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rehearse", "GUARD", "a/b", "über", "😀", "x"})
    void aCursorDecodesToThePositionItWasEncodedFrom(String id) {
        for (ArtifactKind kind : new ArtifactKind[] {ArtifactKind.TACTIC, ArtifactKind.TOOLGUIDE}) {
            ArtifactPosition position = new ArtifactPosition(kind, id);

            String cursor = ArtifactCursor.encode(position);

            assertThat(cursor).matches("[A-Za-z0-9_-]+");
            assertThat(ArtifactCursor.decode(cursor, Optional.empty())).isEqualTo(position);
            assertThat(ArtifactCursor.decode(cursor, Optional.of(kind))).isEqualTo(position);
        }
    }

    @Test
    void aPaddedCursorIsReadToo() {
        assertThat(ArtifactCursor.decode("dGFjdGljL2I=", Optional.empty()))
                .isEqualTo(new ArtifactPosition(ArtifactKind.TACTIC, "b"));
    }

    @Test
    void aCursorForAnotherKindThanTheFilterIsA400() {
        String directive = ArtifactCursor.encode(new ArtifactPosition(ArtifactKind.DIRECTIVE, "GUARD"));

        assertBadRequest(() -> ArtifactCursor.decode(directive, Optional.of(ArtifactKind.TACTIC)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"***", "not base64", "dGFjdGlj", "cHJvY2VkdXJlL3g", "dGFjdGljLw", "Ym9ndXMveA", "L3g"})
    void aCursorThatIsNotBase64urlOfAServedKindAndAnIdIsA400(String cursor) {
        // not base64url; "tactic" (no slash); "procedure/x" (not served); "tactic/" (no id); "bogus/x"; "/x".
        assertBadRequest(() -> ArtifactCursor.decode(cursor, Optional.empty()));
    }

    @Test
    void aCursorThatIsNotUtf8IsA400() {
        String cursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(new byte[] {'t', 'a', 'c', 't', 'i', 'c', '/', (byte) 0xC3, (byte) 0x28});

        assertBadRequest(() -> ArtifactCursor.decode(cursor, Optional.empty()));
        assertThat(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.ISO_8859_1))
                .startsWith("tactic/");
    }

    private static void assertBadRequest(Runnable decode) {
        assertThatThrownBy(decode::run)
                .isInstanceOfSatisfying(
                        ErrorResponseException.class,
                        refused -> assertThat(refused.getStatusCode().value()).isEqualTo(400));
    }
}
