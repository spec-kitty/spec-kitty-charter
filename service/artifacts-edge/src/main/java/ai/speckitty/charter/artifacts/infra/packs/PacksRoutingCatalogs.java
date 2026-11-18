package ai.speckitty.charter.artifacts.infra.packs;

import ai.speckitty.charter.artifacts.application.RoutingCatalogReads;
import ai.speckitty.charter.artifacts.application.RoutingCatalogView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import ai.speckitty.charter.routing.domain.RoutingCatalog;
import java.util.Comparator;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The routing catalog reads over the packs slice (ADR-0080 decision 6), as {@link PacksSkills} reads the skills: the
 * pack store binds the kind (ADR-0037, amendment of 2026-11-18), and the catalog is the routing slice's domain value.
 */
@Component
public final class PacksRoutingCatalogs implements RoutingCatalogReads {

    private final ForeignArtifactRead packs;

    /**
     * The reads over the packs slice.
     *
     * @param packs the packs slice's read of an artifact of a kind it binds for another slice
     */
    @Autowired
    public PacksRoutingCatalogs(ForeignArtifactRead packs) {
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    @Override
    public Listed list(PackIdentifier pack) {
        ConsistentRead.Verified<ForeignArtifactRead.Held> verified = packs.held(pack, ArtifactKind.ROUTING_CATALOG);
        return new Listed(
                verified.value().artifacts().stream()
                        .map(RoutingCatalog.class::cast)
                        .map(PacksRoutingCatalogs::viewOf)
                        .sorted(Comparator.comparing(RoutingCatalogView::id))
                        .toList(),
                verified.value().revision().token(),
                verified.fingerprint());
    }

    @Override
    public Read catalog(PackIdentifier pack, String catalogId) {
        ConsistentRead.Verified<ForeignArtifactRead.Foreign> verified =
                packs.read(pack, ArtifactKind.ROUTING_CATALOG, catalogId, 0);
        return new Read(
                viewOf(RoutingCatalog.class.cast(verified.value().artifact())),
                verified.value().revision().token(),
                verified.fingerprint());
    }

    @Override
    public Listed list(PackIdentifier pack, String expectedFingerprint) {
        Listed listed = list(pack);
        Fingerprints.require(listed.sourceFingerprint(), expectedFingerprint);
        return listed;
    }

    @Override
    public RoutingCatalogView view(CharterArtifact catalog, String read) {
        if (!(catalog instanceof RoutingCatalog held)) {
            throw new IllegalStateException(read + " holds " + catalog.kind().token() + " " + catalog.localId()
                    + ", which is no routing catalog.");
        }
        return viewOf(held);
    }

    static RoutingCatalogView viewOf(RoutingCatalog catalog) {
        return new RoutingCatalogView(catalog.packId(), catalog);
    }
}
