package ai.speckitty.charter.missions.application;

/**
 * The port through which the mission-type reads ask an activation for what the activations slice owns of a mission type
 * (#99, FR-007, D12): the winner of the layer merge, the pack that holds it, the merged closure's step contracts bound
 * to it and the source fingerprint of the read; the winner of a step contract (D11); and the place of a step (research
 * R8, A7). Its adapter, {@code missions.infra.activations}, is the one place of the slice that asks the activations
 * slice. What the holding pack, or the pack of a step's place, stores beside the type is the {@link PackMissionTypes}
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

    /**
     * The winner of one step contract under an activation (D11).
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param stepContractId the step contract's id
     * @return the winning contract, its pack, its bound type, the held artifacts of a listed kind and the fingerprint
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code activation_not_found}, then
     *     {@code mission_step_contract_not_found} when no pack of the closure holds the contract, {@code pack_unreadable}
     *     or the code of a closure that cannot compose or merge, or {@code stale_input}
     */
    EffectiveStepContract stepContract(String activationId, String stepContractId);

    /**
     * The place of one step under an activation (research R8, A7, DIV-243): the pack that holds the merged winner of
     * {@code action:<missionType>/<step>}, judged as the action context judges the step, so the step read refuses an
     * unknown step as the context and the prompt read do.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param missionTypeId the mission type's id
     * @param stepId the step's id
     * @return the place's pack and the source fingerprint
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code activation_not_found}, then
     *     {@code mission_type_not_found} or {@code action_not_found} when the merged closure holds no such mission type
     *     or step, {@code pack_unreadable} or the code of a closure that cannot compose or merge
     */
    EffectiveStepPlace stepPlace(String activationId, String missionTypeId, String stepId);
}
