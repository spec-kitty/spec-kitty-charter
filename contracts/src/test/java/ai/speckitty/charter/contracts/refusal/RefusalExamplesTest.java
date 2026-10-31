package ai.speckitty.charter.contracts.refusal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RefusalExamplesTest {

    private static final String RESPONSE_500 = "/paths/~1info/get/responses/500";

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);

    @Test
    void examplesAndDescriptionsNamingRaisedCodesPass() throws IOException, URISyntaxException {
        RefusalExamples.Report report = RefusalExamples.check(resource("raised-examples.yaml"));
        assertThat(report.violations()).isEmpty();
        assertThat(report.examples()).isEqualTo(3);
        assertThat(report.descriptions()).isEqualTo(2);
    }

    @Test
    void anExampleNamingAnUnraisedCodeIsReported() throws IOException, URISyntaxException {
        RefusalExamples.Report report = RefusalExamples.check(resource("unraised-example.yaml"));
        assertThat(report.violations())
                .contains(RESPONSE_500
                        + "/content/application~1problem+json/examples/pack-level-mismatch/value/code: the example"
                        + " names pack_level_mismatch, which CharterRefusalCode's x-refusal does not declare raised");
    }

    @Test
    void aDescriptionNamingAnUnraisedCodeIsReported() throws IOException, URISyntaxException {
        RefusalExamples.Report report = RefusalExamples.check(resource("unraised-example.yaml"));
        assertThat(report.violations())
                .contains(RESPONSE_500 + "/description: the description names pack_level_mismatch, which"
                        + " CharterRefusalCode's x-refusal does not declare raised");
        assertThat(report.violations()).hasSize(2);
    }

    @Test
    void theRaisedCodesComeFromTheXRefusalMapNotFromAList() {
        Map<String, Object> refusal = Map.of(
                "title",
                "CharterRefusal",
                "allOf",
                List.of(Map.of(
                        "title", "CharterRefusalCode", "x-refusal", Map.of("made_up_code", Map.of("raised", true)))));
        Map<String, Object> document = Map.of(
                "paths",
                Map.of(
                        "/x",
                        Map.of(
                                "get",
                                Map.of(
                                        "responses",
                                        Map.of(
                                                "500",
                                                Map.of(
                                                        "description",
                                                        "Refused as made_up_code.",
                                                        "content",
                                                        Map.of(
                                                                "application/problem+json",
                                                                Map.of(
                                                                        "schema",
                                                                        refusal,
                                                                        "example",
                                                                        Map.of("code", "made_up_code")))))))));
        RefusalExamples.Report report = RefusalExamples.check(document);
        assertThat(report.violations()).isEmpty();
        assertThat(report.examples()).isEqualTo(1);
    }

    @Test
    void aDocumentWithoutAnXRefusalMapIsReported() {
        RefusalExamples.Report report = RefusalExamples.check(Map.of("paths", Map.of()));
        assertThat(report.violations())
                .containsExactly("/: the document has no CharterRefusalCode schema with an x-refusal map");
    }

    @Test
    void theCommandLinePassesARaisedBundle() throws URISyntaxException {
        int code = RefusalExamples.run(List.of(resource("raised-examples.yaml").toString()), out);
        assertThat(code).isZero();
        assertThat(printed())
                .isEqualTo("All 3 refusal examples and 2 response descriptions name raised refusal codes only.\n");
    }

    @Test
    void theCommandLineFailsWithOneLinePerViolation() throws URISyntaxException {
        int code = RefusalExamples.run(List.of(resource("unraised-example.yaml").toString()), out);
        assertThat(code).isEqualTo(1);
        assertThat(printed())
                .startsWith("2 refusal examples or descriptions name a code that is not raised:\n" + RESPONSE_500);
    }

    @Test
    void aMissingArgumentIsAUsageError() {
        assertThat(RefusalExamples.run(List.of(), out)).isEqualTo(2);
        assertThat(printed()).startsWith("usage:");
    }

    @Test
    void aFileThatIsNotYamlCannotBeRead(@TempDir Path dir) throws IOException {
        Path broken = Files.writeString(dir.resolve("broken.yaml"), "openapi: [3.1.0\npaths: {");
        assertThat(RefusalExamples.run(List.of(broken.toString()), out)).isEqualTo(2);
        assertThat(printed()).startsWith("Cannot read " + broken + ": ");
    }

    private Path resource(String name) throws URISyntaxException {
        return Path.of(getClass().getResource("/refusal/" + name).toURI());
    }

    private String printed() {
        return output.toString(StandardCharsets.UTF_8);
    }
}
