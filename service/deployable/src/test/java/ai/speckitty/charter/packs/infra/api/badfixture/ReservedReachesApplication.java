package ai.speckitty.charter.packs.infra.api.badfixture;

import ai.speckitty.charter.packs.application.DirectiveReadService;
import ai.speckitty.charter.packs.infra.api.ReservedOperation;
import org.jspecify.annotations.Nullable;

/** A reserved controller that names a use case: the known-bad of the rule that a reserved controller reads nothing. */
@ReservedOperation
public final class ReservedReachesApplication {

    /**
     * A use case a reserved operation must not reach.
     *
     * @return nothing
     */
    public @Nullable DirectiveReadService reads() {
        return null;
    }
}
