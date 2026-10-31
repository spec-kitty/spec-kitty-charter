package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The {@code charter} schema against the project's own {@code .kittify/charter/charter.yaml} (#131), and the shape of
 * the activation keys core's activation writer puts at the root of {@code charter.yaml} that the schema declared late:
 * {@code activated_glossary_packs} (#131), {@code activated_skills} and {@code activated_anti_patterns} (#283). Like
 * every other {@code activated_*} key each is three-state: absent or {@code null}, {@code []} (none) or a list of ids.
 */
@DisplayName("The project charter.yaml and the charter schema (#131, #283)")
class ProjectCharterYamlSchemaTest {

    private static final String CHARTER = "charter";

    /** The activation keys this test pins, each declared in the shape of the other {@code activated_*} keys. */
    private static final List<String> KEYS =
            List.of("activated_glossary_packs", "activated_skills", "activated_anti_patterns");

    /** The smallest charter.yaml the schema accepts: {@code catalog} is its one required key. */
    private static final String MINIMAL = """
            catalog:
              mission: software-dev
              template_set: software-dev-default
            """;

    private static List<SchemaViolation> validate(String file, String yaml) {
        return PackSchemas.bundled().validate(CHARTER, file, Yaml.load(yaml));
    }

    private static List<SchemaViolation> validate(String yaml) {
        return validate("charter.yaml", yaml);
    }

    @Test
    @DisplayName("this repository's .kittify/charter/charter.yaml validates with no finding")
    void theRepositoryCharterYamlValidates() throws IOException {
        String root = System.getProperty("charter.repositoryRoot");
        assertThat(root).as("system property charter.repositoryRoot").isNotNull();
        Path charterYaml = Path.of(root, ".kittify", "charter", "charter.yaml");
        String text = Files.readString(charterYaml, StandardCharsets.UTF_8);

        assertThat(validate(".kittify/charter/charter.yaml", text)).isEmpty();
    }

    @Test
    @DisplayName("a charter.yaml without the keys is accepted")
    void absentIsAccepted() {
        assertThat(validate(MINIMAL)).isEmpty();
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
                        activated_glossary_packs: [spec-kitty-core]
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
     * Every key of core's flat activation vocabulary, {@code ACTIVATION_YAML_KEYS} ({@code
     * src/charter/activation/pack_manager.py:205} at core {@code 512567ee}), is declared: the closed root would refuse
     * an undeclared one as an unknown key.
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
                "activated_mission_step_contracts",
                "activated_glossary_packs",
                "activated_skills",
                "mission_type_activations",
                "activated_anti_patterns",
            })
    void everyKeyOfCoresActivationVocabularyIsDeclared(String key) {
        assertThat(validate(MINIMAL + key + ": []\n")).isEmpty();
    }

    @ParameterizedTest(name = "{0} is not a declared key")
    @ValueSource(strings = {"activated_glossary_pack", "activated_skill", "activated_antipatterns", "activation"})
    void aMisspelledKeyIsStillRefused(String key) {
        assertThat(at(validate(MINIMAL + key + ": []\n"))).containsExactly("additionalProperties at / [" + key + "]");
    }

    /** Each violation as {@code <keyword> at /<pointer>}, with {@code [<property>]} when it names one. */
    private static List<String> at(List<SchemaViolation> violations) {
        return violations.stream()
                .map(v -> v.keyword() + " at /" + String.join("/", v.pointer())
                        + v.property().map(p -> " [" + p + "]").orElse(""))
                .toList();
    }
}
