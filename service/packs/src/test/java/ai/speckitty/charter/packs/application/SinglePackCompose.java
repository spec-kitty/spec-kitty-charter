package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The production relationship-target check over a compose of one configured pack, for the tests whose fixture is one
 * in-memory pack: the pack's own descriptor and artifacts are what the compose reads, as they are in production.
 */
public final class SinglePackCompose {

    private SinglePackCompose() {}

    /**
     * The check over a compose of {@code id} alone.
     *
     * @param id the configured identifier of the one pack
     * @param current the pack as it is held now
     * @return the production check, resolving the compose from {@code current} on every call
     */
    public static RelationshipTargetCheck check(PackIdentifier id, Supplier<Pack> current) {
        PackDescriptorRepository descriptors = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return current.get().descriptor();
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier pack) {
                return Optional.of(get(pack));
            }

            @Override
            public void save(PackIdentifier pack, PackDescriptor descriptor) {
                throw new UnsupportedOperationException();
            }
        };
        PackArtifactRepository facts = pack -> new PackCompositionFacts(
                List.of(),
                current.get().artifacts().stream()
                        .map(CharterArtifact::urn)
                        .map(Object::toString)
                        .toList(),
                List.of());
        return new ComposedRelationshipTargetCheck(new OfferingReadService(
                () -> List.of(new ConfiguredPack(id, PackLevel.ORG)),
                descriptors,
                facts,
                new ConsistentRead(() -> "sha256:" + "a".repeat(64))));
    }
}
