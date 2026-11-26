package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.packs.domain.ClosureFacts;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ComposedRelations;
import ai.speckitty.charter.packs.domain.EnhancementCompose;
import ai.speckitty.charter.packs.domain.GraphCheck;
import ai.speckitty.charter.packs.domain.MergedRelations;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackGraph;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.ProfileEnhancementGateway;
import ai.speckitty.charter.packs.domain.ScopedPolicies;
import ai.speckitty.charter.packs.domain.ValidationWalker;
import ai.speckitty.charter.packs.domain.graph.PackGraphNode;
import ai.speckitty.charter.packs.domain.graph.PackNode;
import ai.speckitty.charter.packs.domain.graph.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The named compositions of the content rules, of {@code CLOSURE} and of {@code REFERENCES} (#554, FR-002, FR-003, S1
 * to S3; #632 item 3): each surface runs a composition by its name, with no surface profile and no mode flag. The
 * storage edge lifts {@link #content()} onto its one read ({@code StorageRulesets.registration()}), and validate mode
 * takes the same verdict through the domain port {@code PackReading}, so what registration finds, validate mode finds
 * too, by construction.
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

    /**
     * The name of the own-closure phase: the closure walk's name, which ruling R-u keeps free for it. A caller reads it
     * from a {@link ai.speckitty.charter.packs.domain.Ledger#gate()} to place the own-closure breaches apart from the
     * closure's other faults.
     */
    public static final String OWN_CLOSURE_PHASE = "closure_walk";

    private static final String RELATIONS_PHASE = "relations";

    private static final String EDGE_INDEX_PHASE = "edge_index";

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
     * artifact and asset ids, the repeated substep id, the prompt slots, the step enhancements with their fragments,
     * the mission type's step links and substep delegations, the routing catalogs' consistency
     * ({@link RoutingCatalogRules}, ADR-0080), the reference closure ({@link ReferenceRules}, #355: an asset, guard,
     * lifecycle or mission type that does not close over what it names), and the orphan fragments last, in the order a
     * registration lists them. Collect-all: every rule runs, in this order.
     *
     * @return the composition
     */
    public static ModularRuleset<PackContents> content() {
        return CONTENT;
    }

    /**
     * {@code CLOSURE}, the set check pack-doctor runs (#478), so pack-doctor names one composition and defines no rule
     * of its own: every composition fault of a set of packs with levels ({@link CompositionRules#faults}), then the
     * preset contests ({@link CompositionRules#presetFaults}), judged in merge order so the answer does not depend on
     * the order the packs are given in, then the preset items ({@link PresetItemRules}, #629, #632 item 1), judged over
     * each pack's own closure (R-k), then the case-only answer ({@link CaseOnlyRules}, rulings R-y, R-z), judged over
     * the closure rooted at each pack as registration's phase 6 judges it. Collect-all: every phase runs. Its reasons
     * are {@link #closureFaults}', in that order, each with its phase's rule name: there is one derivation.
     * Registration and every write run the same bodies: {@link #presetItemFaults}, and {@link CaseOnlyRules#matches} in
     * the composing judge.
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
     * Registration, every write and the startup census get these rules here and nowhere else; run them over
     * {@link #ownClosureOf} of the declaring pack. Production runs them as the last phase of {@link #composedClosure};
     * this check alone is the phase's unit under test.
     *
     * @param closure the declaring pack's own closure, as the reads compose it
     * @param profiles where a winning profile's entity is read
     * @param merge the profiles slice's merge, which judges an enhancement; empty when the deployment has none
     * @return the check: the packs walk and the one phase
     */
    static GraphCheck ownClosure(
            ClosureFacts closure, ProfileReader profiles, Optional<ProfileEnhancementGateway> merge) {
        Objects.requireNonNull(closure, "closure");
        return new GraphCheck(
                PackGraph.Walk.PACKS, List.of(ownClosurePhase(() -> closure, PackNode::descriptor, profiles, merge)));
    }

    /**
     * The own-closure phase, its closure and the declaring pack's descriptor read only when the phase runs, and once
     * each: a refusal to read them never displaces a fault of an earlier phase.
     */
    private static ValidationWalker.Phase<PackGraphNode> ownClosurePhase(
            Supplier<? extends ClosureFacts> closureOf,
            Function<PackNode, PackDescriptor> descriptorOf,
            ProfileReader profiles,
            Optional<ProfileEnhancementGateway> merge) {
        Objects.requireNonNull(closureOf, "closureOf");
        Objects.requireNonNull(descriptorOf, "descriptorOf");
        Objects.requireNonNull(profiles, "profiles");
        Objects.requireNonNull(merge, "merge");
        Supplier<ClosureFacts> closure = once(closureOf::get);
        return new ValidationWalker.Phase<PackGraphNode>(
                OWN_CLOSURE_PHASE,
                List.of(
                        new ValidationWalker.OnNode<PackGraphNode, PackNode>(
                                "fallback_profile",
                                PackNode.class,
                                node -> descriptorOf
                                        .apply(node)
                                        .fallbackProfile()
                                        .isPresent(),
                                node -> FallbackProfileRule.check(
                                        node.pack(),
                                        descriptorOf
                                                .apply(node)
                                                .fallbackProfile()
                                                .orElseThrow(),
                                        closure.get(),
                                        profiles)),
                        new ValidationWalker.OnNode<PackGraphNode, PackNode>(
                                "role_pins",
                                PackNode.class,
                                node -> true,
                                node -> RolePinRule.ownPins(node.pack(), closure.get(), profiles)),
                        new ValidationWalker.OnClose<PackGraphNode>(
                                "role_pin_conflicts", nodes -> RolePinRule.conflicts(closure.get())),
                        new ValidationWalker.OnClose<PackGraphNode>(
                                "profile_enhancements",
                                nodes -> ProfileEnhancementRule.check(closure.get(), profiles, merge))));
    }

    /**
     * The relation rules of a composed closure (#639, ADR-0082 slice 4): one phase over the closure's relations walk,
     * judging the subject's placed edges, in the order the rules were judged before the move. In order: a self-override
     * ({@code self_override}), an artifact that both enhances and overrides one target ({@code conflicting_relations}),
     * an override of a target that is not open and an enhancement of a step whose mission type is locked
     * ({@code override_not_permitted}), an override cycle ({@code override_cycle}), an enhances cycle and an ungrounded
     * enhancer ({@code enhances_cycle}), then a specialization cycle ({@code specialization_cycle}). The relation rules
     * run only when the closure writes an {@code enhances} or {@code overrides} edge; the specialization rule runs
     * regardless. Each rule reads the subject, since where an edge's ends are placed is a fact of the closure, so the
     * answer does not depend on the elements the walk hands over; the {@code RELATIONS} mode names what the check is
     * about. {@link #composedClosure} runs it ahead of the edge-index rules.
     *
     * @param relations the closure's typed edges, placed
     * @return the check: the relations walk and the one phase; production runs it as the first phase of
     *     {@link #composedClosure}, and this check alone is the phase's unit under test
     */
    static GraphCheck relations(ComposedRelations relations) {
        return new GraphCheck(PackGraph.Walk.RELATIONS, List.of(relationsPhase(relations)));
    }

    private static ValidationWalker.Phase<PackGraphNode> relationsPhase(ComposedRelations relations) {
        Objects.requireNonNull(relations, "relations");
        return new ValidationWalker.Phase<PackGraphNode>(
                RELATIONS_PHASE,
                List.of(
                        relation("self_override", relations, RelationRules::selfOverride),
                        relation("conflicting_relations", relations, RelationRules::conflicting),
                        relation("override_not_permitted", relations, RelationRules::notPermitted),
                        relation("locked_step_enhanced", relations, RelationRules::lockedStepEnhanced),
                        relation("override_cycle", relations, RelationRules::overrideCycle),
                        relation("enhances_cycle", relations, RelationRules::enhancesCycle),
                        relation("ungrounded_enhancer", relations, RelationRules::ungroundedEnhancer),
                        new ValidationWalker.OnClose<PackGraphNode>(
                                "specialization_cycle", nodes -> RelationRules.specializationCycle(relations))));
    }

    /** A relation rule, judged only when the closure writes an {@code enhances} or {@code overrides} edge. */
    private static ValidationWalker.Check<PackGraphNode> relation(
            String name, ComposedRelations relations, Function<ComposedRelations, List<ClosureFault>> rule) {
        return new ValidationWalker.OnClose<>(
                name, nodes -> relations.edges().isEmpty() ? List.of() : rule.apply(relations));
    }

    /**
     * The relations of a composed closure, as the relation rules judge them: its packs, the typed edges each writes and
     * the policy each declares, scoped as the mutability check scopes them ({@link ScopedPolicies}).
     *
     * @param packs the closure's packs, in merge order
     * @param factsOf each pack's composition facts, as the closure was composed with them
     * @return the subject
     */
    public static ComposedRelations relationsOf(
            List<PackUnderResolution> packs, Function<PackUnderResolution, PackCompositionFacts> factsOf) {
        Objects.requireNonNull(packs, "packs");
        Objects.requireNonNull(factsOf, "factsOf");
        return ComposedRelations.of(
                packs,
                member -> factsOf.apply(member).edges(),
                member -> factsOf.apply(member).policy());
    }

    /**
     * The edge-index rules of a composed closure (#639, ADR-0082 slice 4, operator rulings D2, Q-B and Q-C): one phase
     * over the merged closure, so a read and a registration of one closure answer each fault with one code. In order,
     * the order the merge met them before the move: a layer override the overridden artifact's level forbids (C21,
     * {@code override_not_permitted}), an enhancement of a step whose mission type is locked
     * ({@code override_not_permitted}), an {@code overrides} edge that the merge re-points to its own source (C36, A12,
     * {@code self_override}), and another pack's step link at a mission type in force that names no step of it
     * ({@code pack_reference_artifact_missing}, with the violation
     * {@value ai.speckitty.charter.packs.domain.MissionStepLinkRule#CODE}). Every fault of each rule is reported. Each
     * rule reads the subject, so the answer does not depend on the elements the walk hands over; the subject is read
     * only when the phase runs, and once. The reads run it through {@link #edgeIndexFaults}, registration and every
     * write after the relation rules ({@link #composedClosure}).
     *
     * @param merged the merged closure's subject, read when the phase runs
     * @return the check: the packs walk and the one phase
     */
    static GraphCheck edgeIndex(Supplier<MergedRelations> merged) {
        return new GraphCheck(PackGraph.Walk.PACKS, List.of(edgeIndexPhase(merged)));
    }

    private static ValidationWalker.Phase<PackGraphNode> edgeIndexPhase(Supplier<MergedRelations> merged) {
        Objects.requireNonNull(merged, "merged");
        Supplier<MergedRelations> once = once(merged);
        return new ValidationWalker.Phase<PackGraphNode>(
                EDGE_INDEX_PHASE,
                List.of(
                        new ValidationWalker.OnClose<PackGraphNode>(
                                "forbidden_layer_override", nodes -> EdgeIndexRules.forbiddenLayerOverride(once.get())),
                        new ValidationWalker.OnClose<PackGraphNode>(
                                "locked_step_enhanced", nodes -> EdgeIndexRules.lockedStepEnhanced(once.get())),
                        new ValidationWalker.OnClose<PackGraphNode>(
                                "self_override", nodes -> EdgeIndexRules.selfOverride(once.get())),
                        new ValidationWalker.OnClose<PackGraphNode>(
                                "stray_step_link", nodes -> EdgeIndexRules.strayStepLink(once.get()))));
    }

    /**
     * Every edge-index fault of a merged closure ({@link #edgeIndex}), run by the walker over the closure's packs,
     * every pack a root: what a read refuses when it merges. A closure with no pack has no fault.
     *
     * @param merged the merged closure's subject
     * @return the faults, rule by rule; empty when the merge is sound
     */
    public static List<ClosureFault> edgeIndexFaults(MergedRelations merged) {
        Objects.requireNonNull(merged, "merged");
        if (merged.packs().isEmpty()) {
            return List.of();
        }
        PackGraph graph = PackGraph.of(
                merged.packs().stream()
                        .map(member -> new PackNode(member.configured(), member.descriptor()))
                        .toList(),
                pack -> {
                    throw new IllegalStateException("the packs walk reads no pack's facts: " + pack.value());
                });
        return edgeIndex(() -> merged)
                .run(
                        graph,
                        Scope.of(merged.packs().stream()
                                .map(member -> member.configured().id())
                                .distinct()
                                .toList()))
                .faults();
    }

    /**
     * The checks of one composed closure that registration and every write judge (#639, ADR-0082 slice 4, FR-009,
     * FR-010): the relation rules ({@link #relations}), then, only when they pass, the edge-index rules
     * ({@link #edgeIndex}), one walker over the closure's relations walk; the phase gate is the relations gate. Run it
     * over {@code Scope.of} the closure's roots.
     *
     * @param relations the closure's typed edges, placed
     * @param merged the merged closure's subject, read only when the edge-index phase runs, and once
     * @return the check
     */
    public static GraphCheck composedClosure(ComposedRelations relations, Supplier<MergedRelations> merged) {
        return new GraphCheck(PackGraph.Walk.RELATIONS, List.of(relationsPhase(relations), edgeIndexPhase(merged)));
    }

    /**
     * The checks of one declaring pack's own composed closure (#638, #639, ADR-0082 slice 4): the relation rules, the
     * edge-index rules, then, only when both pass, the own-closure rules ({@link #ownClosure}), one walker over the
     * closure's relations walk; the own-closure rules read the merge only once the edge-index rules found it sound, so
     * a fallback or a pin never meets a refused merge (#667). Run it over {@link #ownClosureOf} of the declaring pack:
     * the relation and edge-index rules read their subjects, so the selection does not change their answer.
     *
     * <p>The declaring pack's own closure and its descriptor with the write in place are read only when the
     * own-closure phase runs, so a refusal to read either never displaces or hides a relation or edge-index fault.
     *
     * @param relations the closure's typed edges, placed
     * @param merged the merged closure's subject, read only when the edge-index phase runs, and once
     * @param closure the declaring pack's own closure, as the reads compose it, read only when the own-closure phase
     *     runs, and once
     * @param declaring the declaring pack's descriptor with the write in place, read as the closure is
     * @param profiles where a winning profile's entity is read
     * @param merge the profiles slice's merge, which judges an enhancement; empty when the deployment has none
     * @return the check
     */
    public static GraphCheck composedClosure(
            ComposedRelations relations,
            Supplier<MergedRelations> merged,
            Supplier<? extends ClosureFacts> closure,
            Supplier<PackDescriptor> declaring,
            ProfileReader profiles,
            Optional<ProfileEnhancementGateway> merge) {
        Objects.requireNonNull(declaring, "declaring");
        Supplier<PackDescriptor> descriptor = once(declaring);
        return new GraphCheck(
                PackGraph.Walk.RELATIONS,
                List.of(
                        relationsPhase(relations),
                        edgeIndexPhase(merged),
                        ownClosurePhase(closure, node -> descriptor.get(), profiles, merge)));
    }

    /** The supplier read once, on first use; a walker runs one traversal at a time, so it is never read twice. */
    private static <T> Supplier<T> once(Supplier<T> supplier) {
        List<T> held = new ArrayList<>(1);
        return () -> {
            if (held.isEmpty()) {
                held.add(Objects.requireNonNull(supplier.get(), "the subject"));
            }
            return held.getFirst();
        };
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
