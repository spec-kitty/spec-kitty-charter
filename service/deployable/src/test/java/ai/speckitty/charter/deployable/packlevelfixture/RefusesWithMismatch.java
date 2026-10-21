package ai.speckitty.charter.deployable.packlevelfixture;

import ai.speckitty.charter.kernel.domain.RefusalCode;

/** Route B, the positive control: reads the refusal code that only {@code PackPolicy} may name. */
public final class RefusesWithMismatch {

    private RefusesWithMismatch() {}

    /** The code a class outside the policy must not name. */
    public static RefusalCode code() {
        return RefusalCode.PACK_LEVEL_MISMATCH;
    }
}
