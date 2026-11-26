package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.packs.domain.ClosureFacts;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.EnhancementCompose;
import ai.speckitty.charter.packs.domain.GraphCheck;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackGraph;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.ProfileEnhancementGateway;
import ai.speckitty.charter.packs.domain.ValidationWalker;
import ai.speckitty.charter.packs.domain.graph.PackGraphNode;
import ai.speckitty.charter.packs.domain.graph.PackNode;
import ai.speckitty.charter.packs.domain.graph.Scope;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    /** One of {@code REFERENCES}' checks, by name. */
    private record NamedCheck(String name, MemberCheck check) {}

    /** The name of the own-closure phase: the closure walk's name, which ruling R-u keeps free for it. */
    private static final String OWN_CLOSURE = "closure_walk";

    /**
     * {@code REFERENCES}' checks, in registration's phase order (its phases 2 to 4): the substep delegations, the
     * references, the step enhancements' targets.
     */
    private static final List<NamedCheck> REFERENCES = List.of(
            new NamedCheck("substep_delegations", PackCompositionResolver::delegationFaultsOf),
            new NamedCheck("references", PackCompositionResolver::referenceFaultsOf),
            new NamedCheck("enhancement_targets", EnhancementCompose::targetFaultsOf));

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
     * the same cause again (a delegation candidate is also the reference of its {@code scope} edge). Registration
     * judges every member of the closure it composes; pack-doctor's {@code --full} does the same per root of its set,
     * every pack of the root's closure a member (ruling R-ae), reporting a fault several roots find once. Each check is
     * a phase of the {@link ValidationWalker}, handed each member itself in the order given; its phase gate is the
     * stop rule (every closure fault is a violation).
     *
     * <p>Interim: the members reach the walker by a direct feed, not by {@code PackGraph#walk}. The nodes are
     * {@link PackUnderResolution}s, not pack-graph nodes, and the caller's members (a subset of the closure, in the
     * caller's order) are not the composition closure of a scope, which a driver would walk in the graph's order.
     * Slice 4 of epic #636 (relations as rules) moves this check onto a graph walk with a mode of its own.
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
        List<ValidationWalker.Phase<PackUnderResolution>> phases = REFERENCES.stream()
                .map(named -> new ValidationWalker.Phase<PackUnderResolution>(
                        named.name(),
                        List.of(new ValidationWalker.OnNode<PackUnderResolution, PackUnderResolution>(
                                named.name(),
                                PackUnderResolution.class,
                                member -> true,
                                member -> named.check().faults(closure, member, factsOf.apply(member))))))
                .toList();
        return ValidationWalker.of(phases)
                .run(visitor -> members.forEach(visitor::onNode))
                .faults();
    }

    /**
     * The own-closure rules of one declaring pack (#556 FR-005, #638): one collect-all phase over the declaring pack's
     * node, judging its own closure's facts with the write in place. In order: its ADR-0068 fallback profile
     * ({@link FallbackProfileRule}), only when the pack's descriptor names one; its own role pins, then the pin
     * conflicts of its closure ({@link RolePinRule}); then the profile enhancements in force
     * ({@link ProfileEnhancementRule}), which passes when there are none. Every breach of each rule is reported.
     * The fallback and own-pin rules run on the declaring pack's node, so a walk that does not hand it over exactly
     * once would pass them unjudged; the check throws {@link IllegalStateException} then, rather than report nothing.
     * Registration, every write and the startup census get these rules here and nowhere else; run them over
     * {@link #ownClosureOf} of the declaring pack.
     *
     * @param closure the declaring pack's own closure, as the reads compose it
     * @param profiles where a winning profile's entity is read
     * @param merge the profiles slice's merge, which judges an enhancement; empty when the deployment has none
     * @return the check: the packs walk and the one phase
     */
    public static GraphCheck ownClosure(
            ClosureFacts closure, ProfileReader profiles, Optional<ProfileEnhancementGateway> merge) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(profiles, "profiles");
        Objects.requireNonNull(merge, "merge");
        return new GraphCheck(
                PackGraph.Walk.PACKS,
                List.of(new ValidationWalker.Phase<PackGraphNode>(
                        OWN_CLOSURE,
                        List.of(
                                new ValidationWalker.OnClose<PackGraphNode>("declaring_pack_visited", nodes -> {
                                    long packs = nodes.stream()
                                            .filter(PackNode.class::isInstance)
                                            .count();
                                    if (packs != 1) {
                                        throw new IllegalStateException("the own-closure rules judge the declaring"
                                                + " pack's node exactly once, and the walk handed over " + packs);
                                    }
                                    return List.of();
                                }),
                                new ValidationWalker.OnNode<PackGraphNode, PackNode>(
                                        "fallback_profile",
                                        PackNode.class,
                                        node -> node.descriptor()
                                                .fallbackProfile()
                                                .isPresent(),
                                        node -> FallbackProfileRule.check(
                                                node.pack(),
                                                node.descriptor()
                                                        .fallbackProfile()
                                                        .orElseThrow(),
                                                closure,
                                                profiles)),
                                new ValidationWalker.OnNode<PackGraphNode, PackNode>(
                                        "role_pins",
                                        PackNode.class,
                                        node -> true,
                                        node -> RolePinRule.ownPins(node.pack(), closure, profiles)),
                                new ValidationWalker.OnClose<PackGraphNode>(
                                        "role_pin_conflicts", nodes -> RolePinRule.conflicts(closure)),
                                new ValidationWalker.OnClose<PackGraphNode>(
                                        "profile_enhancements",
                                        nodes -> ProfileEnhancementRule.check(closure, profiles, merge))))));
    }

    /**
     * The scope of a declaring pack's own-closure check: its own closure, the declaring pack alone selected.
     *
     * @param declaring the declaring pack, by configured identifier
     * @return the scope
     */
    public static Scope ownClosureOf(PackIdentifier declaring) {
        Objects.requireNonNull(declaring, "declaring");
        return new Scope(
                List.of(declaring),
                node -> node instanceof PackNode pack && pack.pack().equals(declaring));
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
