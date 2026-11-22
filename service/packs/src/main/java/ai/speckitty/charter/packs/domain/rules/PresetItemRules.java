package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Refusal;
import ai.speckitty.charter.packs.domain.PackOrigins;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The preset-item rule (#629, #632 item 1; operator rulings R-a, R-k, R-l, R-m; plan D3): a preset item the declaring
 * pack's own closure lacks is refused, by registration, by every write and by pack-doctor alike.
 *
 * <p>The own closure is the declaring pack and every pack it reaches over {@code depends_on} and {@code parent_pack}
 * ({@link PackCompositionResolver#compositionClosure} rooted at the pack, never its dependents, R-k): no walk of its
 * own. Over it, each item of each preset the pack holds is judged once:
 *
 * <ol>
 *   <li>an item that names an artifact of the closure only by case gets the existing case-only answer
 *       ({@link PackCompositionResolver#caseOnlyMatches}, {@code pack_reference_artifact_missing}), and nothing else;
 *   <li>else a {@code must_have} item that no closure pack holds as an artifact file
 *       ({@link PackCompositionFacts#held()}) answers {@code preset_item_not_held};
 *   <li>else an item that no closure pack declares as its kind, by a graph node or a file
 *       ({@link PackCompositionFacts#declared()}), answers {@code preset_item_undeclared}.
 * </ol>
 *
 * <p>An item of another kind never satisfies the entry: the item is its {@code <kind>:<id>} URN, matched exactly. The
 * faults come in pack-id order, then preset path order, then item order (the kinds in the kind registry's order, the
 * items of a kind in authored order). Each is about the pack and the item's URN ({@link PackOrigins#of(
 * ai.speckitty.charter.kernel.domain.PackIdentifier, Urn)}), under the rule {@value #RULE}, names the declaring pack
 * first, and carries one violation at the item: {@code presets/<id>.yaml#/activated_<kinds>/<index>}.
 *
 * <p>A pack whose own closure reaches a pack the set does not hold is not judged: its items cannot be told from a
 * missing dependency, which {@code CLOSURE} already answers. A composing read never runs this rule: it reports a
 * {@code must_have} its closure does not hold as {@code requiredNotHeld} (A10, C31).
 */
public final class PresetItemRules {

    /** The rule name of the preset-item phase's reasons. */
    public static final String RULE = "closure.preset-items";

    private static final String PRESETS = "presets/";

    private PresetItemRules() {}

    /**
     * The phase over a set of packs: every fault of every preset item of every pack, in the order above.
     *
     * @param factsOf each pack's composition facts: its presets, and what it holds and declares
     * @return the phase; it answers an empty list when every item is held or declared as it must be
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
        List<PackUnderResolution> ordered = packs.stream()
                .sorted(Comparator.comparing(pack -> pack.configured().id().value()))
                .toList();
        List<ClosureFault> faults = new ArrayList<>();
        for (PackUnderResolution pack : ordered) {
            List<Preset> presets = factsOf.apply(pack).presets().stream()
                    .sorted(Comparator.comparing(preset -> pathOf(preset.id())))
                    .toList();
            if (presets.isEmpty()) {
                continue;
            }
            ownClosure(pack, byPackId)
                    .ifPresent(closure ->
                            presets.forEach(preset -> judge(pack, preset, new Closure(closure, factsOf), faults)));
        }
        return List.copyOf(faults);
    }

    /** The packs of a pack's own closure, in {@code compositionClosure} order; empty when one is not in the set. */
    private static Optional<List<PackUnderResolution>> ownClosure(
            PackUnderResolution pack, Map<String, PackUnderResolution> byPackId) {
        List<PackUnderResolution> closure = new ArrayList<>();
        for (String packId : PackCompositionResolver.compositionClosure(
                List.of(pack.descriptor().packId()), byPackId)) {
            PackUnderResolution member = byPackId.get(packId);
            if (member == null) {
                return Optional.empty();
            }
            closure.add(member);
        }
        return Optional.of(List.copyOf(closure));
    }

    /** What an own closure holds and declares, over its packs' facts. */
    private record Closure(List<PackUnderResolution> packs, Set<String> held, Set<String> declared) {

        Closure(List<PackUnderResolution> packs, Function<PackUnderResolution, PackCompositionFacts> factsOf) {
            this(
                    packs,
                    union(packs, factsOf, PackCompositionFacts::held),
                    union(packs, factsOf, PackCompositionFacts::declared));
        }

        private static Set<String> union(
                List<PackUnderResolution> packs,
                Function<PackUnderResolution, PackCompositionFacts> factsOf,
                Function<PackCompositionFacts, List<String>> field) {
            Set<String> union = new HashSet<>();
            packs.forEach(pack -> union.addAll(field.apply(factsOf.apply(pack))));
            return union;
        }

        String names() {
            return packs.stream()
                    .map(pack -> "`" + pack.configured().id().value() + "`")
                    .collect(Collectors.joining(", "));
        }
    }

    private static void judge(PackUnderResolution pack, Preset preset, Closure closure, List<ClosureFault> faults) {
        preset.artifacts().forEach((kind, items) -> {
            for (int index = 0; index < items.size(); index++) {
                PresetItem item = items.get(index);
                Urn urn = new Urn(NodeKind.of(kind), item.id());
                String pointer = pathOf(preset.id()) + "#/activated_" + kind.plural() + "/" + index;
                itemFault(pack, preset, kind, item, urn, pointer, closure).ifPresent(faults::add);
            }
        });
    }

    /** The one fault of an item, the case-only answer first; empty when the closure has it as it must. */
    private static Optional<ClosureFault> itemFault(
            PackUnderResolution pack,
            Preset preset,
            ArtifactKind kind,
            PresetItem item,
            Urn urn,
            String pointer,
            Closure closure) {
        String mention = urn.toString();
        List<Refusal> caseOnly = PackCompositionResolver.caseOnlyMatches(
                closure.packs(),
                member -> member.configured().equals(pack.configured()) ? List.of(mention) : List.of());
        if (!caseOnly.isEmpty()) {
            Refusal refusal = caseOnly.getFirst();
            return Optional.of(fault(pack, urn, refusal.failure().code(), refusal.detail(), refusal.packs(), pointer));
        }
        String subject = "the preset `" + preset.id() + "` of pack `"
                + pack.configured().id().value() + "`";
        if (item.mustHave() && !closure.held().contains(mention)) {
            return Optional.of(fault(
                    pack,
                    urn,
                    RefusalCode.PRESET_ITEM_NOT_HELD,
                    subject + " requires " + mention + " (must_have), but no pack of its own closure ("
                            + closure.names() + ") holds it as an artifact file",
                    List.of(refusalPack(pack)),
                    pointer));
        }
        if (!closure.declared().contains(mention)) {
            return Optional.of(fault(
                    pack,
                    urn,
                    RefusalCode.PRESET_ITEM_UNDECLARED,
                    subject + " activates " + mention + " (" + (item.mustHave() ? "must_have" : "not must_have")
                            + "), but no pack of its own closure (" + closure.names() + ") declares it as a "
                            + kind.token(),
                    List.of(refusalPack(pack)),
                    pointer));
        }
        return Optional.empty();
    }

    private static ClosureFault fault(
            PackUnderResolution pack,
            Urn urn,
            RefusalCode code,
            String detail,
            List<RefusalPack> packs,
            String pointer) {
        return ClosureFault.of(
                PackOrigins.of(pack.configured().id(), urn),
                RULE,
                code,
                detail,
                packs,
                List.of(new RefusalViolation(pointer, code.token(), detail)));
    }

    private static RefusalPack refusalPack(PackUnderResolution pack) {
        return new RefusalPack(
                pack.descriptorId(), pack.configured().id(), pack.descriptor().name());
    }

    private static String pathOf(String presetId) {
        return PRESETS + presetId + ".yaml";
    }
}
