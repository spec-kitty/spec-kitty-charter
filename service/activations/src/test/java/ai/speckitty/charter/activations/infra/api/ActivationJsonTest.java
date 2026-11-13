package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.ActivationConfig;
import ai.speckitty.charter.api.model.EffectiveGraphNode;
import ai.speckitty.charter.api.model.EffectivePromptSlot;
import ai.speckitty.charter.api.model.EffectiveStepPrompt;
import ai.speckitty.charter.api.model.Link;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The generated {@code ActivationConfig} keeps the three states of the document (ADR-0020 D4): an absent member stays
 * absent and an empty list stays {@code []} through a read and a write, so a fetched document registers unchanged.
 * Without {@code containerDefaultToNull} an absent list would come back as {@code []}; without the mix-in, as
 * {@code null}.
 */
class ActivationJsonTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new ActivationJson().activationJsonModule())
            .build();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"schemaVersion\":2}",
                "{\"schemaVersion\":2,\"languages\":[]}",
                "{\"schemaVersion\":2,\"selected\":{\"directive\":[]}}",
                "{\"schemaVersion\":2,\"selected\":{\"skill\":[]},\"deactivated\":{\"tactic\":[\"T1\"]}}",
                "{\"schemaVersion\":2,\"packs\":[{\"packId\":\"built-in\"}]}",
                "{\"schemaVersion\":2,\"selected\":{\"mission_type\":[]},\"deactivated\":{\"mission_type\":[\"m\"]}}"
            })
    void aDocumentReadAndWrittenAgainIsUnchanged(String document) {
        ActivationConfig read = mapper.readValue(document, ActivationConfig.class);

        assertThat(mapper.readTree(mapper.writeValueAsString(read))).isEqualTo(mapper.readTree(document));
    }

    @Test
    void aGraphNodeWithoutLinksWritesNoLinksMemberAndOneWithLinksWritesThem() {
        EffectiveGraphNode step = new EffectiveGraphNode("action:m/s", EffectiveGraphNode.KindEnum.ACTION);
        EffectiveGraphNode linked = new EffectiveGraphNode("directive:D", EffectiveGraphNode.KindEnum.DIRECTIVE);
        linked.setLinks(Map.of("effective-artifact", new Link("/api/v1/activations/a-1/artifacts/directive/D")));

        assertThat(mapper.readTree(mapper.writeValueAsString(step)).has("_links"))
                .as("an absent _links is left out, not written as null")
                .isFalse();
        assertThat(mapper.readTree(mapper.writeValueAsString(linked))
                        .at("/_links/effective-artifact/href")
                        .asString())
                .isEqualTo("/api/v1/activations/a-1/artifacts/directive/D");
    }

    @Test
    void anEffectivePromptWithoutAPromptPackWritesNoMemberForItAndOneWithAPackWritesIt() {
        EffectiveStepPrompt none = new EffectiveStepPrompt(
                "a-1",
                "sha256:r",
                "sha256:g",
                "software-dev",
                "review",
                "",
                List.of(),
                List.of(),
                Map.of("self", new Link("/api/v1/activations/a-1/mission-types/software-dev/actions/review/prompt")));
        EffectiveStepPrompt held = new EffectiveStepPrompt(
                "a-1",
                "sha256:r",
                "sha256:g",
                "software-dev",
                "plan",
                "# Plan\n",
                List.of(new EffectivePromptSlot("team:checklist", List.of())),
                List.of(),
                Map.of("self", new Link("/api/v1/activations/a-1/mission-types/software-dev/actions/plan/prompt")));
        held.setPromptPackId("base");

        JsonNode written = mapper.readTree(mapper.writeValueAsString(none));
        assertThat(written.has("promptPackId"))
                .as("an absent promptPackId is left out, not written as null")
                .isFalse();
        assertThat(written.at("/slots").isArray()).as("an empty list stays []").isTrue();
        assertThat(written.at("/diagnostics").isArray()).isTrue();
        assertThat(mapper.readTree(mapper.writeValueAsString(held))
                        .at("/promptPackId")
                        .asString())
                .isEqualTo("base");
        assertThat(mapper.readTree(mapper.writeValueAsString(held))
                        .at("/slots/0/fragments")
                        .isArray())
                .isTrue();
    }
}
