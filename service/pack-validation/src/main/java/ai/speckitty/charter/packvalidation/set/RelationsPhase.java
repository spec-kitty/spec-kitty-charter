package ai.speckitty.charter.packvalidation.set;

import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.rules.PackRulesets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * {@code RELATIONS}, the second domain phase {@code pack-doctor validate --full} adds (#632 item 3, FR-012, SC-004;
 * operator ruling D3 of mission adr-0082-graph-walk-slices, DM-01M8A86SHD5PGJZ1ZBEC7EVHV3, 2026-11-25).
 *
 * <p>Per root of the set whose closure passed {@code REFERENCES}, in {@code REFERENCES}' configured-id order of the
 * roots, over the closure the root composes, it runs {@link PackRulesets#relationFaults}: the factory's relation rules,
 * the one body of registration's relations phase ({@code PackRulesets.composedClosure}, which
 * {@code ClosureImpactCheck} runs over each closure a write joins). In the factory's order: a self-override, a
 * conflicting relation, an override of a target that is not open and a locked step enhanced, an override cycle, an
 * enhances cycle and an ungrounded enhancer, then a {@code specializes_from} cycle among agent profiles. So a cycle a
 * dependent closes across its dependency, which the dependency's own closure does not hold, is found from the
 * dependent's root, as registering the dependent refuses it. A fault several roots find is reported once, where it is
 * first found, as {@link ReferencesPhase} reports a reference fault (ruling R-ae). Each fault is the code registration
 * answers; none carries a violation or a pointer.
 *
 * <p>Where it sits: after {@code REFERENCES}, and gated by it root by root, as registration's phases 2 to 4 gate its
 * relations phase: registration judges a closure's relations only once its delegations, references and enhancement
 * targets pass ({@code OfferingReadService.judged}). So a root whose closure has a {@code REFERENCES} fault is not
 * judged here: a relation rule would answer the same cause again (an enhancement whose target pack is missing is also
 * an ungrounded enhancer), and a pack never gets two problems for one cause. The stop rule of
 * {@code PackRulesets.references} spans the two phases as it spans the closure (ruling R-ab). Registration's phases 5
 * and 6 (the preset contests, the case-only answer) also come before its relations, and it places a preset-item fault
 * only for a closure that passed them; here those are {@code CLOSURE}'s faults, which do not stop this phase and stand
 * beside its faults, as beside {@code REFERENCES}' (ruling R-aa). Within one root's closure the faults follow
 * {@code REFERENCES}', so that closure's first finding is registration's. Across roots the order is phase-major (every
 * root's {@code REFERENCES} faults, then every root's {@code RELATIONS} faults), where registration's is closure-major.
 * Like {@code REFERENCES} it runs only when the set has no composition fault (ruling R-aa).
 *
 * <p>Not here: the edge-index rules, which judge the closure's layer merge, and the own-closure rules, which read the
 * agent profiles the merge keeps; pack-doctor composes no merge and has no profile reader (#636 slice 7).
 */
final class RelationsPhase {

    private RelationsPhase() {}

    /**
     * The faults of the closures: closure by closure, in the order given, every relation fault, rule by rule; a fault
     * several closures hold, once.
     *
     * @param closures the composed closure of each root whose {@code REFERENCES} passed, in configured-id order of the
     *     roots, each in merge order ({@link ReferencesPhase.Judged#passed})
     * @param factsOf each pack's composition facts
     * @return the faults, as registration answers each; empty when every closure's relations are sound
     */
    static List<ClosureFault> faults(
            List<List<PackUnderResolution>> closures, Function<PackUnderResolution, PackCompositionFacts> factsOf) {
        Objects.requireNonNull(closures, "closures");
        Objects.requireNonNull(factsOf, "factsOf");
        Set<ClosureFault> faults = new LinkedHashSet<>();
        for (List<PackUnderResolution> closure : closures) {
            faults.addAll(PackRulesets.relationFaults(closure, factsOf));
        }
        return List.copyOf(faults);
    }
}
