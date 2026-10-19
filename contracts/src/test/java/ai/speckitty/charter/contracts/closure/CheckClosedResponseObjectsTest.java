package ai.speckitty.charter.contracts.closure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckClosedResponseObjectsTest {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);

    @Test
    void aClosedBundlePassesAndSaysHowManyResponseSchemasItJudged() {
        int code = CheckClosedResponseObjects.run(List.of(System.getProperty("charter.lintCleanFixture")), out);
        assertThat(code).isZero();
        assertThat(printed()).isEqualTo("All 4 response schemas are closed.\n");
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

    private String printed() {
        return output.toString(StandardCharsets.UTF_8);
    }
}
