package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of a toolguide: a flat mirror of the fields of a {@code *.toolguide.yaml}, not the domain
 * entity, plus its guide (#194): the Markdown companion its {@code guide_path} names, held as the parsed model. Its
 * record equality is the round-trip fidelity assertion; the guide compares by its Markdown model.
 *
 * @param id the toolguide's {@code id}
 * @param schemaVersion the {@code schema_version}
 * @param tool the {@code tool}
 * @param title the {@code title}
 * @param guidePath the {@code guide_path}
 * @param summary the {@code summary}
 * @param commands the {@code commands}, in authored order
 * @param appliesToLanguages the {@code applies_to_languages}, in authored order
 * @param lastUpdated the {@code last_updated}, when stated
 * @param references the {@code references}, in authored order with duplicates kept
 * @param guide the guide file {@code guide_path} names, once the store has read it
 */
record ToolguideDso(
        String id,
        String schemaVersion,
        String tool,
        String title,
        String guidePath,
        String summary,
        List<String> commands,
        List<String> appliesToLanguages,
        Optional<String> lastUpdated,
        List<String> references,
        Optional<MarkdownDocument> guide) {

    /**
     * Checks that the required fields are present and copies the lists.
     *
     * @param id the id
     * @param schemaVersion the schema version
     * @param tool the tool
     * @param title the title
     * @param guidePath the guide path
     * @param summary the summary
     * @param commands the commands
     * @param appliesToLanguages the applies-to languages
     * @param lastUpdated the last-updated text
     * @param references the references
     * @param guide the guide, possibly empty
     */
    ToolguideDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(guidePath, "guidePath");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(lastUpdated, "lastUpdated");
        Objects.requireNonNull(guide, "guide");
        commands = List.copyOf(commands);
        appliesToLanguages = List.copyOf(appliesToLanguages);
        references = List.copyOf(references);
    }

    /**
     * The same shape holding a guide.
     *
     * @param document the guide, possibly empty
     * @return a copy with {@code guide} replaced
     */
    ToolguideDso withGuide(Optional<MarkdownDocument> document) {
        return new ToolguideDso(
                id,
                schemaVersion,
                tool,
                title,
                guidePath,
                summary,
                commands,
                appliesToLanguages,
                lastUpdated,
                references,
                document);
    }
}
