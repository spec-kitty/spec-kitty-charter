package ai.speckitty.charter.packs.domain.markdown;

import java.util.List;

/**
 * The texts the {@code markdown} form's line rules refuse and accept, in one place: {@link MarkdownDocumentTest} drives
 * the model with them, and {@code StepDocumentRulesTest} drives the missions domain's step texts with the same rows,
 * so the two cannot drift apart (#299). Each refused row has an accepted neighbour that differs in the one property the
 * rule is about.
 */
public final class MarkdownRuleCases {

    private MarkdownRuleCases() {}

    /**
     * Titles refused as multi-line, blank or space-padded.
     *
     * @return the texts
     */
    public static List<String> refusedTitles() {
        return List.of("Two\nlines", "Carriage\rreturn", "", "   ", " Leading", "Trailing ");
    }

    /**
     * Titles refused because they would not read back as a heading's text (a closing {@code #} sequence, #271).
     *
     * @return the texts
     */
    public static List<String> titlesThatDoNotReadBack() {
        return List.of("Title ##", "#", "Release #", "Tabbed\t#");
    }

    /**
     * The accepted neighbours of the refused titles.
     *
     * @return the texts
     */
    public static List<String> acceptedTitles() {
        return List.of("Two lines", "Carriage return", "x", "Inner  spaces", "C#", "Release #1", "#hashtag");
    }

    /**
     * Blocks refused for a leading or trailing blank line, a carriage return, a heading, an open fence or an
     * ambiguity.
     *
     * @return the texts
     */
    public static List<String> refusedBlocks() {
        return List.of(
                "\ntext",
                "  \ntext",
                "text\n",
                "text\n  ",
                "text\r\nmore",
                "## Heading",
                "text\n\n# Heading",
                "```\nunclosed",
                " ## indented heading",
                "Paragraph\n===");
    }

    /**
     * The accepted neighbours of the refused blocks.
     *
     * @return the texts
     */
    public static List<String> acceptedBlocks() {
        return List.of(
                "",
                "text",
                "text\n\n\nmore",
                "### Heading",
                "text\n\n#Heading",
                "```\n## Heading\n```",
                "    ## indented four, so text",
                "Paragraph\n\n===");
    }

    /**
     * Preambles refused when they open the text (neither frontmatter nor a title), and accepted behind either.
     *
     * @return the texts
     */
    public static List<String> openingPreambles() {
        return List.of("---", "---\nx", "\uFEFFintro");
    }
}
