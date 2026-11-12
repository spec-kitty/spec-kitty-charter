package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.InForceRequest;
import ai.speckitty.charter.packs.domain.InForceStates;
import ai.speckitty.charter.packs.domain.InForceStates.Bucket;
import ai.speckitty.charter.packs.domain.InForceStates.Reason;
import ai.speckitty.charter.packs.domain.InForceStates.State;
import ai.speckitty.charter.packs.domain.LayerOverride;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link ClosureView} over a real compose and a real {@link ClosureQueries} read: the in-force resolution with the
 * binding of a step enhancer (OD-6, rule E6 as amended) and the node predicate the effective reads share (decisions
 * {@code 01M702A71YF7WNDTG2AA89K8ZA} and {@code 01M7037ZNKJ6JTRKGD7G695ZV1}). No fixture declares a facts binding for a
 * step enhancement, so only the amended rule can bind one.
 */
class ClosureViewTest {

    private final List<ConfiguredPack> configured = new ArrayList<>();
    private final Map<String, PackDescriptor> descriptors = new HashMap<>();
    private final Map<String, PackCompositionFacts> facts = new HashMap<>();
    private final List<String> factsRead = new ArrayList<>();

    private static Urn u(String urn) {
        return Urn.parse(urn).orElseThrow();
    }

    private static Set<Urn> urns(String... urns) {
        return Set.of(java.util.Arrays.stream(urns).map(ClosureViewTest::u).toArray(Urn[]::new));
    }

    private static Relationship edge(String from, RelationType type, String to) {
        PackQualifiedReference source = PackQualifiedReference.parse(from);
        PackQualifiedReference target = PackQualifiedReference.parse(to);
        return new Relationship(
                source.asUrn().orElseThrow(),
                source.packName(),
                target.asUrn().orElseThrow(),
                target.packName(),
                type,
                Optional.empty(),
                Optional.empty());
    }

    /** A pack of the closure holding the artifacts and writing the edges, with no facts binding. */
    private void pack(String name, PackLevel level, List<String> dependsOn, List<String> held, Relationship... edges) {
        pack(name, level, dependsOn, MutabilityPolicySource.empty(), held, edges);
    }

    private void pack(
            String name,
            PackLevel level,
            List<String> dependsOn,
            MutabilityPolicySource policy,
            List<String> held,
            Relationship... edges) {
        configured.add(new ConfiguredPack(new PackIdentifier(name), level));
        descriptors.put(
                name,
                new PackDescriptor(
                        "id-" + name,
                        "1.0.0",
                        Optional.empty(),
                        name,
                        Optional.empty(),
                        Optional.empty(),
                        PackPaths.none(),
                        dependsOn.stream()
                                .map(other -> new PackDependency("id-" + other, ">=0"))
                                .toList()));
        facts.put(
                name,
                new PackCompositionFacts(List.of(), held, List.of(), policy, List.of(), List.of(edges), Map.of()));
    }

    private void pack(String name, List<String> held, Relationship... edges) {
        pack(name, PackLevel.ORG, List.of(), held, edges);
    }

    private ClosureView view() {
        PackDescriptorRepository repository = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return descriptors.get(pack.value());
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier pack) {
                return Optional.ofNullable(descriptors.get(pack.value()));
            }

