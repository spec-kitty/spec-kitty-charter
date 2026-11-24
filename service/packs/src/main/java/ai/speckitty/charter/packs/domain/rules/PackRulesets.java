package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.EnhancementCompose;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.Preset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The named compositions of the content rules, of {@code CLOSURE} and of {@code REFERENCES} (#554, FR-002, FR-003, S1 to
 * S3; #632 item 3): each surface runs a composition by its name,
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

    /** One member's check within a closure: the domain's one body of it. */
    @FunctionalInterface
    private interface MemberCheck {
        List<ClosureFault> faults(
                List<PackUnderResolution> closure, PackUnderResolution member, PackCompositionFacts facts);
    }

    /**
     * {@code REFERENCES}' checks, in registration's phase order (its phases 2 to 4): the substep delegations, the
     * references, the step enhancements' targets.
     */
    private static final List<MemberCheck> REFERENCES = List.of(
            PackCompositionResolver::delegationFaultsOf,
            PackCompositionResolver::referenceFaultsOf,
            EnhancementCompose::targetFaultsOf);

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
     * {@code CLOSURE}, the set check pack-doctor runs (#478), so pack-doctor names one composition and defines no rule of
     * its own: every composition fault of a set of packs with levels ({@link CompositionRules#faults}), then the preset
     * contests ({@link CompositionRules#presetFaults}), judged in merge order so the answer does not depend on the order
     * the packs are given in, then the preset items ({@link PresetItemRules}, #629, #632 item 1), judged over each pack's
     * own closure (R-k), then the case-only answer ({@link CaseOnlyRules}, rulings R-y, R-z), judged over the closure
     * rooted at each pack as registration's phase 6 judges it. Collect-all: every phase runs. Its reasons are
     * {@link #closureFaults}', in that order, each with its phase's rule name: there is one derivation. Registration and
     * every write run the same bodies: {@link #presetItemFaults}, and {@link CaseOnlyRules#matches} in the composing
     * judge.
     *
     * @param factsOf each pack's composition facts: the presets it holds, and what it holds and declares
     * @return the composition
     */
    public static ModularRuleset<List<PackUnderResolution>> closure(
            Function<PackUnderResolution, PackCompositionFacts> factsOf) {
        Objects.requireNonNull(factsOf, "factsOf");
        return ModularRuleset.<List<PackUnderResolution>>emptyRules()
                .must(packs -> closureFaults(packs, factsOf).stream()
                        .map(ClosureFault::reason)
                        .toList());
    }

    /**
     * The faults of {@link #closure(Function)}, with the packs each names (#478, A-M4): every composition fault, then
     * every preset contest, then every preset-item fault, then every case-only preset item or policy lock, in the order
     * the ruleset reports them: the one place the composition is defined. Each fault's rule is its phase's
     * ({@link CompositionRules#RULE}, {@link CompositionRules#PRESETS_RULE}, {@link PresetItemRules#RULE},
     * {@link CaseOnlyRules#RULE}). What pack-doctor reports, so it and the ruleset cannot disagree.
     *
     * @param packs the packs with their descriptors
     * @param factsOf each pack's composition facts
     * @return the faults; empty when the set resolves, no preset is contested, every preset item is in its own closure
     *     and no preset item or policy lock names an artifact of a closure only by case
     * @throws IllegalStateException when a descriptor is not well formed, as {@link CompositionRules#faults} refuses it
     */
    public static List<ClosureFault> closureFaults(
            List<PackUnderResolution> packs, Function<PackUnderResolution, PackCompositionFacts> factsOf) {
        Objects.requireNonNull(factsOf, "factsOf");
        return Stream.of(
                        CompositionRules.faults(packs),
                        CompositionRules.presetFaults(
                                packs,
                                pack -> factsOf.apply(pack).presets().stream()
                                        .map(Preset::id)
                                        .toList()),
                        presetItemFaults(packs, factsOf),
                        CaseOnlyRules.phase(factsOf).apply(packs))
                .flatMap(List::stream)
                .toList();
    }

    /**
     * {@code REFERENCES} within one composed closure (#632 item 3, ruling R-ab): the one body of the ordered checks and
     * their stop rule, which registration's composing judge and pack-doctor's {@code --full} both run. In order, the
     * substep delegations ({@link PackCompositionResolver#delegationFaultsOf}), the references
     * ({@link PackCompositionResolver#referenceFaultsOf}) and the step enhancements' targets
     * ({@link EnhancementCompose#targetFaultsOf}), each judged for every member given, in the order given; the first
     * check that finds any fault answers all of them, and the later checks do not run, since a later check would answer
     * the same cause again (a delegation candidate is also the reference of its {@code scope} edge). Registration judges
     * every member of the closure it composes; pack-doctor's {@code --full} does the same per root of its set, every
     * pack of the root's closure a member (ruling R-ae), reporting a fault several roots find once.
     *
     * @param closure the composed closure, in merge order
     * @param members the members whose checks run, each a pack of the closure
     * @param factsOf each member's composition facts
     * @return every fault of the first check that finds any, member by member; empty when every check passes
     */
    public static List<ClosureFault> references(
            List<PackUnderResolution> closure,
            List<PackUnderResolution> members,
            Function<PackUnderResolution, PackCompositionFacts> factsOf) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(members, "members");
        Objects.requireNonNull(factsOf, "factsOf");
        for (MemberCheck check : REFERENCES) {
            List<ClosureFault> found = new ArrayList<>();
            for (PackUnderResolution member : members) {
                found.addAll(check.faults(closure, member, factsOf.apply(member)));
            }
            if (!found.isEmpty()) {
                return List.copyOf(found);
            }
        }
        return List.of();
    }

    /**
     * The preset-item phase alone ({@link PresetItemRules}): what registration and every write run over a composed
     * closure, beside the closure walk ({@code ClosureImpactCheck}), so they refuse what pack-doctor's
     * {@link #closureFaults} reports.
     *
     * @param packs the packs with their descriptors
     * @param factsOf each pack's composition facts
     * @return the faults, in pack-id, preset path and item order; empty when every item is in its own closure
     */
    public static List<ClosureFault> presetItemFaults(
            List<PackUnderResolution> packs, Function<PackUnderResolution, PackCompositionFacts> factsOf) {
        return PresetItemRules.phase(factsOf).apply(packs);
    }
}
