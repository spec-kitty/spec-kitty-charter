package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * The closure snapshot over in-memory ports (NFR-007, FR-009, FR-023): one verified read answers the closure's packs,
 * held ids, holders, mission types, floor, locks and presets, and a change during the read answers {@code stale_input}.
 */
class ClosureQueriesTest {

    private static final String D1 = "sha256:" + "1".repeat(64);
    private static final String D2 = "sha256:" + "2".repeat(64);
    private static final Urn DIRECTIVE_D1 = new Urn(NodeKind.DIRECTIVE, "D1");

    private final List<ConfiguredPack> configured = new ArrayList<>();
    private final Map<String, PackDescriptor> descriptors = new HashMap<>();
    private final Map<String, PackCompositionFacts> facts = new HashMap<>();
    private final List<String> factsRead = new ArrayList<>();
    private final AtomicInteger digests = new AtomicInteger();

    private void pack(String id, PackLevel level, PackDescriptor descriptor, PackCompositionFacts held) {
        configured.add(new ConfiguredPack(new PackIdentifier(id), level));
        descriptors.put(id, descriptor);
        facts.put(id, held);
    }

    private static PackDescriptor descriptor(String packId, String version, PackDependency... dependencies) {
        return new PackDescriptor(
                packId,
                version,
                Optional.empty(),
                packId + " pack",
                Optional.empty(),
                Optional.empty(),
                PackPaths.none(),
                List.of(dependencies));
    }

    /** A descriptor whose name is its {@code pack_id}, so a {@code <pack>::} qualifier can name it. */
    private static PackDescriptor named(String packId, String version, PackDependency... dependencies) {
        return new PackDescriptor(
                packId,
                version,
                Optional.empty(),
                packId,
                Optional.empty(),
                Optional.empty(),
                PackPaths.none(),
                List.of(dependencies));
    }

    private static PackCompositionFacts holding(
            List<String> defined, MutabilityPolicySource policy, Preset... presets) {
        return new PackCompositionFacts(List.of(), defined, List.of(), policy, List.of(presets));
    }

    private static PackCompositionFacts holding(String... defined) {
        return holding(List.of(defined), MutabilityPolicySource.empty());
    }

    /** alpha depends on beta; gamma is unrelated. */
    private void alphaBetaGamma() {
        pack(
                "alpha",
                PackLevel.ORG,
                descriptor("alpha", "1.0.0", new PackDependency("beta", ">=1.0")),
                holding("directive:A1", "mission_type:software-dev"));
        pack("beta", PackLevel.ORG, descriptor("beta", "1.2.0"), holding("directive:B1", "tactic:t"));
        pack("gamma", PackLevel.ORG, descriptor("gamma", "1.0.0"), holding("directive:G1"));
    }

    private ClosureQueries queries(Supplier<String> fingerprint) {
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
        ConsistentRead read = new ConsistentRead(() -> {
            digests.incrementAndGet();
            return fingerprint.get();
        });
        OfferingReadService offering = new OfferingReadService(
                () -> List.copyOf(configured),
                repository,
                pack -> {
                    factsRead.add(pack.value());
                    return facts.get(pack.value());
                },
                read);
        return new ClosureQueries(offering, read);
    }

    private ClosureQueries queries() {
        return queries(() -> D1);
    }

    private static Optional<List<PackIdentifier>> roots(String... ids) {
        return Optional.of(Arrays.stream(ids).map(PackIdentifier::new).toList());
    }

