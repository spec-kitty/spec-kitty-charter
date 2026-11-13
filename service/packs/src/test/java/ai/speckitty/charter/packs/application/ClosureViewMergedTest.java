package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.InForceRequest;
import ai.speckitty.charter.packs.domain.InForceStates;
import ai.speckitty.charter.packs.domain.LayerOverride;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.RequiredFloor;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link ClosureView#merged()} over a real compose and a real {@link ClosureQueries} read (FR-009 to FR-012, FR-023,
 * NFR-007): the layer merge is built lazily from the verified read the view holds, reads nothing more, and leaves
 * {@code holderOf} and the offering as they were.
 */
class ClosureViewMergedTest {

    private static final Urn D = new Urn(NodeKind.DIRECTIVE, "D");
    private static final Urn X = new Urn(NodeKind.DIRECTIVE, "X");

    private final List<ConfiguredPack> configured = new ArrayList<>();
    private final Map<String, PackDescriptor> descriptors = new HashMap<>();
    private final Map<String, PackCompositionFacts> facts = new HashMap<>();
    private final List<String> factsRead = new ArrayList<>();

    private void pack(String name, PackLevel level, List<String> dependsOn, PackCompositionFacts held) {
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
        facts.put(name, held);
    }

    private static PackCompositionFacts holding(
            MutabilityPolicySource policy,
            Map<String, List<String>> languages,
            List<Relationship> edges,
            String... held) {
        return new PackCompositionFacts(List.of(), List.of(held), List.of(), policy, List.of(), edges, languages);
    }

    private static PackCompositionFacts bound(Map<String, String> bindings, String... held) {
        return new PackCompositionFacts(
                List.of(),
                List.of(held),
                List.of(),
                MutabilityPolicySource.empty(),
                List.of(),
                List.of(),
                Map.of(),
                bindings);
    }

    private static PackCompositionFacts holding(String... held) {
        return holding(MutabilityPolicySource.empty(), Map.of(), List.of(), held);
    }

