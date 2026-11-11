package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.application.RegistrationImpact;
import ai.speckitty.charter.packs.application.StagedPackViews;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The persistence edge's {@link StagedPackViews}: each session reads the stored packs through the repository ports once
 * and lays a written pack over them ({@link StagedPacks}).
 */
@Service
public final class StagedPackViewFactory implements StagedPackViews {

    private final Supplier<StagedPacks> sessions;
    private final Supplier<Optional<PackRepository>> store;

    /**
     * A factory over the repository ports, which a session reads through as they are.
     *
     * @param catalog the configured packs
     * @param descriptors their descriptors
     * @param artifacts what the compose reads of each
     */
    public StagedPackViewFactory(
            PackCatalogRepository catalog, PackDescriptorRepository descriptors, PackArtifactRepository artifacts) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(descriptors, "descriptors");
        Objects.requireNonNull(artifacts, "artifacts");
        this.sessions = () -> StagedPacks.stored(catalog, descriptors, artifacts);
        this.store = () -> artifacts instanceof PackRepository repository ? Optional.of(repository) : Optional.empty();
    }

    /**
     * A factory over the configured locations, as Spring wires it.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format
     */
    @Autowired
    public StagedPackViewFactory(Locations locations, SerializationStrategy strategy) {
        Objects.requireNonNull(locations, "locations");
        Objects.requireNonNull(strategy, "strategy");
        this.store = () -> Optional.of(new DomainRepositoryService(locations, strategy));
        this.sessions = () -> {
            // The session runs under the registry lock, which every pack write takes, so the listing is taken once:
            // each
            // listing parses every pack.yaml, and the repository lists for each pack it reads.
            List<PackLocation> listed = List.copyOf(locations.all());
            DomainRepositoryService repository = new DomainRepositoryService(() -> listed, strategy);
            return StagedPacks.stored(repository, repository, repository);
        };
    }

    @Override
    public Session session() {
        StagedPacks stored = sessions.get();
        Optional<PackRepository> repository = store.get();
        return new Session() {
            @Override
            public View stored() {
                return stored.view();
            }

            @Override
            public View with(RegistrationImpact.Replacement written) {
                return stored.with(written).view();
            }

            @Override
            public Optional<PackRepository> packs() {
                return repository;
            }
        };
    }
}
