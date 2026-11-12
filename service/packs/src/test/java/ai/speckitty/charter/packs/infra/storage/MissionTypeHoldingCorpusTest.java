package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.MissionTypeHoldingRead;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.domain.MissionStepContract;
import ai.speckitty.charter.packs.domain.PackCompanionFiles;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The mission type read serves the example {@code software-dev} as stored (#99, T016, SC-001): the composite
 * read over the production store holds every step the example's type declares (in sequence, then on demand), and each
 * step's prompt and guidelines are the files of the example pack byte for byte, the bare {@code spdd} slot markers
 * included. It holds the skeletons the type's {@code templates/} directory holds and the step contracts bound to it.
 * The expectations derive from the example pack, so a corpus change at a new pin needs no edit here.
 *
 * <p>Reads the charter-owned example packs ({@code ExamplePacks}, ADR-0070) and runs under {@code :packs:test}.
 */
@DisplayName("The example software-dev is served byte for byte (#99)")
class MissionTypeHoldingCorpusTest {

    private static final PackIdentifier BUILT_IN = new PackIdentifier("built-in");
    private static final String TYPE = "software-dev";

    private static MissionTypeHoldingRead readsOverTheBuiltInPackAlone() {
        // The built-in pack alone: the other packs of the example pack have no pack.yaml, which the first digest
        // refuses.
        List<PackLocation> locations = List.of(new PackLocation(
                "built-in", PackLevel.BUILT_IN, ExamplePacks.packsDirectory().resolve("built-in")));
        DomainRepositoryService store = new DomainRepositoryService(() -> locations, new YamlSerializationStrategy());
        return new MissionTypeHoldingRead(
                store,
                store,
                new ConsistentRead(new FileSystemPackSources(() -> locations)),
                store,
                pack -> PackRevision.compute(List.of(pack.value())));
    }

    @Test
    @DisplayName("every step's prompt and guidelines are the stored files, byte for byte")
    void everyStepOfSoftwareDevIsServedByteForByte() throws IOException {
        MissionTypeHoldingRead reads = readsOverTheBuiltInPackAlone();
        Path mission = ExamplePacks.packsDirectory().resolve("built-in/missions/" + TYPE);
        Path steps = mission.resolve("steps");

        MissionTypeHoldingRead.Holding holding = reads.holding(BUILT_IN, TYPE);

        MissionType type = (MissionType) holding.missionType();
        List<MissionStep> all = new ArrayList<>(type.steps());
        all.addAll(type.onDemandSteps());
        PackFiles.DeclaredSteps declaredSteps = PackFiles.declaredSteps(mission.resolve(TYPE + ".mission-type.yaml"));
        List<String> sequence = declaredSteps.sequence();
        List<String> onDemand = declaredSteps.onDemand();
        List<String> declared = new ArrayList<>(sequence);
        declared.addAll(onDemand);
        assertThat(sequence).as("sequence steps the example's type declares").isNotEmpty();
        assertThat(type.steps()).extracting(MissionStep::id).as("the sequence").containsExactlyElementsOf(sequence);
        assertThat(type.onDemandSteps())
                .extracting(MissionStep::id)
                .as("the on-demand steps")
                .containsExactlyElementsOf(onDemand);
        assertThat(holding.stepFiles().keySet())
                .as("a file set per declared step")
                .containsExactlyInAnyOrderElementsOf(declared);
        assertThat(promptNames(steps))
                .as("every prompt the example pack holds belongs to a declared step")
                .isSubsetOf(declared);
        for (MissionStep step : all) {
            PackCompanionFiles.StepFiles files = holding.stepFiles().get(step.id());
            assertThat(files).as(step.id()).isNotNull();
            assertStored(files.prompt(), steps.resolve(step.id() + ".prompt.md"));
            assertStored(files.guidelines(), steps.resolve(step.id() + ".guidelines.md"));
        }
        List<String> templates = PackFiles.filesEndingWith(mission.resolve("templates"), "-template.md").stream()
                .map(name -> name.substring(0, name.length() - "-template.md".length()))
                .toList();
        assertThat(templates)
                .as("skeletons the example's templates/ directory holds")
                .isNotEmpty();
        assertThat(holding.skeletons())
                .extracting(Template::localId)
                .containsExactlyInAnyOrderElementsOf(templates.stream()
                        .map(name -> TYPE + "/" + name + "-template.md")
                        .toList());
        List<String> bound = boundContractActions(mission.getParent().resolve("built_in_step_contracts"));
        assertThat(bound)
                .as("step contracts the example pack binds to the type")
                .isNotEmpty();
        assertThat(holding.stepContracts())
                .extracting(MissionStepContract::action)
                .containsExactlyInAnyOrderElementsOf(bound);
        assertThat(holding.fingerprint()).startsWith("sha256:");
    }

    /** The step ids of the prompt files of {@code steps}, sorted. */
    private static List<String> promptNames(Path steps) throws IOException {
        return PackFiles.filesEndingWith(steps, ".prompt.md").stream()
                .map(name -> name.substring(0, name.length() - ".prompt.md".length()))
                .toList();
    }

    /** The {@code action} of every step contract file of {@code directory} whose {@code mission} is the type. */
    private static List<String> boundContractActions(Path directory) throws IOException {
        List<String> actions = new ArrayList<>();
        for (String name : PackFiles.filesEndingWith(directory, ".step-contract.yaml")) {
            List<String> lines = Files.readAllLines(directory.resolve(name));
            if (lines.contains("mission: " + TYPE)) {
                lines.stream()
                        .filter(line -> line.startsWith("action: "))
                        .findFirst()
                        .ifPresent(line ->
                                actions.add(line.substring("action: ".length()).strip()));
            }
        }
        return actions;
    }

    /** The bytes the read holds are the file's, and a file the example pack lacks is held as none. */
    private static void assertStored(Optional<Payload> held, Path file) throws IOException {
        if (Files.isRegularFile(file)) {
            assertThat(held).as(file.toString()).contains(Payload.of(Files.readAllBytes(file)));
        } else {
            assertThat(held).as(file.toString()).isEmpty();
        }
    }
}
