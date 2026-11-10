package ai.speckitty.charter.profiles.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;

/**
 * The port of the pack-scoped profile read (ADR-0037 as amended 2026-11-09): where a pack's stored profile and its
 * governance come from. The adapter is {@code profiles.infra.packs}, the one place of the slice that asks the packs
 * slice; the application ring names no packs type.
 */
public interface ProfileSource {

    /**
     * One agent profile of a pack with its governance, read once and verified once.
     *
     * @param pack the pack's identity, as the request names it
     * @param profileId the profile's id
     * @param depth the number of {@code suggests} hops of the governance walk, 0 or 1
     * @return the profile, its pack revision, the verified fingerprint and the governance
     * @throws CharterRefusal with {@code PACK_NOT_FOUND}, {@code ARTIFACT_NOT_FOUND}, {@code PACK_UNREADABLE}, the
     *     code of a closure that cannot compose or merge, or {@code STALE_INPUT}
     */
    ProfileRead read(PackIdentifier pack, String profileId, int depth);
}
