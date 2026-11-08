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
                "https://a@b@packs.example.org/a.tgz|https://packs.example.org/a.tgz",
                "https://packs.example.org/dir/a@b.tgz?sig=x@y|https://packs.example.org/dir/a@b.tgz",
                "https://packs.example.org:8443/dir/a@b.tgz|https://packs.example.org:8443/dir/a@b.tgz",
                "https://[::1]:8443/dir/a@b.tgz|https://[::1]:8443/dir/a@b.tgz",
            })
    void aUrlKeepsNoQueryFragmentOrUserInformation(String sent, String kept) {
        assertThat(SourceUrls.withoutSecrets(sent)).isEqualTo(kept);
    }

    /**
     * A credential with a slash in it ({@code u:p/w}) makes a malformed URL, which the fetcher refuses, but the attempt is
     * still recorded: an authority that is no host and port is read as user information up to the last at sign, so the
     * password is not kept. An at sign in the path of a well-formed authority stays (the cases above).
     */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "https://u:p/w@packs.example.org/a.tgz|https://packs.example.org/a.tgz",
                "https://u:p/w@packs.example.org|https://packs.example.org",
                "https://u:p/w/x@packs.example.org:8443/a.git?x=1#y|https://packs.example.org:8443/a.git",
            })
    void aSlashInTheUserInformationDoesNotKeepTheCredential(String sent, String kept) {
        assertThat(SourceUrls.withoutSecrets(sent)).isEqualTo(kept);
    }
}
