package ai.speckitty.charter.missions.application;

/**
 * The port through which the mission-type reads ask an activation for what its merged closure holds of one mission type
 * (#99, FR-007). Its adapter, {@code missions.infra.activations}, is the one place of the slice that asks the activations
 * slice; the answer is missions-owned, the same {@link MissionTypeHolding} as the pack read, with the pack that holds the
 * winner beside it.
 */
@FunctionalInterface
public interface ActivationMissionTypes {

    /**
     * What an activation's merged closure holds of one mission type: the winner of the layer merge, the skeletons and
     * step files of the pack that holds it, and the merged closure's step contracts whose {@code mission} is the type.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param missionTypeId the mission type's id
     * @return the holding and the pack that holds the winner, at one fingerprint
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code activation_not_found}, then
     *     {@code mission_type_not_found} when no pack of the closure holds the type, {@code pack_unreadable} or the code
     *     of a closure that cannot compose or merge, or {@code stale_input} when the inputs changed while they were read
     */
    EffectiveMissionTypeHolding holding(String activationId, String missionTypeId);
}
