package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The rules of a toolguide's {@link Guide} (#194, data-model "Guide"): each refusal beside an accepted neighbour. */
@DisplayName("Guide rules")
class GuideTest {

    private static final String CONTENT_RULE = "no column-0 H1 or H2 outside a fence";

    @Test
    @DisplayName("the seven fixed blocks have their canonical headings in write order; only the overview is required")
    void theFixedBlocksAreCanonical() {
        assertThat(GuideBlock.values())
                .extracting(GuideBlock::heading)
                .containsExactly(
                        "Overview", "When to use", "Setup", "Usage", "Cautions", "Known limitations", "References");
        assertThat(GuideBlock.values()).filteredOn(GuideBlock::required).containsExactly(GuideBlock.OVERVIEW);
        assertThat(GuideBlock.values())
                .filteredOn(GuideBlock::leading)
                .containsExactly(GuideBlock.OVERVIEW, GuideBlock.WHEN_TO_USE, GuideBlock.SETUP, GuideBlock.USAGE);
        assertThat(GuideBlock.byHeading("Known limitations")).contains(GuideBlock.KNOWN_LIMITATIONS);
        assertThat(GuideBlock.byHeading("Known Limitations"))
                .as("exact text only")
                .isEmpty();
        assertThat(GuideBlock.reserving("known LIMITATIONS")).contains(GuideBlock.KNOWN_LIMITATIONS);
        assertThat(GuideBlock.reserving("Known limitation")).isEmpty();
    }

    @Test
    @DisplayName("a guide needs an overview that is not blank; an overview alone is a guide")
    void aGuideNeedsAnOverview() {
        assertThat(Guide.of("What it is.").overview()).isEqualTo("What it is.");
        assertThatThrownBy(() -> Guide.of(" "))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("overview that is not blank");
        assertThatThrownBy(() -> new Guide(Map.of(GuideBlock.USAGE, "Run it."), List.of()))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("overview that is not blank");
        assertThatCode(() -> new Guide(Map.of(GuideBlock.OVERVIEW, "O", GuideBlock.USAGE, ""), List.of()))
                .as("a block other than the overview may be empty")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("blocks are held in block order whatever order they are given in, and the parts are copied")
    void blocksAreHeldInBlockOrder() {
        Map<GuideBlock, String> given = new EnumMap<>(GuideBlock.class);
        given.put(GuideBlock.REFERENCES, "- https://example.org");
        given.put(GuideBlock.OVERVIEW, "O");
        Guide guide = new Guide(given, List.of(new GuideSection("Examples", "e")));
        given.put(GuideBlock.SETUP, "changed after construction");

        assertThat(guide.blocks().keySet()).containsExactly(GuideBlock.OVERVIEW, GuideBlock.REFERENCES);
        assertThat(guide.block(GuideBlock.SETUP)).isEmpty();
        assertThat(guide.block(GuideBlock.REFERENCES)).contains("- https://example.org");
        assertThat(guide)
                .isEqualTo(new Guide(
                        Map.of(GuideBlock.OVERVIEW, "O", GuideBlock.REFERENCES, "- https://example.org"),
                        List.of(new GuideSection("Examples", "e"))));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "# An H1 in content",
                "## An H2 in content",
                "text\n## An H2 after text",
                "#",
                "##\ttabbed",
                "```bash\nunclosed fence",
                "``` not `a` fence: a backtick in the info string opens none\n# so this is an H1",
                "\nleading blank line",
                "trailing blank line\n",
                "trailing blank line\n  ",
                "a carriage return\r\nhere"
            })
    @DisplayName("content that could not stand as an H2 section is refused")
    void contentThatCannotStandAsASectionIsRefused(String content) {
        assertThatThrownBy(() -> Guide.of(content))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining(CONTENT_RULE);
        assertThatThrownBy(() -> new GuideSection("Examples", content))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining(CONTENT_RULE);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "### An H3 stays in content",
                "#hashtag is text",
                " # an indented line is text here; the Markdown reader judges it",
                "```bash\n# a comment in a fence\n## and another\n```",
                "  ~~~~\n# inside an indented tilde fence\n~~~\n## still inside: the closer is shorter\n  ~~~~~ \nafter",
                "````\n```\n# a nested backtick line is inside\n```\n````",
                "text\n\n  more text"
            })
    @DisplayName("content whose H1/H2-looking lines are H3, text or fenced is accepted")
    void fencedOrDeeperHeadingsAreContent(String content) {
        assertThatCode(() -> Guide.of(content)).as(content).doesNotThrowAnyException();
        assertThatCode(() -> new GuideSection("Examples", content)).as(content).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a freeform section may not take a fixed heading in any letter case; a near neighbour is free")
    void aFreeformTitleIsNotAFixedHeading() {
        for (String reserved : List.of("Overview", "overview", "USAGE", "When To Use", "known limitations")) {
            assertThatThrownBy(() -> new GuideSection(reserved, "x"))
                    .as(reserved)
                    .isInstanceOf(ConstraintBreachException.class)
                    .hasMessageContaining("not a fixed block's heading");
        }
        for (String free : List.of("Overview of flags", "Usages", "Known limitation", "Examples")) {
            assertThatCode(() -> new GuideSection(free, "x")).as(free).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("a freeform title is one line, not blank, without surrounding whitespace")
    void aFreeformTitleIsOneTrimmedLine() {
        assertThatCode(() -> new GuideSection("Exit codes", "")).doesNotThrowAnyException();
        for (String bad : List.of("", " ", " Exit codes", "Exit codes ", "Exit\ncodes")) {
            assertThatThrownBy(() -> new GuideSection(bad, ""))
                    .as("'%s'", bad)
                    .isInstanceOf(ConstraintBreachException.class)
                    .hasMessageContaining("one line, not blank");
        }
    }
}
