package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;

/**
 * The port through which the mission-type reads ask a pack for what it holds of one mission type (#99). Its adapter,
 * the one bean of the port, asks the packs slice in one verified read; the answer is missions-owned and carries the
 * verified fingerprint as a string.
 */
public interface PackMissionTypes {

    /**
     * What a pack holds of one mission type.
     *
     * @param pack the pack's identity, as the request names it
     * @param missionTypeId the mission type's id
     * @return the type, its skeletons, the step contracts bound to it and its step files, at one fingerprint
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code pack_not_found} or {@code pack_unreadable}
     *     as the pack is judged first, then {@code mission_type_not_found}, or {@code stale_input} when the inputs
     *     changed while they were read
     */
    MissionTypeHolding holding(PackIdentifier pack, String missionTypeId);

    /**
     * What a pack holds of one mission type with the pack revision it was read at, in one verified read: only the
     * pack-scoped reads ask for it.
     *
     * @param pack the pack's identity, as the request names it
     * @param missionTypeId the mission type's id
     * @return the holding and the pack revision
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as {@link #holding} refuses
     */
    PackHolding revised(PackIdentifier pack, String missionTypeId);
}
