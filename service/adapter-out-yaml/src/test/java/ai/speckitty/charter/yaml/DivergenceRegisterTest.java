package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** The register's machine form and its page list the same ids, and an id is never listed twice. */
class DivergenceRegisterTest {

    private static Path repository() {
        String root = System.getProperty("charter.repositoryRoot");
        assertThat(root).as("system property charter.repositoryRoot").isNotNull();
        return Path.of(root);
    }

    @Test
    void aDuplicateIdInTheYamlFailsInsteadOfTheLaterEntryOverwritingTheEarlier() {
        String yaml = """
                - id: DIV-001
                  surface: first
                - id: DIV-002
                  surface: second
                - id: DIV-001
                  surface: third
                """;
        assertThatThrownBy(() -> DivergenceRegister.entries(yaml))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DIV-001");
    }

    @Test
    void everyIdOfTheYamlIsUniqueAndEveryEntryHasTheSchemaFields() throws IOException {
        var entries = DivergenceRegister.entries(repository());
        assertThat(entries).isNotEmpty();
        entries.forEach((id, row) -> assertThat(
                        row.keySet().stream().map(String::valueOf).toList())
                .as(id)
                .contains("id", "surface", "core", "service", "rationale", "ruling", "status", "since", "evidence"));
    }

    @Test
    void everyIdOfTheYamlIsOnThePageAndEveryIdOfThePageIsInTheYaml() throws IOException {
        Set<String> yaml =
                new TreeSet<>(DivergenceRegister.entries(repository()).keySet());
        List<String> pageList = DivergenceRegister.pageIds(
                Files.readString(DivergenceRegister.page(repository()), StandardCharsets.UTF_8));
        Set<String> page = new TreeSet<>(pageList);
        Set<String> onlyYaml = new LinkedHashSet<>(yaml);
        onlyYaml.removeAll(page);
        Set<String> onlyPage = new LinkedHashSet<>(page);
        onlyPage.removeAll(yaml);
        assertThat(onlyYaml)
                .as("in divergences.yaml but not on divergence-register.md")
                .isEmpty();
        assertThat(onlyPage)
                .as("on divergence-register.md but not in divergences.yaml")
                .isEmpty();
    }
}
