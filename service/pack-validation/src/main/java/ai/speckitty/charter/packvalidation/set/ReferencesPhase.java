package ai.speckitty.charter.packvalidation.set;

import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.rules.PackRulesets;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * {@code REFERENCES}, the domain phase {@code pack-doctor validate --full} adds (#632 item 3, FR-006; ruling R-s,
 * DM-01M801NHF0BFAX05EA3F19KHV7, 2026-11-21: "validate --full = delegations + references + enhancement targets only,
 * each an existing domain function run per own closure in registration's phase order, no new walk"; scope widened by
 * ruling R-ae, DM-01M86V9S1WNSB5A58PZG8PMJY5, 2026-11-24).
 *
 * <p>Per root, each pack of the set in configured-id order (the id each pack is configured under: in pack-doctor its
 * {@code --pack} id, the directory name or {@code =<id>}; not the descriptor's {@code pack_id}), over the closure the
 * root composes, it runs {@link PackRulesets#references} with every pack of that closure a member: the one body of
 * registration's phases 2 to 4 ({@code OfferingReadService.judged}, which judges the closure it composes the same way),
 * its ordered checks and its stop rule across the closure (ruling R-ab). So a dependency's bare reference that only a
 * dependent holds is {@code pack_reference_unqualified} from the dependent's root, as registering the dependent answers
 * it, and a dependency's delegation fault hides a dependent's reference fault, as it does there. A fault several roots
 * find is reported once, where it is first found, as {@code CaseOnlyRules} reports a case-only fault (ruling R-z). A
 * qualified reference into a pack outside the referrer's {@code depends_on} but inside a root's closure is answered
 * once per root whose words differ, as registering each root answers it. Each fault is the code registration answers
 * ({@link ClosureFault#asRegistrationRefuses()}).
 *
 * <p>The closure is {@link PackCompositionResolver#composeRooted} rooted at the pack: the domain's composition closure,
 * the pack and what it reaches over {@code depends_on} and {@code parent_pack}, in merge order. No walk is written here
 * (NFR-003). It runs only when the set has no composition fault (ruling R-aa), so every rooted closure composes. Packs
 * that only a stored activation lists together are never one closure here: pack-doctor is blind to activations, so
 * that shape stays registration's alone, as decision 20 of ADR-0083 says of the case-only answer.
 *
 * <p>Not here (R-s): the {@code specializes_from} cycle, the relation rules, the edge index's refusals and the
 * own-closure walk land in the domain factory with #636 slices 2-4 and 7.
 */
final class ReferencesPhase {

    private ReferencesPhase() {}

    /**
     * The faults of the set: per root, in configured-id order, every fault of the first failing check within the root's
     * closure, every pack of it judged in merge order; a fault several roots find, once.
     *
     * @param set the packs, which have no composition fault
     * @param factsOf each pack's composition facts
     * @return the faults, as registration answers each; empty when every closure passes
     * @throws IllegalStateException when a root's closure has a composition fault: {@code PackSetCheck} runs this only
     *     when none of {@code CLOSURE}'s faults is one (its {@code isCompositionFault} gate), so the throw means the
     *     gate and this phase disagree on the set
     */
    static List<ClosureFault> faults(
            List<PackUnderResolution> set, Function<PackUnderResolution, PackCompositionFacts> factsOf) {
        Objects.requireNonNull(set, "set");
        Objects.requireNonNull(factsOf, "factsOf");
        Set<ClosureFault> faults = new LinkedHashSet<>();
        List<PackUnderResolution> roots = set.stream()
                .sorted(Comparator.comparing(pack -> pack.configured().id().value()))
                .toList();
        for (PackUnderResolution root : roots) {
            PackCompositionResolver.Rooted rooted = PackCompositionResolver.composeRooted(
                    set, List.of(root.configured().id()));
            if (!rooted.faults().isEmpty()) {
                throw new IllegalStateException("REFERENCES runs only when the set composes, yet the closure of "
                        + root.configured().id().value() + " has composition faults: " + rooted.faults());
            }
            PackRulesets.references(rooted.order(), rooted.order(), factsOf).stream()
                    .map(ClosureFault::asRegistrationRefuses)
                    .forEach(faults::add);
        }
        return List.copyOf(faults);
    }
}
