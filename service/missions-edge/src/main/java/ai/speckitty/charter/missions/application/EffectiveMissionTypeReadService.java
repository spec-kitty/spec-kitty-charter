package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
 * <p>A step the winning type does not declare is read from the step's place (research R8, A7, DIV-243): the pack that
 * holds the merged winner of {@code action:<type>/<step>}, which keeps a step that only a version the winner replaced
 * declares, as the action context and the effective prompt read it. That pack's version of the type, its skeletons and
 * step files are read as the winner's are, at the same fingerprint, with the merged closure's step contracts.
 *
 * <p>Refusals come in order: the activations port judges the activation ({@code activation_not_found}) and then the
 * mission type ({@code mission_type_not_found}); then a step no version of the closure holds is
 * {@link RefusalCode#ACTION_NOT_FOUND}, judged by the activations port as the action context judges it, and a name that
 * does not decode to a skeleton of the holding pack's type is {@link RefusalCode#SKELETON_NOT_FOUND}.
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
     * One step of the mission type in force under an activation: the winning type's when it declares the step, else the
     * step of the version at the step's place (research R8, A7), so the step read answers the step the action context and
     * the effective prompt answer.
     *
     * @param activationId the activation's id
     * @param missionTypeId the mission type's id
     * @param stepId the step's id
     * @return the step's view, the verified fingerprint and, for a step the winning type does not declare, the pack of its
     *     place
     * @throws CharterRefusal as {@link ActivationMissionTypes#missionType} or {@link PackMissionTypes#holding}
     *     refuses, or with {@link RefusalCode#STALE_INPUT} when the reads were made at different fingerprints or the
     *     holding pack, or the pack of the step's place, is configured no longer or no longer holds the type at the
     *     second read; for a step the winning type does not declare, as {@link ActivationMissionTypes#stepPlace} refuses,
     *     {@link RefusalCode#ACTION_NOT_FOUND} when no version of the closure holds the step
     */
    public StepRead step(String activationId, String missionTypeId, String stepId) {
        EffectiveMissionType winner = activations.missionType(activationId, missionTypeId);
        if (winner.type().step(stepId).isPresent()) {
            return new StepRead(
                    MissionTypeReadService.stepOf(
                            holding(activationId, winner), scopeOf(activationId), missionTypeId, stepId),
                    Optional.empty());
        }
        EffectiveStepPlace place = activations.stepPlace(activationId, missionTypeId, stepId);
        if (!place.fingerprint().equals(winner.fingerprint())) {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, STALE);
        }
        MissionTypeHolding placed =
                holding(activationId, place.pack(), missionTypeId, winner.stepContracts(), winner.fingerprint());
        return new StepRead(
                MissionTypeReadService.stepOf(placed, scopeOf(activationId), missionTypeId, stepId),
                Optional.of(place.pack()));
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
     * whether the closure holds its mission type, whether the step read answers its step (the winning type declares it,
     * or a version at the step's place does, A7), and which of its delegation candidates the closure holds. One read of
     * the activations port answers all of it, and a second, of the step's place, when the winning type does not declare
     * the step.
     *
     * @param activationId the activation's id
     * @param stepContractId the step contract's id
     * @return the view, the pack that holds the winner and the verified fingerprint
     * @throws CharterRefusal as {@link ActivationMissionTypes#stepContract} or {@link ActivationMissionTypes#stepPlace}
     *     refuses, but {@code action_not_found}, which is no step; {@link RefusalCode#STALE_INPUT} when the two were read
     *     at different fingerprints
     */
    public Held<EffectiveStepContractView> stepContract(String activationId, String stepContractId) {
        EffectiveStepContract winner = activations.stepContract(activationId, stepContractId);
        StepContractView contract = contracts.view(
                winner.contract(),
                "The read of the step contract " + stepContractId + " under " + scopeOf(activationId));
        boolean stepHeld = winner.missionType()
                .map(type -> type.step(contract.action()).isPresent()
                        || placed(activationId, type.localId(), contract.action(), winner.fingerprint()))
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
        MissionTypeHolding stored =
                storedBeside(activationId, winner.holder(), winner.type().localId(), winner.fingerprint());
        return new MissionTypeHolding(
                winner.type(),
                stored.skeletons(),
                bound(activationId, winner.type().localId(), winner.stepContracts()),
                stored.stepFiles(),
                winner.fingerprint());
    }

    /**
     * The version of the type a pack of the closure holds, the pack of a step's place, with what it stores beside it and
     * the merged closure's step contracts bound to the type, at the winner's fingerprint.
     */
    private MissionTypeHolding holding(
            String activationId,
            PackIdentifier pack,
            String missionTypeId,
            List<CharterArtifact> stepContracts,
            String fingerprint) {
        MissionTypeHolding stored = storedBeside(activationId, pack, missionTypeId, fingerprint);
        return new MissionTypeHolding(
                stored.type(),
                stored.skeletons(),
                bound(activationId, missionTypeId, stepContracts),
                stored.stepFiles(),
                fingerprint);
    }

    /** The merged closure's step contracts bound to the type, as the views bind them. */
    private List<MissionTypeHolding.BoundContract> bound(
            String activationId, String missionTypeId, List<CharterArtifact> stepContracts) {
        String read = "The read of the mission type " + missionTypeId + " under " + scopeOf(activationId);
        return stepContracts.stream()
                .map(contract -> contracts.view(contract, read).bound())
                .toList();
    }

    /**
     * What a pack the activation's read named stores of the type, at the fingerprint of that read, else
     * {@link RefusalCode#STALE_INPUT}. The activation's read found the type, or the step, in that pack a moment ago, so a
     * pack that is configured no longer ({@code pack_not_found}) or no longer holds the type
     * ({@code mission_type_not_found}) means the packs changed between the two reads: {@code stale_input}, never a refusal
     * of a pack or type the request did not name. {@code pack_unreadable} is {@code stale_input} only when the packs
     * fingerprint now differs from the first read's (a pack file vanished between the two reads). With the fingerprint
     * unchanged it passes: the second read reads files the first does not (the step files), and one that stays unreadable
     * is no change to read again.
     */
    private MissionTypeHolding storedBeside(
            String activationId, PackIdentifier pack, String missionTypeId, String fingerprint) {
        MissionTypeHolding stored;
        try {
            stored = packs.holding(pack, missionTypeId);
        } catch (CharterRefusal refusal) {
            if (refusal.code() == RefusalCode.PACK_NOT_FOUND || refusal.code() == RefusalCode.MISSION_TYPE_NOT_FOUND) {
                throw new CharterRefusal(RefusalCode.STALE_INPUT, STALE, refusal);
            }
            if (refusal.code() == RefusalCode.PACK_UNREADABLE
                    && packsChangedSince(activationId, missionTypeId, fingerprint)) {
                throw new CharterRefusal(RefusalCode.STALE_INPUT, STALE, refusal);
            }
            throw refusal;
        }
        if (!stored.fingerprint().equals(fingerprint)) {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, STALE);
        }
        return stored;
    }

    /** Whether the activation's read now answers at another fingerprint; a read that refuses is not a known change. */
    private boolean packsChangedSince(String activationId, String missionTypeId, String fingerprint) {
        try {
            return !activations
                    .missionType(activationId, missionTypeId)
                    .fingerprint()
                    .equals(fingerprint);
        } catch (CharterRefusal refusal) {
            return false;
        }
    }

    /**
     * Whether the step has a place under the activation, so the step read answers it although the winning type does not
     * declare it (A7): asked of the activations port at the step contract's fingerprint, else
     * {@link RefusalCode#STALE_INPUT}. A step no version holds is no place, never a refusal of the step contract read.
     */
    private boolean placed(String activationId, String missionTypeId, String stepId, String fingerprint) {
        EffectiveStepPlace place;
        try {
            place = activations.stepPlace(activationId, missionTypeId, stepId);
        } catch (CharterRefusal refusal) {
            if (refusal.code() == RefusalCode.ACTION_NOT_FOUND) {
                return false;
            }
            throw refusal;
        }
        if (!place.fingerprint().equals(fingerprint)) {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, STALE);
        }
        return true;
    }

    private static String scopeOf(String activationId) {
        return "activation " + activationId;
    }

    /**
     * A step's read, with the pack of the step's place when the winning type does not declare the step: the pack whose
     * version of the type the step is read from, so that the step's links name it.
     *
     * @param read the step's view and the verified fingerprint
     * @param place the configured identifier of the pack of the step's place; empty for a step of the winning type
     */
    public record StepRead(Fingerprinted<StepView> read, Optional<PackIdentifier> place) {

        /**
         * Checks the fields.
         *
         * @param read the read
         * @param place the pack of the step's place, if the step is read from it
         */
        public StepRead {
            Objects.requireNonNull(read, "read");
            Objects.requireNonNull(place, "place");
        }
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
