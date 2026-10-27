package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * SC-001 / NFR-001 over the real corpus: every directive, styleguide and paradigm file of the open-packs checkout is
 * read into its DSO, written back and read again, and the two DSOs must be equal. DSO equality is field equality (the
 * DSOs are records), so a field the reader or the writer drops, coerces or reorders fails the run; "no exception" is
 * not the bar. The corpus does contain all three kinds (no fallback fixtures are needed), and each kind's file count
 * must be positive, so an empty or moved corpus cannot pass vacuously. A file the reader refuses is collected and
 * fails the run rather than being skipped.
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout. The pin
 * itself is asserted by {@code VendoredPacksTest}; fidelity does not depend on the exact commit.
 */
@Tag("open-packs")
class PackDoctrineCorpusRoundTripTest {

    private final SerializationStrategy yaml = new YamlSerializationStrategy();

    private static List<Path> filesOfKind(String suffix) throws IOException {
        try (Stream<Path> walk = Files.walk(OpenPacksCorpus.packsDirectory())) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        }
    }

    /** Returns how many files round-tripped; fails with every offender listed. */
    private <D> int roundTripEveryFile(
            String suffix, BiFunction<String, String, FileRead<D>> read, Function<D, String> write) throws IOException {
        List<Path> files = filesOfKind(suffix);
        List<String> problems = new ArrayList<>();
        for (Path path : files) {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            String name = path.getFileName().toString();
            if (!(read.apply(text, name) instanceof FileRead.Read<D> first)) {
                problems.add("unreadable: " + path);
                continue;
            }
            String written = write.apply(first.value());
            if (!(read.apply(written, name) instanceof FileRead.Read<D> second)) {
                problems.add("the written form no longer reads: " + path);
                continue;
            }
            if (!second.value().equals(first.value())) {
                problems.add(
                        "field drift: " + path + "\n  read     " + first.value() + "\n  re-read  " + second.value());
            }
        }
        assertThat(problems).as("corpus files of kind " + suffix).isEmpty();
        return files.size();
    }

    @Test
    void everyCorpusDirectiveReadsBackEqualAfterAWrite() throws IOException {
        int count = roundTripEveryFile(
                ".directive.yaml",
                (text, file) -> yaml.read(DirectiveBinding.INSTANCE, text, file),
                dso -> yaml.write(DirectiveBinding.INSTANCE, dso));

        assertThat(count).as("directives in the corpus").isPositive();
    }

    @Test
    void everyCorpusStyleguideReadsBackEqualAfterAWrite() throws IOException {
        int count = roundTripEveryFile(
                ".styleguide.yaml",
                (text, file) -> yaml.read(StyleguideBinding.INSTANCE, text, file),
                dso -> yaml.write(StyleguideBinding.INSTANCE, dso));

        assertThat(count).as("styleguides in the corpus").isPositive();
    }

    @Test
    void everyCorpusParadigmReadsBackEqualAfterAWrite() throws IOException {
        int count = roundTripEveryFile(
                ".paradigm.yaml",
                (text, file) -> yaml.read(ParadigmBinding.INSTANCE, text, file),
                dso -> yaml.write(ParadigmBinding.INSTANCE, dso));

        assertThat(count).as("paradigms in the corpus").isPositive();
    }

    @Test
    void everyCorpusAgentProfileReadsBackEqualAfterAWrite() throws IOException {
        int count = roundTripEveryFile(
                ".agent.yaml",
                (text, file) -> yaml.read(AgentProfileBinding.INSTANCE, text, file),
                dso -> yaml.write(AgentProfileBinding.INSTANCE, dso));

        assertThat(count).as("agent profiles in the corpus").isPositive();
    }

    /** ADR-0036: the written profile is compared with its source, not with a second read, so a dropped field shows. */
    @Test
    void everyCorpusAgentProfileMeansWhatItsSourceMeant() throws IOException {
        List<Path> files = filesOfKind(".agent.yaml");
        List<String> problems = new ArrayList<>();
        for (Path path : files) {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            if (yaml.read(AgentProfileBinding.INSTANCE, text, path.getFileName().toString())
                    instanceof FileRead.Read<AgentProfileDso> read) {
                String written = yaml.write(AgentProfileBinding.INSTANCE, read.value());
                MeaningComparator.adr0036()
                        .compareTexts("agent-profile", text, written)
                        .ifPresent(difference -> problems.add(path + ": " + difference));
            } else {
                problems.add("unreadable: " + path);
            }
        }
        assertThat(problems)
                .as("agent profiles whose written form differs from the source")
                .isEmpty();
        assertThat(files).as("agent profiles in the corpus").hasSizeGreaterThan(30);
    }
}
