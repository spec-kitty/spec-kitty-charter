package ai.speckitty.charter.packs.infra.storage.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument.Section;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec.Parsed;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The {@code markdown} form's codec (research D6, D12): every refusal rule with a refused text and an accepted
 * neighbour that differs in the one property the rule is about, the three structural non-vacuity pairs, the
 * frontmatter cases, and the canonical writer with its write-time read-back check.
 */
@DisplayName("MarkdownCodec: reading, refusals and the canonical form")
class MarkdownCodecTest {

    private static MarkdownDocument parsed(String text) {
        MarkdownCodec.Outcome outcome = MarkdownCodec.parse(text);
        assertThat(outcome).as("outcome of %s", text).isInstanceOf(Parsed.class);
        return ((Parsed) outcome).document();
    }

    private static MarkdownRefusal refusal(String text) {
        MarkdownCodec.Outcome outcome = MarkdownCodec.parse(text);
        assertThat(outcome).as("outcome of %s", text).isInstanceOf(MarkdownRefusal.class);
        return (MarkdownRefusal) outcome;
    }

    /** Refused text, the line the refusal names, a fragment of its reason, and the accepted neighbour. */
    static Stream<Arguments> refusalsWithNeighbours() {
        return Stream.of(
                Arguments.of("﻿# T\n", 1, "byte order mark", "# T\n"),
                Arguments.of("# T\n\ntext\r\n", 3, "carriage return", "# T\n\ntext\n"),
                Arguments.of("---\na: 1\n# T\n", 1, "unterminated frontmatter", "---\na: 1\n---\n# T\n"),
                Arguments.of("---\n- a\n---\n", 1, "must be a YAML mapping", "---\na: x\n---\n"),
                Arguments.of("---\nhello\n---\n", 1, "must be a YAML mapping", "---\nhello: x\n---\n"),
                Arguments.of("---\na: 1\na: 2\n---\n", 3, "not valid YAML", "---\na: 1\nb: 2\n---\n"),
                Arguments.of("---\n1: x\n---\n", 1, "a mapping key is not a string", "---\n'1': x\n---\n"),
                // A 4-backtick fence is not closed by 3 backticks, but is by 4.
                Arguments.of(
                        "````\n```\n## A\n```\n", 1, "unclosed fence opened at line 1", "````\n```\n## A\n```\n````\n"),
                // A tilde fence is not closed by backticks, only by tildes.
                Arguments.of("~~~\n```\n", 1, "unclosed fence opened at line 1", "~~~\n```\n~~~\n"),
                // A closer carries nothing but spaces after its run.
                Arguments.of("# T\n\n```\nx\n``` y\n", 3, "unclosed fence opened at line 3", "# T\n\n```\nx\n```  \n"),
                Arguments.of(" ## A\n", 1, "an H2 indented by 1 space", "## A\n"),
                Arguments.of("   # A\n", 1, "an H1 indented by 3 spaces", "# A\n"),
                Arguments.of("## A ##\n", 1, "closing # sequence", "## A #1\n"),
                Arguments.of("## A\n\n## #\n", 3, "closing # sequence", "## A\n\n## C#\n"),
                Arguments.of("##\n", 1, "empty heading", "## x\n"),
                Arguments.of("## \n", 1, "empty heading", "## x\n"),
                Arguments.of("##\tA\n", 1, "separated from its text by a tab", "## A\n"),
                Arguments.of("Paragraph\n===\n", 2, "setext", "Paragraph\n\n===\n"),
                // Under a paragraph line, --- is a setext underline; after a blank line it is a thematic break.
                Arguments.of("Paragraph\n---\n", 2, "setext", "Paragraph\n\n---\n"),
                Arguments.of("intro\n\n# T\n", 3, "an H1 that is not the first block", "# T\n\nintro\n"),
                Arguments.of("## S\n\n# T\n", 3, "an H1 that is not the first block", "## S\n\n### T\n"),
                Arguments.of("# A\n\n# B\n", 3, "a second H1 (the first is at line 1)", "# A\n\n## B\n"));
    }

