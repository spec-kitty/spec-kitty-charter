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
 * No procedure source reads a retired field name (SC-005, C-005). The scan is targeted at the procedure's main sources
 * ({@code Procedure*.java}, which takes in {@code ProcedureYaml.java}): the words {@code name}, {@code purpose} and
 * {@code actor} are legitimate keys of other kinds, so a repository-wide scan would be wrong. Its runtime twin is
 * {@link ProcedureYamlTest#theRetiredShapeIsRefusedAtLoad}, which loads a retired-shape file and sees it refused.
 */
@DisplayName("No procedure source reads a retired field name (#194)")
class RetiredFieldNamesTest {

    private static final List<String> RETIRED = List.of("\"name\"", "\"purpose\"", "\"actor\"");

    @Test
    @DisplayName("Procedure*.java holds none of the literals \"name\", \"purpose\", \"actor\"; the scan does see keys")
    void noProcedureSourceReadsARetiredName() throws IOException {
        Map<String, String> sources = procedureSources();

        // Non-vacuity: the scan reaches the format, and the format's own keys are visible to it.
        assertThat(sources).containsKeys("ProcedureYaml.java", "ProcedureBinding.java", "ProcedureDso.java");
        assertThat(sources.get("ProcedureYaml.java")).contains("\"title\"", "\"description\"", "\"role\"");

        for (Map.Entry<String, String> source : sources.entrySet()) {
            for (String literal : RETIRED) {
                assertThat(source.getValue())
                        .as(source.getKey() + " reads " + literal)
                        .doesNotContain(literal);
            }
        }
    }

    private static Map<String, String> procedureSources() throws IOException {
        String root = System.getProperty("charter.repositoryRoot");
        assertThat(root).as("charter.repositoryRoot is set by the build").isNotNull();
        Path main = Path.of(root).resolve("service/packs/src/main/java");
        assertThat(main).isDirectory();
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(main)) {
            for (Path file : walk.filter(Files::isRegularFile)
                    .filter(file -> {
                        String name = file.getFileName().toString();
                        return name.startsWith("Procedure") && name.endsWith(".java");
                    })
                    .toList()) {
                out.put(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return out;
    }
}
