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
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackSources;
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

    private static PackDescriptor extending(String packId, String parent) {
        return new PackDescriptor(
                packId,
                "1.0.0",
                Optional.of(parent),
                packId + " pack",
                Optional.empty(),
                Optional.empty(),
                PackPaths.none(),
                List.of());
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
        return queriesOver(new PackSources() {
            @Override
            public String fingerprint() {
                digests.incrementAndGet();
                return fingerprint.get();
            }

            @Override
            public String fingerprintOf(java.util.Collection<String> packNames) {
                return "scoped:" + packNames;
            }
        });
    }

    /** Sources that digest each pack's content on its own, as the file system does: the whole digest is every pack's. */
    private PackSources contentSources(Map<String, String> contents) {
        return new PackSources() {
            @Override
            public String fingerprint() {
                return "whole:" + new java.util.TreeMap<>(contents);
            }

            @Override
            public String fingerprintOf(java.util.Collection<String> packNames) {
                return "scoped:"
                        + new java.util.TreeMap<>(contents)
                                .entrySet().stream()
                                        .filter(entry -> packNames.contains(entry.getKey()))
                                        .toList();
            }
        };
    }

    private ClosureQueries queriesOver(PackSources sources) {
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
        ConsistentRead read = new ConsistentRead(sources);
        PackCatalogRepository catalog = () -> List.copyOf(configured);
        OfferingReadService offering = new OfferingReadService(
                catalog,
                repository,
                pack -> {
                    factsRead.add(pack.value());
                    PackCompositionFacts held = facts.get(pack.value());
                    if (held == null) {
                        throw new IllegalArgumentException("a fault of the compose, not an unknown root");
                    }
                    return held;
                },
                read);
        return new ClosureQueries(offering, catalog, read);
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
        assertThat(view.heldIds().get(ArtifactKind.MISSION_TYPE)).containsExactly("software-dev");
        assertThat(view.closurePackIds()).containsExactly("beta", "alpha");
        assertThat(view.fingerprint()).isEqualTo(D1);
    }

    @Test
    void theClosureFingerprintCoversTheClosurePacksAndNoOtherConfiguredPack() {
        alphaBetaGamma();
        Map<String, String> contents = new HashMap<>(Map.of("alpha", "a1", "beta", "b1", "gamma", "g1"));
        ClosureQueries queries = queriesOver(contentSources(contents));
        ClosureView before = queries.snapshot(roots("alpha"));

        contents.put("gamma", "g2");
        ClosureView gammaChanged = queries.snapshot(roots("alpha"));
        contents.put("beta", "b2");
        ClosureView betaChanged = queries.snapshot(roots("alpha"));

        assertThat(gammaChanged.fingerprint())
                .as("the source fingerprint is the digest of every configured pack")
                .isNotEqualTo(before.fingerprint());
        assertThat(gammaChanged.closureFingerprint())
                .as("a pack outside the closure")
                .isEqualTo(before.closureFingerprint());
        assertThat(betaChanged.closureFingerprint())
                .as("a pack in the closure, as the dependency of the root")
                .isNotEqualTo(before.closureFingerprint());
        assertThat(queries.snapshot(roots("gamma")).closureFingerprint())
                .as("another closure")
                .isNotEqualTo(betaChanged.closureFingerprint());
        assertThat(before.closureFingerprint()).matches("sha256:[0-9a-f]{64}");
    }

    @Test
    void theClosureFingerprintIsTheSameWhateverOrderThePacksAreConfiguredIn() {
        // Sources that digest the packs they are asked for in configured order, as the file system does.
        Map<String, String> contents = Map.of("alpha", "a1", "beta", "b1", "gamma", "g1");
        PackSources inConfiguredOrder = new PackSources() {
            @Override
            public String fingerprint() {
                return "whole:" + configured;
            }

            @Override
            public String fingerprintOf(java.util.Collection<String> packNames) {
                return "scoped:"
                        + configured.stream()
                                .map(pack -> pack.id().value())
                                .filter(packNames::contains)
                                .map(name -> name + "=" + contents.get(name))
                                .toList();
            }
        };
        alphaBetaGamma();
        String asConfigured =
                queriesOver(inConfiguredOrder).snapshot(roots("alpha", "gamma")).closureFingerprint();
        List<ConfiguredPack> reversed = new ArrayList<>(configured.reversed());
        configured.clear();
        configured.addAll(reversed);

        assertThat(queriesOver(inConfiguredOrder)
                        .snapshot(roots("gamma", "alpha"))
                        .closureFingerprint())
                .as("NFR-001: the same closure, configured and named in the other order")
                .isEqualTo(asConfigured);
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
                Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("D1"), PresetItem.of("D2"))));
        Preset convenience =
                new Preset("shared", "conveniences", Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.of("D3"))));
        Preset only = new Preset(
                "builtin-only", "conveniences", Map.of(ArtifactKind.MISSION_TYPE, List.of(PresetItem.of("x"))));
        pack(
                "builtin",
                PackLevel.BUILT_IN,
                descriptor("builtin", "1.0.0"),
                holding(List.of(), MutabilityPolicySource.empty(), convenience, only));
        pack(
                "org",
                PackLevel.ORG,
                descriptor("org", "1.0.0"),
                holding(List.of("directive:D1"), MutabilityPolicySource.empty(), governing));

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
    void aPresetTwoPacksHoldPutsOnlyTheWinningCopysMustHaveItemsOnTheFloor() {
        Preset earlier =
                new Preset("team", "earlier", Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("A"))));
        Preset later = new Preset("team", "later", Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("B"))));
        pack(
                "builtin",
                PackLevel.BUILT_IN,
                descriptor("builtin", "1.0.0"),
                holding(List.of(), MutabilityPolicySource.empty(), earlier));
        pack(
                "org",
                PackLevel.ORG,
                descriptor("org", "1.0.0"),
                holding(List.of("directive:B"), MutabilityPolicySource.empty(), later));

        ClosureView view = queries().allConfigured();

        assertThat(view.presets().get("team"))
                .as("the last holder in merge order wins (ADR-0053 d6)")
                .isEqualTo(new ClosureView.HeldPreset(later, "org"));
        assertThat(view.floor().requiringPacks(new Urn(NodeKind.DIRECTIVE, "B")))
                .containsExactly("org");
        assertThat(view.floor().isRequired(new Urn(NodeKind.DIRECTIVE, "A")))
                .as("the losing copy requires nothing")
                .isFalse();
        assertThat(view.heldPresets()).as("each preset once").containsExactly(new ClosureView.HeldPreset(later, "org"));
    }

    @Test
    void aPresetItemThatNamesAHeldIdOnlyByCaseRefusesTheClosure() {
        Preset preset = new Preset(
                "governing", "governs", Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("directive_001"))));
        pack(
                "org",
                PackLevel.ORG,
                descriptor("org", "1.0.0"),
                holding(List.of("directive:DIRECTIVE_001"), MutabilityPolicySource.empty(), preset));

        assertThatThrownBy(() -> queries().allConfigured()).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
            assertThat(refusal.getMessage()).contains("directive:directive_001", "directive:DIRECTIVE_001");
        });
    }

    @Test
    void aPolicyLockThatNamesAHeldIdOnlyByCaseRefusesTheClosureButOneOnAnUnheldIdChangesNothing() {
        MutabilityPolicySource caseOnly = MutabilityPolicySource.builder()
                .entry(new Urn(NodeKind.DIRECTIVE, "directive_001"), MutabilityLevel.LOCKED)
                .build();
        pack("org", PackLevel.ORG, descriptor("org", "1.0.0"), holding(List.of("directive:DIRECTIVE_001"), caseOnly));

        assertThatThrownBy(() -> queries().allConfigured())
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING));
    }

    @Test
    void aPolicyLockOnAnIdTheClosureDoesNotHoldIsServedAndLocksNothing() {
        MutabilityPolicySource unheld = MutabilityPolicySource.builder()
                .entry(new Urn(NodeKind.DIRECTIVE, "GONE"), MutabilityLevel.LOCKED)
                .build();
        pack("org", PackLevel.ORG, descriptor("org", "1.0.0"), holding(List.of("directive:DIRECTIVE_001"), unheld));

        ClosureView view = queries().allConfigured();

        assertThat(view.isLocked(new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_001"))).isFalse();
    }

    // ---- A15 (FR-033, DM-01M6RA97220XBR7RR447FE2JXD, rule C22): a pack's lock reaches what it builds on only -------

    @Test
    void a15AProjectPacksKindDefaultLockDoesNotLockTheDirectivesOfAnUnrelatedOrgPackInTheSameClosure() {
        MutabilityPolicySource locksDirectives = MutabilityPolicySource.builder()
                .kindDefault(NodeKind.DIRECTIVE, MutabilityLevel.LOCKED)
                .build();
        pack("p", PackLevel.PROJECT, descriptor("p", "1.0.0"), holding(List.of("directive:P1"), locksDirectives));
        pack("q", PackLevel.ORG, descriptor("q", "1.0.0"), holding("directive:Q1"));

        ClosureView view = queries().allConfigured();

        assertThat(view.isLocked(new Urn(NodeKind.DIRECTIVE, "P1")))
                .as("p locks what p holds")
                .isTrue();
        assertThat(view.isLocked(new Urn(NodeKind.DIRECTIVE, "Q1")))
                .as("q is unrelated to p: p's lock does not reach q's directive")
                .isFalse();
        assertThat(view.lockOf(new Urn(NodeKind.DIRECTIVE, "Q1"))).isEqualTo(PerPackPolicy.Level.OPEN);
        assertThat(view.isRequired(new Urn(NodeKind.DIRECTIVE, "Q1"))).isFalse();
    }

    @Test
    void a15APackLocksTheDirectiveOfThePackItDependsOnAsAnEnterprisePackEnforcesABuiltInPractice() {
        MutabilityPolicySource locksDirectives = MutabilityPolicySource.builder()
                .kindDefault(NodeKind.DIRECTIVE, MutabilityLevel.LOCKED)
                .build();
        pack("b", PackLevel.BUILT_IN, descriptor("b", "1.0.0"), holding("directive:B1"));
        pack(
                "e",
                PackLevel.ORG,
                descriptor("e", "1.0.0", new PackDependency("b", ">=1.0")),
                holding(List.of("directive:E1"), locksDirectives));

        ClosureView view = queries().snapshot(roots("e"));

        assertThat(view.lockOf(new Urn(NodeKind.DIRECTIVE, "B1")))
                .isEqualTo(new PerPackPolicy.Level(MutabilityLevel.LOCKED, Optional.of("e")));
        assertThat(view.isLocked(new Urn(NodeKind.DIRECTIVE, "E1"))).isTrue();
        assertThat(view.isLocked(new Urn(NodeKind.DIRECTIVE, "UNHELD")))
                .as("an id no pack of e's closure holds is not locked by a kind default")
                .isFalse();
    }

    // ---- A10 (FR-028, DM-01M6RA8MG55V8C2V9PXWDTRNQS, rule C31): the floor holds held ids only, unheld is reported ---

    @Test
    void a10APresetMustHaveItemNoPackHoldsIsNotOnTheFloorAndIsReportedUnheld() {
        Urn z = new Urn(NodeKind.DIRECTIVE, "Z");
        Preset governing = new Preset(
                "governing",
                "governs",
                Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("Z"), PresetItem.mustHave("D1"))));
        pack(
                "org",
                PackLevel.ORG,
                descriptor("org", "1.0.0"),
                holding(List.of("directive:D1"), MutabilityPolicySource.empty(), governing));

        ClosureView view = queries().allConfigured();

        assertThat(view.floor().isRequired(z))
                .as("no pack of the closure holds directive:Z")
                .isFalse();
        assertThat(view.isRequired(z)).isFalse();
        assertThat(view.floor().unheld()).containsOnlyKeys(z).containsEntry(z, List.of("org"));
        assertThat(view.floor().requiringPacks(DIRECTIVE_D1))
                .as("same fixture: the held must-have item stays on the floor")
                .containsExactly("org");
    }

    @Test
    void absentRootsOfAServiceWithNoConfiguredPackGiveAnEmptyClosure() {
        ClosureView view = queries().allConfigured();

        assertThat(view.closurePackIds()).isEmpty();
        assertThat(view.packs()).isEmpty();
    }

    @Test
    void aChildPackWhoseIdSortsBeforeItsParentMergesAfterItSoItsPresetWinsAndItsMustHaveStaysOnTheFloor() {
        Preset inherited = new Preset(
                "team",
                "the parent's copy",
                Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("FROM-PARENT"))));
        Preset overriding = new Preset(
                "team", "the child's copy", Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("FROM-CHILD"))));
        // "a-child" sorts before "z-parent": without the parent_pack edge the parent would merge last and win.
        pack(
                "a-child",
                PackLevel.ORG,
                extending("a-child", "z-parent"),
                holding(List.of("directive:FROM-CHILD"), MutabilityPolicySource.empty(), overriding));
        pack(
                "z-parent",
                PackLevel.ORG,
                descriptor("z-parent", "1.0.0"),
                holding(List.of(), MutabilityPolicySource.empty(), inherited));

        ClosureView view = queries().snapshot(roots("a-child"));

        assertThat(view.closurePackIds()).containsExactly("z-parent", "a-child");
        assertThat(view.presets().get("team"))
                .as("the child merges after the pack it extends, so its copy wins")
                .isEqualTo(new ClosureView.HeldPreset(overriding, "a-child"));
        assertThat(view.floor().requiringPacks(new Urn(NodeKind.DIRECTIVE, "FROM-CHILD")))
                .containsExactly("a-child");
        assertThat(view.isRequired(new Urn(NodeKind.DIRECTIVE, "FROM-PARENT")))
                .as("the losing parent copy requires nothing")
                .isFalse();
    }

    @Test
    void twoUnrelatedPacksOfOneLevelHoldingOnePresetIdRefuseTheClosure() {
        Preset left = new Preset("team", "left", Map.of());
        Preset right = new Preset("team", "right", Map.of());
        pack(
                "left",
                PackLevel.ORG,
                descriptor("left", "1.0.0"),
                holding(List.of(), MutabilityPolicySource.empty(), left));
        pack(
                "right",
                PackLevel.ORG,
                descriptor("right", "1.0.0"),
                holding(List.of(), MutabilityPolicySource.empty(), right));

        assertThatThrownBy(() -> queries().allConfigured()).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);
            assertThat(refusal.getMessage()).contains("the preset `team`");
        });
    }

    @Test
    void aParentCycleRefusesTheClosureAsADependencyCycle() {
        pack("a", PackLevel.ORG, extending("a", "b"), holding());
        pack("b", PackLevel.ORG, extending("b", "a"), holding());

        assertThatThrownBy(() -> queries().snapshot(roots("a")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_DEPENDENCY_CYCLE));
    }

    @Test
    void theParentOfEachClosurePackIsItsDescriptorsParentPack() {
        pack("base-local", PackLevel.BUILT_IN, descriptor("base", "1.0.0"), holding());
        pack("team", PackLevel.PROJECT, extending("team", "base"), holding());

        ClosureView view = queries().snapshot(roots("team"));

        assertThat(view.parentOf("team")).contains("base");
        assertThat(view.parentOf("base")).isEmpty();
    }

    @Test
    void aRootNoPackIsConfiguredAsIsAnUnknownRootNamingEveryOneAndComposesNothing() {
        alphaBetaGamma();

        assertThatThrownBy(() -> queries().snapshot(roots("ghost", "alpha", "phantom")))
                .isInstanceOfSatisfying(UnknownRootException.class, unknown -> {
                    assertThat(unknown.unknownIds()).containsExactly("ghost", "phantom");
                    assertThat(unknown.getMessage()).contains("ghost", "phantom");
                });
        assertThat(factsRead).isEmpty();
    }

    @Test
    void anyOtherArgumentErrorOfTheComposePropagatesUnchanged() {
        alphaBetaGamma();
        facts.remove("beta");

        assertThatThrownBy(() -> queries().snapshot(roots("alpha")))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a fault of the compose");
    }

    @Test
    void aPackChangeDuringTheReadAnswersStaleInput() {
        alphaBetaGamma();
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> queries(() -> calls.incrementAndGet() % 2 == 1 ? D1 : D2)
                        .snapshot(roots("alpha")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void aStaleReadIsRetriedAndAnswersTheVersionTheRetryReads() {
        alphaBetaGamma();
        AtomicInteger calls = new AtomicInteger();

        ClosureView view = queries(() -> calls.incrementAndGet() == 1 ? D1 : D2).snapshot(roots("alpha"));

        assertThat(view.fingerprint()).as("the retry's own verified digest").isEqualTo(D2);
        assertThat(view.closurePackIds()).containsExactly("beta", "alpha");
    }

    @Test
    void aReadThatIsStaleOnEveryTryIsRetriedThreeTimesThenAnswersStaleInput() {
        alphaBetaGamma();
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> queries(() -> calls.incrementAndGet() % 2 == 1 ? D1 : D2)
                        .snapshot(roots("alpha")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.STALE_INPUT));
        assertThat(digests.get())
                .as("one try and three retries, two digests each")
                .isEqualTo(8);
    }

    @Test
    void aRefusalThatIsNotStaleIsNeverRetried() {
        pack("alpha", PackLevel.ORG, descriptor("alpha", "1.0.0", new PackDependency("beta", ">=2.0")), holding());
        pack("beta", PackLevel.ORG, descriptor("beta", "1.2.0"), holding());

        assertThatThrownBy(() -> queries().snapshot(roots("alpha"))).isInstanceOf(CharterRefusal.class);

        assertThat(digests.get())
                .as("one try: the first digest, then the second after the refusal")
                .isEqualTo(2);
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
     * The view answers every reach, over {@code requires} or {@code enhances} alike, from the edges of the facts the one
     * verified read already holds; asking it reads nothing more.
     */
    @Test
    void theViewAnswersEachReachFromTheSameReadWithNoFurtherRead() {
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

        ReachResult enhanced = view.reach(
                new ReachRequest(Set.of(tacticE), Set.of(RelationType.ENHANCES), urn -> false, Optional.empty()));

        assertThat(reached.reached()).containsOnlyKeys(new Urn(NodeKind.DIRECTIVE, "A1"), tacticT);
        assertThat(enhanced.reached()).containsOnlyKeys(tacticE, tacticT);
        assertThat(digests.get()).as("still one ConsistentRead").isEqualTo(2);
        assertThat(factsRead).as("each closure pack's facts read once").containsExactlyInAnyOrder("alpha", "beta");
    }
}
