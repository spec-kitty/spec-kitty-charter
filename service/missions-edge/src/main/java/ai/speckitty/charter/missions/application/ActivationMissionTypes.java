package ai.speckitty.charter.missions.application;

/**
 * The port through which the mission-type reads ask an activation for what the activations slice owns of a mission type
 * (#99, FR-007, D12): the winner of the layer merge, the pack that holds it, the merged closure's step contracts bound
 * to it and the source fingerprint of the read. Its adapter, {@code missions.infra.activations}, is the one place of the
 * slice that asks the activations slice. What the holding pack stores beside the type is the {@link PackMissionTypes}
 * port's to read.
 */
public interface ActivationMissionTypes {

    /**
     * The winner of one mission type under an activation.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param missionTypeId the mission type's id
     * @return the winning type, its pack, the step contracts bound to it and the source fingerprint
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code activation_not_found}, then
     *     {@code mission_type_not_found} when no pack of the closure holds the type, {@code pack_unreadable} or the code
     *     of a closure that cannot compose or merge, or {@code stale_input} when the inputs changed while they were read
     */
    EffectiveMissionType missionType(String activationId, String missionTypeId);
}
