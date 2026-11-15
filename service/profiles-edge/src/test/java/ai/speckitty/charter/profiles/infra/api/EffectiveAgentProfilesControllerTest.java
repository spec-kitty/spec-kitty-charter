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
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.profiles.application.EffectiveProfiles;
import ai.speckitty.charter.profiles.application.ProfileFixtures;
import ai.speckitty.charter.profiles.application.ProfileReadService;
import ai.speckitty.charter.profiles.application.ProfileSelectionService;
import ai.speckitty.charter.profiles.application.ProfileSource;
import ai.speckitty.charter.profiles.application.SelectionCandidates;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.Specialization;
import ai.speckitty.charter.profiles.domain.selection.Candidates;
import ai.speckitty.charter.profiles.domain.selection.SelectionCandidate;
import ai.speckitty.charter.profiles.domain.selection.SelectionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    /** A selection port for the tests of the profile read: asked, it is a fault. */
    private static final SelectionCandidates NO_SELECTION = new SelectionCandidates() {
        @Override
        public Read read(String activationId, String role, SelectionContext context) {
            throw new AssertionError("the profile read does not ask the selection port");
        }

        @Override
        public StepRead readForStep(String activationId, String missionType, String action, SelectionContext context) {
            throw new AssertionError("the profile read does not ask the selection port");
        }
    };

    private MockMvc mvc(EffectiveProfiles port) {
        return mvc(port, NO_SELECTION);
    }

    private MockMvc mvc(EffectiveProfiles port, SelectionCandidates selection) {
        ProfileSource noPacks = (pack, profileId, depth) -> {
            throw new AssertionError("the pack-scoped read is not asked");
        };
        EffectiveAgentProfilesController controller = new EffectiveAgentProfilesController(
                new ProfileReadService(noPacks, port),
                new EffectiveProfileDtos("/api/v1"),
                new ProfileSelectionService(selection),
                new ProfileSelectionDtos("/api/v1"));
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

    // ---- selectAgentProfile, served by the same controller (one tag, one generated interface)

    private SelectionCandidates selection() {
        return new SelectionCandidates() {
            @Override
            public Read read(String activationId, String role, SelectionContext context) {
                asked.add(activationId + "/" + role + "/" + context.language().orElse("-") + "/"
                        + context.framework().orElse("-"));
                return rita(activationId, "t-2");
            }

            @Override
            public StepRead readForStep(
                    String activationId, String missionType, String action, SelectionContext context) {
                asked.add(activationId + "/" + missionType + "/" + action + "/"
                        + context.language().orElse("-") + "/"
                        + context.framework().orElse("-"));
                return new StepRead(Optional.of("reviewer"), rita(activationId, "t-3"));
            }
        };
    }

    private static SelectionCandidates.Read rita(String activationId, String tag) {
        AgentProfile rita = AgentProfile.builder()
                .id(new AgentProfileIdentifier("reviewer-rita"))
                .packId(new PackIdentifier("org-base"))
                .name("Reviewer Rita")
                .purpose("Review.")
                .role("reviewer")
                .specialization(new Specialization("Review", Optional.empty(), Optional.empty(), Optional.empty()))
                .build();
        return new SelectionCandidates.Read(
                activationId,
                Candidates.unpinned(List.of(new SelectionCandidate(rita, "org-base", 1, 0)), Optional.empty()),
                SOURCE,
                tag);
    }

    @Test
    void aStepsSelectionIsTheSelectionOfItsRoleLinkedToTheStepWithItsOwnTag() throws Exception {
        mvc(port(), selection())
                .perform(get("/api/v1/activations/a-1/mission-types/software-dev/actions/review/profile-selection"
                                + "?framework=spring")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Charter-Source-Fingerprint", SOURCE))
                .andExpect(header().string("ETag", "\"t-3\""))
                .andExpect(jsonPath("$.role").value("reviewer"))
                .andExpect(jsonPath("$.context.framework").value("spring"))
                .andExpect(jsonPath("$.candidates[0].id").value("reviewer-rita"))
                .andExpect(jsonPath("$._links.self.href")
                        .value("/api/v1/activations/a-1/mission-types/software-dev/actions/review/profile-selection"
                                + "?framework=spring"));
        mvc(port(), selection())
                .perform(get("/api/v1/activations/a-1/mission-types/software-dev/actions/review/profile-selection")
                        .header("If-None-Match", "\"t-3\"")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", "\"t-3\""))
                .andExpect(content().string(""));
        assertThat(asked).containsExactly("a-1/software-dev/review/-/spring", "a-1/software-dev/review/-/-");
    }

    @Test
    void aSelectionCarriesTheVerifiedFingerprintTheQuotedTagAndTheAskedContext() throws Exception {
        mvc(port(), selection())
                .perform(get("/api/v1/activations/a-1/roles/reviewer/profile-selection?language=java")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Charter-Source-Fingerprint", SOURCE))
                .andExpect(header().string("ETag", "\"t-2\""))
                .andExpect(jsonPath("$.role").value("reviewer"))
                .andExpect(jsonPath("$.context.language").value("java"))
                .andExpect(jsonPath("$.context.framework").doesNotExist())
                .andExpect(jsonPath("$.candidates[0].id").value("reviewer-rita"))
                .andExpect(jsonPath("$.candidates[0].pack").value("org-base"))
                .andExpect(jsonPath("$.candidates[0].fit").value("primary"))
                .andExpect(jsonPath("$.tie").value(false))
                .andExpect(jsonPath("$._links.self.href")
                        .value("/api/v1/activations/a-1/roles/reviewer/profile-selection?language=java"));
        assertThat(asked).containsExactly("a-1/reviewer/java/-");
    }

    @Test
    void aSelectionWhoseTagMatchesIsA304ThatSendsTheTagAlone() throws Exception {
        mvc(port(), selection())
                .perform(get("/api/v1/activations/a-1/roles/reviewer/profile-selection")
                        .header("If-None-Match", "\"t-2\"")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", "\"t-2\""))
                .andExpect(header().doesNotExist("Charter-Source-Fingerprint"))
                .andExpect(content().string(""));
    }

    @Test
    void theControllerRequiresItsCollaborators() {
        ProfileReadService service = new ProfileReadService(
                (pack, profileId, depth) -> {
                    throw new AssertionError("not asked");
                },
                ProfileFixtures.NO_ACTIVATIONS);
        EffectiveProfileDtos dtos = new EffectiveProfileDtos("/api/v1");
        ProfileSelectionService selection = new ProfileSelectionService(NO_SELECTION);
        ProfileSelectionDtos selectionDtos = new ProfileSelectionDtos("/api/v1");
        assertThatThrownBy(() -> new EffectiveAgentProfilesController(null, dtos, selection, selectionDtos))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EffectiveAgentProfilesController(service, null, selection, selectionDtos))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EffectiveAgentProfilesController(service, dtos, null, selectionDtos))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EffectiveAgentProfilesController(service, dtos, selection, null))
                .isInstanceOf(NullPointerException.class);
    }
}
