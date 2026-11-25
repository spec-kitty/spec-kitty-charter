package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.rules.Findings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link QuotedNumbers} (#651): which quoted values qualify, and the repair of a file's text, which unquotes only a
 * quoted scalar at the finding's place whose finding words its fix, and leaves the rest of the text as it was.
 */
class QuotedNumbersTest {

    private static final String FILE = "a.yaml";

    @TempDir
    Path pack;

    /** The finding {@link Wording} gives a qualifying quoted value at {@code place}. */
    private static Reason fixable(String value, boolean integer, String... place) {
        return Findings.at(
                PackCodes.ARTIFACT_SCHEMA_ERROR,
                FILE,
                List.of(place),
                FILE + ": '" + place[place.length - 1] + "': " + QuotedNumbers.fault(value, integer));
    }

    private String repaired(String text, Reason... findings) throws IOException {
        Files.writeString(pack.resolve(FILE), text, StandardCharsets.UTF_8);
        QuotedNumbers.repair(pack, List.of(findings));
        return Files.readString(pack.resolve(FILE), StandardCharsets.UTF_8);
    }

    @Test
    void aNumberFieldTakesAFiniteIntegerOrFloatAnIntegerFieldAnIntegerOnly() {
        assertThat(List.of("15", "-0", "0.6", "1e-3", "1E+3", "15.0", "1."))
                .allMatch(text -> QuotedNumbers.qualifies(text, false));
        assertThat(List.of("15", "-15", "-0")).allMatch(text -> QuotedNumbers.qualifies(text, true));
        assertThat(List.of("1.5", "15.0", "1e3")).noneMatch(text -> QuotedNumbers.qualifies(text, true));
        assertThat(List.of(
                        "fifteen", "", " 15", "+1", ".5", "1_000", "0x1F", "015", "1e400", ".inf", ".nan", "1e-99999"))
                .noneMatch(text -> QuotedNumbers.qualifies(text, false));
        assertThat(QuotedNumbers.qualifies("9".repeat(1_001), false))
                .as("past the reader's digit bound")
                .isFalse();
    }

    @Test
    void aQuotedValueIsWrittenAsTheTextInsideItsQuotesAndNothingElseChanges() throws IOException {
        String text = "# a note\nprice: \"15\"  # kept\nother: '15'\nlist:\n- {x: '0.6'}\n";

        assertThat(repaired(text, fixable("15", false, "price"), fixable("0.6", false, "list", "0", "x")))
                .isEqualTo("# a note\nprice: 15  # kept\nother: '15'\nlist:\n- {x: 0.6}\n");
    }

    @Test
    void theOffsetsHoldPastCharactersOutsideTheBasicPlane() throws IOException {
        String text = "note: \"😀 é\"\nprice: '15'\n";

        assertThat(repaired(text, fixable("15", true, "price"))).isEqualTo("note: \"😀 é\"\nprice: 15\n");
    }

    @Test
    void aTaggedAnAnchoredOrAPlainScalarIsLeftAsItIs() throws IOException {
        String text = "a: !!str \"15\"\nb: &n \"15\"\nc: *n\nd: 15\n";

        assertThat(repaired(
                        text,
                        fixable("15", false, "a"),
                        fixable("15", false, "b"),
                        fixable("15", false, "c"),
                        fixable("15", false, "d")))
                .isEqualTo(text);
    }

    @Test
    void aFindingWhoseDetailNamesAnotherFaultOrAnotherValueRepairsNothing() throws IOException {
        String text = "a: \"15\"\nb: \"16\"\n";
        Reason range =
                Findings.at(PackCodes.ARTIFACT_SCHEMA_ERROR, FILE, List.of("a"), FILE + ": 'a' 15 is out of range");

        assertThat(repaired(text, range, fixable("15", false, "b"))).isEqualTo(text);
        assertThat(repaired(text, fixable("15", true, "a")))
                .as("an integer field's fault repairs an integer")
                .isEqualTo("a: 15\nb: \"16\"\n");
    }

    @Test
    void aFileOfMoreThanOneDocumentIsLeftAsItIs() throws IOException {
        String text = "a: \"15\"\n---\na: \"15\"\n";

        assertThat(repaired(text, fixable("15", false, "a"))).isEqualTo(text);
    }
}
