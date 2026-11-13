package ai.speckitty.charter.activations.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.application.PacksSession;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.CascadeRules;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.DocumentRules;
import ai.speckitty.charter.activations.domain.EffectiveSet;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.InForceFacts;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ClosureQueries;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.EdgeIndex;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.application.OfferingReadService.OfferingEntry;
import ai.speckitty.charter.packs.application.ReachRequest;
import ai.speckitty.charter.packs.application.ReachResult;
import ai.speckitty.charter.packs.application.UnknownRootException;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.InForceStates;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import ai.speckitty.charter.packs.domain.RequiredFloor;
import ai.speckitty.charter.packs.domain.ResolvedEdge;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The adapter over the packs slice: one {@code ClosureQueries.snapshot} per call, copied field by field, with each pack
 * of the floor and the locks named by its configured id; an unknown root named by the typed UnknownRootException alone (R4); and a refusal
 * of the compose passed on unchanged.
 */
class PacksAvailabilityTest {

    private static final String ALPHA = "01M4P01D5TWB5FKZGNNQG3943R";
    private static final String BETA = "01M4P01D5TWB5FKZGNNQG3943S";

    private final FakeClosures closures = new FakeClosures();
    private final PacksAvailability adapter = new PacksAvailability(closures);

    /**
     * A closure read that answers one set of roots with what it was given, and refuses every other set: a call with
     * roots the test did not expect fails the test instead of answering.
     */
    private static final class FakeClosures implements PacksAvailability.Closures {
        private Optional<List<PackIdentifier>> expected = Optional.empty();
        private Supplier<ClosureView> answer = () -> {
            throw new AssertionError("no answer was given");
        };
        private int calls;

        void answers(Optional<List<PackIdentifier>> roots, ClosureView view) {
            expected = roots;
            answer = () -> view;
        }

        void throwsOn(Optional<List<PackIdentifier>> roots, RuntimeException fault) {
            expected = roots;
            answer = () -> {
                throw fault;
            };
        }

