package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.infra.storage.MeaningComparator.Difference;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Every mission step contract of the pinned open-packs checkout round-trips by meaning (ADR-0036): read, mapped to the
 * domain and back, written, and compared with its source text. It covers the {@code qa} pack too, which no pack-level
 * gate loads because it has no {@code pack.yaml}.
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout and never
 * skips.
 */
@Tag("open-packs")
@DisplayName("Every pinned mission step contract round-trips by meaning (#191)")
class MissionStepContractCorpusTest {

    private static final String KIND = "mission-step-contract";
    private static final PackIdentifier PROBE = new PackIdentifier("probe");

    private final SerializationStrategy yaml = new YamlSerializationStrategy();

    @Test
    @DisplayName("every step contract in the checkout reads, writes and read back with their meaning unchanged")
    void everyPinnedContractRoundTrips() throws IOException {
        List<Path> files = contracts();
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String written = roundTrip(file, source, UnaryOperator.identity());
            if (!(yaml.read(MissionStepContractBinding.INSTANCE, written, file.toString())
                    instanceof FileRead.Read<?>)) {
                problems.add(file + ": the written form no longer reads");
            }
            MeaningComparator.adr0036()
                    .compareTexts(KIND, source, written)
                    .ifPresent(difference -> problems.add(file + ": " + difference));
        }

        assertThat(problems).as("step contracts that do not round-trip").isEmpty();
        assertThat(files).as("step contract files in the checkout").isNotEmpty();
        OpenPacksCorpus.assertAllPacksPresent();
        OpenPacksCorpus.assertEachPackHolds("step contract", files, "built-in", "qa");
    }

    @Test
    @DisplayName("the corpus exercises gates, guidance, commands and delegations, so the round trip is not vacuous")
    void theCorpusCoversTheSchema() throws IOException {
        int gates = 0;
        int guidance = 0;
        int commands = 0;
        int delegations = 0;
        for (Path file : contracts()) {
            MissionStepContractDso dso = read(file, Files.readString(file, StandardCharsets.UTF_8));
            gates += dso.gates().size();
            for (MissionStepContractDso.Step step : dso.steps()) {
                guidance += step.guidance().isPresent() ? 1 : 0;
                commands += step.command().isPresent() ? 1 : 0;
                delegations += step.delegatesTo().isPresent() ? 1 : 0;
            }
        }

        assertThat(gates).as("gate bindings").isPositive();
        assertThat(guidance).as("steps with guidance").isPositive();
        assertThat(commands).as("steps with a command").isPositive();
        assertThat(delegations).as("steps with a delegation").isPositive();
    }

    /**
     * Positive control: a writer that drops {@code fail_open} and every {@code guidance} must turn the corpus red at
     * exactly those fields. If this passes, the comparison above can see a lossy writer.
     */
    @Test
    @DisplayName("a writer that drops fail_open and guidance is caught at those fields")
    void aLossyWriterIsCaught() throws IOException {
        List<Difference> differences = new ArrayList<>();
        List<String> failOpenFiles = new ArrayList<>();
        List<String> declaringFailOpen = new ArrayList<>();
        int guidance = 0;
        for (Path file : contracts()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (source.contains("fail_open:")) {
                declaringFailOpen.add(String.valueOf(file.getFileName()));
            }
            guidance += (int) read(file, source).steps().stream()
                    .filter(step -> step.guidance().isPresent())
                    .count();
            String lossy = roundTrip(file, source, MissionStepContractCorpusTest::dropFailOpenAndGuidance);
            List<Difference> found = MeaningComparator.adr0036().differencesOfTexts(KIND, source, lossy);
            if (found.stream().anyMatch(d -> d.path().equals("gates[0].fail_open"))) {
                failOpenFiles.add(String.valueOf(file.getFileName()));
            }
            differences.addAll(found);
        }

        assertThat(differences)
                .extracting(Difference::path)
                .contains("gates[0].fail_open")
                .anySatisfy(path -> assertThat(path).matches("steps\\[\\d+]\\.guidance"))
                .allSatisfy(path -> assertThat(path).matches("gates\\[0]\\.fail_open|steps\\[\\d+]\\.guidance"));
        assertThat(differences.stream().filter(d -> d.path().endsWith(".guidance")))
                .as("one difference per step that carries guidance")
                .hasSize(guidance);
        assertThat(failOpenFiles)
                .as("the files whose gate writes fail_open")
                .isNotEmpty()
                .containsExactlyElementsOf(declaringFailOpen);
    }

    private String roundTrip(Path file, String source, UnaryOperator<MissionStepContractDso> writer) {
        MissionStepContractBinding binding = MissionStepContractBinding.INSTANCE;
        MissionStepContractDso back = binding.toDso(binding.toEntity(PROBE, read(file, source)));
        return yaml.write(binding, writer.apply(back));
    }

    private MissionStepContractDso read(Path file, String text) {
        FileRead<MissionStepContractDso> read = yaml.read(MissionStepContractBinding.INSTANCE, text, file.toString());
        assertThat(read).as("%s reads", file).isInstanceOf(FileRead.Read.class);
        return ((FileRead.Read<MissionStepContractDso>) read).value();
    }

    private static MissionStepContractDso dropFailOpenAndGuidance(MissionStepContractDso dso) {
        return new MissionStepContractDso(
                dso.id(),
                dso.schemaVersion(),
                dso.action(),
                dso.mission(),
                dso.steps().stream()
                        .map(step -> new MissionStepContractDso.Step(
                                step.id(),
                                step.description(),
                                step.command(),
                                step.inputs(),
                                step.delegatesTo(),
                                Optional.empty()))
                        .toList(),
                dso.gates().stream()
                        .map(gate -> new MissionStepContractDso.Gate(
                                gate.onTransition(),
                                gate.handler(),
                                gate.handlerKind(),
                                gate.schemaVersion(),
                                Optional.empty(),
                                gate.provenance()))
                        .toList());
    }

    private static List<Path> contracts() throws IOException {
        try (Stream<Path> walk = Files.walk(OpenPacksCorpus.packsDirectory())) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".step-contract.yaml"))
                    .sorted()
                    .toList();
        }
    }
}
