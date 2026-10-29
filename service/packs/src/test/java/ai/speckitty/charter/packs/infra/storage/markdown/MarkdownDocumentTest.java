package ai.speckitty.charter.packs.infra.storage.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownDocument.Section;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The construction invariants and the equality of {@link MarkdownDocument}: each invariant is shown refused and with
 * an accepted neighbour that differs in the one property the invariant is about.
 */
@DisplayName("MarkdownDocument: construction invariants and equality")
class MarkdownDocumentTest {

    private static MarkdownDocument withTitle(String title) {
        return new MarkdownDocument(Optional.empty(), Optional.of(title), "", List.of());
    }

    private static MarkdownDocument withPreamble(String preamble) {
        return new MarkdownDocument(Optional.empty(), Optional.empty(), preamble, List.of());
    }

    @Nested
    @DisplayName("titles (the H1 and every H2)")
    class Titles {

        @ParameterizedTest(name = "title {0} is refused")
        @ValueSource(strings = {"Two\nlines", "Carriage\rreturn", "", "   ", " Leading", "Trailing "})
        @DisplayName("a multi-line, blank or space-padded title is refused, naming the field")
        void refusedTitles(String title) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> withTitle(title))
                    .withMessageStartingWith("title ");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Section(title, ""))
                    .withMessageStartingWith("section title ");
        }

        @ParameterizedTest(name = "title {0} is accepted")
        @ValueSource(strings = {"Two lines", "Carriage return", "x", "Inner  spaces", "C#"})
        @DisplayName("the single-line, non-blank, unpadded neighbours are accepted")
        void acceptedTitles(String title) {
            assertThat(withTitle(title).title()).contains(title);
            assertThat(new Section(title, "").title()).isEqualTo(title);
        }
    }

    @Nested
    @DisplayName("blocks (the preamble and every section's content)")
    class Blocks {

        @ParameterizedTest(name = "block {0} is refused")
        @ValueSource(
                strings = {
                    "\ntext",
                    "  \ntext",
                    "text\n",
                    "text\n  ",
                    "text\r\nmore",
                    "## Heading",
                    "text\n\n# Heading",
                    "```\nunclosed",
                    " ## indented heading",
                    "Paragraph\n==="
                })
        @DisplayName("a leading or trailing blank line, a CR, a heading, an open fence or an ambiguity is refused")
        void refusedBlocks(String block) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> withPreamble(block))
                    .withMessageStartingWith("preamble ");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Section("S", block))
                    .withMessageStartingWith("section content ");
        }

        @ParameterizedTest(name = "block {0} is accepted")
        @ValueSource(
                strings = {
                    "",
                    "text",
                    "text\n\n\nmore",
                    "### Heading",
                    "text\n\n#Heading",
                    "```\n## Heading\n```",
                    "    ## indented four, so text",
                    "Paragraph\n\n==="
                })
        @DisplayName("the neighbours (empty, inner blank lines, H3, fenced or deeply indented headings) are accepted")
        void acceptedBlocks(String block) {
            assertThat(withPreamble(block).preamble()).isEqualTo(block);
            assertThat(new Section("S", block).content()).isEqualTo(block);
        }

        @Test
        @DisplayName("the refusal names the line inside the block that would read as a heading")
        void headingRefusalNamesTheLine() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Section("S", "one\ntwo\n## three"))
                    .withMessageContaining("line 3 is a column-0 H2 heading outside a fence");
        }
    }

    @Nested
    @DisplayName("copies")
    class Copies {

        @Test
        @DisplayName("the sections list is copied and unmodifiable")
        void sectionsAreCopied() {
            List<Section> source = new ArrayList<>(List.of(new Section("A", "")));
            MarkdownDocument document = new MarkdownDocument(Optional.empty(), Optional.empty(), "", source);

            source.add(new Section("B", ""));

            assertThat(document.sections()).extracting(Section::title).containsExactly("A");
            assertThatThrownBy(() -> document.sections().add(new Section("C", "")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("the frontmatter mapping is copied and unmodifiable at its top level")
        void frontmatterIsCopied() {
            Map<String, Object> source = new LinkedHashMap<>(Map.of("a", "1"));
            MarkdownDocument document = new MarkdownDocument(Optional.of(source), Optional.empty(), "", List.of());

            source.put("b", "2");

            assertThat(document.frontmatter()).contains(Map.of("a", "1"));
            assertThatThrownBy(() -> document.frontmatter().orElseThrow().put("c", "3"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("equality and differences")
    class Equality {

        @Test
        @DisplayName("frontmatter compares as loaded values, whatever map implementation holds them")
        void frontmatterComparesByValue() {
            Map<String, Object> ordered = new LinkedHashMap<>();
            ordered.put("tags", List.of("a", "b"));
            ordered.put("n", 1);

            assertThat(new MarkdownDocument(Optional.of(ordered), Optional.empty(), "", List.of()))
                    .isEqualTo(new MarkdownDocument(
                            Optional.of(Map.of("n", 1, "tags", List.of("a", "b"))), Optional.empty(), "", List.of()));
        }

        @Test
        @DisplayName("an absent frontmatter and an empty one are different models")
        void absentAndEmptyFrontmatterDiffer() {
            MarkdownDocument absent = MarkdownDocument.empty();
            MarkdownDocument empty = new MarkdownDocument(Optional.of(Map.of()), Optional.empty(), "", List.of());

            assertThat(absent).isNotEqualTo(empty);
            assertThat(MarkdownDocument.differences(absent, empty)).containsExactly("frontmatter");
        }

        @Test
        @DisplayName("differences locate every differing field by its model path; equal documents have none")
        void differencesLocateByPath() {
            MarkdownDocument a = new MarkdownDocument(
                    Optional.empty(), Optional.of("T"), "intro", List.of(new Section("A", "a"), new Section("B", "b")));
            MarkdownDocument b = new MarkdownDocument(
                    Optional.of(Map.of()),
                    Optional.of("U"),
                    "intro!",
                    List.of(new Section("A", "a!"), new Section("B2", "b"), new Section("C", "")));

            assertThat(MarkdownDocument.differences(a, a)).isEmpty();
            assertThat(MarkdownDocument.differences(a, b))
                    .containsExactly(
                            "frontmatter",
                            "title",
                            "preamble",
                            "sections[0].content",
                            "sections[1].title",
                            "sections[2]");
        }
    }
}