    @Test
    void aRootedSnapshotHoldsTheRootsClosureInDependencyOrderAndNothingElse() {
        alphaBetaGamma();

        ClosureView view = queries().snapshot(roots("alpha"));

        assertThat(view.packs()).extracting(entry -> entry.id().value()).containsExactly("beta", "alpha");
        assertThat(view.packs().getLast().dependencies()).singleElement().satisfies(edge -> {
            assertThat(edge.to()).isEqualTo("beta");
            assertThat(edge.version()).isEqualTo("1.2.0");
        });
        assertThat(view.heldIds().get(ArtifactKind.DIRECTIVE)).containsExactlyInAnyOrder("A1", "B1");
        assertThat(view.heldIds().get(ArtifactKind.TACTIC)).containsExactly("t");
        assertThat(view.holds(ArtifactKind.DIRECTIVE, "G1"))
                .as("gamma is outside the closure")
                .isFalse();
        assertThat(view.holds(ArtifactKind.DIRECTIVE, "B1")).isTrue();
        assertThat(view.holderOf()).containsEntry(new Urn(NodeKind.DIRECTIVE, "B1"), "beta");
        assertThat(view.missionTypes()).containsExactly("software-dev");
        assertThat(view.closurePackIds()).containsExactly("beta", "alpha");
        assertThat(view.fingerprint()).isEqualTo(D1);
    }

    @Test
    void absentRootsMeanEveryConfiguredPack() {
        alphaBetaGamma();
        ClosureQueries queries = queries();

        assertThat(queries.snapshot(Optional.empty()).packs())
                .extracting(entry -> entry.id().value())
                .containsExactly("beta", "alpha", "gamma");
        assertThat(queries.allConfigured().packs()).hasSize(3);
    }