            @Override
            public void save(PackIdentifier pack, PackDescriptor descriptor) {
                throw new UnsupportedOperationException("the closure never writes");
            }
        };
        ConsistentRead read = new ConsistentRead(() -> "sha256:" + "1".repeat(64));
        PackCatalogRepository catalog = () -> List.copyOf(configured);
        OfferingReadService offering = new OfferingReadService(
                catalog,
                repository,
                pack -> {
                    factsRead.add(pack.value());
                    return facts.get(pack.value());
                },
                read);
        return new ClosureQueries(offering, catalog, read).allConfigured();
    }

    private InForceStates select(String... selected) {
        return view().inForce(InForceRequest.selecting(urns(selected)));
    }

    private static void expect(InForceStates states, String urn, State state, Reason reason) {
        assertThat(states.stateOf(u(urn))).as(urn).hasValueSatisfying(actual -> {
            assertThat(actual.state()).as(urn + " state").isEqualTo(state);
            assertThat(actual.reason()).as(urn + " reason").isEqualTo(reason);
        });
    }

    // ---- OD-6: a step enhancer is grounded when the mission type of its single action target is in force ----------

    private void aStepEnhancerOfAHeldStep() {
        pack(
                "alpha",
                List.of("mission_type:M", "action:M/s", "step_enhancement:x"),
                edge("step_enhancement:x", RelationType.ENHANCES, "action:M/s"));
    }

    @Test
    void aStepEnhancerIsActiveWhenItAndTheMissionTypeOfItsStepAreSelectedAndNoActionIs() {
        aStepEnhancerOfAHeldStep();

        InForceStates states = select("step_enhancement:x", "mission_type:M");

        expect(states, "step_enhancement:x", State.ACTIVE, Reason.SELECTED);
        assertThat(states.enhancedTargets()).isEmpty();
    }

    @Test
    void aStepEnhancerWhoseMissionTypeIsNotSelectedIsPrunedForTheMissionType() {
        aStepEnhancerOfAHeldStep();

        InForceStates states = select("step_enhancement:x");

        expect(states, "step_enhancement:x", State.INACTIVE, Reason.MISSION_TYPE_NOT_IN_FORCE);
        assertThat(states.stateOf(u("step_enhancement:x")).orElseThrow().bucket())
                .contains(Bucket.MISSION_TYPE_NOT_IN_FORCE);
    }

    @Test
    void aStepEnhancerOfAStepThatAnotherPackOverridesWithoutTheMissionTypeIsStillBoundToIt() {
        pack("org", PackLevel.ORG, List.of(), List.of("mission_type:M", "action:M/s"));
        pack(
                "tools",
                PackLevel.ORG,
                List.of("org"),
                List.of("step_enhancement:x"),
                edge("step_enhancement:x", RelationType.ENHANCES, "org::action:M/s"));
        pack("project", PackLevel.PROJECT, List.of("org"), List.of("action:M/s"));

        assertThat(select("step_enhancement:x", "mission_type:M").stateOf(u("step_enhancement:x")))
                .hasValueSatisfying(state -> assertThat(state.state()).isEqualTo(State.ACTIVE));
        expect(select("step_enhancement:x"), "step_enhancement:x", State.INACTIVE, Reason.MISSION_TYPE_NOT_IN_FORCE);
    }

    // E6's unheld-target branch (squad A5): C37 now refuses the compose of a step enhancer whose sole step no pack
    // holds, so a composed closure never reaches the branch. The two cases that pinned it here are C37 refusal tests,
    // each beside the twin that composes; the branch itself stays pinned on the pure resolution (InForceResolutionTest,
    // aStepEnhancerWhoseKeptEnhancesEdgeEndsAtNoWinnerIsPrunedForItsTarget).

    @Test
    void aStepEnhancerOfAStepNoPackHoldsIsRefusedByTheComposeAsTargetMissing() {
        pack(
                "alpha",
                List.of("mission_type:M", "action:M/s", "step_enhancement:x"),
                edge("step_enhancement:x", RelationType.ENHANCES, "action:M/missing"));

        assertThatThrownBy(() -> select("step_enhancement:x", "mission_type:M"))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
                    assertThat(refused.getMessage()).contains("action:M/missing");
                });
    }

    @Test
    void theTwinAStepEnhancerOfTheHeldStepComposesAndIsInForce() {
        pack(
                "alpha",
                List.of("mission_type:M", "action:M/s", "step_enhancement:x"),
                edge("step_enhancement:x", RelationType.ENHANCES, "action:M/s"));

        expect(select("step_enhancement:x", "mission_type:M"), "step_enhancement:x", State.ACTIVE, Reason.SELECTED);
    }

    // beta is a project pack, so its mission type in the twin is a layer override of alpha's, no sibling contest.
    @Test
    void aStepEnhancerOfAnActionDeclaredWithoutItsMissionTypeIsRefusedByTheComposeAsTargetMissing() {
        pack("alpha", List.of("mission_type:M", "action:M/s"));
        pack(
                "beta",
                PackLevel.PROJECT,
                List.of("alpha"),
                List.of("action:M/other", "step_enhancement:x"),
                edge("step_enhancement:x", RelationType.ENHANCES, "action:M/other"));

        assertThatThrownBy(() -> select("step_enhancement:x", "mission_type:M"))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
                    assertThat(refused.getMessage()).contains("action:M/other");
                });
    }

    @Test
    void theTwinAStepEnhancerOfAnActionDeclaredWithItsMissionTypeComposes() {
        pack("alpha", List.of("mission_type:M", "action:M/s"));
        pack(
                "beta",
                PackLevel.PROJECT,
                List.of("alpha"),
                List.of("mission_type:M", "action:M/other", "step_enhancement:x"),
                edge("step_enhancement:x", RelationType.ENHANCES, "action:M/other"));

        expect(select("step_enhancement:x", "mission_type:M"), "step_enhancement:x", State.ACTIVE, Reason.SELECTED);
    }

    // ---- the inherited lock at read (C22, C25 as amended, OD-2 (A)): a closure-level refusal, as C21 is -----------

    private void anEnhancerOfPlanUnder(MutabilityLevel missionTypeLevel) {
        pack(
                "base",
                PackLevel.BUILT_IN,
                List.of(),
                MutabilityPolicySource.builder()
                        .entry(u("mission_type:M"), missionTypeLevel)
                        .build(),
                List.of("mission_type:M", "action:M/s"));
        pack(
                "enhancer",
                PackLevel.ORG,
                List.of("base"),
                List.of("step_enhancement:x"),
                edge("step_enhancement:x", RelationType.ENHANCES, "base::action:M/s"));
    }

    @Test
    void anEnhancementOfAStepWhoseMissionTypeIsLockedMakesTheMergeUnreadableWhateverIsSelected() {
        anEnhancerOfPlanUnder(MutabilityLevel.LOCKED);

        assertThatThrownBy(() -> select()).isInstanceOfSatisfying(CharterRefusal.class, refused -> {
            assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refused.getMessage())
                    .contains("step_enhancement:x")
                    .contains("mission_type:M")
                    .contains("locked");
            assertThat(refused.packs()).extracting(RefusalPack::packId).containsExactly("enhancer", "base");
        });
    }

    @Test
    void theTwinAnEnhancementOfAStepWhoseMissionTypeIsExtendableIsMerged() {
        anEnhancerOfPlanUnder(MutabilityLevel.EXTENDABLE);

        expect(select("step_enhancement:x", "mission_type:M"), "step_enhancement:x", State.ACTIVE, Reason.SELECTED);
    }

    @Test
    void aStepEnhancerWithTwoTargetsKeepsTheEnhancerRule() {
        pack(
                "alpha",
                List.of("mission_type:M", "action:M/s", "action:M/t", "step_enhancement:x"),
                edge("step_enhancement:x", RelationType.ENHANCES, "action:M/s"),
                edge("step_enhancement:x", RelationType.ENHANCES, "action:M/t"));

        InForceStates states = select("step_enhancement:x", "mission_type:M");

        expect(states, "step_enhancement:x", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE);
        assertThat(states.enhancedTargets().get(u("step_enhancement:x")))
                .containsExactly(u("action:M/s"), u("action:M/t"));
    }

    @Test
    void aStepEnhancerWithAnActionAndAnArtifactTargetKeepsTheEnhancerRule() {
        pack(
                "alpha",
                List.of("mission_type:M", "action:M/s", "tactic:T", "step_enhancement:x"),
                edge("step_enhancement:x", RelationType.ENHANCES, "action:M/s"),
                edge("step_enhancement:x", RelationType.ENHANCES, "tactic:T"));

        expect(
                select("step_enhancement:x", "mission_type:M"),
                "step_enhancement:x",
                State.INACTIVE,
                Reason.ENHANCED_TARGET_NOT_IN_FORCE);
        expect(
                select("step_enhancement:x", "mission_type:M", "tactic:T"),
                "step_enhancement:x",
                State.ACTIVE,
                Reason.SELECTED);
    }

    @Test
    void aStepEnhancerOfOneArtifactTargetKeepsTheEnhancerRule() {
        pack(
                "alpha",
                List.of("mission_type:M", "tactic:T", "step_enhancement:x"),
                edge("step_enhancement:x", RelationType.ENHANCES, "tactic:T"));

        expect(
                select("step_enhancement:x", "mission_type:M"),
                "step_enhancement:x",
                State.INACTIVE,
                Reason.ENHANCED_TARGET_NOT_IN_FORCE);
        expect(select("step_enhancement:x", "tactic:T"), "step_enhancement:x", State.ACTIVE, Reason.SELECTED);
    }

    @Test
    void anEnhancerOfAnotherKindWhoseTargetIsAnActionKeepsTheEnhancerRule() {
        pack(
                "alpha",
                List.of("mission_type:M", "action:M/s", "tactic:Y"),
                edge("tactic:Y", RelationType.ENHANCES, "action:M/s"));

        expect(select("tactic:Y", "mission_type:M"), "tactic:Y", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE);
    }

    // ---- the node predicate, built from the in-force states (decisions 01M702A71YF7WNDTG2AA89K8ZA,
    // 01M7037ZNKJ6JTRKGD7G695ZV1)

    /** One pack: mission type {@code M}, steps {@code a} and {@code b}, artifacts {@code T}, {@code U}, {@code D}. */
    private void aMissionTypeWithAnAction() {
        pack(
                "alpha",
                List.of("mission_type:M", "action:M/a", "action:M/b", "tactic:T", "tactic:U", "directive:D", "skill:E"),
                edge("action:M/a", RelationType.SCOPE, "tactic:T"),
                edge("action:M/a", RelationType.SCOPE, "action:M/b"),
                edge("tactic:T", RelationType.REQUIRES, "directive:D"),
                edge("tactic:T", RelationType.SUGGESTS, "tactic:U"),
                edge("skill:E", RelationType.ENHANCES, "tactic:T"));
    }

    private static List<String> names(Iterable<Urn> urns) {
        List<String> names = new ArrayList<>();
        urns.forEach(urn -> names.add(urn.toString()));
        return names;
    }

    @Test
    void theGraphHoldsTheArtifactsInForceAndTheHeldStepsOfAMissionTypeInForceAndNothingElse() {
        aMissionTypeWithAnAction();
        ClosureView view = view();

        GraphProjection graph =
                view.mergedGraph(view.inForce(InForceRequest.selecting(urns("mission_type:M", "tactic:T"))));

        assertThat(names(graph.nodes())).containsExactly("action:M/a", "action:M/b", "mission_type:M", "tactic:T");
        assertThat(graph.edges())
                .extracting(edge -> edge.source() + " " + edge.relation().token() + " " + edge.target())
                .containsExactly("action:M/a scope action:M/b", "action:M/a scope tactic:T");
    }

    @Test
    void theGraphHoldsNoStepWhileTheMissionTypeIsOutOfForce() {
        aMissionTypeWithAnAction();
        ClosureView view = view();

        GraphProjection graph = view.mergedGraph(view.inForce(InForceRequest.selecting(urns("tactic:T"))));

        assertThat(names(graph.nodes())).containsExactly("tactic:T");
        assertThat(graph.edges()).isEmpty();
    }

    @Test
    void aKindWithNoActivationStateIsAbsentFromTheGraphAndTheWalkEvenWhenALockPutsItInForceInsidePacks() {
        pack(
                "alpha",
                PackLevel.ORG,
                List.of(),
                MutabilityPolicySource.builder()
                        .entry(u("template:Tpl"), MutabilityLevel.LOCKED)
                        .build(),
                List.of("mission_type:M", "action:M/a", "template:Tpl", "tactic:T"),
                edge("action:M/a", RelationType.SCOPE, "template:Tpl"),
                edge("action:M/a", RelationType.SCOPE, "tactic:T"));
        ClosureView view = view();
        InForceStates states = view.inForce(InForceRequest.selecting(urns("mission_type:M", "tactic:T")));

        assertThat(states.stateOf(u("template:Tpl")))
                .as("inside packs the lock puts it in force")
                .hasValueSatisfying(state -> assertThat(state.active()).isTrue());
        assertThat(names(view.mergedGraph(states).nodes())).doesNotContain("template:Tpl");
        assertThat(view.mergedGraph(states).edges())
                .extracting(edge -> edge.target().toString())
                .doesNotContain("template:Tpl");
        assertThat(names(view.actionContext("M", "a", 0, states).entries().stream()
                        .map(ActionContextWalk.Entry::urn)
                        .toList()))
                .containsExactly("tactic:T");
    }

    @Test
    void theLayerOverridesFollowTheNodesOfTheGraph() {
        pack("org", PackLevel.ORG, List.of(), List.of("mission_type:M", "action:M/a", "tactic:T", "tactic:Out"));
        pack("project", PackLevel.PROJECT, List.of("org"), List.of("action:M/a", "tactic:T", "tactic:Out"));
        ClosureView view = view();

        GraphProjection graph =
                view.mergedGraph(view.inForce(InForceRequest.selecting(urns("mission_type:M", "tactic:T"))));

        assertThat(graph.layerOverrides())
                .extracting(LayerOverride::urn)
                .containsExactly(u("action:M/a"), u("tactic:T"));
    }

    @Test
    void theWalkStartsAtTheHeldStepOfAMissionTypeInForceAndListsOnlyArtifactsInForce() {
        aMissionTypeWithAnAction();
        ClosureView view = view();
        InForceStates states = view.inForce(InForceRequest.selecting(urns("mission_type:M", "tactic:T", "skill:E")));

        ActionContextWalk.Context context = view.actionContext("M", "a", 1, states);

        assertThat(context.missionTypeInForce()).isTrue();
        assertThat(context.entries())
                .as("action:M/b is a held step, never an entry; D is not selected and U is not in force")
                .extracting(entry -> entry.urn().toString())
                .containsExactly("tactic:T");
        assertThat(context.entries().getFirst().enhancedBy()).containsExactly(u("skill:E"));
    }

    @Test
    void aSuggestedArtifactInForceCarriesTheWhenAndTheReasonOfTheEdge() {
        pack(
                "alpha",
                List.of("mission_type:M", "action:M/a", "tactic:T", "tactic:U"),
                edge("action:M/a", RelationType.SCOPE, "tactic:T"),
                new Relationship(
                        u("tactic:T"),
                        u("tactic:U"),
                        RelationType.SUGGESTS,
                        Optional.of("the change is large"),
                        Optional.of("U checks it")));
        ClosureView view = view();
        InForceStates states = view.inForce(InForceRequest.selecting(urns("mission_type:M", "tactic:T", "tactic:U")));

        ActionContextWalk.Context context = view.actionContext("M", "a", 1, states);

        assertThat(context.entries())
                .filteredOn(entry -> entry.urn().equals(u("tactic:U")))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.when()).contains("the change is large");
                    assertThat(entry.reason()).contains("U checks it");
                });
    }

    @Test
    void aSuggestedArtifactNotSelectedIsASuggestionCandidateWithTheWhenAndTheReasonOfTheEdge() {
        pack(
                "alpha",
                List.of("mission_type:M", "action:M/a", "tactic:T", "tactic:S", "tactic:Other"),
                edge("action:M/a", RelationType.SCOPE, "tactic:T"),
                edge("tactic:T", RelationType.VOCABULARY, "tactic:Other"),
                new Relationship(
                        u("tactic:T"),
                        u("tactic:S"),
                        RelationType.SUGGESTS,
                        Optional.of("the change is large"),
                        Optional.of("S checks it")));
        ClosureView view = view();
        InForceStates states = view.inForce(InForceRequest.selecting(urns("mission_type:M", "tactic:T")));

        ActionContextWalk.Context context = view.actionContext("M", "a", 1, states);

        assertThat(states.stateOf(u("tactic:S")))
                .as("the real resolution does not put S in force")
                .hasValueSatisfying(state -> assertThat(state.active()).isFalse());
        assertThat(context.suggestionCandidates())
                .as("S only: the vocabulary link to an unselected artifact is no suggestion")
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.urn()).isEqualTo(u("tactic:S"));
                    assertThat(candidate.via()).isEqualTo(u("tactic:T"));
                    assertThat(candidate.hops()).isEqualTo(2);
                    assertThat(candidate.when()).contains("the change is large");
                    assertThat(candidate.reason()).contains("S checks it");
                });
        assertThat(context.entries())
                .extracting(entry -> entry.urn().toString())
                .containsExactly("tactic:T");
    }

    @Test
    void aMissionTypeNotInForceAnswersNoEntryAndTheNamesAreJudgedOnTheClosure() {
        aMissionTypeWithAnAction();
        ClosureView view = view();
        InForceStates states = view.inForce(InForceRequest.selecting(urns("tactic:T")));

        ActionContextWalk.Context context = view.actionContext("M", "a", 1, states);

        assertThat(context.missionTypeInForce()).isFalse();
        assertThat(context.entries()).isEmpty();
        assertThatThrownBy(() -> view.actionContext("Nope", "a", 1, states))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND));
        assertThatThrownBy(() -> view.actionContext("M", "nope", 1, states))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ACTION_NOT_FOUND));
    }

    @Test
    void theInForceStatesTheGraphAndEveryWalkAnswerFromTheOneVerifiedRead() {
        // NFR-007: building the view reads the packs; the resolution, the graph, the walks and StepHolding read
        // nothing.
        aMissionTypeWithAnAction();
        ClosureView view = view();
        List<String> readOnce = List.copyOf(factsRead);
        assertThat(readOnce).isNotEmpty();

        InForceStates states = view.inForce(InForceRequest.selecting(urns("mission_type:M", "tactic:T")));
        view.mergedGraph(states);
        view.actionContext("M", "a", 0, states);
        view.actionContext("M", "a", 1, states);
        view.merged().edges().holdsStep(u("action:M/a"));

        assertThat(factsRead).as("no pack was read again").isEqualTo(readOnce);
    }
}
