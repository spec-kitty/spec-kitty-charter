package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.MissionTypeHoldingRead;
import ai.speckitty.charter.packs.domain.MissionStepContract;
import ai.speckitty.charter.packs.domain.PackCompanionFiles;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Template;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The mission type read serves the pinned open-packs {@code software-dev} as stored (#99, T016, SC-001): the composite
 * read over the production store holds all 13 of its steps (8 in sequence, 5 on demand), and each step's prompt and
 * guidelines are the files of the checkout byte for byte, the bare {@code spdd} slot markers included. It holds the 4
 * skeletons the type's {@code templates/} directory holds and the 5 step contracts bound to it.
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout and never
 * skips.
 */
@Tag("open-packs")
@DisplayName("The pinned software-dev is served byte for byte (#99)")
class MissionTypeHoldingCorpusTest {

    private static final PackIdentifier BUILT_IN = new PackIdentifier("built-in");

    /** Fail with the pin's own message rather than as a mismatch when the checkout is at another commit. */
    @BeforeAll
    static void theCheckoutIsAtThePin() {
        OpenPacksCorpus.assertPinned();
    }

    @Test
    @DisplayName("every step's prompt and guidelines are the stored files, byte for byte")
    void everyStepOfSoftwareDevIsServedByteForByte() throws IOException {
        // The built-in pack alone: the other packs of the checkout have no pack.yaml, which the first digest refuses.
        List<PackLocation> locations = List.of(new PackLocation(
                "built-in", PackLevel.BUILT_IN, OpenPacksCorpus.packsDirectory().resolve("built-in")));
        DomainRepositoryService store = new DomainRepositoryService(() -> locations, new YamlSerializationStrategy());
        MissionTypeHoldingRead reads = new MissionTypeHoldingRead(
                store, store, new ConsistentRead(new FileSystemPackSources(() -> locations)), store);
        Path steps = OpenPacksCorpus.packsDirectory().resolve("built-in/missions/software-dev/steps");

        MissionTypeHoldingRead.Holding holding = reads.holding(BUILT_IN, "software-dev");

        MissionType type = (MissionType) holding.missionType();
        assertThat(type.steps()).hasSize(8);
        assertThat(type.onDemandSteps()).hasSize(5);
        List<MissionStep> all = new ArrayList<>(type.steps());
        all.addAll(type.onDemandSteps());
        assertThat(holding.stepFiles()).hasSize(13);
        int marked = 0;
        for (MissionStep step : all) {
            PackCompanionFiles.StepFiles files = holding.stepFiles().get(step.id());
            assertThat(files).as(step.id()).isNotNull();
            assertStored(files.prompt(), steps.resolve(step.id() + ".prompt.md"));
            assertStored(files.guidelines(), steps.resolve(step.id() + ".guidelines.md"));
            if (new String(files.prompt().orElseThrow().bytes(), StandardCharsets.UTF_8)
                    .contains("<!-- spdd:reasons-block:start -->")) {
                marked++;
            }
        }
        assertThat(marked).as("prompts that keep their spdd slot markers").isEqualTo(5);
        assertThat(holding.skeletons())
                .extracting(Template::localId)
                .containsExactly(
                        "software-dev/plan-template.md",
                        "software-dev/spec-template.md",
                        "software-dev/task-prompt-template.md",
                        "software-dev/tasks-template.md");
        assertThat(holding.stepContracts())
                .extracting(MissionStepContract::action)
                .containsExactly("implement", "plan", "review", "specify", "tasks");
        assertThat(holding.fingerprint()).startsWith("sha256:");
    }

    /** The bytes the read holds are the file's, and a file the checkout lacks is held as none. */
    private static void assertStored(Optional<Payload> held, Path file) throws IOException {
        if (Files.isRegularFile(file)) {
            assertThat(held).as(file.toString()).contains(Payload.of(Files.readAllBytes(file)));
        } else {
            assertThat(held).as(file.toString()).isEmpty();
        }
    }
}
