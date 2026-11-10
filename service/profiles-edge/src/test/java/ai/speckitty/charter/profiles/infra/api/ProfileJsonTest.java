package ai.speckitty.charter.profiles.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.AgentProfile;
import ai.speckitty.charter.api.model.AgentProfileExcluding;
import ai.speckitty.charter.api.model.AgentProfileSpecialization;
import ai.speckitty.charter.api.model.EffectiveAgentProfileRead;
import ai.speckitty.charter.api.model.EffectiveGovernanceEntry;
import ai.speckitty.charter.api.model.PackGovernanceEntry;
import ai.speckitty.charter.api.model.PackGovernanceSuggestion;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The JSON rule of the profile models, and the hints a native executable needs to apply it. */
class ProfileJsonTest {

    private static final JsonMapper JSON = JsonMapper.builder()
            .addModule(new ProfileJson().profileJsonModule())
            .build();

    @Test
    void anEmptyProfileOmitsTheOptionalMembersAndWritesTheRequiredNullableOnesAsNull() {
        JsonNode written = JSON.valueToTree(new AgentProfile());

        assertThat(written.has("description")).isFalse();
        assertThat(written.has("schemaVersion")).isFalse();
        assertThat(written.has("routingPriority")).isFalse();
        assertThat(written.has("maxConcurrentTasks")).isFalse();
        assertThat(written.has("sentinel")).isFalse();
        assertThat(written.has("initializationDeclaration")).isFalse();
        assertThat(written.get("avatarImage").isNull()).isTrue();
        assertThat(written.get("model").isNull()).isTrue();
        assertThat(written.get("effort").isNull()).isTrue();
        assertThat(written.get("specializationContext").isNull()).isTrue();
        assertThat(written.get("selfReviewProtocol").isNull()).isTrue();
        assertThat(written.get("excluding").isNull()).isTrue();
    }

    @Test
    void anEmptySpecializationOmitsItsProseMembers() {
        JsonNode written = JSON.valueToTree(new AgentProfileSpecialization());

        assertThat(written.has("secondaryAwareness")).isFalse();
        assertThat(written.has("avoidanceBoundary")).isFalse();
        assertThat(written.has("successDefinition")).isFalse();
    }

    @Test
    void anEmptySuggestionIsAnEmptyObject() {
        JsonNode written = JSON.valueToTree(new PackGovernanceSuggestion());
        assertThat(written.isObject()).isTrue();
        assertThat(written.size()).isZero();
    }

    @Test
    void anExcludingWritesTheFormItDoesNotUseAsNull() {
        JsonNode written = JSON.valueToTree(new AgentProfileExcluding(List.of("roles"), null));

        assertThat(written.get("wholeFields")).hasSize(1);
        assertThat(written.get("fieldValues").isNull()).isTrue();
    }

    @Test
    void aGovernanceEntryWritesItsNullablePackIdAndSuggestionAsNull() {
        JsonNode written = JSON.valueToTree(new PackGovernanceEntry());

        assertThat(written.get("packId").isNull()).isTrue();
        assertThat(written.get("suggestion").isNull()).isTrue();
    }

    @Test
    void anAnswerUnderAnActivationOmitsItsOptionalMembersAndKeepsTheRequiredOnes() {
        JsonNode read = JSON.valueToTree(new EffectiveAgentProfileRead());
        JsonNode entry = JSON.valueToTree(new EffectiveGovernanceEntry());

        for (JsonNode written : List.of(read, entry)) {
            assertThat(written.has("satisfiedBy")).isFalse();
            assertThat(written.has("suggestion")).isFalse();
        }
        assertThat(read.has("agentProfile")).isFalse();
    }

    @Test
    void theHintsRegisterTheModelsAndTheMixIns() {
        RuntimeHints hints = new RuntimeHints();

        new ProfileJson.Hints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(AgentProfile.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(PackGovernanceEntry.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(AgentProfileExcluding.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(EffectiveAgentProfileRead.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(EffectiveGovernanceEntry.class))
                .accepts(hints);
        for (String mixIn : List.of("OptionalProfileMembers", "OptionalSpecializationMembers", "OmitAbsentMembers")) {
            assertThat(hints.reflection()
                            .getTypeHint(
                                    TypeReference.of("ai.speckitty.charter.profiles.infra.api.ProfileJson$" + mixIn)))
                    .as("the hint of the mix-in " + mixIn)
                    .isNotNull();
        }
    }
}
