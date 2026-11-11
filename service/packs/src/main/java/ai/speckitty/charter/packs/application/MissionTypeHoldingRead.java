package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.MissionStepContract;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCompanionFiles;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Template;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * What a pack holds of one mission type, in one verified read (#99): the type itself, the skeletons under its
 * {@code templates/} directory, the step contracts whose {@code mission} is the type, and the stored bytes of each
 * step's prompt and guidelines files. The missions slice builds its mission-type, step and skeleton reads from it, so
 * a read and its links name the same inputs, and the fingerprint it carries is the one those inputs give.
 *
 * <p>The packs rings cannot name the missions domain, so the type is held as a {@link CharterArtifact} and its step
 * files come from the {@link PackCompanionFiles} port, read between the read's two digests.
 *
 * <p>The pack is judged first, as the generic item read judges it: an identifier no configured pack answers to is
 * {@link RefusalCode#PACK_NOT_FOUND}, and a configured pack without a readable {@code pack.yaml} is
 * {@link RefusalCode#PACK_UNREADABLE}. Then a type the pack does not hold is
 * {@link RefusalCode#MISSION_TYPE_NOT_FOUND}.
 */
@Service
public final class MissionTypeHoldingRead {

    private static final String STALE = "The inputs changed while the mission type was read; read it again.";

    private final PackRepository packs;
    private final PackDescriptorRepository descriptors;
    private final ConsistentRead read;
    private final PackCompanionFiles companions;
    private final PackRevisions revisions;

    /**
     * What one pack holds of one mission type, read at one fingerprint.
     *
     * @param missionType the mission type, an artifact of kind {@code mission_type}
     * @param skeletons the templates under the type's {@code templates/} directory, by id in code-point order
     * @param stepContracts the step contracts of the pack whose {@code mission} is the type, by id in code-point order
     * @param stepFiles the stored prompt and guidelines files of each step, by step id
     * @param fingerprint the source fingerprint the read verified
     */
    public record Holding(
            CharterArtifact missionType,
            List<Template> skeletons,
            List<MissionStepContract> stepContracts,
            Map<String, PackCompanionFiles.StepFiles> stepFiles,
            String fingerprint) {

        /**
         * A holding, its lists and map copied.
         *
         * @param missionType the mission type
         * @param skeletons its skeletons
         * @param stepContracts the step contracts bound to it
         * @param stepFiles its step files
         * @param fingerprint the verified fingerprint
         */
        public Holding {
            Objects.requireNonNull(missionType, "missionType");
            skeletons = List.copyOf(skeletons);
            stepContracts = List.copyOf(stepContracts);
            stepFiles = Map.copyOf(stepFiles);
            Objects.requireNonNull(fingerprint, "fingerprint");
        }
    }

    /**
     * A read over the pack ports.
     *
     * @param packs the port that loads a pack aggregate
     * @param descriptors the port that reads a pack's descriptor, judged before the type
     * @param read the verifier that digests the inputs before and after the read
     * @param companions the port that reads the stored step files of a mission type
     * @param revisions the pack revision, read inside the verified read of a pack-scoped read for its {@code ETag}
     */
    @Autowired
    public MissionTypeHoldingRead(
            PackRepository packs,
            PackDescriptorRepository descriptors,
            ConsistentRead read,
            PackCompanionFiles companions,
            PackRevisions revisions) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.read = Objects.requireNonNull(read, "read");
        this.companions = Objects.requireNonNull(companions, "companions");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
    }

    /**
     * What a pack holds of one mission type.
     *
     * @param pack the pack's identity
     * @param missionTypeId the mission type's id
     * @return the type, its skeletons, its step contracts and its step files, with the verified fingerprint
     * @throws CharterRefusal with {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier, with
     *     {@link RefusalCode#PACK_UNREADABLE} when the pack has no readable {@code pack.yaml} or a file cannot be read,
     *     with {@link RefusalCode#MISSION_TYPE_NOT_FOUND} when the pack holds no such type, or with
     *     {@link RefusalCode#STALE_INPUT} when the inputs changed while it was read
     */
    public Holding holding(PackIdentifier pack, String missionTypeId) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(missionTypeId, "missionTypeId");
        ConsistentRead.Verified<Held> verified = read.read(STALE, () -> load(pack, missionTypeId));
        return holdingOf(verified.value(), verified.fingerprint());
    }

    /**
     * What a pack holds of one mission type with the pack revision it was read at, for a pack-scoped read's body and
     * {@code ETag}: both are taken inside the one verified read, so they name the same state. Only the pack-scoped
     * reads ask for it; a read under an activation answers its own tag and never computes a pack revision.
     *
     * @param pack the pack's identity
     * @param missionTypeId the mission type's id
     * @return the holding, with the verified fingerprint, and the pack's revision
     * @throws CharterRefusal as {@link #holding(PackIdentifier, String)} does
     */
    public Revised<Holding> revised(PackIdentifier pack, String missionTypeId) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(missionTypeId, "missionTypeId");
        ConsistentRead.Verified<Revised<Held>> verified =
                read.read(STALE, () -> new Revised<>(load(pack, missionTypeId), revisions.currentRevision(pack)));
        Revised<Held> held = verified.value();
        return new Revised<>(holdingOf(held.value(), verified.fingerprint()), held.revision());
    }

    private Held load(PackIdentifier pack, String missionTypeId) {
        if (descriptors.find(pack).isEmpty()) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + pack.value() + " has no pack.yaml");
        }
        Pack held = packs.load(pack);
        CharterArtifact type = held.artifacts(ArtifactKind.MISSION_TYPE).stream()
                .filter(candidate -> candidate.localId().equals(missionTypeId))
                .findFirst()
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.MISSION_TYPE_NOT_FOUND,
                        "Pack " + pack.value() + " has no mission type " + missionTypeId + "."));
        List<Template> skeletons = held.templates().stream()
                .filter(template -> template.missionType().equals(Optional.of(missionTypeId)))
                .sorted(Comparator.comparing(Template::localId, MissionTypeHoldingRead::byCodePoint))
                .toList();
        List<MissionStepContract> contracts =
                held.artifacts(ArtifactKind.MISSION_STEP_CONTRACT, MissionStepContract.class).stream()
                        .filter(contract -> contract.mission().equals(missionTypeId))
                        .sorted(Comparator.comparing(MissionStepContract::localId, MissionTypeHoldingRead::byCodePoint))
                        .toList();
        return new Held(type, skeletons, contracts, companions.stepFiles(pack, type));
    }

    private static Holding holdingOf(Held held, String fingerprint) {
        return new Holding(held.type(), held.skeletons(), held.contracts(), held.files(), fingerprint);
    }

    /** What the verified read loaded, before it carries its fingerprint. */
    private record Held(
            CharterArtifact type,
            List<Template> skeletons,
            List<MissionStepContract> contracts,
            Map<String, PackCompanionFiles.StepFiles> files) {}

    /** Code-point order, which {@link String#compareTo} is not beyond the Basic Multilingual Plane. */
    private static int byCodePoint(String left, String right) {
        return Arrays.compare(left.codePoints().toArray(), right.codePoints().toArray());
    }
}
