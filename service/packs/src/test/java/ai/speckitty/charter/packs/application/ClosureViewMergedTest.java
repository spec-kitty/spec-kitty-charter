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

        assertThat(view.reach(request).reached().keySet())
                .as("the unmerged index keeps both versions' edges")
                .contains(new Urn(NodeKind.TACTIC, "T1"), new Urn(NodeKind.TACTIC, "T2"));
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
}
