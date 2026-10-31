package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.ActivationConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
                "{\"schemaVersion\":1}",
                "{\"schemaVersion\":1,\"missionTypes\":[]}",
                "{\"schemaVersion\":1,\"selected\":{\"directive\":[]}}",
                "{\"schemaVersion\":1,\"selected\":{\"skill\":[]},\"deactivated\":{\"tactic\":[\"T1\"]}}",
                "{\"schemaVersion\":1,\"packs\":[{\"packId\":\"built-in\"}]}"
            })
    void aDocumentReadAndWrittenAgainIsUnchanged(String document) {
        ActivationConfig read = mapper.readValue(document, ActivationConfig.class);

        assertThat(mapper.readTree(mapper.writeValueAsString(read))).isEqualTo(mapper.readTree(document));
    }
}
