package ai.speckitty.charter.activations.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.activations.application.PacksSession;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.CascadeRules;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.DocumentRules;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ClosureQueries;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.OfferingReadService.OfferingEntry;
import ai.speckitty.charter.packs.application.ReachRequest;
import ai.speckitty.charter.packs.application.ReachResult;
import ai.speckitty.charter.packs.application.UnknownRootException;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import ai.speckitty.charter.packs.domain.RequiredFloor;
import ai.speckitty.charter.packs.domain.ResolvedEdge;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The adapter over the packs slice: one {@link ClosureQueries#snapshot} per call, copied field by field, with each pack
 * of the floor and the locks named by its configured id; an unknown root named by the typed UnknownRootException alone (R4); and a refusal
 * of the compose passed on unchanged.
 */
class PacksAvailabilityTest {

    private static final String ALPHA = "01M4P01D5TWB5FKZGNNQG3943R";
    private static final String BETA = "01M4P01D5TWB5FKZGNNQG3943S";

    private final ClosureQueries closures = mock(ClosureQueries.class);
    private final PacksAvailability adapter = new PacksAvailability(closures);

    /** beta, then alpha, which depends on it; alpha locks D1 and its governing preset requires D2. */
    private static ClosureView view() {
        Urn d1 = new Urn(NodeKind.DIRECTIVE, "D1");
        Urn d2 = new Urn(NodeKind.DIRECTIVE, "D2");
        Map<ArtifactKind, Set<String>> held = new EnumMap<>(ArtifactKind.class);
        held.put(ArtifactKind.DIRECTIVE, Set.of("D1", "D2"));
        held.put(ArtifactKind.MISSION_TYPE, Set.of("software-dev"));
        Map<Urn, String> holderOf = new LinkedHashMap<>();
        holderOf.put(d1, BETA);
        holderOf.put(d2, BETA);
        holderOf.put(Urn.missionType("software-dev"), BETA);
        Preset governing = new Preset(
                "governing",
                "D2 is required.",
                List.of(ArtifactKind.DIRECTIVE),
                Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("D2"), PresetItem.of("D1"))),
                List.of(PresetItem.of("software-dev")));
        return new ClosureView(
                List.of(
                        new OfferingEntry(
                                new PackIdentifier("beta"), BETA, PackLevel.BUILT_IN, "beta-pack", "1.0.0", List.of()),
                        new OfferingEntry(
                                new PackIdentifier("alpha"),
                                ALPHA,
                                PackLevel.ORG,
                                "alpha-pack",
                                "2.0.0",
                                List.of(new ResolvedEdge(ALPHA, BETA, ">=1.0", "1.0.0")))),
                held,
                holderOf,
                Set.of("software-dev"),
                new RequiredFloor(Map.of(d2, List.of(ALPHA))),
                new PerPackPolicy(Map.of(
                        ALPHA,
                                MutabilityPolicySource.builder()
                                        .entry(d1, MutabilityLevel.LOCKED)
                                        .build(),
                        BETA,
                                MutabilityPolicySource.builder()
                                        .entry(d2, MutabilityLevel.EXTENDABLE)
                                        .build())),
                List.of(BETA, ALPHA),
                List.of(new ClosureView.HeldPreset(governing, ALPHA)),
                Map.of(ALPHA, BETA),
                "sha256:fingerprint");
    }

    @Test
    void theViewIsCopiedFieldByField() {
        when(closures.snapshot(Optional.of(List.of(new PackIdentifier("alpha")))))
                .thenReturn(view());

        ClosureSnapshot snapshot = adapter.snapshot(Optional.of(List.of("alpha")));

        assertThat(snapshot.resolved()).isTrue();
        assertThat(snapshot.packs())
                .extracting(ClosureSnapshot.PackEntry::packId)
                .containsExactly("beta", "alpha");
        ClosureSnapshot.PackEntry alpha = snapshot.pack("alpha").orElseThrow();
        assertThat(alpha.descriptorPackId()).isEqualTo(ALPHA);
        assertThat(alpha.level()).isEqualTo("org");
        assertThat(alpha.name()).isEqualTo("alpha-pack");
        assertThat(alpha.version()).isEqualTo("2.0.0");
        assertThat(alpha.dependsOn()).containsExactly(new ClosureSnapshot.Dependency(BETA, ">=1.0", "1.0.0"));
        assertThat(snapshot.pack("beta").orElseThrow().level()).isEqualTo("built-in");
        assertThat(alpha.parentPackId())
                .as("alpha's parent_pack, named by the id it is configured under")
                .contains("beta");
        assertThat(snapshot.pack("beta").orElseThrow().parentPackId()).isEmpty();
        assertThat(snapshot.extendsChain("alpha", "beta")).contains(List.of("alpha"));
        assertThat(snapshot.held(ArtifactKind.DIRECTIVE)).containsExactlyInAnyOrder("D1", "D2");
        assertThat(snapshot.missionTypes()).containsExactly("software-dev");
        assertThat(snapshot.fingerprint()).isEqualTo("sha256:fingerprint");
    }

    @Test
    void theFloorAndTheLocksNameTheConfiguredPackAndOnlyLockedLevelsCount() {
        when(closures.snapshot(Optional.empty())).thenReturn(view());

        ClosureSnapshot snapshot = adapter.snapshot(Optional.empty());

        assertThat(snapshot.requiredBy()).containsExactly(Map.entry("directive:D2", List.of("alpha")));
        assertThat(snapshot.lockedBy()).containsExactly(Map.entry("directive:D1", "alpha"));
    }

    @Test
    void thePresetsAreCopiedWithTheirMustHaveItems() {
        when(closures.snapshot(Optional.empty())).thenReturn(view());

        ClosureSnapshot.PresetView preset =
                adapter.snapshot(Optional.empty()).presets().get("governing");

        assertThat(preset.holdingPack()).isEqualTo(ALPHA);
        assertThat(preset.activatedKinds()).containsExactly(ArtifactKind.DIRECTIVE);
        assertThat(preset.members().get(ArtifactKind.DIRECTIVE)).containsExactlyInAnyOrder("D1", "D2");
        assertThat(preset.mustHave().get(ArtifactKind.DIRECTIVE)).containsExactly("D2");
        assertThat(preset.missionTypes()).containsExactly("software-dev");
        assertThat(preset.mustHaveMissionTypes()).isEmpty();
        assertThat(preset.governing()).isTrue();
    }

    @Test
    void anUnknownRootIsNamedAndTheWriteIsRefusedAtItsPosition() {
        when(closures.snapshot(Optional.of(List.of(new PackIdentifier("alpha"), new PackIdentifier("ghost")))))
                .thenThrow(new UnknownRootException(List.of("ghost")));

        ClosureSnapshot snapshot = adapter.snapshot(Optional.of(List.of("alpha", "ghost")));

        assertThat(snapshot.resolved()).isFalse();
        assertThat(snapshot.unknownPacks()).containsExactly("ghost");
        ActivationDocument.Built built = ActivationDocument.builder()
                .packs(List.of(PackRef.of("alpha"), PackRef.of("ghost")))
                .build();
        assertThat(DocumentRules.check(built, snapshot)).hasValueSatisfying(refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
            assertThat(refusal.violations()).singleElement().satisfies(violation -> {
                assertThat(violation.pointer()).isEqualTo("/packs/1/packId");
                assertThat(violation.code()).isEqualTo("unknown_pack");
            });
        });
    }

    @Test
    void anyOtherArgumentErrorOfTheComposeIsNoUnknownPackAndPropagatesUnchanged() {
        IllegalArgumentException fault = new IllegalArgumentException("a fault of the compose");
        when(closures.snapshot(Optional.of(List.of(new PackIdentifier("alpha")))))
                .thenThrow(fault);

        assertThatThrownBy(() -> adapter.snapshot(Optional.of(List.of("alpha"))))
                .isSameAs(fault);
    }

    @Test
    void aRefusalOfTheComposePassesUnchanged() {
        CharterRefusal missing = new CharterRefusal(RefusalCode.PACK_REFERENCE_PACK_MISSING, "alpha names beta-pack");
        when(closures.snapshot(Optional.empty())).thenThrow(missing);

        assertThatThrownBy(() -> adapter.snapshot(Optional.empty())).isSameAs(missing);
    }

    @Test
    void aSessionReadsTheClosureOnceForItsSnapshotAndEveryWalk() {
        when(closures.snapshot(Optional.of(List.of(new PackIdentifier("alpha")))))
                .thenReturn(view());
        ArtifactRef d1 = new ArtifactRef(ArtifactKind.DIRECTIVE, "D1");
        ReachPlan plan = new ReachPlan(Set.of(d1), CascadeRules.CASCADE_RELATIONS, Set.of(), true);

        PacksSession session = adapter.session(Optional.of(List.of("alpha")));
        session.reach(plan);
        session.reach(plan);
        session.enhancesPull(Set.of(d1), Set.of(ArtifactKind.DIRECTIVE));
        session.enhancersOf(Set.of(d1));

        assertThat(session.snapshot().held(ArtifactKind.DIRECTIVE)).containsExactlyInAnyOrder("D1", "D2");
        verify(closures, times(1)).snapshot(any());
    }

    @Test
    void anUnresolvedSessionWalksNothing() {
        when(closures.snapshot(Optional.of(List.of(new PackIdentifier("ghost")))))
                .thenThrow(new UnknownRootException(List.of("ghost")));
        ArtifactRef d1 = new ArtifactRef(ArtifactKind.DIRECTIVE, "D1");

        PacksSession session = adapter.session(Optional.of(List.of("ghost")));

        assertThat(session.snapshot().unknownPacks()).containsExactly("ghost");
        assertThat(session.reach(new ReachPlan(Set.of(d1), CascadeRules.CASCADE_RELATIONS, Set.of(), true)))
                .isEqualTo(Reach.empty());
        assertThat(session.enhancesPull(Set.of(d1), Set.of(ArtifactKind.DIRECTIVE)))
                .isEmpty();
        assertThat(session.enhancersOf(Set.of(d1))).isEmpty();
    }

    @Test
    void aPlanIsAskedAsAReachRequestWithTheLineageWhenItFollowsIt() {
        ArtifactRef p = new ArtifactRef(ArtifactKind.AGENT_PROFILE, "p");
        ArtifactRef q = new ArtifactRef(ArtifactKind.AGENT_PROFILE, "q");

        ReachRequest lineage =
                ClosureViewSession.requestOf(new ReachPlan(Set.of(p), CascadeRules.CASCADE_RELATIONS, Set.of(q), true));
        ReachRequest plain =
                ClosureViewSession.requestOf(new ReachPlan(Set.of(p), CascadeRules.CASCADE_RELATIONS, Set.of(), false));

        assertThat(lineage.starts()).containsExactly(new Urn(NodeKind.AGENT_PROFILE, "p"));
        assertThat(lineage.relations()).isEqualTo(CascadeRules.CASCADE_RELATIONS);
        assertThat(lineage.stopAt().test(new Urn(NodeKind.AGENT_PROFILE, "q"))).isTrue();
        assertThat(lineage.stopAt().test(new Urn(NodeKind.AGENT_PROFILE, "p"))).isFalse();
        assertThat(lineage.lineage()).contains(ReachRequest.Lineage.specializesFrom());
        assertThat(plain.lineage()).isEmpty();
    }

    @Test
    void aWalksAnswerIsCopiedWithoutTheNodesThatAreNoArtifact() {
        Urn p = new Urn(NodeKind.AGENT_PROFILE, "p");
        Urn q = new Urn(NodeKind.AGENT_PROFILE, "q");
        Urn r = new Urn(NodeKind.DIRECTIVE, "R");
        Urn action = Urn.action("software-dev", "implement");
        ArtifactRef pRef = new ArtifactRef(ArtifactKind.AGENT_PROFILE, "p");
        ArtifactRef qRef = new ArtifactRef(ArtifactKind.AGENT_PROFILE, "q");
        ArtifactRef rRef = new ArtifactRef(ArtifactKind.DIRECTIVE, "R");

        Reach reach = ClosureViewSession.reachOf(new ReachResult(
                Map.of(
                        p, List.of(),
                        r,
                                List.of(
                                        new ReachResult.Via(q, RelationType.REQUIRES),
                                        new ReachResult.Via(action, RelationType.SCOPE)),
                        action, List.of()),
                Map.of(q, List.of(new ReachResult.Via(p, RelationType.SPECIALIZES_FROM))),
                Set.of(r),
                Set.of(q),
                Map.of()));

        assertThat(reach.reached()).containsOnlyKeys(pRef, rRef);
        assertThat(reach.reached().get(rRef)).containsExactly(new Reach.Via(qRef, RelationType.REQUIRES));
        assertThat(reach.stopped().get(qRef)).containsExactly(new Reach.Via(pRef, RelationType.SPECIALIZES_FROM));
        assertThat(reach.behindStopped()).containsExactly(rRef);
        assertThat(reach.ancestorsPassed()).containsExactly(qRef);
    }
}
