package ai.speckitty.charter.profiles.infra.api;

import static ai.speckitty.charter.profiles.application.ProfileFixtures.effectiveRead;
import static ai.speckitty.charter.profiles.application.ProfileFixtures.effectiveRequires;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.profiles.application.EffectiveProfiles;
import ai.speckitty.charter.profiles.application.ProfileFixtures;
import ai.speckitty.charter.profiles.application.ProfileReadService;
import ai.speckitty.charter.profiles.application.ProfileSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * The controller's own work, over a fake port: the headers of the answer, the {@code 304} that sends the tag alone, the
 * {@code self} link that repeats the depth only when the request named it, and a refusal that passes through to the
 * shared handler. The routing against the generic path, the problem parity and the whole service are
 * {@code ActivationRoutingTest}, {@code ProfileProblemParityTest} and the contract scenarios.
 */
class EffectiveAgentProfilesControllerTest {

    private static final String SOURCE = "sha256:" + "b".repeat(64);

    private final List<String> asked = new ArrayList<>();

    private MockMvc mvc(EffectiveProfiles port) {
        ProfileSource noPacks = (pack, profileId, depth) -> {
            throw new AssertionError("the pack-scoped read is not asked");
        };
        EffectiveAgentProfilesController controller = new EffectiveAgentProfilesController(
                new ProfileReadService(noPacks, port), new EffectiveProfileDtos("/api/v1"));
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                        .addModule(new ProfileJson().profileJsonModule())
                        .build()))
                .build();
    }

    private EffectiveProfiles port() {
        return (activationId, profileId, depth, ifNoneMatch) -> {
            asked.add(activationId + "/" + profileId + "/" + depth + "/" + ifNoneMatch);
            return "\"t-1\"".equals(ifNoneMatch)
                    ? new EffectiveProfiles.Outcome.NotModified("t-1")
                    : new EffectiveProfiles.Outcome.Found(
                            effectiveRead(effectiveRequires("directive:D", "main")), "t-1");
        };
    }

    @Test
    void theAnswerCarriesTheVerifiedFingerprintAndTheTagQuotedAsItsEtag() throws Exception {
        mvc(port())
                .perform(
                        get("/api/v1/activations/a-1/artifacts/agent_profile/P").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Charter-Source-Fingerprint", SOURCE))
                .andExpect(header().string("ETag", "\"t-1\""))
                .andExpect(jsonPath("$.kind").value("agent_profile"))
                .andExpect(jsonPath("$.activationId").value("a-1"))
                .andExpect(jsonPath("$.agentProfile.profileId").value("P"))
                .andExpect(jsonPath("$.governance[0].urn").doesNotExist())
                .andExpect(jsonPath("$.governance[0].artifactId").value("D"))
                .andExpect(jsonPath("$.governance[0]._links.artifact.href")
                        .value("/api/v1/packs/main/artifacts/directive/D"))
                .andExpect(jsonPath("$._links.up.href").value("/api/v1/activations/a-1"));
        assertThat(asked).containsExactly("a-1/P/0/null");
    }

    @Test
    void aMatchingIfNoneMatchIsA304ThatSendsTheTagAloneAndNoFingerprint() throws Exception {
        mvc(port())
                .perform(get("/api/v1/activations/a-1/artifacts/agent_profile/P")
                        .header("If-None-Match", "\"t-1\"")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", "\"t-1\""))
                .andExpect(header().doesNotExist("Charter-Source-Fingerprint"))
                .andExpect(content().string(""));
        assertThat(asked).containsExactly("a-1/P/0/\"t-1\"");
    }

    @Test
    void theDepthTheRequestNamedIsReadAndRepeatedInSelf() throws Exception {
        mvc(port())
                .perform(get("/api/v1/activations/a-1/artifacts/agent_profile/P?depth=1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href")
                        .value("/api/v1/activations/a-1/artifacts/agent_profile/P?depth=1"));
        assertThat(asked).containsExactly("a-1/P/1/null");
    }

    @Test
    void aDepthTheRequestDidNotNameIsTheDefaultAndSelfDoesNotRepeatIt() throws Exception {
        mvc(port())
                .perform(
                        get("/api/v1/activations/a-1/artifacts/agent_profile/P").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/activations/a-1/artifacts/agent_profile/P"));
    }

    @Test
    void aRefusalOfTheReadIsLeftToTheSharedHandler() {
        EffectiveProfiles refusing = (activationId, profileId, depth, ifNoneMatch) -> {
            throw new CharterRefusal(RefusalCode.ACTIVATION_NOT_FOUND, "Activation nope was not found.");
        };

        assertThatThrownBy(() -> mvc(refusing)
                        .perform(get("/api/v1/activations/nope/artifacts/agent_profile/P")
                                .accept(MediaType.APPLICATION_JSON)))
                .hasCauseInstanceOf(CharterRefusal.class);
    }

    @Test
    void theControllerRequiresItsCollaborators() {
        ProfileReadService service = new ProfileReadService(
                (pack, profileId, depth) -> {
                    throw new AssertionError("not asked");
                },
                ProfileFixtures.NO_ACTIVATIONS);
        assertThatThrownBy(() -> new EffectiveAgentProfilesController(null, new EffectiveProfileDtos("/api/v1")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EffectiveAgentProfilesController(service, null))
                .isInstanceOf(NullPointerException.class);
    }
}
