package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Refusal;

/**
 * The resolver's refusals as closure faults (#614): what the offering's compose phases and the edge index turn a
 * {@link Refusal} into a {@link ClosureFault} through. The factory is the domain's, {@link Refusal#toFault()}, so the
 * reference check's one body ({@code PackCompositionResolver.referenceFaultsOf}) gives the same fault. A refusal's fault
 * is about the pack that broke the rule first, the first pack it names (R-o).
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
        return refusal.toFault();
    }
}
