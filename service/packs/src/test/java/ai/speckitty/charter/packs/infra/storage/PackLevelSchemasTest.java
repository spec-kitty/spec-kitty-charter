package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The sanitized pack-level shapes (#195, ADR-0042): {@code preset.schema.json} and {@code pack-manifest.schema.json} are
 * closed, accept the designed shape and refuse every other spelling. Each refusal is paired with the clean document it
 * departs from, so a schema that accepted everything, or refused everything, fails here.
 */
class PackLevelSchemasTest {

    private static final PackSchemas SCHEMAS = new PackSchemas("packs/schemas/", List.of("preset", "pack-manifest"));

    private static final String HASH = "a".repeat(64);

    /** A preset using every key of the shape, with and without the must-have mark. */
    private static final String PRESET = """
            schema_version: "1.0"
            id: house-rules
            description: The organisation's house rules.
            activated_kinds: [directives, tactics, mission_step_contracts, skills]
            activated_directives:
              - id: GUARD
                must_have: true
              - id: STYLE
            activated_tactics: [{id: acceptance-test-first}]
            activated_styleguides: [{id: s}]
            activated_toolguides: [{id: t}]
            activated_paradigms: [{id: p}]
            activated_procedures: [{id: q}]
            activated_agent_profiles: [{id: a}]
            activated_mission_step_contracts: [{id: m}]
            activated_anti_patterns: [{id: x}]
            activated_mission_types: [{id: software-dev, must_have: true}]
            """;

    private static final String MANIFEST = """
            schema_version: "1.0"
            manifest_hash: %1$s
            constituents:
              - kind: directive
                id: DIRECTIVE_001
                path: directives/001-a.directive.yaml
                content_hash: %1$s
            presets:
              - id: minimal
                path: presets/minimal.yaml
                content_hash: %1$s
            """.formatted(HASH);

    private static List<SchemaViolation> preset(String text) {
        return SCHEMAS.validate("preset", "presets/house-rules.yaml", Yaml.load(text));
    }

    private static List<SchemaViolation> manifest(String text) {
        return SCHEMAS.validate("pack-manifest", "pack-manifest.yaml", Yaml.load(text));
    }

    private static List<String> keywords(List<SchemaViolation> violations) {
        return violations.stream().map(SchemaViolation::keyword).distinct().toList();
    }

    /** Each violation as {@code <keyword> at /<pointer>}, with {@code [<property>]} when it names one. */
    private static List<String> at(List<SchemaViolation> violations) {
        return violations.stream()
                .map(v -> v.keyword() + " at /" + String.join("/", v.pointer())
                        + v.property().map(p -> " [" + p + "]").orElse(""))
                .toList();
    }

    // ---- preset -----------------------------------------------------------------------------------------------

    @Test
    void theDesignedPresetIsAccepted() {
        assertThat(preset(PRESET)).isEmpty();
    }

    @Test
    void theSmallestPresetIsAccepted() {
        assertThat(preset("schema_version: \"1.0\"\nid: none\ndescription: Selects nothing.\n"))
                .isEmpty();
    }

    @ParameterizedTest(name = "{0} is refused naming {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                // A legacy or variant key is not part of the shape (S3, S5).
                "'name: house-rules'|name",
                "'required_directives: [GUARD]'|required_directives",
                "'mission_type_activations: [software-dev]'|mission_type_activations",
                "'activations: {}'|activations",
                "'activated_skills: [{id: s}]'|activated_skills",
                "'activated_glossary_packs: [{id: g}]'|activated_glossary_packs",
            })
    void anUnknownTopLevelKeyIsRefused(String extra, String key) {
        assertThat(preset(PRESET)).isEmpty();
        assertThat(at(preset(PRESET + extra + "\n"))).containsExactly("additionalProperties at / [" + key + "]");
    }

    @Test
    void mustHaveHasNoFalseSpelling() {
        assertThat(preset(PRESET)).isEmpty();
        String withFalse = PRESET.replace("  - id: STYLE\n", "  - id: STYLE\n    must_have: false\n");
        assertThat(at(preset(withFalse))).containsExactly("const at /activated_directives/1/must_have");
    }

    @Test
    void anEmptyActivationListIsRefusedWhereLeavingItOutIsAccepted() {
        String without = PRESET.replace("activated_tactics: [{id: acceptance-test-first}]\n", "");
        assertThat(preset(without)).isEmpty();
        assertThat(at(preset(
                        PRESET.replace("activated_tactics: [{id: acceptance-test-first}]", "activated_tactics: []"))))
                .containsExactly("minItems at /activated_tactics");
    }

    @Test
    void anItemIsAnObjectWithAnIdAndNothingElse() {
        assertThat(preset(PRESET)).isEmpty();
        assertThat(at(preset(PRESET.replace("[{id: acceptance-test-first}]", "[acceptance-test-first]"))))
                .containsExactly("type at /activated_tactics/0");
        assertThat(at(preset(PRESET.replace("{id: acceptance-test-first}", "{id: a, weight: 1}"))))
                .containsExactly("additionalProperties at /activated_tactics/0 [weight]");
        assertThat(keywords(preset(PRESET.replace("{id: acceptance-test-first}", "{must_have: true}"))))
                .containsExactly("required");
        assertThat(preset(PRESET.replace("{id: acceptance-test-first}", "{must_have: true}")))
                .extracting(SchemaViolation::pointer)
                .containsOnly(List.of("activated_tactics", "0"));
    }

    @Test
    void theKindGateNamesKindPluralsOnceAndIsNeverEmpty() {
        assertThat(preset(PRESET)).isEmpty();
        assertThat(at(preset(PRESET.replace("tactics, mission_step", "tactic, mission_step"))))
                .containsExactly("enum at /activated_kinds/1");
        assertThat(at(preset(PRESET.replace("[directives, tactics,", "[directives, directives,"))))
                .containsExactly("uniqueItems at /activated_kinds");
        assertThat(at(preset(PRESET.replace("[directives, tactics, mission_step_contracts, skills]", "[]"))))
                .containsExactly("minItems at /activated_kinds");
    }

    @Test
    void theVersionIsTheString10() {
        assertThat(preset(PRESET)).isEmpty();
        // A number breaks both the type and the constant at the one field.
        assertThat(at(preset(PRESET.replace("schema_version: \"1.0\"", "schema_version: 1.0"))))
                .containsExactlyInAnyOrder("type at /schema_version", "const at /schema_version");
        assertThat(at(preset(PRESET.replace("schema_version: \"1.0\"", "schema_version: \"1\""))))
                .containsExactly("const at /schema_version");
        assertThat(at(preset(PRESET.replace("schema_version: \"1.0\"\n", ""))))
                .containsExactly("required at / [schema_version]");
    }

    @ParameterizedTest(name = "id {0} is refused")
    @CsvSource({"house.rules", "House-rules", "1st", "house--rules", "house-", "''"})
    void anIdOutsideTheGrammarIsRefused(String id) {
        assertThat(preset(PRESET)).isEmpty();
        assertThat(at(preset(PRESET.replace("id: house-rules", "id: '" + id + "'"))))
                .containsExactly("pattern at /id");
    }

    @Test
    void anIdOfSixtyFourCharactersIsTheLongest() {
        assertThat(preset(PRESET.replace("id: house-rules", "id: " + "a".repeat(64))))
                .isEmpty();
        assertThat(at(preset(PRESET.replace("id: house-rules", "id: " + "a".repeat(65)))))
                .containsExactly("maxLength at /id");
    }

    @Test
    void aPresetIsAMappingWithADescription() {
        assertThat(at(preset(""))).containsExactly("type at /");
        assertThat(at(preset(PRESET.replace("description: The organisation's house rules.", "description: ''"))))
                .containsExactly("minLength at /description");
    }

    // ---- pack manifest ----------------------------------------------------------------------------------------

    @Test
    void theDesignedManifestIsAccepted() {
        assertThat(manifest(MANIFEST)).isEmpty();
    }

    @Test
    void aManifestWithoutPresetsOrConstituentsIsAccepted() {
        String bare = "schema_version: \"1.0\"\nmanifest_hash: " + HASH + "\nconstituents: []\n";
        assertThat(manifest(bare)).isEmpty();
    }

    @ParameterizedTest(name = "{0} is refused")
    @CsvSource({
        "'charter: null'",
        "'fetched_at: null'",
        "'source_url: null'",
        "'source_type: built-in'",
        "'generated_at: null'",
        "'generated_by: x'"
    })
    void aFieldOfAnotherVariantIsRefused(String extra) {
        assertThat(manifest(MANIFEST)).isEmpty();
        assertThat(at(manifest(MANIFEST + extra + "\n")))
                .containsExactly("additionalProperties at / [" + extra.substring(0, extra.indexOf(':')) + "]");
    }

    @Test
    void aConstituentHasExactlyItsFourFields() {
        assertThat(manifest(MANIFEST)).isEmpty();
        String withProvenance = MANIFEST.replace(
                "path: directives/001-a.directive.yaml\n",
                "path: directives/001-a.directive.yaml\n    provenance_path: null\n");
        assertThat(at(manifest(withProvenance)))
                .containsExactly("additionalProperties at /constituents/0 [provenance_path]");
        assertThat(at(manifest(MANIFEST.replace("kind: directive", "kind: preset"))))
                .containsExactly("enum at /constituents/0/kind");
        assertThat(at(manifest(MANIFEST.replace("    id: DIRECTIVE_001\n", ""))))
                .containsExactly("required at /constituents/0 [id]");
    }

    @Test
    void presetEntriesAreKeyedByIdAndNeverAnEmptyList() {
        assertThat(manifest(MANIFEST)).isEmpty();
        assertThat(at(manifest(MANIFEST.replace("  - id: minimal", "  - name: minimal"))))
                .containsExactlyInAnyOrder("additionalProperties at /presets/0 [name]", "required at /presets/0 [id]");
        String noPresets = MANIFEST.substring(0, MANIFEST.indexOf("presets:")) + "presets: []\n";
        assertThat(at(manifest(noPresets))).containsExactly("minItems at /presets");
        assertThat(at(manifest(MANIFEST.replace("path: presets/minimal.yaml", "path: minimal.yaml"))))
                .containsExactly("pattern at /presets/0/path");
    }

    @Test
    void aHashIsSixtyFourLowerHexCharacters() {
        assertThat(at(manifest(MANIFEST.replaceFirst(HASH, "a".repeat(63)))))
                .containsExactly("pattern at /manifest_hash");
        assertThat(at(manifest(MANIFEST.replaceFirst(HASH, "A".repeat(64)))))
                .containsExactly("pattern at /manifest_hash");
    }

    @Test
    void theManifestVersionIsTheString10() {
        assertThat(at(manifest(MANIFEST.replace("schema_version: \"1.0\"", "schema_version: '1'"))))
                .containsExactly("const at /schema_version");
    }

    // ---- the pinned content -----------------------------------------------------------------------------------

    /**
     * The pinned built-in presets and manifest are not in the sanitized shape: each is refused, naming the unknown keys
     * the divergence table (research/grounding-squad.md §5) lists for it. Fixed in open-packs (OP-1, OP-2).
     */
    @Nested
    @Tag("open-packs")
    class PinnedContent {

        private String pinned(String file) throws IOException {
            Path builtIn = OpenPacksCorpus.packsDirectory().resolve("built-in");
            return Files.readString(builtIn.resolve(file), StandardCharsets.UTF_8);
        }

        @ParameterizedTest(name = "{0} is refused for {1}")
        @CsvSource({"presets/default.yaml, name", "presets/minimal.yaml, name"})
        void aPinnedPresetIsRefused(String file, String key) throws IOException {
            List<SchemaViolation> violations = preset(pinned(file));
            assertThat(violations)
                    .filteredOn(v -> v.keyword().equals("additionalProperties"))
                    .extracting(v -> v.property().orElse(""))
                    .contains(key, "mission_type_activations");
        }

        @Test
        void thePinnedManifestIsRefused() throws IOException {
            List<SchemaViolation> violations = manifest(pinned("pack-manifest.yaml"));
            assertThat(violations)
                    .filteredOn(v -> v.keyword().equals("additionalProperties")
                            && v.pointer().isEmpty())
                    .extracting(v -> v.property().orElse(""))
                    .contains("charter", "fetched_at", "source_type", "source_url", "generated_by");
        }
    }
}
