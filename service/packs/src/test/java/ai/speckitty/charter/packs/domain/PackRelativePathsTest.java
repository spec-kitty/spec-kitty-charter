package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The lexical escape of a pack-relative path (#382, #254): core's verdict on every path, POSIX and Windows alike. The
 * one reader's sidecar rule and a fragment node's {@code body_path} both judge through it, so these cases are its own.
 */
@DisplayName("PackRelativePaths.leavesRoot is core's verdict")
class PackRelativePathsTest {

    @ParameterizedTest
    @ValueSource(strings = {"/etc/passwd", "../logo.png", "a/../logo.png", "a/..", "..", "assets/../../x.png"})
    void anAbsoluteOrDotDotPathEscapes(String path) {
        assertThat(PackRelativePaths.leavesRoot(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"logo.png", "img/logo.png", "a/..b/logo.png", "./logo.png", "a/b..c.png"})
    void aContainedRelativePathIsFine(String path) {
        assertThat(PackRelativePaths.leavesRoot(path)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "C:\\x.png",
                "C:/x.png",
                "c:\\x.png",
                "1:\\x.png",
                "\\\\srv\\share\\x.png",
                "//srv/share/x.png",
                "//x.png",
                "\\\\srv",
                "a\\..\\b.png",
                "..\\b.png",
                "a\\..",
                "a/..\\b.png",
                "a\\../b.png",
                "C:..",
                "\\\\?\\C:\\x.png"
            })
    void aWindowsAbsoluteOrBackslashDotDotPathEscapesAsCoreFlagsIt(String path) {
        assertThat(PackRelativePaths.leavesRoot(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"C:x.png", "\\x.png", "a\\b.png", "a\\.\\b.png", "a\\..b\\c.png", "C:", "x:y\\z.png"})
    void aWindowsDriveRelativeOrRootRelativePathDoesNotEscapeInCoreEither(String path) {
        assertThat(PackRelativePaths.leavesRoot(path)).isFalse();
    }

    /** {@code path<TAB>escapes}, decided by core's own function under {@code python -I}. */
    @Test
    void theEscapeVerdictIsCoresOnEveryRowOfTheGeneratedCorpus() throws IOException {
        List<String> wrong = new ArrayList<>();
        int rows = 0;
        try (InputStream in = PackRelativePathsTest.class.getResourceAsStream("/paths/asset-escape.tsv")) {
            assertThat(in).as("the asset-escape corpus").isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.US_ASCII)
                    .lines()
                    .toList()) {
                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }
                int tab = line.lastIndexOf('\t');
                String path = unescape(line.substring(0, tab));
                rows++;
                if (PackRelativePaths.leavesRoot(path) != Boolean.parseBoolean(line.substring(tab + 1))) {
                    wrong.add(line);
                }
            }
        }
        assertThat(rows).isGreaterThan(9000);
        assertThat(wrong).as("rows where the verdict differs from core's").isEmpty();
    }

    /** The corpus writes every non-printable or non-ASCII UTF-16 unit, and the backslash, as backslash-u-hhhh. */
    private static String unescape(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '\\') {
                out.append((char) Integer.parseInt(text.substring(i + 2, i + 6), 16));
                i += 6;
            } else {
                out.append(text.charAt(i));
                i++;
            }
        }
        return out.toString();
    }
}
