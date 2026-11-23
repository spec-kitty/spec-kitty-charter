package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Failure;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Refusal;
import ai.speckitty.charter.packs.domain.PackOrigins;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * {@code CLOSURE} (#554, FR-003, IC-07): the composition checks of a set of packs with levels as a kernel ruleset, for
 * pack-doctor (#478) and every caller that wants each fault, not the first. It reports every duplicate {@code pack_id},
 * name conflict, pair of disjoint ranges, dependency and parent fault, sibling contest and dependency cycle that
 * {@link PackCompositionResolver#faults} collects, in the order the resolver judges them, so the first {@link Reason}
 * is the refusal the offering answers. Its codes are {@link PackCodes#CLOSURE}, built from today's
 * {@link ai.speckitty.charter.kernel.domain.RefusalCode} tokens.
 *
 * <p>The input is the {@link PackUnderResolution} of each pack, built once by the storage edge's composition-facts
 * builder from the one read (P7); nothing here reads a pack. The reference checks and the case-only matches are the
 * closure walk's (#556), and the delegation and step-enhancement checks stay in the offering's sequence: none of them
 * is {@code CLOSURE}.
 *
 * <p>A {@link Reason} has no slot for the packs a fault names, so {@link #faults} and {@link #presetFaults} answer
 * {@link ClosureFault}s, the one fault type of the closure checks (#614), for the callers that need the list (A-M4). A
 * fault's reason carries its phase's rule name, {@link #RULE} for {@link #faults} and {@link #PRESETS_RULE} for
 * {@link #presetFaults}, and is about the pack that broke the rule first, the first pack it names
 * ({@link PackOrigins#firstOf}, operator ruling R-o).
 */
public final class CompositionRules {

    /** The rule name every {@code CLOSURE} reason carries. */
    public static final String RULE = "closure";

    /** The rule name of the preset phase's reasons ({@link #presetFaults}, as {@code PackRulesets} states them). */
    public static final String PRESETS_RULE = "closure.presets";

    private CompositionRules() {}

    /**
     * {@code CLOSURE} over a set of packs: every fault {@link #faults} collects, as failing reasons in its order. A
     * valid set reports nothing.
     *
     * @return the ruleset
     */
    public static ModularRuleset<List<PackUnderResolution>> closure() {
        return ModularRuleset.<List<PackUnderResolution>>emptyRules().must(packs -> reasons(faults(packs)));
    }

    /**
     * Every composition fault of a set of packs, in the resolver's order ({@link PackCompositionResolver#faults}), each
     * under the rule {@value #RULE}.
     *
     * @param packs the packs with their descriptors
     * @return the faults; empty when the set resolves
     * @throws IllegalStateException when a descriptor is not well formed, as {@link PackCompositionResolver#resolve}
     *     refuses it
     */
    public static List<ClosureFault> faults(List<PackUnderResolution> packs) {
        return PackCompositionResolver.faults(packs).stream()
                .map(refusal -> faultOf(refusal, RULE))
                .toList();
    }

    /**
     * Every preset contest of a set of packs ({@link PackCompositionResolver#presetContests}), judged in the set's merge
     * order ({@link PackCompositionResolver#mergeOrder}): the order the offering judges a resolved set in, and the
     * stable order for the packs a set that does not resolve leaves unplaced. So the answer does not depend on the
     * order the packs are given in. Each fault is under the rule {@value #PRESETS_RULE}.
     *
     * @param packs the packs, in any order
     * @param presetIdsOf the ids of the presets a pack holds
     * @return the faults; empty when no two unrelated packs of one level hold one preset id
     * @throws IllegalStateException when a descriptor is not well formed, as {@link #faults} refuses it
     */
    public static List<ClosureFault> presetFaults(
            List<PackUnderResolution> packs, Function<PackUnderResolution, Collection<String>> presetIdsOf) {
        Objects.requireNonNull(presetIdsOf, "presetIdsOf");
        return PackCompositionResolver.presetContests(PackCompositionResolver.mergeOrder(packs), presetIdsOf).stream()
                .map(refusal -> faultOf(refusal, PRESETS_RULE))
                .toList();
    }

    /**
     * The {@code CLOSURE} code of a failure: the {@link PackCodes#CLOSURE} constant whose token is the failure's
     * refusal code.
     *
     * @param failure a failure of a {@code CLOSURE} check
     * @return its code
     * @throws IllegalArgumentException for a reference failure, which is the closure walk's (#556), not {@code CLOSURE}
     */
    public static ReasonCode codeOf(Failure failure) {
        String token = failure.code().token();
        return PackCodes.CLOSURE.stream()
                .filter(code -> code.value().equals(token))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(failure + " is no CLOSURE failure"));
    }

    /**
     * The fault of a {@code CLOSURE} refusal from a phase: its {@link #codeOf} code, its detail and its packs, under the
     * phase's rule, about the first pack it names.
     *
     * @throws IllegalArgumentException for a reference failure, which is no {@code CLOSURE} fault
     */
    private static ClosureFault faultOf(Refusal refusal, String rule) {
        ReasonCode code = codeOf(refusal.failure());
        return ClosureFault.of(
                PackOrigins.firstOf(refusal.packs()),
                rule,
                PackCodes.refusalOf(code),
                refusal.detail(),
                refusal.packs());
    }

    private static List<Reason> reasons(List<ClosureFault> faults) {
        return faults.stream().map(ClosureFault::reason).toList();
    }
}
