package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.application.RegistrationImpact;
import ai.speckitty.charter.packs.application.StagedPackViews;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The persistence edge's {@link StagedPackViews}: each session reads the stored packs through the repository ports once
 * and lays a written pack over them ({@link StagedPacks}).
 */
@Service
public final class StagedPackViewFactory implements StagedPackViews {

    private final PackCatalogRepository catalog;
    private final PackDescriptorRepository descriptors;
    private final PackArtifactRepository artifacts;

    /**
     * A factory over the repository ports.
     *
     * @param catalog the configured packs
     * @param descriptors their descriptors
     * @param artifacts what the compose reads of each
     */
    @Autowired
    public StagedPackViewFactory(
            PackCatalogRepository catalog, PackDescriptorRepository descriptors, PackArtifactRepository artifacts) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
    }

    @Override
    public Session session() {
        StagedPacks stored = StagedPacks.stored(catalog, descriptors, artifacts);
        return new Session() {
            @Override
            public View stored() {
                return stored.view();
            }

            @Override
            public View with(RegistrationImpact.Replacement written) {
                return stored.with(written).view();
            }
        };
    }
}
