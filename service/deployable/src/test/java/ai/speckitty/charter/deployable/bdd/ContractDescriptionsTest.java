package ai.speckitty.charter.deployable.bdd;

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
    void theRefusalCodeSaysStaleInputAndGovernancePolicyViolationAreRaised() throws IOException {
        assertThat(description("contract/charter/schemas/CharterRefusalCode.yaml"))
                .contains("stale_input is raised: the packs or the project configuration changed while the answer was"
                        + " computed, so the answer and the Charter-Source-Fingerprint it would report might disagree")
                .contains("The read may be retried")
                .contains("governance_policy_violation (409) is raised by the registration writes")
                .contains("Each pack's policy.yaml is read, per pack along the composition closure of the pack written"
                        + " (ADR-0063); no open pack ships one yet")
                .doesNotContain("reads no policy file")
                .doesNotContain("No operation of this version raises it");
    }

    @Test
    void theRefusalCodeSaysWhichActivationCodesAreRaisedAndGivesEveryStatus() throws IOException {
        assertThat(description("contract/charter/schemas/CharterRefusalCode.yaml"))
                .contains("Six codes belong to the activation set (ADR-0020 D9, D10, #125), and every one is raised")
                .contains("preset_not_found (404), a preset application whose path names a preset")
                .contains("No operation of the activation set is reserved any more")
                .contains("action_not_found is raised")
                .doesNotContain("stay reserved (ADR-0020 D8)")
                .doesNotContain("Only the two effective reads")
                .doesNotContain("Only the four effective reads")
                .doesNotContain("One stays reserved")
                .doesNotContain("No operation of this version raises that one")
                .contains("artifact_required, artifact_locked and mission_type_not_in_force are 409")
                .contains("validation_failed and activation_invalid are 422")
                .doesNotContain("are no longer raised: the action-context operation");
    }

    @Test
    void theRefusedResponseCoversStaleInput() throws IOException {
        assertThat(description("contract/charter/responses/OfferingUnreadable.yaml"))
                .contains("the packs changed while the read was answered")
                .contains("stale_input, when the packs or the project configuration changed while the read was"
                        + " answered, which may be retried")
                .doesNotContain("pack_level_mismatch")
                .doesNotContain("drg_schema_error");
    }
}
