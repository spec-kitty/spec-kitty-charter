package ai.speckitty.charter.packs.infra.storage.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.kernel.markdown.PromptSlots.Fault;
import ai.speckitty.charter.kernel.markdown.PromptSlots.Kind;
import ai.speckitty.charter.kernel.markdown.PromptSlots.Parse;
import ai.speckitty.charter.kernel.markdown.PromptSlots.Slot;
import ai.speckitty.charter.kernel.markdown.SlotId;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import ai.speckitty.charter.packs.infra.storage.OpenPacksCorpus;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec.Parsed;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The slot grammar reads the canonical text (ADR-0066 A5, T-05): load, validate mode and the read all parse
 * {@code MarkdownCodec.write(document)}, not the raw file. This test shows that the canonical render loses no marker,
 * no id, no order and no emptiness, over every built-in step prompt of the pinned open-packs checkout and over fixtures
 * that the render does change. A mutant canonicaliser that drops a comment makes the property fail, so it is not
 * vacuous.
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout and never
 * skips.
 */
@Tag("open-packs")
@DisplayName("PromptSlots reads the same slots from a prompt and from its canonical render (T-05)")
class PromptSlotsCanonicalCorpusTest {

    /** The slot every pinned marker declares. */
    private static final SlotId REASONS = new SlotId("spdd:reasons-block");

    /** The step prompts that carry the slot at the pin. */
    private static final List<String> SLOT_PROMPTS = List.of(
            "implement.prompt.md", "plan.prompt.md", "review.prompt.md", "specify.prompt.md", "tasks.prompt.md");

    /** A column-0 H2 inside a comment is split across sections by the codec, which also adds blank lines (A5). */
    private static final String HEADING_IN_COMMENT = """
            ## Section

            <!-- a:b:start -->
            <!-- a:b:end -->

            <!--
            ## hidden
            -->

            <!-- c:d:start --><!-- c:d:end -->
            """;

    /** Blank lines between blocks are dropped or normalised; blank lines inside a block, and a marker pair, stay. */
    private static final String SPREAD_BLOCKS = "\n\n# T\n\n\nintro\n\n\n\n## S\n\n\n<!-- c:d:start -->\n\n\n"
            + "<!-- c:d:end -->\n\n\n## U\n<!-- e:f:start -->text<!-- e:f:end -->";

    /** A marker in a fenced block, which the codec keeps as text. */
    private static final String IN_FENCE = "## S\n\n```\n<!-- a:b:start -->\n## not a heading\n<!-- a:b:end -->\n```\n";

    /** A frontmatter value that holds a pair, as the detailed description of a prompt may (MissionTypeBinding). */
    private static final String WITH_FRONTMATTER = """
            ---
            description: Create a plan <!-- a:b:start --><!-- a:b:end -->
            ---
            # Plan

            <!-- c:d:start -->
            filled
            <!-- c:d:end -->
            """;

    @BeforeAll
    static void pinned() {
        OpenPacksCorpus.assertPinned();
    }

    /** The text with its first comment removed: a canonicaliser that loses a marker. */
    private static String dropFirstComment(String text) {
        int open = text.indexOf("<!--");
        int close = text.indexOf("-->", open + 4);
        return text.substring(0, open) + text.substring(close + 3);
    }

    private static String mutant(MarkdownDocument document) {
        return dropFirstComment(MarkdownCodec.write(document));
    }

    /** The property: the slots of the raw text equal the slots of the text the canonicaliser writes, with the kinds. */
    private static void assertAgrees(String raw, Function<MarkdownDocument, String> canonicaliser) {
        MarkdownCodec.Outcome outcome = MarkdownCodec.parse(raw);
        assertThat(outcome).as("outcome of %s", raw).isInstanceOf(Parsed.class);
        String written = canonicaliser.apply(((Parsed) outcome).document());

        Parse fromRaw = PromptSlots.parse(raw);
        Parse fromCanonical = PromptSlots.parse(written);

        assertThat(fromCanonical.slots()).as("slots of the canonical text").isEqualTo(fromRaw.slots());
        assertThat(kinds(fromCanonical)).as("fault kinds of the canonical text").isEqualTo(kinds(fromRaw));
    }

    private static List<String> kinds(Parse parse) {
        return parse.faults().stream().map(Fault::kind).map(Kind::name).toList();
    }

    private static String canonicalOf(String raw) {
        return MarkdownCodec.write(((Parsed) MarkdownCodec.parse(raw)).document());
    }

