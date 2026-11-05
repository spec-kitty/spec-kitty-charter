package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import ai.speckitty.charter.kernel.domain.ConfigValue;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A guard's embedded {@code config_schema} is a Draft 2020-12 schema checked offline (ADR-0042 S4 as amended). */
@DisplayName("Embedded config schemas are checked against the metaschema and never fetch (ADR-0054 decision 4)")
class EmbeddedSchemasTest {

    /** Only a hang cap, far above the work's time so load cannot trip it; the cause assertion is the guard (#320). */
    private static final Duration HANG_CAP = Duration.ofSeconds(30);

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
        // The linear-time engine has no backreference or lookaround: such a pattern is refused, never run on the JDK's.
        for (String uncompilable : List.of(
                "{type: object, x-check: true}",
                "{properties: {a: {pattern: '(a)\\1'}}}",
                "{patternProperties: {'^(?=a)': {type: string}}}")) {
            assertThat(EmbeddedSchemas.schemaProblems(tree(uncompilable)))
                    .as(uncompilable)
                    .singleElement()
                    .asString()
                    .startsWith("it does not compile offline (an unknown keyword, or a pattern RE2 cannot run): ");
        }
        for (String reference : List.of(
                "{$ref: 'https://example.com/evil.schema.json'}",
                "{$ref: 'https://json-schema.org/draft/2020-12/not-a-vocabulary'}",
                "{$ref: '#/$defs/missing'}",
                "{$dynamicRef: '#meta'}",
                "{$ref: '#'}",
                "{$defs: {a: {$ref: '#/$defs/b'}, b: {$ref: '#/$defs/a'}}, $ref: '#/$defs/a'}",
                "{properties: {b: {$dynamicRef: '#meta'}}}")) {
            assertThat(EmbeddedSchemas.schemaProblems(tree(reference)))
                    .as(reference)
                    .isNotEmpty()
                    .allSatisfy(problem -> assertThat(problem).startsWith("a reference keyword is not allowed"));
        }
    }

    @Test
    @DisplayName("a pack-written pattern runs in linear time, and one the engine cannot run is refused (ReDoS)")
    void aCatastrophicPatternIsEvaluatedInLinearTime() {
        ConfigValue schema = tree("{type: object, properties: {a: {type: string, pattern: '^(.*a){12}$'}}}");
        ConfigValue value = ConfigValue.of(Map.of("a", "a".repeat(40) + "!"));

        assertThat(EmbeddedSchemas.schemaProblems(schema)).isEmpty();
        assertTimeoutPreemptively(
                HANG_CAP,
                () -> assertThat(EmbeddedSchemas.valueProblems(schema, Optional.of(value)))
                        .containsExactly("pattern at /a"));
    }
}
