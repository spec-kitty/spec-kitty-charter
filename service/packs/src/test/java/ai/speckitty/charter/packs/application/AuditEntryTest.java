package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.application.AuditLog.Credential;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** What an audit record keeps of the request: the locator without its query, and texts a request cannot forge a line in. */
class AuditEntryTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "https://packs.example.org/a/pack.tar.gz?token=SECRET|https://packs.example.org/a/pack.tar.gz",
                "https://user:pw@packs.example.org:8443/a.zip#frag|https://packs.example.org:8443/a.zip",
                "https://packs.example.org|https://packs.example.org",
                "https://packs.example.org/a%20b?x=1&y=2|https://packs.example.org/a%20b",
            })
    void theLocatorOfAnHttpsSourceHasNoUserInformationQueryOrFragment(String url, String expected) {
        assertThat(AuditLog.Entry.locatorOf(url)).isEqualTo(expected);
    }

    @Test
    void aLocatorThatIsNoUrlIsNamedInvalidAndNeverEchoed() {
        assertThat(AuditLog.Entry.locatorOf("not a url ?secret=1")).isEqualTo("invalid");
        assertThat(AuditLog.Entry.locatorOf("https://[bad?token=SECRET")).isEqualTo("invalid");
    }

    @Test
    void controlLineSeparatorAndFormatCharactersAreEscaped() {
        String forged = "acme\nINFO audit worktree=other\r" + (char) 0x2028 + (char) 0x202e;

        assertThat(AuditLog.Entry.clean(forged))
                .isEqualTo("acme\\u000aINFO audit worktree=other\\u000d\\u2028\\u202e")
                .doesNotContain("\n", "\r");
    }

    @Test
    void aTextIsCutToTheLimitInBytes() {
        String clean = AuditLog.Entry.clean("é".repeat(400));

        assertThat(clean.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(AuditLog.Entry.MAX_VALUE_BYTES);
        assertThat(clean).isNotEmpty().matches("é+");
    }

    @Test
    void aMissingTextIsEmptyAndTheRecordCleansEveryTextItCarries() {
        AuditLog.Entry record = new AuditLog.Entry(
                Instant.parse("2026-11-07T10:00:00Z"),
                "wt\n1",
                Credential.NONE,
                "registerPackFromArchive",
                "pack\n-x",
                "archive",
                null,
                "refused:413",
                null,
                "sha256:ab\ncd");

        assertThat(record.worktree()).isEqualTo("wt\\u000a1");
        assertThat(record.packId()).isEqualTo("pack\\u000a-x");
        assertThat(record.locator()).isEmpty();
        assertThat(record.revisionBefore()).isEmpty();
        assertThat(record.revisionAfter()).isEqualTo("sha256:ab\\u000acd");
        assertThat(record.credential().wire()).isEqualTo("none");
    }
}
