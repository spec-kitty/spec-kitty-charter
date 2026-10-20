package ai.speckitty.charter.service.networkntfixture;

import com.networknt.fixture.Stand;

/** The positive control of the networknt rule: depends on a {@code com.networknt} class outside the YAML adapter. */
public final class UsesNetworkntStandIn {

    private UsesNetworkntStandIn() {}

    /** Touches the stand-in. */
    public static Stand stand() {
        return new Stand();
    }
}
