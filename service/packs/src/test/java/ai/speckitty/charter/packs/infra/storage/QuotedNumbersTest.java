package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.rules.Findings;
import ai.speckitty.charter.packs.infra.storage.QuotedNumbers.Repairs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link QuotedNumbers} (#651): which quoted values qualify, and the repair of a file's text, which writes the value of a
 * scalar at the finding's place whose finding words its fix in place of the whole node (quotes, tag, escapes or block
 * indicator), leaves the rest of the text as it was, and says why it left a value it could not repair.
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
        repairs(text, findings);
        return Files.readString(pack.resolve(FILE), StandardCharsets.UTF_8);
    }

    private Repairs repairs(String text, Reason... findings) throws IOException {
        Files.writeString(pack.resolve(FILE), text, StandardCharsets.UTF_8);
        return QuotedNumbers.repair(pack, List.of(findings));
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
    void aTaggedAnEscapedOrABlockScalarIsWrittenAsItsValueInPlaceOfItsWholeNode() throws IOException {
        String text = "a: !!str \"15\"\nb: !!str 16\nc: \"1\\x37\"\nd: |-\n  18\ne: >-  # folded\n  19\n\nf: 1\n";

        assertThat(repaired(
                        text,
                        fixable("15", false, "a"),
                        fixable("16", false, "b"),
                        fixable("17", false, "c"),
                        fixable("18", false, "d"),
                        fixable("19", false, "e")))
                .isEqualTo("a: 15\nb: 16\nc: 17\nd: 18\ne: 19\n\nf: 1\n");
    }

    @Test
    void aPlainScalarIsLeftAsItIs() throws IOException {
        String text = "d: 15\n";

        Repairs repairs = repairs(text, fixable("15", false, "d"));

        assertThat(Files.readString(pack.resolve(FILE), StandardCharsets.UTF_8)).isEqualTo(text);
        assertThat(repairs.repaired()).isEmpty();
        assertThat(repairs.unrepaired()).containsOnlyKeys(new Location(FILE, "/d"));
    }

    @Test
    void anAnchoredScalarOrAFileWithAnAliasIsLeftAsItIsAndSaysWhy() throws IOException {
        String anchored = "a: &n \"15\"\nb: \"16\"\n";
        Repairs repairs = repairs(anchored, fixable("15", false, "a"), fixable("16", false, "b"));
        assertThat(Files.readString(pack.resolve(FILE), StandardCharsets.UTF_8))
                .as("the scalar without the anchor is repaired")
                .isEqualTo("a: &n \"15\"\nb: 16\n");
        assertThat(repairs.unrepaired()).containsOnlyKeys(new Location(FILE, "/a"));
        assertThat(repairs.unrepaired().get(new Location(FILE, "/a"))).contains("anchor");

        String aliased = "a: &n {x: \"15\"}\nb: *n\n";
        repairs = repairs(aliased, fixable("15", false, "a", "x"), fixable("15", false, "b", "x"));
        assertThat(Files.readString(pack.resolve(FILE), StandardCharsets.UTF_8)).isEqualTo(aliased);
        assertThat(repairs.repaired()).isEmpty();
        assertThat(repairs.unrepaired().values()).allMatch(why -> why.contains("alias"));
    }

    @Test
    void twoFindingsAtOnePlaceRepairItOnce() throws IOException {
        Reason finding = fixable("15", false, "a");

        Repairs repairs = repairs("a: \"15\"\nb: 2\n", finding, finding);

        assertThat(Files.readString(pack.resolve(FILE), StandardCharsets.UTF_8)).isEqualTo("a: 15\nb: 2\n");
        assertThat(repairs.repaired()).hasSize(1);
    }

    @Test
    void thePlaceIsFoundPastClosedNestedCollectionsAndUnderKeysWithEscapedPointerCharacters() throws IOException {
        String text = "a: {b: [1, {c: x}], d: [[2]]}\ne:\n  f:\n  - g: 1\n  h: \"15\"\n\"i/j\": {\"k~l\": '16'}\n";

        Repairs repairs = repairs(text, fixable("15", false, "e", "h"), fixable("16", true, "i/j", "k~l"));

        assertThat(Files.readString(pack.resolve(FILE), StandardCharsets.UTF_8))
                .isEqualTo("a: {b: [1, {c: x}], d: [[2]]}\ne:\n  f:\n  - g: 1\n  h: 15\n\"i/j\": {\"k~l\": 16}\n");
        assertThat(repairs.repaired())
                .extracting(PackRemediation.Repaired::pointer)
                .containsExactly("/e/h", "/i~1j/k~0l");
    }

    @Test
    void theRepairsAreInTheFindingsOrderAcrossFiles() throws IOException {
        Files.writeString(pack.resolve("b.yaml"), "x: \"1\"\n", StandardCharsets.UTF_8);
        Reason inB = Findings.at(
                PackCodes.ARTIFACT_SCHEMA_ERROR,
                "b.yaml",
                List.of("x"),
                "b.yaml: 'x': " + QuotedNumbers.fault("1", true));

        Repairs repairs = repairs("y: \"2\"\nz: \"3\"\n", fixable("2", true, "y"), inB, fixable("3", true, "z"));

        assertThat(repairs.repaired())
                .extracting(PackRemediation.Repaired::file, PackRemediation.Repaired::pointer)
                .containsExactly(tuple(FILE, "/y"), tuple("b.yaml", "/x"), tuple(FILE, "/z"));
    }

    @Test
    void aFileGoneFromTheCopyIsNamed() throws IOException {
        Repairs repairs = QuotedNumbers.repair(pack, List.of(fixable("15", false, "a")));

        assertThat(repairs.gone()).contains(FILE);
        assertThat(repairs.repaired()).isEmpty();
    }

    @Test
    void theRemedyIsDroppedWhereRemediateCannotRepairAndReworded() {
        String detail = FILE + ": 'a': " + QuotedNumbers.fault("15", false);

        assertThat(QuotedNumbers.withoutRemedy(detail))
                .isEqualTo(FILE + ": 'a': \"15\" must be a number: write 15" + " without quotes");
        assertThat(QuotedNumbers.withoutRemedy("other")).isEqualTo("other");
        Reason finding = fixable("15", false, "a");
        assertThat(QuotedNumbers.blocked(finding).detail())
                .isEqualTo(FILE + ": 'a': \"15\" must be a number: write 15 without quotes; remediate does once the"
                        + " pack's other faults are fixed");
        assertThat(QuotedNumbers.unrepaired(finding, "it carries an anchor").detail())
                .isEqualTo(FILE + ": 'a': \"15\" must be a number: write 15 without quotes; remediate cannot: it"
                        + " carries an anchor");
        assertThat(QuotedNumbers.blocked(finding).location()).isEqualTo(finding.location());
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
    void theOffsetsHoldInACrlfFileWithAByteOrderMarkAndABlockScalarKeepsItsLineEnd() throws IOException {
        String text = "\uFEFFa: \"15\"\r\nb: |-\r\n  16\r\nc: 1\r\n";

        assertThat(repaired(text, fixable("15", false, "a"), fixable("16", true, "b")))
                .isEqualTo("\uFEFFa: 15\r\nb: 16\r\nc: 1\r\n");
    }

    @Test
    void theGuardRefusesARepairThatChangesMoreThanItsValues() {
        String before = "a: \"15\"\nb: x\n";

        assertThat(QuotedNumbers.readsAs(before, "a: 15\nb: x\n", Map.of("/a", "15")))
                .isTrue();
        assertThat(QuotedNumbers.readsAs(before, "a: 15\nb: y\n", Map.of("/a", "15")))
                .as("another value changed")
                .isFalse();
        assertThat(QuotedNumbers.readsAs(before, "a: 15\n", Map.of("/a", "15")))
                .as("a key went missing")
                .isFalse();
        assertThat(QuotedNumbers.readsAs(before, "a: 15\nb: x\n", Map.of("/z", "15")))
                .as("a place the file does not hold")
                .isFalse();
        assertThat(QuotedNumbers.readsAs(before, "a: [15\n", Map.of("/a", "15")))
                .as("a text the reader refuses")
                .isFalse();
    }

    @Test
    void aFileOfMoreThanOneDocumentIsLeftAsItIs() throws IOException {
        String text = "a: \"15\"\n---\na: \"15\"\n";

        assertThat(repaired(text, fixable("15", false, "a"))).isEqualTo(text);
    }
}
