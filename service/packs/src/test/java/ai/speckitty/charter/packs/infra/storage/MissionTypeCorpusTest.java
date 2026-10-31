package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Every mission type of the pinned open-packs checkout reads into the domain (#194, ADR-0044): the built-in, cave-kitty
 * and qa packs each hold their mission types at {@code missions/<id>/<id>.mission-type.yaml}, with every step's prompt
 * and guidelines and every output template the types name, and no file of a retired family is left. Each mission-type
 * file and step companion writes back through the production writer byte for byte (the whole-pack gate covers only
 * the built-in pack, and compares by meaning). The counts are pinned.
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout and never
 * skips.
 */
@Tag("open-packs")
@DisplayName("Every pinned mission type reads into the domain (#194)")
class MissionTypeCorpusTest {

    /** The descriptor added to a copy of a pack that has none, so the store can read it. */
    private static final String PACK_YAML = """
            pack_id: 01M60H9B0000000000000000QA
            pack_version: 1.0.0
            name: qa
            pack_level: org
            """;

    /** The schema violations of the built-in pack's two pinned presets (OP-1), as the store reports them. */
    private static final int BUILT_IN_PRESET_VIOLATIONS = 14;

    @TempDir
    Path host;

    @ParameterizedTest(name = "{0}: {1} mission types, {2} steps, {3} on demand")
    @CsvSource({"built-in, 4, 25, 6", "cave-kitty, 1, 8, 0", "qa, 1, 7, 0"})
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
    @DisplayName("the corpus holds 6 mission types, 40 steps and 6 on demand, 43 prompts and 17 guidelines")
    void theCorpusCountsArePinned() throws IOException {
        List<MissionStep> steps = new ArrayList<>();
        for (String pack : List.of("built-in", "cave-kitty", "qa")) {
            for (MissionType type : missionTypes(pack)) {
                steps.addAll(type.steps());
                steps.addAll(type.onDemandSteps());
            }
        }

        assertThat(steps).hasSize(46);
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
                .hasSize(13)
                .allSatisfy(template -> assertThat(template).isRegularFile());
        assertThat(steps.stream().filter(step -> step.prompt().isPresent())).hasSize(43);
        assertThat(steps.stream().filter(step -> step.guidelines().isPresent())).hasSize(17);
        assertThat(steps.stream()
                        .filter(step -> step.prompt()
                                .flatMap(prompt -> prompt.description())
                                .isPresent()))
                .as("prompts with a detailed description (ruling Q1 as refined on 2026-10-30)")
                .hasSize(28);
    }

    @ParameterizedTest(name = "{0}: {1} files")
    @CsvSource({"built-in, 49", "cave-kitty, 9", "qa, 8"})
    @DisplayName("each mission-type file and step companion writes back through the production writer byte for byte")
    void everyMissionFileWritesBackByteForByte(String pack, int files) throws IOException {
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
                .as("%s mission-type files and step companions", pack)
                .hasSize(files);
        assertThat(missionFiles(copy)).containsExactlyElementsOf(written);
        for (String file : written) {
            assertThat(copy.resolve(file)).as(file).hasSameBinaryContentAs(source.resolve(file));
        }
    }

    @Test
    @DisplayName("no pack at the pin, and no vendored pack, holds a file of a retired family")
    void noRetiredFileIsLeft() throws IOException {
        Path vendored =
                Path.of(System.getProperty("charter.repositoryRoot", "../..")).resolve("packs");
        for (Path root : List.of(OpenPacksCorpus.packsDirectory(), vendored)) {
            try (Stream<Path> packs = Files.list(root)) {
                for (Path pack : packs.filter(Files::isDirectory).sorted().toList()) {
                    assertThat(PackFileSet.listing(pack, PackFileKind.bundled()).retired())
                            .as("retired files in %s", pack)
                            .isEmpty();
                }
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

        // The built-in pack's two pinned presets have known findings of their own (OP-1); nothing else is refused.
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
