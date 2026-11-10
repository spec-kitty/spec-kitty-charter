package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The mission-type reads under an activation (#99, FR-007): the mission type the activation's merged closure holds,
 * one step and one skeleton of it. Each read composes two reads (D12): the activation's winner of the type
 * ({@link ActivationMissionTypes}: the type, the pack that holds it, the merged closure's step contracts bound to it and
 * the source fingerprint) and what that pack stores beside the type ({@link PackMissionTypes}: its skeletons and step
 * files). The two must be read at one source fingerprint, else the answer would mix two states of the packs and is
 * refused {@link RefusalCode#STALE_INPUT}. The view is then built as the pack-scoped read builds it
 * ({@link MissionTypeReadService}), from one {@link MissionTypeHolding}; its step contracts are the merged closure's
 * winners, never the holding pack's own.
 *
 * <p>Refusals come in order: the activations port judges the activation ({@code activation_not_found}) and then the
 * mission type ({@code mission_type_not_found}); then an unknown step is {@link RefusalCode#ACTION_NOT_FOUND} and a name
 * that does not decode to a skeleton of the holding pack's type is {@link RefusalCode#SKELETON_NOT_FOUND}.
 */
@Service
public final class EffectiveMissionTypeReadService {

    private static final String STALE = "The packs changed while the mission type was read; read it again.";

    private final ActivationMissionTypes activations;
    private final PackMissionTypes packs;
    private final StepContracts contracts;

    /**
     * A read service over the activations port and the packs ports.
     *
     * @param activations the port that reads the winner of a mission type under an activation
     * @param packs the port that reads what a pack holds of a mission type
     * @param contracts the port that narrows a step contract
     */
    @Autowired
    public EffectiveMissionTypeReadService(
            ActivationMissionTypes activations, PackMissionTypes packs, StepContracts contracts) {
        this.activations = Objects.requireNonNull(activations, "activations");
        this.packs = Objects.requireNonNull(packs, "packs");
        this.contracts = Objects.requireNonNull(contracts, "contracts");
    }

    /**
     * The mission type in force under an activation, with its steps and skeletons, and the pack that holds it.
     *
     * @param activationId the activation's id
     * @param missionTypeId the mission type's id
     * @return the view, the pack that holds the winner and the verified fingerprint
     * @throws CharterRefusal as {@link ActivationMissionTypes#missionType} or {@link PackMissionTypes#holding}
     *     refuses, or with {@link RefusalCode#STALE_INPUT} when the two were read at different fingerprints
     */
    public Held<MissionTypeView> missionType(String activationId, String missionTypeId) {
        EffectiveMissionType winner = activations.missionType(activationId, missionTypeId);
        return new Held<>(MissionTypeReadService.missionTypeOf(holding(winner)), winner.holder());
    }

    /**
     * One step of the mission type in force under an activation.
     *
     * @param activationId the activation's id
     * @param missionTypeId the mission type's id
     * @param stepId the step's id
     * @return the step's view and the verified fingerprint
     * @throws CharterRefusal as {@link ActivationMissionTypes#missionType} or {@link PackMissionTypes#holding}
     *     refuses, or with {@link RefusalCode#STALE_INPUT} when the two were read at different fingerprints, then with
     *     {@link RefusalCode#ACTION_NOT_FOUND} when the winning type holds no such step
     */
    public Fingerprinted<StepView> step(String activationId, String missionTypeId, String stepId) {
        MissionTypeHolding holding = holding(activations.missionType(activationId, missionTypeId));
        return MissionTypeReadService.stepOf(holding, scopeOf(activationId), missionTypeId, stepId);
    }

    /**
     * One skeleton of the mission type in force under an activation, from the pack that holds the winner.
     *
     * @param activationId the activation's id
     * @param missionTypeId the mission type's id
     * @param name the skeleton's name (D9)
     * @return the skeleton's view and the verified fingerprint
     * @throws CharterRefusal as {@link ActivationMissionTypes#missionType} or {@link PackMissionTypes#holding}
     *     refuses, or with {@link RefusalCode#STALE_INPUT} when the two were read at different fingerprints, then with
     *     {@link RefusalCode#SKELETON_NOT_FOUND} when the name does not decode to a skeleton of the holding pack's type
     */
    public Fingerprinted<SkeletonView> skeleton(String activationId, String missionTypeId, String name) {
        MissionTypeHolding holding = holding(activations.missionType(activationId, missionTypeId));
        return MissionTypeReadService.skeletonOf(holding, scopeOf(activationId), missionTypeId, name);
    }

    /**
     * The winner of the type with what the pack that holds it stores beside it, at one fingerprint: the holding pack's
     * skeletons and step files, and the merged closure's step contracts bound to the type.
     */
    private MissionTypeHolding holding(EffectiveMissionType winner) {
        MissionTypeHolding stored = packs.holding(winner.holder(), winner.type().localId());
        if (!stored.fingerprint().equals(winner.fingerprint())) {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, STALE);
        }
        return new MissionTypeHolding(
                winner.type(),
                stored.skeletons(),
                winner.stepContracts().stream().map(contracts::bound).toList(),
                stored.stepFiles(),
                winner.fingerprint());
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
