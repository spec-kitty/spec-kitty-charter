package ai.speckitty.charter.packs.infra.fetch.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.packs.infra.fetch.GuideAllowlist;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The git source URL path check matches possessively, so it never depends on the URL cap alone (#440).
 *
 * <p>The path pattern repeats one group per segment. The 2048-character URL cap already bounds it at about 1 000
 * repetitions, below the depth that overflows, so these tests pin behaviour at the cap rather than a crash.
 */
@DisplayName("A git source URL path at the URL cap is judged as before")
class GitSourceRulesBoundsTest {

    private static final GuideAllowlist ALLOWED = new GuideAllowlist(Set.of("git.example.org"));
    private static final String PREFIX = "https://git.example.org";

    @Test
    @DisplayName("a URL of exactly 2048 characters made of one-letter segments names its repository")
    void aPathOfManySegmentsAtTheCapIsAccepted() {
        String path = "/a".repeat((2048 - PREFIX.length()) / 2);
        String url = PREFIX + path + (PREFIX.length() % 2 == 0 ? "" : "b");
        assertThat(url).hasSize(2048);

        assertThat(GitSourceRules.repository(url, ALLOWED).url()).isEqualTo(url);
    }

    @Test
    @DisplayName("a URL one character over the cap is refused")
    void aUrlOverTheCapIsRefused() {
        String url = PREFIX + "/a".repeat(1100);
        assertThat(url.length()).isGreaterThan(2048);

        assertThatThrownBy(() -> GitSourceRules.repository(url, ALLOWED)).isInstanceOf(CharterRefusal.class);
    }

    @Test
    @DisplayName("an empty segment at the cap is still refused")
    void anEmptySegmentIsStillRefused() {
        String url = PREFIX + "/a".repeat(900) + "//b";

        assertThatThrownBy(() -> GitSourceRules.repository(url, ALLOWED)).isInstanceOf(CharterRefusal.class);
    }
}
