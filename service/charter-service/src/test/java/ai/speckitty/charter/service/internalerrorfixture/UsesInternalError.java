package ai.speckitty.charter.service.internalerrorfixture;

import ai.speckitty.charter.domain.RefusalCode;

/** A class outside the inbound adapter that refuses with {@code internal_error}: the positive control of the rule. */
public final class UsesInternalError {

    private UsesInternalError() {}

    /** The code a class outside the inbound adapter must not name. */
    public static RefusalCode code() {
        return RefusalCode.INTERNAL_ERROR;
    }
}
