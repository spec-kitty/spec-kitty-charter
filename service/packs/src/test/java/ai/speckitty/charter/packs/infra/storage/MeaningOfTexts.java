package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.roundtrip.Reading;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownRefusal;
import ai.speckitty.charter.packvalidation.roundtrip.MeaningComparator;
import ai.speckitty.charter.packvalidation.roundtrip.MeaningComparator.Difference;
import java.util.List;
import java.util.Optional;

/**
 * {@code pack-validation}'s ADR-0036 oracle over texts, for the per-kind tests of {@code :packs}: each text is parsed
 * with the pack readers' own settings ({@link Yaml}, {@link MarkdownCodec}), as the production flow parses it
 * ({@link PackRoundTrip}), then compared by {@link MeaningComparator}, which never reads a file.
 */
final class MeaningOfTexts {

    private final MeaningComparator comparator = MeaningComparator.adr0036();

    private MeaningOfTexts() {}

    /** The rules of ADR-0036, over texts. */
    static MeaningOfTexts adr0036() {
        return new MeaningOfTexts();
    }

    /** The first difference between two YAML texts, or empty when both mean the same. */
    Optional<Difference> compareTexts(String kind, String sourceText, String writtenText) {
        return comparator.compare(kind, Yaml.load(sourceText), Yaml.load(writtenText));
    }

    /** Every difference between two YAML texts. */
    List<Difference> differencesOfTexts(String kind, String sourceText, String writtenText) {
        return comparator.differences(kind, Yaml.load(sourceText), Yaml.load(writtenText));
    }

    /** Every difference between two Markdown texts, by their models. */
    List<Difference> compareMarkdown(String sourceText, String writtenText) {
        return comparator.compareMarkdown(markdown(sourceText), markdown(writtenText));
    }

    /** A graph shard's duplicate edges, from its text. */
    List<String> duplicateEdges(String kind, String text) {
        return comparator.duplicateEdges(kind, Yaml.load(text));
    }

    private static Reading markdown(String text) {
        return switch (MarkdownCodec.parse(text)) {
            case MarkdownCodec.Parsed parsed -> new Reading.Markdown(parsed.document());
            case MarkdownRefusal refusal -> new Reading.MarkdownRefused(refusal.message());
        };
    }
}
