package ai.speckitty.charter.packs.domain.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The scanner's own line rules, beside the codec that reads whole texts with them ({@code MarkdownCodecTest}). */
@DisplayName("MarkdownScanner: line rules")
class MarkdownScannerTest {

    /** The regular expression the scanner used before its linear scan, kept here as the oracle. */
    private static final Pattern REGEX =
            Pattern.compile("^ {0,3}(?:(?:-[ \\t]*){3,}|(?:\\*[ \\t]*){3,}|(?:_[ \\t]*){3,})$");

    @Test
    @DisplayName("the linear scan classifies every short line over the break alphabet as the regular expression did")
    void sameAsRegex() {
        char[] alphabet = {' ', '\t', '-', '*', '_', 'x'};
        Random random = new Random(194);
        for (int n = 0; n < 50_000; n++) {
            StringBuilder line = new StringBuilder();
            int length = random.nextInt(11);
            for (int i = 0; i < length; i++) {
                line.append(alphabet[random.nextInt(alphabet.length)]);
            }
            String text = line.toString();
            assertThat(MarkdownScanner.isThematicBreak(text))
                    .as("%s", text.replace("\t", "\\t"))
                    .isEqualTo(REGEX.matcher(text).matches());
        }
    }
}
