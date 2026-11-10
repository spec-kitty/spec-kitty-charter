package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
     *     refuses, or with {@link RefusalCode#STALE_INPUT} when the two were read at different fingerprints or the
     *     holding pack is configured no longer or no longer holds the type at the second read
     */
    public Held<MissionTypeView> missionType(String activationId, String missionTypeId) {
        EffectiveMissionType winner = activations.missionType(activationId, missionTypeId);
        return new Held<>(MissionTypeReadService.missionTypeOf(holding(activationId, winner)), winner.holder());
    }

    /**
     * One step of the mission type in force under an activation.
     *
     * @param activationId the activation's id
     * @param missionTypeId the mission type's id
     * @param stepId the step's id
     * @return the step's view and the verified fingerprint
     * @throws CharterRefusal as {@link ActivationMissionTypes#missionType} or {@link PackMissionTypes#holding}
     *     refuses, or with {@link RefusalCode#STALE_INPUT} when the two were read at different fingerprints or the
     *     holding pack is configured no longer or no longer holds the type at the second read, then with
     *     {@link RefusalCode#ACTION_NOT_FOUND} when the winning type holds no such step
     */
    public Fingerprinted<StepView> step(String activationId, String missionTypeId, String stepId) {
        MissionTypeHolding holding = holding(activationId, activations.missionType(activationId, missionTypeId));
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
     *     refuses, or with {@link RefusalCode#STALE_INPUT} when the two were read at different fingerprints or the
     *     holding pack is configured no longer or no longer holds the type at the second read, then with
     *     {@link RefusalCode#SKELETON_NOT_FOUND} when the name does not decode to a skeleton of the holding pack's type
     */
    public Fingerprinted<SkeletonView> skeleton(String activationId, String missionTypeId, String name) {
        MissionTypeHolding holding = holding(activationId, activations.missionType(activationId, missionTypeId));
        return MissionTypeReadService.skeletonOf(holding, scopeOf(activationId), missionTypeId, name);
    }

    /**
     * The step contract in force under an activation (D11), with the pack that holds it: the winner of the layer merge,
     * whether the closure holds its mission type, whether the winning type declares its step, and which of its
     * delegation candidates the closure holds. One read of the activations port answers all of it.
     *
     * @param activationId the activation's id
     * @param stepContractId the step contract's id
     * @return the view, the pack that holds the winner and the verified fingerprint
     * @throws CharterRefusal as {@link ActivationMissionTypes#stepContract} refuses
     */
    public Held<EffectiveStepContractView> stepContract(String activationId, String stepContractId) {
        EffectiveStepContract winner = activations.stepContract(activationId, stepContractId);
        StepContractView contract = contracts.view(
                winner.contract(),
                "The read of the step contract " + stepContractId + " under " + scopeOf(activationId));
        boolean stepHeld = winner.missionType()
                .map(type -> Stream.concat(type.steps().stream(), type.onDemandSteps().stream())
                        .anyMatch(step -> step.id().equals(contract.action())))
                .orElse(false);
        Set<HeldArtifact> candidates = contract.steps().stream()
                .flatMap(step -> step.delegatesTo().stream())
                .flatMap(delegation -> delegation.candidates().stream()
                        .map(candidate -> new HeldArtifact(delegation.kind(), candidate)))
                .filter(winner.held()::contains)
                .collect(Collectors.toUnmodifiableSet());
        return new Held<>(
                new Fingerprinted<>(
                        new EffectiveStepContractView(
                                contract, winner.missionType().isPresent(), stepHeld, candidates),
                        winner.fingerprint()),
                winner.holder());
    }

    /**
     * The winner of the type with what the pack that holds it stores beside it, at one fingerprint: the holding pack's
     * skeletons and step files, and the merged closure's step contracts bound to the type.
     */
    private MissionTypeHolding holding(String activationId, EffectiveMissionType winner) {
        String read = "The read of the mission type " + winner.type().localId() + " under " + scopeOf(activationId);
        MissionTypeHolding stored = storedBeside(winner);
        if (!stored.fingerprint().equals(winner.fingerprint())) {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, STALE);
        }
        return new MissionTypeHolding(
                winner.type(),
                stored.skeletons(),
                winner.stepContracts().stream()
                        .map(contract -> contracts.view(contract, read).bound())
                        .toList(),
                stored.stepFiles(),
                winner.fingerprint());
    }

    /**
     * What the pack that holds the winner stores beside it. The activation's read found the type in that pack a moment
     * ago, so a pack that is configured no longer ({@code pack_not_found}) or no longer holds the type
     * ({@code mission_type_not_found}) means the packs changed between the two reads: {@link RefusalCode#STALE_INPUT},
     * never a refusal of a pack or type the request did not name. {@code pack_unreadable} passes unchanged: the second
     * read reads files the first does not (the step files), and one that stays unreadable is no change to read again.
     */
    private MissionTypeHolding storedBeside(EffectiveMissionType winner) {
        try {
            return packs.holding(winner.holder(), winner.type().localId());
        } catch (CharterRefusal refusal) {
            if (refusal.code() == RefusalCode.PACK_NOT_FOUND || refusal.code() == RefusalCode.MISSION_TYPE_NOT_FOUND) {
                throw new CharterRefusal(RefusalCode.STALE_INPUT, STALE, refusal);
            }
            throw refusal;
        }
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
