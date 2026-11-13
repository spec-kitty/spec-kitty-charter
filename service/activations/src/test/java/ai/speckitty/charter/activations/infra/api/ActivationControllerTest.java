package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ai.speckitty.charter.activations.application.FakeAvailablePacks;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.EffectivePrompt;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.api.ActivationApi;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.kernel.transport.ReservedOperation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The activation set is reserved one operation at a time (ADR-0020 D8): the controller carries no mark of its own, and an
 * operation that is not built carries one, so an operation is built by removing its mark alone. Every one of the sixteen
 * operations is built now (PR C built the effective graph and the effective action context, WP06 the read of one
 * artifact, mission effective-step-and-action-context's WP07 the effective prompt), so none is marked; the interceptor
 * stays for a reservation to come, which {@code ReservedOperationInterceptorTest} and the architecture rules keep honest.
 *
 * <p>The effective prompt is also served here over HTTP: its body, {@code ETag} and fingerprint, its {@code 304}, and its
 * {@code 409} before the {@code 304}.
 */
class ActivationControllerTest {

    /** The operations the activation set builds: the register (#125, #360), the writes (#291) and the seven reads. */
    private static final Set<String> BUILT = Set.of(
            "createActivation",
            "getActivation",
            "replaceActivation",
            "deleteActivation",
            "listActivationPacks",
            "selectActivationArtifact",
            "unselectActivationArtifact",
            "deactivateActivationArtifact",
            "reactivateActivationArtifact",
            "applyActivationPreset",
            "listEffectiveArtifacts",
            "getActivationSkippedReport",
            "getEffectiveGraph",
            "getEffectiveActionContext",
            "getEffectiveArtifact",
            "getEffectiveStepPrompt");

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PROMPT = "/api/v1/activations/a-1/mission-types/software-dev/actions/plan/prompt";

    private static List<Method> operations() {
        return Arrays.stream(ActivationApi.class.getMethods())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .toList();
    }

    @Test
    void everyOneOfTheSixteenOperationsIsOverriddenAndNoneIsMarkedReserved() throws NoSuchMethodException {
        assertThat(operations()).hasSize(16);
        assertThat(operations()).extracting(Method::getName).containsExactlyInAnyOrderElementsOf(BUILT);
        for (Method operation : operations()) {
            Method overridden =
                    ActivationController.class.getMethod(operation.getName(), operation.getParameterTypes());
            assertThat(overridden.getDeclaringClass()).isEqualTo(ActivationController.class);
            assertThat(overridden.isAnnotationPresent(ReservedOperation.class))
                    .as("%s is marked reserved", operation.getName())
                    .isFalse();
        }
        assertThat(ActivationController.class.isAnnotationPresent(ReservedOperation.class))
                .as("the controller carries no mark of its own")
                .isFalse();
    }

    // ---- getEffectiveStepPrompt over HTTP (WP07)

    private static final ArtifactRef SOFTWARE_DEV = new ArtifactRef(ArtifactKind.MISSION_TYPE, "software-dev");

    private record Served(MockMvc mvc, FakeAvailablePacks packs) {

        MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
            return mvc.perform(request).andReturn().getResponse();
        }
    }

    private static Served served() throws Exception {
        FakeAvailablePacks packs = new FakeAvailablePacks(ClosureSnapshots.snapshot()
                .pack("fixture", "1.0.0")
                .held(ArtifactKind.MISSION_TYPE, "software-dev")
                .fingerprint("sha256:source")
                .closureFingerprint("sha256:closure")
                .build());
        packs.active((ref, query) -> ref.equals(SOFTWARE_DEV));
        packs.prompts(ask -> new EffectivePrompt(
                "# Plan\n",
                Optional.of("fixture"),
                List.of(new EffectivePrompt.Slot(
                        "spdd:reasons-block",
                        List.of(new EffectivePrompt.Fragment(
                                "fixture", "fixture-pack", PackLevel.ORG, "spdd-plan", "Fill.\n")))),
                List.of()));
        Served served = new Served(StandaloneActivationMvc.over(new InMemoryActivationRepository(), packs), packs);
        MockHttpServletResponse created = served.send(put("/api/v1/activations/a-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schemaVersion\": 2, \"packs\": [{\"packId\": \"fixture\"}],"
                        + " \"selected\": {\"mission_type\": [\"software-dev\"]}}"));
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        return served;
    }

    @Test
    void thePromptAnswersItsBodyWithItsTagAndTheFingerprintItVerified() throws Exception {
        Served served = served();

        MockHttpServletResponse answer = served.send(get(PROMPT));

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        assertThat(answer.getHeader("ETag")).matches("\"sha256:[0-9a-f]{64}\"");
        assertThat(answer.getHeader(HeaderNames.SOURCE_FINGERPRINT)).isEqualTo("sha256:source");
        JsonNode body = JSON.readTree(answer.getContentAsString());
        assertThat(body.at("/prompt").asString()).isEqualTo("# Plan\n");
        assertThat(body.at("/promptPackId").asString()).isEqualTo("fixture");
        assertThat(body.at("/governanceFingerprint").asString()).isEqualTo("sha256:closure");
        assertThat(body.at("/slots/0/slot").asString()).isEqualTo("spdd:reasons-block");
        assertThat(body.at("/slots/0/fragments/0/packName").asString()).isEqualTo("fixture-pack");
        assertThat(body.at("/slots/0/fragments/0/level").asString()).isEqualTo("org");
        assertThat(body.at("/diagnostics").isArray()).isTrue();
        assertThat(body.at("/_links/self/href").asString()).isEqualTo(PROMPT);
        assertThat(served.packs().promptAsks()).singleElement().satisfies(ask -> {
            assertThat(ask.missionType()).isEqualTo("software-dev");
            assertThat(ask.action()).isEqualTo("plan");
        });
    }

    @Test
    void aMatchingIfNoneMatchIs304WithTheTagAndNoBodyOrFingerprint() throws Exception {
        Served served = served();
        String tag = served.send(get(PROMPT)).getHeader("ETag");

        MockHttpServletResponse answer = served.send(get(PROMPT).header("If-None-Match", tag));

        assertThat(answer.getStatus()).isEqualTo(304);
        assertThat(answer.getHeader("ETag")).isEqualTo(tag);
        assertThat(answer.getContentAsString()).isEmpty();
        assertThat(answer.getHeader(HeaderNames.SOURCE_FINGERPRINT)).isNull();
        assertThat(served.packs().promptAsks())
                .as("only the first read read the prompt")
                .hasSize(1);
    }

    @Test
    void aMissionTypeNotInForceIs409EvenAgainstAWildcardIfNoneMatch() throws Exception {
        Served served = served();
        served.packs().active((ref, query) -> false);

        MockHttpServletResponse answer = served.send(get(PROMPT).header("If-None-Match", "*"));

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(409);
        assertThat(JSON.readTree(answer.getContentAsString()).at("/code").asString())
                .isEqualTo("mission_type_not_in_force");
        assertThat(answer.getHeader(HeaderNames.SOURCE_FINGERPRINT)).isNull();
        assertThat(served.packs().promptAsks()).isEmpty();
    }

    @Test
    void aMissionTypeOutsideItsPatternIs400BeforeAnythingIsRead() throws Exception {
        Served served = served();
        int calls = served.packs().calls();

        MockHttpServletResponse answer =
                served.send(get("/api/v1/activations/a-1/mission-types/Software-Dev/actions/plan/prompt"));

        assertThat(answer.getStatus()).isEqualTo(400);
        assertThat(JSON.readTree(answer.getContentAsString()).has("code")).isFalse();
        assertThat(served.packs().calls()).isEqualTo(calls);
    }
}
