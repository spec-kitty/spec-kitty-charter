package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * The pack mutability policy file (ADR-0063, FR-022): {@code policy.schema.json} is closed, accepts the designed shape
 * and refuses every other spelling, role locks and pins included (reserved, ADR-0030). Each refusal departs from the
 * clean document by one line, so a schema that accepted everything, or refused everything, fails here. The descriptor's
 * {@code mutability_policy} key admits only {@code policy.yaml} in this version.
 */
class PolicySchemaTest {

    private static final PackSchemas SCHEMAS = new PackSchemas("packs/schemas/", List.of("policy", "pack-descriptor"));

    private static final String VALID = """
            schema_version: 1
            kind_defaults:
              directive: extendable
              tactic: open
              mission_type: locked
            artifacts:
              directive:DIRECTIVE_001: locked
              tactic:acceptance-test-first: extendable
              template:writing/minutes.md: open
            """;

    private static final String DESCRIPTOR = """
            pack_id: 01M5DEH8R0VNDTR1PF1XTVRE0B
            pack_version: 0.1.0
            name: qa
            """;

    private static List<SchemaViolation> policy(String text) {
        return SCHEMAS.validate("policy", "policy.yaml", Yaml.load(text));
    }

    private static List<SchemaViolation> descriptor(String text) {
        return SCHEMAS.validate("pack-descriptor", "pack.yaml", Yaml.load(text));
    }

    @Test
    void theDesignedShapePassesAndSoDoesTheSmallestFile() {
        assertThat(policy(VALID)).isEmpty();
        assertThat(policy("schema_version: 1\n")).as("both maps are optional").isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "an unknown top-level key|schema_version: 1\\nowner: qa|additionalProperties",
                "role locks are reserved|schema_version: 1\\nroles: {implementer: locked}|additionalProperties",
                "pins are reserved|schema_version: 1\\npins: [directive:D1]|additionalProperties",
                "a level outside the three|schema_version: 1\\nartifacts: {directive:D1: frozen}|enum",
                "a kind default outside the three levels|schema_version: 1\\nkind_defaults: {directive: frozen}|enum",
                "a kind token that is no artifact kind|schema_version: 1\\nkind_defaults: {action: locked}|additionalProperties",
                "a plural kind token|schema_version: 1\\nkind_defaults: {directives: locked}|additionalProperties",
                "a pack-qualified URN key|schema_version: 1\\nartifacts: {'qa::directive:D1': locked}|additionalProperties",
                "a key that is no URN|schema_version: 1\\nartifacts: {D1: locked}|additionalProperties",
                "a URN of a non-artifact kind|schema_version: 1\\nartifacts: {'action:sd/plan': locked}|additionalProperties",
                "schema_version 2|schema_version: 2|const",
                "schema_version as a string|schema_version: '1'|type",
                "no schema_version|kind_defaults: {directive: locked}|required"
            })
    void eachDepartureFromTheShapeIsRefused(String why, String text, String keyword) {
        List<SchemaViolation> violations = policy(text.replace("\\n", "\n"));

        assertThat(violations).as(why).isNotEmpty();
        assertThat(violations).extracting(SchemaViolation::keyword).as(why).contains(keyword);
    }

    @Test
    void theKindDefaultsKeysAreExactlyTheDomainsArtifactKinds() throws IOException {
        Map<?, ?> schema;
        try (InputStream in = PolicySchemaTest.class.getResourceAsStream("/packs/schemas/policy.schema.json")) {
            schema = JsonMapper.builder().build().readValue(in, Map.class);
        }
        Map<?, ?> kindDefaults = (Map<?, ?>) ((Map<?, ?>) schema.get("properties")).get("kind_defaults");
        Map<?, ?> artifacts = (Map<?, ?>) ((Map<?, ?>) schema.get("properties")).get("artifacts");
        List<String> tokens =
                Arrays.stream(ArtifactKind.values()).map(ArtifactKind::token).toList();

        assertThat(((Map<?, ?>) kindDefaults.get("properties"))
                        .keySet().stream().map(String::valueOf).toList())
                .containsExactlyElementsOf(tokens);
        assertThat(((Map<?, ?>) artifacts.get("patternProperties"))
                        .keySet().stream().map(String::valueOf).toList())
                .containsExactly("^(" + String.join("|", tokens) + "):[A-Za-z0-9_/.-]+$");
    }

    @Test
    void theDescriptorNamesOnlyPolicyYamlAndTheKeyIsOptional() {
        assertThat(descriptor(DESCRIPTOR)).isEmpty();
        assertThat(descriptor(DESCRIPTOR + "mutability_policy: policy.yaml\n")).isEmpty();

        assertThat(descriptor(DESCRIPTOR + "mutability_policy: locks.yaml\n"))
                .extracting(SchemaViolation::keyword)
                .as("only policy.yaml in this version (ADR-0063 open question 1)")
                .containsExactly("const");
        assertThat(descriptor(DESCRIPTOR + "mutability_policy: policies/policy.yaml\n"))
                .extracting(SchemaViolation::keyword)
                .containsExactly("const");
    }
}
