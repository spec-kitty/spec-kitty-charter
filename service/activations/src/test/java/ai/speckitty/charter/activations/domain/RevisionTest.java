package ai.speckitty.charter.activations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RevisionTest {

    /** {@code printf '{"schemaVersion":1}' | sha256sum}, computed outside the JVM. */
    private static final String SCHEMA_VERSION_ONE =
            "sha256:0e9561cfb83d50990a103b3896fe249a11fe27fa28985448187f93ec12116d72";

    /** {@code printf '{"schemaVersion":2}' | sha256sum}, computed outside the JVM: the minimal document's revision. */
    private static final String SCHEMA_VERSION_TWO =
            "sha256:bafebd36189ad3688b7b3915ea55d461e0bfcfbdde11e54b0a123999fb6be50f";

    @Test
    void isTheSha256OfTheCanonicalBytes() {
        Revision revision = Revision.of("{\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8));

        assertThat(revision.token()).isEqualTo(SCHEMA_VERSION_ONE);
        assertThat(revision).hasToString(SCHEMA_VERSION_ONE);
    }

    @Test
    void theMinimalDocumentHasThePinnedRevision() {
        assertThat(ActivationDocument.builder().build().document().revision().token())
                .isEqualTo(SCHEMA_VERSION_TWO);
    }

    @Test
    void parseRoundTripsAndEqualityIsByValue() {
        Revision revision = Revision.of(new byte[] {1, 2, 3});

        assertThat(Revision.parse(revision.token())).contains(revision);
        assertThat(Revision.of(new byte[] {1, 2, 3})).isEqualTo(revision).hasSameHashCodeAs(revision);
        assertThat(Revision.of(new byte[] {1, 2, 4})).isNotEqualTo(revision);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "sha256:",
                "sha1:0e9561cfb83d50990a103b3896fe249a11fe27fa28985448187f93ec12116d72",
                "sha256:0E9561CFB83D50990A103B3896FE249A11FE27FA28985448187F93EC12116D72",
                "sha256:0e9561cfb83d50990a103b3896fe249a11fe27fa28985448187f93ec12116d7"
            })
    void parseRefusesAnythingElse(String text) {
        assertThat(Revision.parse(text)).isEmpty();
        assertThatThrownBy(() -> new Revision(text)).isInstanceOf(IllegalArgumentException.class);
    }
}
