package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.groups.Tuple.tuple;

import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * FR-005 (#194): a {@code files} rule may declare {@code content_form: markdown}. With a copy of the bundled table plus
 * one test rule, a fixture file is classified as {@code markdown} with its structure schema, and the path a Markdown
 * kind takes holds end to end: parse, validate against the structure schema, write canonically, compare by model. The
 * store reads only the pack document ({@code pack.md}, #260) and the companions, so this proves the classification,
 * schema and comparison path, and that every production path that dispatches on a rule's content form refuses any
 * other {@code markdown} file loudly instead of reading it as YAML or skipping it.
 */
class MarkdownFilesRuleTest {

    private static final String FIXTURES = "markdown-rule-fixtures/";
    private static final String RULE = "  - match: \"*/notes/*.md\"\n    schema: notes\n    content_form: markdown\n";

    /** The bundled table with the test rule first among the {@code files} rules, its schemas under the fixtures. */
    private static final PackFileKind TABLE = new PackFileKind(withTestRule(resource(PackFileKind.RESOURCE)), FIXTURES);

    private static final PackSchemas SCHEMAS = new PackSchemas(FIXTURES, List.of("notes"));
    private static final String DIRECTIVE = "directives/x.directive.yaml";
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

    @Test
    void listingAPackRefusesAMarkdownFileLoudlyWhileNoStorePathReadsIt(@TempDir Path host) throws IOException {
        Path pack = Files.createDirectories(host.resolve("built-in"));
        Files.createDirectories(pack.resolve("notes"));
        Files.writeString(pack.resolve("notes/valid.md"), fixture("valid.md"));

        assertThatIllegalStateException()
                .isThrownBy(() -> PackFileSet.listing(pack, TABLE))
                .withMessage("'notes/valid.md' is classified as content_form markdown: Markdown is read only as a"
                        + " companion or the pack document; this rule has no store path of its own yet");
        assertThat(PackFileSet.listing(pack, PackFileKind.bundled()).byKind())
                .as("the bundled table leaves the file unclassified, so the same pack lists")
                .isEmpty();
    }

    @Test
    void validateModeRefusesAMarkdownFileInsteadOfReadingItAsYaml() {
        PackFileKind.Mapped markdown = (PackFileKind.Mapped) TABLE.classify("built-in/notes/valid.md");
        PackFileKind.Mapped yaml = (PackFileKind.Mapped) TABLE.classify("built-in/directives/x.directive.yaml");

        assertThatIllegalStateException()
                .isThrownBy(() -> FileSystemPackInspector.readsAsYaml(markdown, "notes/valid.md"))
                .withMessageContaining("this rule has no store path of its own yet");
        assertThat(FileSystemPackInspector.readsAsYaml(yaml, "directives/x.directive.yaml"))
                .isTrue();
    }

    @Test
    void aCompanionPathOntoAMarkdownFileIsRefusedAsOwnedByItsRule(@TempDir Path host) {
        Path pack = host.resolve("built-in").toAbsolutePath().normalize();

        KindBinding.Companion text =
                new KindBinding.Companion("../notes/valid.md", Optional.empty(), ContentForm.TEXT, Optional.empty());

        assertThat(Companions.resolve(new Companions.Rules(TABLE, () -> SCHEMAS), pack, DIRECTIVE, text))
                .isEqualTo(new Companions.Refused(
                        "is a pack file of kind notes (file-kinds rule '*/notes/*.md'), not a companion"));
        assertThat(Companions.resolve(Companions.Rules.bundled(), pack, DIRECTIVE, text))
                .as("unclassified under the bundled table, the same path is a companion")
                .isInstanceOf(Companions.Inside.class);
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
