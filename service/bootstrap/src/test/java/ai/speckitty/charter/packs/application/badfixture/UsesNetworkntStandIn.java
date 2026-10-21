package ai.speckitty.charter.packs.application.badfixture;

import com.networknt.fixture.Stand;

/** A class outside the storage adapter that depends on a {@code com.networknt} class: the known-bad of that rule. */
public final class UsesNetworkntStandIn {

    private UsesNetworkntStandIn() {}

    /**
     * Touches the stand-in.
     *
     * @return the stand-in
     */
    public static Stand stand() {
        return new Stand();
    }
}
