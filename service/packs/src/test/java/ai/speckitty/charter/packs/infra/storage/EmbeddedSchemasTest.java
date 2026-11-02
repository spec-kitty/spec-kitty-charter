package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ConfigValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A guard's embedded {@code config_schema} is a Draft 2020-12 schema checked offline (ADR-0042 S4 as amended). */
@DisplayName("Embedded config schemas are checked against the metaschema and never fetch (ADR-0054 decision 4)")
class EmbeddedSchemasTest {

    private static ConfigValue tree(String yaml) {
        return ConfigValue.of(Yaml.load(yaml));
    }

    @Test
    @DisplayName("a usable schema has no problem, and a value is checked against it")
    void aUsableSchemaChecksAValue() {
        ConfigValue schema = tree("{type: object, required: [carrier], properties: {carrier: {type: string}}}");

        assertThat(EmbeddedSchemas.schemaProblems(schema)).isEmpty();
        assertThat(EmbeddedSchemas.valueProblems(schema, Optional.of(ConfigValue.of(Map.of("carrier", "c.yaml")))))
                .isEmpty();
        assertThat(EmbeddedSchemas.valueProblems(schema, Optional.of(ConfigValue.of(Map.of("carrier", 3)))))
                .containsExactly("type at /carrier");
        assertThat(EmbeddedSchemas.valueProblems(schema, Optional.empty()))
                .as("an absent config is checked as an empty mapping")
                .containsExactly("required at /");
    }

    @Test
    @DisplayName(
            "a schema the metaschema refuses, of another draft, with an unknown keyword or a remote $ref is refused")
    void anUnusableSchemaIsRefused() {
        assertThat(EmbeddedSchemas.schemaProblems(tree("{type: text}")))
                .as("the metaschema refuses an unknown type")
                .isNotEmpty()
                .allSatisfy(problem -> assertThat(problem).endsWith(" at /type"));
        assertThat(EmbeddedSchemas.schemaProblems(tree("{$schema: 'http://json-schema.org/draft-07/schema#'}")))
                .containsExactly("$schema is not https://json-schema.org/draft/2020-12/schema");
        for (String unusable : List.of(
                "{type: object, x-check: true}",
                "{$ref: 'https://example.com/evil.schema.json'}",
                "{$ref: 'https://json-schema.org/draft/2020-12/not-a-vocabulary'}",
                "{$ref: '#/$defs/missing'}",
                "{$dynamicRef: '#meta'}")) {
            assertThat(EmbeddedSchemas.schemaProblems(tree(unusable)))
                    .as(unusable)
                    .singleElement()
                    .asString()
                    .startsWith("it does not compile offline (an unknown keyword or a $ref outside the document): ");
        }
    }
}
