package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.LayerOverride;
import ai.speckitty.charter.packs.domain.MergedRelations;
import ai.speckitty.charter.packs.domain.MergedRelations.PlacedEdge;
import ai.speckitty.charter.packs.domain.MissionStepLinkRule;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackOrigins;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The edge-index rules of a composed closure (#639, ADR-0082 slice 4, operator rulings D2, Q-B and Q-C): the faults the
 * layer merge places, judged on the merged closure ({@link MergedRelations}) so that a read and a registration of one
 * closure answer each with one code. {@link PackRulesets#edgeIndex} declares them, after the relation rules, so a
 * closure that breaks a relation rule is never judged here; each reports every fault it finds, in the order of the
 * merge.
 */
final class EdgeIndexRules {

    private EdgeIndexRules() {}

    /**
     * Every edge-index fault of a merged closure, rule by rule in the order the merge met them: a forbidden layer
     * override, a locked step enhanced, a self-override, then a stray step link.
     */
    static List<ClosureFault> faults(MergedRelations relations) {
        List<ClosureFault> faults = new ArrayList<>(forbiddenLayerOverride(relations));
        faults.addAll(lockedStepEnhanced(relations));
        faults.addAll(selfOverride(relations));
        faults.addAll(strayStepLink(relations));
        return List.copyOf(faults);
    }

    /**
     * Every layer override the overridden artifact's level forbids (C21, FR-011, ADR-0063 D3): only an {@code open}
     * artifact may be replaced by a later level. The level is judged along the closure with the winning pack left out,
     * so the losers' own files and any pack that raised the level count, and a pack never blocks its own override
     * (research R3).
     */
    static List<ClosureFault> forbiddenLayerOverride(MergedRelations relations) {
        List<PackUlid> closurePackIds = relations.packs().stream()
                .map(PackUnderResolution::descriptorId)
                .toList();
        List<ClosureFault> faults = new ArrayList<>();
        for (LayerOverride override : relations.layerOverrides()) {
            PackUnderResolution winner = relations.packOf(override.winner().packId());
            PackUnderResolution loser = relations.packOf(override.loser().packId());
            List<PackUlid> judging = closurePackIds.stream()
                    .filter(packId -> !packId.equals(winner.descriptorId()))
                    .toList();
            PerPackPolicy.Level level = relations.policy().levelIn(judging, override.urn());
            if (level.level() != MutabilityLevel.OPEN) {
                faults.add(fault(
                        RefusalCode.OVERRIDE_NOT_PERMITTED,
                        "pack `" + winner.descriptor().name() + "` at "
                                + winner.configured().level().token()
                                + " replaces `" + override.urn() + "` of pack `"
                                + loser.descriptor().name() + "` at "
                                + loser.configured().level().token() + ", but the artifact is "
                                + level.level().token()
                                + " along the closure"
                                + level.setBy()
                                        .map(by -> " (set by pack " + by.value() + ")")
                                        .orElse("")
                                + "; only an open artifact may be replaced by a later level",
                        List.of(winner, loser),
                        List.of()));
            }
        }
        return List.copyOf(faults);
    }

    /**
     * Every enhancement of a step whose mission type is locked (C22 and C25 as amended, OD-2 (A)), judged as the
     * relation rule judges it ({@link RelationRules#lockedSteps}) over the policies the merge reads, once the merge
     * ended: the read judged it after the merge.
     */
    static List<ClosureFault> lockedStepEnhanced(MergedRelations relations) {
        if (!relations.complete()) {
            return List.of();
        }
        return RelationRules.lockedSteps(relations.packs(), relations::edgesOf, relations.policy());
    }

    /** Every {@code overrides} edge that, re-pointed to the winner, ends at its own source (C36, A12). */
    static List<ClosureFault> selfOverride(MergedRelations relations) {
        return relations.placed().stream()
                .filter(PlacedEdge::isSelfOverride)
                .map(placed -> {
                    PackUnderResolution holder = relations.packOf(placed.holder());
                    return fault(
                            RefusalCode.SELF_OVERRIDE,
                            "pack `" + holder.descriptor().name() + "` writes the overrides edge `"
                                    + placed.edge().from() + "` -> `"
                                    + placed.edge().toReference()
                                    + "`, which resolves to its own source in the merged closure",
                            List.of(holder),
                            List.of());
                })
                .toList();
    }

    /**
     * Every {@code requires} edge another pack writes at a mission type in force that names an action that is no step
     * of the type (operator rulings DM-01M7B1YQR5ZKQPDW6DX64KPNRN and Q-C): each pack's own edges are judged when it
     * loads, and an edge written at another pack's type is judged here, once the merge has placed it. The steps of a
     * type are the targets of the step links its own packs write from it, before the merge, so a merged type keeps the
     * steps of every version it layers over. The fault is the reference's, {@code pack_reference_artifact_missing},
     * with a violation {@value MissionStepLinkRule#CODE} that names the edge.
     */
    static List<ClosureFault> strayStepLink(MergedRelations relations) {
        Map<Urn, Set<String>> steps = new LinkedHashMap<>();
        relations.written().stream()
                .filter(written -> !written.foreign()
                        && written.isStepLink()
                        && written.edge().toPack().isEmpty())
                .forEach(written -> steps.computeIfAbsent(written.source(), ignored -> new LinkedHashSet<>())
                        .add(written.edge().to().id()));
        return relations.placed().stream()
                .filter(placed -> placed.foreign()
                        && placed.isStepLink()
                        && !steps.getOrDefault(placed.source(), Set.of())
                                .contains(placed.edge().to().id()))
                .map(placed -> {
                    PackUnderResolution holder = relations.packOf(placed.holder());
                    String detail = "the mission type in force `" + placed.source() + "` of pack `"
                            + holder.descriptor().name() + "` is required to `"
                            + placed.edge().to()
                            + "` by another pack's edge, which names no step of the type";
                    return fault(
                            RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING,
                            detail,
                            List.of(holder),
                            List.of(new RefusalViolation("", MissionStepLinkRule.CODE, detail)));
                })
                .toList();
    }

    /** A fault naming packs of the closure, each once, about the first it names. */
    private static ClosureFault fault(
            RefusalCode code, String detail, List<PackUnderResolution> packs, List<RefusalViolation> violations) {
        List<RefusalPack> named = packs.stream()
                .distinct()
                .map(pack -> new RefusalPack(
                        pack.descriptorId(),
                        pack.configured().id(),
                        pack.descriptor().name()))
                .toList();
        return ClosureFault.of(PackOrigins.firstOf(named), code, detail, named, violations, List.of());
    }
}
