package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every keyword the bundled schemas use is accounted for (T024): it has a row in {@link PackPolicy}'s detail
 * vocabulary, or it is declared here as one that never reports a violation of its own (an annotation, or an
 * applicator whose children report), or as one that gets the fixed locale-free fallback text. A schema that starts to
 * use a new keyword fails this test until someone decides which of the three it is.
 */
class SchemaKeywordCoverageTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Keywords whose value maps names to schemas. */
    private static final Set<String> NAMED_SCHEMAS =
            Set.of("properties", "$defs", "definitions", "patternProperties", "dependentSchemas");

    /** Keywords whose value is one schema. */
    private static final Set<String> ONE_SCHEMA = Set.of(
            "items",
            "additionalProperties",
            "if",
            "then",
            "else",
            "not",
            "contains",
            "propertyNames",
            "unevaluatedProperties",
            "unevaluatedItems");

    /** Keywords whose value is a list of schemas. */
    private static final Set<String> SCHEMA_LISTS = Set.of("allOf", "anyOf", "oneOf", "prefixItems");

    /** Annotations and applicators: they carry no violation of their own; their children report. */
    private static final Set<String> NO_VIOLATION_OF_THEIR_OWN = Set.of(
            "$schema",
            "$id",
            "$ref",
            "$defs",
            "title",
            "description",
            "default",
            "properties",
            "items",
            "allOf",
            "if",
            "then");

    /** Keywords whose violation gets the fixed fallback text, {@code <o>: '<pointer>' breaks schema keyword '<k>'}. */
    private static final Set<String> FIXED_FALLBACK = Set.of("anyOf", "oneOf", "not", "maxLength", "uniqueItems");

    private static void collect(JsonNode schema, String where, Map<String, Set<String>> found) {
        if (!schema.isObject()) {
            return;
        }
        schema.properties().forEach(entry -> {
            String keyword = entry.getKey();
            found.computeIfAbsent(keyword, k -> new TreeSet<>()).add(where);
            JsonNode value = entry.getValue();
            if (NAMED_SCHEMAS.contains(keyword)) {
                value.properties().forEach(named -> collect(named.getValue(), where, found));
            } else if (ONE_SCHEMA.contains(keyword)) {
                collect(value, where, found);
            } else if (SCHEMA_LISTS.contains(keyword)) {
                value.forEach(child -> collect(child, where, found));
            }
        });
    }

    private static Map<String, Set<String>> keywordsInUse() throws IOException {
        Map<String, Set<String>> found = new TreeMap<>();
        for (String kind : PackSchemas.bundled().kinds()) {
            try (InputStream in =
                    SchemaKeywordCoverageTest.class.getResourceAsStream("/packs/schemas/" + kind + ".schema.json")) {
                assertThat(in).as(kind).isNotNull();
                collect(JSON.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8)), kind, found);
            }
        }
        return found;
    }

    @Test
    void everyKeywordInUseHasAVocabularyRowOrIsDeclaredAsNeverReportingOrAsTheFixedFallback() throws IOException {
        Map<String, Set<String>> inUse = keywordsInUse();

        assertThat(inUse).as("the walk found the schemas' keywords").hasSizeGreaterThan(15);
        for (String keyword : inUse.keySet()) {
            assertThat(PackPolicy.handles(keyword)
                            || NO_VIOLATION_OF_THEIR_OWN.contains(keyword)
                            || FIXED_FALLBACK.contains(keyword))
                    .as(
                            "keyword '%s' (used by %s) has no vocabulary row and is not declared",
                            keyword, inUse.get(keyword))
                    .isTrue();
        }
    }

    @Test
    void theDeclaredSetsAreDisjointFromTheVocabularyAndEveryRowIsUsedBySomeSchema() throws IOException {
        Set<String> used = keywordsInUse().keySet();

        for (String keyword : NO_VIOLATION_OF_THEIR_OWN) {
            assertThat(PackPolicy.handles(keyword))
                    .as("'%s' is declared silent but has a row", keyword)
                    .isFalse();
        }
        for (String keyword : FIXED_FALLBACK) {
            assertThat(PackPolicy.handles(keyword))
                    .as("'%s' is declared fallback but has a row", keyword)
                    .isFalse();
        }
        for (String row : Set.of(
                "type",
                "additionalProperties",
                "required",
                "minLength",
                "pattern",
                "enum",
                "const",
                "minItems",
                "minimum",
                "maximum",
                "exclusiveMinimum")) {
            assertThat(PackPolicy.handles(row)).as("'%s' has a row", row).isTrue();
            assertThat(used).as("a schema uses '%s'", row).contains(row);
        }
    }

    @Test
    void aKeywordNoSchemaUsesYetHasNoRow() {
        assertThat(PackPolicy.handles("dependentRequired")).isFalse();
        assertThat(PackPolicy.handles("format")).isFalse();
        assertThat(PackPolicy.handles("")).isFalse();
    }
}
