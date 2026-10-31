package ai.speckitty.charter.packs.infra.storage.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import ai.speckitty.charter.packs.infra.storage.OpenPacksCorpus;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Every Markdown file of the pinned open-packs checkout that the {@code markdown} form covers reads and writes back
 * byte for byte (SC-006): the toolguide guides ({@code packs/*}{@code /toolguides/**}), each pack's {@code pack.md},
 * and the mission step prompts and guidelines ({@code packs/*}{@code /missions/*}{@code /steps/*.md}, #194).
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout and never
 * skips. A refused or non-canonical pinned file is a finding for open-packs, never a reason to loosen a rule.
 */
@Tag("open-packs")
@DisplayName("Every pinned Markdown file reads and writes back byte for byte (SC-006)")
class MarkdownCorpusTest {

    /** The covered Markdown files at the pin: 24 guides, pack.md and 60 mission step prompts and guidelines (#194). */
    private static final int PINNED_COUNT = 85;

    private static final List<PathMatcher> COVERED = Stream.of(
                    "glob:packs/*/toolguides/**.md", "glob:packs/*/pack.md", "glob:packs/*/missions/*/steps/*.md")
            .map(FileSystems.getDefault()::getPathMatcher)
            .toList();

    @BeforeAll
    static void pinned() {
        OpenPacksCorpus.assertPinned();
    }

    @Test
    @DisplayName("the 85 covered Markdown files parse, and write(parse(bytes)) equals the bytes")
    void everyCoveredFileIsCanonical() throws IOException {
        List<Path> files = coveredFiles();
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            check(file).ifPresent(problem -> problems.add(OpenPacksCorpus.root().relativize(file) + ": " + problem));
        }

        assertThat(problems)
                .as("pinned Markdown files refused or not byte-canonical")
                .isEmpty();
        assertThat(files).as("covered Markdown files at the pin").hasSize(PINNED_COUNT);
    }

    @Test
    @DisplayName("the corpus exercises frontmatter, titles, sections and fences, so the check is not vacuous")
    void corpusIsNotVacuous() throws IOException {
        List<MarkdownDocument> documents = new ArrayList<>();
        for (Path file : coveredFiles()) {
            if (MarkdownCodec.parse(read(file)) instanceof Parsed parsed) {
                documents.add(parsed.document());
            }
        }

        assertThat(documents).as("documents that parse").hasSize(PINNED_COUNT);
        assertThat(documents.stream().filter(d -> d.frontmatter().isPresent()).count())
                .as("documents with frontmatter")
                .isPositive();
        assertThat(documents.stream()
                        .filter(d -> d.title().isPresent() && d.sections().isEmpty())
                        .count())
                .as("documents with a title and no sections (pack.md)")
                .isPositive();
        assertThat(documents.stream()
                        .filter(MarkdownCorpusTest::hidesHeadingInFence)
                        .count())
                .as("documents whose blocks hold a column-0 H1 or H2 line, which the model admits only inside a fence")
                .isPositive();
        // No empty file is left at the pin: the two empty retrospect prompts were not carried by the mission-type
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
        Path root = OpenPacksCorpus.root();
        try (Stream<Path> walk = Files.walk(root.resolve("packs"))) {
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
