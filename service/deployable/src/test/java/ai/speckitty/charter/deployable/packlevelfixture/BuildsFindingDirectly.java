package ai.speckitty.charter.deployable.packlevelfixture;

import ai.speckitty.charter.packs.domain.PackFindingCode;

/** Route C, the positive control: builds a pack-level finding by hand, which would bypass the policy. */
public final class BuildsFindingDirectly {

    private BuildsFindingDirectly() {}

    /** The finding code a class outside the policy must not name. */
    public static PackFindingCode code() {
        return PackFindingCode.PACK_LEVEL_MISSING;
    }
}
