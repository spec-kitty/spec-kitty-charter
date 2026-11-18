package ai.speckitty.charter.missions.infra.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.api.ArtifactsApi;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.missions.application.HeldArtifact;
import ai.speckitty.charter.missions.application.MissionTypeHolding;
import ai.speckitty.charter.missions.application.MissionTypeReadService;
import ai.speckitty.charter.missions.application.PackMissionTypes;
import ai.speckitty.charter.missions.application.PackRead;
import ai.speckitty.charter.missions.application.StoredFile;
import ai.speckitty.charter.missions.domain.ArtifactClass;
import ai.speckitty.charter.missions.domain.ExpectedArtifact;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.missions.domain.MissionTypeIdentifier;
import ai.speckitty.charter.missions.domain.Substep;
import ai.speckitty.charter.missions.domain.SubstepCandidate;
import ai.speckitty.charter.missions.domain.SubstepDelegation;
import ai.speckitty.charter.missions.domain.SubstepInput;
import ai.speckitty.charter.web.RefusalHandler;
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
 * The mission-type controller over a fake pack (#99): its literal paths win over the generic
 * {@code /packs/{packId}/artifacts/{kind}/{artifactId}} (FR-006), which a stand-in of the generic read maps beside it,
 * and its answers carry the read's fingerprint and the links of the missions relations under the base path.
 */
class MissionTypeControllerTest {

    private static final String FINGERPRINT = "sha256:" + "b".repeat(64);
    private static final String REVISION = "sha256:" + "d".repeat(64);
    private static final String ETAG = "\"" + REVISION + "\"";

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new MissionTypeController(
                            new MissionTypeReadService(new PackMissionTypes() {
                                @Override
                                public MissionTypeHolding holding(PackIdentifier pack, String type) {
                                    throw new AssertionError("a pack-scoped read asks for the revised holding");
                                }

                                @Override
                                public PackRead<MissionTypeHolding> revised(PackIdentifier pack, String type) {
                                    if (!type.equals("software-dev")) {
                                        throw new CharterRefusal(
                                                RefusalCode.MISSION_TYPE_NOT_FOUND,
                                                "Pack main has no mission type " + type + ".");
                                    }
                                    MissionTypeHolding held = MissionTypeControllerTest.holding(pack);
                                    return new PackRead<>(held, held.fingerprint(), REVISION);
                                }
                            }),
                            new MissionLinks("/api/v1")),
                    new GenericItemRead())
            .setControllerAdvice(new RefusalHandler())
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
                .andExpect(header().string("ETag", ETAG))
                .andExpect(jsonPath("$.packId").value("main"))
                .andExpect(
                        jsonPath("$._links.self.href").value("/api/v1/packs/main/artifacts/mission_type/software-dev"))
                .andExpect(jsonPath("$._links.collection.href").value("/api/v1/packs/main/artifacts?kind=mission_type"))
                .andExpect(jsonPath("$.missionType.steps[0]._links.self.href")
                        .value("/api/v1/packs/main/artifacts/mission_type/software-dev/steps/specify"))
                .andExpect(jsonPath("$.missionType.skeletons[0].name").value("a~01b.md"))
                .andExpect(jsonPath("$.missionType.skeletons[0]._links.self.href")
                        .value("/api/v1/packs/main/artifacts/mission_type/software-dev/skeletons/a~01b.md"))
                .andExpect(jsonPath("$.missionType.skeletons[2].name").value("y".repeat(256)))
                .andExpect(jsonPath("$.missionType.skeletons[2]._links.self").doesNotExist())
                .andExpect(jsonPath("$.missionType.skeletons[3].name").value("z\\b.md"))
                .andExpect(jsonPath("$.missionType.skeletons[3]._links.self").doesNotExist());
    }

    @Test
    void aStepLinksItsTypeAndTheServableSkeletonsOfItsOutputs() throws Exception {
        mvc.perform(get("/api/v1/packs/main/artifacts/mission_type/software-dev/steps/specify"))
                .andExpect(status().isOk())
                .andExpect(header().string(HeaderNames.SOURCE_FINGERPRINT, FINGERPRINT))
                .andExpect(header().string("ETag", ETAG))
                .andExpect(jsonPath("$.prompt").value("# Specify\n"))
                .andExpect(jsonPath("$.modelTier").value("medium"))
                .andExpect(jsonPath("$._links.self.href")
                        .value("/api/v1/packs/main/artifacts/mission_type/software-dev/steps/specify"))
                .andExpect(jsonPath("$._links.mission-type.href")
                        .value("/api/v1/packs/main/artifacts/mission_type/software-dev"))
                .andExpect(jsonPath("$._links.step-contract").doesNotExist())
                .andExpect(jsonPath("$._links.profile-selection")
                        .doesNotExist()) // a pack selects no profile; only an activation's step links it (FR-012)
                .andExpect(jsonPath("$.expectedOutputArtifacts[0]._links.skeleton.href")
                        .value("/api/v1/packs/main/artifacts/mission_type/software-dev/skeletons/divio~1x.md"))
                .andExpect(
                        jsonPath("$.expectedOutputArtifacts[1]._links.skeleton").doesNotExist());
    }

    @Test
    void aStepServesItsSubstepsInAuthoredOrderEachHeldCandidateLinkedToItsPackRead() throws Exception {
        mvc.perform(get("/api/v1/packs/main/artifacts/mission_type/software-dev/steps/specify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.substeps[0].id").value("bootstrap"))
                .andExpect(
                        jsonPath("$.substeps[0].command").value("spec-kitty charter context --action specify --json"))
                .andExpect(jsonPath("$.substeps[0].inputs[0].flag").value("--mission"))
                .andExpect(jsonPath("$.substeps[0].inputs[0].source").value("mission_slug"))
                .andExpect(jsonPath("$.substeps[0].inputs[0].optional").value(true))
                .andExpect(jsonPath("$.substeps[0].delegatesTo").doesNotExist())
                .andExpect(jsonPath("$.substeps[1].id").value("capture-intent"))
                .andExpect(jsonPath("$.substeps[1].guidance").value("Ask first."))
                .andExpect(jsonPath("$.substeps[1].delegatesTo.kind").value("directive"))
                .andExpect(
                        jsonPath("$.substeps[1].delegatesTo.candidates[0].id").value("D1"))
                .andExpect(jsonPath("$.substeps[1].delegatesTo.candidates[0]._links.artifact.href")
                        .value("/api/v1/packs/main/artifacts/directive/D1"))
                .andExpect(
                        jsonPath("$.substeps[1].delegatesTo.candidates[1].id").value("base::D1"))
                .andExpect(jsonPath("$.substeps[1].delegatesTo.candidates[1]._links.artifact")
                        .doesNotExist());
    }

    @Test
    void aSkeletonIsReadByItsEscapedNameAndLinksItsType() throws Exception {
        mvc.perform(get("/api/v1/packs/main/artifacts/mission_type/software-dev/skeletons/divio~1x.md"))
                .andExpect(status().isOk())
                .andExpect(header().string(HeaderNames.SOURCE_FINGERPRINT, FINGERPRINT))
                .andExpect(header().string("ETag", ETAG))
                .andExpect(jsonPath("$.name").value("divio~1x.md"))
                .andExpect(jsonPath("$.path").value("templates/divio/x.md"))
                .andExpect(jsonPath("$.encoding").value("text"))
                .andExpect(jsonPath("$.size").value(4))
                .andExpect(jsonPath("$._links.self.href")
                        .value("/api/v1/packs/main/artifacts/mission_type/software-dev/skeletons/divio~1x.md"))
                .andExpect(jsonPath("$._links.mission-type.href")
                        .value("/api/v1/packs/main/artifacts/mission_type/software-dev"));
    }

    @Test
    void aRefusalAnswersNoETag() throws Exception {
        for (String path : List.of(
                "/api/v1/packs/main/artifacts/mission_type/research-x",
                "/api/v1/packs/main/artifacts/mission_type/software-dev/steps/nope",
                "/api/v1/packs/main/artifacts/mission_type/software-dev/skeletons/nope.md")) {
            mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(header().doesNotExist("ETag"));
        }
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
                        Optional.of(CostTier.MEDIUM),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        List.of(
                                new ExpectedArtifact(
                                        "input.spec.main",
                                        ArtifactClass.INPUT,
                                        "spec.md",
                                        true,
                                        Optional.of("templates/divio/x.md")),
                                new ExpectedArtifact(
                                        "output.long",
                                        ArtifactClass.OUTPUT,
                                        "long.md",
                                        false,
                                        Optional.of("templates/" + "y".repeat(256)))),
                        List.of(
                                new Substep(
                                        "bootstrap",
                                        "Load the context",
                                        Optional.of("spec-kitty charter context --action specify --json"),
                                        List.of(new SubstepInput("--mission", "mission_slug", true)),
                                        Optional.empty(),
                                        Optional.empty()),
                                new Substep(
                                        "capture-intent",
                                        "Capture the intent",
                                        Optional.empty(),
                                        List.of(),
                                        Optional.of(new SubstepDelegation(
                                                ArtifactKind.DIRECTIVE,
                                                List.of(
                                                        SubstepCandidate.parse("D1"),
                                                        SubstepCandidate.parse("base::D1")))),
                                        Optional.of("Ask first.")))))
                .build();
        return new MissionTypeHolding(
                type,
                List.of(
                        new MissionTypeHolding.StoredSkeleton("divio/x.md", utf8("# X\n")),
                        new MissionTypeHolding.StoredSkeleton("a~1b.md", utf8("# A\n")),
                        new MissionTypeHolding.StoredSkeleton("z\\b.md", utf8("# B\n")),
                        new MissionTypeHolding.StoredSkeleton("y".repeat(256), utf8("# Y\n"))),
                Map.of("specify", new MissionTypeHolding.StepFiles(Optional.of(utf8("# Specify\n")), Optional.empty())),
                FINGERPRINT,
                Set.of(new HeldArtifact(ArtifactKind.DIRECTIVE, "D1")));
    }

    private static StoredFile utf8(String text) {
        return StoredFile.of(text.getBytes(StandardCharsets.UTF_8));
    }
}