    @ParameterizedTest(name = "[{index}] refused at line {1}: {2}")
    @MethodSource("refusalsWithNeighbours")
    @DisplayName("each refusal names its line and reason, and its one-property neighbour is accepted")
    void refusalAndNeighbour(String refused, int line, String reason, String neighbour) {
        MarkdownRefusal refusal = refusal(refused);

        assertThat(refusal.line()).isEqualTo(line);
        assertThat(refusal.reason()).contains(reason);
        assertThat(MarkdownCodec.parse(neighbour)).isInstanceOf(Parsed.class);
    }

    @Nested
    @DisplayName("structural non-vacuity pairs")
    class NonVacuity {

        @Test
        @DisplayName("1. '## Not a heading' inside a fence starts no section; the same line outside a fence does")
        void fenceHidesHeading() {
            MarkdownDocument fenced = parsed("## A\n\n```\n## Not a heading\n```\n");
            MarkdownDocument unfenced = parsed("## A\n\n## Not a heading\n");

            assertThat(fenced.sections()).hasSize(1);
            assertThat(fenced.sections().getFirst().content()).isEqualTo("```\n## Not a heading\n```");
            assertThat(unfenced.sections()).extracting(Section::title).containsExactly("A", "Not a heading");
        }

        @Test
        @DisplayName("2. one changed character in a section's content makes the models unequal at sections[i].content")
        void oneCharacterIsLocated() {
            String base = "# T\n\n## A\n\na\n\n## B\n\nb\n\n## C\n\nthe content\n";
            MarkdownDocument original = parsed(base);
            MarkdownDocument changed = parsed(base.replace("the content", "the contend"));

            assertThat(changed).isNotEqualTo(original);
            assertThat(MarkdownDocument.differences(original, changed)).containsExactly("sections[2].content");
        }

        @Test
        @DisplayName("3. blank lines added or removed between blocks keep the models equal")
        void blankLinesBetweenBlocksAreNotMeaning() {
            MarkdownDocument canonical = parsed("# T\n\nintro\n\n## A\n\ncontent\n\n## B\n");
            MarkdownDocument spread = parsed("\n\n# T\n\n\n\nintro\n\n\n## A\n\n\ncontent\n\n\n\n## B\n\n\n");
            MarkdownDocument tight = parsed("# T\nintro\n## A\ncontent\n## B");

            assertThat(spread).isEqualTo(canonical);
            assertThat(tight).isEqualTo(canonical);
            assertThat(MarkdownDocument.differences(canonical, spread)).isEmpty();
        }

        @Test
        @DisplayName("blank lines inside a block are content: the neighbour of pair 3 is unequal")
        void blankLinesInsideBlockAreMeaning() {
            assertThat(parsed("## A\n\none\ntwo\n")).isNotEqualTo(parsed("## A\n\none\n\ntwo\n"));
        }
    }

    @Nested
    @DisplayName("fences")
    class Fences {

        @Test
        @DisplayName(
                "an info string on the opener is allowed; a backtick in a backtick opener's info string is no fence")
        void infoStrings() {
            assertThat(parsed("```java\n## x\n```\n").sections()).isEmpty();
            assertThat(parsed("``` a`b\n## x\n").sections())
                    .extracting(Section::title)
                    .containsExactly("x");
        }

        @Test
        @DisplayName("a fence opens at any indentation, inside a list too, and hides headings")
        void indentedFence() {
            MarkdownDocument document = parsed("- item\n\n    ```\n## x\n    ```\n");

            assertThat(document.sections()).isEmpty();
            assertThat(document.preamble()).isEqualTo("- item\n\n    ```\n## x\n    ```");
        }

        @Test
        @DisplayName("H3 and deeper are content, indented ones in a list included")
        void deeperHeadingsAreContent() {
            MarkdownDocument document = parsed("## A\n\n### Sub\n\n1. step\n   ### Listed\n");

            assertThat(document.sections())
                    .singleElement()
                    .extracting(Section::content)
                    .isEqualTo("### Sub\n\n1. step\n   ### Listed");
        }
    }

    @Nested
    @DisplayName("frontmatter")
    class Frontmatter {

        @Test
        @DisplayName("no frontmatter and an empty frontmatter are different models")
        void absentVersusEmpty() {
            MarkdownDocument absent = parsed("# T\n");
            MarkdownDocument empty = parsed("---\n---\n# T\n");

            assertThat(absent.frontmatter()).isEmpty();
            assertThat(empty.frontmatter()).contains(Map.of());
            assertThat(MarkdownDocument.differences(absent, empty)).containsExactly("frontmatter");
        }

