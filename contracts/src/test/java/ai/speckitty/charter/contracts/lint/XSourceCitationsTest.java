package ai.speckitty.charter.contracts.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * An {@code x-source} citation of the charter contract names a file that exists: its {@code path} is relative to the
 * repository root, so a moved or renamed source cannot leave a stale citation behind (#430).
 */
class XSourceCitationsTest {

    private static final String KEY = "x-source";

    @Test
    void everyCitedPathExistsInTheRepository() throws IOException {
        Path root = Path.of(System.getProperty("charter.repositoryRoot"));
        Path contract = Path.of(System.getProperty("charter.contractDir"));
        List<String> stale = new ArrayList<>();
        int citations = 0;
        try (Stream<Path> files = Files.walk(contract)) {
            for (Path file :
                    files.filter(f -> f.toString().endsWith(".yaml")).sorted().toList()) {
                for (String cited : citedPaths(read(file))) {
                    citations++;
                    if (!Files.exists(root.resolve(cited))) {
                        stale.add(root.relativize(file) + " cites " + cited);
                    }
                }
            }
        }
        assertThat(citations).as("the citations found").isPositive();
        assertThat(stale).as("x-source paths that do not exist").isEmpty();
    }

    @Test
    void theWalkFindsACitationAtAnyDepthAndNothingElse() {
        Object document = new Load(LoadSettings.builder().build()).loadFromString("""
                properties:
                  detail:
                    type: string
                    x-source:
                      path: a/B.java
                      symbol: B.detail
                x-source:
                  path: c/D.java
                note: {path: not/a/citation.java}
                """);
        assertThat(citedPaths(document)).containsExactlyInAnyOrder("a/B.java", "c/D.java");
    }

    /** Every {@code x-source.path} in a loaded YAML document, at any depth. */
    private static List<String> citedPaths(Object node) {
        List<String> paths = new ArrayList<>();
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (KEY.equals(key) && value instanceof Map<?, ?> citation && citation.get("path") != null) {
                    paths.add(String.valueOf(citation.get("path")));
                } else {
                    paths.addAll(citedPaths(value));
                }
            });
        } else if (node instanceof List<?> list) {
            list.forEach(item -> paths.addAll(citedPaths(item)));
        }
        return paths;
    }

    private static Object read(Path file) {
        try {
            return new Load(LoadSettings.builder().build())
                    .loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(file.toString(), e);
        }
    }
}
