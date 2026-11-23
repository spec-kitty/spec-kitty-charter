package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Refusal;
import ai.speckitty.charter.packs.domain.PackOrigins;

/**
 * The resolver's refusals as closure faults (#614): the one place the offering's compose phases and the edge index turn
 * a {@link Refusal} into a {@link ClosureFault}, so the fault keeps a single factory and no bridge from the resolver's
 * type. A refusal's fault is about the pack that broke the rule first, the first pack it names (R-o).
 */
final class ResolverFaults {

    private ResolverFaults() {}

    /**
     * A resolver's refusal as a fault: its code, detail and packs, about the first pack it names.
     *
     * @param refusal the refusal
     * @return the fault
     */
    static ClosureFault of(Refusal refusal) {
        return ClosureFault.of(
                PackOrigins.firstOf(refusal.packs()), refusal.failure().code(), refusal.detail(), refusal.packs());
    }
}
