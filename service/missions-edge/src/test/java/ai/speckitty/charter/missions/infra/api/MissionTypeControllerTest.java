package ai.speckitty.charter.missions.infra.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.api.ArtifactsApi;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.missions.application.MissionTypeHolding;
import ai.speckitty.charter.missions.application.MissionTypeReadService;
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
 * The mission-type controller over a fake pack (#99): its literal paths win over the generic
 * {@code /packs/{packId}/artifacts/{kind}/{artifactId}} (FR-006), which a stand-in of the generic read maps beside it,
 * and its answers carry the read's fingerprint and the links of the missions relations under the base path.
 */
class MissionTypeControllerTest {

    private static final String FINGERPRINT = "sha256:" + "b".repeat(64);

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new MissionTypeController(
                            new MissionTypeReadService((pack, type) -> holding(pack)), new MissionLinks("/api/v1")),
                    new GenericItemRead())
            .build();

    @Test
    void theLiteralMissionTypePathWinsOverTheGenericKindPath() throws Exception {
        mvc.perform(get("/api/v1/packs/main/artifacts/mission_type/software-dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("mission_type"))
                .andExpect(jsonPath("$.missionType.id").value("software-dev"));
        mvc.perform(get("/api/v1/packs/main/artifacts/directive/GUARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generic").value("directive GUARD"));
        mvc.perform(get("/api/v1/packs/main/artifacts/mission_step_contract/specify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generic").value("mission_step_contract specify"));
    }

    @Test
    void theMissionTypeAnswerCarriesTheFingerprintAndItsLinks() throws Exception {
        mvc.perform(get("/api/v1/packs/main/artifacts/mission_type/software-dev"))
                .andExpect(header().string(HeaderNames.SOURCE_FINGERPRINT, FINGERPRINT))
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(jsonPath("$.packId").value("main"))
                .andExpect(
                        jsonPath("$._links.self.href").value("/api/v1/packs/main/artifacts/mission_type/software-dev"))
                .andExpect(jsonPath("$._links.collection.href").value("/api/v1/packs/main/artifacts?kind=mission_type"))
                .andExpect(jsonPath("$.missionType.steps[0]._links.self.href")
                        .value("/api/v1/packs/main/artifacts/mission_type/software-dev/steps/specify"))
                .andExpect(jsonPath("$.missionType.skeletons[0].name").value("a~01b.md"))
                .andExpect(jsonPath("$.missionType.skeletons[0]._links.self.href")
                        .value("/api/v1/packs/main/artifacts/mission_type/software-dev/skeletons/a~01b.md"));
    }

    @Test
    void aStepLinksItsTypeItsStepContractAndTheSkeletonOfItsOutput() throws Exception {
        mvc.perform(get("/api/v1/packs/main/artifacts/mission_type/software-dev/steps/specify"))
                .andExpect(status().isOk())
                .andExpect(header().string(HeaderNames.SOURCE_FINGERPRINT, FINGERPRINT))
                .andExpect(jsonPath("$.prompt").value("# Specify\n"))
                .andExpect(jsonPath("$._links.self.href")
                        .value("/api/v1/packs/main/artifacts/mission_type/software-dev/steps/specify"))
                .andExpect(jsonPath("$._links.mission-type.href")
                        .value("/api/v1/packs/main/artifacts/mission_type/software-dev"))
                .andExpect(jsonPath("$._links.step-contract.href")
                        .value("/api/v1/packs/main/artifacts/mission_step_contract/specify"))
                .andExpect(jsonPath("$.expectedOutputArtifacts[0]._links.skeleton.href")
                        .value("/api/v1/packs/main/artifacts/mission_type/software-dev/skeletons/divio~1x.md"));
    }

    @Test
    void aSkeletonIsReadByItsEscapedNameAndLinksItsType() throws Exception {
        mvc.perform(get("/api/v1/packs/main/artifacts/mission_type/software-dev/skeletons/divio~1x.md"))
                .andExpect(status().isOk())
                .andExpect(header().string(HeaderNames.SOURCE_FINGERPRINT, FINGERPRINT))
                .andExpect(jsonPath("$.name").value("divio~1x.md"))
                .andExpect(jsonPath("$.path").value("templates/divio/x.md"))
                .andExpect(jsonPath("$.encoding").value("text"))
                .andExpect(jsonPath("$.size").value(4))
                .andExpect(jsonPath("$._links.self.href")
                        .value("/api/v1/packs/main/artifacts/mission_type/software-dev/skeletons/divio~1x.md"))
                .andExpect(jsonPath("$._links.mission-type.href")
                        .value("/api/v1/packs/main/artifacts/mission_type/software-dev"));
    }

    /** A stand-in of the packs slice's generic item read, at the generated interface's path. */
    @RestController
    @RequestMapping("/api/v1")
    static class GenericItemRead {

        @GetMapping(ArtifactsApi.PATH_GET_PACK_ARTIFACT)
        ResponseEntity<Map<String, String>> item(
                @PathVariable("packId") String packId,
                @PathVariable("kind") String kind,
                @PathVariable("artifactId") String artifactId) {
            return ResponseEntity.ok(Map.of("generic", kind + " " + artifactId));
        }
    }

    private static MissionTypeHolding holding(PackIdentifier pack) {
        MissionType type = MissionType.builder()
                .id(new MissionTypeIdentifier("software-dev"))
                .packId(pack)
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
                        new MissionTypeHolding.StoredSkeleton("a~1b.md", utf8("# A\n"))),
                List.of(new MissionTypeHolding.BoundContract("specify", "specify")),
                Map.of("specify", new MissionTypeHolding.StepFiles(Optional.of(utf8("# Specify\n")), Optional.empty())),
                FINGERPRINT);
    }

    private static StoredFile utf8(String text) {
        return StoredFile.of(text.getBytes(StandardCharsets.UTF_8));
    }
}
