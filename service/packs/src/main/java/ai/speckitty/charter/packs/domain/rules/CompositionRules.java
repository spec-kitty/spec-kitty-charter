package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Failure;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Refusal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * {@code CLOSURE} (#554, FR-003, IC-07): the composition checks of a set of packs with levels as a kernel ruleset, for
 * pack-check (#478) and every caller that wants each fault, not the first. It reports every duplicate {@code pack_id},
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
 * <p>A {@link Reason} has no slot for the packs a fault names, so {@link #faults} and {@link #presetFaults} keep a
 * typed {@link CompositionFault} beside it for the callers that need the list (A-M4).
 */
public final class CompositionRules {

    /** The rule name every {@code CLOSURE} reason carries. */
    public static final String RULE = "closure";

    /** The rule name of the preset phase's reasons. */
    public static final String PRESETS_RULE = "closure.presets";

    private CompositionRules() {}

    /**
     * One composition fault with the packs it names: the {@link Reason}'s code and detail, and the {@link RefusalPack}
     * list the offering answers with, the pack that broke the graph first.
     *
     * @param code the {@code CLOSURE} code, one of {@link PackCodes#CLOSURE}
     * @param packs the packs the fault names, the violating pack first; never empty
     * @param detail what the caller needs to read
     */
    public record CompositionFault(ReasonCode code, List<RefusalPack> packs, String detail) {

        /**
         * Checks the fields and copies the list.
         *
         * @param code the code
         * @param packs the packs it names
         * @param detail the detail
         */
        public CompositionFault {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(packs, "packs");
            Objects.requireNonNull(detail, "detail");
            packs = List.copyOf(packs);
        }

        /**
         * The fault as the resolver reports it.
         *
         * @param refusal a refusal of a {@code CLOSURE} check
         * @return the fault
         */
        static CompositionFault of(Refusal refusal) {
            return new CompositionFault(codeOf(refusal.failure()), refusal.packs(), refusal.detail());
        }

        /**
         * The fault as a kernel finding, unplaced: a composition fault belongs to a set of packs, not to a file.
         *
         * @param rule the rule name the reason carries
         * @return the failing reason
         */
        Reason reason(String rule) {
            return new Reason(rule, Evaluation.FAIL, code, Location.NOWHERE, detail);
        }
    }

    /**
     * {@code CLOSURE} over a set of packs: every fault {@link #faults} collects, as failing reasons in its order. A
     * valid set reports nothing.
     *
     * @return the ruleset
     */
    public static ModularRuleset<List<PackUnderResolution>> closure() {
        return ModularRuleset.<List<PackUnderResolution>>emptyRules().must(packs -> reasons(faults(packs), RULE));
    }

    /**
     * The preset phase of {@code CLOSURE}: the preset ids are composition facts the {@link PackUnderResolution} does
     * not carry, so the caller names them. It judges the packs in the order given, which is the merge order when the
     * set resolves ({@link PackCompositionResolver#resolve}).
     *
     * @param presetIdsOf the ids of the presets a pack holds
     * @return the rule
     */
    public static Rule<List<PackUnderResolution>> presets(
            Function<PackUnderResolution, Collection<String>> presetIdsOf) {
        Objects.requireNonNull(presetIdsOf, "presetIdsOf");
        return packs -> reasons(presetFaults(packs, presetIdsOf), PRESETS_RULE);
    }

    /**
     * Every composition fault of a set of packs, in the resolver's order ({@link PackCompositionResolver#faults}).
     *
     * @param packs the packs with their descriptors
     * @return the faults; empty when the set resolves
     * @throws IllegalStateException when a descriptor is not well formed, as {@link PackCompositionResolver#resolve}
     *     refuses it
     */
    public static List<CompositionFault> faults(List<PackUnderResolution> packs) {
        return PackCompositionResolver.faults(packs).stream()
                .map(CompositionFault::of)
                .toList();
    }

    /**
     * Every preset contest of a set of packs ({@link PackCompositionResolver#presetContests}).
     *
     * @param ordered the packs, in merge order
     * @param presetIdsOf the ids of the presets a pack holds
     * @return the faults; empty when no two unrelated packs of one level hold one preset id
     */
    public static List<CompositionFault> presetFaults(
            List<PackUnderResolution> ordered, Function<PackUnderResolution, Collection<String>> presetIdsOf) {
        return PackCompositionResolver.presetContests(ordered, presetIdsOf).stream()
                .map(CompositionFault::of)
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

    private static List<Reason> reasons(List<CompositionFault> faults, String rule) {
        return faults.stream().map(fault -> fault.reason(rule)).toList();
    }
}