    @Test
    void aClosureThatCannotComposePropagatesTheCompositionRefusalUnchanged() {
        pack("alpha", PackLevel.ORG, descriptor("alpha", "1.0.0", new PackDependency("beta", ">=2.0")), holding());
        pack("beta", PackLevel.ORG, descriptor("beta", "1.2.0"), holding());

        assertThatThrownBy(() -> queries().snapshot(roots("alpha")))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.PACK_DEPENDENCY_OUT_OF_RANGE);
                    assertThat(refused.getMessage()).contains("beta");
                });
    }

    @Test
    void aLockAlongTheClosureIsReportedWithTheDescriptorPackIdThatSetItAndImpliesRequired() {
        MutabilityPolicySource locksD1 = MutabilityPolicySource.builder()
                .entry(DIRECTIVE_D1, MutabilityLevel.LOCKED)
                .build();
        pack("org-local", PackLevel.ORG, descriptor("org", "1.0.0"), holding(List.of("directive:D1"), locksD1));
        pack("team", PackLevel.PROJECT, descriptor("team", "1.0.0", new PackDependency("org", ">=1.0")), holding());

        ClosureView view = queries().snapshot(roots("team"));

        assertThat(view.lockOf(DIRECTIVE_D1))
                .isEqualTo(new PerPackPolicy.Level(MutabilityLevel.LOCKED, Optional.of("org")));
        assertThat(view.isLocked(DIRECTIVE_D1)).isTrue();
        assertThat(view.floor().isRequired(DIRECTIVE_D1))
                .as("no preset requires it")
                .isFalse();
        assertThat(view.isRequired(DIRECTIVE_D1))
                .as("locked implies required (ADR-0012 D7)")
                .isTrue();
        assertThat(view.isLocked(new Urn(NodeKind.DIRECTIVE, "D2"))).isFalse();
        assertThat(view.holderOf()).containsEntry(DIRECTIVE_D1, "org");
    }

    @Test
    void theFloorAndThePresetsComeFromThePresetsTheClosureHolds() {
        Preset governing = new Preset(
                "shared",
                "governs",
                List.of(),
                Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("D1"), PresetItem.of("D2"))),
                List.of());
        Preset convenience = new Preset(
                "shared",
                "conveniences",
                List.of(),
                Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.of("D3"))),
                List.of());
        Preset only = new Preset("builtin-only", "conveniences", List.of(), Map.of(), List.of(PresetItem.of("x")));
        pack(
                "builtin",
                PackLevel.BUILT_IN,
                descriptor("builtin", "1.0.0"),
                holding(List.of(), MutabilityPolicySource.empty(), convenience, only));
        pack(
                "org",
                PackLevel.ORG,
                descriptor("org", "1.0.0"),
                holding(List.of(), MutabilityPolicySource.empty(), governing));

        ClosureView view = queries().allConfigured();

        assertThat(view.floor().requiringPacks(DIRECTIVE_D1)).containsExactly("org");
        assertThat(view.isRequired(new Urn(NodeKind.DIRECTIVE, "D2"))).isFalse();
        assertThat(view.isRequired(new Urn(NodeKind.DIRECTIVE, "D3"))).isFalse();
        assertThat(view.presets()).containsOnlyKeys("shared", "builtin-only");
        assertThat(view.presets().get("shared"))
                .as("the last holder in merge order wins (ADR-0053 d6)")
                .isEqualTo(new ClosureView.HeldPreset(governing, "org"));
        assertThat(view.presets().get("builtin-only").descriptorPackId()).isEqualTo("builtin");
    }

    @Test
    void aPackChangeDuringTheReadAnswersStaleInput() {
        alphaBetaGamma();
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() ->
                        queries(() -> calls.incrementAndGet() == 1 ? D1 : D2).snapshot(roots("alpha")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void oneSnapshotIsOneVerifiedReadThatReadsEachClosurePacksFactsOnce() {
        alphaBetaGamma();
        ClosureQueries queries = queries();

        queries.snapshot(roots("alpha"));

        assertThat(digests.get())
                .as("one ConsistentRead: one digest before, one after")
                .isEqualTo(2);
        assertThat(factsRead).as("no hidden second compose").containsExactlyInAnyOrder("alpha", "beta");

        factsRead.clear();
        digests.set(0);
        queries.allConfigured();

        assertThat(digests.get()).isEqualTo(2);
        assertThat(factsRead).containsExactlyInAnyOrder("alpha", "beta", "gamma");
    }

    /**
     * WP11 T066: the view answers the reach and the enhances lookups from the edges of the facts the one verified read
     * already holds; asking them reads nothing more.
     */
    @Test
    void theViewAnswersReachAndEnhancersFromTheSameReadWithNoFurtherRead() {
        Urn tacticT = new Urn(NodeKind.TACTIC, "t");
        Urn tacticE = new Urn(NodeKind.TACTIC, "e");
        pack(
                "alpha",
                PackLevel.ORG,
                named("alpha", "1.0.0", new PackDependency("beta", ">=1.0")),
                new PackCompositionFacts(
                        List.of(),
                        List.of("directive:A1", "tactic:e"),
                        List.of(),
                        MutabilityPolicySource.empty(),
                        List.of(),
                        List.of(
                                new Relationship(
                                        new Urn(NodeKind.DIRECTIVE, "A1"),
                                        Optional.empty(),
                                        tacticT,
                                        Optional.of("beta"),
                                        RelationType.REQUIRES,
                                        Optional.empty(),
                                        Optional.empty()),
                                new Relationship(
                                        tacticE,
                                        Optional.empty(),
                                        tacticT,
                                        Optional.of("beta"),
                                        RelationType.ENHANCES,
                                        Optional.empty(),
                                        Optional.empty()))));
        pack("beta", PackLevel.ORG, named("beta", "1.2.0"), holding("directive:B1", "tactic:t"));
        ClosureQueries queries = queries();

        ClosureView view = queries.snapshot(roots("alpha"));
        ReachResult reached = view.reach(new ReachRequest(
                Set.of(new Urn(NodeKind.DIRECTIVE, "A1")),
                Set.of(RelationType.REQUIRES),
                urn -> false,
                Optional.empty()));

        assertThat(reached.reached()).containsOnlyKeys(new Urn(NodeKind.DIRECTIVE, "A1"), tacticT);
        assertThat(view.enhancesPull(Set.of(tacticT))).containsOnlyKeys(tacticE);
        assertThat(view.enhancersOf(Set.of(tacticT)))
                .containsEntry(tacticT, List.of(new EdgeIndex.Place(new PackIdentifier("alpha"), tacticE)));
        assertThat(digests.get()).as("still one ConsistentRead").isEqualTo(2);
        assertThat(factsRead).as("each closure pack's facts read once").containsExactlyInAnyOrder("alpha", "beta");
    }
}
