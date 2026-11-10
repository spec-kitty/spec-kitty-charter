package ai.speckitty.charter.kernel.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.markdown.PromptSlots.Fault;
import ai.speckitty.charter.kernel.markdown.PromptSlots.Kind;
import ai.speckitty.charter.kernel.markdown.PromptSlots.Parse;
import ai.speckitty.charter.kernel.markdown.PromptSlots.Slot;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The slot grammar of ADR-0066 decision 2 (FR-007, T-05): a parameterized "refused / accepted neighbour" table with one
 * row per edge case, each stating the whole answer (the declared slots, in order, with their emptiness, and the kinds
 * of the faults, in the order found), then the facts a row cannot state: positions, lines, reasons, determinism and
 * the cost of a long text.
 */
@DisplayName("PromptSlots: the slot grammar")
class PromptSlotsTest {

    private static final String START = "<!-- a:b:start -->";
    private static final String END = "<!-- a:b:end -->";
    private static final String PAIR = START + "\n" + END;

    private static final List<String> NO_SLOTS = List.of();
    private static final List<Kind> NO_FAULTS = List.of();

    /** An empty slot is written {@code id}, a slot holding text {@code id+text}. */
    private static List<String> slotsOf(Parse parse) {
        return parse.slots().stream()
                .map(slot -> slot.id() + (slot.empty() ? "" : "+text"))
                .toList();
    }

    private static List<Kind> kindsOf(Parse parse) {
        return parse.faults().stream().map(Fault::kind).toList();
    }

