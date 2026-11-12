package ai.speckitty.charter.packs.infra.storage.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.PackFiles;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec.Parsed;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every Markdown file of the example packs that the {@code markdown} form covers reads and writes back
 * byte for byte (SC-006): the toolguide guides ({@code packs/*}{@code /toolguides/**}), each pack's {@code README.md},
 * and the mission step prompts and guidelines ({@code packs/*}{@code /missions/*}{@code /steps/*.md}, #194).
 *
 * <p>Reads the charter-owned example packs ({@code ExamplePacks}, ADR-0070) and runs under {@code :packs:test}.
 */
@DisplayName("Every example Markdown file reads and writes back byte for byte (SC-006)")
class MarkdownCorpusTest {

    private static final List<PathMatcher> COVERED = Stream.of(
                    "glob:*/toolguides/**.md", "glob:*/README.md", "glob:*/missions/*/steps/*.md")
            .map(FileSystems.getDefault()::getPathMatcher)
            .toList();

    @Test
    @DisplayName("every covered Markdown file parses, and write(parse(bytes)) equals the bytes")
    void everyCoveredFileIsCanonical() throws IOException {
        List<Path> files = coveredFiles();
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            check(file)
                    .ifPresent(problem ->
                            problems.add(ExamplePacks.packsDirectory().relativize(file) + ": " + problem));
        }

        assertThat(problems)
                .as("example Markdown files refused or not byte-canonical")
                .isEmpty();
        assertThat(files).as("covered Markdown files in the example packs").isNotEmpty();
        PackFiles.assertEachPackHolds("covered Markdown file", files, ExamplePacks.BUILT_IN, ExamplePacks.ORG);
    }

    @Test
    @DisplayName("the corpus exercises frontmatter, titles, sections and fences, so the check is not vacuous")
    void corpusIsNotVacuous() throws IOException {
        List<Path> files = coveredFiles();
        List<MarkdownDocument> documents = new ArrayList<>();
        for (Path file : files) {
            if (MarkdownCodec.parse(read(file)) instanceof Parsed parsed) {
                documents.add(parsed.document());
            }
        }

        assertThat(documents).as("every covered file parses to a document").hasSameSizeAs(files);
        assertThat(documents).as("documents that parse").isNotEmpty();
        assertThat(documents.stream().filter(d -> d.frontmatter().isPresent()).count())
                .as("documents with frontmatter")
                .isPositive();
        // The example READMEs each carry a provenance section (#451), so no example is a title with no sections; the
        // codec's byte-for-byte path for that shape is held by this literal.
        assertThat(documents.stream().filter(d -> d.title().isPresent()).count())
                .as("documents with a title (README.md)")
                .isPositive();
        String titleOnly = "# A title only\n";
        assertThat(MarkdownCodec.parse(titleOnly))
                .isInstanceOfSatisfying(
                        Parsed.class,
                        parsed -> assertThat(MarkdownCodec.write(parsed.document()))
                                .as("a document with a title and no sections writes back byte for byte")
                                .isEqualTo(titleOnly));
        assertThat(documents.stream()
                        .filter(MarkdownCorpusTest::hidesHeadingInFence)
                        .count())
                .as("documents whose blocks hold a column-0 H1 or H2 line, which the model admits only inside a fence")
                .isPositive();
        // No empty file is left in the examples: the two empty retrospect prompts were not carried by the mission-type
        // migration (#194); MarkdownCodecTest covers the empty document.
    }

    private static boolean hidesHeadingInFence(MarkdownDocument document) {
        return Stream.concat(
                        Stream.of(document.preamble()),
                        document.sections().stream().map(MarkdownDocument.Section::content))
                .flatMap(block -> Arrays.stream(block.split("\n", -1)))
                .anyMatch(line -> line.startsWith("# ") || line.startsWith("## "));
    }

    private static Optional<String> check(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String text;
        try {
            text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return Optional.of("not valid UTF-8");
        }
        MarkdownCodec.Outcome outcome = MarkdownCodec.parse(text);
        if (outcome instanceof MarkdownRefusal refusal) {
            return Optional.of("refused at " + refusal.message());
        }
        String written;
        try {
            written = MarkdownCodec.write(((Parsed) outcome).document());
        } catch (IllegalStateException e) {
            return Optional.of("does not write back: " + e.getMessage());
        }
        if (!Arrays.equals(written.getBytes(StandardCharsets.UTF_8), bytes)) {
            return Optional.of("not byte-canonical; first difference at line " + firstDifferingLine(text, written));
        }
        return Optional.empty();
    }

    private static int firstDifferingLine(String expected, String actual) {
        String[] left = expected.split("\n", -1);
        String[] right = actual.split("\n", -1);
        int i = 0;
        while (i < left.length && i < right.length && left[i].equals(right[i])) {
            i++;
        }
        return i + 1;
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static List<Path> coveredFiles() throws IOException {
        Path root = ExamplePacks.packsDirectory();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        Path relative = root.relativize(p);
                        return COVERED.stream().anyMatch(m -> m.matches(relative));
                    })
                    .sorted()
                    .toList();
        }
    }
}
