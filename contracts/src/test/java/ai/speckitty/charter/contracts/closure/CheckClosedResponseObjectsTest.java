package ai.speckitty.charter.contracts.closure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckClosedResponseObjectsTest {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);

    @Test
    void aClosedBundlePassesAndSaysHowManyResponseSchemasItJudged() {
        int code = CheckClosedResponseObjects.run(List.of(System.getProperty("charter.lintCleanFixture")), out);
        assertThat(code).isZero();
        assertThat(printed()).isEqualTo("All 24 response schemas are closed.\n");
    }

    @Test
    void anOpenBundleFailsWithOneLinePerViolation() throws URISyntaxException {
        Path fixture = Path.of(getClass()
                .getResource("/closure/additional-properties-true.yaml")
                .toURI());
        int code = CheckClosedResponseObjects.run(List.of(fixture.toString()), out);
        assertThat(code).isEqualTo(1);
        assertThat(printed())
                .startsWith("1 violation of the closed-response-objects convention:\n"
                        + "/paths/~1probe/get/responses/200/content/application~1json/schema/properties/probe: ");
    }

    @Test
    void aMissingArgumentIsAUsageError() {
        assertThat(CheckClosedResponseObjects.run(List.of(), out)).isEqualTo(2);
        assertThat(printed()).startsWith("usage:");
    }

    @Test
    void aMissingFileCannotBeRead(@TempDir Path dir) {
        Path missing = dir.resolve("missing.yaml");
        assertThat(CheckClosedResponseObjects.run(List.of(missing.toString()), out))
                .isEqualTo(2);
        assertThat(printed()).startsWith("Cannot read " + missing + ": ");
    }

    @Test
    void aFileThatIsNotYamlCannotBeRead(@TempDir Path dir) throws IOException {
        Path broken = Files.writeString(dir.resolve("broken.yaml"), "openapi: [3.1.0\npaths: {");
        assertThat(CheckClosedResponseObjects.run(List.of(broken.toString()), out))
                .isEqualTo(2);
        assertThat(printed()).startsWith("Cannot read " + broken + ": ");
    }

    private String printed() {
        return output.toString(StandardCharsets.UTF_8);
    }
}
