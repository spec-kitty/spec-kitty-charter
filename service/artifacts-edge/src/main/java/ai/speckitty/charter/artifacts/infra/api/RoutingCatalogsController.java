package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.RoutingCatalogsApi;
import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.RoutingCatalogList;
import ai.speckitty.charter.artifacts.application.RoutingCatalogReads;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.web.DeferredETag;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The pack-scoped routing catalog reads (ADR-0080 decision 6), {@code listPackRoutingCatalogs} and
 * {@code getPackRoutingCatalog}: each answers the pack revision as its {@code ETag} and the source fingerprint of its
 * read, as the skill reads do.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class RoutingCatalogsController implements RoutingCatalogsApi {

    private final RoutingCatalogReads catalogs;
    private final RoutingCatalogBodies bodies;

    /**
     * The controller over the port.
     *
     * @param catalogs the pack-scoped catalog reads
     * @param bodies the wire mapping
     */
    @Autowired
    public RoutingCatalogsController(RoutingCatalogReads catalogs, RoutingCatalogBodies bodies) {
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.bodies = Objects.requireNonNull(bodies, "bodies");
    }

    @Override
    public ResponseEntity<RoutingCatalogList> listPackRoutingCatalogs(String packId) {
        RoutingCatalogReads.Listed listed = catalogs.list(new PackIdentifier(packId));
        DeferredETag.offer(EntityTags.quote(listed.revision()));
        return ResponseEntity.ok()
                .header(HeaderNames.SOURCE_FINGERPRINT, listed.sourceFingerprint())
                .body(bodies.list(packId, listed.catalogs()));
    }

    @Override
    public ResponseEntity<Artifact> getPackRoutingCatalog(String packId, String artifactId) {
        RoutingCatalogReads.Read read = catalogs.catalog(new PackIdentifier(packId), artifactId);
        DeferredETag.offer(EntityTags.quote(read.revision()));
        return ResponseEntity.ok()
                .header(HeaderNames.SOURCE_FINGERPRINT, read.sourceFingerprint())
                .body(bodies.artifact(read.catalog()));
    }
}
