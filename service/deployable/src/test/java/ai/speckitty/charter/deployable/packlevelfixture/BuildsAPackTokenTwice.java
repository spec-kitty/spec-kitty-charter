package ai.speckitty.charter.deployable.packlevelfixture;

import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;

/** PACK_CODES_HAVE_ONE_OWNER, the positive control: builds a pack token a second time, outside its owner. */
public final class BuildsAPackTokenTwice {

    private BuildsAPackTokenTwice() {}

    /** A second spelling of the storage edge's {@code file_not_held}. */
    public static ReasonCode code() {
        return new ReasonCode("file_not_held", Severity.WARNING);
    }
}
