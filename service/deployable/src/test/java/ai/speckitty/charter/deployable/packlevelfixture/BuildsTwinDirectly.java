package ai.speckitty.charter.deployable.packlevelfixture;

import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packvalidation.placement.PlacementCodes;

/**
 * Route C, the positive control for the pack-set check's twin (#478): names the violation twin of
 * {@code pack_level_missing} outside the level rules, which would state that finding by hand.
 */
public final class BuildsTwinDirectly {

    private BuildsTwinDirectly() {}

    /** The pack-level code a class outside the level rules must not name. */
    public static ReasonCode code() {
        return PlacementCodes.PACK_LEVEL_MISSING_VIOLATION;
    }
}
