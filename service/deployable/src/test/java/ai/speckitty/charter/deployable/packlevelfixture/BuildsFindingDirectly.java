package ai.speckitty.charter.deployable.packlevelfixture;

import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packvalidation.placement.PlacementCodes;

/** Route C, the positive control: names a pack-level code outside the level rules, which would state one by hand. */
public final class BuildsFindingDirectly {

    private BuildsFindingDirectly() {}

    /** The pack-level code a class outside the level rules must not name. */
    public static ReasonCode code() {
        return PlacementCodes.PACK_LEVEL_MISSING;
    }
}
