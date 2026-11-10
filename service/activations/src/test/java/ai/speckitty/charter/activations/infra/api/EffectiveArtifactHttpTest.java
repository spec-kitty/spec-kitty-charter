package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ai.speckitty.charter.activations.application.FakeAvailablePacks;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.EffectiveArtifactRead;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.GovernanceFacts;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.ArtifactState;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code getEffectiveArtifact} as the controller serves it over HTTP (WP06): the winner with its pack, level and state,
 * its three links, the verified fingerprint and the strong tag; the {@code 304}; the refusals in their order; a
 * {@code depth} out of range as a {@code 400} without a code.
 *
 * <p>It also pins what the generic handler does for an {@code agent_profile} while the profile route is not built (WP07):
 * it answers the winner like any other kind, with no body and no governance, because the schema is closed and the read
 * embeds none. The governance the session holds for a profile is never rendered here. Once WP07 maps the literal path,
 * Spring answers a GET on a profile from that controller and not from this handler.
 */
class EffectiveArtifactHttpTest {

    private static final String BASE = "/api/v1/activations/a-1";
    private static final ArtifactRef D = new ArtifactRef(ArtifactKind.DIRECTIVE, "D");
    private static final ArtifactRef P = new ArtifactRef(ArtifactKind.AGENT_PROFILE, "P");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final InMemoryActivationRepository repository = new InMemoryActivationRepository();
    private FakeAvailablePacks packs;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        packs = new FakeAvailablePacks(ClosureSnapshots.snapshot()
                .pack("fixture", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "D")
                .held(ArtifactKind.AGENT_PROFILE, "P")
                .fingerprint("sha256:source")
                .closureFingerprint("sha256:closure")
                .build());
        Map<ArtifactRef, MergedSnapshot.Winner> winners = new LinkedHashMap<>();
        winners.put(D, new MergedSnapshot.Winner("fixture", PackLevel.ORG, Policy.OPEN));
        winners.put(P, new MergedSnapshot.Winner("fixture", PackLevel.ORG, Policy.OPEN));
        packs.merged(new MergedSnapshot(winners, Map.of(), List.of(), List.of()));
        Map<ArtifactRef, ArtifactState> states = new LinkedHashMap<>();
        for (ArtifactRef ref : List.of(D, P)) {
            states.put(
                    ref,
                    new ArtifactState(
                            ref,
                            State.ACTIVE,
                            Reason.SELECTED,
                            false,
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty()));
        }
        packs.resolves(
                query -> new InForceAnswer(states, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of()));
        packs.winners(ask -> Optional.of(new EffectiveArtifactRead(
                entity(ask.kind(), ask.id()),
                "fixture",
                PackLevel.ORG,
                ask.kind() == ArtifactKind.AGENT_PROFILE
                        ? Optional.of(new GovernanceFacts(P, List.of(), List.of()))
                        : Optional.empty())));
        mvc = StandaloneActivationMvc.over(repository, packs);
        MockHttpServletResponse created = send(put(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schemaVersion\": 2, \"packs\": [{\"packId\": \"fixture\"}],"
                        + " \"selected\": {\"directive\": [\"D\"], \"agent_profile\": [\"P\"]}}"));
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
    }

    private static CharterArtifact entity(ArtifactKind kind, String id) {
        return new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return kind;
            }

            @Override
            public PackIdentifier packId() {
                return new PackIdentifier("fixture");
            }

            @Override
            public String localId() {
                return id;
            }

            @Override
            public Optional<String> schemaVersion() {
                return Optional.empty();
            }

            @Override
            public String label() {
                return id;
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }
        };
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    private static JsonNode body(MockHttpServletResponse response) throws Exception {
        return JSON.readTree(response.getContentAsString());
    }

    @Test
    void theWinnerIsAnsweredWithItsFactsItsLinksTheFingerprintAndAStrongTag() throws Exception {
        MockHttpServletResponse response = send(get(BASE + "/artifacts/directive/D"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaders("Charter-Source-Fingerprint")).containsExactly("sha256:source");
        assertThat(response.getHeader("ETag")).matches("\"sha256:[0-9a-f]{64}\"");
        JsonNode body = body(response);
        assertThat(body.get("activationId").asString()).isEqualTo("a-1");
        assertThat(body.get("governanceFingerprint").asString()).isEqualTo("sha256:closure");
        assertThat(body.get("kind").asString()).isEqualTo("directive");
        assertThat(body.get("artifactId").asString()).isEqualTo("D");
        assertThat(body.get("packId").asString()).isEqualTo("fixture");
        assertThat(body.get("level").asString()).isEqualTo("org");
        assertThat(body.get("state").asString()).isEqualTo("active");
        assertThat(body.get("policy").asString()).isEqualTo("open");
        assertThat(body.get("reason").asString()).isEqualTo("selected");
        assertThat(body.get("required").asBoolean()).isFalse();
        assertThat(body.get("_links").get("self").get("href").asString()).isEqualTo(BASE + "/artifacts/directive/D");
        assertThat(body.get("_links").get("artifact").get("href").asString())
                .isEqualTo("/api/v1/packs/fixture/artifacts/directive/D");
        assertThat(body.get("_links").get("up").get("href").asString()).isEqualTo(BASE);
        assertThat(body.propertyNames()).doesNotContain("satisfiedBy", "suggestion", "directive");
    }

    @Test
    void aMatchingIfNoneMatchIs304AndAnotherTagIs200() throws Exception {
        String etag = send(get(BASE + "/artifacts/directive/D")).getHeader("ETag");

        MockHttpServletResponse notModified =
                send(get(BASE + "/artifacts/directive/D").header("If-None-Match", etag));
        assertThat(notModified.getStatus()).isEqualTo(304);
        assertThat(notModified.getHeader("ETag")).isEqualTo(etag);
        assertThat(notModified.getContentAsString()).isEmpty();
        assertThat(notModified.getHeader("Charter-Source-Fingerprint")).isNull();

        assertThat(send(get(BASE + "/artifacts/directive/D?depth=1").header("If-None-Match", etag))
                        .getStatus())
                .isEqualTo(200);
    }

    @Test
    void theSelfLinkRepeatsTheDepthOnlyWhenTheRequestSentOne() throws Exception {
        assertThat(body(send(get(BASE + "/artifacts/directive/D?depth=1")))
                        .get("_links")
                        .get("self")
                        .get("href")
                        .asString())
                .isEqualTo(BASE + "/artifacts/directive/D?depth=1");
        assertThat(body(send(get(BASE + "/artifacts/directive/D?depth=0")))
                        .get("_links")
                        .get("self")
                        .get("href")
                        .asString())
                .isEqualTo(BASE + "/artifacts/directive/D?depth=0");
    }

    @Test
    void aDepthOutOfRangeAndAKindThatIsNoSlugAre400WithoutACode() throws Exception {
        for (String path : List.of(
                "/artifacts/directive/D?depth=2", "/artifacts/directive/D?depth=-1", "/artifacts/Directive/D")) {
            MockHttpServletResponse response = send(get(BASE + path));

            assertThat(response.getStatus()).as(path).isEqualTo(400);
            assertThat(body(response).propertyNames()).as(path).doesNotContain("code");
        }
    }

    @Test
    void theRefusalsAreJudgedInOrderAndAnswered404WithTheirCode() throws Exception {
        assertRefused(get("/api/v1/activations/nope/artifacts/procedure/D"), "activation_not_found");
        assertRefused(get(BASE + "/artifacts/procedure/D"), "kind_not_found");
        assertRefused(get(BASE + "/artifacts/directive/GHOST"), "artifact_not_found");
        assertRefused(get(BASE + "/artifacts/directive/GHOST").header("If-None-Match", "*"), "artifact_not_found");
    }

    private void assertRefused(MockHttpServletRequestBuilder request, String code) throws Exception {
        MockHttpServletResponse response = send(request);

        assertThat(response.getStatus()).as(code).isEqualTo(404);
        assertThat(body(response).get("code").asString()).isEqualTo(code);
    }

    @Test
    void aStaleReadIsAnsweredWithItsOwnCodeAndNothingPartial() throws Exception {
        packs.winners(ask -> {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, "The packs changed.");
        });

        MockHttpServletResponse response = send(get(BASE + "/artifacts/directive/D"));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(body(response).get("code").asString()).isEqualTo("stale_input");
        assertThat(response.getHeader("Charter-Source-Fingerprint")).isNull();
    }

    @Test
    void anAgentProfileIsAnsweredAsAWinnerOnlyAndTheGovernanceTheSessionHoldsIsNeverRendered() throws Exception {
        MockHttpServletResponse response = send(get(BASE + "/artifacts/agent_profile/P?depth=1"));

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.get("kind").asString()).isEqualTo("agent_profile");
        assertThat(body.get("_links").get("artifact").get("href").asString())
                .isEqualTo("/api/v1/packs/fixture/artifacts/agent_profile/P");
        assertThat(body.propertyNames())
                .as("the generic read carries neither the profile nor its governance")
                .doesNotContain("agentProfile", "governance", "directive");
        assertThat(packs.effectiveAsks()).hasSize(1);
        assertThat(packs.effectiveAsks().getFirst().depth()).isEqualTo(1);
    }
}
