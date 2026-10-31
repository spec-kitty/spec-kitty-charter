package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The pack document (#260, ADR-0042 decision 8): its own checks, each refusal beside a clean control, the deep copy of
 * its frontmatter, and the {@code Pack} slot that holds it.
 */
@DisplayName("Pack document (#260)")
class PackDocumentTest {

    private static final PackIdentifier ID = new PackIdentifier("01PACK");
    private static final PackDescriptor DESCRIPTOR =
            new PackDescriptor("01PACK", "1.0.0", Optional.empty(), "demo", Optional.empty(), Optional.empty());
    private static final PackDocument DOCUMENT = new PackDocument(
            Optional.empty(),
            Optional.of("Demo"),
            "What the pack is for.",
            List.of(new PackDocument.Section("Contents", "Directives.")));

    private static PackDocument titled(String title) {
        return new PackDocument(Optional.empty(), Optional.of(title), "", List.of());
    }

    private static PackDocument withFrontmatter(Map<String, Object> frontmatter) {
        return new PackDocument(Optional.of(frontmatter), Optional.empty(), "p", List.of());
    }

    @Test
    @DisplayName("a title or section title that is multi-line, blank, padded or not read back is refused when built")
    void headingsAreHeadingsTheCodecReadsBack() {
        assertThatCode(() -> titled("Demo pack")).doesNotThrowAnyException();
        assertThatCode(() -> titled("Release #1")).doesNotThrowAnyException();
        assertThatCode(() -> new PackDocument.Section("Contents", "")).doesNotThrowAnyException();

        assertBreach(() -> titled("Demo\npack"), "title must be a single line");
        assertBreach(() -> titled(" "), "title must not be blank");
        assertBreach(
                () -> new PackDocument.Section(" Contents", "x"), "section title must not have leading or trailing");
        // #271: the codec refused these only when the document was written.
        assertBreach(() -> titled("Release #"), "title cannot be a heading's text: an H2 with a closing # sequence");
        assertBreach(() -> new PackDocument.Section("Steps ##", "x"), "section title cannot be a heading's text");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "'# A second title' | cannot stand inside a document: line 1 is a column-0 H1 heading outside a fence",
                "'text\n## A section inside' | cannot stand inside a document: line 2 is a column-0 H2 heading",
                "' ## An indented H2' | cannot stand inside a document: line 1: an H2 indented by 1 space is ambiguous",
                "'## ' | cannot stand inside a document: line 1: an empty heading is ambiguous",
                "'##\ttabbed' | cannot stand inside a document: line 1: an H2 separated from its text by a tab",
                "'```bash\nunclosed fence' | cannot stand inside a document: line 1: unclosed fence opened at line 1",
                "'a paragraph line\n===' | cannot stand inside a document: line 2: a setext heading underline",
                "'\nleading blank line' | must not start with a blank line",
                "'trailing blank line\n' | must not end with a blank line",
                "'a carriage return\r\nhere' | must not contain a carriage return"
            })
    @DisplayName("#271: a preamble or section content the codec refuses is refused when the document is built")
    void blocksTheCodecRefusesAreRefusedWhenBuilt(String written, String problem) {
        String block = written.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
        assertBreach(
                () -> new PackDocument(Optional.empty(), Optional.of("Demo"), block, List.of()),
                "Constraint breached: preamble " + problem);
        assertBreach(
                () -> new PackDocument.Section("Contents", block), "Constraint breached: section content " + problem);
    }

    @Test
    @DisplayName("#271: a preamble that opens the file must not read back as frontmatter; under a title it may")
    void anOpeningPreambleMustNotReadAsFrontmatter() {
        assertBreach(
                () -> new PackDocument(Optional.empty(), Optional.empty(), "---\nkey: value", List.of()),
                "preamble must not start with a --- line");
        assertThatCode(() -> new PackDocument(Optional.empty(), Optional.of("Demo"), "---\nkey: value", List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> new PackDocument(Optional.of(Map.of()), Optional.empty(), "---", List.of()))
                .doesNotThrowAnyException();
    }

    private static void assertBreach(ThrowingCallable build, String why) {
        assertThatThrownBy(build).isInstanceOf(ConstraintBreachException.class).hasMessageContaining(why);
    }

    @Test
    @DisplayName("the frontmatter is copied deeply and cannot be changed, a YAML null kept as a value")
    void theFrontmatterIsADeepUnmodifiableCopy() {
        List<Object> tags = new ArrayList<>(List.of("a"));
        Map<String, Object> nested = new HashMap<>(Map.of("k", 1));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("tags", tags);
        source.put("nested", nested);
        source.put("none", null);

        PackDocument document = withFrontmatter(source);
        tags.add("b");
        nested.put("k", 2);

        Map<String, Object> held = document.frontmatter().orElseThrow();
        assertThat(held).containsEntry("tags", List.of("a")).containsEntry("nested", Map.of("k", 1));
        assertThat(held).containsKey("none");
        assertThat(held.get("none")).isNull();
        assertThatThrownBy(() -> held.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<?>) held.get("tags")).clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((Map<?, ?>) held.get("nested")).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("a frontmatter value that is not a YAML value is refused; strings, booleans and numbers are not")
    void aNonYamlValueIsRefused() {
        assertThatCode(() -> withFrontmatter(Map.of("s", "x", "b", true, "n", 1.5)))
                .doesNotThrowAnyException();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> withFrontmatter(Map.of("when", Instant.EPOCH)))
                .withMessage("frontmatter values must be YAML values, but one is a java.time.Instant");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> withFrontmatter(Map.of("nested", Map.of(1, "x"))))
                .withMessage("frontmatter keys must be strings, but one is 1");
    }

    @Test
    @DisplayName("the Pack keeps its document through toBuilder and with, and withoutDocument clears it")
    void thePackSlotKeepsTheDocument() {
        Pack pack =
                Pack.builder().id(ID).descriptor(DESCRIPTOR).document(DOCUMENT).build();
        Directive directive = Directive.builder()
                .id(new DirectiveIdentifier("D"))
                .packId(ID)
                .schemaVersion("1.0")
                .title("t")
                .intent("i")
                .enforcement(Enforcement.REQUIRED)
                .build();

        assertThat(pack.document()).contains(DOCUMENT);
        assertThat(pack.toBuilder().build().document()).contains(DOCUMENT);
        assertThat(pack.with(directive).document()).contains(DOCUMENT);
        assertThat(pack.toBuilder().withoutDocument().build().document()).isEmpty();
        assertThat(Pack.builder().id(ID).descriptor(DESCRIPTOR).build().document())
                .isEmpty();
    }
}
