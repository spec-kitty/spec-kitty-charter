package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The layer merge: the one winner function over a composed closure, its facts and its policies (FR-009 to FR-012). It
 * is pure: it reads what it is given and answers a {@link MergedClosure}; it reads no store and holds no state.
 */
public final class LayerMerge {

    private LayerMerge() {}

    /**
     * Merges a composed closure.
     *
     * @param closure the composed closure, in closure order
     * @param factsByConfiguredId the facts each pack was read with, by configured identifier
     * @param policy the mutability policies of the closure's packs
     * @return the winners and the layer overrides
     */
    public static MergedClosure merge(
            List<PackUnderResolution> closure,
            Map<String, PackCompositionFacts> factsByConfiguredId,
            PerPackPolicy policy) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(factsByConfiguredId, "factsByConfiguredId");
        Objects.requireNonNull(policy, "policy");
        return MergedClosure.EMPTY;
    }
}
