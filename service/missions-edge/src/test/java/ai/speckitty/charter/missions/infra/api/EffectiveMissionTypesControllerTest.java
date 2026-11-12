package ai.speckitty.charter.missions.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.api.ActivationApi;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.missions.application.ActivationMissionTypes;
import ai.speckitty.charter.missions.application.EffectiveMissionType;
import ai.speckitty.charter.missions.application.EffectiveMissionTypeReadService;
import ai.speckitty.charter.missions.application.EffectiveStepContract;
import ai.speckitty.charter.missions.application.EffectiveStepPlace;
import ai.speckitty.charter.missions.application.HeldArtifact;
import ai.speckitty.charter.missions.application.MissionTypeHolding;
import ai.speckitty.charter.missions.application.PackMissionTypes;
import ai.speckitty.charter.missions.application.PackRead;
import ai.speckitty.charter.missions.application.StepContractView;
import ai.speckitty.charter.missions.application.StoredFile;
import ai.speckitty.charter.missions.domain.ArtifactClass;
import ai.speckitty.charter.missions.domain.ExpectedArtifact;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.missions.domain.MissionTypeIdentifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final String PACK_REVISION = "sha256:" + "e".repeat(64);

    /** specify, bound to software-dev's specify; its first step delegates to GUARD, which the closure holds, and NOPE. */
    private static final StepContractView STEP_CONTRACT = new StepContractView(
            "specify",
            "1.0",
            "specify",
            "software-dev",
            List.of(new StepContractView.Step(
                    "capture",
                    "Capture the intent",
                    Optional.of("run it"),
                    List.of(new StepContractView.Input("--json", "literal", Optional.of(true))),
                    Optional.of(new StepContractView.Delegation(ArtifactKind.DIRECTIVE, List.of("GUARD", "NOPE"))),
                    Optional.of("Ask first."))),
            List.of(new StepContractView.Gate(
                    "for_review", "check", Optional.empty(), "1.0", Optional.of(false), Optional.empty())));

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new EffectiveMissionTypesController(
                            new EffectiveMissionTypeReadService(
                                    new ActivationMissionTypes() {
                                        @Override
                                        public EffectiveMissionType missionType(String activation, String type) {
                                            return new EffectiveMissionType(
                                                    new PackIdentifier("proj"),
                                                    holding().type(),
                                                    List.of(mock(CharterArtifact.class)),
                                                    FINGERPRINT);
                                        }

                                        @Override
                                        public EffectiveStepContract stepContract(String activation, String id) {
                                            return new EffectiveStepContract(
                                                    new PackIdentifier("main"),
                                                    mock(CharterArtifact.class),
                                                    Optional.of(holding().type()),
                                                    Set.of(new HeldArtifact(ArtifactKind.DIRECTIVE, "GUARD")),
                                                    FINGERPRINT);
                                        }

                                        @Override
                                        public EffectiveStepPlace stepPlace(
                                                String activation, String type, String step) {
                                            throw new CharterRefusal(RefusalCode.ACTION_NOT_FOUND, "no step " + step);
                                        }
                                    },
                                    new PackMissionTypes() {
                                        @Override
                                        public MissionTypeHolding holding(PackIdentifier pack, String type) {
                                            return EffectiveMissionTypesControllerTest.holding();
                                        }

                                        @Override
                                        public PackRead<MissionTypeHolding> revised(PackIdentifier pack, String type) {
                                            MissionTypeHolding held = EffectiveMissionTypesControllerTest.holding();
                                            return new PackRead<>(held, held.fingerprint(), PACK_REVISION);
                                        }
                                    },
                                    (contract, read) -> STEP_CONTRACT),
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
                .andExpect(jsonPath("$.kind").value("mission_step_contract"))
                .andExpect(jsonPath("$.missionStepContract.id").value("specify"));
        mvc.perform(get("/api/v1/activations/a-1/artifacts/directive/GUARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generic").value("directive GUARD"));
    }

    @Test
    void aConditionalOrHeadRequestReachesTheLiteralPathsTooSoNoGenericTagCanAnswerThem() throws Exception {
        for (String path : List.of(
                "/api/v1/activations/a-1/artifacts/mission_type/software-dev",
                "/api/v1/activations/a-1/artifacts/mission_step_contract/specify")) {
            mvc.perform(get(path).header("If-None-Match", "*"))
                    .andExpect(status().isOk())
                    // the holding pack's revision is at hand, yet an activation read never answers it (C-002)
                    .andExpect(header().doesNotExist("ETag"))
                    .andExpect(jsonPath("$.generic").doesNotExist());
            mvc.perform(head(path)).andExpect(status().isOk()).andExpect(header().doesNotExist("ETag"));
        }
    }

    @Test
    void aStepContractNamesItsHoldingPackAndLinksItsTypeStepAndHeldCandidatesUnderTheActivation() throws Exception {
        mvc.perform(get("/api/v1/activations/a-1/artifacts/mission_step_contract/specify"))
                .andExpect(header().string(HeaderNames.SOURCE_FINGERPRINT, FINGERPRINT))
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(jsonPath("$.packId").value("main"))
                .andExpect(jsonPath("$.missionStepContract.mission").value("software-dev"))
                .andExpect(jsonPath("$.missionStepContract.steps[0].command").value("run it"))
                .andExpect(jsonPath("$.missionStepContract.steps[0].inputs[0].optional")
                        .value(true))
                .andExpect(jsonPath("$.missionStepContract.steps[0].guidance").value("Ask first."))
                .andExpect(jsonPath("$.missionStepContract.steps[0].delegatesTo.kind")
                        .value("directive"))
                .andExpect(jsonPath(
                                "$.missionStepContract.steps[0].delegatesTo.candidates[0]._links.effective-artifact.href")
                        .value("/api/v1/activations/a-1/artifacts/directive/GUARD"))
                .andExpect(jsonPath("$.missionStepContract.steps[0].delegatesTo.candidates[1].id")
                        .value("NOPE"))
                .andExpect(jsonPath("$.missionStepContract.steps[0].delegatesTo.candidates[1]._links")
                        .isEmpty())
                .andExpect(jsonPath("$.missionStepContract.gates[0].failOpen").value(false))
                .andExpect(jsonPath("$._links.self.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_step_contract/specify"))
                .andExpect(jsonPath("$._links.collection.href")
                        .value("/api/v1/activations/a-1/artifacts?kind=mission_step_contract"))
                .andExpect(jsonPath("$._links.mission-type.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_type/software-dev"))
                .andExpect(jsonPath("$._links.step.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/specify"))
                .andExpect(jsonPath("$._links.artifact.href")
                        .value("/api/v1/packs/main/artifacts/mission_step_contract/specify"));
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

    /**
     * D9: a skeleton whose name holds a {@code ;} is linked with it encoded ({@code %3B}), which the servlet stack would
     * otherwise read as the start of path parameters and drop, and the link reads it back.
     */
    @Test
    void aSkeletonWhoseNameHoldsASemicolonIsLinkedEncodedAndReadBack() throws Exception {
        String href = "/api/v1/activations/a-1/artifacts/mission_type/software-dev/skeletons/x%3By.md";
        mvc.perform(get("/api/v1/activations/a-1/artifacts/mission_type/software-dev"))
                .andExpect(jsonPath("$.missionType.skeletons[1].name").value("x;y.md"))
                .andExpect(
                        jsonPath("$.missionType.skeletons[1]._links.self.href").value(href));
        mvc.perform(get(new URI(href)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("x;y.md"))
                .andExpect(jsonPath("$.content").value("# Semicolon\n"))
                .andExpect(jsonPath("$._links.self.href").value(href));
    }

    /** A {@code \} is encoded as any character a segment may not hold (the embedded Tomcat refuses it; deferred). */
    @Test
    void aBackslashIsEncodedAsAnyCharacterASegmentMayNotHold() {
        assertThat(new MissionLinks("/api/v1/")
                        .activation("a 1")
                        .skeleton("software-dev", "a\\b;c~1d.md")
                        .getHref())
                .isEqualTo("/api/v1/activations/a%201/artifacts/mission_type/software-dev/skeletons/a%5Cb%3Bc~1d.md");
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
                List.of(
                        new MissionTypeHolding.StoredSkeleton("divio/x.md", utf8("# X\n")),
                        new MissionTypeHolding.StoredSkeleton("x;y.md", utf8("# Semicolon\n"))),
                List.of(),
                Map.of("specify", new MissionTypeHolding.StepFiles(Optional.of(utf8("# Specify\n")), Optional.empty())),
                FINGERPRINT);
    }

    private static StoredFile utf8(String text) {
        return StoredFile.of(text.getBytes(StandardCharsets.UTF_8));
    }
}
