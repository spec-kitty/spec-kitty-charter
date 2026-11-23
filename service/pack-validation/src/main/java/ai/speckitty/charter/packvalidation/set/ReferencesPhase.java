package ai.speckitty.charter.packvalidation.set;

import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.rules.PackRulesets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * {@code REFERENCES}, the domain phase {@code pack-doctor validate --full} adds (#632 item 3, FR-006; ruling R-s,
 * DM-01M801NHF0BFAX05EA3F19KHV7, 2026-11-21: "validate --full = delegations + references + enhancement targets only,
 * each an existing domain function run per own closure in registration's phase order, no new walk").
 *
 * <p>Per pack of the set, in the set's order, over the pack's own closure, it runs {@link PackRulesets#references}: the
 * one body of registration's phases 2 to 4 ({@code OfferingReadService.judged}), its ordered checks and its stop rule
 * (ruling R-ab), with the pack the one member judged. Each fault is the code registration answers ({@link
 * ClosureFault#asRegistrationRefuses()}).
 *
 * <p>The own closure is {@link PackCompositionResolver#composeRooted} rooted at the pack: the domain's composition
 * closure, the pack and what it reaches over {@code depends_on} and {@code parent_pack}, as registration composes the
 * closure the pack roots. No walk is written here (NFR-003). It runs only when the set has no composition fault (ruling
 * R-aa), so every own closure composes.
 *
 * <p>Not here (R-s): the {@code specializes_from} cycle, the relation rules, the edge index's refusals and the
 * own-closure walk land in the domain factory with #636 slices 2-4 and 7.
 */
final class ReferencesPhase {

    private ReferencesPhase() {}

    /**
     * The faults of the set: per pack, in the set's order, every fault of its first failing check within its own
     * closure.
     *
     * @param set the packs, which have no composition fault, in the order the set was given
     * @param factsOf each pack's composition facts
     * @return the faults, as registration answers each; empty when every pack passes
     * @throws IllegalStateException when a pack's own closure has a composition fault, which the gate rules out
     */
    static List<ClosureFault> faults(
            List<PackUnderResolution> set, Function<PackUnderResolution, PackCompositionFacts> factsOf) {
        Objects.requireNonNull(set, "set");
        Objects.requireNonNull(factsOf, "factsOf");
        List<ClosureFault> faults = new ArrayList<>();
        for (PackUnderResolution pack : set) {
            PackCompositionResolver.Rooted rooted = PackCompositionResolver.composeRooted(
                    set, List.of(pack.configured().id()));
            if (!rooted.faults().isEmpty()) {
                throw new IllegalStateException("REFERENCES runs only when the set composes, yet the own closure of "
                        + pack.configured().id().value() + " has composition faults: " + rooted.faults());
            }
            PackRulesets.references(rooted.order(), List.of(pack), factsOf).stream()
                    .map(ClosureFault::asRegistrationRefuses)
                    .forEach(faults::add);
        }
        return List.copyOf(faults);
    }
}