    /** Label, text, the declared slots, the kinds of the faults, and whether the text holds a marker candidate. */
    static Stream<Arguments> table() {
        return Stream.of(
                // Candidates: what makes a comment a marker, and what its id may be.
                Arguments.of(
                        "accepted: no spaces", "<!--a:b:start-->\n<!--a:b:end-->", List.of("a:b"), NO_FAULTS, true),
                Arguments.of(
                        "accepted: padded",
                        "<!--   a:b:start   -->\n<!--\ta:b:end\t-->",
                        List.of("a:b"),
                        NO_FAULTS,
                        true),
                Arguments.of(
                        "refused: uppercase namespace", "<!-- A:b:start -->", NO_SLOTS, List.of(Kind.MALFORMED), true),
                Arguments.of("refused: uppercase name", "<!-- a:B:end -->", NO_SLOTS, List.of(Kind.MALFORMED), true),
                Arguments.of("refused: no namespace", "<!-- x:start -->", NO_SLOTS, List.of(Kind.MALFORMED), true),
                Arguments.of("refused: empty id", "<!-- :start -->", NO_SLOTS, List.of(Kind.MALFORMED), true),
                Arguments.of(
                        "refused: a note that ends in :start",
                        "<!-- note: see x:start -->",
                        NO_SLOTS,
                        List.of(Kind.MALFORMED),
                        true),
                Arguments.of(
                        "accepted: a comment over several lines",
                        "<!--\na:b:start\n-->\n<!--\na:b:end\n-->",
                        List.of("a:b"),
                        NO_FAULTS,
                        true),
                Arguments.of(
                        "accepted: two markers on one line",
                        "text " + START + END + " text",
                        List.of("a:b"),
                        NO_FAULTS,
                        true),
                Arguments.of("accepted: no markers at all", "# T\n\nJust text.\n", NO_SLOTS, NO_FAULTS, false),
                // Comments that are text.
                Arguments.of("text: decision_id", "<!-- decision_id: x -->", NO_SLOTS, NO_FAULTS, false),
                Arguments.of("text: an empty comment", "<!---->\n<!-- -->", NO_SLOTS, NO_FAULTS, false),
                Arguments.of(
                        "text: the suffix is lowercase only",
                        "<!-- a:b:START -->\n<!-- a:b:End -->",
                        NO_SLOTS,
                        NO_FAULTS,
                        false),
                Arguments.of("text: the suffix ends the content", "<!-- a:b:start now -->", NO_SLOTS, NO_FAULTS, false),
                // No slot and no fault of its own; a fragment may not leave one open, so it counts as a marker.
                Arguments.of("text: a comment never closed", "<!-- a:b:start\nmore", NO_SLOTS, NO_FAULTS, true),
                Arguments.of(
                        "text: a comment never closed, in a body", "Use the plan. <!-- ", NO_SLOTS, NO_FAULTS, true),
                // CommonMark closes <!--> and <!---> where they stand, so neither runs on to the next -->.
                Arguments.of(
                        "text: <!--> is a complete comment",
                        "# T\n\n<!--> hi\n\n" + PAIR + "\n",
                        List.of("a:b"),
                        NO_FAULTS,
                        true),
                Arguments.of(
                        "text: <!---> is a complete comment",
                        "# T\n\n<!---> hi\n\n" + PAIR + "\n",
                        List.of("a:b"),
                        NO_FAULTS,
                        true),
                Arguments.of("text: <!--> alone is no marker", "<!--> hi", NO_SLOTS, NO_FAULTS, false),
                Arguments.of("text: a comment never opened", "a:b:start -->", NO_SLOTS, NO_FAULTS, false),
                // Where a marker may stand: any marker counts (d7).
                Arguments.of(
                        "candidate: in an HTML block", "<div>\n" + PAIR + "\n</div>", List.of("a:b"), NO_FAULTS, true),
                Arguments.of("candidate: in inline code", "`" + START + "`", NO_SLOTS, List.of(Kind.MALFORMED), true),
                Arguments.of("candidate: in a fenced block", "```\n" + PAIR + "\n```", List.of("a:b"), NO_FAULTS, true),
                Arguments.of(
                        "candidate: in a frontmatter value",
                        "---\ndescription: Create a plan " + START + END + "\n---\n# T\n",
                        List.of("a:b"),
                        NO_FAULTS,
                        true),
                // Pairing.
                Arguments.of("refused: an end with no start", END, NO_SLOTS, List.of(Kind.MALFORMED), true),
                Arguments.of(
                        "refused: an end before its start",
                        END + "\n" + START,
                        NO_SLOTS,
                        List.of(Kind.MALFORMED, Kind.MALFORMED),
                        true),
                Arguments.of("refused: a start with no end", START, NO_SLOTS, List.of(Kind.MALFORMED), true),
                Arguments.of(
                        "refused: the same id declared twice",
                        PAIR + "\n" + PAIR,
                        List.of("a:b"),
                        List.of(Kind.MALFORMED, Kind.MALFORMED),
                        true),
                Arguments.of(
                        "accepted: two different ids, in order",
                        PAIR + "\n<!-- c:d:start --><!-- c:d:end -->",
                        List.of("a:b", "c:d"),
                        NO_FAULTS,
                        true),
                Arguments.of(
                        "refused: a start inside an open slot",
                        START + "\n<!-- c:d:start -->\n<!-- c:d:end -->\n" + END,
                        List.of("a:b+text"),
                        List.of(Kind.MALFORMED, Kind.MALFORMED, Kind.NOT_EMPTY),
                        true),
                Arguments.of(
                        "refused: an end of another id closes nothing",
                        START + "\n<!-- c:d:end -->\n" + END,
                        List.of("a:b+text"),
                        List.of(Kind.MALFORMED, Kind.NOT_EMPTY),
                        true),
                Arguments.of(
                        "refused: a comment that swallows the end",
                        START + "\n<!-- not closed\n" + END,
                        NO_SLOTS,
                        List.of(Kind.MALFORMED, Kind.MALFORMED),
                        true),
                // Emptiness: the explicit set of space, tab and LF.
                Arguments.of("empty: nothing between", START + END, List.of("a:b"), NO_FAULTS, true),
                Arguments.of(
                        "empty: spaces, tabs and LFs", START + " \t\n \n\t " + END, List.of("a:b"), NO_FAULTS, true),
                Arguments.of(
                        "not empty: text",
                        START + "\nsome text\n" + END,
                        List.of("a:b+text"),
                        List.of(Kind.NOT_EMPTY),
                        true),
                Arguments.of(
                        "not empty: a no-break space",
                        START + " " + END,
                        List.of("a:b+text"),
                        List.of(Kind.NOT_EMPTY),
                        true),
                Arguments.of(
                        "not empty: a zero-width space",
                        START + "​" + END,
                        List.of("a:b+text"),
                        List.of(Kind.NOT_EMPTY),
                        true),
                Arguments.of(
                        "not empty: an ideographic space",
                        START + "　" + END,
                        List.of("a:b+text"),
                        List.of(Kind.NOT_EMPTY),
                        true),
                Arguments.of(
                        "not empty: a form feed",
                        START + "\f" + END,
                        List.of("a:b+text"),
                        List.of(Kind.NOT_EMPTY),
                        true),
                Arguments.of(
                        "not empty: a non-ASCII character",
                        START + "é" + END,
                        List.of("a:b+text"),
                        List.of(Kind.NOT_EMPTY),
                        true),
                Arguments.of(
                        "not empty: a non-BMP character",
                        START + "😀" + END,
                        List.of("a:b+text"),
                        List.of(Kind.NOT_EMPTY),
                        true),
                Arguments.of(
                        "not empty: another comment",
                        START + "<!-- note -->" + END,
                        List.of("a:b+text"),
                        List.of(Kind.NOT_EMPTY),
                        true));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("table")
    @DisplayName("each row states the declared slots, the kinds of the faults and whether a marker is there")
    void row(String label, String text, List<String> slots, List<Kind> faults, boolean marker) {
        Parse parse = PromptSlots.parse(text);

        assertThat(slotsOf(parse)).as("slots of %s", label).isEqualTo(slots);
        assertThat(kindsOf(parse)).as("faults of %s", label).isEqualTo(faults);
        assertThat(PromptSlots.containsMarker(text))
                .as("containsMarker of %s", label)
                .isEqualTo(marker);
    }

    @Nested
    @DisplayName("what a row cannot state")
    class Details {

        @Test
        @DisplayName("slots are in declaration order, numbered from 0, with their emptiness")
        void positions() {
            Parse parse = PromptSlots.parse(
                    "<!-- z:last:start --><!-- z:last:end -->\n<!-- a:first:start -->x<!-- a:first:end -->");

            assertThat(parse.slots())
                    .containsExactly(
                            new Slot(new SlotId("z:last"), 0, true), new Slot(new SlotId("a:first"), 1, false));
        }

        @Test
        @DisplayName("a fault names its kind, its slot, the line of its marker and a reason")
        void faultsAreLocated() {
            String text = "intro\n\n" + START + "\ntext\n" + END + "\n\n<!-- Bad:id:start -->\n";

            List<Fault> faults = PromptSlots.parse(text).faults();

            assertThat(faults).hasSize(2);
            assertThat(faults.get(0).kind()).isEqualTo(Kind.NOT_EMPTY);
            assertThat(faults.get(0).slot()).contains(new SlotId("a:b"));
            assertThat(faults.get(0).line()).isEqualTo(3);
            assertThat(faults.get(0).reason()).isNotBlank();
            assertThat(faults.get(1).kind()).isEqualTo(Kind.MALFORMED);
            assertThat(faults.get(1).slot()).isEqualTo(Optional.empty());
            assertThat(faults.get(1).line()).isEqualTo(7);
            assertThat(faults.get(1).reason()).contains("Bad:id");

            String longId = "X".repeat(500);
            assertThat(PromptSlots.parse("<!-- " + longId + ":start -->").faults())
                    .singleElement()
                    .satisfies(fault -> assertThat(fault.reason())
                            .contains("X".repeat(64) + "…")
                            .doesNotContain("X".repeat(65)));
        }

        @Test
        @DisplayName("a comment over several lines counts the lines it spans")
        void linesAfterAMultiLineComment() {
            Parse parse = PromptSlots.parse("<!--\nnote\n-->\n<!-- a:b:end -->");

            assertThat(parse.faults())
                    .singleElement()
                    .satisfies(fault -> assertThat(fault.line()).isEqualTo(4));
        }

        @Test
        @DisplayName("an unclosed start is reported at its own line, after the faults found before the end of the text")
        void unclosedStartIsReportedLast() {
            Parse parse = PromptSlots.parse(START + "\n<!-- c:d:end -->\n");

            assertThat(parse.faults()).extracting(Fault::line).containsExactly(2, 1);
            assertThat(parse.faults())
                    .extracting(Fault::slot)
                    .containsExactly(Optional.of(new SlotId("c:d")), Optional.of(new SlotId("a:b")));
        }

        @Test
        @DisplayName("the same text gives equal answers, and a text with no marker gives no slot and no fault")
        void deterministic() {
            String text = "x\n" + PAIR + "\n<!-- Bad:id:end -->";

            assertThat(PromptSlots.parse(text)).isEqualTo(PromptSlots.parse(new String(text)));
            assertThat(PromptSlots.parse(text).slots()).isNotEmpty();
            assertThat(PromptSlots.parse("")).isEqualTo(new Parse(List.of(), List.of()));
        }

        @Test
        @DisplayName("a long text of many pairs, and a long text of unclosed comments, are read in linear time")
        void longInput() {
            StringBuilder many = new StringBuilder();
            for (int i = 0; i < 20_000; i++) {
                many.append("<!-- a:s")
                        .append(i)
                        .append(":start --><!-- a:s")
                        .append(i)
                        .append(":end -->\n");
            }
            String unclosed = "<!-- a:b:start ".repeat(200_000);

            Parse pairsParse = Assertions.assertTimeoutPreemptively(
                    Duration.ofSeconds(10), () -> PromptSlots.parse(many.toString()));
            Parse unclosedParse =
                    Assertions.assertTimeoutPreemptively(Duration.ofSeconds(10), () -> PromptSlots.parse(unclosed));

            assertThat(pairsParse.slots()).hasSize(20_000);
            assertThat(pairsParse.faults()).isEmpty();
            assertThat(unclosedParse.slots()).isEmpty();
            assertThat(unclosedParse.faults()).isEmpty();
        }
    }
}
