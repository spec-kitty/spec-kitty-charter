package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.EffectiveRoutingCatalogsApi;
import ai.speckitty.charter.api.model.EffectiveRoutingCatalogRead;
import ai.speckitty.charter.api.model.RoutingCatalogList;
import ai.speckitty.charter.artifacts.application.EffectiveRoutingCatalogReads;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The routing catalog reads under an activation (ADR-0080 decision 6), {@code listEffectiveRoutingCatalogs} and
 * {@code getEffectiveRoutingCatalog}: each answers its {@code ETag}, honours {@code If-None-Match} with a {@code 304},
 * and carries the source fingerprint of its read, as the skill twins do.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class EffectiveRoutingCatalogsController implements EffectiveRoutingCatalogsApi {

    private final EffectiveRoutingCatalogReads catalogs;
    private final RoutingCatalogBodies bodies;

    /**
     * The controller over the application ring.
     *
     * @param catalogs the catalog reads under an activation
     * @param bodies the wire mapping
     */
    @Autowired
    public EffectiveRoutingCatalogsController(EffectiveRoutingCatalogReads catalogs, RoutingCatalogBodies bodies) {
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.bodies = Objects.requireNonNull(bodies, "bodies");
    }

    @Override
    public ResponseEntity<RoutingCatalogList> listEffectiveRoutingCatalogs(
            String activationId, @Nullable String ifNoneMatch) {
        return EffectiveAssetsController.answer(
                catalogs.list(activationId, ifNoneMatch),
                listed -> listed.scope().sourceFingerprint(),
                bodies::effectiveList);
    }

    @Override
    public ResponseEntity<EffectiveRoutingCatalogRead> getEffectiveRoutingCatalog(
            String activationId, String artifactId, @Nullable String ifNoneMatch) {
        return EffectiveAssetsController.answer(
                catalogs.catalog(activationId, artifactId, ifNoneMatch),
                read -> read.scope().sourceFingerprint(),
                bodies::effective);
    }
}
