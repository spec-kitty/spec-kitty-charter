package ai.speckitty.charter.kernel.validation.badfixture;

import com.networknt.fixture.Stand;

/** The known-bad control of the validation-purity rule: a validation class that depends on a third-party type. */
public final class UsesThirdPartyType {

    private UsesThirdPartyType() {}

    /** Touches the stand-in. */
    public static Stand stand() {
        return new Stand();
    }
}
