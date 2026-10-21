package ai.speckitty.charter.packs.application.badfixture;

import ai.speckitty.charter.kernel.domain.RefusalCode;

/** A class outside the inbound adapter that refuses with {@code internal_error}: the known-bad of that rule. */
public final class UsesInternalError {

    private UsesInternalError() {}

    /**
     * The code a class outside the inbound adapter must not name.
     *
     * @return the forbidden code
     */
    public static RefusalCode code() {
        return RefusalCode.INTERNAL_ERROR;
    }
}
