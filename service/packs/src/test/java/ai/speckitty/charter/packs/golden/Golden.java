package ai.speckitty.charter.packs.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads and writes the golden files. */
final class Golden {

    static final String SURFACES = "surfaces.json";
    static final String COMPOSITION = "composition.json";

    /** The switch that rewrites a golden file instead of comparing with it. */
    static final String UPDATE = "golden.update";

    /** The source directory of the golden files, which the build passes to the test JVM for a rewrite. */
    static final String DIRECTORY = "charter.goldenDir";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Golden() {}

    static boolean updating() {
        return Boolean.getBoolean(UPDATE);
    }

    /** The recorded file, from the test classpath. */
    static String text(String file) {
        try (InputStream in =
                Objects.requireNonNull(Golden.class.getResourceAsStream("/golden/" + file), "/golden/" + file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JsonNode read(String file) {
        return JSON.readTree(text(file));
    }

    /** The one way a golden value is written: pretty, two-space indented, LF line ends, a final newline. */
    static String render(Object value) {
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value).replace("\r\n", "\n") + "\n";
    }

    static void write(String file, Object value) throws IOException {
        String directory = System.getProperty(DIRECTORY);
        assertThat(directory)
                .as("system property %s (set by service/packs/build.gradle.kts)", DIRECTORY)
                .isNotNull();
        Files.writeString(
                Path.of(Objects.requireNonNull(directory)).resolve(file), render(value), StandardCharsets.UTF_8);
    }
}
