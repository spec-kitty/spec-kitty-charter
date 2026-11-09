package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The locator of an audit record (NFR-010, NFR-012): the URL with its query, its fragment and its user information cut,
 * so no credential or query secret is stored; the relative path of a local source; control characters escaped and every
 * value held to 256 bytes, so a log line cannot be forged or inflated.
 */
class AuditLocatorsTest {

    private static PackSourceRequest https(String url) {
        return new PackSourceRequest(PackSourceKind.HTTPS_ARCHIVE, url, null, null, null, null);
    }

    private static PackSourceRequest filesystem(String path) {
        return new PackSourceRequest(PackSourceKind.FILESYSTEM, null, null, null, null, path);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "https://packs.example.org/acme.tar.gz|https://packs.example.org/acme.tar.gz",
                "https://packs.example.org/acme.tar.gz?token=s3cr3t|https://packs.example.org/acme.tar.gz",
                "https://packs.example.org/acme.tar.gz#frag|https://packs.example.org/acme.tar.gz",
                "https://packs.example.org/a.tgz?x=1#frag?y|https://packs.example.org/a.tgz",
                "https://user:s3cr3t@packs.example.org/a.tgz|https://packs.example.org/a.tgz",
                "https://s3cr3t@packs.example.org/a.tgz?k=v|https://packs.example.org/a.tgz",
                "https://packs.example.org?token=s3cr3t|https://packs.example.org"
            })
    void aUrlIsKeptWithoutItsQueryItsFragmentOrItsUserInformation(String url, String expected) {
        assertThat(AuditLocators.of(https(url))).isEqualTo(expected);
    }

    @Test
    void aLocalPathIsKeptAsSent() {
        assertThat(AuditLocators.of(filesystem("packs/acme"))).isEqualTo("packs/acme");
    }

    @Test
    void aSourceThatNamesNothingHasAnEmptyLocator() {
        assertThat(AuditLocators.of(https(null))).isEmpty();
        assertThat(AuditLocators.of(filesystem(null))).isEmpty();
    }

    @Test
    void controlAndLineSeparatorCharactersAreEscapedSoALogLineCannotBeForged() {
        String locator = AuditLocators.of(https("https://packs.example.org/a\nlocator=\"forged\"\r \u0000.tgz"));

        assertThat(locator)
                .doesNotContain("\n", "\r", " ", "\u0000")
                .contains("\\u000a", "\\u000d", "\\u2028", "\\u0000");
    }

    @Test
    void aValueIsHeldToTwoHundredFiftySixBytes() {
        String locator = AuditLocators.of(https("https://packs.example.org/" + "a".repeat(1000)));

        assertThat(locator.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(256);
        assertThat(locator).startsWith("https://packs.example.org/aaa");
    }

    @Test
    void multibyteCharactersAreNeverCutInTwo() {
        String locator = AuditLocators.of(filesystem("é".repeat(200)));

        assertThat(locator.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(256);
        assertThat(locator).matches("(é)+");
    }
}
