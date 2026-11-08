package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** What a URL keeps when it is recorded: scheme, host, port and path, and nothing a client could hide a secret in. */
class SourceUrlsTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "https://packs.example.org/a.tgz|https://packs.example.org/a.tgz",
                "https://packs.example.org/a.tgz?sig=s3cr3t|https://packs.example.org/a.tgz",
                "https://packs.example.org/a.tgz#frag|https://packs.example.org/a.tgz",
                "https://u:p%40ss@packs.example.org/a.tgz|https://packs.example.org/a.tgz",
                "https://u:s3cr3t@packs.example.org:443/a.git?x=1#y|https://packs.example.org:443/a.git",
                "https://packs.example.org/a@b.tgz|https://packs.example.org/a@b.tgz",
                "https://packs.example.org?x=a@b|https://packs.example.org",
            })
    void aUrlKeepsNoQueryFragmentOrUserInformation(String sent, String kept) {
        assertThat(SourceUrls.withoutSecrets(sent)).isEqualTo(kept);
    }
}
