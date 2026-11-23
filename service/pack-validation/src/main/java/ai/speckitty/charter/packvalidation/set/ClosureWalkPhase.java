package ai.speckitty.charter.packvalidation.set;

import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.EnhancementCompose;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * {@code REFERENCES}, the domain phase {@code pack-doctor validate --full} adds (#632 item 3, FR-006; ruling R-s,
 * DM-01M801NHF0BFAX05EA3F19KHV7, 2026-11-21: "validate --full = delegations + references + enhancement targets only,
 * each an existing domain function run per own closure in registration's phase order, no new walk").
 *
 * <p>Per pack of the set, in the set's order, over the pack's own closure, it runs registration's phases 2 to 4
 * ({@code OfferingReadService.judged}), each the domain's one body of its check:
 *
 * <ol>
 *   <li>delegations: {@link PackCompositionResolver#delegationFaultsOf};
 *   <li>references: {@link PackCompositionResolver#referenceFaultsOf};
 *   <li>enhancement targets: {@link EnhancementCompose#targetFaultsOf}.
 * </ol>
 *
 * <p>Every fault of the first check that finds any is kept; a later check runs only when the earlier ones pass, as a
 * later phase of registration does, since it would answer the same cause again (a delegation candidate is also the
 * reference of its {@code scope} edge). Each fault is the code registration answers ({@link
 * ClosureFault#asRegistrationRefuses()}).
 *
 * <p>The own closure is {@link PackCompositionResolver#composeRooted} rooted at the pack: the domain's composition
 * closure, the pack and what it reaches over {@code depends_on} and {@code parent_pack}, as registration composes the
 * closure the pack roots. No walk is written here (NFR-003). It runs only after {@code CLOSURE} passed, so the set
 * resolves and no own closure has a fault of its own.
 *
 * <p>Not here (R-s): the {@code specializes_from} cycle, the relation rules, the edge index's refusals and the
 * own-closure walk land in the domain factory with #636 slices 2-4 and 7.
 */
final class ClosureWalkPhase {

    private ClosureWalkPhase() {}

    /** One pack's check within a closure: the domain's one body of it. */
    @FunctionalInterface
    private interface Check {
        List<ClosureFault> faults(
                List<PackUnderResolution> closure, PackUnderResolution pack, PackCompositionFacts facts);
    }

    /** Registration's phase order: delegations, references, enhancement targets. */
    private static final List<Check> PHASES = List.of(
            PackCompositionResolver::delegationFaultsOf,
            PackCompositionResolver::referenceFaultsOf,
            EnhancementCompose::targetFaultsOf);

    /**
     * The faults of the set: per pack, in the set's order, every fault of its first failing check within its own
     * closure.
     *
     * @param set the packs, which {@code CLOSURE} passed, in the order the set was given
     * @param factsOf each pack's composition facts
     * @return the faults, as registration answers each; empty when every pack passes
     */
    static List<ClosureFault> faults(
            List<PackUnderResolution> set, Function<PackUnderResolution, PackCompositionFacts> factsOf) {
        Objects.requireNonNull(set, "set");
        Objects.requireNonNull(factsOf, "factsOf");
        List<ClosureFault> faults = new ArrayList<>();
        for (PackUnderResolution pack : set) {
            List<PackUnderResolution> closure = PackCompositionResolver.composeRooted(
                            set, List.of(pack.configured().id()))
                    .order();
            PackCompositionFacts facts = factsOf.apply(pack);
            for (Check phase : PHASES) {
                List<ClosureFault> found = phase.faults(closure, pack, facts);
                if (!found.isEmpty()) {
                    found.stream().map(ClosureFault::asRegistrationRefuses).forEach(faults::add);
                    break;
                }
            }
        }
        return List.copyOf(faults);
    }
}
