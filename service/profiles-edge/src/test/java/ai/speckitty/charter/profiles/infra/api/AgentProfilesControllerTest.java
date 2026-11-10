package ai.speckitty.charter.profiles.infra.api;

import static ai.speckitty.charter.profiles.application.ProfileFixtures.leanProfile;
import static ai.speckitty.charter.profiles.application.ProfileFixtures.requires;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.profiles.application.ProfileRead;
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
 * The controller's own work, over a fake source: the headers of the answer, the {@code self} link that repeats the depth
 * only when the request named it, and a refusal that passes through to the shared handler. The routing against the
 * generic paths and the whole service are {@code ProfileRoutingTest} and the contract scenarios.
 */
class AgentProfilesControllerTest {

    private static final String FINGERPRINT = "sha256:" + "b".repeat(64);
    private static final String REVISION = "sha256:" + "a".repeat(64);

    private final List<String> asked = new ArrayList<>();

    private MockMvc mvc(ProfileSource source) {
        AgentProfilesController controller =
                new AgentProfilesController(new ProfileReadService(source), new ProfileDtos("/api/v1"));
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                        .addModule(new ProfileJson().profileJsonModule())
                        .build()))
                .build();
    }

    private ProfileSource source() {
        return (PackIdentifier pack, String profileId, int depth) -> {
            asked.add(pack.value() + "/" + profileId + "/" + depth);
            return new ProfileRead(
                    leanProfile(pack.value(), profileId),
                    REVISION,
                    FINGERPRINT,
                    List.of(requires("directive:D", pack.value())),
                    List.of(ArtifactKind.DIRECTIVE));
        };
    }

    @Test
    void theAnswerCarriesTheVerifiedFingerprintAndThePackRevisionQuotedAsItsEtag() throws Exception {
        mvc(source())
                .perform(get("/api/v1/packs/built-in/artifacts/agent_profile/P").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(header().string("ETag", "\"" + REVISION + "\""))
                .andExpect(jsonPath("$.kind").value("agent_profile"))
                .andExpect(jsonPath("$.agentProfile.profileId").value("P"))
                .andExpect(jsonPath("$.governance[0].urn").value("directive:D"))
                .andExpect(jsonPath("$.governance[0]._links.artifact.href")
                        .value("/api/v1/packs/built-in/artifacts/directive/D"));
        assertThat(asked).containsExactly("built-in/P/0");
    }

    @Test
    void theDepthTheRequestNamedIsReadAndRepeatedInSelf() throws Exception {
        mvc(source())
                .perform(get("/api/v1/packs/built-in/artifacts/agent_profile/P?depth=1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href")
                        .value("/api/v1/packs/built-in/artifacts/agent_profile/P?depth=1"));
        assertThat(asked).containsExactly("built-in/P/1");
    }

    @Test
    void aDepthTheRequestDidNotNameIsTheDefaultAndSelfDoesNotRepeatIt() throws Exception {
        mvc(source())
                .perform(get("/api/v1/packs/built-in/artifacts/agent_profile/P").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/packs/built-in/artifacts/agent_profile/P"));
    }

    @Test
    void aRefusalOfTheReadIsLeftToTheSharedHandler() {
        ProfileSource refusing = (pack, profileId, depth) -> {
            throw new CharterRefusal(RefusalCode.ARTIFACT_NOT_FOUND, "Pack built-in has no agent_profile nobody.");
        };

        assertThatThrownBy(() -> mvc(refusing)
                        .perform(get("/api/v1/packs/built-in/artifacts/agent_profile/nobody")
                                .accept(MediaType.APPLICATION_JSON)))
                .hasCauseInstanceOf(CharterRefusal.class);
    }
}