    private static PackCompositionFacts locking(Urn urn, MutabilityLevel level, String... held) {
        return holding(MutabilityPolicySource.builder().entry(urn, level).build(), Map.of(), List.of(), held);
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

    private static MergedClosure.Winner winner(ClosureView.MergedView merged, Urn urn) {
        assertThat(merged.closure().winnerOf(urn))
                .as("the merge names a winner of " + urn)
                .isPresent();
        return merged.closure().winnerOf(urn).orElseThrow();
    }

    private ClosureView snapshot() {
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

    /** built-in, org and project each hold directive D. */
    private void threeLevels() {
        pack("builtin", PackLevel.BUILT_IN, List.of(), holding("directive:D"));
        pack("org", PackLevel.ORG, List.of("builtin"), holding("directive:D"));
        pack("project", PackLevel.PROJECT, List.of("org"), holding("directive:D"));
    }

    @Test
    void theMergeNamesTheWinnerAndRecordsEveryReplacedVersion() {
        threeLevels();

        ClosureView.MergedView merged = snapshot().merged();

        assertThat(winner(merged, D).packId()).isEqualTo(new PackIdentifier("project"));
        assertThat(winner(merged, D).descriptorPackId()).isEqualTo("id-project");
        assertThat(merged.closure().layerOverrides())
                .extracting(LayerOverride::loser)
                .extracting(LayerOverride.Side::level)
                .containsExactly(PackLevel.BUILT_IN, PackLevel.ORG);
    }

    @Test
    void holderOfStaysTheFirstHolderInClosureOrderWhileTheMergeAnswersTheWinner() {
        threeLevels();

        ClosureView view = snapshot();

        assertThat(view.holderOf()).containsEntry(D, "id-builtin");
        assertThat(winner(view.merged(), D).descriptorPackId()).isEqualTo("id-project");
    }

    @Test
    void theMergeReadsNothingBeyondTheVerifiedReadAndIsBuiltOnce() {
        threeLevels();
        ClosureView view = snapshot();
        List<String> read = List.copyOf(factsRead);

        ClosureView.MergedView first = view.merged();
        ClosureView.MergedView second = view.merged();

        assertThat(factsRead).as("NFR-007: the merge reads no pack").isEqualTo(read);
        assertThat(second).isSameAs(first);
    }

    @Test
    void theMergeIsBuiltOnlyWhenAskedForSoARefusedOverrideDoesNotRefuseTheRead() {
        pack("org", PackLevel.ORG, List.of(), locking(D, MutabilityLevel.LOCKED, "directive:D"));
        pack("project", PackLevel.PROJECT, List.of("org"), holding("directive:D"));

        ClosureView view = snapshot();

        assertThat(view.holderOf()).containsKey(D);
        assertThatThrownBy(view::merged).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refusal.getMessage()).contains("directive:D", "locked");
        });
    }

    @Test
    void anOverrideOfAnArtifactAnUnrelatedPackLocksIsAllowedByTheLockScope() {
        pack("builtin", PackLevel.BUILT_IN, List.of(), holding("directive:X"));
        pack("guard", PackLevel.ORG, List.of(), locking(X, MutabilityLevel.LOCKED, "directive:G"));
        pack("project", PackLevel.PROJECT, List.of("builtin"), holding("directive:X"));

        ClosureView view = snapshot();

        assertThat(winner(view.merged(), X).packId()).isEqualTo(new PackIdentifier("project"));
        assertThat(view.merged().closure().layerOverrides()).hasSize(1);
    }

    @Test
    void anOverrideOfAnArtifactAPackThatBuildsOnTheHolderLocksIsRefused() {
        pack("builtin", PackLevel.BUILT_IN, List.of(), holding("directive:X"));
        pack("guard", PackLevel.ORG, List.of("builtin"), locking(X, MutabilityLevel.LOCKED, "directive:G"));
        pack("project", PackLevel.PROJECT, List.of("builtin", "guard"), holding("directive:X"));

        ClosureView view = snapshot();

        assertThatThrownBy(view::merged)
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @Test
    void theMergedLanguagesAreTheWinnersOwn() {
        pack(
                "org",
                PackLevel.ORG,
                List.of(),
                holding(
                        MutabilityPolicySource.empty(),
                        Map.of("directive:D", List.of("java")),
                        List.of(),
                        "directive:D"));
        pack(
                "project",
                PackLevel.PROJECT,
                List.of("org"),
                holding(
                        MutabilityPolicySource.empty(),
                        Map.of("directive:D", List.of("rust")),
                        List.of(),
                        "directive:D"));

        assertThat(snapshot().merged().closure().languages()).containsEntry(D, Set.of("rust"));
    }

    @Test
    void aReachOverTheMergedEdgesNeverFollowsAnEdgeOfAnOverriddenArtifact() {
        pack(
                "org",
                PackLevel.ORG,
                List.of(),
                holding(
                        MutabilityPolicySource.empty(),
                        Map.of(),
                        List.of(edge("directive:D", RelationType.REQUIRES, "tactic:T1")),
                        "directive:D",
                        "tactic:T1"));
        pack(
                "project",
                PackLevel.PROJECT,
                List.of("org"),
                holding(
                        MutabilityPolicySource.empty(),
                        Map.of(),
                        List.of(edge("directive:D", RelationType.REQUIRES, "tactic:T2")),
                        "directive:D",
                        "tactic:T2"));
        ClosureView view = snapshot();
        ReachRequest request = new ReachRequest(
                Set.of(D), EnumSet.of(RelationType.REQUIRES), urn -> false, Optional.empty(), OptionalInt.empty());

        assertThat(Reachability.reach(view.edges(), request).reached().keySet())
                .as("the unmerged index keeps both versions' edges")
                .contains(new Urn(NodeKind.TACTIC, "T1"), new Urn(NodeKind.TACTIC, "T2"));
        assertThat(view.reach(request))
                .as("the cascade walks the merged edges (FR-038)")
                .isEqualTo(view.merged().reach(request));
        assertThat(view.merged().reach(request).reached().keySet())
                .as("the merged one follows the version in force only")
                .contains(new Urn(NodeKind.TACTIC, "T2"))
                .doesNotContain(new Urn(NodeKind.TACTIC, "T1"));
    }

    @Test
    void aViewBuiltWithoutThePacksMergesNothing() {
        ClosureView view = new ClosureView(
                List.of(),
                Map.of(),
                Map.of(),
                new RequiredFloor(Map.of()),
                new PerPackPolicy(Map.of()),
                List.of(),
                List.of(),
                Map.of(),
                "sha256:" + "0".repeat(64));

        assertThat(view.merged().closure().winners()).isEmpty();
    }

    private static ReachRequest cascadeFrom(Urn start) {
        return new ReachRequest(Set.of(start), Set.of(RelationType.REQUIRES), urn -> false, Optional.empty());
    }

    @Test
    void theCascadeNeverFollowsAnEdgeOfAnOverriddenArtifact() {
        pack(
                "org",
                PackLevel.ORG,
                List.of(),
                holding(
                        MutabilityPolicySource.empty(),
                        Map.of(),
                        List.of(edge("directive:D", RelationType.REQUIRES, "directive:X")),
                        "directive:D",
                        "directive:X"));
        pack("project", PackLevel.PROJECT, List.of("org"), holding("directive:D"));

        ClosureView view = snapshot();

        assertThat(view.reach(cascadeFrom(D)).reached()).containsOnlyKeys(D);
    }

    @Test
    void theCascadeWalksTheSameEdgesWhereNoLayerOverrideExists() {
        pack(
                "alpha",
                PackLevel.ORG,
                List.of(),
                holding(
                        MutabilityPolicySource.empty(),
                        Map.of(),
                        List.of(edge("directive:D", RelationType.REQUIRES, "directive:X")),
                        "directive:D",
                        "directive:X"));

        ClosureView view = snapshot();

        assertThat(view.reach(cascadeFrom(D)).reached()).containsOnlyKeys(D, X);
        assertThat(view.reach(cascadeFrom(D))).isEqualTo(Reachability.reach(view.edges(), cascadeFrom(D)));
    }

    private static InForceRequest selecting(Urn... urns) {
        return InForceRequest.selecting(Set.of(urns));
    }

    @Test
    void whatIsInForceAppliesTheOverridesOfTheWinnersEdges() {
        pack(
                "alpha",
                PackLevel.ORG,
                List.of(),
                holding(
                        MutabilityPolicySource.empty(),
                        Map.of(),
                        List.of(edge("directive:D", RelationType.OVERRIDES, "directive:X")),
                        "directive:D",
                        "directive:X"));

        InForceStates states = snapshot().inForce(selecting(D, X));

        assertThat(states.stateOf(D))
                .hasValueSatisfying(state -> assertThat(state.active()).isTrue());
        assertThat(states.stateOf(X)).hasValueSatisfying(state -> {
            assertThat(state.active()).isFalse();
            assertThat(state.reason()).isEqualTo(InForceStates.Reason.OVERRIDDEN);
        });
    }

    @Test
    void whatIsInForceIgnoresTheOverridesAnOverriddenArtifactDeclared() {
        pack(
                "builtin",
                PackLevel.BUILT_IN,
                List.of(),
                holding(
                        MutabilityPolicySource.empty(),
                        Map.of(),
                        List.of(edge("directive:D", RelationType.OVERRIDES, "directive:X")),
                        "directive:D",
                        "directive:X"));
        pack("project", PackLevel.PROJECT, List.of("builtin"), holding("directive:D"));

        InForceStates states = snapshot().inForce(selecting(D, X));

        assertThat(states.stateOf(X))
                .hasValueSatisfying(state -> assertThat(state.active()).isTrue());
    }

    @Test
    void whatIsInForceReportsAnOverrideOfALockedTargetAsNotApplied() {
        pack("alpha", PackLevel.ORG, List.of(), locking(X, MutabilityLevel.LOCKED, "directive:D", "directive:X"));
        pack(
                "beta",
                PackLevel.ORG,
                List.of("alpha"),
                holding(
                        MutabilityPolicySource.empty(),
                        Map.of(),
                        List.of(edge("directive:E", RelationType.OVERRIDES, "alpha::directive:X")),
                        "directive:E"));

        InForceStates states = snapshot().inForce(selecting(new Urn(NodeKind.DIRECTIVE, "E")));

        assertThat(states.overrideNotApplied())
                .containsExactly(new InForceStates.OverrideNotApplied(X, List.of(new Urn(NodeKind.DIRECTIVE, "E"))));
    }

    @Test
    void whatIsInForcePrunesAnEnhancerWhoseTargetIsNotInForce() {
        pack(
                "alpha",
                PackLevel.ORG,
                List.of(),
                holding(
                        MutabilityPolicySource.empty(),
                        Map.of(),
                        List.of(edge("directive:D", RelationType.ENHANCES, "directive:X")),
                        "directive:D",
                        "directive:X"));

        InForceStates states = snapshot().inForce(selecting(D));

        assertThat(states.stateOf(D)).hasValueSatisfying(state -> {
            assertThat(state.active()).isFalse();
            assertThat(state.reason()).isEqualTo(InForceStates.Reason.ENHANCED_TARGET_NOT_IN_FORCE);
        });
    }

    // ---- the binding to a mission type (A7 Q1, rule E6)

    private static final Urn M = new Urn(NodeKind.MISSION_TYPE, "M");
    private static final Urn CM = new Urn(NodeKind.MISSION_STEP_CONTRACT, "cm");
    private static final Urn CN = new Urn(NodeKind.MISSION_STEP_CONTRACT, "cn");

    @Test
    void aStepContractBoundToAMissionTypeIsInForceOnlyWhileTheMissionTypeIs() {
        pack(
                "alpha",
                PackLevel.ORG,
                List.of(),
                bound(
                        Map.of(
                                "mission_step_contract:cm", "mission_type:M",
                                "mission_step_contract:cn", "mission_type:N"),
                        "mission_type:M",
                        "mission_type:N",
                        "mission_step_contract:cm",
                        "mission_step_contract:cn"));

        InForceStates states = snapshot().inForce(selecting(M, CM, CN));

        assertThat(states.states().get(CM).state()).isEqualTo(InForceStates.State.ACTIVE);
        assertThat(states.states().get(CN).state()).isEqualTo(InForceStates.State.INACTIVE);
        assertThat(states.states().get(CN).reason()).isEqualTo(InForceStates.Reason.MISSION_TYPE_NOT_IN_FORCE);
        assertThat(states.states().get(CN).bucket()).contains(InForceStates.Bucket.MISSION_TYPE_NOT_IN_FORCE);
    }

    @Test
    void aStepContractBoundToAMissionTypeNoPackHoldsStaysOut() {
        pack(
                "alpha",
                PackLevel.ORG,
                List.of(),
                bound(Map.of("mission_step_contract:cm", "mission_type:M"), "mission_step_contract:cm"));

        InForceStates states = snapshot().inForce(selecting(CM));

        assertThat(states.states().get(CM).reason()).isEqualTo(InForceStates.Reason.MISSION_TYPE_NOT_IN_FORCE);
    }

    @Test
    void theBindingIsTheOneOfThePackThatHoldsTheVersionInForce() {
        pack(
                "beta",
                PackLevel.BUILT_IN,
                List.of(),
                bound(
                        Map.of("mission_step_contract:cm", "mission_type:N"),
                        "mission_type:N",
                        "mission_step_contract:cm"));
        pack("alpha", PackLevel.ORG, List.of("beta"), holding("mission_step_contract:cm"));

        InForceStates states = snapshot().inForce(selecting(CM));

        assertThat(states.states().get(CM).state())
                .as("the org version replaces the built-in one whole, and binds nothing")
                .isEqualTo(InForceStates.State.ACTIVE);
    }
}