    private static List<Path> builtInPrompts() throws IOException {
        Path missions = OpenPacksCorpus.root().resolve("packs/built-in/missions");
        try (Stream<Path> walk = Files.walk(missions)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".prompt.md"))
                    .filter(p -> "steps".equals(p.getParent().getFileName().toString()))
                    .sorted()
                    .toList();
        }
    }

    private static Map<String, String> prompts() throws IOException {
        Map<String, String> texts = new LinkedHashMap<>();
        for (Path file : builtInPrompts()) {
            texts.put(
                    OpenPacksCorpus.root().relativize(file).toString(), Files.readString(file, StandardCharsets.UTF_8));
        }
        return texts;
    }

    private static Map<String, String> fixtures() {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("fixture: a column-0 H2 inside a comment", HEADING_IN_COMMENT);
        texts.put("fixture: blank lines between blocks", SPREAD_BLOCKS);
        texts.put("fixture: markers in a fenced block", IN_FENCE);
        texts.put("fixture: frontmatter holding a marker pair", WITH_FRONTMATTER);
        return texts;
    }

    @Test
    @DisplayName("every built-in step prompt declares, in its canonical text, what its raw text declares")
    void pinnedPromptsAgree() throws IOException {
        Map<String, String> prompts = prompts();

        assertThat(prompts).as("built-in step prompts at the pin").isNotEmpty();
        prompts.forEach((name, raw) -> assertAgrees(raw, MarkdownCodec::write));
    }

    @Test
    @DisplayName("every fixture prompt declares, in its canonical text, what its raw text declares")
    void fixturesAgree() {
        fixtures().forEach((name, raw) -> assertAgrees(raw, MarkdownCodec::write));
    }

    @Test
    @DisplayName("the fixtures that the render changes are changed, and the canonical text declares the expected slots")
    void fixturesAreCanonicalised() {
        assertThat(canonicalOf(HEADING_IN_COMMENT))
                .isNotEqualTo(HEADING_IN_COMMENT)
                .contains("\n\n## hidden\n\n");
        assertThat(slotsOf(canonicalOf(HEADING_IN_COMMENT)))
                .containsExactly(new Slot(new SlotId("a:b"), 0, true), new Slot(new SlotId("c:d"), 1, true));

        assertThat(canonicalOf(SPREAD_BLOCKS)).isNotEqualTo(SPREAD_BLOCKS);
        assertThat(slotsOf(canonicalOf(SPREAD_BLOCKS)))
                .containsExactly(new Slot(new SlotId("c:d"), 0, true), new Slot(new SlotId("e:f"), 1, false));

        assertThat(slotsOf(canonicalOf(IN_FENCE))).containsExactly(new Slot(new SlotId("a:b"), 0, false));

        assertThat(slotsOf(canonicalOf(WITH_FRONTMATTER)))
                .containsExactly(new Slot(new SlotId("a:b"), 0, true), new Slot(new SlotId("c:d"), 1, false));
    }

    @Test
    @DisplayName(
            "mutant: a canonicaliser that drops the first comment makes the property fail on every prompt with a marker")
    void mutantIsCaught() throws IOException {
        Map<String, String> withMarkers = new LinkedHashMap<>();
        Stream.concat(prompts().entrySet().stream(), fixtures().entrySet().stream())
                .filter(entry -> PromptSlots.containsMarker(entry.getValue()))
                .forEach(entry -> withMarkers.put(entry.getKey(), entry.getValue()));

        assertThat(withMarkers).as("prompts and fixtures that carry a marker").hasSizeGreaterThanOrEqualTo(9);
        withMarkers.forEach(
                (name, raw) -> assertThatThrownBy(() -> assertAgrees(raw, PromptSlotsCanonicalCorpusTest::mutant))
                        .as("the mutant must fail on %s", name)
                        .isInstanceOf(AssertionError.class));
    }

    @Test
    @DisplayName(
            "positive control: the pinned built-in prompts hold 10 markers over 5 prompts, all non-empty spdd:reasons-block")
    void pinnedCorpusFacts() throws IOException {
        Map<String, String> withSlots = new LinkedHashMap<>();
        List<Slot> slots = new ArrayList<>();
        List<Fault> faults = new ArrayList<>();
        int markers = 0;
        for (Map.Entry<String, String> prompt : prompts().entrySet()) {
            String canonical = canonicalOf(prompt.getValue());
            Parse parse = PromptSlots.parse(canonical);
            markers += count(canonical, "<!-- spdd:reasons-block:start -->")
                    + count(canonical, "<!-- spdd:reasons-block:end -->");
            if (!parse.slots().isEmpty()) {
                withSlots.put(prompt.getKey(), canonical);
            }
            slots.addAll(parse.slots());
            faults.addAll(parse.faults());
        }

        assertThat(markers).as("slot markers in the built-in prompts").isEqualTo(10);
        assertThat(withSlots.keySet())
                .as("prompts that declare a slot")
                .hasSize(5)
                .allSatisfy(name -> assertThat(SLOT_PROMPTS).anyMatch(name::endsWith));
        assertThat(slots).hasSize(5).allSatisfy(slot -> {
            assertThat(slot.id()).isEqualTo(REASONS);
            assertThat(slot.empty()).as("slot %s is empty", slot).isFalse();
        });
        assertThat(faults)
                .as("the only faults are the non-empty slots, which become warnings by descriptor version")
                .hasSize(5)
                .allSatisfy(fault -> assertThat(fault.kind()).isEqualTo(Kind.NOT_EMPTY));
    }

    private static List<Slot> slotsOf(String canonical) {
        return PromptSlots.parse(canonical).slots();
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int from = text.indexOf(needle); from >= 0; from = text.indexOf(needle, from + needle.length())) {
            count++;
        }
        return count;
    }
}