        @Test
        @DisplayName("frontmatter compares by YAML meaning: quoting a plain string changes the bytes, not the model")
        void comparedByMeaning() {
            MarkdownDocument plain = parsed("---\ndescription: x\nn: 1\n---\n");
            MarkdownDocument quoted = parsed("---\ndescription: 'x'\nn: 1\n---\n");
            MarkdownDocument number = parsed("---\ndescription: x\nn: '1'\n---\n");

            assertThat(quoted).isEqualTo(plain);
            assertThat(number).isNotEqualTo(plain);
            assertThat(plain.frontmatter().orElseThrow()).containsEntry("n", 1);
        }

        @Test
        @DisplayName("a frontmatter-only file reads and writes back unchanged")
        void frontmatterOnly() {
            String text = "---\ndescription: Capture discovery findings before specification\n---\n";
            MarkdownDocument document = parsed(text);

            assertThat(document.title()).isEmpty();
            assertThat(document.preamble()).isEmpty();
            assertThat(document.sections()).isEmpty();
            assertThat(MarkdownCodec.write(document)).isEqualTo(text);
        }

        /** Aliases chained level by level, each level twice the last: 48 aliases, 2^24 paths. */
        private static String aliasChain(int levels) {
            StringBuilder yaml = new StringBuilder("a0: &a0 [x, x]\n");
            for (int i = 1; i < levels; i++) {
                yaml.append("a%d: &a%d [*a%d, *a%d]\n".formatted(i, i, i - 1, i - 1));
            }
            return "---\n" + yaml + "---\n# T\n";
        }

        /** Frontmatter refused by a guard every pack YAML file passes, and its accepted neighbour. */
        static Stream<Arguments> guardedWithNeighbours() {
            return Stream.of(
                    Arguments.of(aliasChain(25), "the document is too large", aliasChain(3)),
                    Arguments.of(
                            "---\na: " + "[".repeat(10_000) + "]".repeat(10_000) + "\n---\n",
                            "the document is nested too deeply",
                            "---\na: [[[x]]]\n---\n"),
                    Arguments.of(
                            "---\na:\n" + nestedBlock(300) + "---\n",
                            "the document is nested too deeply",
                            "---\na:\n" + nestedBlock(3) + "---\n"),
                    Arguments.of(
                            "---\nb: !!binary aGVsbG8=\n---\n",
                            "a value is not a string, a number, a boolean or null",
                            "---\nb: aGVsbG8=\n---\n"),
                    Arguments.of(
                            "---\na:\n  1: x\n---\n", "a mapping key is not a string", "---\na:\n  '1': x\n---\n"));
        }

        /** A block mapping nested {@code depth} levels under the first key. */
        private static String nestedBlock(int depth) {
            StringBuilder yaml = new StringBuilder();
            for (int i = 1; i <= depth; i++) {
                yaml.append("  ".repeat(i)).append("k:\n");
            }
            return yaml.append("  ".repeat(depth + 1)).append("v: x\n").toString();
        }

        @ParameterizedTest(name = "[{index}] {1}")
        @MethodSource("guardedWithNeighbours")
        @DisplayName("frontmatter passes the guards of a pack YAML file: refused at the opening ---, never thrown")
        void guarded(String refused, String reason, String neighbour) {
            MarkdownRefusal refusal = refusal(refused);

            assertThat(refusal.line()).isEqualTo(1);
            assertThat(refusal.reason()).isEqualTo("frontmatter is refused: " + reason);
            assertThat(MarkdownCodec.parse(neighbour)).isInstanceOf(Parsed.class);
        }

        @Test
        @DisplayName("a parser refusal inside the frontmatter names the file's line, the frontmatter's line plus one")
        void parserRefusalNamesFileLine() {
            MarkdownRefusal refusal = refusal("---\na: 1\nb: 2\nb: 3\n---\n");

            assertThat(refusal.line()).isEqualTo(4);
            assertThat(refusal.reason()).startsWith("frontmatter is not valid YAML: ");
        }
    }

