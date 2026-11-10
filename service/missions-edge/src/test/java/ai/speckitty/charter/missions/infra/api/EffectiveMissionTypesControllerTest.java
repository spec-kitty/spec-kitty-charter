package ai.speckitty.charter.missions.infra.api;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.api.ActivationApi;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.missions.application.EffectiveMissionType;
import ai.speckitty.charter.missions.application.EffectiveMissionTypeReadService;
import ai.speckitty.charter.missions.application.MissionTypeHolding;
import ai.speckitty.charter.missions.application.StoredFile;
import ai.speckitty.charter.missions.domain.ArtifactClass;
import ai.speckitty.charter.missions.domain.ExpectedArtifact;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.missions.domain.MissionTypeIdentifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The mission-type controller under an activation over a fake activation (#99, FR-007): its literal paths win over the
 * activation set's templated {@code /activations/{activationId}/artifacts/{kind}/{artifactId}}, which a stand-in maps
 * beside it; the envelope names the pack that holds the winner and links its pack read as {@code artifact}, and every
 * other link stays under the activation.
 */
class EffectiveMissionTypesControllerTest {

    private static final String FINGERPRINT = "sha256:" + "c".repeat(64);

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new EffectiveMissionTypesController(
                            new EffectiveMissionTypeReadService(
                                    (activation, type) -> new EffectiveMissionType(
                                            new PackIdentifier("proj"),
                                            holding().type(),
                                            List.of(mock(CharterArtifact.class)),
                                            FINGERPRINT),
                                    (pack, type) -> holding(),
                                    contract -> new MissionTypeHolding.BoundContract("specify", "specify")),
                            new MissionLinks("/api/v1")),
                    new GenericEffectiveRead())
            .build();

    @Test
    void theLiteralMissionTypePathWinsOverTheTemplatedEffectiveRead() throws Exception {
        mvc.perform(get("/api/v1/activations/a-1/artifacts/mission_type/software-dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("mission_type"))
                .andExpect(jsonPath("$.missionType.id").value("software-dev"));
        mvc.perform(get("/api/v1/activations/a-1/artifacts/mission_step_contract/specify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generic").value("mission_step_contract specify"));
    }

    @Test
    void theMissionTypeNamesItsHoldingPackLinksItsPackReadAndKeepsEveryOtherLinkUnderTheActivation() throws Exception {
        mvc.perform(get("/api/v1/activations/a-1/artifacts/mission_type/software-dev"))
                .andExpect(header().string(HeaderNames.SOURCE_FINGERPRINT, FINGERPRINT))
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(jsonPath("$.packId").value("proj"))
                .andExpect(jsonPath("$._links.self.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_type/software-dev"))
                .andExpect(jsonPath("$._links.collection.href")
                        .value("/api/v1/activations/a-1/artifacts?kind=mission_type"))
                .andExpect(jsonPath("$._links.artifact.href")
                        .value("/api/v1/packs/proj/artifacts/mission_type/software-dev"))
                .andExpect(jsonPath("$.missionType.steps[0]._links.self.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/specify"))
                .andExpect(jsonPath("$.missionType.skeletons[0]._links.self.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_type/software-dev/skeletons/divio~1x.md"));
    }

    @Test
    void aStepLinksItsTypeItsStepContractAndItsSkeletonUnderTheActivation() throws Exception {
        mvc.perform(get("/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/specify"))
                .andExpect(status().isOk())
                .andExpect(header().string(HeaderNames.SOURCE_FINGERPRINT, FINGERPRINT))
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(jsonPath("$.prompt").value("# Specify\n"))
                .andExpect(jsonPath("$._links.self.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/specify"))
                .andExpect(jsonPath("$._links.mission-type.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_type/software-dev"))
                .andExpect(jsonPath("$._links.step-contract.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_step_contract/specify"))
                .andExpect(jsonPath("$._links.artifact").doesNotExist())
                .andExpect(jsonPath("$.expectedOutputArtifacts[0]._links.skeleton.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_type/software-dev/skeletons/divio~1x.md"));
    }

    @Test
    void aSkeletonLinksItsTypeUnderTheActivation() throws Exception {
        mvc.perform(get("/api/v1/activations/a-1/artifacts/mission_type/software-dev/skeletons/divio~1x.md"))
                .andExpect(status().isOk())
                .andExpect(header().string(HeaderNames.SOURCE_FINGERPRINT, FINGERPRINT))
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(jsonPath("$.path").value("templates/divio/x.md"))
                .andExpect(jsonPath("$._links.self.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_type/software-dev/skeletons/divio~1x.md"))
                .andExpect(jsonPath("$._links.mission-type.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_type/software-dev"));
    }

    /** A stand-in of the activation set's templated effective read, at the generated interface's path. */
    @RestController
    @RequestMapping("/api/v1")
    static class GenericEffectiveRead {

        @GetMapping(ActivationApi.PATH_GET_EFFECTIVE_ARTIFACT)
        ResponseEntity<Map<String, String>> item(
                @PathVariable("activationId") String activationId,
                @PathVariable("kind") String kind,
                @PathVariable("artifactId") String artifactId) {
            return ResponseEntity.ok(Map.of("generic", kind + " " + artifactId));
        }
    }

    private static MissionTypeHolding holding() {
        MissionType type = MissionType.builder()
                .id(new MissionTypeIdentifier("software-dev"))
                .packId(new PackIdentifier("proj"))
                .schemaVersion("1.0")
                .title("Software Development")
                .description("Build software")
                .step(new MissionStep(
                        "specify",
                        "Specification",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        List.of(new ExpectedArtifact(
                                "input.spec.main",
                                ArtifactClass.INPUT,
                                "spec.md",
                                true,
                                Optional.of("templates/divio/x.md")))))
                .build();
        return new MissionTypeHolding(
                type,
                List.of(new MissionTypeHolding.StoredSkeleton("divio/x.md", utf8("# X\n"))),
                List.of(),
                Map.of("specify", new MissionTypeHolding.StepFiles(Optional.of(utf8("# Specify\n")), Optional.empty())),
                FINGERPRINT);
    }

    private static StoredFile utf8(String text) {
        return StoredFile.of(text.getBytes(StandardCharsets.UTF_8));
    }
}