        @Override
        public ClosureView snapshot(Optional<List<PackIdentifier>> roots) {
            calls++;
            assertThat(roots).as("the roots the adapter asks for").isEqualTo(expected);
            return answer.get();
        }
    }

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
                Map.of(
                        ArtifactKind.DIRECTIVE,
                        List.of(PresetItem.mustHave("D2"), PresetItem.of("D1")),
                        ArtifactKind.MISSION_TYPE,
                        List.of(PresetItem.of("software-dev"))));
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
                "sha256:fingerprint",
                EdgeIndex.empty(),
                mergeInputs(governing));
    }

    /** What the layer merge reads of {@link #view()}: beta holds D1 and D2, alpha holds the governing preset. */
    private static ClosureView.MergeInputs mergeInputs(Preset governing) {
        List<String> held = List.of("directive:D1", "directive:D2");
        Merging merging = new Merging(Map.of("beta", BETA, "alpha", ALPHA))
                .pack("beta", PackLevel.BUILT_IN, List.of(), held, Map.of(), List.of(), MutabilityPolicySource.empty())
                .pack(
                        "alpha",
                        PackLevel.ORG,
                        List.of("beta"),
                        List.of(),
                        Map.of(),
                        List.of(governing),
                        MutabilityPolicySource.empty());
        return merging.mergeInputs();
    }

    @Test
    void theViewIsCopiedFieldByField() {
        closures.answers(Optional.of(List.of(new PackIdentifier("alpha"))), view());

        ClosureSnapshot snapshot = adapter.snapshot(Optional.of(List.of("alpha")));

        assertThat(closures.calls).as("one verified read per snapshot").isEqualTo(1);
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
        assertThat(snapshot.held(ArtifactKind.MISSION_TYPE)).containsExactly("software-dev");
        assertThat(snapshot.fingerprint()).isEqualTo("sha256:fingerprint");
    }

    @Test
    void theFloorAndTheLocksNameTheConfiguredPackAndOnlyLockedLevelsCount() {
        closures.answers(Optional.empty(), view());

        ClosureSnapshot snapshot = adapter.snapshot(Optional.empty());

        assertThat(snapshot.requiredBy()).containsExactly(Map.entry("directive:D2", List.of("alpha")));
        assertThat(snapshot.lockedBy()).containsExactly(Map.entry("directive:D1", "alpha"));
    }

    /** A1: the built-in copy of the governing preset requires D1, the org copy of the same id requires D2. */
    private static Merging unionOfTwoCopies() {
        Preset core =
                new Preset("governing", "Core.", Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("D1"))));
        Preset house =
                new Preset("governing", "House.", Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("D2"))));
        return new Merging()
                .pack(
                        "core",
                        PackLevel.BUILT_IN,
                        List.of(),
                        List.of("directive:D1", "directive:D2"),
                        Map.of(),
                        List.of(core),
                        MutabilityPolicySource.empty())
                .pack(
                        "house",
                        PackLevel.ORG,
                        List.of("core"),
                        List.of(),
                        Map.of(),
                        List.of(house),
                        MutabilityPolicySource.empty());
    }

    private static ClosureView unionView() {
        return unionOfTwoCopies().viewHolding(Map.of(ArtifactKind.DIRECTIVE, Set.of("D1", "D2")));
    }

    @Test
    void theFloorIsTheUnionOfEveryGoverningCopyAndNamesTheLosingCopysPack() {
        closures.answers(Optional.empty(), unionView());

        ClosureSnapshot snapshot = adapter.snapshot(Optional.empty());

        assertThat(snapshot.requiredBy())
                .as("D1 stays required by the built-in copy that the org copy replaces, and D2 by the org copy")
                .containsOnly(Map.entry("directive:D1", List.of("core")), Map.entry("directive:D2", List.of("house")));
    }

    @Test
    void aDenyEntryOnAnItemOnlyTheLosingCopyRequiresIsRefusedNamingThatPack() {
        closures.answers(Optional.empty(), unionView());
        ClosureSnapshot snapshot = adapter.snapshot(Optional.empty());
        ActivationDocument.Built built = ActivationDocument.builder()
                .deactivated(ArtifactKind.DIRECTIVE, List.of("D1"))
                .build();

        assertThat(DocumentRules.check(built, snapshot, InForceFacts.NONE)).hasValueSatisfying(refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED);
            assertThat(refusal.requiredBy()).extracting(by -> by.packId()).containsExactly("core");
        });
    }

    @Test
    void aDenyEntryOnAMustHaveItemOfTheChildsPresetIsRefusedNamingTheChild() {
        closures.answers(Optional.empty(), view());
        ClosureSnapshot snapshot = adapter.snapshot(Optional.empty());
        ActivationDocument.Built built = ActivationDocument.builder()
                .deactivated(ArtifactKind.DIRECTIVE, List.of("D2"))
                .build();

        assertThat(DocumentRules.check(built, snapshot, InForceFacts.NONE)).hasValueSatisfying(refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED);
            assertThat(refusal.requiredBy()).extracting(by -> by.packId()).containsExactly("alpha");
        });
    }

    @Test
    void thePresetsAreCopiedWithTheirMustHaveItemsAndMissionTypesAmongTheMembers() {
        closures.answers(Optional.empty(), view());

        ClosureSnapshot.PresetView preset =
                adapter.snapshot(Optional.empty()).presets().get("governing");

        assertThat(preset.id()).isEqualTo("governing");
        assertThat(preset.members()).containsOnlyKeys(ArtifactKind.DIRECTIVE, ArtifactKind.MISSION_TYPE);
        assertThat(preset.members().get(ArtifactKind.DIRECTIVE)).containsExactlyInAnyOrder("D1", "D2");
        assertThat(preset.members().get(ArtifactKind.MISSION_TYPE)).containsExactly("software-dev");
    }

    @Test
    void anUnknownRootIsNamedAndTheWriteIsRefusedAtItsPosition() {
        closures.throwsOn(
                Optional.of(List.of(new PackIdentifier("alpha"), new PackIdentifier("ghost"))),
                new UnknownRootException(List.of("ghost")));

        ClosureSnapshot snapshot = adapter.snapshot(Optional.of(List.of("alpha", "ghost")));

        assertThat(snapshot.resolved()).isFalse();
        assertThat(snapshot.unknownPacks()).containsExactly("ghost");
        ActivationDocument.Built built = ActivationDocument.builder()
                .packs(List.of(PackRef.of("alpha"), PackRef.of("ghost")))
                .build();
        assertThat(DocumentRules.check(built, snapshot, InForceFacts.NONE)).hasValueSatisfying(refusal -> {
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
        closures.throwsOn(Optional.of(List.of(new PackIdentifier("alpha"))), fault);

        assertThatThrownBy(() -> adapter.snapshot(Optional.of(List.of("alpha"))))
                .isSameAs(fault);
    }

    @Test
    void aRefusalOfTheComposePassesUnchanged() {
        CharterRefusal missing = new CharterRefusal(RefusalCode.PACK_REFERENCE_PACK_MISSING, "alpha names beta-pack");
        closures.throwsOn(Optional.empty(), missing);

        assertThatThrownBy(() -> adapter.snapshot(Optional.empty())).isSameAs(missing);
    }

    @Test
    void aSessionReadsTheClosureOnceForItsSnapshotAndEveryWalk() {
        closures.answers(Optional.of(List.of(new PackIdentifier("alpha"))), view());
        ArtifactRef d1 = new ArtifactRef(ArtifactKind.DIRECTIVE, "D1");
        ReachPlan plan = new ReachPlan(Set.of(d1), CascadeRules.CASCADE_RELATIONS, Set.of(), true);

        PacksSession session = adapter.session(Optional.of(List.of("alpha")));
        session.reach(plan);
        session.reach(plan);

        assertThat(session.snapshot().held(ArtifactKind.DIRECTIVE)).containsExactlyInAnyOrder("D1", "D2");
        assertThat(closures.calls).as("one verified read for the session").isEqualTo(1);
    }

    @Test
    void anUnresolvedSessionWalksNothing() {
        closures.throwsOn(
                Optional.of(List.of(new PackIdentifier("ghost"))), new UnknownRootException(List.of("ghost")));
        ArtifactRef d1 = new ArtifactRef(ArtifactKind.DIRECTIVE, "D1");

        PacksSession session = adapter.session(Optional.of(List.of("ghost")));

        assertThat(session.snapshot().unknownPacks()).containsExactly("ghost");
        assertThat(session.reach(new ReachPlan(Set.of(d1), CascadeRules.CASCADE_RELATIONS, Set.of(), true)))
                .isEqualTo(Reach.empty());
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
        assertThat(reach.reached().get(rRef))
                .containsExactly(
                        new Reach.Via(qRef, RelationType.REQUIRES),
                        new Reach.Via(new ArtifactRef(ArtifactKind.MISSION_TYPE, "software-dev"), RelationType.SCOPE));
        assertThat(reach.stopped().get(qRef)).containsExactly(new Reach.Via(pRef, RelationType.SPECIALIZES_FROM));
        assertThat(reach.behindStopped()).containsExactly(rRef);
        assertThat(reach.ancestorsPassed()).containsExactly(qRef);
    }

    // ---- the merged closure and the in-force states, as values (T120) ----

    /** A closure of hand-built packs, merged for real: the facts each pack holds, in merge order. */
    private static final class Merging {

        private final List<PackUnderResolution> packs = new ArrayList<>();
        private final Map<String, PackCompositionFacts> facts = new LinkedHashMap<>();
        private final Map<String, MutabilityPolicySource> policies = new LinkedHashMap<>();
        private final Map<String, String> ids;

        Merging() {
            this(Map.of());
        }

        /** A closure whose packs declare the given {@code pack_id}s, and {@code id-<name>} when a name is not given. */
        Merging(Map<String, String> ids) {
            this.ids = ids;
        }

        /** A pack at a level, holding bare URNs, depending on the named packs and writing the edges. */
        Merging pack(
                String name,
                PackLevel level,
                List<String> dependsOn,
                List<String> held,
                Map<String, List<String>> languages,
                List<Preset> presets,
                MutabilityPolicySource policy,
                Relationship... edges) {
            PackDescriptor descriptor = new PackDescriptor(
                    descriptorId(name),
                    "1.0.0",
                    Optional.empty(),
                    name,
                    Optional.empty(),
                    Optional.empty(),
                    PackPaths.none(),
                    dependsOn.stream()
                            .map(other -> new PackDependency(descriptorId(other), ">=0"))
                            .toList());
            packs.add(new PackUnderResolution(
                    new ConfiguredPack(new PackIdentifier(name), level), descriptor, List.of(), held));
            facts.put(
                    name,
                    new PackCompositionFacts(
                            List.of(), held, List.of(), policy, presets, Arrays.asList(edges), languages));
            policies.put(descriptorId(name), policy);
            return this;
        }

        ClosureView.MergeInputs mergeInputs() {
            return new ClosureView.MergeInputs(packs, facts);
        }

        ClosureView view() {
            return view("sha256:merged");
        }

        ClosureView view(String closureFingerprint) {
            return view(closureFingerprint, Map.of(), List.of());
        }

        /** The view of a closure whose held ids are declared, as the compose declares them. */
        ClosureView viewHolding(Map<ArtifactKind, Set<String>> held) {
            return view("sha256:merged", held, entries());
        }

        private ClosureView view(
                String closureFingerprint, Map<ArtifactKind, Set<String>> held, List<OfferingEntry> offered) {
            PerPackPolicy policy = new PerPackPolicy(policies);
            return new ClosureView(
                    offered,
                    held,
                    Map.of(),
                    new RequiredFloor(Map.of()),
                    policy,
                    packs.stream().map(pack -> pack.descriptor().packId()).toList(),
                    List.of(),
                    Map.of(),
                    "sha256:merged",
                    EdgeIndex.of(packs, facts),
                    new ClosureView.MergeInputs(packs, facts),
                    closureFingerprint);
        }

        /** The packs as the offering lists them, so the floor names each by the id it is configured under. */
        private List<OfferingEntry> entries() {
            return packs.stream()
                    .map(pack -> new OfferingEntry(
                            pack.configured().id(),
                            pack.descriptor().packId(),
                            pack.configured().level(),
                            pack.descriptor().name(),
                            "1.0.0",
                            List.of()))
                    .toList();
        }

        private String descriptorId(String name) {
            return ids.getOrDefault(name, "id-" + name);
        }
    }

    private static Urn urn(String text) {
        return Urn.parse(text).orElseThrow();
    }

    private static ArtifactRef ref(String text) {
        return ArtifactRef.of(urn(text)).orElseThrow();
    }

    private static Relationship edge(String from, RelationType type, String to, String when, String rationale) {
        return new Relationship(urn(from), urn(to), type, Optional.ofNullable(when), Optional.ofNullable(rationale));
    }

    /**
     * beta (built-in) holds D1, T, L and a template; alpha (org) holds D1 again and a step of a mission type, and writes
     * D1 suggests T. T declares two languages.
     */
    private static Merging layered() {
        return new Merging()
                .pack(
                        "beta",
                        PackLevel.BUILT_IN,
                        List.of(),
                        List.of("directive:D1", "tactic:T", "tactic:L", "template:tpl"),
                        Map.of("tactic:T", List.of("python", "java")),
                        List.of(),
                        MutabilityPolicySource.builder()
                                .entry(urn("tactic:L"), MutabilityLevel.LOCKED)
                                .build())
                .pack(
                        "alpha",
                        PackLevel.ORG,
                        List.of("beta"),
                        List.of("directive:D1", "mission_type:M", "action:M/step"),
                        Map.of(),
                        List.of(),
                        MutabilityPolicySource.empty(),
                        edge("directive:D1", RelationType.SUGGESTS, "tactic:T", "the change is large", "T checks it"));
    }

    @Test
    void aSessionMapsTheMergedClosureIntoValues() {
        closures.answers(Optional.empty(), layered().view());

        MergedSnapshot merged = adapter.session(Optional.empty()).merged();

        assertThat(merged.winners().keySet())
                .as("artifacts of an activatable kind only: no template, no step")
                .containsExactlyInAnyOrder(
                        ref("directive:D1"), ref("tactic:T"), ref("tactic:L"), ref("mission_type:M"));
        assertThat(merged.winners().get(ref("directive:D1")))
                .isEqualTo(new MergedSnapshot.Winner("alpha", PackLevel.ORG, EffectiveSet.Policy.OPEN));
        assertThat(merged.winners().get(ref("tactic:T")))
                .isEqualTo(new MergedSnapshot.Winner("beta", PackLevel.BUILT_IN, EffectiveSet.Policy.OPEN));
        assertThat(merged.winners().get(ref("tactic:L")).policy()).isEqualTo(EffectiveSet.Policy.LOCKED);
        assertThat(merged.languages()).containsOnlyKeys(ref("tactic:T"));
        assertThat(merged.languages().get(ref("tactic:T")))
                .as("in declared order")
                .containsExactly("python", "java");
        assertThat(merged.layerOverrides())
                .containsExactly(new MergedSnapshot.LayerOverride(
                        NodeRef.of(ref("directive:D1")),
                        new MergedSnapshot.LayerOverride.Side("alpha", PackLevel.ORG),
                        new MergedSnapshot.LayerOverride.Side("beta", PackLevel.BUILT_IN)));
        assertThat(merged.edges())
                .containsExactly(new MergedSnapshot.Edge(
                        ref("directive:D1"),
                        ref("tactic:T"),
                        RelationType.SUGGESTS,
                        Optional.of("the change is large"),
                        Optional.of("T checks it")));
    }

    @Test
    void aSessionMapsTheInForceStatesIntoValues() {
        closures.answers(Optional.empty(), layered().view());
        InForceQuery query = new InForceQuery(
                Set.of(ref("directive:D1"), ref("tactic:ghost")),
                Set.of(ref("tactic:T"), ref("tactic:L")),
                Optional.of(Set.of("java")),
                Map.of());

        InForceAnswer answered = adapter.session(Optional.empty()).inForce(query);

        assertThat(answered.states().keySet())
                .containsExactlyInAnyOrder(
                        ref("directive:D1"), ref("tactic:T"), ref("tactic:L"), ref("mission_type:M"));
        assertThat(answered.states().get(ref("directive:D1")))
                .isEqualTo(new MergedSnapshot.ArtifactState(
                        ref("directive:D1"),
                        EffectiveSet.State.ACTIVE,
                        EffectiveSet.Reason.SELECTED,
                        false,
                        List.of(),
                        List.of(),
                        Optional.empty()));
        assertThat(answered.states().get(ref("tactic:T")))
                .as("denied, and T declares java")
                .isEqualTo(new MergedSnapshot.ArtifactState(
                        ref("tactic:T"),
                        EffectiveSet.State.EXCLUDED,
                        EffectiveSet.Reason.DEACTIVATED,
                        false,
                        List.of(),
                        List.of(),
                        Optional.of(EffectiveSet.Bucket.DEACTIVATED)));
        assertThat(answered.states().get(ref("tactic:L")).reason())
                .as("a lock holds against the deny entry")
                .isEqualTo(EffectiveSet.Reason.LOCKED);
        assertThat(answered.staleIds()).containsExactly(ref("tactic:ghost"));
        assertThat(answered.denyNotApplied())
                .containsExactly(new EffectiveSkippedReport.DenyNotApplied(
                        ref("tactic:L"), EffectiveSkippedReport.DenyReason.LOCKED));
    }

    @Test
    void anOverriddenArtifactNamesItsOverriderAndAFloorItemNobodyHoldsIsReported() {
        Preset governing = new Preset(
                "governing",
                "T and a ghost are required.",
                Map.of(
                        ArtifactKind.TACTIC,
                        List.of(PresetItem.mustHave("T")),
                        ArtifactKind.DIRECTIVE,
                        List.of(PresetItem.mustHave("ghost"))));
        Merging closure = new Merging()
                .pack(
                        "alpha",
                        PackLevel.ORG,
                        List.of(),
                        List.of("tactic:E", "tactic:T", "tactic:U"),
                        Map.of(),
                        List.of(governing),
                        MutabilityPolicySource.empty(),
                        edge("tactic:E", RelationType.OVERRIDES, "tactic:U", null, null));
        closures.answers(Optional.empty(), closure.view());
        InForceQuery query =
                new InForceQuery(Set.of(ref("tactic:E"), ref("tactic:U")), Set.of(), Optional.empty(), Map.of());

        InForceAnswer answered = adapter.session(Optional.empty()).inForce(query);

        assertThat(answered.states().get(ref("tactic:U")).overriddenBy()).containsExactly(ref("tactic:E"));
        assertThat(answered.states().get(ref("tactic:U")).bucket()).contains(EffectiveSet.Bucket.OVERRIDDEN);
        assertThat(answered.states().get(ref("tactic:T")))
                .as("required, so in force although the document does not select it")
                .satisfies(state -> {
                    assertThat(state.reason()).isEqualTo(EffectiveSet.Reason.REQUIRED);
                    assertThat(state.required()).isTrue();
                });
        assertThat(answered.requiredNotHeld()).containsExactly(ref("directive:ghost"));
    }

    @Test
    void theSnapshotCarriesTheDigestOfTheClosureAloneBesideTheSourceFingerprint() {
        closures.answers(Optional.empty(), layered().view("sha256:closure-alone"));

        ClosureSnapshot snapshot = adapter.snapshot(Optional.empty());

        assertThat(snapshot.fingerprint()).isEqualTo("sha256:merged");
        assertThat(snapshot.closureFingerprint()).isEqualTo("sha256:closure-alone");
    }

    /**
     * The tags of an activation over alpha, which depends on beta, with gamma configured beside them: the real
     * ClosureQueries and ConsistentRead over the real files of three pack directories, and the descriptors and facts of
     * each pack in memory.
     */
    @Test
    void aPackOutsideTheClosureLeavesTheTagAndAPackOfTheClosureChangesItOverRealPackDirectories(@TempDir Path dir)
            throws IOException {
        Map<String, Path> roots = new LinkedHashMap<>();
        for (String name : List.of("alpha", "beta", "gamma")) {
            roots.put(name, Files.createDirectories(dir.resolve(name)));
            Files.writeString(roots.get(name).resolve("pack.yaml"), "name: " + name + "\n");
        }
        PacksAvailability real = new PacksAvailability(closureQueriesOver(roots));
        Revision revision = ActivationDocument.builder().build().document().revision();
        EffectiveTag.Query page = new EffectiveTag.Query(Optional.empty(), Optional.empty(), Optional.empty(), 100);
        ClosureSnapshot before = real.snapshot(Optional.of(List.of("alpha")));

        Files.writeString(roots.get("gamma").resolve("pack.yaml"), "name: gamma\nchanged: true\n");
        ClosureSnapshot gammaChanged = real.snapshot(Optional.of(List.of("alpha")));
        Files.writeString(roots.get("beta").resolve("pack.yaml"), "name: beta\nchanged: true\n");
        ClosureSnapshot betaChanged = real.snapshot(Optional.of(List.of("alpha")));

        assertThat(gammaChanged.fingerprint()).as("every configured pack").isNotEqualTo(before.fingerprint());
        assertThat(EffectiveTag.artifacts(revision, gammaChanged, page))
                .as("a pack outside the closure")
                .isEqualTo(EffectiveTag.artifacts(revision, before, page));
        assertThat(EffectiveTag.activation(revision, gammaChanged))
                .isEqualTo(EffectiveTag.activation(revision, before));
        assertThat(EffectiveTag.artifacts(revision, betaChanged, page))
                .as("a pack of the closure, a dependency of the root")
                .isNotEqualTo(EffectiveTag.artifacts(revision, before, page));
        assertThat(EffectiveTag.activation(revision, betaChanged))
                .isNotEqualTo(EffectiveTag.activation(revision, before));
    }

    private static ClosureQueries closureQueriesOver(Map<String, Path> roots) {
        List<ConfiguredPack> configured = roots.keySet().stream()
                .map(name -> new ConfiguredPack(new PackIdentifier(name), PackLevel.ORG))
                .toList();
        Map<String, PackDescriptor> descriptors = new LinkedHashMap<>();
        roots.keySet()
                .forEach(name -> descriptors.put(
                        name,
                        new PackDescriptor(
                                name,
                                "1.0.0",
                                Optional.empty(),
                                name + " pack",
                                Optional.empty(),
                                Optional.empty(),
                                PackPaths.none(),
                                name.equals("alpha") ? List.of(new PackDependency("beta", ">=1.0")) : List.of())));
        PackDescriptorRepository repository = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return find(pack).orElseThrow();
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
        PackCatalogRepository catalog = () -> configured;
        ConsistentRead read = new ConsistentRead(new FileSystemPackSources(() -> roots.entrySet().stream()
                .map(entry -> new PackLocation(entry.getKey(), PackLevel.ORG, entry.getValue()))
                .toList()));
        OfferingReadService offering =
                new OfferingReadService(catalog, repository, pack -> PackCompositionFacts.NONE, read);
        return new ClosureQueries(offering, catalog, read);
    }

    @Test
    void theMergedClosureAndTheInForceStatesAnswerFromTheOneReadOfTheSession() {
        closures.answers(Optional.empty(), layered().view());
        InForceQuery query = new InForceQuery(Set.of(ref("directive:D1")), Set.of(), Optional.empty(), Map.of());

        PacksSession session = adapter.session(Optional.empty());
        session.snapshot();
        session.merged();
        session.inForce(query);
        session.inForce(query);
        session.merged();

        assertThat(closures.calls)
                .as("NFR-007: one verified read for the snapshot, the merge and every in-force question")
                .isEqualTo(1);
    }

    @Test
    void aMergeRefusalReachesTheCallerUnchanged() {
        Merging closure = new Merging()
                .pack(
                        "beta",
                        PackLevel.BUILT_IN,
                        List.of(),
                        List.of("directive:D1"),
                        Map.of(),
                        List.of(),
                        MutabilityPolicySource.builder()
                                .entry(urn("directive:D1"), MutabilityLevel.LOCKED)
                                .build())
                .pack(
                        "alpha",
                        PackLevel.ORG,
                        List.of("beta"),
                        List.of("directive:D1"),
                        Map.of(),
                        List.of(),
                        MutabilityPolicySource.empty());
        closures.answers(Optional.empty(), closure.view());
        PacksSession session = adapter.session(Optional.empty());

        assertThatThrownBy(session::merged)
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        assertThatThrownBy(() -> session.inForce(new InForceQuery(Set.of(), Set.of(), Optional.empty(), Map.of())))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @Test
    void anUnresolvedSessionHoldsNoMergedClosureAndNoStates() {
        closures.throwsOn(
                Optional.of(List.of(new PackIdentifier("ghost"))), new UnknownRootException(List.of("ghost")));

        PacksSession session = adapter.session(Optional.of(List.of("ghost")));

        assertThat(session.merged()).isEqualTo(MergedSnapshot.empty());
        assertThat(session.inForce(new InForceQuery(Set.of(), Set.of(), Optional.empty(), Map.of())))
                .isEqualTo(InForceAnswer.empty());
    }

    @Test
    void everyStateReasonAndBucketOfThePacksSliceHasItsCounterpartInTheDomain() {
        assertThat(names(InForceStates.State.values())).isSubsetOf(names(EffectiveSet.State.values()));
        assertThat(names(InForceStates.Reason.values())).isSubsetOf(names(EffectiveSet.Reason.values()));
        assertThat(names(InForceStates.Bucket.values())).isSubsetOf(names(EffectiveSet.Bucket.values()));
        assertThat(names(InForceStates.DenyReason.values()))
                .isSubsetOf(names(EffectiveSkippedReport.DenyReason.values()));
        assertThat(names(MutabilityLevel.values())).isSubsetOf(names(EffectiveSet.Policy.values()));
    }

    private static Set<String> names(Enum<?>[] constants) {
        return new LinkedHashSet<>(Stream.of(constants).map(Enum::name).toList());
    }
}
