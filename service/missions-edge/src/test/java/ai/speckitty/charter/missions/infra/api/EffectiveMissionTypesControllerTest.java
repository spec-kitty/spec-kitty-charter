package ai.speckitty.charter.missions.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.api.ActivationApi;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.missions.application.ActivationMissionTypes;
import ai.speckitty.charter.missions.application.EffectiveMissionType;
import ai.speckitty.charter.missions.application.EffectiveMissionTypeReadService;
import ai.speckitty.charter.missions.application.EffectiveStepPlace;
import ai.speckitty.charter.missions.application.MissionTypeHolding;
import ai.speckitty.charter.missions.application.PackMissionTypes;
import ai.speckitty.charter.missions.application.PackRead;
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

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new EffectiveMissionTypesController(
                            new EffectiveMissionTypeReadService(
                                    new ActivationMissionTypes() {
                                        @Override
                                        public EffectiveMissionType missionType(String activation, String type) {
                                            return new EffectiveMissionType(
                                                    new PackIdentifier("proj"),
                                                    holding().type(),
                                                    FINGERPRINT,
                                                    Set.of());
                                        }

                                        @Override
                                        public EffectiveStepPlace stepPlace(
                                                String activation, String type, String step) {
                                            // main keeps review, which proj's winning type drops (A7).
                                            if (!step.equals("review")) {
                                                throw new CharterRefusal(
                                                        RefusalCode.ACTION_NOT_FOUND, "no step " + step);
                                            }
                                            return new EffectiveStepPlace(new PackIdentifier("main"), FINGERPRINT);
                                        }
                                    },
                                    new PackMissionTypes() {
                                        @Override
                                        public MissionTypeHolding holding(PackIdentifier pack, String type) {
                                            return pack.value().equals("main")
                                                    ? EffectiveMissionTypesControllerTest.replaced()
                                                    : EffectiveMissionTypesControllerTest.holding();
                                        }

                                        @Override
                                        public PackRead<MissionTypeHolding> revised(PackIdentifier pack, String type) {
                                            MissionTypeHolding held = EffectiveMissionTypesControllerTest.holding();
                                            return new PackRead<>(held, held.fingerprint(), PACK_REVISION);
                                        }
                                    }),
                            new MissionLinks("/api/v1")),
                    new GenericEffectiveRead())
            .build();

    @Test
    void theLiteralMissionTypePathWinsOverTheTemplatedEffectiveRead() throws Exception {
        mvc.perform(get("/api/v1/activations/a-1/artifacts/mission_type/software-dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("mission_type"))
                .andExpect(jsonPath("$.missionType.id").value("software-dev"));
        // The retired step contract has no literal read any more: the templated read gets its path.
        mvc.perform(get("/api/v1/activations/a-1/artifacts/mission_step_contract/specify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generic").value("mission_step_contract specify"));
        mvc.perform(get("/api/v1/activations/a-1/artifacts/directive/GUARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generic").value("directive GUARD"));
    }

    @Test
    void aConditionalOrHeadRequestReachesTheLiteralPathsTooSoNoGenericTagCanAnswerThem() throws Exception {
        for (String path : List.of("/api/v1/activations/a-1/artifacts/mission_type/software-dev")) {
            mvc.perform(get(path).header("If-None-Match", "*"))
                    .andExpect(status().isOk())
                    // the holding pack's revision is at hand, yet an activation read never answers it (C-002)
                    .andExpect(header().doesNotExist("ETag"))
                    .andExpect(jsonPath("$.generic").doesNotExist());
            mvc.perform(head(path)).andExpect(status().isOk()).andExpect(header().doesNotExist("ETag"));
        }
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
    void aStepLinksItsTypeAndItsSkeletonUnderTheActivation() throws Exception {
        mvc.perform(get("/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/specify"))
                .andExpect(status().isOk())
                .andExpect(header().string(HeaderNames.SOURCE_FINGERPRINT, FINGERPRINT))
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(jsonPath("$.prompt").value("# Specify\n"))
                .andExpect(jsonPath("$.modelTier").doesNotExist()) // the step advises no tier, so the key is absent
                .andExpect(jsonPath("$._links.self.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/specify"))
                .andExpect(jsonPath("$._links.mission-type.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_type/software-dev"))
                .andExpect(jsonPath("$._links.profile-selection.href")
                        .value("/api/v1/activations/a-1/mission-types/software-dev/actions/specify/profile-selection"))
                .andExpect(jsonPath("$._links.step-contract").doesNotExist())
                .andExpect(jsonPath("$._links.artifact").doesNotExist())
                .andExpect(jsonPath("$.expectedOutputArtifacts[0]._links.skeleton.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_type/software-dev/skeletons/divio~1x.md"));
    }

    /** L4: a step read from its place links its skeleton in the place's pack, whose read serves that version's. */
    @Test
    void aStepOnlyAReplacedVersionKeepsLinksItsSkeletonInThePlacesPack() throws Exception {
        mvc.perform(get("/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Review at main"))
                .andExpect(jsonPath("$._links.profile-selection.href")
                        .value("/api/v1/activations/a-1/mission-types/software-dev/actions/review/profile-selection"))
                .andExpect(jsonPath("$._links.self.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/review"))
                .andExpect(jsonPath("$._links.mission-type.href")
                        .value("/api/v1/activations/a-1/artifacts/mission_type/software-dev"))
                .andExpect(jsonPath("$.expectedOutputArtifacts[0]._links.skeleton.href")
                        .value("/api/v1/packs/main/artifacts/mission_type/software-dev/skeletons/divio~1r.md"));
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
                        Optional.empty(),
                        List.of(),
                        List.of(new ExpectedArtifact(
                                "input.spec.main",
                                ArtifactClass.INPUT,
                                "spec.md",
                                true,
                                Optional.of("templates/divio/x.md"))),
                        List.of()))
                .build();
        return new MissionTypeHolding(
                type,
                List.of(
                        new MissionTypeHolding.StoredSkeleton("divio/x.md", utf8("# X\n")),
                        new MissionTypeHolding.StoredSkeleton("x;y.md", utf8("# Semicolon\n"))),
                Map.of("specify", new MissionTypeHolding.StepFiles(Optional.of(utf8("# Specify\n")), Optional.empty())),
                FINGERPRINT,
                Set.of());
    }

    /** main's version, which proj's replaced: it keeps review, whose output starts from main's skeleton divio/r.md. */
    private static MissionTypeHolding replaced() {
        MissionType type = MissionType.builder()
                .id(new MissionTypeIdentifier("software-dev"))
                .packId(new PackIdentifier("main"))
                .schemaVersion("1.0")
                .title("Software Development at main")
                .description("Build software")
                .step(new MissionStep(
                        "review",
                        "Review at main",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        List.of(new ExpectedArtifact(
                                "input.review.notes",
                                ArtifactClass.INPUT,
                                "review.md",
                                true,
                                Optional.of("templates/divio/r.md"))),
                        List.of()))
                .build();
        return new MissionTypeHolding(
                type,
                List.of(new MissionTypeHolding.StoredSkeleton("divio/r.md", utf8("# R\n"))),
                Map.of(),
                FINGERPRINT,
                Set.of());
    }

    private static StoredFile utf8(String text) {
        return StoredFile.of(text.getBytes(StandardCharsets.UTF_8));
    }
}
