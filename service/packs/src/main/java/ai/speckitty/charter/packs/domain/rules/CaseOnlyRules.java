package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Refusal;
import ai.speckitty.charter.packs.domain.PackOrigins;
import ai.speckitty.charter.packs.domain.Preset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * The case-only answer (the operator ruling of 2026-11-06; rulings R-y, R-z; FR-016): a preset item or a policy lock
 * that names an artifact of a composed closure only by letter case, {@code directive:directive_001} where a pack of the
 * closure holds {@code directive:DIRECTIVE_001}, is refused {@code pack_reference_artifact_missing}. Ids are matched
 * exactly, so such a mention would silently govern nothing.
 *
 * <p>One body ({@link #matches}): over one composed closure, every member's mentions ({@link #mentionsOf}) against
 * every member's holdings ({@link PackCompositionResolver#caseOnlyMatches}), the holder the first in merge order.
 * Registration's composing judge runs it as its phase 6 over the closure it composes ({@code OfferingReadService}), and
 * pack-doctor's {@code CLOSURE} runs it as its last phase ({@link #phase}) over the closure rooted at each pack of the
 * set: the same code, packs and detail.
 *
 * <p>A mention of an id no pack of the closure holds, in any case, is no mistake this check can tell from a pack not yet
 * configured: it is left alone. Only the case-only answer is judged over the composed closure; a preset item's own
 * faults ({@code preset_item_not_held}, {@code preset_item_undeclared}) keep the declaring pack's own closure (R-k,
 * {@link PresetItemRules}).
 */
public final class CaseOnlyRules {

    /** The rule name of the case-only phase's reasons in pack-doctor's {@code CLOSURE}. */
    public static final String RULE = "closure.case-only";

    private CaseOnlyRules() {}

    /**
     * What a pack's presets and policy file name, as written: what must match a held id exactly. Each preset item's URN,
     * the presets in the order the pack holds them and the items of a preset in the kind registry's then authored order,
     * then each URN its mutability policy locks, in URN order.
     *
     * @param facts the pack's composition facts
     * @return the mentions, as URN text
     */
    public static List<String> mentionsOf(PackCompositionFacts facts) {
        Objects.requireNonNull(facts, "facts");
        List<String> mentions = new ArrayList<>();
        for (Preset preset : facts.presets()) {
            preset.artifacts()
                    .forEach((kind, items) ->
                            items.forEach(item -> mentions.add(new Urn(NodeKind.of(kind), item.id()).toString())));
        }
        new TreeSet<>(facts.policy().urnEntries().keySet()).forEach(urn -> mentions.add(urn.toString()));
        return List.copyOf(mentions);
    }

    /**
     * The case-only answer over one composed closure: every member's mention that names an artifact a member holds
     * only by case, member by member in merge order, each naming the mentioning pack then the first holder in merge
     * order (#556, FR-006).
     *
     * @param composed the closure, in merge order
     * @param factsOf each member's composition facts
     * @return the refusals; empty when no mention differs from a held id by case alone
     */
    public static List<Refusal> matches(
            List<PackUnderResolution> composed, Function<PackUnderResolution, PackCompositionFacts> factsOf) {
        Objects.requireNonNull(factsOf, "factsOf");
        return PackCompositionResolver.caseOnlyMatches(composed, pack -> mentionsOf(factsOf.apply(pack)));
    }

    /**
     * pack-doctor's case-only phase over a set of packs: {@link #matches} over the closure rooted at each pack of the
     * set, in pack-id order, as registration's phase 6 judges the closure each pack roots. A fault two roots' closures
     * find is reported once, where it is first found. A root whose closure reaches a pack the set does not hold is not
     * judged: that is a composition fault, which {@code CLOSURE} already answers. Each fault is the resolver's refusal
     * ({@link Refusal#toFault()}: its code, detail and packs, about the mentioning pack, no violation) under the rule
     * {@value #RULE}.
     *
     * @param factsOf each pack's composition facts
     * @return the phase; it answers an empty list when no mention names an artifact only by case
     */
    public static Function<List<PackUnderResolution>, List<ClosureFault>> phase(
            Function<PackUnderResolution, PackCompositionFacts> factsOf) {
        Objects.requireNonNull(factsOf, "factsOf");
        return packs -> faults(packs, factsOf);
    }

    private static List<ClosureFault> faults(
            List<PackUnderResolution> packs, Function<PackUnderResolution, PackCompositionFacts> factsOf) {
        Objects.requireNonNull(packs, "packs");
        Map<String, PackUnderResolution> byPackId = new LinkedHashMap<>();
        packs.forEach(pack -> byPackId.putIfAbsent(pack.descriptor().packId(), pack));
        Set<ClosureFault> faults = new LinkedHashSet<>();
        packs.stream()
                .sorted(Comparator.comparing(pack -> pack.configured().id().value()))
                .forEach(root -> PresetItemRules.ownClosure(root, byPackId)
                        .ifPresent(closure -> matches(PackCompositionResolver.mergeOrder(closure), factsOf).stream()
                                .map(refusal -> ClosureFault.of(
                                        PackOrigins.firstOf(refusal.packs()),
                                        RULE,
                                        refusal.failure().code(),
                                        refusal.detail(),
                                        refusal.packs()))
                                .forEach(faults::add)));
        return List.copyOf(faults);
    }
}
