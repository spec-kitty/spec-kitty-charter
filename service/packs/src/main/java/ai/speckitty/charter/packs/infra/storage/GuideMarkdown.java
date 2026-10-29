package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.GuideBlock;
import ai.speckitty.charter.packs.domain.GuideSection;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownDocument;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A toolguide's {@link Guide} as a {@link MarkdownDocument} and back (#194, data-model "Guide", research D5 and D11).
 *
 * <p>Written: no frontmatter, the H1 from the toolguide's title, an empty preamble, then the H2 sections in write
 * order: the {@linkplain GuideBlock#leading() leading} fixed blocks, the freeform sections in authored order, the
 * trailing fixed blocks. Each fixed block's heading is its canonical text.
 *
 * <p>Read: the same layout, strictly. Refused: frontmatter; a preamble (its text belongs in {@code ## Overview}); an H1
 * that differs from the toolguide's title; a missing overview; a fixed block out of order or twice; a fixed heading in
 * another letter case; a leading block after a freeform section; a freeform section after a trailing block. An absent
 * H1 is accepted (incoming content, FR-011), so the title has one home; the writer always adds it.
 */
public final class GuideMarkdown {

    private GuideMarkdown() {}

    /**
     * The document a guide is stored as.
     *
     * @param title the toolguide's title, written as the H1
     * @param guide the guide
     * @return the document, in write order
     * @throws IllegalArgumentException when the title cannot be an H1 (blank, or more than one line)
     */
    public static MarkdownDocument toDocument(String title, Guide guide) {
        List<MarkdownDocument.Section> sections = new ArrayList<>();
        guide.blocks().forEach((block, content) -> {
            if (block.leading()) {
                sections.add(new MarkdownDocument.Section(block.heading(), content));
            }
        });
        guide.sections()
                .forEach(section -> sections.add(new MarkdownDocument.Section(section.title(), section.content())));
        guide.blocks().forEach((block, content) -> {
            if (!block.leading()) {
                sections.add(new MarkdownDocument.Section(block.heading(), content));
            }
        });
        return new MarkdownDocument(Optional.empty(), Optional.of(title), "", sections);
    }

    /**
     * The guide a stored or incoming document holds.
     *
     * @param title the toolguide's title, which a present H1 must equal
     * @param document the parsed document
     * @return the guide
     * @throws IllegalArgumentException naming the first rule the document breaks
     */
    public static Guide fromDocument(String title, MarkdownDocument document) {
        if (document.frontmatter().isPresent()) {
            throw refused("has frontmatter; a guide has none, its fields live in the toolguide");
        }
        document.title().filter(h1 -> !h1.equals(title)).ifPresent(h1 -> {
            throw refused("has the H1 '" + h1 + "', which differs from the toolguide title '" + title + "'");
        });
        if (!document.preamble().isEmpty()) {
            throw refused("has text before its first section; it belongs in ## " + GuideBlock.OVERVIEW.heading());
        }
        Map<GuideBlock, String> blocks = new EnumMap<>(GuideBlock.class);
        List<GuideSection> sections = new ArrayList<>();
        Optional<GuideBlock> last = Optional.empty();
        for (MarkdownDocument.Section section : document.sections()) {
            String heading = section.title();
            Optional<GuideBlock> fixed = GuideBlock.byHeading(heading);
            if (fixed.isPresent()) {
                GuideBlock block = fixed.get();
                if (blocks.containsKey(block)) {
                    throw refused("has ## " + heading + " twice");
                }
                if (last.filter(previous -> previous.compareTo(block) > 0).isPresent()) {
                    throw refused("has ## " + heading + " after ## "
                            + last.get().heading() + "; the fixed blocks are in the order " + order());
                }
                if (block.leading() && !sections.isEmpty()) {
                    throw refused("has ## " + heading + " after the freeform section '"
                            + sections.getLast().title() + "'; it comes before the freeform sections");
                }
                blocks.put(block, section.content());
                last = fixed;
                continue;
            }
            GuideBlock.reserving(heading).ifPresent(reserved -> {
                throw refused("has ## " + heading + "; the fixed heading is written ## " + reserved.heading());
            });
            if (last.filter(previous -> !previous.leading()).isPresent()) {
                throw refused("has the freeform section '" + heading + "' after ## "
                        + last.get().heading() + "; freeform sections come before ## " + GuideBlock.CAUTIONS.heading());
            }
            sections.add(new GuideSection(heading, section.content()));
        }
        if (!blocks.containsKey(GuideBlock.OVERVIEW)) {
            throw refused("has no ## " + GuideBlock.OVERVIEW.heading());
        }
        try {
            return new Guide(blocks, sections);
        } catch (ConstraintBreachException breach) {
            throw refused("breaks a guide rule: " + breach.getMessage());
        }
    }

    private static String order() {
        List<String> headings = new ArrayList<>();
        for (GuideBlock block : GuideBlock.values()) {
            headings.add(block.heading());
        }
        return String.join(", ", headings);
    }

    private static IllegalArgumentException refused(String why) {
        return new IllegalArgumentException("the guide " + why);
    }
}
