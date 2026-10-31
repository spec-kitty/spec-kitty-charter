package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.GuideBlock;
import ai.speckitty.charter.packs.domain.GuideSection;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
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
 * another letter case; a leading block after a freeform section; a freeform section after a trailing block.
 *
 * <p>The H1 is derived data (ADR-0036 S8, ADR-0043): it is written from the toolguide's {@code title}, which is its one
 * home. Incoming content ({@link #fromDocument}, FR-011) may leave it out; a stored guide ({@link #fromStored}) must
 * carry it, so a stored guide has one spelling: the one the writer produces.
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
     * The Markdown a guide is stored and answered as: {@link #toDocument} in the canonical form.
     *
     * @param title the toolguide's title, written as the H1
     * @param guide the guide
     * @return the Markdown text
     * @throws IllegalArgumentException when the title cannot be an H1: blank, more than one line, or a text the
     *     Markdown reader would not read back as itself (one that ends in a closing {@code #} sequence)
     */
    public static String write(String title, Guide guide) {
        MarkdownDocument document = toDocument(title, guide);
        try {
            return MarkdownCodec.write(document);
        } catch (IllegalStateException unreadable) {
            throw new IllegalArgumentException(
                    "the title '" + title + "' cannot be the guide's H1: Markdown would not read it back as written",
                    unreadable);
        }
    }

    /**
     * The guide a stored document holds: as {@link #fromDocument}, and the document must carry the H1, which the
     * writer always adds.
     *
     * @param title the toolguide's title, which the H1 must equal
     * @param document the parsed document
     * @return the guide
     * @throws IllegalArgumentException naming the first rule the document breaks
     */
    public static Guide fromStored(String title, MarkdownDocument document) {
        if (document.title().isEmpty()) {
            throw refused("has no H1; a stored guide carries its toolguide's title '" + title + "' as its H1");
        }
        return fromDocument(title, document);
    }

    /**
     * The structure rules of a stored guide, judged without its toolguide (validate mode reads the guide file on its
     * own): {@link #fromStored} with the guide's own H1 as the title, so every rule but the H1's equality is applied.
     *
     * @param document the parsed document
     * @throws IllegalArgumentException naming the first rule the document breaks
     */
    static void checkStored(MarkdownDocument document) {
        fromStored(document.title().orElse(""), document);
    }

    /**
     * The guide an incoming document holds (FR-011): the H1 may be absent.
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
            sections.add(section(heading, section.content()));
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

    /** A freeform section, a breach of its rules refused like any other rule of the guide. */
    private static GuideSection section(String title, String content) {
        try {
            return new GuideSection(title, content);
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
