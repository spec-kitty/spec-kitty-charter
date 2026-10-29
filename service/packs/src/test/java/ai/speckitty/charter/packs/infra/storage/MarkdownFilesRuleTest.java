package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownDocument;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * FR-005 (#194): a {@code files} rule may declare {@code content_form: markdown}. With a copy of the bundled table plus
 * one test rule, a fixture file is classified as {@code markdown} with its structure schema, and the path a Markdown
 * kind takes holds end to end: parse, validate against the structure schema, write canonically, compare by model. No
 * store consumer exists yet (#195 adds the first), so this proves the classification, schema and comparison path.
 */
class MarkdownFilesRuleTest {

    private static final String FIXTURES = "markdown-rule-fixtures/";
    private static final String RULE = "  - match: \"*/notes/*.md\"\n    schema: notes\n    content_form: markdown\n";

    /** The bundled table with the test rule first among the {@code files} rules, its schemas under the fixtures. */
    private static final PackFileKind TABLE = new PackFileKind(withTestRule(resource(PackFileKind.RESOURCE)), FIXTURES);

    private static final PackSchemas SCHEMAS = new PackSchemas(FIXTURES, List.of("notes"));
    private static final MeaningComparator COMPARATOR = MeaningComparator.adr0036();

    @Test
    void theTestRuleClassifiesAFixtureFileAsMarkdownWithItsStructureSchema() {
        assertThat(TABLE.classify("built-in/notes/valid.md"))
                .isEqualTo(new PackFileKind.Mapped(
                        "notes", Optional.of(FIXTURES + "notes.schema.json"), ContentForm.MARKDOWN));
        assertThat(TABLE.matchingRules("built-in/notes/valid.md")).containsExactly("*/notes/*.md");
        assertThat(TABLE.fileRules())
                .as("the copy keeps every bundled rule and adds one")
                .hasSize(PackFileKind.bundled().fileRules().size() + 1);
        assertThat(PackFileKind.bundled().classify("built-in/notes/valid.md"))
                .as("the bundled table has no markdown rule at the pin")
                .isEqualTo(PackFileKind.Unclassified.INSTANCE);
    }

    @Test
    void aValidFileParsesValidatesWritesBackByteForByteAndComparesEqual() {
        String source = fixture("valid.md");
        MarkdownDocument document = parsed(source);

        assertThat(SCHEMAS.validateMarkdown("notes", "built-in/notes/valid.md", document))
                .isEmpty();
        String written = MarkdownCodec.write(document);
        assertThat(written).as("the fixture is canonical").isEqualTo(source);
        assertThat(COMPARATOR.compareMarkdown(source, written)).isEmpty();
        assertThat(document.sections())
                .as("an H3 and a fenced H2 stay inside their section")
                .extracting(MarkdownDocument.Section::title)
                .containsExactly("Summary", "Detail");
    }

    @Test
    void oneChangedCharacterInASectionIsReportedAtThatSectionsContent() {
        List<MeaningComparator.Difference> differences =
                COMPARATOR.compareMarkdown(fixture("valid.md"), fixture("changed.md"));

        assertThat(differences).extracting(MeaningComparator.Difference::path).containsExactly("sections[1].content");
        assertThat(differences.get(0).source()).endsWith("Done.");
        assertThat(differences.get(0).written()).endsWith("Done!");
        assertThat(SCHEMAS.validateMarkdown("notes", "built-in/notes/changed.md", parsed(fixture("changed.md"))))
                .as("the change is a difference of meaning, not of structure")
                .isEmpty();
    }

    @Test
    void aFileMissingTheRequiredSectionIsRefusedByTheStructureSchema() {
        List<SchemaViolation> violations = SCHEMAS.validateMarkdown(
                "notes", "built-in/notes/missing-summary.md", parsed(fixture("missing-summary.md")));

        assertThat(violations)
                .extracting(SchemaViolation::file, SchemaViolation::pointer, SchemaViolation::keyword)
                .contains(tuple("built-in/notes/missing-summary.md", List.of("sections"), "minContains"));
    }

    private static MarkdownDocument parsed(String text) {
        MarkdownCodec.Outcome outcome = MarkdownCodec.parse(text);
        assertThat(outcome).isInstanceOf(MarkdownCodec.Parsed.class);
        return ((MarkdownCodec.Parsed) outcome).document();
    }

    private static String fixture(String name) {
        return resource(FIXTURES + "built-in/notes/" + name);
    }

    private static String withTestRule(String table) {
        String files = "\nfiles:\n";
        assertThat(table).contains(files);
        return table.replace(files, files + RULE);
    }

    private static String resource(String name) {
        try (InputStream in = MarkdownFilesRuleTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
