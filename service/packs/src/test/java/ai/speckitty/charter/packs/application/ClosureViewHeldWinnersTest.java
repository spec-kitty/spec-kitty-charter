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
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MergedClosure;
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
import org.junit.jupiter.api.Test;

/**
 * {@link ClosureView#heldWinners} over a real compose and a real {@link ClosureQueries} read (slice A2, research R2):
 * the winners of one kind after the layer merge, whatever the kind, an asset included, in id order with the pack, the
 * level and the policy of each. A copy a nearer level replaces is not listed, and an artifact of another kind is not
 * either.
 */
class ClosureViewHeldWinnersTest {

    private final List<ConfiguredPack> configured = new ArrayList<>();
    private final Map<String, PackDescriptor> descriptors = new HashMap<>();
    private final Map<String, PackCompositionFacts> facts = new HashMap<>();

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

    private static PackCompositionFacts holding(MutabilityPolicySource policy, String... held) {
        return new PackCompositionFacts(List.of(), List.of(held), List.of(), policy, List.of(), List.of(), Map.of());
    }

    private static PackCompositionFacts holding(String... held) {
        return holding(MutabilityPolicySource.empty(), held);
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
        OfferingReadService offering =
                new OfferingReadService(catalog, repository, pack -> facts.get(pack.value()), read);
        return new ClosureQueries(offering, catalog, read).allConfigured();
    }

    private static String described(MergedClosure.Winner winner) {
        return winner.urn() + "@" + winner.packId().value() + "/"
                + winner.level().token() + "/" + winner.policy().level().name().toLowerCase(java.util.Locale.ROOT);
    }

    @Test
    void theAssetWinnersAreTheProjectCopyOverTheBuiltInOneInIdOrderAndNoOtherKind() {
        pack("core", PackLevel.BUILT_IN, List.of(), holding("asset:run", "asset:logo", "directive:D"));
        pack("proj", PackLevel.PROJECT, List.of("core"), holding("asset:run", "skill:land-pr"));

        List<MergedClosure.Winner> assets = snapshot().heldWinners(ArtifactKind.ASSET);

        assertThat(assets)
                .extracting(ClosureViewHeldWinnersTest::described)
                .as("run is held by both and the project copy wins; logo only by core; no directive or skill")
                .containsExactly("asset:logo@core/built-in/open", "asset:run@proj/project/open");
    }

    @Test
    void anyKindIsAnsweredAndAKindNoPackHoldsIsEmpty() {
        pack("core", PackLevel.BUILT_IN, List.of(), holding("skill:zeta", "skill:alpha", "directive:D"));

        ClosureView view = snapshot();

        assertThat(view.heldWinners(ArtifactKind.SKILL))
                .extracting(winner -> winner.urn().id())
                .as("code-point order, not the order the pack holds them in")
                .containsExactly("alpha", "zeta");
        assertThat(view.heldWinners(ArtifactKind.DIRECTIVE))
                .extracting(MergedClosure.Winner::urn)
                .containsExactly(new Urn(NodeKind.DIRECTIVE, "D"));
        assertThat(view.heldWinners(ArtifactKind.ASSET)).isEmpty();
    }

    @Test
    void theWinnerCarriesThePolicyItsClosureDeclares() {
        Urn logo = new Urn(NodeKind.ASSET, "logo");
        pack(
                "core",
                PackLevel.BUILT_IN,
                List.of(),
                holding(
                        MutabilityPolicySource.builder()
                                .entry(logo, MutabilityLevel.EXTENDABLE)
                                .build(),
                        "asset:logo"));

        assertThat(snapshot().heldWinners(ArtifactKind.ASSET))
                .extracting(ClosureViewHeldWinnersTest::described)
                .containsExactly("asset:logo@core/built-in/extendable");
    }

    @Test
    void aMergeThatIsRefusedRefusesTheListing() {
        Urn run = new Urn(NodeKind.ASSET, "run");
        pack(
                "org",
                PackLevel.ORG,
                List.of(),
                holding(
                        MutabilityPolicySource.builder()
                                .entry(run, MutabilityLevel.LOCKED)
                                .build(),
                        "asset:run"));
        pack("proj", PackLevel.PROJECT, List.of("org"), holding("asset:run"));

        ClosureView view = snapshot();

        assertThatThrownBy(() -> view.heldWinners(ArtifactKind.ASSET))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }
}
