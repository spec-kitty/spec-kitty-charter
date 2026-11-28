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
 * {@code RELATIONS}, the second domain phase {@code pack-doctor validate --full} adds (#632 item 3, FR-012; operator
 * ruling D3). Per root whose closure passed {@code REFERENCES}, in configured-id order, over the closure the root
 * composes, it runs {@link PackRulesets#relationFaults}, registration's relations phase, with registration's codes. A
 * fault several roots find is reported once, where it is first found (ruling R-ae).
 *
 * <p>Gate: a root whose closure has a {@code REFERENCES} fault is not judged, as registration's phases 2 to 4 gate its
 * relations phase, so one cause never gets two problems.
 *
 * <p>Not here: the edge-index rules and the own-closure rules, which need the layer merge and a profile reader
 * (#636 slice 7). {@link PackSetVerdict.Check#RELATIONS} and the guide "Check packs" hold the rest.
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
