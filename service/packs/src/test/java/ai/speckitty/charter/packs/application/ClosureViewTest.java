package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.InForceRequest;
import ai.speckitty.charter.packs.domain.InForceStates;
import ai.speckitty.charter.packs.domain.InForceStates.Bucket;
import ai.speckitty.charter.packs.domain.InForceStates.Reason;
import ai.speckitty.charter.packs.domain.InForceStates.State;
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
                new PackCompositionFacts(
                        List.of(),
                        held,
                        List.of(),
                        MutabilityPolicySource.empty(),
                        List.of(),
                        List.of(edges),
                        Map.of()));
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
        OfferingReadService offering =
                new OfferingReadService(catalog, repository, pack -> facts.get(pack.value()), read);
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

    @Test
    void aStepEnhancerOfAStepNoPackHoldsKeepsItsEdgeAndIsPrunedForItsTarget() {
        pack(
                "alpha",
                List.of("mission_type:M", "action:M/s", "step_enhancement:x"),
                edge("step_enhancement:x", RelationType.ENHANCES, "action:M/missing"));

        InForceStates states = select("step_enhancement:x", "mission_type:M");

        expect(states, "step_enhancement:x", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE);
        assertThat(states.enhancedTargets()).containsEntry(u("step_enhancement:x"), List.of(u("action:M/missing")));
    }

    @Test
    void aStepEnhancerOfAnActionDeclaredWithoutItsMissionTypeIsPrunedForItsTarget() {
        pack("alpha", List.of("mission_type:M", "action:M/s"));
        pack(
                "beta",
                PackLevel.ORG,
                List.of("alpha"),
                List.of("action:M/other", "step_enhancement:x"),
                edge("step_enhancement:x", RelationType.ENHANCES, "action:M/other"));

        InForceStates states = select("step_enhancement:x", "mission_type:M");

        expect(states, "step_enhancement:x", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE);
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
}
