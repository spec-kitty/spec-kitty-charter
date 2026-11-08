package ai.speckitty.charter.kernel.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The kernel's line rules of the {@code markdown} form (#299): each problem is worded to follow a field's name and
 * stands beside an accepted neighbour. The full tables run through the packs model and the missions step texts
 * ({@code MarkdownRuleCases}); these cases pin the rules' own wording.
 */
@DisplayName("MarkdownLines: the markdown form's line rules")
class MarkdownLinesTest {

    @Nested
    @DisplayName("a heading's text")
    class Headings {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(
                delimiterString = "->",
                value = {
                    "' ' -> must not be blank",
                    "' Lead' -> must not have leading or trailing whitespace",
                    "'Title ##' -> cannot be a heading's text: an H2 with a closing # sequence is ambiguous",
                    "'Tabbed\t#' -> cannot be a heading's text: an H2 with a closing # sequence is ambiguous"
                })
        @DisplayName("a blank, padded or non-reading-back title is refused with the rule's wording")
        void refused(String title, String problem) {
            assertThat(MarkdownLines.headingProblem(title.translateEscapes())).contains(problem);
        }

        @Test
        @DisplayName("a line break is refused, and its single-line neighbour accepted")
        void singleLine() {
            assertThat(MarkdownLines.headingProblem("Two\nlines")).contains("must be a single line");
            assertThat(MarkdownLines.headingProblem("Two lines")).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"x", "C#", "Release #1", "#hashtag"})
        @DisplayName("a single-line, unpadded text that reads back is accepted")
        void accepted(String title) {
            assertThat(MarkdownLines.headingProblem(title)).isEmpty();
        }
    }

    @Nested
    @DisplayName("a block")
    class Blocks {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(
                delimiterString = "->",
                value = {
                    "'\\ntext' -> must not start with a blank line",
                    "'text\\n' -> must not end with a blank line",
                    "'a\\rb' -> must not contain a carriage return",
                    "'one\\n## two' -> cannot stand inside a document: line 2 is a column-0 H2 heading outside a fence"
                })
        @DisplayName("a blank edge, a carriage return or a heading is refused, naming the block's own line")
        void refused(String block, String problem) {
            assertThat(MarkdownLines.blockProblem(block.translateEscapes())).contains(problem);
        }

        @ParameterizedTest
        @ValueSource(strings = {"```\nunclosed", "Paragraph\n===", " ## indented heading"})
        @DisplayName("an open fence, a setext underline and an indented heading are refused as ambiguous")
        void refusedAsAmbiguous(String block) {
            assertThat(MarkdownLines.blockProblem(block))
                    .hasValueSatisfying(problem -> assertThat(problem).startsWith("cannot stand inside a document: "));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "text", "### Heading", "```\n## fenced\n```"})
        @DisplayName("the empty block, text, a deeper heading and a fenced heading are accepted")
        void accepted(String block) {
            assertThat(MarkdownLines.blockProblem(block)).isEmpty();
        }
    }

    @Nested
    @DisplayName("a document's opening")
    class Openings {

        @Test
        @DisplayName("a --- line or a byte order mark is refused; a later --- line is accepted")
        void opening() {
            assertThat(MarkdownLines.openingProblem("---\nx"))
                    .hasValueSatisfying(problem -> assertThat(problem).startsWith("must not start with a --- line"));
            assertThat(MarkdownLines.openingProblem("\uFEFFintro"))
                    .hasValueSatisfying(
                            problem -> assertThat(problem).startsWith("must not start with a byte order mark"));
            assertThat(MarkdownLines.openingProblem("x\n\n---")).isEmpty();
            assertThat(MarkdownLines.openingProblem("")).isEmpty();
        }
    }
}
