package ai.speckitty.charter.bootstrap.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/** What the contract tells a consumer in words, which no schema keyword can say, stays said. */
class ContractDescriptionsTest {

    private static String description(String resource) throws IOException {
        try (var in = ContractDescriptionsTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            Map<?, ?> schema = (Map<?, ?>) new Load(LoadSettings.builder().build())
                    .loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return String.valueOf(schema.get("description")).replaceAll("\\s+", " ");
        }
    }

    @Test
    void theRefusalCodeTellsAConsumerHowToDispatchOnAFailure() throws IOException {
        assertThat(description("contract/charter/schemas/CharterRefusalCode.yaml"))
                .contains("A body with a code is a charter refusal")
                .contains("A body without a code is a transport or routing failure")
                .contains("internal_error is a fault of the service and may be retried")
                .contains("every other code is a deterministic refusal");
    }

    @Test
    void theRefusalCodeSaysStaleInputIsRaisedAndGovernancePolicyViolationIsOnlyDeclared() throws IOException {
        assertThat(description("contract/charter/schemas/CharterRefusalCode.yaml"))
                .contains("stale_input is raised: the packs or the project configuration changed while the answer was"
                        + " computed, so the answer and the Charter-Source-Fingerprint it would report might disagree")
                .contains("The read may be retried")
                .contains("No operation of this version raises it")
                .contains("Its scenario is a declaration pin, not behavior");
    }

    @Test
    void theRefusedResponseCoversStaleInput() throws IOException {
        assertThat(description("contract/charter/responses/OfferingUnreadable.yaml"))
                .contains("the packs changed while the read was answered")
                .contains("stale_input, which may be retried");
    }
}
