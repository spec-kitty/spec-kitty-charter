package ai.speckitty.charter.activations.infra.api.badfixture;

import ai.speckitty.charter.activations.application.AvailablePacks;
import ai.speckitty.charter.kernel.transport.ReservedOperation;

/**
 * A controller whose reserved operation reaches a use case: the known-bad of the rule that a method marked
 * {@link ReservedOperation} reads and stores nothing. The controller itself is unmarked.
 */
public final class ReservedMethodReachesApplication {

    /**
     * A reserved operation that asks the application port.
     *
     * @param packs the port a reserved operation must not reach
     * @return what the port says of itself
     */
    @ReservedOperation
    public String reads(AvailablePacks packs) {
        return packs.toString();
    }
}
