package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackContents;
import java.util.Collection;
import java.util.List;
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
     * so the answer does not depend on the order the packs are given in. The closure walk's checks (#556) extend it
     * here. The service paths that judge presets elsewhere keep {@link #closure()}.
     *
     * @param presetIdsOf the ids of the presets a pack holds
     * @return the composition
     */
    public static ModularRuleset<List<PackUnderResolution>> closure(
            Function<PackUnderResolution, Collection<String>> presetIdsOf) {
        return closure().must(CompositionRules.presets(presetIdsOf));
    }
}
