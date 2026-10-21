package ai.speckitty.charter.bootstrap.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The packs of one scenario live in a directory of their own, which goes when the scenario does. */
class ScenarioPacksTest {

    private static Path packRoot(ScenarioPacks packs) {
        return packs.locations().getFirst().root();
    }

    @Test
    void aResetDeletesTheDirectoryOfThePreviousScenario() {
        ScenarioPacks packs = new ScenarioPacks();
        packs.reset();
        packs.addFile(PackLevel.ORG, "qa", "drg/fragment.yaml", "nodes: []");
        Path previous = packRoot(packs);
        assertThat(previous.resolve("drg/fragment.yaml")).hasContent("nodes: []");

        packs.reset();

        assertThat(previous).doesNotExist();
        assertThat(packs.locations()).isEmpty();
        packs.delete();
    }

    @Test
    void deletingRemovesTheWholeDirectoryAndCanBeRepeated() {
        ScenarioPacks packs = new ScenarioPacks();
        packs.reset();
        packs.addFile(PackLevel.BUILT_IN, "built-in", "pack.yaml", "name: built-in\n");
        Path scenarioRoot = packRoot(packs).getParent().getParent();

        packs.delete();
        packs.delete();

        assertThat(Files.exists(scenarioRoot)).isFalse();
    }

    @Test
    void theProjectConfigurationDirectoryExistsEmptyAndTakesExactFiles() throws Exception {
        ScenarioPacks packs = new ScenarioPacks();
        assertThat(packs.configDir()).isEmpty();
        packs.reset();
        Path directory = packs.configDir().orElseThrow();
        assertThat(directory).isDirectory().isEmptyDirectory();

        packs.writeConfigFile("charter.yaml", "a: 1");
        assertThat(directory.resolve("charter.yaml")).hasContent("a: 1");
        assertThat(Files.readAllBytes(directory.resolve("charter.yaml"))).hasSize(4);
        packs.writeConfigFile("charter.yaml", "");
        assertThat(directory.resolve("charter.yaml")).isEmptyFile();
        packs.deleteConfigFile("charter.yaml");
        packs.deleteConfigFile("charter.yaml");
        assertThat(directory).isEmptyDirectory();

        packs.delete();
        assertThat(directory).doesNotExist();
        assertThat(packs.configDir()).isEmpty();
    }
}
