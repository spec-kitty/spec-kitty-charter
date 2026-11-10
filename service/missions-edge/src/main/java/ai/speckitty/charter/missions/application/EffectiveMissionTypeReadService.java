package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The mission-type reads under an activation (#99, FR-007): the mission type the activation's merged closure holds,
 * one step and one skeleton of it. Each read is one read of the {@link ActivationMissionTypes} port, and its view is
 * built as the pack-scoped read builds it ({@link MissionTypeReadService}), from the same {@link MissionTypeHolding}:
 * the winner of the layer merge, the skeletons and step files of the pack that holds it, and the merged closure's step
 * contracts bound to it.
 *
 * <p>Refusals come in order: the port judges the activation ({@code activation_not_found}) and then the mission type
 * ({@code mission_type_not_found}); then an unknown step is {@link RefusalCode#ACTION_NOT_FOUND} and a name that does
 * not decode to a skeleton of the holding pack's type is {@link RefusalCode#SKELETON_NOT_FOUND}.
 */
@Service
public final class EffectiveMissionTypeReadService {

    private final ActivationMissionTypes activations;

    /**
     * A read service over the activations port.
     *
     * @param activations the port that reads what an activation's merged closure holds of a mission type
     */
    @Autowired
    public EffectiveMissionTypeReadService(ActivationMissionTypes activations) {
        this.activations = Objects.requireNonNull(activations, "activations");
    }

    /**
     * The mission type in force under an activation, with its steps and skeletons, and the pack that holds it.
     *
     * @param activationId the activation's id
     * @param missionTypeId the mission type's id
     * @return the view, the pack that holds the winner and the verified fingerprint
     * @throws CharterRefusal as {@link ActivationMissionTypes#holding} refuses
     */
    public Held<MissionTypeView> missionType(String activationId, String missionTypeId) {
        EffectiveMissionTypeHolding held = activations.holding(activationId, missionTypeId);
        return new Held<>(MissionTypeReadService.missionTypeOf(held.holding()), held.holder());
    }

    /**
     * One step of the mission type in force under an activation.
     *
     * @param activationId the activation's id
     * @param missionTypeId the mission type's id
     * @param stepId the step's id
     * @return the step's view and the verified fingerprint
     * @throws CharterRefusal as {@link ActivationMissionTypes#holding} refuses, then with
     *     {@link RefusalCode#ACTION_NOT_FOUND} when the winning type holds no such step
     */
    public Fingerprinted<StepView> step(String activationId, String missionTypeId, String stepId) {
        EffectiveMissionTypeHolding held = activations.holding(activationId, missionTypeId);
        return MissionTypeReadService.stepOf(held.holding(), scopeOf(activationId), missionTypeId, stepId);
    }

    /**
     * One skeleton of the mission type in force under an activation, from the pack that holds the winner.
     *
     * @param activationId the activation's id
     * @param missionTypeId the mission type's id
     * @param name the skeleton's name (D9)
     * @return the skeleton's view and the verified fingerprint
     * @throws CharterRefusal as {@link ActivationMissionTypes#holding} refuses, then with
     *     {@link RefusalCode#SKELETON_NOT_FOUND} when the name does not decode to a skeleton of the holding pack's type
     */
    public Fingerprinted<SkeletonView> skeleton(String activationId, String missionTypeId, String name) {
        EffectiveMissionTypeHolding held = activations.holding(activationId, missionTypeId);
        return MissionTypeReadService.skeletonOf(held.holding(), scopeOf(activationId), missionTypeId, name);
    }

    private static String scopeOf(String activationId) {
        return "activation " + activationId;
    }

    /**
     * A read's answer with the pack that holds what it answers.
     *
     * @param read the view and the verified fingerprint
     * @param holder the configured identifier of the pack that holds the winner
     * @param <T> the type of the view
     */
    public record Held<T>(Fingerprinted<T> read, PackIdentifier holder) {

        /**
         * Checks the fields.
         *
         * @param read the read
         * @param holder the holding pack
         */
        public Held {
            Objects.requireNonNull(read, "read");
            Objects.requireNonNull(holder, "holder");
        }
    }
}
