package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Every mission type of the example packs reads into the domain (#194, ADR-0044): the built-in example
 * pack holds its mission type at {@code missions/<id>/<id>.mission-type.yaml}, with every step's prompt
 * and guidelines and every output template the types name, and no file of a retired family is left. Each mission-type
 * file and step companion writes back through the production writer byte for byte (the whole-pack gate covers only
 * the built-in pack, and compares by meaning). The counts are pinned.
 *
 * <p>Reads the charter-owned example packs ({@code ExamplePacks}, ADR-0070) and runs under {@code :packs:test}.
 */
@DisplayName("Every example mission type reads into the domain (#194)")
class MissionTypeCorpusTest {

    /**
     * The schema violations of the built-in example pack's presets: none; they carry the canonical shape, so a new
     * one is a regression this count catches.
     */
    private static final int BUILT_IN_PRESET_VIOLATIONS = 0;

    @TempDir
    Path host;

    @ParameterizedTest(name = "{0}: {1} mission types, {2} steps, {3} on demand")
    @CsvSource({"built-in, 1, 8, 5"})
    @DisplayName("each pack's mission types read with no violation, and their counts are pinned")
    void everyMissionTypeReads(String pack, int types, int steps, int onDemand) throws IOException {
        List<MissionType> read = missionTypes(pack);

        assertThat(read).as("%s mission types", pack).hasSize(types);
        assertThat(read.stream().mapToInt(type -> type.steps().size()).sum())
                .as("%s steps", pack)
                .isEqualTo(steps);
        assertThat(read.stream().mapToInt(type -> type.onDemandSteps().size()).sum())
                .as("%s on-demand steps", pack)
                .isEqualTo(onDemand);
    }

    @Test
    @DisplayName("the example holds 1 mission type, 8 steps and 5 on demand, 13 prompts and 6 guidelines")
    void theCorpusCountsArePinned() throws IOException {
        List<MissionStep> steps = new ArrayList<>();
        for (String pack : List.of(ExamplePacks.BUILT_IN)) {
            for (MissionType type : missionTypes(pack)) {
                steps.addAll(type.steps());
                steps.addAll(type.onDemandSteps());
            }
        }

        assertThat(steps).hasSize(13);
        List<Path> templates = new ArrayList<>();
        for (String pack : List.of(ExamplePacks.BUILT_IN)) {
            Path packDirectory = ExamplePacks.packsDirectory().resolve(pack);
            for (MissionType type : missionTypes(pack)) {
                Path typeDirectory = packDirectory.resolve("missions").resolve(type.localId());
                Stream.concat(
                                type.expectedOutputArtifacts().stream(),
                                Stream.concat(type.steps().stream(), type.onDemandSteps().stream())
                                        .flatMap(step -> step.expectedOutputArtifacts().stream()))
                        .flatMap(artifact -> artifact.templatePath().stream())
                        .forEach(path -> templates.add(typeDirectory.resolve(path)));
            }
        }
        assertThat(templates)
                .as("output templates the mission types name")
                .hasSize(4)
                .allSatisfy(template -> assertThat(template).isRegularFile());
        assertThat(steps.stream().filter(step -> step.prompt().isPresent())).hasSize(13);
        assertThat(steps.stream().filter(step -> step.guidelines().isPresent())).hasSize(6);
        assertThat(steps.stream()
                        .filter(step -> step.prompt()
                                .flatMap(prompt -> prompt.description())
                                .isPresent()))
                .as("prompts with a detailed description (ruling Q1 as refined on 2026-10-30)")
                .hasSize(13);
    }

    @ParameterizedTest(name = "{0}: {1} files")
    @CsvSource({"built-in, 20"})
    @DisplayName("each mission-type file and step companion writes back through the production writer byte for byte")
    void everyMissionFileWritesBackByteForByte(String pack, int files) throws IOException {
        Path source = ExamplePacks.packsDirectory().resolve(pack);
        Path packs = copyOf(source);
        Path copy = packs.resolve(pack);
        List<String> written = missionFiles(copy);
        FilesystemPackStore store = new FilesystemPackStore(packs, new YamlSerializationStrategy());
        PackIdentifier id = new PackIdentifier(pack);
        List<MissionTypeDso> dsos =
                store.read(id, List.of(MissionTypeBinding.INSTANCE)).dsos(MissionTypeBinding.INSTANCE);
        for (String file : written) {
            Files.delete(copy.resolve(file));
        }

        for (MissionTypeDso dso : dsos) {
            store.write(
                    id,
                    MissionTypeBinding.INSTANCE,
                    MissionTypeBinding.INSTANCE.toDso(MissionTypeBinding.INSTANCE.toEntity(id, dso)));
        }

        assertThat(written)
                .as("%s mission-type files and step companions", pack)
                .hasSize(files);
        assertThat(missionFiles(copy)).containsExactlyElementsOf(written);
        for (String file : written) {
            assertThat(copy.resolve(file)).as(file).hasSameBinaryContentAs(source.resolve(file));
        }
    }

    /**
     * The five software-dev prompts that declare the {@code spdd:reasons-block} slot. The slots are
     * empty: the org example pack fills them through prompt fragments (ADR-0066 amendment), and the built-in
     * descriptor has {@code schema_version} {@code "1.0"}, under which a non-empty slot would be the blocking {@code prompt_slot_not_empty}.
     */
    private static final List<String> SLOT_PROMPTS = List.of(
            "missions/software-dev/steps/implement.prompt.md",
            "missions/software-dev/steps/plan.prompt.md",
            "missions/software-dev/steps/review.prompt.md",
            "missions/software-dev/steps/specify.prompt.md",
            "missions/software-dev/steps/tasks.prompt.md");

    @Test
    @DisplayName("the built-in pack loads with 0 prompt-slot findings: its 5 software-dev slots are present and empty"
            + " because the org example pack fills them through prompt fragments")
    void theBuiltInPackHoldsItsSlotsEmpty() throws IOException {
        Path builtIn = ExamplePacks.packsDirectory().resolve("built-in");

        for (String prompt : SLOT_PROMPTS) {
            String text = Files.readString(builtIn.resolve(prompt), StandardCharsets.UTF_8);
            assertThat(text)
                    .as("%s declares an empty spdd:reasons-block", prompt)
                    .contains("<!-- spdd:reasons-block:start -->\n<!-- spdd:reasons-block:end -->");
        }

        WholePackStore.LoadedPack loaded = new WholePackStore(new YamlSerializationStrategy()).load(builtIn);

        assertThat(loaded.warnings())
                .as("no prompt_slot_not_empty warning, nor any other: no legacy slot text is left")
                .isEmpty();
        assertThat(loaded.pack().violations())
                .as("no prompt_slot_not_empty or prompt_slot_malformed refusal under the 1.0 descriptor")
                .isEmpty();
    }

    @Test
    @DisplayName("the built-in pack composes in the examples: no pack_unreadable (NFR-002)")
    void theBuiltInPackComposesAtThePin() {
        Path builtIn = ExamplePacks.packsDirectory().resolve("built-in");
        DomainRepositoryService service = new DomainRepositoryService(
                () -> List.of(new PackLocation("built-in", PackLevel.BUILT_IN, builtIn)),
                new YamlSerializationStrategy());

        assertThatCode(() -> service.compositionFacts(new PackIdentifier("built-in")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no pack in the examples holds a file of a retired family")
    void noRetiredFileIsLeft() throws IOException {
        try (Stream<Path> packs = Files.list(ExamplePacks.packsDirectory())) {
            for (Path pack : packs.filter(Files::isDirectory).sorted().toList()) {
                assertThat(PackFileSet.listing(pack, PackFileKind.bundled()).retired())
                        .as("retired files in %s", pack)
                        .isEmpty();
            }
        }
    }

    @Test
    @DisplayName("the throwaway migration harness of #194 is gone: no source set, no task")
    void theMigrationHarnessIsGone() throws IOException {
        Path packs =
                Path.of(System.getProperty("charter.repositoryRoot", "../..")).resolve("service/packs");

        assertThat(packs.resolve("src/migration")).doesNotExist();
        assertThat(Files.readString(packs.resolve("build.gradle.kts"), StandardCharsets.UTF_8))
                .doesNotContain("migration", "MissionTierMigration");
    }

    private List<MissionType> missionTypes(String pack) throws IOException {
        Path packs = ExamplePacks.packsDirectory();
        FilesystemPackStore.PackRead read = new FilesystemPackStore(packs, new YamlSerializationStrategy())
                .read(new PackIdentifier(pack), List.of(MissionTypeBinding.INSTANCE));

        // The built-in pack's presets load clean (OP-1 landed upstream before the pin); nothing is refused.
        assertThat(read.violations())
                .as("%s violations outside its presets", pack)
                .filteredOn(violation -> !violation.file().startsWith("presets/"))
                .isEmpty();
        assertThat(read.violations())
                .as("%s preset violations", pack)
                .hasSize(pack.equals("built-in") ? BUILT_IN_PRESET_VIOLATIONS : 0);
        return read.dsos(MissionTypeBinding.INSTANCE).stream()
                .map(dso -> MissionTypeBinding.INSTANCE.toEntity(new PackIdentifier(pack), dso))
                .toList();
    }

    /** The pack's mission-type files and step companions, pack-relative, in path order. */
    private static List<String> missionFiles(Path pack) throws IOException {
        try (Stream<Path> walk = Files.walk(pack.resolve("missions"))) {
            return walk.filter(Files::isRegularFile)
                    .map(path -> pack.relativize(path).toString().replace('\\', '/'))
                    .filter(file ->
                            file.endsWith(".mission-type.yaml") || file.matches("missions/[^/]+/steps/[^/]+\\.md"))
                    .sorted()
                    .toList();
        }
    }

    /** A copy of a pack the test may change, under a fresh packs directory. */
    private Path copyOf(Path pack) throws IOException {
        Path packs = host.resolve("copy-" + System.nanoTime());
        Path copy = packs.resolve(String.valueOf(pack.getFileName()));
        try (Stream<Path> walk = Files.walk(pack)) {
            for (Path path : walk.sorted(Comparator.naturalOrder()).toList()) {
                Path to = copy.resolve(pack.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(path, to);
                }
            }
        }
        return packs;
    }
}
