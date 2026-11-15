package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The {@code charter} schema against a canonical example {@code charter.yaml} (#131), and the shape of the activation
 * keys at the root of {@code charter.yaml} that the schema declared late: {@code activated_glossaries} (#131, ADR-0049),
 * {@code activated_skills} and {@code activated_anti_patterns} (#283). Like every other {@code activated_*} key each is
 * three-state: absent or {@code null}, {@code []} (none) or a list of ids.
 *
 * <p>The example is a test resource, not this repository's {@code .kittify/charter/charter.yaml}: that file is core's
 * format, written and read by the spec-kitty CLI, and the charter keeps no compatibility with it
 * (DM-01M7CEZ19K8QRA3KX68KRWX22G).
 */
@DisplayName("A canonical charter.yaml and the charter schema (#131, #283)")
class ProjectCharterYamlSchemaTest {

    private static final String CHARTER = "charter";

    /** The activation keys this test pins, each declared in the shape of the other {@code activated_*} keys. */
    private static final List<String> KEYS =
            List.of("activated_glossaries", "activated_skills", "activated_anti_patterns");

    /** The smallest charter.yaml the schema accepts: {@code catalog} is its one required key. */
    private static final String MINIMAL = """
            catalog:
              mission: software-dev
            """;

    private static List<SchemaViolation> validate(String file, String yaml) {
        return PackSchemas.bundled().validate(CHARTER, file, Yaml.load(yaml));
    }

    private static List<SchemaViolation> validate(String yaml) {
        return validate("charter.yaml", yaml);
    }

    /** The canonical example: a whole charter.yaml, every section filled, in the charter's own keys. */
    private static final String EXAMPLE = "/project-charter/charter.yaml";

    @Test
    @DisplayName("the canonical example charter.yaml validates with no finding")
    void theExampleCharterYamlValidates() throws IOException {
        String text;
        try (InputStream in = ProjectCharterYamlSchemaTest.class.getResourceAsStream(EXAMPLE)) {
            assertThat(in).as(EXAMPLE).isNotNull();
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(text).contains("\nactivated_glossaries:\n", "\n    selected_glossaries: []\n");
        assertThat(validate("charter.yaml", text)).isEmpty();
    }

    @Test
    @DisplayName("a charter.yaml without the keys is accepted")
    void absentIsAccepted() {
        assertThat(validate(MINIMAL)).isEmpty();
    }

    @Test
    @DisplayName("a catalog that carries the retired template_set is refused as an unknown field, by name (FR-010)")
    void theRetiredTemplateSetIsRefusedByName() {
        List<SchemaViolation> violations = validate("""
                catalog:
                  mission: software-dev
                  template_set: software-dev-default
                """);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.pointer()).containsExactly("catalog");
            assertThat(violation.keyword()).isEqualTo("additionalProperties");
            assertThat(violation.property()).contains("template_set");
        });
    }

    static Stream<Arguments> acceptedValues() {
        return KEYS.stream()
                .flatMap(key -> Stream.of("null", "[]", "[first-id]", "[first-id, second-id]")
                        .map(value -> Arguments.of(key, value)));
    }

    @ParameterizedTest(name = "{0}: {1} is accepted")
    @MethodSource("acceptedValues")
    void theThreeStatesAreAccepted(String key, String value) {
        assertThat(validate(MINIMAL + key + ": " + value + "\n")).isEmpty();
    }

    @Test
    @DisplayName("all three keys together are accepted")
    void allThreeKeysTogetherAreAccepted() {
        assertThat(validate(MINIMAL + """
                        activated_glossaries: [spec-kitty-core]
                        activated_skills: []
                        activated_anti_patterns: [crud-affordance-echo]
                        """)).isEmpty();
    }

    static Stream<Arguments> refusedValues() {
        return KEYS.stream()
                .flatMap(key -> Stream.of(
                        // Not a list: neither anyOf branch (array, null) matches the value.
                        Arguments.of(key, "first-id", List.of("type at /" + key, "type at /" + key)),
                        Arguments.of(key, "{first-id: true}", List.of("type at /" + key, "type at /" + key)),
                        // A list whose item is not an id string: the array branch fails at the item, the null branch
                        // at the key.
                        Arguments.of(key, "[{id: first-id}]", List.of("type at /" + key + "/0", "type at /" + key)),
                        Arguments.of(key, "[1]", List.of("type at /" + key + "/0", "type at /" + key))));
    }

    /** Refused at the key (one violation per anyOf branch), never as an unknown root key. */
    @ParameterizedTest(name = "{0}: {1} is refused with {2}")
    @MethodSource("refusedValues")
    void anythingButAListOfIdsIsRefused(String key, String value, List<String> expected) {
        assertThat(at(validate(MINIMAL + key + ": " + value + "\n"))).containsExactlyInAnyOrderElementsOf(expected);
    }

    /**
     * Every key of the flat activation vocabulary is declared, as the charter's model names it (core's {@code
     * ACTIVATION_YAML_KEYS} at core {@code 512567ee}, with its glossary key renamed {@code activated_glossaries},
     * ADR-0049, and without {@code activated_mission_step_contracts}, whose kind slice F retires; core aligns later):
     * the closed root would refuse an undeclared one as an unknown key.
     */
    @ParameterizedTest(name = "{0} is declared")
    @ValueSource(
            strings = {
                "activated_kinds",
                "activated_directives",
                "activated_tactics",
                "activated_styleguides",
                "activated_toolguides",
                "activated_paradigms",
                "activated_procedures",
                "activated_agent_profiles",
                "activated_glossaries",
                "activated_skills",
                "mission_type_activations",
                "activated_anti_patterns",
            })
    void everyKeyOfCoresActivationVocabularyIsDeclared(String key) {
        assertThat(validate(MINIMAL + key + ": []\n")).isEmpty();
    }

    @ParameterizedTest(name = "{0} is not a declared key")
    @ValueSource(
            strings = {
                "activated_glossary",
                "activated_skill",
                "activated_antipatterns",
                "activation",
                // The step contract is retired (slice F, #514): its activation key is no key of the charter any more.
                "activated_mission_step_contracts"
            })
    void aMisspelledKeyIsStillRefused(String key) {
        assertThat(at(validate(MINIMAL + key + ": []\n"))).containsExactly("additionalProperties at / [" + key + "]");
    }

    /**
     * The glossary_pack keys are replaced outright by the glossary ones (ADR-0049), with no reader of the old shape: a
     * charter.yaml that still carries one is refused as an unknown key, at the root and in the governance selection.
     */
    @Test
    @DisplayName("the replaced glossary_pack keys are refused as unknown keys")
    void theReplacedGlossaryPackKeysAreRefused() {
        assertThat(at(validate(MINIMAL + "activated_glossary_packs: []\n")))
                .containsExactly("additionalProperties at / [activated_glossary_packs]");
        assertThat(validate(MINIMAL + "activated_glossaries: []\n")).isEmpty();
    }

    /** Each violation as {@code <keyword> at /<pointer>}, with {@code [<property>]} when it names one. */
    private static List<String> at(List<SchemaViolation> violations) {
        return violations.stream()
                .map(v -> v.keyword() + " at /" + String.join("/", v.pointer())
                        + v.property().map(p -> " [" + p + "]").orElse(""))
                .toList();
    }
}
