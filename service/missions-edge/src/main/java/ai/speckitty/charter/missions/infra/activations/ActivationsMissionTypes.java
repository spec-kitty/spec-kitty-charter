package ai.speckitty.charter.missions.infra.activations;

import ai.speckitty.charter.activations.application.EffectiveMissionTypeAnswer;
import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.application.HeldBytes;
import ai.speckitty.charter.activations.application.HeldMissionType;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.missions.application.ActivationMissionTypes;
import ai.speckitty.charter.missions.application.EffectiveMissionTypeHolding;
import ai.speckitty.charter.missions.application.MissionTypeHolding;
import ai.speckitty.charter.missions.application.StoredFile;
import ai.speckitty.charter.missions.domain.MissionType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The one bean of {@link ActivationMissionTypes}: it asks the activations slice's application ring
 * ({@link EffectiveReadService#missionType}) and turns its answer into the missions slice's own
 * {@link MissionTypeHolding}, the same value the pack-scoped reads build from. The activations slice holds the winning
 * type as a {@link CharterArtifact}; the pack store binds it to {@link MissionType}, so any other artifact here is a
 * bug, never a refusal. It decides nothing: the refusals and their order are the activations slice's, unchanged.
 */
@Component
public final class ActivationsMissionTypes implements ActivationMissionTypes {

    private final EffectiveReadService effective;

    /**
     * An adapter over the activations slice's effective reads.
     *
     * @param effective the reads of what an activation puts in force
     */
    @Autowired
    public ActivationsMissionTypes(EffectiveReadService effective) {
        this.effective = Objects.requireNonNull(effective, "effective");
    }

    @Override
    public EffectiveMissionTypeHolding holding(String activationId, String missionTypeId) {
        EffectiveMissionTypeAnswer answer = effective.missionType(new ActivationId(activationId), missionTypeId);
        HeldMissionType held = answer.held();
        MissionType type = narrowed(held.missionType());
        String prefix = type.localId() + "/";
        Map<String, MissionTypeHolding.StepFiles> files = new LinkedHashMap<>();
        held.stepFiles()
                .forEach((step, stored) -> files.put(
                        step, new MissionTypeHolding.StepFiles(stored(stored.prompt()), stored(stored.guidelines()))));
        return new EffectiveMissionTypeHolding(
                new PackIdentifier(held.packId()),
                new MissionTypeHolding(
                        type,
                        held.skeletons().stream()
                                .map(skeleton -> skeleton(skeleton, prefix))
                                .toList(),
                        held.stepContracts().stream()
                                .map(contract -> new MissionTypeHolding.BoundContract(contract.id(), contract.action()))
                                .toList(),
                        files,
                        answer.sourceFingerprint()));
    }

    /** The mission type the pack store bound; anything else is a bug of the store. */
    static MissionType narrowed(CharterArtifact artifact) {
        if (artifact instanceof MissionType type) {
            return type;
        }
        throw new IllegalStateException(
                "the activations read a mission type as " + artifact.getClass().getName());
    }

    /** A skeleton: its template id without the {@code <type>/} prefix, which a mission type's template id starts with. */
    private static MissionTypeHolding.StoredSkeleton skeleton(HeldMissionType.Skeleton skeleton, String prefix) {
        String id = skeleton.id();
        if (!id.startsWith(prefix)) {
            throw new IllegalStateException("a template of the type outside its directory: " + id);
        }
        return new MissionTypeHolding.StoredSkeleton(
                id.substring(prefix.length()), StoredFile.of(skeleton.content().bytes()));
    }

    private static Optional<StoredFile> stored(Optional<HeldBytes> bytes) {
        return bytes.map(held -> StoredFile.of(held.bytes()));
    }
}
