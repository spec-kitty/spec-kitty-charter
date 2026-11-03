package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No main source of {@code :missions} or {@code :packs} reads a retired mission field or file name (#194, NFR-003): the
 * consolidated mission type tolerates no retired or variant field, so its reader and domain never name one. Only
 * {@code contracts/packs/file-kinds.yaml}, which is not a main source, names the retired files, in its {@code retired}
 * section. The field names are scanned as the token itself, except {@code agent_profile}: it is also the live
 * {@code agent_profile} node kind token, so only its use as a YAML key, the quoted literal, is retired; and
 * {@code depends_on}: it is retired as a mission step field, but live as the pack descriptor's dependency list (the
 * pack-composition mission, ADR-0048 decision 4), so a source whose concern is the mission type may not name it while
 * the pack descriptor's reader, writer and resolver do. Its runtime twin is {@link RetiredFilesTest}, which refuses
 * each retired file by name. The precedent is {@link RetiredFieldNamesTest}.
 */
@DisplayName("No :missions or :packs main source reads a retired mission field or file name (#194)")
class RetiredMissionFieldNamesTest {

    private static final List<String> RETIRED = List.of(
            "display_name",
            "\"agent_profile\"",
            "sequence_index",
            "in_action_sequence",
            "mission-runtime",
            "governance-profile",
            "expected-artifacts");

    /** Retired as a mission step field (#194); live as the pack descriptor's dependency list (ADR-0048 decision 4). */
    private static final String DEPENDS_ON = "depends_on";

    @Test
    @DisplayName("the main sources hold none of the retired names; the scan does see the mission-type keys")
    void noMainSourceReadsARetiredName() throws IOException {
        Map<String, String> sources = mainSources();

        // Non-vacuity: the scan reaches both modules, and the mission type's own keys are visible to it.
        assertThat(sources)
                .containsKeys(
                        "service/missions/src/main/java/ai/speckitty/charter/missions/domain/MissionType.java",
                        "service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/MissionTypeYaml.java");
        assertThat(sources.get(
                        "service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/MissionTypeYaml.java"))
                .contains("\"steps\"", "\"on_demand_steps\"", "\"role\"");
        assertThat(sources.keySet()).noneMatch(path -> path.endsWith("file-kinds.yaml"));
        assertThat(sources.keySet())
                .as("the depends_on scoping below reaches mission-type sources in both modules")
                .anyMatch(RetiredMissionFieldNamesTest::missionTypeConcern);

        for (Map.Entry<String, String> source : sources.entrySet()) {
            for (String name : RETIRED) {
                assertThat(source.getValue())
                        .as(source.getKey() + " names " + name)
                        .doesNotContain(name);
            }
            if (missionTypeConcern(source.getKey())) {
                assertThat(source.getValue())
                        .as(source.getKey() + " names " + DEPENDS_ON)
                        .doesNotContain(DEPENDS_ON);
            }
        }
    }

    /** Whether a source's concern is the mission type: the missions module, or a {@code Mission*} source of :packs. */
    private static boolean missionTypeConcern(String path) {
        return path.startsWith("service/missions/src/main/")
                || path.substring(path.lastIndexOf('/') + 1).startsWith("Mission");
    }

    private static Map<String, String> mainSources() throws IOException {
        String root = System.getProperty("charter.repositoryRoot");
        assertThat(root).as("charter.repositoryRoot is set by the build").isNotNull();
        Path repository = Path.of(root);
        Map<String, String> out = new TreeMap<>();
        for (String module : List.of("service/missions/src/main", "service/packs/src/main")) {
            Path main = repository.resolve(module);
            assertThat(main).isDirectory();
            try (Stream<Path> walk = Files.walk(main)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    out.put(
                            repository.relativize(file).toString().replace('\\', '/'),
                            Files.readString(file, StandardCharsets.UTF_8));
                }
            }
        }
        return out;
    }
}
