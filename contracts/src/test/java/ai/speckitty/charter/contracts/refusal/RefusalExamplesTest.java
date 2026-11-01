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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    void aRefusalKeptAsComponentReferencesIsJudgedWhereTheOperationUsesIt() throws IOException, URISyntaxException {
        RefusalExamples.Report report = RefusalExamples.check(resource("unraised-example-by-ref.yaml"));
        assertThat(report.violations())
                .containsExactlyInAnyOrder(
                        RESPONSE_500 + "/description: the description names pack_level_mismatch, which"
                                + " CharterRefusalCode's x-refusal does not declare raised",
                        RESPONSE_500
                                + "/content/application~1problem+json/examples/pack-level-mismatch/value/code: the"
                                + " example names pack_level_mismatch, which CharterRefusalCode's x-refusal does not"
                                + " declare raised");
        assertThat(report.examples()).isEqualTo(1);
        assertThat(report.descriptions()).isEqualTo(1);
    }

    @Test
    void aReservedOperationIsNotJudgedUntilItIsBuilt() {
        Map<String, Object> refusal = Map.of(
                "title",
                "CharterRefusal",
                "allOf",
                List.of(Map.of(
                        "title",
                        "CharterRefusalCode",
                        "x-refusal",
                        Map.of("raised_code", Map.of("raised", true), "reserved_code", Map.of("raised", false)))));
        Map<String, Object> notFound = Map.of(
                "description",
                "Refused as reserved_code.",
                "content",
                Map.of(
                        "application/problem+json",
                        Map.of("schema", refusal, "example", Map.of("code", "reserved_code"))));
        Map<String, Object> reserved = Map.of(
                "x-reserved",
                Map.of("contractSet", "activation", "issue", "#125"),
                "responses",
                Map.of("404", notFound));
        Map<String, Object> built = Map.of("responses", Map.of("404", notFound));

        assertThat(RefusalExamples.check(Map.of("paths", Map.of("/r", Map.of("get", reserved))))
                        .violations())
                .as("a reserved operation answers 501, so its declared refusals are not judged yet")
                .isEmpty();
        assertThat(RefusalExamples.check(Map.of("paths", Map.of("/r", Map.of("get", built))))
                        .violations())
                .as("once built, the same refusal must name raised codes only")
                .hasSize(2);
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

    /**
     * A code is named as a whole word, in backticks or not: {@code no_pack_level_mismatch} and {@code
     * pack_level_mismatch_here} name none, a bare {@code pack_level_mismatch} names it.
     */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Refused as no_pack_level_mismatch.                 | 0",
                "Refused as pack_level_mismatch_here.               | 0",
                "Refused as pack_level_mismatches.                  | 0",
                "Refused as pack_level_mismatch.                    | 1",
                "Refused as pack_level_mismatch, or pack_unreadable. | 1",
                "Refused as (pack_level_mismatch).                  | 1"
            })
    void aDescriptionNamesACodeOnlyAsAWholeWord(String description, int violations) {
        Map<String, Object> code = Map.of(
                "title",
                "CharterRefusalCode",
                "x-refusal",
                Map.of(
                        "pack_unreadable", Map.of("raised", true),
                        "pack_level_mismatch", Map.of("raised", false)));
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
                                                        description,
                                                        "content",
                                                        Map.of(
                                                                "application/problem+json",
                                                                Map.of(
                                                                        "schema",
                                                                        Map.of(
                                                                                "title",
                                                                                "CharterRefusal",
                                                                                "allOf",
                                                                                List.of(code))))))))));
        assertThat(RefusalExamples.check(document).violations()).hasSize(violations);
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
