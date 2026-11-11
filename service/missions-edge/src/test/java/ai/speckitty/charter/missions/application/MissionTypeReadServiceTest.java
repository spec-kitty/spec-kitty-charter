package ai.speckitty.charter.missions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.missions.domain.ArtifactClass;
import ai.speckitty.charter.missions.domain.ExpectedArtifact;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.missions.domain.MissionTypeIdentifier;
import ai.speckitty.charter.missions.domain.StepDocument;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The mission-type reads of the missions slice (#99) over a fake {@link PackMissionTypes}: the type lists its steps then
 * its on-demand steps and its skeletons by name; a step serves its stored files, binds the step contract of its type
 * and action (the lowest id when several are) and the stored skeleton each output names; a skeleton is found by its D9
 * name and served as text when its bytes are valid UTF-8, else as base64.
 */
class MissionTypeReadServiceTest {

    private static final PackIdentifier PACK = new PackIdentifier("main");
    private static final String FINGERPRINT = "sha256:" + "a".repeat(64);
    private static final String REVISION = "sha256:" + "9".repeat(64);
    private static final String PROMPT =
            "---\ndescription: Create it\n---\n# Specify\n\n<!-- spdd:reasons-block:start -->\n";

    private final List<String> asked = new ArrayList<>();
    private MissionTypeHolding holding = holding(List.of(
            new MissionTypeHolding.BoundContract("specify-b", "specify"),
            new MissionTypeHolding.BoundContract("specify-a", "specify"),
            new MissionTypeHolding.BoundContract("plan-only", "plan")));

    private final MissionTypeReadService reads = new MissionTypeReadService(new PackMissionTypes() {
        @Override
        public MissionTypeHolding holding(PackIdentifier pack, String type) {
            throw new AssertionError("a pack-scoped read asks for the revised holding, in one verified read");
        }

        @Override
        public PackHolding revised(PackIdentifier pack, String type) {
            asked.add(pack.value() + " " + type);
            if (!type.equals("software-dev")) {
                throw new CharterRefusal(
                        RefusalCode.MISSION_TYPE_NOT_FOUND, "Pack main has no mission type " + type + ".");
            }
            return new PackHolding(holding, REVISION);
        }
    });

    @Test
    void theTypeListsItsStepsThenItsOnDemandStepsAndItsSkeletonsByNameInCodePointOrder() {
        PackRead<MissionTypeView> read = reads.missionType(PACK, "software-dev");

        assertThat(read.fingerprint()).isEqualTo(FINGERPRINT);
        assertThat(read.revision()).isEqualTo(REVISION);
        assertThat(read.value().type().localId()).isEqualTo("software-dev");
        assertThat(read.value().steps())
                .containsExactly(
                        new MissionTypeView.StepEntry("specify", "Specification", false),
                        new MissionTypeView.StepEntry("plan", "Implementation Planning", false),
                        new MissionTypeView.StepEntry("retrospect", "Retrospective", true));
        assertThat(read.value().skeletons())
                .containsExactly(
                        new MissionTypeView.SkeletonEntry("a~01b.md", "templates/a~1b.md"),
                        new MissionTypeView.SkeletonEntry("divio~1howto.md", "templates/divio/howto.md"),
                        new MissionTypeView.SkeletonEntry("spec-template.md", "templates/spec-template.md"));
        assertThat(asked).containsExactly("main software-dev");
    }

    @Test
    void aStepServesItsStoredFilesNotItsParsedDocuments() {
        StepView step = reads.step(PACK, "software-dev", "specify").value();

        assertThat(step.prompt()).contains(PROMPT);
        assertThat(step.guidelines()).contains("# Guidelines\n");
        assertThat(step.onDemand()).isFalse();
        assertThat(step.step().id()).isEqualTo("specify");
        assertThat(step.missionTypeId()).isEqualTo("software-dev");
    }

    @Test
    void aStepWithoutAGuidelinesFileHasNoGuidelinesAndAnOnDemandStepIsMarked() {
        PackRead<StepView> read = reads.step(PACK, "software-dev", "retrospect");
        StepView step = read.value();

        assertThat(read.revision()).as("the step answers the pack revision").isEqualTo(REVISION);

        assertThat(step.onDemand()).isTrue();
        assertThat(step.prompt()).contains("# Retrospect\n");
        assertThat(step.guidelines()).isEmpty();
    }

    @Test
    void aStepBindsTheStepContractOfItsTypeAndActionWithTheLowestIdWhenSeveralAre() {
        assertThat(reads.step(PACK, "software-dev", "specify").value().stepContractId())
                .contains("specify-a");
        assertThat(reads.step(PACK, "software-dev", "plan").value().stepContractId())
                .contains("plan-only");
        assertThat(reads.step(PACK, "software-dev", "retrospect").value().stepContractId())
                .isEmpty();
    }

    @Test
    void anOutputLinksTheStoredSkeletonItsTemplatePathNamesWithTheTemplatesPrefixStripped() {
        List<StepView.OutputView> outputs =
                reads.step(PACK, "software-dev", "specify").value().outputs();

        assertThat(outputs)
                .extracting(output -> output.artifact().artifactKey())
                .containsExactly("input.spec.main", "output.nested.main", "output.missing.main", "output.none.main");
        assertThat(outputs)
                .extracting(StepView.OutputView::skeletonName)
                .containsExactly(
                        Optional.of("spec-template.md"),
                        Optional.of("divio~1howto.md"),
                        Optional.empty(),
                        Optional.empty());
    }

    @Test
    void anUnknownStepIsAnActionNotFoundAfterTheType() {
        CharterRefusal refusal =
                catchThrowableOfType(CharterRefusal.class, () -> reads.step(PACK, "software-dev", "deploy"));

        assertThat(refusal.code()).isEqualTo(RefusalCode.ACTION_NOT_FOUND);
        assertThat(refusal.getMessage()).isEqualTo("Mission type software-dev of pack main has no step deploy.");
        assertThat(codeOf(() -> reads.step(PACK, "research-x", "deploy")))
                .isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND);
    }

    @Test
    void aUtf8SkeletonIsServedAsTextWithTheDigestAndSizeOfItsBytes() {
        PackRead<SkeletonView> read = reads.skeleton(PACK, "software-dev", "spec-template.md");

        assertThat(read.revision()).isEqualTo(REVISION);
        SkeletonView skeleton = read.value();
        assertThat(skeleton.name()).isEqualTo("spec-template.md");
        assertThat(skeleton.path()).isEqualTo("templates/spec-template.md");
        assertThat(skeleton.missionTypeId()).isEqualTo("software-dev");
        assertThat(skeleton.encoding()).isEqualTo(SkeletonView.Encoding.TEXT);
        assertThat(skeleton.content()).isEqualTo("# Spec ✓\n");
        assertThat(skeleton.sha256()).isEqualTo("sha256:" + sha256("# Spec ✓\n".getBytes(StandardCharsets.UTF_8)));
        assertThat(skeleton.size()).isEqualTo(11);
        assertThat(read.fingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void aSkeletonWhoseBytesAreNotUtf8IsServedAsBase64NeverAltered() {
        byte[] bytes = {(byte) 0x89, 'P', 'N', 'G', (byte) 0xff, 0x00};
        holding = holdingWith(new MissionTypeHolding.StoredSkeleton("logo.png", StoredFile.of(bytes)));

        SkeletonView skeleton = reads.skeleton(PACK, "software-dev", "logo.png").value();

        assertThat(skeleton.encoding()).isEqualTo(SkeletonView.Encoding.BASE64);
        assertThat(skeleton.content()).isEqualTo(Base64.getEncoder().encodeToString(bytes));
        assertThat(Base64.getDecoder().decode(skeleton.content())).isEqualTo(bytes);
        assertThat(skeleton.size()).isEqualTo(6);
    }

    @Test
    void aNestedSkeletonAndOneWhoseFileNameHoldsATildeAreFoundByTheirNames() {
        assertThat(reads.skeleton(PACK, "software-dev", "divio~1howto.md")
                        .value()
                        .path())
                .isEqualTo("templates/divio/howto.md");
        assertThat(reads.skeleton(PACK, "software-dev", "a~01b.md").value().path())
                .isEqualTo("templates/a~1b.md");
    }

    @Test
    void aNameThatDoesNotDecodeToASkeletonOfTheTypeIsSkeletonNotFound() {
        CharterRefusal refusal =
                catchThrowableOfType(CharterRefusal.class, () -> reads.skeleton(PACK, "software-dev", "nope.md"));

        assertThat(refusal.code()).isEqualTo(RefusalCode.SKELETON_NOT_FOUND);
        assertThat(refusal.getMessage()).isEqualTo("Mission type software-dev of pack main has no skeleton nope.md.");
        for (String name : List.of("howto.md", "divio~2howto.md", "a~1b.md", "divio~1howto.md~", "~")) {
            assertThat(codeOf(() -> reads.skeleton(PACK, "software-dev", name)))
                    .as(name)
                    .isEqualTo(RefusalCode.SKELETON_NOT_FOUND);
        }
        assertThat(codeOf(() -> reads.skeleton(PACK, "research-x", "spec-template.md")))
                .isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND);
    }

    private MissionTypeHolding holdingWith(MissionTypeHolding.StoredSkeleton skeleton) {
        return new MissionTypeHolding(
                holding.type(), List.of(skeleton), holding.stepContracts(), holding.stepFiles(), FINGERPRINT);
    }

    private static MissionTypeHolding holding(List<MissionTypeHolding.BoundContract> contracts) {
        MissionType type = MissionType.builder()
                .id(new MissionTypeIdentifier("software-dev"))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Software Development")
                .description("Build software")
                .step(step(
                        "specify",
                        "Specification",
                        true,
                        true,
                        output("input.spec.main", Optional.of("templates/spec-template.md")),
                        output("output.nested.main", Optional.of("templates/divio/howto.md")),
                        output("output.missing.main", Optional.of("templates/missing.md")),
                        output("output.none.main", Optional.empty())))
                .step(step("plan", "Implementation Planning", true, false))
                .onDemandStep(step("retrospect", "Retrospective", true, false))
                .build();
        return new MissionTypeHolding(
                type,
                List.of(
                        new MissionTypeHolding.StoredSkeleton(
                                "spec-template.md", StoredFile.of("# Spec ✓\n".getBytes(StandardCharsets.UTF_8))),
                        new MissionTypeHolding.StoredSkeleton(
                                "divio/howto.md", StoredFile.of("# How\n".getBytes(StandardCharsets.UTF_8))),
                        new MissionTypeHolding.StoredSkeleton(
                                "a~1b.md", StoredFile.of("# A\n".getBytes(StandardCharsets.UTF_8)))),
                contracts,
                Map.of(
                        "specify",
                        new MissionTypeHolding.StepFiles(
                                Optional.of(utf8(PROMPT)), Optional.of(utf8("# Guidelines\n"))),
                        "plan",
                        new MissionTypeHolding.StepFiles(Optional.of(utf8("# Plan\n")), Optional.empty()),
                        "retrospect",
                        new MissionTypeHolding.StepFiles(Optional.of(utf8("# Retrospect\n")), Optional.empty())),
                FINGERPRINT);
    }

    /** A step whose parsed documents differ from its stored files, so a test sees which one is served. */
    private static MissionStep step(
            String id, String title, boolean prompt, boolean guidelines, ExpectedArtifact... outputs) {
        StepDocument parsed = new StepDocument(Optional.of("Parsed " + id), Optional.empty(), "", List.of());
        return new MissionStep(
                id,
                title,
                Optional.empty(),
                Optional.empty(),
                prompt ? Optional.of(parsed) : Optional.empty(),
                guidelines ? Optional.of(parsed) : Optional.empty(),
                List.of(),
                List.of(outputs));
    }

    private static ExpectedArtifact output(String key, Optional<String> templatePath) {
        return new ExpectedArtifact(key, ArtifactClass.OUTPUT, "out.md", false, templatePath);
    }

    private static StoredFile utf8(String text) {
        return StoredFile.of(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RefusalCode codeOf(Runnable read) {
        return catchThrowableOfType(CharterRefusal.class, read::run).code();
    }
}
