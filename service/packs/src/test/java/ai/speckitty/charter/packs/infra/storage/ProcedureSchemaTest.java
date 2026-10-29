package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The sanitized {@code procedure} schema (FR-001, C-004): a procedure in the sanitized shape validates, and each retired
 * or unknown name is refused. Every refused fixture is a twin of the accepted one that differs from it in exactly one
 * line, so a refusal can only come from that one property.
 */
@DisplayName("Sanitized procedure schema (#194)")
class ProcedureSchemaTest {

    private static final String KIND = "procedure";
    private static final String DIR = "/schema-fixtures/procedure/";
    private static final String ACCEPTED = "accepted";

    private static String fixture(String name) throws IOException {
        try (InputStream in = ProcedureSchemaTest.class.getResourceAsStream(DIR + name + ".procedure.yaml")) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<SchemaViolation> validate(String name) throws IOException {
        return PackSchemas.bundled().validate(KIND, name + ".procedure.yaml", Yaml.load(fixture(name)));
    }

    private static List<String> facts(List<SchemaViolation> violations) {
        return violations.stream()
                .map(v -> v.keyword() + " /" + String.join("/", v.pointer()) + " "
                        + v.property().orElse("-"))
                .sorted()
                .toList();
    }

    @Test
    @DisplayName("a procedure in the sanitized shape, carrying every field, validates")
    void theSanitizedProcedureValidates() throws IOException {
        assertThat(validate(ACCEPTED)).isEmpty();
    }

    @Test
    @DisplayName("a step without a role validates: an absent role stays absent")
    void aStepWithoutARoleValidates() throws IOException {
        String yaml = fixture(ACCEPTED).replace("  role: operator\n", "");

        assertThat(PackSchemas.bundled().validate(KIND, "x.procedure.yaml", Yaml.load(yaml)))
                .isEmpty();
    }

    @ParameterizedTest(name = "{0} is refused with: {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "refused-name-instead-of-title | additionalProperties / name; required / title",
                "refused-purpose-instead-of-description | additionalProperties / purpose; required / description",
                "refused-step-actor | additionalProperties /steps/0 actor",
                "refused-anti-pattern-name | additionalProperties /anti_patterns/0 name; required /anti_patterns/0 title",
                "refused-anti-pattern-description | additionalProperties /anti_patterns/0 description",
                "refused-unknown-top-level-key | additionalProperties / remarks",
                "refused-integer-schema-version | const /schema_version -; type /schema_version -",
                "refused-uppercase-role | pattern /steps/0/role -"
            })
    void eachTwinIsRefusedForItsOneProperty(String twin, String expected) throws IOException {
        assertThat(facts(validate(twin))).containsExactlyElementsOf(List.of(expected.split("; ")));
    }

    @ParameterizedTest(name = "{0} differs from the accepted fixture in exactly one line")
    @CsvSource({
        "refused-name-instead-of-title",
        "refused-purpose-instead-of-description",
        "refused-step-actor",
        "refused-anti-pattern-name",
        "refused-anti-pattern-description",
        "refused-unknown-top-level-key",
        "refused-integer-schema-version",
        "refused-uppercase-role"
    })
    void eachTwinDiffersFromTheAcceptedFixtureInExactlyOneLine(String twin) throws IOException {
        List<String> accepted = fixture(ACCEPTED).lines().toList();
        List<String> refused = fixture(twin).lines().toList();

        assertThat(refused).hasSameSizeAs(accepted);
        List<Integer> differing = new ArrayList<>();
        for (int i = 0; i < accepted.size(); i++) {
            if (!accepted.get(i).equals(refused.get(i))) {
                differing.add(i);
            }
        }
        assertThat(differing).as("lines that differ").hasSize(1);
    }

    @Test
    @DisplayName("the retired procedure shape is refused: name, purpose and step actor are no longer accepted")
    void theRetiredShapeIsRefused() {
        String retired = """
                schema_version: "1.0"
                id: old-procedure
                name: Old
                purpose: The pre-sanitized shape.
                entry_condition: e
                exit_condition: x
                steps:
                - title: s
                  actor: human
                """;

        assertThat(PackSchemas.bundled().validate(KIND, "x.procedure.yaml", Yaml.load(retired)))
                .extracting(SchemaViolation::property)
                .contains(Optional.of("name"), Optional.of("purpose"), Optional.of("actor"));
    }
}
