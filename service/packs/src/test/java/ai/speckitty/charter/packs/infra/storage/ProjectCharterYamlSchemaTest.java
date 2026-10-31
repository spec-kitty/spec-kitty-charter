package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The {@code charter} schema against the project's own {@code .kittify/charter/charter.yaml} (#131), and the shape of
 * {@code activated_glossary_packs}: like every other {@code activated_*} key it is three-state, absent or {@code null}
 * (unrestricted), {@code []} (none) or a list of glossary pack ids, which is what core's activation writer puts at the
 * root of {@code charter.yaml}.
 */
@DisplayName("The project charter.yaml and the charter schema (#131)")
class ProjectCharterYamlSchemaTest {

    private static final String CHARTER = "charter";

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
    @DisplayName("an absent activated_glossary_packs is accepted (unrestricted)")
    void absentIsAccepted() {
        assertThat(validate(MINIMAL)).isEmpty();
    }

    @ParameterizedTest(name = "activated_glossary_packs: {0} is accepted")
    @ValueSource(strings = {"null", "[]", "[spec-kitty-core]", "[spec-kitty-core, spk-internal]"})
    void theThreeStatesAreAccepted(String value) {
        assertThat(validate(MINIMAL + "activated_glossary_packs: " + value + "\n"))
                .isEmpty();
    }

    /** Refused at the key (one violation per anyOf branch), never as an unknown root key. */
    @ParameterizedTest(name = "activated_glossary_packs: {0} is refused with {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                // Not a list: neither anyOf branch (array, null) matches the value.
                "spec-kitty-core|type at /activated_glossary_packs;type at /activated_glossary_packs",
                "'{spec-kitty-core: true}'|type at /activated_glossary_packs;type at /activated_glossary_packs",
                // A list whose item is not an id string: the array branch fails at the item, the null branch at the
                // key.
                "'[{id: spec-kitty-core}]'|type at /activated_glossary_packs/0;type at /activated_glossary_packs",
                "'[1]'|type at /activated_glossary_packs/0;type at /activated_glossary_packs",
            })
    void anythingButAListOfIdsIsRefused(String value, String expected) {
        assertThat(at(validate(MINIMAL + "activated_glossary_packs: " + value + "\n")))
                .containsExactlyInAnyOrder(expected.split(";"));
    }

    /** Each violation as {@code <keyword> at /<pointer>}, with {@code [<property>]} when it names one. */
    private static List<String> at(List<SchemaViolation> violations) {
        return violations.stream()
                .map(v -> v.keyword() + " at /" + String.join("/", v.pointer())
                        + v.property().map(p -> " [" + p + "]").orElse(""))
                .toList();
    }
}
