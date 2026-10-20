package ai.speckitty.charter.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks {@link Pep440} against the corpus {@code scripts/packs/pep440_corpus.py} wrote from
 * {@code packaging.specifiers.SpecifierSet}, plus a few strings that name the rules directly.
 */
class Pep440Test {

    private record Row(String input, boolean valid) {
        @Override
        public String toString() {
            String shown = input.length() > 60 ? input.substring(0, 60) + "...(" + input.length() + " chars)" : input;
            return "'" + shown.replaceAll("[^\\x20-\\x7e]", "?") + "' -> " + valid;
        }
    }

    static List<Row> corpus() throws IOException {
        List<Row> rows = new ArrayList<>();
        try (InputStream in = Pep440Test.class.getResourceAsStream("/pep440/specifiers.tsv")) {
            assertThat(in).as("the corpus resource").isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.US_ASCII)
                    .lines()
                    .toList()) {
                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }
                int tab = line.lastIndexOf('\t');
                rows.add(new Row(unescape(line.substring(0, tab)), Boolean.parseBoolean(line.substring(tab + 1))));
            }
        }
        return rows;
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

    @Test
    void theCorpusCoversBothOutcomesAndTheAdversarialList() throws IOException {
        List<Row> rows = corpus();
        assertThat(rows).hasSizeGreaterThan(600);
        assertThat(rows).anyMatch(r -> r.valid() && r.input().equals(">=4.0.0rc7,<5"));
        assertThat(rows).anyMatch(r -> !r.valid() && r.input().equals("~=1"));
        assertThat(rows).anyMatch(r -> r.valid() && r.input().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void agreesWithPackagingOnEveryCorpusRow(Row row) {
        assertThat(Pep440.isValidSpecifierSet(row.input())).isEqualTo(row.valid());
    }

    @Test
    void coreRealValueIsValid() {
        assertThat(Pep440.isValidSpecifierSet(">=4.0.0rc7,<5")).isTrue();
    }

    @Test
    void rejectsWildcardWithOrderingOperatorAndLocalWithOrderingOperator() {
        assertThat(Pep440.isValidSpecifierSet(">=1.*")).isFalse();
        assertThat(Pep440.isValidSpecifierSet(">=1.0+local")).isFalse();
        assertThat(Pep440.isValidSpecifierSet("==1.0+local")).isTrue();
        assertThat(Pep440.isValidSpecifierSet("~=1")).isFalse();
        assertThat(Pep440.isValidSpecifierSet("~=1.0")).isTrue();
    }

    @Test
    void anyOneBadClauseMakesTheSetInvalid() {
        assertThat(Pep440.isValidSpecifierSet(">=1.0,banana")).isFalse();
        assertThat(Pep440.isValidSpecifierSet("banana,>=1.0")).isFalse();
    }

    @Test
    void emptyAndCommaOnlySetsAreValid() {
        assertThat(Pep440.isValidSpecifierSet("")).isTrue();
        assertThat(Pep440.isValidSpecifierSet("  ")).isTrue();
        assertThat(Pep440.isValidSpecifierSet(",")).isTrue();
    }

    /** Python's {@code str.isspace} set, which {@code str.strip} and {@code \\s} use in {@code packaging}. */
    private static boolean pythonSpace(int c) {
        return (c >= 0x09 && c <= 0x0d)
                || (c >= 0x1c && c <= 0x20)
                || c == 0x85
                || c == 0xa0
                || c == 0x1680
                || (c >= 0x2000 && c <= 0x200a)
                || c == 0x2028
                || c == 0x2029
                || c == 0x202f
                || c == 0x205f
                || c == 0x3000;
    }

    @Test
    void whitespaceAroundAndInsideAClauseIsPythonsWhitespaceSet() {
        for (int c = 0; c <= 0xffff; c++) {
            if ((c >= 0xd800 && c <= 0xdfff) || c == ',') {
                continue;
            }
            String space = String.valueOf((char) c);
            boolean expected = pythonSpace(c);
            assertThat(Pep440.isValidSpecifierSet(space + ">=" + space + "1.0" + space))
                    .as("U+%04X as whitespace", c)
                    .isEqualTo(expected);
        }
    }

    // ---- long input: the matcher must neither overflow the stack nor give up (no length cap) ----------

    private static String repeat(String unit, int times) {
        return unit.repeat(times);
    }

    @ParameterizedTest
    @ValueSource(ints = {1_000, 10_000, 100_000})
    void aVeryLongReleaseIsValidAndDoesNotOverflowTheStack(int segments) {
        assertThat(Pep440.isValidSpecifierSet(">=1" + repeat(".1", segments))).isTrue();
        assertThat(Pep440.isValidSpecifierSet("==1" + repeat(".1", segments) + ".*"))
                .isTrue();
        assertThat(Pep440.isValidSpecifierSet("~=1" + repeat(".1", segments))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {1_000, 10_000, 100_000})
    void aVeryLongReleaseWithABadTailIsInvalidAndDoesNotOverflowTheStack(int segments) {
        assertThat(Pep440.isValidSpecifierSet(">=1" + repeat(".1", segments) + "."))
                .isFalse();
        assertThat(Pep440.isValidSpecifierSet(">=1" + repeat(".1", segments) + ".x"))
                .isFalse();
        assertThat(Pep440.isValidSpecifierSet(">=1" + repeat(".1", segments) + ".*"))
                .isFalse();
        assertThat(Pep440.isValidSpecifierSet("~=1" + repeat(".1", segments) + "+a"))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {1_000, 10_000, 100_000})
    void aVeryLongLocalVersionIsValidAndDoesNotOverflowTheStack(int segments) {
        assertThat(Pep440.isValidSpecifierSet("==1.0+a" + repeat(".a", segments)))
                .isTrue();
        assertThat(Pep440.isValidSpecifierSet("==1.0+a" + repeat("-b_1", segments)))
                .isTrue();
        assertThat(Pep440.isValidSpecifierSet("!=1.0+a" + repeat(".a", segments) + ",>=1"))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {1_000, 10_000, 100_000})
    void aVeryLongLocalVersionWithABadTailIsInvalidAndDoesNotOverflowTheStack(int segments) {
        assertThat(Pep440.isValidSpecifierSet("==1.0+a" + repeat(".a", segments) + "."))
                .isFalse();
        assertThat(Pep440.isValidSpecifierSet("==1.0+a" + repeat(".a", segments) + "..a"))
                .isFalse();
        assertThat(Pep440.isValidSpecifierSet("==1.0+a" + repeat(".a", segments) + "!"))
                .isFalse();
    }

    @Test
    void aVeryLongSetOfClausesIsStillValid() {
        assertThat(Pep440.isValidSpecifierSet(">=1,".repeat(100_000))).isTrue();
        assertThat(Pep440.isValidSpecifierSet(">=1,".repeat(100_000) + "x")).isFalse();
    }
}
