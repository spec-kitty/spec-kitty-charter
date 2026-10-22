package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.ConfigValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of a styleguide: a flat mirror of a {@code *.styleguide.yaml}, not the domain entity. Its
 * record equality is the round-trip fidelity assertion: lists are order- and duplicate-sensitive, and an absent
 * optional differs from a present one, including a present-empty map.
 *
 * @param id the {@code id}
 * @param schemaVersion the {@code schema_version}
 * @param title the {@code title}
 * @param scope the {@code scope} token, as plain text
 * @param principles the {@code principles}, in authored order
 * @param patterns the {@code patterns}, in authored order
 * @param antiPatterns the {@code anti_patterns}, in authored order
 * @param tooling the {@code tooling} map, when stated
 * @param qualityTest the {@code quality_test}, when stated
 * @param structuralLintConfig the {@code structural_lint_config} tree, when stated
 * @param appliesToLanguages the {@code applies_to_languages}, in authored order
 * @param references the {@code references}, in authored order with duplicates kept
 */
record StyleguideDso(
        String id,
        String schemaVersion,
        String title,
        String scope,
        List<String> principles,
        List<Pattern> patterns,
        List<AntiPattern> antiPatterns,
        Optional<Map<String, String>> tooling,
        Optional<String> qualityTest,
        Optional<ConfigValue> structuralLintConfig,
        List<String> appliesToLanguages,
        List<String> references) {

    /** Checks that the required fields are present and copies the collections. */
    StyleguideDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(qualityTest, "qualityTest");
        Objects.requireNonNull(structuralLintConfig, "structuralLintConfig");
        principles = List.copyOf(principles);
        patterns = List.copyOf(patterns);
        antiPatterns = List.copyOf(antiPatterns);
        tooling = tooling.map(map -> Collections.unmodifiableMap(new LinkedHashMap<>(map)));
        appliesToLanguages = List.copyOf(appliesToLanguages);
        references = List.copyOf(references);
    }

    /**
     * A pattern; the examples are optional.
     *
     * @param name the name
     * @param description the description
     * @param badExample the {@code bad_example}, when stated
     * @param goodExample the {@code good_example}, when stated
     */
    record Pattern(String name, String description, Optional<String> badExample, Optional<String> goodExample) {
        /** Checks that the fields are present. */
        Pattern {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(badExample, "badExample");
            Objects.requireNonNull(goodExample, "goodExample");
        }
    }

    /**
     * An anti-pattern; all four parts are required.
     *
     * @param name the name
     * @param description the description
     * @param badExample the {@code bad_example}
     * @param goodExample the {@code good_example}
     */
    record AntiPattern(String name, String description, String badExample, String goodExample) {
        /** Checks that every field is present. */
        AntiPattern {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(badExample, "badExample");
            Objects.requireNonNull(goodExample, "goodExample");
        }
    }
}
