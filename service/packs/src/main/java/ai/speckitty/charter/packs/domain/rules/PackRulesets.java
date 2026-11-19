package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.rules.CompositionRules.CompositionFault;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The named compositions of the content rules and of {@code CLOSURE} (#554, FR-002, FR-003, S1 to S3): each surface runs a composition by its name,
 * with no surface profile and no mode flag. The storage edge lifts {@link #content()} onto its one read
 * ({@code StorageRulesets.registration()}), and validate mode takes the same verdict through the domain port
 * {@code PackReading}, so what registration finds, validate mode finds too, by construction.
 *
 * <p>No glossary rule and no asset-escape rule: both are the edge's checks (S2). The glossary's checks are the one
 * reader's prechecks, a storage format rule, a retired shape or a {@link GraphBinding} fault (WP06 D1); a path that
 * leaves {@code assets/} is the reader's sidecar check alone (WP06 D2).
 */
public final class PackRulesets {

    private static final ModularRuleset<PackContents> CONTENT = ModularRuleset.<PackContents>emptyRules()
            .must(GraphRules.RULES)
            .must(ArtifactRules.RULES)
            .must(AssetRules.RULES)
            .must(StepRules.RULES)
            .must(PromptSlotRules.RULES)
            .must(FragmentRules.ENHANCEMENT)
            .must(MissionTypeRules.RULES)
            .must(RoutingCatalogRules.RULES)
            .must(ReferenceRules.RULES)
            .must(FragmentRules.ORPHAN);

    private PackRulesets() {}

    /**
     * The pack's consistency, over the facts of the one read: the graph (binding and the endpoint bridge), the repeated
     * artifact and asset ids, the repeated substep id, the prompt slots, the step enhancements with their fragments, the
     * mission type's step links and substep delegations, the routing catalogs' consistency ({@link RoutingCatalogRules},
     * ADR-0080), the reference closure ({@link ReferenceRules}, #355: an
     * asset, guard, lifecycle or mission type that does not close over what it names), and the orphan fragments last,
     * in the order a registration lists them. Collect-all: every rule runs, in this order.
     *
     * @return the composition
     */
    public static ModularRuleset<PackContents> content() {
        return CONTENT;
    }

    /**
     * {@code CLOSURE}, the pack set's consistency ({@link CompositionRules#closure()}): every composition fault of a set
     * of packs with levels, collect-all. Validate mode does not run it; the pack-set check is pack-check's (#478).
     *
     * @return the composition
     */
    public static ModularRuleset<List<PackUnderResolution>> closure() {
        return CompositionRules.closure();
    }

    /**
     * {@code CLOSURE} with its preset phase ({@link CompositionRules#presets}): the set check pack-check runs (#478), so
     * pack-check names one composition and defines no rule of its own. The preset phase judges the packs in merge order,
     * so the answer does not depend on the order the packs are given in. Its reasons are {@link #closureFaults}', in
     * that order, each with its phase's rule name: there is one derivation, and the closure walk's checks (#556) extend
     * it in {@link #phases}. The service paths that judge presets elsewhere keep {@link #closure()}.
     *
     * @param presetIdsOf the ids of the presets a pack holds
     * @return the composition
     */
    public static ModularRuleset<List<PackUnderResolution>> closure(
            Function<PackUnderResolution, Collection<String>> presetIdsOf) {
        Objects.requireNonNull(presetIdsOf, "presetIdsOf");
        return ModularRuleset.<List<PackUnderResolution>>emptyRules()
                .must(packs -> phases(packs, presetIdsOf).stream()
                        .flatMap(phase -> phase.faults().stream().map(fault -> fault.reason(phase.rule())))
                        .toList());
    }

    /**
     * The faults of {@link #closure(Function)}, with the packs each names (#478, A-M4): every fault of {@code CLOSURE},
     * then every preset contest, in the order the ruleset reports them. What pack-check reports, so it and the ruleset
     * cannot disagree.
     *
     * @param packs the packs with their descriptors
     * @param presetIdsOf the ids of the presets a pack holds
     * @return the faults; empty when the set resolves and no preset is contested
     * @throws IllegalStateException when a descriptor is not well formed, as {@link CompositionRules#faults} refuses it
     */
    public static List<CompositionFault> closureFaults(
            List<PackUnderResolution> packs, Function<PackUnderResolution, Collection<String>> presetIdsOf) {
        Objects.requireNonNull(presetIdsOf, "presetIdsOf");
        return phases(packs, presetIdsOf).stream()
                .flatMap(phase -> phase.faults().stream())
                .toList();
    }

    /** One phase of {@code CLOSURE}: the rule name its reasons carry and the faults it found. */
    private record Phase(String rule, List<CompositionFault> faults) {}

    /** The phases of {@code CLOSURE} with presets, in order: the one place the composition is defined. */
    private static List<Phase> phases(
            List<PackUnderResolution> packs, Function<PackUnderResolution, Collection<String>> presetIdsOf) {
        return List.of(
                new Phase(CompositionRules.RULE, CompositionRules.faults(packs)),
                new Phase(CompositionRules.PRESETS_RULE, CompositionRules.presetFaults(packs, presetIdsOf)));
    }
}
