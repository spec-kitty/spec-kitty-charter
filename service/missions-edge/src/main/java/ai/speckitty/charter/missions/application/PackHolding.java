package ai.speckitty.charter.missions.application;

import java.util.Objects;

/**
 * What a pack holds of one mission type with the pack revision it was read at, both from one verified read: the
 * pack-scoped reads answer the revision as their {@code ETag}. A read under an activation never asks for it.
 *
 * @param holding what the pack holds of the type, at the read's fingerprint
 * @param revision the pack revision, {@code sha256:} and 64 lower-case hex digits
 */
public record PackHolding(MissionTypeHolding holding, String revision) {

    /**
     * Checks the fields.
     *
     * @param holding the holding
     * @param revision the pack revision
     */
    public PackHolding {
        Objects.requireNonNull(holding, "holding");
        Objects.requireNonNull(revision, "revision");
    }
}
