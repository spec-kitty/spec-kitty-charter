package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContractDocumentTest {

    /** Not ASCII, so a decoder or a digest over anything but these bytes shows. */
    private static final byte[] BYTES = "openapi: 3.1.0 # é — €\n".getBytes(StandardCharsets.UTF_8);

    /** SHA-256 of {@link #BYTES}, computed outside the code under test ({@code printf … | sha256sum}). */
    private static final String DIGEST = "31b74c5d521b1c22806e3f17c367e62cddfd3d762649c6567f97b03cc38cd0a9";

    private final ContractDocument document = new ContractDocument(BYTES);
    private final String tag = document.etag();

    @Test
    void theTextIsTheBytesDecodedAsUtf8() {
        assertThat(document.text()).isEqualTo("openapi: 3.1.0 # é — €\n");
        assertThat(document.text().getBytes(StandardCharsets.UTF_8)).isEqualTo(BYTES);
    }

    @Test
    void theEntityTagIsTheQuotedSha256OfTheBytes() {
        assertThat(tag).isEqualTo("\"sha256:" + DIGEST + "\"");
    }

    @Test
    void bytesThatAreNotUtf8AreRefused() {
        assertThatThrownBy(() -> new ContractDocument(new byte[] {'a', (byte) 0xC3, '(', 'b'}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not well-formed UTF-8");
    }

    @Test
    void theTagItselfMatches() {
        assertThat(document.matchedBy(tag)).isTrue();
    }

    @Test
    void aWeakTagMatchesBecauseTheComparisonIsWeak() {
        assertThat(document.matchedBy("W/" + tag)).isTrue();
    }

    @Test
    void aListMatchesWhenAnyTagMatches() {
        assertThat(document.matchedBy("\"other\", W/\"x\" ,  " + tag)).isTrue();
    }

    @Test
    void headerLinesJoinedByCommasAreOneList() {
        assertThat(document.matchedBy("\"other\"," + tag)).isTrue();
    }

    @Test
    void emptyListElementsAreSkipped() {
        assertThat(document.matchedBy(", ," + tag + ",")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", " * ", "\t*\t"})
    void aWildcardMatches(String value) {
        assertThat(document.matchedBy(value)).isTrue();
    }

    @Test
    void anotherTagDoesNotMatch() {
        assertThat(document.matchedBy("\"sha256:" + "0".repeat(64) + "\"")).isFalse();
    }

    @Test
    void noHeaderDoesNotMatch() {
        assertThat(document.matchedBy(null)).isFalse();
        assertThat(document.matchedBy("")).isFalse();
        assertThat(document.matchedBy(" , ")).isFalse();
    }

    /** Values that are not an entity-tag list match nothing, though each holds the document's tag. */
    @Test
    void aValueThatIsNotAnEntityTagListMatchesNothing() {
        String bare = tag.substring(1, tag.length() - 1);
        assertThat(document.matchedBy("garbage " + tag)).isFalse();
        assertThat(document.matchedBy(tag + " junk")).isFalse();
        assertThat(document.matchedBy("w/" + tag)).isFalse();
        assertThat(document.matchedBy(bare)).isFalse();
        assertThat(document.matchedBy("*, " + tag)).isFalse();
        assertThat(document.matchedBy(tag + ", \"unterminated")).isFalse();
        assertThat(document.matchedBy(tag + " " + tag)).isFalse();
        assertThat(document.matchedBy("\u00a0*")).isFalse();
        assertThat(document.matchedBy("**")).isFalse();
    }
}
