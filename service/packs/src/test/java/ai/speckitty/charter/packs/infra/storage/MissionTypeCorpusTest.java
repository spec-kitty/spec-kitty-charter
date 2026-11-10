package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every mission type of the pinned open-packs checkout reads into the domain (#194, ADR-0044): the built-in, cave-kitty
 * and qa packs each hold their mission types at {@code missions/<id>/<id>.mission-type.yaml}, with every step's prompt
 * and guidelines and every output template the types name, and no file of a retired family is left. Each mission-type
 * file and step companion writes back through the production writer byte for byte (the whole-pack gate covers only
 * the built-in pack, and compares by meaning). The counts are never literals: each is what the mission-type files and
 * the step files on disk declare.
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout and never
 * skips.
 */
@Tag("open-packs")
@DisplayName("Every pinned mission type reads into the domain (#194)")
class MissionTypeCorpusTest {

    /** Fail with the pin's own message rather than as a count mismatch when the checkout is at another commit. */
    @BeforeAll
    static void theCheckoutIsAtThePin() {
        OpenPacksCorpus.assertPinned();
    }

    /** The descriptor added to a copy of a pack that has none, so the store can read it. */
    private static final String PACK_YAML = """
            pack_id: 01M60H9B0000000000000000QA
            pack_version: 1.0.0
            name: qa
            pack_level: org
            """;

    /**
     * The schema violations of the built-in pack's two pinned presets: none. OP-1 landed upstream before the pin
     * (open-packs commit 213a059, the presets moved to the sanitized shape), so the presets load clean; a new one is
     * a regression this count catches.
     */
    private static final int BUILT_IN_PRESET_VIOLATIONS = 0;

    @TempDir
    Path host;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"built-in", "cave-kitty", "qa"})
    @DisplayName("each pack's mission types read with no violation, and read what their files declare")
    void everyMissionTypeReads(String pack) throws IOException {
        List<MissionType> read = missionTypes(pack);
        List<Map<?, ?>> declared = declaredMissionTypes(pack);

        assertThat(read).as("%s mission types", pack).isNotEmpty().hasSameSizeAs(declared);
        assertThat(read.stream().mapToInt(type -> type.steps().size()).sum())
                .as("%s steps", pack)
                .isPositive()
                .isEqualTo(declared.stream()
                        .mapToInt(type -> listOf(type, "steps").size())
                        .sum());
        assertThat(read.stream().mapToInt(type -> type.onDemandSteps().size()).sum())
                .as("%s on-demand steps", pack)
                .isEqualTo(declared.stream()
                        .mapToInt(type -> listOf(type, "on_demand_steps").size())
                        .sum());
    }

    @Test
    @DisplayName("every step that declares a prompt or guidelines reads it, and each file on disk is a step's")
    void everyStepCompanionOnDiskIsReadByItsStep() throws IOException {
        List<MissionStep> steps = new ArrayList<>();
        long promptFiles = 0;
        long guidelineFiles = 0;
        for (String pack : List.of("built-in", "cave-kitty", "qa")) {
            for (MissionType type : missionTypes(pack)) {
                steps.addAll(type.steps());
                steps.addAll(type.onDemandSteps());
            }
            for (String file : missionFiles(OpenPacksCorpus.packsDirectory().resolve(pack))) {
                promptFiles += file.endsWith(".prompt.md") ? 1 : 0;
                guidelineFiles += file.endsWith(".guidelines.md") ? 1 : 0;
            }
        }

        assertThat(steps).as("steps read").isNotEmpty();
        List<Path> templates = new ArrayList<>();
        for (String pack : List.of("built-in", "cave-kitty", "qa")) {
            Path packDirectory = OpenPacksCorpus.packsDirectory().resolve(pack);
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
                .isNotEmpty()
                .allSatisfy(template -> assertThat(template).isRegularFile());
        assertThat(steps.stream().filter(step -> step.prompt().isPresent()).count())
                .as("steps that read a prompt are the step prompt files on disk")
                .isPositive()
                .isEqualTo(promptFiles);
        assertThat(steps.stream().filter(step -> step.guidelines().isPresent()).count())
                .as("steps that read guidelines are the step guidelines files on disk")
                .isPositive()
                .isEqualTo(guidelineFiles);
        assertThat(steps.stream()
                        .filter(step -> step.prompt()
                                .flatMap(prompt -> prompt.description())
                                .isPresent()))
                .as("prompts with a detailed description (ruling Q1 as refined on 2026-10-30)")
                .isNotEmpty();
    }

    /** The mission-type files of a pack, read as raw YAML: what the files declare, without the store. */
    private static List<Map<?, ?>> declaredMissionTypes(String pack) throws IOException {
        Path root = OpenPacksCorpus.packsDirectory().resolve(pack);
        List<Map<?, ?>> types = new ArrayList<>();
        for (String file : missionFiles(root)) {
            if (file.endsWith(".mission-type.yaml")) {
                types.add((Map<?, ?>) Yaml.load(Files.readString(root.resolve(file), StandardCharsets.UTF_8)));
            }
        }
        return types;
    }

    private static List<?> listOf(Map<?, ?> type, String key) {
        return type.get(key) instanceof List<?> list ? list : List.of();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"built-in", "cave-kitty", "qa"})
    @DisplayName("each mission-type file and step companion writes back through the production writer byte for byte")
    void everyMissionFileWritesBackByteForByte(String pack) throws IOException {
        OpenPacksCorpus.assertPinned();
        Path source = OpenPacksCorpus.packsDirectory().resolve(pack);
        Path packs = copyWithDescriptor(source);
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
                .as("%s mission-type files and step companions: every one the checkout holds", pack)
                .isNotEmpty()
                .containsExactlyElementsOf(missionFiles(source));
        assertThat(missionFiles(copy)).containsExactlyElementsOf(written);
        for (String file : written) {
            assertThat(copy.resolve(file)).as(file).hasSameBinaryContentAs(source.resolve(file));
        }
    }

    /**
     * The five software-dev prompts that declare the {@code spdd:reasons-block} slot. At the pin (open-packs a2a9379)
     * the slots are empty: open-packs 170dbc3 moved their legacy REASONS text into the opt-in {@code spdd} pack as
     * prompt fragments (open-packs#175, ADR-0066 amendment) and gave the built-in descriptor {@code schema_version}
     * {@code "1.0"}, under which a non-empty slot would be the blocking {@code prompt_slot_not_empty}.
     */
    private static final List<String> SLOT_PROMPTS = List.of(
            "missions/software-dev/steps/implement.prompt.md",
            "missions/software-dev/steps/plan.prompt.md",
            "missions/software-dev/steps/review.prompt.md",
            "missions/software-dev/steps/specify.prompt.md",
            "missions/software-dev/steps/tasks.prompt.md");

    @Test
    @DisplayName("the built-in pack loads with 0 prompt-slot findings: its 5 software-dev slots are present and empty"
            + " since open-packs 170dbc3 moved the REASONS text into the spdd pack")
    void theBuiltInPackHoldsItsSlotsEmpty() throws IOException {
        OpenPacksCorpus.assertPinned();
        Path builtIn = OpenPacksCorpus.packsDirectory().resolve("built-in");

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
    @DisplayName("the built-in pack composes at the pin: no pack_unreadable (NFR-002)")
    void theBuiltInPackComposesAtThePin() {
        OpenPacksCorpus.assertPinned();
        Path builtIn = OpenPacksCorpus.packsDirectory().resolve("built-in");
        DomainRepositoryService service = new DomainRepositoryService(
                () -> List.of(new PackLocation("built-in", PackLevel.BUILT_IN, builtIn)),
                new YamlSerializationStrategy());

        assertThatCode(() -> service.compositionFacts(new PackIdentifier("built-in")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no pack at the pin holds a file of a retired family")
    void noRetiredFileIsLeft() throws IOException {
        try (Stream<Path> packs = Files.list(OpenPacksCorpus.packsDirectory())) {
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
        OpenPacksCorpus.assertPinned();
        Path packs = OpenPacksCorpus.packsDirectory();
        if (!Files.isRegularFile(packs.resolve(pack).resolve(PackFileSet.PACK_YAML))) {
            packs = copyWithDescriptor(packs.resolve(pack));
        }
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

    /** A copy of a pack with a minimal {@code pack.yaml} added (a test fixture only, never the checkout). */
    private Path copyWithDescriptor(Path pack) throws IOException {
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
        if (!Files.exists(copy.resolve(PackFileSet.PACK_YAML))) {
            Files.writeString(copy.resolve(PackFileSet.PACK_YAML), PACK_YAML, StandardCharsets.UTF_8);
        }
        return packs;
    }
}
