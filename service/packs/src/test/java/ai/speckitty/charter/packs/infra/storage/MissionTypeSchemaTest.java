package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The sanitized {@code mission-type} schema (ADR-0044 decision 3): a mission type in the sanitized shape validates, and
 * each retired, variant or unknown name is refused. Every refused case is a twin of the accepted fixture with exactly
 * one text replaced, so a refusal can only come from that one change.
 */
@DisplayName("Sanitized mission-type schema (#194)")
class MissionTypeSchemaTest {

    private static final String KIND = "mission-type";

    private static String accepted() throws IOException {
        try (InputStream in = MissionTypeSchemaTest.class.getResourceAsStream(
                "/schema-fixtures/mission-type/accepted.mission-type.yaml")) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> facts(String yaml) {
        List<SchemaViolation> violations =
                PackSchemas.bundled().validate(KIND, "research.mission-type.yaml", Yaml.load(yaml));
        return violations.stream()
                .map(v -> v.keyword() + " /" + String.join("/", v.pointer()) + " "
                        + v.property().orElse("-"))
                .sorted()
                .toList();
    }

    @Test
    @DisplayName("a mission type in the sanitized shape, carrying every field, validates")
    void theSanitizedMissionTypeValidates() throws IOException {
        assertThat(facts(accepted())).isEmpty();
    }

    @Test
    @DisplayName("the smallest mission type validates: one sequenced step, no optional field")
    void theSmallestMissionTypeValidates() {
        String yaml = """
                schema_version: "1.0"
                id: plan
                title: Plan
                description: Plan a change.
                steps:
                - id: specify
                  title: Specify
                """;

        assertThat(facts(yaml)).isEmpty();
    }

    @Test
    @DisplayName("a mission type with no step is refused")
    void aMissionTypeWithNoStepIsRefused() {
        String yaml = """
                schema_version: "1.0"
                id: plan
                title: Plan
                description: Plan a change.
                steps: []
                """;

        assertThat(facts(yaml)).containsExactly("minItems /steps -");
    }

    @Test
    @DisplayName("the step description bound counts code points: 160 astral characters pass, 161 do not")
    void theDescriptionBoundCountsCodePoints() throws IOException {
        String rocket = new String(Character.toChars(0x1F680));
        String accepted = accepted();
        String anchor = "description: Define the research question.";

        assertThat(facts(accepted.replace(anchor, "description: " + rocket.repeat(160))))
                .isEmpty();
        assertThat(facts(accepted.replace(anchor, "description: " + rocket.repeat(161))))
                .containsExactly("maxLength /steps/0/description -");
    }

    @ParameterizedTest(name = "[{0}] is refused with: {2}")
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '~',
            value = {
                "title is display_name    | 'title: Research\\n' >> 'display_name: Research\\n'"
                        + " | additionalProperties / display_name; required / title",
                "integer schema version   | 'schema_version: \"1.0\"' >> 'schema_version: 1'"
                        + " | const /schema_version -; type /schema_version -",
                "requires spec false      | 'requires_substantive_spec: true' >> 'requires_substantive_spec: false'"
                        + " | const /requires_substantive_spec -",
                "unknown path convention  | '  data: data/' >> '  sources: sources/'"
                        + " | additionalProperties /path_conventions sources",
                "agent profile on a step  | '  role: researcher' >> '  agent_profile: researcher-robbie'"
                        + " | additionalProperties /steps/0 agent_profile",
                "depends_on on a step     | '- id: methodology\\n' >> '- id: methodology\\n  depends_on: [scoping]\\n'"
                        + " | additionalProperties /steps/1 depends_on",
                "sequence index on a step | '- id: methodology\\n' >> '- id: methodology\\n  sequence_index: 2\\n'"
                        + " | additionalProperties /steps/1 sequence_index",
                "uppercase role           | 'role: researcher' >> 'role: Researcher'" + " | pattern /steps/0/role -",
                "prompt in another place  | 'steps/scoping.prompt.md' >> 'scoping/prompt.md'"
                        + " | pattern /steps/0/prompt_path -",
                "blocking false           | 'blocking: true\\n    template_path' >> 'blocking: false\\n    template_path'"
                        + " | const /steps/0/expected_output_artifacts/0/blocking -",
                "key is the retired id    | '  - artifact_key: output.spec.research' >> '  - id: output.spec.research'"
                        + " | additionalProperties /steps/0/expected_output_artifacts/0 id;"
                        + " required /steps/0/expected_output_artifacts/0 artifact_key",
                "undotted artifact key    | 'artifact_key: output.spec.research' >> 'artifact_key: spec'"
                        + " | pattern /steps/0/expected_output_artifacts/0/artifact_key -",
                "unknown artifact class   | 'artifact_class: output' >> 'artifact_class: policy'"
                        + " | enum /steps/0/expected_output_artifacts/0/artifact_class -",
                "input with a class       | '    path_pattern: charter.md' >> '    artifact_class: input\\n    path_pattern: charter.md'"
                        + " | additionalProperties /steps/0/expected_input_artifacts/0 artifact_class",
                "input with a template    | '    path_pattern: charter.md' >> '    template_path: templates/c.md\\n    path_pattern: charter.md'"
                        + " | additionalProperties /steps/0/expected_input_artifacts/0 template_path",
                "retired expected list    | '  expected_input_artifacts:' >> '  expected_artifacts:'"
                        + " | additionalProperties /steps/0 expected_artifacts",
                "path pattern leaves      | 'path_pattern: spec.md' >> 'path_pattern: ../spec.md'"
                        + " | not /steps/0/expected_output_artifacts/0/path_pattern -",
                "absolute path pattern    | 'path_pattern: spec.md' >> 'path_pattern: /spec.md'"
                        + " | pattern /steps/0/expected_output_artifacts/0/path_pattern -",
                "template outside         | 'templates/research-spec-template.md' >> 'templates/../x.md'"
                        + " | not /steps/0/expected_output_artifacts/0/template_path -",
                "profile reference        | 'type: tactic' >> 'type: agent_profile'"
                        + " | enum /steps/0/references/0/type -",
                "empty on-demand list     | 'on_demand_steps:\\n- id: retrospect\\n  title: Retrospective\\n  role: facilitator\\n'"
                        + " >> 'on_demand_steps: []\\n' | minItems /on_demand_steps -",
                "empty path conventions   | '  workspace: research/\\n  data: data/\\n' >> '  {}\\n'"
                        + " | minProperties /path_conventions -",
                "guidelines in another place | 'steps/scoping.guidelines.md' >> 'scoping/guidelines.md'"
                        + " | pattern /steps/0/guidelines_path -",
                "unknown key on a reference | '  reason: the spec is the contract' >> '  remarks: the spec is the contract'"
                        + " | additionalProperties /references/0 remarks",
                "empty step references    | '  references:\\n  - type: tactic\\n    id: requirements-validation-workflow\\n'"
                        + " >> '  references: []\\n' | minItems /steps/0/references -",
                "step id with a slash     | '- id: methodology' >> '- id: methodology/x'" + " | pattern /steps/1/id -",
                "type id with a digit     | 'id: research\\n' >> 'id: 9research\\n'" + " | pattern /id -",
                "step description too long | 'description: Define the research question.'"
                        + " >> 'description: "
                        + "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
                        + "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
                        + "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
                        + "' | maxLength /steps/0/description -",
            })
    void eachTwinIsRefusedForItsOneChange(String name, String change, String expected) throws IOException {
        int arrow = change.indexOf(" >> ");
        String from = unquote(change.substring(0, arrow));
        String to = unquote(change.substring(arrow + " >> ".length()));
        String original = accepted();
        assertThat(original)
                .as("the twin's anchor is in the accepted fixture once")
                .containsOnlyOnce(from);

        assertThat(facts(original.replace(from, to))).containsExactlyElementsOf(List.of(expected.split("; ")));
    }

    private static String unquote(String text) {
        String trimmed = text.strip();
        return trimmed.substring(1, trimmed.length() - 1).replace("\\n", "\n");
    }
}
