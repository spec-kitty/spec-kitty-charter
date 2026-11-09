package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the {@code step-enhancement} schema tells an author in words stays said (FR-012): no schema keyword says which
 * faults the schema refuses and which a semantic check beside it refuses as {@code prompt_fragment_invalid}, so the
 * descriptions do, and these pins are the positive control that they still do. Descriptions only: the properties are
 * {@code PromptFragmentContractsTest}'s.
 */
class StepEnhancementSchemaDescriptionTest {

    private static JsonNode schema() throws IOException {
        try (InputStream in = StepEnhancementSchemaDescriptionTest.class.getResourceAsStream(
                "/packs/schemas/step-enhancement.schema.json")) {
            assertThat(in).isNotNull();
            return JsonMapper.builder().build().readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String describe(JsonNode node) {
        return node.path("description").asString();
    }

    @Test
    void theSchemaSaysWhichFaultsItRefusesAndWhichAreSemanticChecksUnderPromptFragmentInvalid() throws IOException {
        String description = describe(schema());

        assertThat(description)
                .as("operator ruling A1: the stem rule is a schema violation, as the slot pattern and minItems are")
                .contains("the store's stem rule refuses an id that differs from its file stem as a schema violation")
                .contains("a slot that breaks <namespace>:<name>")
                .contains("prompt_fragments: [] (minItems)")
                .as("the semantic checks, each answering prompt_fragment_invalid")
                .contains("with prompt_fragment_invalid")
                .contains("a slot that occurs twice")
                .contains("a companion that is missing, shared by two entries or named by none")
                .contains("a fragment that is blank, has frontmatter or holds a slot marker")
                .contains("without exactly one enhances edge to an action:<type>/<step>")
                .contains("an overrides edge (an enhancement only adds)")
                .contains("whose <type> directory differs from that edge's type")
                .contains("whose id repeats across type directories")
                .contains("an absent prompt_fragments key")
                .as("guards and emits are fields built under #434, not edges")
                .contains("add_guards, add_lifecycle_guards and emits")
                .contains("#434");
    }

    @Test
    void theDescriptionNoLongerListsTheIdStemRuleAmongThePromptFragmentInvalidRules() throws IOException {
        String description = describe(schema());

        assertThat(description)
                .doesNotContain("A semantic check beside this schema requires the id to equal the file stem")
                .doesNotContain("requires the id to equal the file stem");
        assertThat(describe(schema().path("properties").path("id")))
                .as("the id property keeps saying it equals its file stem")
                .contains("equal to its file stem");
    }

    @Test
    void thePromptFragmentsDescriptionSaysTheEmptyListIsTheSchemasAndTheAbsentKeyIsARule() throws IOException {
        String fragments = describe(schema().path("properties").path("prompt_fragments"));

        assertThat(fragments)
                .contains("An empty list is refused by this schema (minItems)")
                .contains("an absent key is refused by a semantic check as prompt_fragment_invalid")
                .contains("Each slot id occurs at most once");
    }

    @Test
    void thePathDescriptionSaysOneCompanionPerEntryAndNamesNoIdBoundStem() throws IOException {
        String path = describe(schema().path("properties")
                .path("prompt_fragments")
                .path("items")
                .path("properties")
                .path("path"));

        assertThat(path)
                .contains("one companion per entry, any stem matching the pattern, each named by exactly one entry")
                .doesNotContain("the <id>.prompt-fragment.md companion");
    }
}
