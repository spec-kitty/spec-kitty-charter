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
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The holder of a reference (FR-013, FR-014, FR-015, FR-017): {@link CompositeComponentResolver} names the pack of the
 * compose closure the offering already resolved that holds what a reference names. A qualified reference is held by the
 * pack it names, a bare reference is held only by the referring pack, and the two refusals name their packs.
 */
class CompositeComponentResolverTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    /** The second pack of the shared two-pack fixture (SC-004): bravo, holding directive SHARED_1. */
    private static final PackIdentifier PACK_B = new PackIdentifier("pack-b");

    // ---- the pack-qualified reference grammar (FR-013, FR-014, FR-015, FR-017; SC-004's shared fixture) --------

    @Test
    void aPackQualifiedReferenceIsHeldByTheNamedPackOfTheClosure() {
        assertThat(holderOf(twoPackResolver(), "bravo::directive:SHARED_1")).isEqualTo(PACK_B);
    }

    @Test
    void aBareReferenceThatIsHeldOnlyByAnotherPackIsRefusedNamingTheHoldingPack() {
        assertThatThrownBy(() -> holderOf(twoPackResolver(), "directive:SHARED_1"))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.PACK_REFERENCE_UNQUALIFIED);
                    assertThat(refused.getMessage()).contains("bravo").contains("pack-qualified");
                });
    }

    @Test
    void aBareReferenceIsHeldByTheReferringPackWhenAnotherPackHoldsTheSameIdAndTheQualifiedOneByTheOther() {
        // FR-014: alpha writes directive:SHARE bare, and bravo holds one too. Alpha's own shadows it, with no refusal.
        CompositeComponentResolver.Composed shadowing = shadowingResolver();

        assertThat(holderOf(shadowing, "directive:SHARE")).isEqualTo(PACK);
        assertThat(holderOf(shadowing, "bravo::directive:SHARE")).isEqualTo(PACK_B);
    }

    @Test
    void aBareReferenceHeldByNoPackHasNoHolder() {
        assertThat(twoPackResolver().holder(PackQualifiedReference.parse("directive:NOWHERE"), PACK))
                .isEmpty();
    }

    @Test
    void aBareReferenceHeldByTheReferringPackIsHeldByIt() {
        assertThat(holderOf(twoPackResolver(), "directive:OWN_1")).isEqualTo(PACK);
    }

    @Test
    void aQualifiedReferenceNamingAPackOutsideTheClosureIsRefusedNamingThatPack() {
        assertThatThrownBy(() -> holderOf(twoPackResolver(), "charlie::directive:ANY"))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.PACK_REFERENCE_PACK_MISSING);
                    assertThat(refused.getMessage()).contains("charlie");
                });
    }

    /** The pack of the compose that holds what the reference names, from alpha's perspective. */
    private static PackIdentifier holderOf(CompositeComponentResolver.Composed composed, String reference) {
        return composed.holder(PackQualifiedReference.parse(reference), PACK)
                .orElseThrow()
                .configured()
                .id();
    }

    private static ConsistentRead fixedDigest() {
        return new ConsistentRead(() -> "sha256:" + "a".repeat(64));
    }

    /**
     * The shared two-pack fixture of the grammar (SC-004): alpha ({@code pack-a}, directive {@code OWN_1}) and bravo
     * ({@code pack-b}, directive {@code SHARED_1}) in one compose closure, resolved through the offering.
     */
    private CompositeComponentResolver.Composed twoPackResolver() {
        return composed(
                Map.of(
                        PACK, List.of("directive:OWN_1"),
                        PACK_B, List.of("directive:SHARED_1", "procedure:release")),
                PackLevel.ORG);
    }

    /**
     * The FR-014 fixture: alpha (project) and bravo (org) both hold {@code directive:SHARE}, so alpha's shadows
     * bravo's, as a project artifact shadows an org one.
     */
    private CompositeComponentResolver.Composed shadowingResolver() {
        return composed(
                Map.of(PACK, List.of("directive:SHARE"), PACK_B, List.of("directive:SHARE")), PackLevel.PROJECT);
    }

    /** The compose over alpha at {@code alphaLevel} and bravo at org, resolved once, as a read resolves it. */
    private CompositeComponentResolver.Composed composed(Map<PackIdentifier, List<String>> held, PackLevel alphaLevel) {
        return new CompositeComponentResolver()
                .over(new OfferingReadService(
                                () -> List.of(
                                        new ConfiguredPack(PACK, alphaLevel),
                                        new ConfiguredPack(PACK_B, PackLevel.ORG)),
                                new NamedDescriptors(),
                                new PackArtifactRepository() {
                                    @Override
                                    public PackCompositionFacts compositionFacts(PackIdentifier pack) {
                                        return new PackCompositionFacts(List.of(), held.get(pack), List.of());
                                    }
                                },
                                fixedDigest())
                        .composed());
    }

    /** The descriptors of the two-pack compose: {@code pack-a} is alpha, {@code pack-b} is bravo. */
    private static final class NamedDescriptors implements PackDescriptorRepository {
        @Override
        public PackDescriptor get(PackIdentifier pack) {
            return find(pack).orElseThrow();
        }

        @Override
        public Optional<PackDescriptor> find(PackIdentifier pack) {
            return Optional.of(new PackDescriptor(
                    pack.value(),
                    "1.0.0",
                    Optional.empty(),
                    pack.equals(PACK_B) ? "bravo" : "alpha",
                    Optional.of(PackLevel.ORG),
                    Optional.empty(),
                    PackPaths.none(),
                    // alpha relies on bravo, so bravo is in alpha's own closure
                    pack.equals(PACK_B) ? List.of() : List.of(new PackDependency(PACK_B.value(), ">=1.0"))));
        }

        @Override
        public void save(PackIdentifier pack, PackDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }
    }
}
