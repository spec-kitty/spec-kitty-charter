package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCompanionFiles;
import ai.speckitty.charter.packs.domain.Payload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the store tells the packs rings about a mission type it loaded (#99): the step ids it declares
 * ({@link ai.speckitty.charter.packs.domain.MissionTypeSteps}), its steps then its on-demand steps, and the stored bytes
 * of each step's prompt and guidelines files ({@link PackCompanionFiles}), byte for byte, slot markers and frontmatter
 * included. Any artifact that is not a mission type has neither.
 */
class MissionTypeStoredFilesTest {

    private static final String PACK = "pack-descriptor-only";
    private static final PackIdentifier ID = new PackIdentifier(PACK);

    /** A prompt with frontmatter and bare spdd slot markers. */
    private static final String SPECIFY_PROMPT = """
            ---
            description: Create a mission specification
            ---
            # /spec-kitty.specify - Create Mission Specification

            <!-- spdd:reasons-block:start -->
            <!-- spdd:reasons-block:end -->

            ## Discovery

            Ask the operator what the mission is for.
            """;

    private static final String SPECIFY_GUIDELINES = """
            # Specify Action - Governance Guidelines

            ## Core Authorship Focus

            - Focus on **WHAT** users need and **WHY**.
            """;

    private static final String RETROSPECT_PROMPT = """
            # /spec-kitty.retrospect - Mission Retrospective

            Record what helped and what did not.
            """;

    private Path pack;
    private DomainRepositoryService service;

    @BeforeEach
    void aPackWithAMissionType(@TempDir Path host) throws IOException {
        pack = PackFixtures.copyOf(PACK, host);
        Path type = Files.createDirectories(pack.resolve("missions/software-dev"));
        Files.createDirectories(type.resolve("steps"));
        Files.writeString(type.resolve("software-dev.mission-type.yaml"), """
                schema_version: '1.0'
                id: software-dev
                title: Software Development
                description: Build software
                steps:
                - id: specify
                  title: Specification
                  prompt_path: steps/specify.prompt.md
                  guidelines_path: steps/specify.guidelines.md
                - id: plan
                  title: Implementation Planning
                on_demand_steps:
                - id: retrospect
                  title: Retrospective
                  prompt_path: steps/retrospect.prompt.md
                """);
        Files.writeString(type.resolve("steps/specify.prompt.md"), SPECIFY_PROMPT);
        Files.writeString(type.resolve("steps/specify.guidelines.md"), SPECIFY_GUIDELINES);
        Files.writeString(type.resolve("steps/retrospect.prompt.md"), RETROSPECT_PROMPT);
        Files.createDirectories(pack.resolve("directives"));
        Files.writeString(pack.resolve("directives/SD.directive.yaml"), """
                schema_version: '1.0'
                id: SD
                title: SD
                intent: Not a mission type
                enforcement: required
                """);
        service = new DomainRepositoryService(
                () -> List.of(new PackLocation(PACK, PackLevel.ORG, pack)), new YamlSerializationStrategy());
    }

    @Test
    void theStepIdsOfAMissionTypeAreItsStepsAndItsOnDemandSteps() {
        assertThat(service.stepIds(missionType())).containsExactlyInAnyOrder("specify", "plan", "retrospect");
    }

    @Test
    void anArtifactThatIsNotAMissionTypeHasNoStepIdsAndNoStepFiles() {
        CharterArtifact directive =
                service.load(ID).artifacts(ArtifactKind.DIRECTIVE).getFirst();

        assertThat(service.stepIds(directive)).isEmpty();
        assertThat(service.stepFiles(ID, directive)).isEmpty();
    }

    @Test
    void theStepFilesAreTheStoredBytesOfEachStepsPromptAndGuidelinesInStepOrder() throws IOException {
        Map<String, PackCompanionFiles.StepFiles> files = service.stepFiles(ID, missionType());

        assertThat(files).containsOnlyKeys("specify", "plan", "retrospect");
        assertThat(List.copyOf(files.keySet())).containsExactly("specify", "plan", "retrospect");
        assertThat(files.get("specify").prompt()).contains(stored("steps/specify.prompt.md"));
        assertThat(text(files.get("specify").prompt()))
                .isEqualTo(SPECIFY_PROMPT)
                .contains("spdd:reasons-block:start");
        assertThat(files.get("specify").guidelines()).contains(stored("steps/specify.guidelines.md"));
        assertThat(files.get("plan").prompt()).isEmpty();
        assertThat(files.get("plan").guidelines()).isEmpty();
        assertThat(text(files.get("retrospect").prompt())).isEqualTo(RETROSPECT_PROMPT);
        assertThat(files.get("retrospect").guidelines()).isEmpty();
    }

    @Test
    void theStepFilesAreTheBytesAsStoredNotARenderingOfTheParsedDocument() throws IOException {
        // Two blank lines and a trailing space: a parse and re-render would normalise them; the stored bytes keep them.
        String irregular = "# /spec-kitty.retrospect - Mission Retrospective\n\n\nRecord what helped. \n";
        Files.writeString(pack.resolve("missions/software-dev/steps/retrospect.prompt.md"), irregular);

        Map<String, PackCompanionFiles.StepFiles> files = service.stepFiles(ID, missionType());

        assertThat(text(files.get("retrospect").prompt())).isEqualTo(irregular);
    }

    private CharterArtifact missionType() {
        Pack loaded = service.load(ID);
        assertThat(loaded.violations()).isEmpty();
        return loaded.artifacts(ArtifactKind.MISSION_TYPE).getFirst();
    }

    private Payload stored(String path) throws IOException {
        return Payload.of(
                Files.readAllBytes(pack.resolve("missions/software-dev").resolve(path)));
    }

    private static String text(Optional<Payload> payload) {
        return new String(payload.orElseThrow().bytes(), StandardCharsets.UTF_8);
    }
}