    @Nested
    @DisplayName("thematic breaks")
    class ThematicBreaks {

        @ParameterizedTest(name = "[{index}] marker {0}")
        @ValueSource(strings = {"- ", "* ", "_ "})
        @DisplayName("a 5000-marker break under a paragraph line reads in one pass and still ends the paragraph")
        void longBreakEndsParagraph(String marker) {
            String breakLine = marker.repeat(5_000);
            MarkdownDocument document = parsed("x\n" + breakLine + "\n---\n");
            MarkdownRefusal neighbour = refusal("x\n" + breakLine + "x\n---\n");

            assertThat(document.preamble()).isEqualTo("x\n" + breakLine + "\n---");
            assertThat(neighbour.line()).isEqualTo(3);
            assertThat(neighbour.reason()).contains("setext");
        }
    }

    @Nested
    @DisplayName("the canonical form")
    class Canonical {

        @Test
        @DisplayName("the empty file is the empty document, which writes as the empty string")
        void emptyFile() {
            assertThat(parsed("")).isEqualTo(MarkdownDocument.empty());
            assertThat(parsed("\n\n")).isEqualTo(MarkdownDocument.empty());
            assertThat(MarkdownCodec.write(MarkdownDocument.empty())).isEmpty();
        }

        @Test
        @DisplayName("blocks are separated by one blank line, frontmatter is followed directly, one final newline")
        void writesCanonically() {
            Map<String, Object> frontmatter = new LinkedHashMap<>();
            frontmatter.put("description", "Create a plan");
            frontmatter.put("tags", List.of("a", "b"));
            MarkdownDocument document = new MarkdownDocument(
                    Optional.of(frontmatter),
                    Optional.of("Title"),
                    "Intro.\n\nMore.",
                    List.of(new Section("Empty", ""), new Section("Full", "```\n## fenced\n```")));

            String written = MarkdownCodec.write(document);

            assertThat(written).isEqualTo("""
                            ---
                            description: Create a plan
                            tags:
                            - a
                            - b
                            ---
                            # Title

                            Intro.

                            More.

                            ## Empty

                            ## Full

                            ```
                            ## fenced
                            ```
                            """);
            assertThat(parsed(written)).isEqualTo(document);
        }

        @Test
        @DisplayName("a document without title and preamble starts with its first section")
        void sectionsOnly() {
            MarkdownDocument document = new MarkdownDocument(
                    Optional.empty(), Optional.empty(), "", List.of(new Section("A", "a"), new Section("B", "b")));

            assertThat(MarkdownCodec.write(document)).isEqualTo("## A\n\na\n\n## B\n\nb\n");
        }

        @Test
        @DisplayName("a non-canonical text reads to a model whose canonical form reads back to the same model")
        void canonicalisesSpacing() {
            MarkdownDocument document = parsed("#   Title  \n\n\nintro\n##  A\ncontent");

            assertThat(document.title()).contains("Title");
            assertThat(document.sections()).extracting(Section::title).containsExactly("A");
            assertThat(MarkdownCodec.write(document)).isEqualTo("# Title\n\nintro\n\n## A\n\ncontent\n");
        }

        @Test
        @DisplayName("write refuses a model whose output reads back differently: a title ending in a closing sequence")
        void writeChecksTitleReadBack() {
            MarkdownDocument document = new MarkdownDocument(Optional.empty(), Optional.of("Title ##"), "", List.of());

            assertThatIllegalStateException()
                    .isThrownBy(() -> MarkdownCodec.write(document))
                    .withMessageContaining("closing # sequence");
        }

        @Test
        @DisplayName("a leading --- line behind a title or a frontmatter writes and reads back as preamble")
        void leadingDelimiterBehindTitleOrFrontmatter() {
            MarkdownDocument titled = new MarkdownDocument(Optional.empty(), Optional.of("T"), "---\nx", List.of());
            MarkdownDocument fronted =
                    new MarkdownDocument(Optional.of(Map.of()), Optional.empty(), "---\nx", List.of());

            assertThat(MarkdownCodec.write(titled)).isEqualTo("# T\n\n---\nx\n");
            assertThat(MarkdownCodec.write(fronted)).isEqualTo("---\n---\n---\nx\n");
        }
    }
}
