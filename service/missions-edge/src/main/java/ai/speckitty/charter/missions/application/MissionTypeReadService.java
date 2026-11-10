package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.missions.domain.ExpectedArtifact;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The pack-scoped mission-type reads (#99): a mission type with its steps and skeletons, one step, and one skeleton.
 * Each read is one verified read of the {@link PackMissionTypes} port, so its answer, its links and its fingerprint name
 * the same inputs.
 *
 * <p>Refusals come in order (FR-009): the port judges the pack ({@code pack_not_found}, {@code pack_unreadable}) and then
 * the mission type ({@code mission_type_not_found}); then an unknown step is {@link RefusalCode#ACTION_NOT_FOUND}, since
 * a step is the action {@code action:<type>/<step>}, and a name that does not decode to a skeleton of the type is
 * {@link RefusalCode#SKELETON_NOT_FOUND}. Nothing outside the type's own skeletons is ever looked up.
 */
@Service
public final class MissionTypeReadService {

    private static final String TEMPLATES = "templates/";

    private final PackMissionTypes packs;

    /**
     * A read service over the packs port.
     *
     * @param packs the port that reads what a pack holds of a mission type
     */
    @Autowired
    public MissionTypeReadService(PackMissionTypes packs) {
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    /**
     * A pack's mission type with its steps, then its on-demand steps, and its skeletons by name.
     *
     * @param pack the pack's identity
     * @param missionTypeId the mission type's id
     * @return the mission type's view and the verified fingerprint
     * @throws CharterRefusal as {@link PackMissionTypes#holding} refuses
     */
    public Fingerprinted<MissionTypeView> missionType(PackIdentifier pack, String missionTypeId) {
        MissionTypeHolding holding = packs.holding(pack, missionTypeId);
        MissionType type = holding.type();
        List<MissionTypeView.StepEntry> steps = Stream.concat(
                        type.steps().stream()
                                .map(step -> new MissionTypeView.StepEntry(step.id(), step.title(), false)),
                        type.onDemandSteps().stream()
                                .map(step -> new MissionTypeView.StepEntry(step.id(), step.title(), true)))
                .toList();
        List<MissionTypeView.SkeletonEntry> skeletons = holding.skeletons().stream()
                .map(skeleton -> new MissionTypeView.SkeletonEntry(
                        SkeletonNames.encode(skeleton.path()), TEMPLATES + skeleton.path()))
                .sorted(Comparator.comparing(MissionTypeView.SkeletonEntry::name, MissionTypeReadService::byCodePoint))
                .toList();
        return new Fingerprinted<>(new MissionTypeView(type, steps, skeletons), holding.fingerprint());
    }

    /**
     * One step of a pack's mission type, from its steps or its on-demand steps.
     *
     * @param pack the pack's identity
     * @param missionTypeId the mission type's id
     * @param stepId the step's id
     * @return the step's view and the verified fingerprint
     * @throws CharterRefusal as {@link PackMissionTypes#holding} refuses, then with
     *     {@link RefusalCode#ACTION_NOT_FOUND} when the type holds no such step
     */
    public Fingerprinted<StepView> step(PackIdentifier pack, String missionTypeId, String stepId) {
        MissionTypeHolding holding = packs.holding(pack, missionTypeId);
        MissionType type = holding.type();
        Optional<MissionStep> inSequence =
                type.steps().stream().filter(s -> s.id().equals(stepId)).findFirst();
        Optional<MissionStep> onDemand =
                type.onDemandSteps().stream().filter(s -> s.id().equals(stepId)).findFirst();
        MissionStep step = inSequence
                .or(() -> onDemand)
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.ACTION_NOT_FOUND,
                        "Mission type " + missionTypeId + " of pack " + pack.value() + " has no step " + stepId + "."));
        MissionTypeHolding.StepFiles files =
                holding.stepFiles().getOrDefault(stepId, MissionTypeHolding.StepFiles.NONE);
        List<StepView.OutputView> outputs = step.expectedOutputArtifacts().stream()
                .map(output -> new StepView.OutputView(output, boundSkeleton(holding, output)))
                .toList();
        return new Fingerprinted<>(
                new StepView(
                        missionTypeId,
                        step,
                        inSequence.isEmpty(),
                        files.prompt().map(MissionTypeReadService::utf8),
                        files.guidelines().map(MissionTypeReadService::utf8),
                        boundContract(holding, stepId),
                        outputs),
                holding.fingerprint());
    }

    /**
     * One skeleton of a pack's mission type, by its one-segment name (D9).
     *
     * @param pack the pack's identity
     * @param missionTypeId the mission type's id
     * @param name the skeleton's name
     * @return the skeleton's view and the verified fingerprint
     * @throws CharterRefusal as {@link PackMissionTypes#holding} refuses, then with
     *     {@link RefusalCode#SKELETON_NOT_FOUND} when the name does not decode to a skeleton of the type
     */
    public Fingerprinted<SkeletonView> skeleton(PackIdentifier pack, String missionTypeId, String name) {
        MissionTypeHolding holding = packs.holding(pack, missionTypeId);
        Optional<String> path = SkeletonNames.decode(name);
        MissionTypeHolding.StoredSkeleton skeleton = holding.skeletons().stream()
                .filter(stored -> path.filter(stored.path()::equals).isPresent())
                .findFirst()
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.SKELETON_NOT_FOUND,
                        "Mission type " + missionTypeId + " of pack " + pack.value() + " has no skeleton " + name
                                + "."));
        StoredFile content = skeleton.content();
        Optional<String> text = content.text();
        return new Fingerprinted<>(
                new SkeletonView(
                        SkeletonNames.encode(skeleton.path()),
                        TEMPLATES + skeleton.path(),
                        missionTypeId,
                        text.isPresent() ? SkeletonView.Encoding.TEXT : SkeletonView.Encoding.BASE64,
                        text.orElseGet(() -> Base64.getEncoder().encodeToString(content.bytes())),
                        "sha256:" + content.sha256(),
                        content.size()),
                holding.fingerprint());
    }

    /** The step contract whose action is the step, the lowest id in code-point order when several are. */
    private static Optional<String> boundContract(MissionTypeHolding holding, String stepId) {
        return holding.stepContracts().stream()
                .filter(contract -> contract.action().equals(stepId))
                .map(MissionTypeHolding.BoundContract::id)
                .min(MissionTypeReadService::byCodePoint);
    }

    /**
     * The name of the stored skeleton an output's {@code templatePath} names, its {@code templates/} prefix stripped;
     * empty when it names none, or a skeleton the type does not store (never a broken link).
     */
    private static Optional<String> boundSkeleton(MissionTypeHolding holding, ExpectedArtifact output) {
        return output.templatePath()
                .filter(path -> path.startsWith(TEMPLATES))
                .map(path -> path.substring(TEMPLATES.length()))
                .filter(path -> holding.skeletons().stream()
                        .anyMatch(stored -> stored.path().equals(path)))
                .map(SkeletonNames::encode);
    }

    /** A step file's text: the store parsed it as Markdown text, so bytes that are not UTF-8 are a bug. */
    private static String utf8(StoredFile file) {
        return file.text().orElseThrow(() -> new IllegalStateException("a step file that is not UTF-8: " + file));
    }

    /** Code-point order, which {@link String#compareTo} is not beyond the Basic Multilingual Plane. */
    private static int byCodePoint(String left, String right) {
        return Arrays.compare(left.codePoints().toArray(), right.codePoints().toArray());
    }
}
